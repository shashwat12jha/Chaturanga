package engine;

import model.GameState;
import model.Move;
import model.MoveType;
import model.Piece;
import model.PieceType;
import rules.MoveApplier;
import rules.MoveGenerator;
import rules.CheckDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * The Chaturanga Search Engine — Strong Traditional Edition.
 *
 * Algorithm: Iterative Deepening Negamax + Alpha-Beta + the following pruning techniques:
 *
 *   SEARCH ENHANCEMENTS:
 *   ✦ Null Move Pruning        — skip a move and see if beta still holds (R=3)
 *   ✦ Late Move Reductions     — reduce depth for later moves (LMR, ln-formula)
 *   ✦ Futility Pruning         — skip quiet moves that can't raise alpha at low depths
 *   ✦ Aspiration Windows       — narrow ID window to speed up convergence
 *   ✦ Delta Pruning            — skip hopeless captures in quiescence
 *
 *   MOVE ORDERING (critical for pruning efficiency):
 *   ✦ TT move first
 *   ✦ Winning captures by SEE (Static Exchange Evaluation)
 *   ✦ Killer moves (2 per ply)
 *   ✦ History heuristic (quiet moves that previously caused cutoffs)
 *   ✦ Losing captures last
 *
 *   INFRASTRUCTURE:
 *   ✦ Incremental Zobrist hashing (O(1) per node vs O(64))
 *   ✦ Two-bucket transposition table (depth-preferred + always-replace)
 *   ✦ MAX_DEPTH raised to 12 (LMR/NMP make this tractable)
 *
 * 100% original code. Concepts from standard computer chess literature.
 */
public class SearchEngine {

    public static final int MATE_SCORE   = 100_000;
    public static final int DRAW_SCORE   = 0;
    private static final int MAX_DEPTH   = 12; // effective depth ~ 16+ with LMR
    private static final int Q_DEPTH     = 6;  // quiescence depth

    // Null Move Pruning reduction factor
    private static final int NMP_R = 3;

    // Futility margins per depth (index = depth)
    private static final int[] FUTILITY_MARGIN = { 0, 200, 500 };

    // Aspiration window — initial delta around previous iteration's score
    private static final int ASPIRATION_DELTA = 50;

    private final MoveGenerator moveGen   = new MoveGenerator();
    private final MoveApplier   applier   = new MoveApplier();
    private final CheckDetector checkDet  = new CheckDetector();
    private final Evaluator     evaluator = new Evaluator();
    private final ZobristHasher hasher    = ZobristHasher.get();
    private final SEEEvaluator  see       = new SEEEvaluator();

    private Personality personality = Personality.balanced();
    private SearchTreeRecorder recorder = null;
    private Runnable onDepthComplete = null;

    // ---- Search state (reset on each findBestMove call) ----

    private long nodesSearched;
    private volatile boolean stopSearch;

    // Killer moves: killers[ply][0..1] = the two best non-capture beta-cutoff moves at that ply
    private final Move[][] killers = new Move[MAX_DEPTH + 10][2];

    // History table: history[pieceIndex][toSquare] = accumulated score
    // pieceIndex: 0-5 white pieces, 6-11 black pieces (same mapping as ZobristHasher)
    private final int[][] history = new int[12][64];

    // ---- Public API ----

    public void setPersonality(Personality p) { this.personality = p; }
    public void setRecorder(SearchTreeRecorder r) { this.recorder = r; }
    public void setOnDepthComplete(Runnable callback) { this.onDepthComplete = callback; }
    public long getNodesSearched() { return nodesSearched; }

