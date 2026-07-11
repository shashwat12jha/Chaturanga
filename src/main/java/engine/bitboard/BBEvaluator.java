package engine.bitboard;

import engine.EvalBreakdown;
import engine.Personality;

/**
 * Fast positional evaluator using bitboards.
 *
 * Replaces the loop-over-64-squares approach with popcount / bit-scan operations
 * on piece bitboards — O(pieces) not O(squares).
 *
 * Evaluation components:
 *   ✦ Material (per-piece values)
 *   ✦ Piece-Square Tables — tapered (MG/EG blended by game phase)
 *   ✦ Mobility — pseudo-legal move count differential
 *   ✦ Pawn structure — doubled, isolated, passed pawns
 *   ✦ King safety — pawn shield score
 *   ✦ Bishop pair bonus
 *   ✦ Open/semi-open file bonus for rooks
 *
 * Returns centipawns from White's perspective.
 */
public final class BBEvaluator {

    // ---- Material values (centipawns) ----
    private static final int VAL_PAWN   = 100;
    private static final int VAL_KNIGHT = 320;
    private static final int VAL_BISHOP = 330;
    private static final int VAL_ROOK   = 500;
    private static final int VAL_QUEEN  = 900;

    /** Piece value by internal piece-type index (0-5 = W, 6-11 = B). */
    public static int pieceValue(int idx) {
        switch (idx % 6) {
            case 0: return VAL_PAWN;
            case 1: return VAL_KNIGHT;
            case 2: return VAL_BISHOP;
            case 3: return VAL_ROOK;
            case 4: return VAL_QUEEN;
            default: return 0; // King
        }
    }

    // =========================================================================
    //  Piece-Square Tables (PSTs)
    //  Row 0 = rank 8 (Black's back rank); values from White's perspective.
    //  Black pieces use mirrored index (63 - sq).
    // =========================================================================

