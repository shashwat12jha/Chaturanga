package io.chaturanga.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Iterative-deepening fail-soft negamax with alpha-beta, PVS, quiescence,
 * transposition table, killer/history ordering, and conservative LMR.
 */
public final class SearchEngine {
    public static final int MATE_SCORE = 30_000;
    public static final int INFINITY = 32_000;
    private static final int MATE_THRESHOLD = MATE_SCORE - 1_000;
    private static final int MAX_PLY = 128;

    private final MoveGenerator generator = new MoveGenerator();
    private final ClassicalEvaluator evaluator = new ClassicalEvaluator();
    private final TranspositionTable table;
    private final int[][] killers = new int[MAX_PLY][2];
    private final int[][][] history = new int[2][64][64];
    private final Move[][] pv = new Move[MAX_PLY][MAX_PLY];
    private final int[] pvLength = new int[MAX_PLY];

    private volatile boolean stopped;
    private long nodes;
    private long startedNanos;
    private long deadlineNanos;
    private long nodeLimit;
    private Consumer<SearchInfo> infoListener = ignored -> {};

    public SearchEngine() {
        this(32);
    }

    public SearchEngine(int hashMegabytes) {
        table = new TranspositionTable(hashMegabytes);
    }

    public void setInfoListener(Consumer<SearchInfo> listener) {
        infoListener = listener == null ? ignored -> {} : listener;
    }

    public void stop() {
        stopped = true;
    }

    public void clear() {
        table.clear();
        for (int ply = 0; ply < killers.length; ply++) {
            killers[ply][0] = 0;
            killers[ply][1] = 0;
        }
        for (int color = 0; color < 2; color++) {
            for (int from = 0; from < 64; from++) {
                java.util.Arrays.fill(history[color][from], 0);
            }
        }
    }