    /**
     * Find the best move for the side to move.
     * Uses Iterative Deepening + Aspiration Windows up to targetDepth.
     *
     * @param state       current position
     * @param targetDepth max depth (use 8 for play mode, 5 for coaching/visualisation)
     * @param timeLimitMs stop searching after this many ms (0 = unlimited)
     * @return the best Move found, or null if no legal moves exist
     */
    public Move findBestMove(GameState state, int targetDepth, long timeLimitMs) {
        // Check opening book first (only in first 15 moves)
        if (state.getFullMoveNumber() <= 15) {
            String fingerprint = buildFenFingerprint(state);
            String bookMove = OpeningBook.get().lookup(fingerprint);
            if (bookMove != null) {
                Move m = uciToMove(bookMove, state);
                if (m != null) return m;
            }
        }

        nodesSearched = 0;
        stopSearch    = false;
        long startTime = System.currentTimeMillis();
        long deadline = timeLimitMs > 0 ? startTime + timeLimitMs : Long.MAX_VALUE;

        // Reset search heuristics for a fresh search
        clearKillers();
        clearHistory();

        Move bestMove = null;
        int  prevScore = 0;

        long rootHash = hasher.computeHash(state);

        // ---- Iterative Deepening ----
        for (int depth = 1; depth <= targetDepth; depth++) {
            long now = System.currentTimeMillis();
            if (now >= deadline) break;

            // ---- SOFT TIME STOP (Pitfall #4 fix) ----
            // If we've used > 50% of the time budget, don't start a new depth.
            // The next depth typically takes 2-4x longer, so we'd almost certainly
            // time out and discard the result — wasting all that time.
            if (timeLimitMs > 0 && depth > 1 && (now - startTime) * 2 > timeLimitMs) break;

            SearchResult result;

            if (depth >= 4 && Math.abs(prevScore) < MATE_SCORE - 200) {
                // ---- Aspiration Windows ----
                int delta = ASPIRATION_DELTA;
                int alpha = prevScore - delta;
                int beta  = prevScore + delta;

                while (true) {
                    if (recorder != null) recorder.startRecording("root", alpha, beta);

                    SearchResult rootResult = negamax(state, depth, alpha, beta, 0, rootHash, deadline, true);

                    if (onDepthComplete != null) {
                        onDepthComplete.run();
                    }

                    result = rootResult;
                    if (result.timedOut) break;

                    if (result.score <= alpha) {
                        // Fail-low: widen lower bound
                        alpha = Math.max(alpha - delta, -MATE_SCORE);
                        delta *= 2;
                    } else if (result.score >= beta) {
                        // Fail-high: widen upper bound
                        beta = Math.min(beta + delta, MATE_SCORE);
                        delta *= 2;
                    } else {
                        // Inside window — done
                        break;
                    }

                    // Safety: avoid infinite widening
                    if (delta > 2000) {
                        if (recorder != null) recorder.startRecording("root", -MATE_SCORE, MATE_SCORE);
                        result = negamax(state, depth, -MATE_SCORE, MATE_SCORE, 0, rootHash, deadline, false);
                        break;
                    }
                }
            } else {
                // Full-window search for early depths
                if (recorder != null) recorder.startRecording("root", -MATE_SCORE, MATE_SCORE);
                result = negamax(state, depth, -MATE_SCORE, MATE_SCORE, 0, rootHash, deadline, false);
            }

            if (!result.timedOut && result.bestMove != null) {
                bestMove  = result.bestMove;
                prevScore = result.score;
            }

            // Stop early if we found forced mate
            if (Math.abs(prevScore) >= MATE_SCORE - 100) break;
        }

        if (recorder != null) recorder.stopRecording();
        return bestMove;
    }

    /** Evaluate the current position without searching (for eval bar). */
    public EvalBreakdown evaluatePosition(GameState state) {
        return evaluator.evaluate(state, personality);
    }

    /** Stop an in-progress search (called from UI thread). */
    public void stop() { stopSearch = true; }

    // ======================================================================
    //  Core Negamax with Alpha-Beta
    // ======================================================================