    private static final int[] PAWN_MID = {
         0,   0,   0,   0,   0,   0,   0,   0,
        60,  70,  70,  80,  80,  70,  70,  60,
        20,  30,  35,  50,  50,  35,  30,  20,
         5,  10,  18,  40,  40,  18,  10,   5,
         0,   5,  10,  30,  30,  10,   5,   0,
         5,  -5,  -8,   5,   5,  -8,  -5,   5,
         5,  10,  10, -25, -25,  10,  10,   5,
         0,   0,   0,   0,   0,   0,   0,   0
    };
    private static final int[] PAWN_END = {
         0,   0,   0,   0,   0,   0,   0,   0,
        90, 100, 100, 100, 100, 100, 100,  90,
        50,  60,  60,  60,  60,  60,  60,  50,
        30,  40,  40,  40,  40,  40,  40,  30,
        15,  20,  20,  25,  25,  20,  20,  15,
         5,  10,  10,  10,  10,  10,  10,   5,
         0,   0,   0,   0,   0,   0,   0,   0,
         0,   0,   0,   0,   0,   0,   0,   0
    };
    private static final int[] KNIGHT_MID = {
       -50, -40, -30, -30, -30, -30, -40, -50,
       -40, -20,   0,   5,   5,   0, -20, -40,
       -30,   5,  15,  18,  18,  15,   5, -30,
       -30,   5,  18,  22,  22,  18,   5, -30,
       -30,   0,  18,  22,  22,  18,   0, -30,
       -30,   5,  15,  18,  18,  15,   5, -30,
       -40, -20,   0,   5,   5,   0, -20, -40,
       -50, -40, -30, -30, -30, -30, -40, -50
    };
    private static final int[] KNIGHT_END = {
       -50, -35, -25, -25, -25, -25, -35, -50,
       -35, -15,   0,   5,   5,   0, -15, -35,
       -25,   0,  15,  20,  20,  15,   0, -25,
       -25,   5,  20,  25,  25,  20,   5, -25,
       -25,   0,  20,  25,  25,  20,   0, -25,
       -25,   0,  15,  20,  20,  15,   0, -25,
       -35, -15,   0,   5,   5,   0, -15, -35,
       -50, -35, -25, -25, -25, -25, -35, -50
    };
    private static final int[] BISHOP_MID = {
       -20, -10, -10, -10, -10, -10, -10, -20,
       -10,   5,   0,   0,   0,   0,   5, -10,
       -10,  10,  10,  10,  10,  10,  10, -10,
       -10,   0,  12,  14,  14,  12,   0, -10,
       -10,   5,  10,  14,  14,  10,   5, -10,
       -10,   5,  10,  10,  10,  10,   5, -10,
       -10,   5,   0,   0,   0,   0,   5, -10,
       -20, -10, -10, -10, -10, -10, -10, -20
    };
    private static final int[] BISHOP_END = {
       -14,  -8,  -8,  -8,  -8,  -8,  -8, -14,
        -8,   0,   0,   0,   0,   0,   0,  -8,
        -8,   0,   5,   5,   5,   5,   0,  -8,
        -8,   0,   5,  10,  10,   5,   0,  -8,
        -8,   0,   5,  10,  10,   5,   0,  -8,
        -8,   0,   5,   5,   5,   5,   0,  -8,
        -8,   0,   0,   0,   0,   0,   0,  -8,
       -14,  -8,  -8,  -8,  -8,  -8,  -8, -14
    };
    private static final int[] ROOK_MID = {
         0,   0,   0,   5,   5,   0,   0,   0,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
         5,  10,  10,  10,  10,  10,  10,   5,
         0,   0,   0,   0,   0,   0,   0,   0
    };
    private static final int[] ROOK_END = {
         5,   5,   5,   5,   5,   5,   5,   5,
        10,  10,  10,  10,  10,  10,  10,  10,
         0,   0,   0,   0,   0,   0,   0,   0,
         0,   0,   0,   0,   0,   0,   0,   0,
         0,   0,   0,   0,   0,   0,   0,   0,
         0,   0,   0,   0,   0,   0,   0,   0,
         0,   0,   0,   0,   0,   0,   0,   0,
         5,   5,   5,   5,   5,   5,   5,   5
    };
    private static final int[] QUEEN_MID = {
       -20, -10, -10,  -5,  -5, -10, -10, -20,
       -10,   0,   5,   0,   0,   0,   0, -10,
       -10,   5,   5,   5,   5,   5,   0, -10,
        -5,   0,   5,   5,   5,   5,   0,  -5,
         0,   0,   5,   5,   5,   5,   0,  -5,
       -10,   5,   5,   5,   5,   5,   0, -10,
       -10,   0,   5,   0,   0,   0,   0, -10,
       -20, -10, -10,  -5,  -5, -10, -10, -20
    };
    private static final int[] QUEEN_END = {
       -20, -10, -10,  -5,  -5, -10, -10, -20,
       -10,   0,   0,   0,   0,   0,   0, -10,
       -10,   0,   5,   5,   5,   5,   0, -10,
        -5,   0,   5,   8,   8,   5,   0,  -5,
        -5,   0,   5,   8,   8,   5,   0,  -5,
       -10,   0,   5,   5,   5,   5,   0, -10,
       -10,   0,   0,   0,   0,   0,   0, -10,
       -20, -10, -10,  -5,  -5, -10, -10, -20
    };
    private static final int[] KING_MID = {
        20,  30,  10,   0,   0,  10,  30,  20,
        20,  20,   0,   0,   0,   0,  20,  20,
       -10, -20, -20, -20, -20, -20, -20, -10,
       -20, -30, -30, -40, -40, -30, -30, -20,
       -30, -40, -40, -50, -50, -40, -40, -30,
       -30, -40, -40, -50, -50, -40, -40, -30,
       -30, -40, -40, -50, -50, -40, -40, -30,
       -30, -40, -40, -50, -50, -40, -40, -30
    };
    private static final int[] KING_END = {
       -50, -40, -30, -20, -20, -30, -40, -50,
       -30, -20, -10,   0,   0, -10, -20, -30,
       -30, -10,  20,  30,  30,  20, -10, -30,
       -30, -10,  30,  40,  40,  30, -10, -30,
       -30, -10,  30,  40,  40,  30, -10, -30,
       -30, -10,  20,  30,  30,  20, -10, -30,
       -30, -30,   0,   0,   0,   0, -30, -30,
       -50, -30, -30, -30, -30, -30, -30, -50
    };

