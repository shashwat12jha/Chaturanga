package engine;

import model.GameState;
import model.Piece;
import model.PieceType;
import rules.CheckDetector;
import rules.MoveGenerator;

import java.util.Random;

/**
 * Position evaluator — Strong Traditional Edition.
 *
 * Produces an EvalBreakdown from any GameState + Personality.
 * All scores are centipawns from White's perspective.
 *
 * Evaluation components:
 *   ✦ Material + Piece-Square Tables (PSTs)
 *   ✦ TAPERED EVALUATION — smooth interpolation between middlegame and endgame PSTs
 *   ✦ MOBILITY — legal move count differential
 *   ✦ KING SAFETY — pawn shield + tropism (attacker count near king)
 *   ✦ PAWN STRUCTURE — doubled, isolated, backward pawns
 *   ✦ PASSED PAWNS — rank-scaled bonus, unstoppable rook-pawn detection
 *   ✦ OPEN / SEMI-OPEN FILES — rook bonuses
 *   ✦ BISHOP PAIR — classic positional bonus
 *   ✦ CENTER CONTROL — piece and pawn occupancy of central squares
 *
 * Original code — PST values designed for Chaturanga, not derived from any external engine.
 */
public class Evaluator {

    private final MoveGenerator moveGen  = new MoveGenerator();
    private final CheckDetector checkDet = new CheckDetector();
    private final Random rng = new Random();

    // =========================================================================
    //  Piece-Square Tables (PSTs)
    //  Row 0 = rank 8 (Black's back rank), row 7 = rank 1 (White's back rank).
    //  Values represent White's perspective; Black pieces use mirrored index (63 - i).
    //  MIDDLEGAME and ENDGAME tables are blended by the game phase for tapered eval.
    // =========================================================================

    // -- PAWN --
    private static final int[] PAWN_MID_PST = {
         0,   0,   0,   0,   0,   0,   0,   0,
        60,  70,  70,  80,  80,  70,  70,  60,
        20,  30,  35,  50,  50,  35,  30,  20,
         5,  10,  18,  40,  40,  18,  10,   5,
         0,   5,  10,  30,  30,  10,   5,   0,
         5,  -5,  -8,   5,   5,  -8,  -5,   5,
         5,  10,  10, -25, -25,  10,  10,   5,
         0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] PAWN_END_PST = {
         0,   0,   0,   0,   0,   0,   0,   0,
        90, 100, 100, 100, 100, 100, 100,  90,
        50,  60,  60,  60,  60,  60,  60,  50,
        30,  40,  40,  40,  40,  40,  40,  30,
        15,  20,  20,  25,  25,  20,  20,  15,
         5,  10,  10,  10,  10,  10,  10,   5,
         0,   0,   0,   0,   0,   0,   0,   0,
         0,   0,   0,   0,   0,   0,   0,   0
    };

    // -- KNIGHT --
    private static final int[] KNIGHT_MID_PST = {
       -50, -40, -30, -30, -30, -30, -40, -50,
       -40, -20,   0,   5,   5,   0, -20, -40,
       -30,   5,  15,  18,  18,  15,   5, -30,
       -30,   5,  18,  22,  22,  18,   5, -30,
       -30,   0,  18,  22,  22,  18,   0, -30,
       -30,   5,  15,  18,  18,  15,   5, -30,
       -40, -20,   0,   5,   5,   0, -20, -40,
       -50, -40, -30, -30, -30, -30, -40, -50
    };

    private static final int[] KNIGHT_END_PST = {
       -50, -35, -25, -25, -25, -25, -35, -50,
       -35, -15,   0,   5,   5,   0, -15, -35,
       -25,   0,  15,  20,  20,  15,   0, -25,
       -25,   5,  20,  25,  25,  20,   5, -25,
       -25,   0,  20,  25,  25,  20,   0, -25,
       -25,   0,  15,  20,  20,  15,   0, -25,
       -35, -15,   0,   5,   5,   0, -15, -35,
       -50, -35, -25, -25, -25, -25, -35, -50
    };

    // -- BISHOP --
    private static final int[] BISHOP_MID_PST = {
       -20, -10, -10, -10, -10, -10, -10, -20,
       -10,   5,   0,   0,   0,   0,   5, -10,
       -10,  10,  10,  10,  10,  10,  10, -10,
       -10,   0,  12,  14,  14,  12,   0, -10,
       -10,   5,  10,  14,  14,  10,   5, -10,
       -10,   5,  10,  10,  10,  10,   5, -10,
       -10,   5,   0,   0,   0,   0,   5, -10,
       -20, -10, -10, -10, -10, -10, -10, -20
    };