    public SearchResult search(Position root, SearchLimits limits) {
        stopped = false;
        nodes = 0;
        startedNanos = System.nanoTime();
        deadlineNanos = limits.moveTimeMillis() == 0
                ? Long.MAX_VALUE : startedNanos + limits.moveTimeMillis() * 1_000_000L;
        nodeLimit = limits.nodeLimit() == 0 ? Long.MAX_VALUE : limits.nodeLimit();
        table.newSearch();
        ageHistory();

        int maxDepth = limits.depth() == 0 ? 64 : Math.min(64, limits.depth());
        Move completedBest = Move.NONE;
        int completedScore = 0;
        int completedDepth = 0;
        List<Move> completedPv = List.of();

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                int alpha = -INFINITY;
                int beta = INFINITY;
                if (depth >= 4) {
                    alpha = Math.max(-INFINITY, completedScore - 35);
                    beta = Math.min(INFINITY, completedScore + 35);
                }
                int score = negamax(root, depth, alpha, beta, 0, true, false);
                if (score <= alpha || score >= beta) {
                    score = negamax(root, depth, -INFINITY, INFINITY, 0, true, false);
                }

                List<Move> iterationPv = principalVariation();
                if (!iterationPv.isEmpty()) completedBest = iterationPv.get(0);
                completedScore = score;
                completedDepth = depth;
                completedPv = iterationPv;
                long elapsed = elapsedMillis();
                infoListener.accept(new SearchInfo(depth, score, nodes, elapsed,
                        table.hashfullPermill(), List.copyOf(iterationPv)));
                if (Math.abs(score) >= MATE_THRESHOLD) break;
            } catch (SearchStopped ignored) {
                break;
            }
        }

        if (completedBest == Move.NONE) {
            List<Move> legal = generator.generateLegalMoves(root);
            if (!legal.isEmpty()) completedBest = legal.get(0);
        }
        return new SearchResult(completedBest, completedScore, completedDepth, nodes,
                elapsedMillis(), List.copyOf(completedPv));
    }

    private int negamax(Position position, int depth, int alpha, int beta, int ply, boolean principalNode, boolean allowNull) {
        checkStop();
        pvLength[ply] = ply;
        if (ply >= MAX_PLY - 1) return evaluator.evaluate(position);
        if (ply > 0 && (position.halfmoveClock() >= 100 || position.isRepetition()
                || position.isInsufficientMaterial())) return 0;

        boolean inCheck = generator.isInCheck(position, position.sideToMove());
        if (depth <= 0) return quiescence(position, alpha, beta, ply);
        if (inCheck && depth < 64) depth++;

        int originalAlpha = alpha;
        int ttIndex = table.find(position.zobristKey());
        int ttMove = ttIndex >= 0 ? table.move(ttIndex) : 0;
        if (ttIndex >= 0 && table.depth(ttIndex) >= depth && !principalNode) {
            int ttScore = scoreFromTable(table.score(ttIndex), ply);
            if (table.flag(ttIndex) == TranspositionTable.EXACT) return ttScore;
            if (table.flag(ttIndex) == TranspositionTable.LOWER_BOUND && ttScore >= beta) return ttScore;
            if (table.flag(ttIndex) == TranspositionTable.UPPER_BOUND && ttScore <= alpha) return ttScore;
        }

        // Null Move Pruning
        if (allowNull && !principalNode && depth >= 3 && !inCheck && !position.isInsufficientMaterial()) {
            int staticEval = evaluator.evaluate(position);
            if (staticEval >= beta) {
                int R = depth > 6 ? 3 : 2;
                Undo nullUndo = new Undo();
                position.makeNullMove(nullUndo);
                int nullScore = -negamax(position, depth - 1 - R, -beta, -beta + 1, ply + 1, false, false);
                position.unmakeNullMove(nullUndo);
                if (nullScore >= beta && Math.abs(nullScore) < MATE_THRESHOLD) {
                    return beta;
                }
            }
        }

        // Futility Pruning Setup
        boolean futilPruning = false;
        if (depth <= 2 && !principalNode && !inCheck && Math.abs(alpha) < MATE_THRESHOLD) {
            int staticEval = evaluator.evaluate(position);
            int margin = depth * 200;
            if (staticEval + margin <= alpha) {
                futilPruning = true;
            }
        }

        List<Move> moves = generator.generateLegalMoves(position);
        if (moves.isEmpty()) return inCheck ? -MATE_SCORE + ply : 0;
        orderMoves(position, moves, ttMove, ply);

        Move bestMove = Move.NONE;
        int bestScore = -INFINITY;
        int moveNumber = 0;
        int side = position.sideToMove();
        Undo undo = new Undo();
        for (Move move : moves) {
            moveNumber++;
            boolean quiet = !move.isCapture() && !move.isPromotion();
            position.makeMove(move, undo);

            // Futility Pruning (skip quiet moves if they don't give check)
            if (futilPruning && quiet && moveNumber > 1) {
                if (!generator.isInCheck(position, side ^ 1)) {
                    position.unmakeMove(move, undo);
                    continue;
                }
            }

            int score;
            if (moveNumber == 1) {
                score = -negamax(position, depth - 1, -beta, -alpha, ply + 1, principalNode, true);
            } else {
                int reduction = quiet && depth >= 3 && moveNumber >= 5 && !inCheck ? 1 : 0;
                score = -negamax(position, depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, false, true);
                if (score > alpha && reduction > 0) {
                    score = -negamax(position, depth - 1, -alpha - 1, -alpha, ply + 1, false, true);
                }
                if (score > alpha && score < beta) {
                    score = -negamax(position, depth - 1, -beta, -alpha, ply + 1, principalNode, true);
                }
            }
            position.unmakeMove(move, undo);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
                pv[ply][ply] = move;
                for (int next = ply + 1; next < pvLength[ply + 1]; next++) pv[ply][next] = pv[ply + 1][next];
                pvLength[ply] = pvLength[ply + 1];
            }
            if (alpha >= beta) {
                if (quiet) {
                    updateKillers(move.encode(), ply);
                    history[side][move.from()][move.to()] += depth * depth;
                }
                table.store(position.zobristKey(), depth, scoreToTable(score, ply),
                        TranspositionTable.LOWER_BOUND, bestMove.encode());
                return score;
            }
        }

        byte flag = alpha > originalAlpha ? TranspositionTable.EXACT : TranspositionTable.UPPER_BOUND;
        table.store(position.zobristKey(), depth, scoreToTable(bestScore, ply), flag, bestMove.encode());
        return bestScore;
    }

    private int quiescence(Position position, int alpha, int beta, int ply) {
        checkStop();
        pvLength[ply] = ply;
        if (ply >= MAX_PLY - 1) return evaluator.evaluate(position);
        if (ply > 0 && (position.halfmoveClock() >= 100 || position.isRepetition()
                || position.isInsufficientMaterial())) return 0;

        boolean inCheck = generator.isInCheck(position, position.sideToMove());
        if (!inCheck) {
            int standPat = evaluator.evaluate(position);
            if (standPat >= beta) return standPat;
            if (standPat > alpha) alpha = standPat;
        }

        List<Move> moves = inCheck
                ? generator.generateLegalMoves(position)
                : generator.generateLegalTacticalMoves(position);
        if (moves.isEmpty()) return inCheck ? -MATE_SCORE + ply : alpha;
        orderMoves(position, moves, 0, ply);

        Undo undo = new Undo();
        for (Move move : moves) {
            if (!inCheck && move.isCapture() && !move.isPromotion()) {
                if (SEE.evaluate(position, move) < 0) continue;
            }

            position.makeMove(move, undo);
            int score = -quiescence(position, -beta, -alpha, ply + 1);
            position.unmakeMove(move, undo);
            if (score >= beta) return score;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private void orderMoves(Position position, List<Move> moves, int ttMove, int ply) {
        int side = position.sideToMove();
        moves.sort(Comparator.comparingInt((Move move) -> moveOrderingScore(position, move, ttMove, ply, side)).reversed());
    }

    private int moveOrderingScore(Position position, Move move, int ttMove, int ply, int side) {
        int encoded = move.encode();
        if (encoded == ttMove) return 2_000_000;
        if (move.isCapture()) {
            int seeScore = SEE.evaluate(position, move);
            if (seeScore >= 0) {
                return 1_000_000 + seeScore;
            } else {
                return -500_000 + seeScore;
            }
        }
        if (move.isPromotion()) return 900_000 + pieceValue(move.promotion());
        if (ply < MAX_PLY) {
            if (killers[ply][0] == encoded) return 800_000;
            if (killers[ply][1] == encoded) return 799_000;
        }
        return history[side][move.from()][move.to()];
    }

    private int pieceValue(int type) {
        return switch (type) {
            case Piece.PAWN -> 100;
            case Piece.KNIGHT -> 320;
            case Piece.BISHOP -> 335;
            case Piece.ROOK -> 500;
            case Piece.QUEEN -> 925;
            case Piece.KING -> 20_000;
            default -> 0;
        };
    }

    private void updateKillers(int move, int ply) {
        if (ply >= MAX_PLY || killers[ply][0] == move) return;
        killers[ply][1] = killers[ply][0];
        killers[ply][0] = move;
    }

    private void ageHistory() {
        for (int color = 0; color < 2; color++) {
            for (int from = 0; from < 64; from++) {
                for (int to = 0; to < 64; to++) history[color][from][to] /= 2;
            }
        }
    }

    private List<Move> principalVariation() {
        List<Move> result = new ArrayList<>();
        for (int index = 0; index < pvLength[0]; index++) {
            if (pv[0][index] != null && pv[0][index] != Move.NONE) result.add(pv[0][index]);
        }
        return result;
    }

    private int scoreToTable(int score, int ply) {
        if (score > MATE_THRESHOLD) return score + ply;
        if (score < -MATE_THRESHOLD) return score - ply;
        return score;
    }

    private int scoreFromTable(int score, int ply) {
        if (score > MATE_THRESHOLD) return score - ply;
        if (score < -MATE_THRESHOLD) return score + ply;
        return score;
    }

    private void checkStop() {
        nodes++;
        if (stopped || nodes >= nodeLimit
                || ((nodes & 1023) == 0 && System.nanoTime() >= deadlineNanos)) {
            throw SearchStopped.INSTANCE;
        }
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static final class SearchStopped extends RuntimeException {
        private static final long serialVersionUID = 1L;
        static final SearchStopped INSTANCE = new SearchStopped();
        private SearchStopped() { super(null, null, false, false); }
    }
}
