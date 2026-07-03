package io.chaturanga.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Correctness benchmark for the legal move generator. */
public final class Perft {
    private final MoveGenerator generator = new MoveGenerator();

    public long count(Position position, int depth) {
        if (depth < 0) throw new IllegalArgumentException("Depth cannot be negative");
        if (depth == 0) return 1;
        long nodes = 0;
        Undo undo = new Undo();
        for (Move move : generator.generateLegalMoves(position)) {
            position.makeMove(move, undo);
            nodes += count(position, depth - 1);
            position.unmakeMove(move, undo);
        }
        return nodes;
    }

    public Map<Move, Long> divide(Position position, int depth) {
        if (depth < 1) throw new IllegalArgumentException("Divide depth must be positive");
        Map<Move, Long> result = new LinkedHashMap<>();
        Undo undo = new Undo();
        for (Move move : generator.generateLegalMoves(position)) {
            position.makeMove(move, undo);
            result.put(move, count(position, depth - 1));
            position.unmakeMove(move, undo);
        }
        return result;
    }
}