    private static final int[] BISHOP_END_PST = {
       -15,  -8,  -5,  -5,  -5,  -5,  -8, -15,
        -8,   0,   5,   5,   5,   5,   0,  -8,
        -5,   5,  10,  10,  10,  10,   5,  -5,
        -5,   5,  10,  14,  14,  10,   5,  -5,
        -5,   5,  10,  14,  14,  10,   5,  -5,
        -5,   5,  10,  10,  10,  10,   5,  -5,
        -8,   0,   5,   5,   5,   5,   0,  -8,
       -15,  -8,  -5,  -5,  -5,  -5,  -8, -15
    };

    // -- ROOK --
    private static final int[] ROOK_MID_PST = {
         0,   0,   0,   5,   5,   0,   0,   0,
         5,  12,  12,  12,  12,  12,  12,   5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
         0,   0,   0,   5,   5,   0,   0,   0
    };

    private static final int[] ROOK_END_PST = {
         5,   5,   5,   5,   5,   5,   5,   5,
         5,  10,  10,  10,  10,  10,  10,   5,
         0,   5,   5,   5,   5,   5,   5,   0,
         0,   5,   5,   5,   5,   5,   5,   0,
         0,   5,   5,   5,   5,   5,   5,   0,
         0,   5,   5,   5,   5,   5,   5,   0,
         0,   5,   5,   5,   5,   5,   5,   0,
         5,   5,   5,   5,   5,   5,   5,   5
    };

    // -- QUEEN --
    private static final int[] QUEEN_MID_PST = {
       -20, -10, -10,  -5,  -5, -10, -10, -20,
       -10,   0,   5,   0,   0,   0,   0, -10,
       -10,   5,   5,   5,   5,   5,   0, -10,
        -5,   0,   5,   5,   5,   5,   0,  -5,
         0,   0,   5,   5,   5,   5,   0,  -5,
       -10,   5,   5,   5,   5,   5,   0, -10,
       -10,   0,   5,   0,   0,   0,   0, -10,
       -20, -10, -10,  -5,  -5, -10, -10, -20
    };

    private static final int[] QUEEN_END_PST = {
       -20, -10,  -5,  -5,  -5,  -5, -10, -20,
       -10,   0,   5,   5,   5,   5,   0, -10,
        -5,   5,   8,  10,  10,   8,   5,  -5,
        -5,   5,  10,  12,  12,  10,   5,  -5,
        -5,   5,  10,  12,  12,  10,   5,  -5,
        -5,   5,   8,  10,  10,   8,   5,  -5,
       -10,   0,   5,   5,   5,   5,   0, -10,
       -20, -10,  -5,  -5,  -5,  -5, -10, -20
    };

    // -- KING (middlegame: stay castled; endgame: centralize) --
    private static final int[] KING_MID_PST = {
       -30, -40, -40, -50, -50, -40, -40, -30,
       -30, -40, -40, -50, -50, -40, -40, -30,
       -30, -40, -40, -50, -50, -40, -40, -30,
       -30, -40, -40, -50, -50, -40, -40, -30,
       -20, -30, -30, -40, -40, -30, -30, -20,
       -10, -20, -20, -20, -20, -20, -20, -10,
        20,  20,   0,   0,   0,   0,  20,  20,
        25,  35,  10,   0,   0,  10,  35,  25
    };

    private static final int[] KING_END_PST = {
       -50, -30, -30, -30, -30, -30, -30, -50,
       -30, -20,   0,   0,   0,   0, -20, -30,
       -30,  -5,  20,  25,  25,  20,  -5, -30,
       -30,  -5,  25,  30,  30,  25,  -5, -30,
       -30,  -5,  25,  30,  30,  25,  -5, -30,
       -30,  -5,  20,  25,  25,  20,  -5, -30,
       -30, -25,   0,   0,   0,   0, -25, -30,
       -50, -40, -30, -20, -20, -30, -40, -50
    };