    private SearchResult negamax(GameState state, int depth, int alpha, int beta,
                                  int ply, long hash, long deadline, boolean nullMoveAllowed) {
        nodesSearched++;

        // ---- Time check (Pitfall #5 fix: check every 1024 nodes instead of 4096) ----
        if ((nodesSearched & 0x3FF) == 0 &&
                (System.currentTimeMillis() >= deadline || stopSearch)) {
            return new SearchResult(null, 0, true);
        }

        // ---- Transposition Table probe ----
        // PITFALL #1 FIX: Never use TT cutoffs at ply 0 (root node).
        // The TT can store EXACT entries with null bestMove from terminal nodes.
        // If this hash-collides with the root, findBestMove returns null → crash.
        // We still extract the TT move for move ordering at the root.
        ZobristHasher.TTEntry ttEntry = hasher.ttProbe(hash);
        Move ttMove = null;
        if (ttEntry != null) {
            ttMove = ttEntry.bestMove;
            if (ply > 0 && ttEntry.depth >= depth) {
                switch (ttEntry.type) {
                    case EXACT:
                        return new SearchResult(ttEntry.bestMove, ttEntry.score, false);
                    case LOWER_BOUND:
                        alpha = Math.max(alpha, ttEntry.score);
                        break;
                    case UPPER_BOUND:
                        beta = Math.min(beta, ttEntry.score);
                        break;
                }
                if (alpha >= beta) {
                    return new SearchResult(ttEntry.bestMove, ttEntry.score, false);
                }
            }
        }

        // ---- Terminal / leaf node ----
        List<Move> legalMoves = moveGen.generateLegalMoves(state);

        if (legalMoves.isEmpty()) {
            boolean inCheck = checkDet.isKingInCheck(state.isWhiteToMove(), state);
            int score = inCheck ? -(MATE_SCORE - ply) : DRAW_SCORE;
            return new SearchResult(null, score, false);
        }

        if (state.getHalfMoveClock() >= 100) {
            return new SearchResult(null, DRAW_SCORE, false); // 50-move rule
        }

        // ---- Quiescence at depth 0 ----
        if (depth == 0) {
            int qScore = quiescence(state, Q_DEPTH, alpha, beta, ply, hash, deadline);
            return new SearchResult(null, qScore, false);
        }

        // ---- Static evaluation (needed for pruning) ----
        int staticEval = evaluator.evaluateStatic(state, personality);
        if (!state.isWhiteToMove()) staticEval = -staticEval;

        boolean inCheck = checkDet.isKingInCheck(state.isWhiteToMove(), state);

        // ======================================================
        //  NULL MOVE PRUNING
        // ======================================================
        // If we can pass (skip our move) and alpha-beta still returns >= beta
        // at reduced depth, the position is so good we can prune this branch.
        // Skip: in check, at ply 0, in endgame (zugzwang risk), when not allowed.
        if (nullMoveAllowed
                && depth >= 3
                && !inCheck
                && ply > 0
                && !evaluator.isEndgame(state)
                && staticEval >= beta) {

            GameState nullState = state.copy();
            nullState.setWhiteToMove(!state.isWhiteToMove());
            nullState.setEnPassantTarget(-1);
            long nullHash = hash ^ hasher.sideToMoveKey();
            int prevEP = state.getEnPassantTarget();
            if (prevEP >= 0) nullHash ^= hasher.enPassantKey(prevEP % 8);

            int R = NMP_R;
            if (depth >= 6) R = NMP_R + 1; // deeper R for more depth

            SearchResult nullResult = negamax(nullState, depth - 1 - R, -beta, -beta + 1,
                    ply + 1, nullHash, deadline, false); // no chaining NMP
            if (!nullResult.timedOut && -nullResult.score >= beta) {
                // Null move cutoff!
                return new SearchResult(null, beta, false);
            }
        }

        // ======================================================
        //  FUTILITY PRUNING
        // ======================================================
        // At depths 1 and 2, if the static eval is far below alpha, quiet moves
        // are unlikely to raise alpha. Skip them (still search captures and checks).
        boolean futilityPrune = !inCheck
                && depth <= 2
                && depth >= 1
                && staticEval + FUTILITY_MARGIN[depth] <= alpha
                && Math.abs(alpha) < MATE_SCORE - 100;

        // ---- Move ordering ----
        orderMoves(legalMoves, ttMove, ply, state);

        Move bestMove = null;
        int originalAlpha = alpha;
        int moveCount = 0;

        for (Move move : legalMoves) {
            boolean isCapture = (move.captured != null || move.type == MoveType.EN_PASSANT);
            boolean isPromotion = (move.type == MoveType.PROMOTION);
            boolean isQuiet = !isCapture && !isPromotion;

            // ---- Futility Pruning: skip quiet moves ----
            if (futilityPrune && isQuiet && moveCount > 0) {
                continue;
            }

            if (recorder != null) recorder.enterNode(move, depth, alpha, beta);

            GameState next = applier.apply(move, state);
            long nextHash  = hasher.updateHash(hash, move, state, next);

            // ======================================================
            //  LATE MOVE REDUCTIONS (LMR)
            // ======================================================
            // After the first few moves, search remaining quiet moves at
            // reduced depth. If they raise alpha, re-search at full depth.
            int reduction = 0;
            boolean doFullSearch = true;

            if (moveCount >= 2
                    && depth >= 3
                    && isQuiet
                    && !inCheck
                    && !checkDet.isKingInCheck(next.isWhiteToMove(), next)) {

                // Standard LMR reduction formula from Stockfish/Ethereal
                reduction = (int)(1.0 + Math.log(depth) * Math.log(moveCount + 1) / 2.0);
                reduction = Math.min(reduction, depth - 1);

                // Reduced-depth search with null window
                SearchResult lmrResult = negamax(next, depth - 1 - reduction, -alpha - 1, -alpha,
                        ply + 1, nextHash, deadline, true);

                if (lmrResult.timedOut) {
                    if (recorder != null) recorder.exitNode(0, false);
                    return new SearchResult(bestMove, alpha, true);
                }

                int lmrScore = -lmrResult.score;
                if (lmrScore <= alpha) {
                    // LMR confirmed: this move is not good, skip full search
                    doFullSearch = false;
                    if (recorder != null) recorder.exitNode(lmrScore, false);
                    moveCount++;
                    continue;
                }
                // Otherwise fall through to full search
            }

            SearchResult child;
            if (doFullSearch) {
                // Principal Variation Search (PVS): null-window after first move
                if (moveCount == 0) {
                    child = negamax(next, depth - 1, -beta, -alpha,
                            ply + 1, nextHash, deadline, true);
                } else {
                    // Null-window search first
                    child = negamax(next, depth - 1, -alpha - 1, -alpha,
                            ply + 1, nextHash, deadline, true);
                    if (!child.timedOut && -child.score > alpha && -child.score < beta) {
                        // Re-search with full window
                        child = negamax(next, depth - 1, -beta, -alpha,
                                ply + 1, nextHash, deadline, true);
                    }
                }
            } else {
                child = negamax(next, depth - 1, -beta, -alpha,
                        ply + 1, nextHash, deadline, true);
            }

            int score = -child.score;

            if (child.timedOut) {
                if (recorder != null) recorder.exitNode(score, false);
                return new SearchResult(bestMove, alpha, true);
            }

            boolean pruned = false;
            if (score > alpha) {
                alpha    = score;
                bestMove = move;
            }

            if (alpha >= beta) {
                pruned = true;
                // ---- Update killer and history on beta-cutoff ----
                if (isQuiet) {
                    updateKillers(move, ply);
                    updateHistory(move, depth);
                }
                if (recorder != null) recorder.exitNode(score, true);
                hasher.ttStore(hash, depth, beta, ZobristHasher.EntryType.LOWER_BOUND, bestMove);
                return new SearchResult(bestMove, beta, false);
            }

            if (recorder != null) recorder.exitNode(score, pruned);
            moveCount++;
        }

        // ---- Store in transposition table ----
        ZobristHasher.EntryType entryType = alpha > originalAlpha
                ? ZobristHasher.EntryType.EXACT
                : ZobristHasher.EntryType.UPPER_BOUND;
        hasher.ttStore(hash, depth, alpha, entryType, bestMove);

        return new SearchResult(bestMove, alpha, false);
    }

