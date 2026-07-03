package io.chaturanga.engine;

import java.util.List;

/** Completed iterative-deepening iteration. */
public record SearchInfo(int depth, int score, long nodes, long elapsedMillis,
                         int hashfullPermill, List<Move> principalVariation) {
}
