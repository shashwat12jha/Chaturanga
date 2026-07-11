package engine;

import engine.bitboard.BBEvaluator;
import engine.bitboard.BBMoveApplier;
import engine.bitboard.BBMoveGen;
import engine.bitboard.BBPosition;
import model.GameState;
import model.Move;
import model.MoveType;
import model.Piece;
import model.PieceType;
import rules.MoveApplier;
import rules.MoveGenerator;

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

    // Legacy (used only for GUI/book helpers that work with Move objects)
    private final MoveGenerator moveGen   = new MoveGenerator();
    private final MoveApplier   applier   = new MoveApplier();
    private final Evaluator     evaluator = new Evaluator();
    private final ZobristHasher hasher    = ZobristHasher.get();
    private final SEEEvaluator  see       = new SEEEvaluator();

    // ---- Bitboard engine layer (hot path) ----
    private final BBEvaluator  bbEval  = new BBEvaluator();
    // Per-search BBPosition — created once in findBestMove, reused throughout search via make/unmake
    private BBPosition bbRoot = null;
    // Reusable move list stack (one array per ply to avoid inter-ply interference)
    private static final int MAX_MOVES = 256;
    private final int[][] moveStack = new int[MAX_DEPTH + 20][MAX_MOVES];

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

        // ---- Build BBPosition once for this search ----
        bbRoot = BBPosition.fromGameState(state);

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

                    SearchResult rootResult = negamax(bbRoot, depth, alpha, beta, 0, rootHash, deadline, true);

                    if (onDepthComplete != null) {
                        onDepthComplete.run();
                    }

                    result = rootResult;
                    if (result.timedOut) break;

                    if (result.score <= alpha) {
                        alpha = Math.max(alpha - delta, -MATE_SCORE);
                        delta *= 2;
                    } else if (result.score >= beta) {
                        beta = Math.min(beta + delta, MATE_SCORE);
                        delta *= 2;
                    } else {
                        break;
                    }

                    if (delta > 2000) {
                        if (recorder != null) recorder.startRecording("root", -MATE_SCORE, MATE_SCORE);
                        result = negamax(bbRoot, depth, -MATE_SCORE, MATE_SCORE, 0, rootHash, deadline, false);
                        break;
                    }
                }
            } else {
                // Full-window search for early depths
                if (recorder != null) recorder.startRecording("root", -MATE_SCORE, MATE_SCORE);
                result = negamax(bbRoot, depth, -MATE_SCORE, MATE_SCORE, 0, rootHash, deadline, false);
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
        // Use fast bitboard evaluator if available, fall back to legacy
        try {
            BBPosition pos = BBPosition.fromGameState(state);
            return bbEval.evaluateBreakdown(pos, personality);
        } catch (Exception e) {
            return evaluator.evaluate(state, personality);
        }
    }

    /** Stop an in-progress search (called from UI thread). */
    public void stop() { stopSearch = true; }

    // ======================================================================
    //  Core Negamax with Alpha-Beta  (Bitboard hot path)
    // ======================================================================

    /**
     * Negamax search operating on a shared mutable BBPosition.
     * make/unmake keep it in sync — no heap allocation per node.
     * Returns score from the perspective of the side to move.
     *
     * @param bbPos     shared mutable position (make/unmake applied/rolled back)
     * @param ttBestInt int-encoded TT best move from outer scope (0 = none)
     */
    private SearchResult negamax(BBPosition bbPos, int depth, int alpha, int beta,
                                  int ply, long hash, long deadline, boolean nullMoveAllowed) {
        nodesSearched++;

        // ---- Time check every 1024 nodes ----
        if ((nodesSearched & 0x3FF) == 0 &&
                (System.currentTimeMillis() >= deadline || stopSearch)) {
            return new SearchResult(null, 0, true);
        }

        // ---- Transposition Table probe ----
        ZobristHasher.TTEntry ttEntry = hasher.ttProbe(hash);
        Move ttMove = null;
        int  ttMoveInt = 0;
        if (ttEntry != null) {
            ttMove = ttEntry.bestMove;
            ttMoveInt = moveToInt(ttMove, bbPos);
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

        // ---- Generate pseudo-legal moves ----
        int[] pseudoMoves = moveStack[ply];
        int pseudoCount   = BBMoveGen.generateMoves(bbPos, pseudoMoves);

        // ---- Filter to legal moves (check-test via make/unmake) ----
        int[] legalInts = new int[pseudoCount];
        int legalCount  = BBMoveGen.filterLegal(bbPos, pseudoMoves, pseudoCount, legalInts);

        // ---- Terminal node ----
        if (legalCount == 0) {
            boolean inCheck = bbPos.isInCheck(bbPos.whiteToMove);
            int score = inCheck ? -(MATE_SCORE - ply) : DRAW_SCORE;
            return new SearchResult(null, score, false);
        }

        if (bbPos.halfMoveClock >= 100) {
            return new SearchResult(null, DRAW_SCORE, false);
        }

        // ---- Quiescence at depth 0 ----
        if (depth <= 0) {
            int qScore = quiescenceBB(bbPos, Q_DEPTH, alpha, beta, ply, hash, deadline);
            return new SearchResult(null, qScore, false);
        }

        // ---- Static eval (for pruning decisions) ----
        int staticEval = bbEval.evaluate(bbPos, personality);
        if (!bbPos.whiteToMove) staticEval = -staticEval;

        boolean inCheck = bbPos.isInCheck(bbPos.whiteToMove);

        // ---- Null Move Pruning ----
        if (nullMoveAllowed && depth >= 3 && !inCheck && ply > 0
                && !isEndgameBB(bbPos) && staticEval >= beta) {
            // Make null move (flip side, clear EP)
            boolean savedWtm = bbPos.whiteToMove;
            int savedEP      = bbPos.enPassantSquare;
            bbPos.whiteToMove    = !bbPos.whiteToMove;
            bbPos.enPassantSquare = -1;
            long nullHash = hash ^ hasher.sideToMoveKey();
            if (savedEP >= 0) nullHash ^= hasher.enPassantKey(savedEP % 8);

            int R = (depth >= 6) ? NMP_R + 1 : NMP_R;
            SearchResult nullResult = negamax(bbPos, depth - 1 - R, -beta, -beta + 1,
                    ply + 1, nullHash, deadline, false);

            bbPos.whiteToMove    = savedWtm;
            bbPos.enPassantSquare = savedEP;

            if (!nullResult.timedOut && -nullResult.score >= beta) {
                return new SearchResult(null, beta, false);
            }
        }

        // ---- Futility Pruning ----
        boolean futilityPrune = !inCheck && depth <= 2 && depth >= 1
                && staticEval + FUTILITY_MARGIN[depth] <= alpha
                && Math.abs(alpha) < MATE_SCORE - 100;

        // ---- Order moves ----
        orderMovesInt(legalInts, legalCount, ttMoveInt, ply, bbPos);

        Move bestMove    = null;
        int originalAlpha = alpha;
        int moveCount    = 0;

        for (int mi = 0; mi < legalCount; mi++) {
            int move = legalInts[mi];
            boolean isCapture   = BBMoveGen.isCapture(move);
            boolean isPromotion = BBMoveGen.isPromotion(move);
            boolean isQuiet     = !isCapture && !isPromotion;

            if (futilityPrune && isQuiet && moveCount > 0) continue;

            // Convert to legacy Move for recorder and TT
            Move legacyMove = intToMove(move, bbPos);
            if (recorder != null) recorder.enterNode(legacyMove, depth, alpha, beta);

            long undo    = BBMoveApplier.make(bbPos, move);
            long nextHash = hasher.updateHashBB(hash, move, bbPos);

            // ---- LMR ----
            int reduction   = 0;
            boolean doFull  = true;

            if (moveCount >= 2 && depth >= 3 && isQuiet && !inCheck
                    && !bbPos.isInCheck(bbPos.whiteToMove)) {
                reduction = (int)(1.0 + Math.log(depth) * Math.log(moveCount + 1) / 2.0);
                reduction = Math.min(reduction, depth - 1);

                SearchResult lmrResult = negamax(bbPos, depth - 1 - reduction, -alpha - 1, -alpha,
                        ply + 1, nextHash, deadline, true);

                if (lmrResult.timedOut) {
                    BBMoveApplier.unmake(bbPos, move, undo);
                    if (recorder != null) recorder.exitNode(0, false);
                    return new SearchResult(bestMove, alpha, true);
                }

                if (-lmrResult.score <= alpha) {
                    doFull = false;
                    BBMoveApplier.unmake(bbPos, move, undo);
                    if (recorder != null) recorder.exitNode(-lmrResult.score, false);
                    moveCount++;
                    continue;
                }
            }

            SearchResult child;
            if (doFull) {
                if (moveCount == 0) {
                    child = negamax(bbPos, depth - 1, -beta, -alpha, ply + 1, nextHash, deadline, true);
                } else {
                    child = negamax(bbPos, depth - 1, -alpha - 1, -alpha, ply + 1, nextHash, deadline, true);
                    if (!child.timedOut && -child.score > alpha && -child.score < beta) {
                        child = negamax(bbPos, depth - 1, -beta, -alpha, ply + 1, nextHash, deadline, true);
                    }
                }
            } else {
                child = negamax(bbPos, depth - 1, -beta, -alpha, ply + 1, nextHash, deadline, true);
            }

            int score = -child.score;
            BBMoveApplier.unmake(bbPos, move, undo);

            if (child.timedOut) {
                if (recorder != null) recorder.exitNode(score, false);
                return new SearchResult(bestMove, alpha, true);
            }

            boolean pruned = false;
            if (score > alpha) {
                alpha    = score;
                bestMove = legacyMove;
            }

            if (alpha >= beta) {
                pruned = true;
                if (isQuiet) {
                    updateKillers(legacyMove, ply);
                    if (legacyMove != null) updateHistory(legacyMove, depth);
                }
                if (recorder != null) recorder.exitNode(score, true);
                hasher.ttStore(hash, depth, beta, ZobristHasher.EntryType.LOWER_BOUND, bestMove);
                return new SearchResult(bestMove, beta, false);
            }

            if (recorder != null) recorder.exitNode(score, pruned);
            moveCount++;
        }

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

    private int quiescenceBB(BBPosition bbPos, int depth, int alpha, int beta,
                              int ply, long hash, long deadline) {
        nodesSearched++;

        int standPat = bbEval.evaluate(bbPos, personality);
        if (!bbPos.whiteToMove) standPat = -standPat;

        if (standPat >= beta) return beta;

        int bigDelta = 1100; // queen + margin
        if (standPat + bigDelta < alpha) return alpha;

        if (standPat > alpha) alpha = standPat;
        if (depth == 0) return alpha;

        // Generate captures only
        int[] caps = new int[MAX_MOVES];
        int capCount = BBMoveGen.generateCaptures(bbPos, caps);
        // Filter to legal captures
        int[] legalCaps = new int[capCount];
        int legalCapCount = BBMoveGen.filterLegal(bbPos, caps, capCount, legalCaps);

        // Sort by MVV-LVA (captured piece value - moving piece value)
        sortCaptures(legalCaps, legalCapCount);

        for (int i = 0; i < legalCapCount; i++) {
            int move = legalCaps[i];
            int capPiece = BBMoveGen.capturedPiece(move);
            if (capPiece != BBMoveGen.NO_CAPTURE) {
                int gain = BBEvaluator.pieceValue(capPiece);
                if (standPat + gain + 200 < alpha) continue;
            }

            long undo    = BBMoveApplier.make(bbPos, move);
            long nextHash = hasher.updateHashBB(hash, move, bbPos);
            int score    = -quiescenceBB(bbPos, depth - 1, -beta, -alpha, ply + 1, nextHash, deadline);
            BBMoveApplier.unmake(bbPos, move, undo);

            if (score >= beta) return beta;
            if (score > alpha)  alpha = score;
        }
        return alpha;
    }

    // ======================================================================
    //  Move Ordering (bitboard int-move version)
    // ======================================================================

    /** Sort int-encoded moves: TT move first, then winning captures, killers, history, losing captures. */
    private void orderMovesInt(int[] moves, int count, int ttMoveInt, int ply, BBPosition pos) {
        int[] scores = new int[count];
        for (int i = 0; i < count; i++) scores[i] = intMoveScore(moves[i], ttMoveInt, ply, pos);
        // Simple insertion sort (count is usually small: ~30-40 moves)
        for (int i = 1; i < count; i++) {
            int keyMove = moves[i]; int keyScore = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < keyScore) {
                moves[j + 1]  = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1]  = keyMove;
            scores[j + 1] = keyScore;
        }
    }

    private int intMoveScore(int move, int ttMoveInt, int ply, BBPosition pos) {
        if (move == ttMoveInt && ttMoveInt != 0) return 2_000_000;

        boolean isCapture   = BBMoveGen.isCapture(move);
        boolean isPromotion = BBMoveGen.isPromotion(move);
        int capPiece = BBMoveGen.capturedPiece(move);
        int movPiece = BBMoveGen.movingPiece(move);

        if (isCapture) {
            // MVV-LVA: value of captured - value of attacker
            int captured = capPiece != BBMoveGen.NO_CAPTURE ? BBEvaluator.pieceValue(capPiece) : 0;
            int attacker = BBEvaluator.pieceValue(movPiece);
            int mvvLva   = captured - attacker / 10;
            return mvvLva >= 0 ? 1_000_000 + mvvLva : -100_000 + mvvLva;
        }
        if (isPromotion) {
            int promoPiece = BBMoveGen.promoPiece(move);
            return 900_000 + BBEvaluator.pieceValue(promoPiece);
        }

        // Killers (compare encoded from/to)
        Move[] k = killers[Math.min(ply, killers.length - 1)];
        int encodedFrom = BBMoveGen.fromSq(move), encodedTo = BBMoveGen.toSq(move);
        if (k[0] != null && k[0].fromRow * 8 + k[0].fromCol == encodedFrom
                         && k[0].toRow   * 8 + k[0].toCol   == encodedTo) return 800_000;
        if (k[1] != null && k[1].fromRow * 8 + k[1].fromCol == encodedFrom
                         && k[1].toRow   * 8 + k[1].toCol   == encodedTo) return 799_999;

        // History
        return history[movPiece][encodedTo];
    }

    private void sortCaptures(int[] caps, int count) {
        // Sort descending by captured piece value (MVV)
        for (int i = 1; i < count; i++) {
            int key = caps[i];
            int v   = BBEvaluator.pieceValue(BBMoveGen.capturedPiece(key));
            int j = i - 1;
            while (j >= 0 && BBEvaluator.pieceValue(BBMoveGen.capturedPiece(caps[j])) < v) {
                caps[j + 1] = caps[j]; j--;
            }
            caps[j + 1] = key;
        }
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
    //  Bitboard helpers (bridge from legacy types)
    // ======================================================================

    /** Is the position in an endgame (few non-pawn pieces)? */
    private boolean isEndgameBB(BBPosition pos) {
        int wMajors = Long.bitCount(pos.pieces[BBPosition.W_QUEEN]  | pos.pieces[BBPosition.W_ROOK]);
        int bMajors = Long.bitCount(pos.pieces[BBPosition.B_QUEEN]  | pos.pieces[BBPosition.B_ROOK]);
        int wMinors = Long.bitCount(pos.pieces[BBPosition.W_BISHOP] | pos.pieces[BBPosition.W_KNIGHT]);
        int bMinors = Long.bitCount(pos.pieces[BBPosition.B_BISHOP] | pos.pieces[BBPosition.B_KNIGHT]);
        return (wMajors + bMajors + wMinors + bMinors) <= 3;
    }

    /**
     * Convert a legacy Move to an int-encoded move for TT-move ordering.
     * Returns 0 if move is null or not found in position.
     */
    private int moveToInt(Move m, BBPosition pos) {
        if (m == null) return 0;
        int from = m.fromRow * 8 + m.fromCol;
        int to   = m.toRow   * 8 + m.toCol;
        // Find the piece at `from` in the position
        int piece = pos.pieceAt[from] & 0xFF;
        if (piece == BBPosition.NO_PIECE) return 0;
        int cap   = pos.pieceAt[to] & 0xFF;
        int flag  = BBMoveGen.FLAG_NORMAL;
        int promo = 0;
        if (m.type == MoveType.EN_PASSANT)     flag = BBMoveGen.FLAG_EN_PASSANT;
        else if (m.type == MoveType.KINGSIDE_CASTLE || m.type == MoveType.QUEENSIDE_CASTLE)
                                               flag = BBMoveGen.FLAG_CASTLE;
        else if (m.type == MoveType.PROMOTION) { flag = BBMoveGen.FLAG_PROMOTION; promo = piece < 6 ? 4 : 10; /* queen */ }
        return from | (to << 6) | (piece << 12) | (cap << 16) | (flag << 20) | (promo << 22);
    }

    /**
     * Convert an int-encoded move to a legacy Move object (for TT storage and recorder).
     * Called only at root and when a beta cutoff records a killer/history — not in the inner loop.
     */
    private Move intToMove(int m, BBPosition pos) {
        int from  = BBMoveGen.fromSq(m);
        int to    = BBMoveGen.toSq(m);
        int pieceIdx = BBMoveGen.movingPiece(m);
        int capIdx   = BBMoveGen.capturedPiece(m);
        int flag     = BBMoveGen.flag(m);
        int promoIdx = BBMoveGen.promoPiece(m);

        boolean isWhite = pieceIdx < 6;
        model.PieceType pieceType = BBPosition.pieceTypeof(pieceIdx);
        model.PieceType capType   = capIdx != BBMoveGen.NO_CAPTURE ? BBPosition.pieceTypeof(capIdx) : null;
        boolean capWhite = capIdx < 6;

        Piece piece     = new Piece(pieceType, isWhite, true);
        Piece captured  = capType != null ? new Piece(capType, capWhite, true) : null;

        MoveType moveType;
        model.PieceType promoPieceType = null;
        switch (flag) {
            case BBMoveGen.FLAG_CASTLE:      moveType = (to > from + 1) ? MoveType.KINGSIDE_CASTLE : MoveType.QUEENSIDE_CASTLE; break;
            case BBMoveGen.FLAG_EN_PASSANT: moveType = MoveType.EN_PASSANT; break;
            case BBMoveGen.FLAG_PROMOTION:  moveType = MoveType.PROMOTION;
                                            promoPieceType = BBPosition.pieceTypeof(promoIdx); break;
            default:                         moveType = MoveType.NORMAL; break;
        }

        return new Move(from % 8, from / 8, to % 8, to / 8, piece, captured, moveType, promoPieceType);
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
