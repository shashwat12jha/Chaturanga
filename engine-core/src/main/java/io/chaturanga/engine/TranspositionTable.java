package io.chaturanga.engine;

import java.util.Arrays;

/** Fixed-size, depth-preferred transposition table. */
final class TranspositionTable {
    static final byte EXACT = 0;
    static final byte LOWER_BOUND = 1;
    static final byte UPPER_BOUND = 2;

    private final long[] keys;
    private final int[] moves;
    private final short[] scores;
    private final byte[] depths;
    private final byte[] flags;
    private final byte[] generations;
    private final int mask;
    private byte generation;

    TranspositionTable(int megabytes) {
        int requestedEntries = Math.max(1, megabytes) * 1024 * 1024 / 24;
        int size = 1;
        while (size < requestedEntries) size <<= 1;
        keys = new long[size];
        moves = new int[size];
        scores = new short[size];
        depths = new byte[size];
        flags = new byte[size];
        generations = new byte[size];
        mask = size - 1;
    }

    int find(long key) {
        int index = (int) key & mask;
        return keys[index] == key ? index : -1;
    }

    int move(int index) { return moves[index]; }
    int score(int index) { return scores[index]; }
    int depth(int index) { return Byte.toUnsignedInt(depths[index]); }
    byte flag(int index) { return flags[index]; }

    void store(long key, int depth, int score, byte flag, int move) {
        int index = (int) key & mask;
        if (keys[index] == 0 || keys[index] == key || generations[index] != generation
                || depth >= Byte.toUnsignedInt(depths[index])) {
            keys[index] = key;
            moves[index] = move;
            scores[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, score));
            depths[index] = (byte) Math.min(255, depth);
            flags[index] = flag;
            generations[index] = generation;
        }
    }

    void newSearch() { generation++; }

    void clear() {
        Arrays.fill(keys, 0);
        Arrays.fill(moves, 0);
        Arrays.fill(scores, (short) 0);
        Arrays.fill(depths, (byte) 0);
        Arrays.fill(flags, (byte) 0);
        Arrays.fill(generations, (byte) 0);
        generation = 0;
    }

    int hashfullPermill() {
        int sample = Math.min(1000, keys.length);
        int used = 0;
        for (int i = 0; i < sample; i++) if (keys[i] != 0 && generations[i] == generation) used++;
        return used * 1000 / sample;
    }
}