    private static final int[][] PST_MID = { PAWN_MID, KNIGHT_MID, BISHOP_MID, ROOK_MID, QUEEN_MID, KING_MID };
    private static final int[][] PST_END = { PAWN_END, KNIGHT_END, BISHOP_END, ROOK_END, QUEEN_END, KING_END };

    // =========================================================================
    //  Main evaluate entry point
    // =========================================================================

    /**
     * Evaluate the position from White's perspective (centipawns).
     * Positive = White advantage.
     */
    public int evaluate(BBPosition pos, Personality personality) {
        int mgScore = 0, egScore = 0;
        int mgPhase = 0; // game phase accumulator

        // ---- Material + PST ----
        for (int idx = 0; idx < 12; idx++) {
            long bb = pos.pieces[idx];
            if (bb == 0L) continue;

            boolean isWhite = idx < 6;
            int typeIdx = idx % 6; // 0=P,1=N,2=B,3=R,4=Q,5=K
            int sign    = isWhite ? 1 : -1;
            int[] mgPST = PST_MID[typeIdx];
            int[] egPST = PST_END[typeIdx];

            // Game phase weight: P=0, N=1, B=1, R=2, Q=4, K=0
            int phaseWeight = typeIdx == 1 ? 1 : typeIdx == 2 ? 1 : typeIdx == 3 ? 2 : typeIdx == 4 ? 4 : 0;

            while (bb != 0L) {
                int sq = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
                int pstIdx = isWhite ? sq : (63 - sq);

                mgScore += sign * (pieceValue(idx) + mgPST[pstIdx]);
                egScore += sign * (pieceValue(idx) + egPST[pstIdx]);
                mgPhase += phaseWeight;
            }
        }

        // ---- Tapered eval ----
        int maxPhase = 2*1 + 2*1 + 2*2 + 1*4; // = 12 per side = 24 total
        int phase = Math.min(mgPhase, maxPhase * 2);
        // score = (mg * phase + eg * (maxPhase*2 - phase)) / (maxPhase*2)
        int taperedScore = (mgScore * phase + egScore * (maxPhase * 2 - phase)) / (maxPhase * 2);

        // ---- Mobility ----
        int mobility = evalMobility(pos, personality);

        // ---- Pawn structure ----
        int pawnStruct = evalPawnStructure(pos, personality);

        // ---- King safety ----
        int kingSafety = evalKingSafety(pos, personality);

        // ---- Bishop pair ----
        int bishopPair = evalBishopPair(pos);

        // ---- Rook open files ----
        int rookFiles = evalRookOpenFiles(pos);

        int total = (int)(taperedScore * personality.materialWeight())
                  + (int)(mobility     * personality.mobilityWeight())
                  + (int)(pawnStruct   * personality.pawnStructureWeight())
                  + (int)(kingSafety   * personality.kingSafetyWeight())
                  + bishopPair + rookFiles;

        // Small noise for non-balanced personalities
        total += personality.evaluationNoise();

        return total;
    }

    // =========================================================================
    //  Mobility
    // =========================================================================

