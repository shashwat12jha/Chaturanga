package io.chaturanga.engine;

import java.util.List;

/** Final search result from the last fully completed iteration. */
public record SearchResult(Move bestMove, int score, int depth, long nodes,
                           long elapsedMillis, List<Move> principalVariation) {
}
