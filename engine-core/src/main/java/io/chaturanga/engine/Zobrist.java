package io.chaturanga.engine;

import java.util.SplittableRandom;

/** Deterministic Zobrist keys for reproducible hashes and benchmarks. */
final class Zobrist {
    static final long[][] PIECE_SQUARE = new long[12][64];
    static final long SIDE_TO_MOVE;
    static final long[] CASTLING = new long[16];
    static final long[] EN_PASSANT_FILE = new long[8];

    static {
        SplittableRandom random = new SplittableRandom(0x434841545552414EL);
        for (int piece = 0; piece < 12; piece++) {
            for (int square = 0; square < 64; square++) {
                PIECE_SQUARE[piece][square] = random.nextLong();
            }
        }
        SIDE_TO_MOVE = random.nextLong();
        for (int i = 0; i < CASTLING.length; i++) CASTLING[i] = random.nextLong();
        for (int i = 0; i < EN_PASSANT_FILE.length; i++) EN_PASSANT_FILE[i] = random.nextLong();
    }

    private Zobrist() {}
}