    private int evalMobility(BBPosition pos, Personality personality) {
        long occ = pos.allPieces;
        int[] moves = new int[256];
        
        // Count pseudo-legal moves for each side
        // White
        int whiteMobility = 0;
        whiteMobility += countAttackTargets(pos.pieces[BBPosition.W_KNIGHT], occ, pos.whitePieces, false, occ);
        whiteMobility += countAttackTargets(pos.pieces[BBPosition.W_BISHOP], occ, pos.whitePieces, true,  occ);
        whiteMobility += countAttackTargets(pos.pieces[BBPosition.W_ROOK],   occ, pos.whitePieces, false, occ);
        whiteMobility += countAttackTargets(pos.pieces[BBPosition.W_QUEEN],  occ, pos.whitePieces, true,  occ);

        // Black  
        int blackMobility = 0;
        blackMobility += countAttackTargets(pos.pieces[BBPosition.B_KNIGHT], occ, pos.blackPieces, false, occ);
        blackMobility += countAttackTargets(pos.pieces[BBPosition.B_BISHOP], occ, pos.blackPieces, true,  occ);
        blackMobility += countAttackTargets(pos.pieces[BBPosition.B_ROOK],   occ, pos.blackPieces, false, occ);
        blackMobility += countAttackTargets(pos.pieces[BBPosition.B_QUEEN],  occ, pos.blackPieces, true,  occ);

        return (whiteMobility - blackMobility) * 4;
    }

    private int countAttackTargets(long pieceBB, long occ, long ownPieces, boolean diagonal, long fullOcc) {
        int count = 0;
        while (pieceBB != 0L) {
            int sq = Bitboard.lsb(pieceBB); pieceBB = Bitboard.popLsb(pieceBB);
            long attacks = diagonal ? BBAttacks.bishopAttacks(sq, fullOcc) : BBAttacks.rookAttacks(sq, fullOcc);
            // For bishops with combined rook/bishop (queen): add both
            // This is used for individual piece types so the parameter `diagonal` guides us
            attacks &= ~ownPieces;
            count += Bitboard.popcount(attacks);
        }
        return count;
    }

    // =========================================================================
    //  Pawn structure
    // =========================================================================

    private int evalPawnStructure(BBPosition pos, Personality personality) {
        long wPawns = pos.pieces[BBPosition.W_PAWN];
        long bPawns = pos.pieces[BBPosition.B_PAWN];
        int score   = 0;

        // ---- Doubled pawns ----
        for (int f = 0; f < 8; f++) {
            long fileMask = fileMask(f);
            int wCount = Bitboard.popcount(wPawns & fileMask);
            int bCount = Bitboard.popcount(bPawns & fileMask);
            if (wCount > 1) score -= 15 * (wCount - 1);
            if (bCount > 1) score += 15 * (bCount - 1);
        }

        // ---- Isolated pawns ----
        for (int f = 0; f < 8; f++) {
            long neighborMask = adjacentFilesMask(f);
            if ((wPawns & fileMask(f)) != 0 && (wPawns & neighborMask) == 0) score -= 20;
            if ((bPawns & fileMask(f)) != 0 && (bPawns & neighborMask) == 0) score += 20;
        }

        // ---- Passed pawns ----
        // White passed: no black pawns ahead (lower row) on same or adjacent files
        long wPassedBB = wPawns;
        long bPassedBB = bPawns;
        long wPawnsCopy = wPawns;
        while (wPawnsCopy != 0L) {
            int sq = Bitboard.lsb(wPawnsCopy); wPawnsCopy = Bitboard.popLsb(wPawnsCopy);
            if (isPassedPawn(sq, true, bPawns)) {
                int rank = 7 - Bitboard.row(sq); // rank from white's back rank (1-7)
                score += rank * rank * 3; // exponential bonus for advanced passed pawns
            }
        }
        long bPawnsCopy = bPawns;
        while (bPawnsCopy != 0L) {
            int sq = Bitboard.lsb(bPawnsCopy); bPawnsCopy = Bitboard.popLsb(bPawnsCopy);
            if (isPassedPawn(sq, false, wPawns)) {
                int rank = Bitboard.row(sq); // rank from black's back rank (1-7)
                score -= rank * rank * 3;
            }
        }

        return (int)(score * personality.pawnStructureWeight());
    }