    // ======================================================================
    //  Quiescence Search
    // ======================================================================
    // Only searches captures (+ checks) to resolve tactical sequences and
    // avoid the horizon effect.

    private int quiescence(GameState state, int depth, int alpha, int beta,
                           int ply, long hash, long deadline) {
        nodesSearched++;

        int standPat = evaluator.evaluateStatic(state, personality);
        if (!state.isWhiteToMove()) standPat = -standPat;

        if (standPat >= beta) return beta;

        // ---- Delta Pruning ----
        // If even capturing the best piece on the board can't raise alpha, skip.
        int bigDelta = 900 + 200; // queen + safety margin
        if (standPat + bigDelta < alpha) return alpha;

        if (standPat > alpha) alpha = standPat;
        if (depth == 0) return alpha;

        List<Move> captures = capturesOnly(moveGen.generateLegalMoves(state));

        // Order captures by SEE
        captures.sort((a, b) -> see.seeForMove(state, b) - see.seeForMove(state, a));

        for (Move move : captures) {
            // ---- Delta Pruning per move ----
            if (move.captured != null) {
                int gain = SEEEvaluator.pieceValue(move.captured.type);
                if (standPat + gain + 200 < alpha) continue; // this capture can't help
            }

            // Skip losing captures in quiescence (SEE < 0)
            if (see.seeForMove(state, move) < 0) continue;

            GameState next = applier.apply(move, state);
            long nextHash  = hasher.updateHash(hash, move, state, next);
            int score = -quiescence(next, depth - 1, -beta, -alpha, ply + 1, nextHash, deadline);

            if (score >= beta) return beta;
            if (score > alpha)  alpha = score;
        }
        return alpha;
    }

    // ======================================================================
    //  Move Ordering
    // ======================================================================

