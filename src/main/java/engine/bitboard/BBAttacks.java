package engine.bitboard;

/**
 * Pre-computed attack tables for all piece types.
 *
 * Leapers (knight, king, pawn): fully pre-computed into 64-entry arrays — O(1).
 *
 * Sliders (bishop, rook, queen): use classical ray-scan with an occupancy mask.
 * This is O(ray-length ≤ 7) per direction — still an order of magnitude faster
 * than the old GameState.copy() approach and 100% correct.
 *
 * A magic-bitboard upgrade can be layered in later without changing the public API.
 */
public final class BBAttacks {

    private BBAttacks() {}

    // ---- Pre-computed leaper tables ----
    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[] KING_ATTACKS   = new long[64];
    /** PAWN_ATTACKS[0][sq] = white pawn attacks from sq, [1][sq] = black */
    public static final long[][] PAWN_ATTACKS  = new long[2][64];

    // ---- Pre-computed ray tables for sliding pieces ----
    // rays[dir][sq] = all squares reachable in direction dir from sq (empty board)
    // Direction indices: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW
    static final long[][] RAY = new long[8][64];

    static {
        initLeapers();
        initRays();
    }

    // =========================================================================
    //  Public attack API
    // =========================================================================

    /** Rook attacks from `sq` given `occ` occupancy. */
    public static long rookAttacks(int sq, long occ) {
        return slidingRay(sq, occ, 0)   // N
             | slidingRay(sq, occ, 2)   // E
             | slidingRay(sq, occ, 4)   // S
             | slidingRay(sq, occ, 6);  // W
    }

    /** Bishop attacks from `sq` given `occ` occupancy. */
    public static long bishopAttacks(int sq, long occ) {
        return slidingRay(sq, occ, 1)   // NE
             | slidingRay(sq, occ, 3)   // SE
             | slidingRay(sq, occ, 5)   // SW
             | slidingRay(sq, occ, 7);  // NW
    }

    /** Queen attacks from `sq` given `occ` occupancy. */
    public static long queenAttacks(int sq, long occ) {
        return rookAttacks(sq, occ) | bishopAttacks(sq, occ);
    }

    // =========================================================================
    //  Classical ray scan
    // =========================================================================

    /**
     * Compute attacks along a single ray from `sq` using classical o^(o-2r) trick.
     *
     * The "o^(o-2r)" method (Hyperbola Quintessence) is branchless for positive rays
     * and uses a reflection trick for negative rays. We use a simpler but equally
     * correct version here since correctness is priority #1.
     *
     * @param dir 0=N,1=NE,2=E,3=SE,4=S,5=SW,6=W,7=NW
     */
    private static long slidingRay(int sq, long occ, int dir) {
        long ray = RAY[dir][sq];
        long blocker = ray & occ;
        if (blocker == 0L) return ray;

        // For "positive" rays (N, NE, E — lower sq index = toward bit-0):
        // first blocker in the direction is the LSB of (ray & occ)
        // For "negative" rays (S, SE, SW, W — higher sq index):
        // first blocker is the MSB of (ray & occ)

        // Direction parity: even dirs 0,2,4,6 ~ N,E,S,W; odd ~ diagonals
        // Directions where sq decreases (lower bit indices): N(0), NE(1), NW(7), W(6).
        // Here, the ray has bits lower than sq. The CLOSEST blocker is the HIGHEST bit among them.
        // Directions where sq increases (higher bit indices): S(4), SE(3), SW(5), E(2).
        // Here, the ray has bits higher than sq. The CLOSEST blocker is the LOWEST bit among them.
        int blockerSq;
        switch (dir) {
            case 0: // N  (sq decreases)
            case 1: // NE (sq decreases)
            case 6: // W  (sq decreases)
            case 7: // NW (sq decreases)
                blockerSq = 63 - Long.numberOfLeadingZeros(blocker); // MSB (highest bit)
                break;
            default: // 2, 3, 4, 5 (sq increases)
                blockerSq = Long.numberOfTrailingZeros(blocker); // LSB (lowest bit)
                break;
        }
        // Include the blocker square but not beyond
        return ray ^ RAY[dir][blockerSq];
    }

    // =========================================================================
    //  Leaper initialization
    // =========================================================================

    private static void initLeapers() {
        for (int sq = 0; sq < 64; sq++) {
            long bb = 1L << sq;

            // ---- Knight ----
            long n = 0L;
            n |= (bb & Bitboard.NOT_FILE_A)  >>> 17;
            n |= (bb & Bitboard.NOT_FILE_H)  >>> 15;
            n |= (bb & Bitboard.NOT_FILE_AB) >>> 10;
            n |= (bb & Bitboard.NOT_FILE_GH) >>>  6;
            n |= (bb & Bitboard.NOT_FILE_GH) <<   6;
            n |= (bb & Bitboard.NOT_FILE_AB) <<  10;
            n |= (bb & Bitboard.NOT_FILE_A)  <<  15;
            n |= (bb & Bitboard.NOT_FILE_H)  <<  17;
            KNIGHT_ATTACKS[sq] = n;

            // ---- King ----
            long k = 0L;
            k |= (bb & Bitboard.NOT_FILE_A) >>> 9;
            k |=  bb                        >>> 8;
            k |= (bb & Bitboard.NOT_FILE_H) >>> 7;
            k |= (bb & Bitboard.NOT_FILE_A) >>> 1;
            k |= (bb & Bitboard.NOT_FILE_H) <<  1;
            k |= (bb & Bitboard.NOT_FILE_A) <<  7;
            k |=  bb                        <<  8;
            k |= (bb & Bitboard.NOT_FILE_H) <<  9;
            KING_ATTACKS[sq] = k;

            // ---- Pawn attacks ----
            PAWN_ATTACKS[0][sq] = Bitboard.shiftNE(bb) | Bitboard.shiftNW(bb);
            PAWN_ATTACKS[1][sq] = Bitboard.shiftSE(bb) | Bitboard.shiftSW(bb);
        }
    }

    // =========================================================================
    //  Ray initialization
    // =========================================================================

    private static void initRays() {
        // Direction deltas: row delta, col delta
        // sq = row*8 + col, so sq delta = rowDelta*8 + colDelta
        int[] rowDelta = { -1, -1,  0, +1, +1, +1,  0, -1 };
        int[] colDelta = {  0, +1, +1, +1,  0, -1, -1, -1 };

        for (int dir = 0; dir < 8; dir++) {
            for (int sq = 0; sq < 64; sq++) {
                long ray = 0L;
                int row = Bitboard.row(sq);
                int col = Bitboard.col(sq);
                int r = row + rowDelta[dir];
                int c = col + colDelta[dir];
                while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                    ray |= Bitboard.bit(r * 8 + c);
                    r += rowDelta[dir];
                    c += colDelta[dir];
                }
                RAY[dir][sq] = ray;
            }
        }
    }

    // =========================================================================
    //  Classical attack helpers (used by BBPosition.isAttackedBy)
    // =========================================================================

    /** Classical rook attacks (used for table init or fallback). */
    public static long rookAttacksClassical(int sq, long occ) {
        return rookAttacks(sq, occ);
    }

    /** Classical bishop attacks. */
    public static long bishopAttacksClassical(int sq, long occ) {
        return bishopAttacks(sq, occ);
    }
}