    private boolean isPassedPawn(int sq, boolean white, long opponentPawns) {
        int col = Bitboard.col(sq);
        int row = Bitboard.row(sq);
        // For a white pawn, no opponent pawns should exist in the "passed pawn span" — rows 0..row-1, cols col-1..col+1
        long span = 0L;
        if (white) {
            for (int r = 0; r < row; r++) {
                for (int c = Math.max(0, col-1); c <= Math.min(7, col+1); c++) span |= Bitboard.bit(r*8+c);
            }
        } else {
            for (int r = row + 1; r < 8; r++) {
                for (int c = Math.max(0, col-1); c <= Math.min(7, col+1); c++) span |= Bitboard.bit(r*8+c);
            }
        }
        return (opponentPawns & span) == 0L;
    }

    // =========================================================================
    //  King safety
    // =========================================================================

    private int evalKingSafety(BBPosition pos, Personality personality) {
        int score = 0;

        // White king pawn shield (king at its square, count pawns within 1-2 squares in front)
        int wKingSq = pos.kingSquare(true);
        int bKingSq = pos.kingSquare(false);

        if (wKingSq >= 0) {
            long shield = BBAttacks.KING_ATTACKS[wKingSq] & Bitboard.shiftN(Bitboard.bit(wKingSq));
            shield |= Bitboard.shiftN(shield); // two ranks in front
            int shieldPawns = Bitboard.popcount(pos.pieces[BBPosition.W_PAWN] & shield);
            score += shieldPawns * 15;
        }
        if (bKingSq >= 0) {
            long shield = BBAttacks.KING_ATTACKS[bKingSq] & Bitboard.shiftS(Bitboard.bit(bKingSq));
            shield |= Bitboard.shiftS(shield);
            int shieldPawns = Bitboard.popcount(pos.pieces[BBPosition.B_PAWN] & shield);
            score -= shieldPawns * 15;
        }

        return (int)(score * personality.kingSafetyWeight());
    }

    // =========================================================================
    //  Bishop pair
    // =========================================================================

    private int evalBishopPair(BBPosition pos) {
        int score = 0;
        if (Bitboard.popcount(pos.pieces[BBPosition.W_BISHOP]) >= 2) score += 30;
        if (Bitboard.popcount(pos.pieces[BBPosition.B_BISHOP]) >= 2) score -= 30;
        return score;
    }

    // =========================================================================
    //  Rook open file bonus
    // =========================================================================

    private int evalRookOpenFiles(BBPosition pos) {
        int score = 0;
        long wRooks = pos.pieces[BBPosition.W_ROOK];
        long bRooks = pos.pieces[BBPosition.B_ROOK];
        long wPawns = pos.pieces[BBPosition.W_PAWN];
        long bPawns = pos.pieces[BBPosition.B_PAWN];

        long r = wRooks;
        while (r != 0L) {
            int sq = Bitboard.lsb(r); r = Bitboard.popLsb(r);
            long fm = fileMask(Bitboard.col(sq));
            if ((wPawns & fm) == 0) score += (bPawns & fm) == 0 ? 20 : 10; // open / semi-open
        }
        r = bRooks;
        while (r != 0L) {
            int sq = Bitboard.lsb(r); r = Bitboard.popLsb(r);
            long fm = fileMask(Bitboard.col(sq));
            if ((bPawns & fm) == 0) score -= (wPawns & fm) == 0 ? 20 : 10;
        }
        return score;
    }

    // =========================================================================
    //  Convenience — EvalBreakdown wrapper for GUI compatibility
    // =========================================================================

    public EvalBreakdown evaluateBreakdown(BBPosition pos, Personality personality) {
        int total = evaluate(pos, personality);
        // Simple breakdown (detailed per-component scoring can be added later)
        return new EvalBreakdown(total, 0, 0, 0, 0);
    }

    // =========================================================================
    //  Bit helpers
    // =========================================================================

    private static long fileMask(int f) {
        return 0x0101010101010101L << f;
    }

    private static long adjacentFilesMask(int f) {
        long mask = 0L;
        if (f > 0) mask |= fileMask(f - 1);
        if (f < 7) mask |= fileMask(f + 1);
        return mask;
    }
}
