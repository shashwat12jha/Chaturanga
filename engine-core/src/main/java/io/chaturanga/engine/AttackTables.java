package io.chaturanga.engine;

/** Precomputed leaper attacks and occupancy-aware sliding attacks. */
public final class AttackTables {
    public static final long[] KNIGHT = new long[64];
    public static final long[] KING = new long[64];
    public static final long[][] PAWN = new long[2][64];

    static {
        int[][] knightSteps = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        int[][] kingSteps = {{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int square = 0; square < 64; square++) {
            KNIGHT[square] = stepAttacks(square, knightSteps);
            KING[square] = stepAttacks(square, kingSteps);
            PAWN[Piece.WHITE][square] = stepAttacks(square, new int[][]{{-1, 1}, {1, 1}});
            PAWN[Piece.BLACK][square] = stepAttacks(square, new int[][]{{-1, -1}, {1, -1}});
        }
    }

    private AttackTables() {}

    public static long bishop(int square, long occupied) {
        return ray(square, occupied, 1, 1) | ray(square, occupied, 1, -1)
                | ray(square, occupied, -1, 1) | ray(square, occupied, -1, -1);
    }

    public static long rook(int square, long occupied) {
        return ray(square, occupied, 1, 0) | ray(square, occupied, -1, 0)
                | ray(square, occupied, 0, 1) | ray(square, occupied, 0, -1);
    }

    public static long queen(int square, long occupied) {
        return bishop(square, occupied) | rook(square, occupied);
    }

    private static long stepAttacks(int square, int[][] steps) {
        int file = square & 7;
        int rank = square >>> 3;
        long attacks = 0;
        for (int[] step : steps) {
            int nextFile = file + step[0];
            int nextRank = rank + step[1];
            if (nextFile >= 0 && nextFile < 8 && nextRank >= 0 && nextRank < 8) {
                attacks |= 1L << (nextRank * 8 + nextFile);
            }
        }
        return attacks;
    }

    private static long ray(int square, long occupied, int fileDelta, int rankDelta) {
        int file = (square & 7) + fileDelta;
        int rank = (square >>> 3) + rankDelta;
        long attacks = 0;
        while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
            int target = rank * 8 + file;
            long bit = 1L << target;
            attacks |= bit;
            if ((occupied & bit) != 0) break;
            file += fileDelta;
            rank += rankDelta;
        }
        return attacks;
    }
}