    /**
     * Order moves for maximum pruning efficiency:
     *   1. TT / hash move
     *   2. Winning captures (SEE >= 0), ordered by SEE score descending
     *   3. Killer moves (quiet moves that caused cutoffs at this ply)
     *   4. History-scored quiet moves (descending)
     *   5. Losing captures (SEE < 0)
     */
    private void orderMoves(List<Move> moves, Move ttBestMove, int ply, GameState state) {
        moves.sort((a, b) -> moveScore(b, ttBestMove, ply, state) - moveScore(a, ttBestMove, ply, state));
    }

    private int moveScore(Move move, Move ttBest, int ply, GameState state) {
        // 1. TT move
        if (move.equals(ttBest)) return 2_000_000;

        boolean isCapture = (move.captured != null || move.type == MoveType.EN_PASSANT);
        boolean isPromotion = (move.type == MoveType.PROMOTION);

        // 2. Captures ordered by SEE
        if (isCapture) {
            int seeScore = see.seeForMove(state, move);
            if (seeScore >= 0) {
                // Winning capture: 1_000_000 + seeScore
                return 1_000_000 + seeScore;
            } else {
                // Losing capture: goes to bottom
                return -100_000 + seeScore;
            }
        }

        // 3. Promotions (treat as near-winning)
        if (isPromotion && move.promotionPiece != null) {
            return 900_000 + move.promotionPiece.getValue();
        }

        // 4. Killer moves
        Move[] k = killers[Math.min(ply, killers.length - 1)];
        if (move.equals(k[0])) return 800_000;
        if (move.equals(k[1])) return 799_999;

        // 5. History heuristic
        int pieceIdx = ZobristHasher.pieceIndex(move.piece.type, move.piece.isWhite);
        int toSq     = move.toRow * 8 + move.toCol;
        return history[pieceIdx][toSq];
    }

    private List<Move> capturesOnly(List<Move> moves) {
        List<Move> captures = new ArrayList<>();
        for (Move m : moves) {
            if (m.captured != null || m.type == MoveType.EN_PASSANT || m.type == MoveType.PROMOTION) {
                captures.add(m);
            }
        }
        return captures;
    }

    // ======================================================================
    //  Killer & History Heuristic
    // ======================================================================

    private void updateKillers(Move move, int ply) {
        int idx = Math.min(ply, killers.length - 1);
        if (!move.equals(killers[idx][0])) {
            killers[idx][1] = killers[idx][0];
            killers[idx][0] = move;
        }
    }

    private void updateHistory(Move move, int depth) {
        int pieceIdx = ZobristHasher.pieceIndex(move.piece.type, move.piece.isWhite);
        int toSq     = move.toRow * 8 + move.toCol;
        history[pieceIdx][toSq] += depth * depth; // square of depth — deep cutoffs matter more
        // Cap to avoid overflow
        if (history[pieceIdx][toSq] > 500_000) {
            // Age the history table (divide all by 2)
            for (int p = 0; p < 12; p++)
                for (int s = 0; s < 64; s++)
                    history[p][s] >>= 1;
        }
    }

    private void clearKillers() {
        for (Move[] row : killers) {
            row[0] = null;
            row[1] = null;
        }
    }

    private void clearHistory() {
        for (int[] row : history) java.util.Arrays.fill(row, 0);
    }

    // ======================================================================
    //  Opening Book helpers
    // ======================================================================

    private String buildFenFingerprint(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.isWhiteToMove() ? "w" : "b").append("/");
        Piece[] board = state.getBoard();
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                Piece p = board[row * 8 + col];
                if (p == null) {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(p.type.getFenChar(p.isWhite));
                }
            }
            if (empty > 0) sb.append(empty);
            if (row < 7) sb.append("/");
        }
        return sb.toString();
    }

    private Move uciToMove(String uci, GameState state) {
        if (uci == null || uci.length() < 4) return null;
        int fromCol = uci.charAt(0) - 'a';
        int fromRow = '8' - uci.charAt(1);
        int toCol   = uci.charAt(2) - 'a';
        int toRow   = '8' - uci.charAt(3);

        List<Move> legalMoves = moveGen.generateLegalMoves(state);
        for (Move m : legalMoves) {
            if (m.fromCol == fromCol && m.fromRow == fromRow &&
                m.toCol   == toCol   && m.toRow   == toRow) {
                return m;
            }
        }
        return null; // Book move not legal in this position
    }

    // ======================================================================
    //  Inner result class
    // ======================================================================

    private static class SearchResult {
        final Move bestMove;
        final int score;
        final boolean timedOut;

        SearchResult(Move bestMove, int score, boolean timedOut) {
            this.bestMove = bestMove;
            this.score    = score;
            this.timedOut = timedOut;
        }
    }
}