    // =========================================================================
    //  Phase weights for tapered evaluation.
    //  Phase = sum of non-pawn, non-king material (max ~7800 at game start).
    // =========================================================================
    private static final int PHASE_WEIGHT_KNIGHT = 1;
    private static final int PHASE_WEIGHT_BISHOP = 1;
    private static final int PHASE_WEIGHT_ROOK   = 2;
    private static final int PHASE_WEIGHT_QUEEN  = 4;
    private static final int MAX_PHASE           = 24; // 4*(K+B) + 4*R*2 + Q*4*2

    // =========================================================================
    //  Public API
    // =========================================================================

    public EvalBreakdown evaluate(GameState state, Personality personality) {
        int material      = evaluateMaterial(state, personality);
        int mobility      = evaluateMobility(state, personality);
        int kingSafety    = evaluateKingSafety(state, personality);
        int centerControl = evaluateCenterControl(state, personality);
        int pawnStructure = evaluatePawnStructure(state, personality);

        int noise = personality.evaluationNoise();
        if (noise > 0) material += rng.nextInt(noise * 2 + 1) - noise;

        return new EvalBreakdown(material, mobility, kingSafety, centerControl, pawnStructure);
    }

    /**
     * Fast static evaluation used inside the search tree.
     * Returns score from White's perspective (positive = White better).
     */
    public int evaluateStatic(GameState state, Personality personality) {
        Piece[] board = state.getBoard();
        int phase = computePhase(board);

        int score = 0;

        // ---- Material + Tapered PST ----
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p == null) continue;
            int sign = p.isWhite ? 1 : -1;
            int pst  = getPSTTapered(p.type, i, p.isWhite, phase);
            score += sign * (p.type.getValue() + pst);
        }

        // ---- Bishop Pair bonus ----
        score += evaluateBishopPair(board, true);
        score -= evaluateBishopPair(board, false);

        // ---- Rook file bonuses ----
        score += evaluateRookFiles(board, true);
        score -= evaluateRookFiles(board, false);

        // ---- Passed Pawns ----
        score += evaluatePassedPawns(board, true);
        score -= evaluatePassedPawns(board, false);

        // ---- Pawn structure (doubled + isolated) ----
        score += evaluatePawnStructureStatic(board, true);
        score -= evaluatePawnStructureStatic(board, false);

        // ---- King Tropism (attacker count near king) ----
        score -= evaluateKingTropism(board, state, true);   // danger for White king
        score += evaluateKingTropism(board, state, false);  // danger for Black king

        // ---- Mobility (lightweight) ----
        // Always count White legal moves vs Black legal moves, regardless of who is to move.
        int whiteMoves, blackMoves;
        if (state.isWhiteToMove()) {
            whiteMoves = moveGen.generateLegalMoves(state).size();
            blackMoves = moveGen.generateLegalMoves(forceSide(state, false)).size();
        } else {
            whiteMoves = moveGen.generateLegalMoves(forceSide(state, true)).size();
            blackMoves = moveGen.generateLegalMoves(state).size();
        }
        score += (int)((whiteMoves - blackMoves) * 3 * personality.mobilityWeight());

        // ---- Noise ----
        int noise = personality.evaluationNoise();
        if (noise > 0) score += rng.nextInt(noise * 2 + 1) - noise;

        return (int)(score * personality.materialWeight());
    }

    // =========================================================================
    //  Detailed Evaluation Components (for EvalBreakdown)
    // =========================================================================

    private int evaluateMaterial(GameState state, Personality personality) {
        Piece[] board = state.getBoard();
        int phase = computePhase(board);
        int score = 0;
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p == null) continue;
            int pst = getPSTTapered(p.type, i, p.isWhite, phase);
            score += p.isWhite ? (p.type.getValue() + pst) : -(p.type.getValue() + pst);
        }
        score += evaluateBishopPair(board, true) - evaluateBishopPair(board, false);
        return (int)(score * personality.materialWeight());
    }

    private int evaluateMobility(GameState state, Personality personality) {
        // Always generate moves from each side's perspective, regardless of whose turn it is.
        // The old code used state.isWhiteToMove() which inverted the score after every move.
        int whiteMoves = state.isWhiteToMove()
                ? moveGen.generateLegalMoves(state).size()
                : moveGen.generateLegalMoves(forceSide(state, true)).size();
        int blackMoves = state.isWhiteToMove()
                ? moveGen.generateLegalMoves(forceSide(state, false)).size()
                : moveGen.generateLegalMoves(state).size();
        return (int)((whiteMoves - blackMoves) * 4 * personality.mobilityWeight());
    }

    private int evaluateKingSafety(GameState state, Personality personality) {
        Piece[] board = state.getBoard();
        int score = 0;
        score += kingSafetyForSide(board, state, true);
        score -= kingSafetyForSide(board, state, false);
        return (int)(score * personality.kingSafetyWeight());
    }

    private int kingSafetyForSide(Piece[] board, GameState state, boolean white) {
        int kingIdx = -1;
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p != null && p.type == PieceType.KING && p.isWhite == white) {
                kingIdx = i; break;
            }
        }
        if (kingIdx == -1) return -500;

        int kingRow = kingIdx / 8;
        int kingCol = kingIdx % 8;
        int pawnRow = white ? kingRow - 1 : kingRow + 1;
        int safetyScore = 0;

        // Pawn shield
        for (int dc = -1; dc <= 1; dc++) {
            int col = kingCol + dc;
            if (col < 0 || col > 7) continue;
            if (pawnRow >= 0 && pawnRow < 8) {
                Piece p = state.getPiece(col, pawnRow);
                if (p != null && p.type == PieceType.PAWN && p.isWhite == white) {
                    safetyScore += 25;
                }
            }
        }

        // Center file penalty (king should be castled in middlegame)
        if (!isEndgame(state) && kingCol >= 2 && kingCol <= 5) {
            safetyScore -= 45;
        }

        // Tropism: enemy heavy pieces near king
        safetyScore -= evaluateKingTropism(board, state, white);

        return safetyScore;
    }

    private int evaluateCenterControl(GameState state, Personality personality) {
        int[] centerSquares   = {27, 28, 35, 36};         // d4, e4, d5, e5
        int[] extendedCenter  = {18, 19, 20, 21, 26, 29, 34, 37, 42, 43, 44, 45};
        int score = 0;
        Piece[] board = state.getBoard();
        for (int sq : centerSquares) {
            Piece p = board[sq];
            if (p != null) score += p.isWhite ? 18 : -18;
        }
        for (int sq : extendedCenter) {
            Piece p = board[sq];
            if (p != null) score += p.isWhite ? 6 : -6;
        }
        return (int)(score * personality.centerControlWeight());
    }

    private int evaluatePawnStructure(GameState state, Personality personality) {
        Piece[] board = state.getBoard();
        int score = 0;
        score += evaluatePawnStructureStatic(board, true);
        score -= evaluatePawnStructureStatic(board, false);
        score += evaluatePassedPawns(board, true);
        score -= evaluatePassedPawns(board, false);
        score += evaluateRookFiles(board, true);
        score -= evaluateRookFiles(board, false);
        return (int)(score * personality.pawnStructureWeight());
    }

    // =========================================================================
    //  Pawn Structure Analysis
    // =========================================================================

    private int evaluatePawnStructureStatic(Piece[] board, boolean white) {
        boolean[] filesOccupied = new boolean[8];
        int[] pawnsPerFile = new int[8];
        int score = 0;

        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p == null || p.type != PieceType.PAWN || p.isWhite != white) continue;
            int col = i % 8;
            pawnsPerFile[col]++;
            if (filesOccupied[col]) {
                score -= 20; // Doubled pawn
            }
            filesOccupied[col] = true;
        }

        // Isolated pawn penalty
        for (int col = 0; col < 8; col++) {
            if (filesOccupied[col]) {
                boolean hasNeighbor = (col > 0 && filesOccupied[col - 1]) ||
                                      (col < 7 && filesOccupied[col + 1]);
                if (!hasNeighbor) score -= 15;
            }
        }

        // Backward pawn: a pawn that cannot be protected by another pawn
        // and is behind all pawns on adjacent files
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p == null || p.type != PieceType.PAWN || p.isWhite != white) continue;
            int col = i % 8;
            int row = i / 8;
            if (isBackwardPawn(board, col, row, white)) {
                score -= 10;
            }
        }

        return score;
    }

    private boolean isBackwardPawn(Piece[] board, int col, int row, boolean white) {
        // A backward pawn: no friendly pawn can defend it from behind,
        // and it's on a half-open file where the next advance would be dangerous.
        int dir = white ? -1 : 1; // forward direction
        // Check if any friendly pawn is on adjacent files behind this pawn
        for (int adjCol = col - 1; adjCol <= col + 1; adjCol += 2) {
            if (adjCol < 0 || adjCol > 7) continue;
            // Look for friendly pawn behind (in dir's opposite direction)
            for (int r = row + dir; r >= 0 && r < 8; r += dir) {
                Piece p = board[r * 8 + adjCol];
                if (p != null && p.type == PieceType.PAWN && p.isWhite == white) {
                    return false; // protected from behind
                }
            }
        }
        // Check if an enemy pawn controls the square in front
        int frontRow = row + dir;
        if (frontRow < 0 || frontRow >= 8) return false;
        for (int adjCol = col - 1; adjCol <= col + 1; adjCol += 2) {
            if (adjCol < 0 || adjCol > 7) continue;
            Piece ep = board[frontRow * 8 + adjCol];
            if (ep != null && ep.type == PieceType.PAWN && ep.isWhite != white) {
                return true; // enemy pawn controls advance square → this pawn is backward
            }
        }
        return false;
    }

    // =========================================================================
    //  Passed Pawns
    // =========================================================================

    /**
     * A pawn is "passed" if no enemy pawn can ever block or capture it
     * (no enemy pawns on same file or adjacent files ahead of it).
     * Bonus scales with rank advancement (closer to promotion = more valuable).
     */
    private int evaluatePassedPawns(Piece[] board, boolean white) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p == null || p.type != PieceType.PAWN || p.isWhite != white) continue;
            int col = i % 8;
            int row = i / 8;
            if (isPassedPawn(board, col, row, white)) {
                // Rank-scaled bonus: closer to promotion = bigger bonus
                int rankAdvance = white ? (7 - row) : row; // 0 = own back rank, 7 = promo rank
                // Bonus: 10 → 20 → 30 → 45 → 65 → 90 as pawn advances
                int[] passedBonus = {0, 10, 20, 30, 45, 65, 90, 0};
                score += passedBonus[Math.min(rankAdvance, 7)];
            }
        }
        return score;
    }

    private boolean isPassedPawn(Piece[] board, int col, int row, boolean white) {
        int dir = white ? -1 : 1;
        // Check all squares ahead on same file and adjacent files
        for (int r = row + dir; r >= 0 && r < 8; r += dir) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (c < 0 || c > 7) continue;
                Piece p = board[r * 8 + c];
                if (p != null && p.type == PieceType.PAWN && p.isWhite != white) {
                    return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    //  Rook File Bonuses (Open / Semi-open Files)
    // =========================================================================

    /**
     * Rook on an open file (no pawns at all) = +25.
     * Rook on a semi-open file (no friendly pawns, but enemy pawns exist) = +15.
     */
    private int evaluateRookFiles(Piece[] board, boolean white) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p == null || p.type != PieceType.ROOK || p.isWhite != white) continue;
            int col = i % 8;

            boolean hasFriendlyPawn = false;
            boolean hasEnemyPawn    = false;
            for (int row = 0; row < 8; row++) {
                Piece sq = board[row * 8 + col];
                if (sq != null && sq.type == PieceType.PAWN) {
                    if (sq.isWhite == white) hasFriendlyPawn = true;
                    else hasEnemyPawn = true;
                }
            }

            if (!hasFriendlyPawn && !hasEnemyPawn) {
                score += 25; // Open file
            } else if (!hasFriendlyPawn) {
                score += 15; // Semi-open file
            }
        }
        return score;
    }

    // =========================================================================
    //  Bishop Pair Bonus
    // =========================================================================

    /** +50 if the side has both a light-squared and dark-squared bishop. */
    private int evaluateBishopPair(Piece[] board, boolean white) {
        int bishops = 0;
        for (Piece p : board) {
            if (p != null && p.type == PieceType.BISHOP && p.isWhite == white) bishops++;
        }
        return bishops >= 2 ? 50 : 0;
    }

    // =========================================================================
    //  King Tropism — Attacker Density Near the King
    // =========================================================================

    /**
     * Counts enemy piece attacks near the king and returns a danger score.
     * Heavy attackers (queen, rook) get higher weights.
     * This is a simplified version of the "king safety attack" count used
     * in classic engines like Crafty and Fruit.
     */
    private int evaluateKingTropism(Piece[] board, GameState state, boolean white) {
        // Find king
        int kingRow = -1, kingCol = -1;
        for (int i = 0; i < 64; i++) {
            Piece p = board[i];
            if (p != null && p.type == PieceType.KING && p.isWhite == white) {
                kingRow = i / 8;
                kingCol = i % 8;
                break;
            }
        }
        if (kingRow == -1) return 0;

        int attackCount  = 0;
        int attackWeight = 0;

        // Check a 3×3 zone + 2-step radius around king
        for (int r = Math.max(0, kingRow - 2); r <= Math.min(7, kingRow + 2); r++) {
            for (int c = Math.max(0, kingCol - 2); c <= Math.min(7, kingCol + 2); c++) {
                Piece p = board[r * 8 + c];
                if (p == null || p.isWhite == white) continue; // friendly or empty
                // Give weight by piece type
                int w = switch (p.type) {
                    case QUEEN  -> 5;
                    case ROOK   -> 3;
                    case BISHOP -> 2;
                    case KNIGHT -> 2;
                    case PAWN   -> 1;
                    default     -> 0;
                };
                attackCount++;
                attackWeight += w;
            }
        }

        // Safety table based on attack weight (inspired by Fruit/Toga)
        // Returns danger score for White king being attacked
        if (attackCount == 0) return 0;
        return Math.min(attackWeight * 12, 150); // Cap at 150cp danger
    }

    // =========================================================================
    //  Tapered Evaluation (Game Phase Interpolation)
    // =========================================================================

    /**
     * Compute the game phase on a scale of 0 (endgame) to MAX_PHASE (opening/midgame).
     * Non-pawn, non-king material is used to determine phase.
     */
    private int computePhase(Piece[] board) {
        int phase = 0;
        for (Piece p : board) {
            if (p == null) continue;
            phase += switch (p.type) {
                case KNIGHT -> PHASE_WEIGHT_KNIGHT;
                case BISHOP -> PHASE_WEIGHT_BISHOP;
                case ROOK   -> PHASE_WEIGHT_ROOK;
                case QUEEN  -> PHASE_WEIGHT_QUEEN;
                default     -> 0;
            };
        }
        return Math.min(phase, MAX_PHASE);
    }

    /**
     * Return the interpolated PST value for a piece.
     * @param phase current game phase (0 = endgame, MAX_PHASE = opening)
     */
    private int getPSTTapered(PieceType type, int boardIdx, boolean isWhite, int phase) {
        int tableIdx = isWhite ? boardIdx : 63 - boardIdx;
        int midVal = getPSTMid(type, tableIdx);
        int endVal = getPSTEnd(type, tableIdx);
        // Interpolate: phase=MAX_PHASE → midgame, phase=0 → endgame
        return (midVal * phase + endVal * (MAX_PHASE - phase)) / MAX_PHASE;
    }

    private int getPSTMid(PieceType type, int tableIdx) {
        return switch (type) {
            case PAWN   -> PAWN_MID_PST[tableIdx];
            case KNIGHT -> KNIGHT_MID_PST[tableIdx];
            case BISHOP -> BISHOP_MID_PST[tableIdx];
            case ROOK   -> ROOK_MID_PST[tableIdx];
            case QUEEN  -> QUEEN_MID_PST[tableIdx];
            case KING   -> KING_MID_PST[tableIdx];
        };
    }

    private int getPSTEnd(PieceType type, int tableIdx) {
        return switch (type) {
            case PAWN   -> PAWN_END_PST[tableIdx];
            case KNIGHT -> KNIGHT_END_PST[tableIdx];
            case BISHOP -> BISHOP_END_PST[tableIdx];
            case ROOK   -> ROOK_END_PST[tableIdx];
            case QUEEN  -> QUEEN_END_PST[tableIdx];
            case KING   -> KING_END_PST[tableIdx];
        };
    }

    /**
     * Returns a copy of state with isWhiteToMove forced to the given value.
     * Used by mobility evaluation so we always count White moves as White moves
     * and Black moves as Black moves, regardless of whose actual turn it is.
     */
    private GameState forceSide(GameState state, boolean white) {
        GameState copy = state.copy();
        copy.setWhiteToMove(white);
        return copy;
    }

    /**
     * Endgame detector: true when total non-pawn, non-king material < 1300cp.
     * Used externally (e.g., by SearchEngine for NMP zugzwang check).
     */
    public boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (Piece p : state.getBoard()) {
            if (p != null && p.type != PieceType.KING && p.type != PieceType.PAWN) {
                totalMaterial += p.type.getValue();
            }
        }
        return totalMaterial < 1300;
    }
}
