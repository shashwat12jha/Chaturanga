package engine.bitboard;

/**
 * Bitboard utilities — all methods are pure-static, branchless where possible.
 *
 * Square convention (matches existing GameState.board[]):
 *   sq = row * 8 + col
 *   row 0 = rank 8 (Black's back rank, top of board)
 *   row 7 = rank 1 (White's back rank, bottom of board)
 *   col 0 = a-file, col 7 = h-file
 *
 * Consequently:
 *   sq  0 = a8   sq  7 = h8
 *   sq 56 = a1   sq 63 = h1
 *
 * Directional shifts:
 *   North (+rank) = sq - 8 (row decreases)
 *   South (-rank) = sq + 8
 *   East  (+file) = sq + 1
 *   West  (-file) = sq - 1
 */
public final class Bitboard {

    private Bitboard() {} // static-only

    // ---- File / rank masks ----
    public static final long FILE_A = 0x0101010101010101L; // col 0
    public static final long FILE_B = 0x0202020202020202L; // col 1
    public static final long FILE_G = 0x4040404040404040L; // col 6
    public static final long FILE_H = 0x8080808080808080L; // col 7

    public static final long NOT_FILE_A  = ~FILE_A;
    public static final long NOT_FILE_H  = ~FILE_H;
    public static final long NOT_FILE_AB = ~(FILE_A | FILE_B);
    public static final long NOT_FILE_GH = ~(FILE_G | FILE_H);

    public static final long RANK_1 = 0xFF00000000000000L; // row 7 (sq 56-63)
    public static final long RANK_2 = 0x00FF000000000000L; // row 6
    public static final long RANK_7 = 0x000000000000FF00L; // row 1
    public static final long RANK_8 = 0x00000000000000FFL; // row 0

    // ---- Square helpers ----

    /** Bitmask with only square `sq` set. */
    public static long bit(int sq) {
        return 1L << sq;
    }

    /** Return the index (0-63) of the lowest set bit. Undefined if bb == 0. */
    public static int lsb(long bb) {
        return Long.numberOfTrailingZeros(bb);
    }

    /** Return `bb` with its lowest set bit cleared. */
    public static long popLsb(long bb) {
        return bb & (bb - 1);
    }

    /** Count number of set bits. */
    public static int popcount(long bb) {
        return Long.bitCount(bb);
    }

    /** Row (0-7) from square index. */
    public static int row(int sq) { return sq >>> 3; }

    /** Column (0-7) from square index. */
    public static int col(int sq) { return sq & 7; }

    /** Square index from (row, col). */
    public static int sq(int row, int col) { return (row << 3) | col; }

    // ---- Directional shifts (whole-board, no per-square branching) ----

    /** Shift all pieces one step North (toward rank 8 / row 0). */
    public static long shiftN(long bb)  { return bb >>> 8; }

    /** Shift all pieces one step South (toward rank 1 / row 7). */
    public static long shiftS(long bb)  { return bb  << 8; }

    /** Shift all pieces one step East (toward h-file). */
    public static long shiftE(long bb)  { return (bb & NOT_FILE_H) << 1; }

    /** Shift all pieces one step West (toward a-file). */
    public static long shiftW(long bb)  { return (bb & NOT_FILE_A) >>> 1; }

    public static long shiftNE(long bb) { return (bb & NOT_FILE_H) >>> 7; }
    public static long shiftNW(long bb) { return (bb & NOT_FILE_A) >>> 9; }
    public static long shiftSE(long bb) { return (bb & NOT_FILE_H) <<  9; }
    public static long shiftSW(long bb) { return (bb & NOT_FILE_A) <<  7; }

    // ---- Pawn attack helpers (for move generation) ----

    /** All squares attacked by white pawns in `bb`. */
    public static long whitePawnAttacks(long bb) {
        return shiftNE(bb) | shiftNW(bb);
    }

    /** All squares attacked by black pawns in `bb`. */
    public static long blackPawnAttacks(long bb) {
        return shiftSE(bb) | shiftSW(bb);
    }

    // ---- Debug ----

    /** Pretty-print a bitboard to stdout (for debugging). */
    public static String format(long bb) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            sb.append((char)('8' - r)).append(" ");
            for (int c = 0; c < 8; c++) {
                sb.append((bb & (1L << (r * 8 + c))) != 0 ? "1 " : ". ");
            }
            sb.append("\n");
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }
}
