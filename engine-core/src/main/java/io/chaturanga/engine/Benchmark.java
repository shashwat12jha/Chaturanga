package io.chaturanga.engine;

import java.io.PrintWriter;

/** Deterministic multi-position search benchmark. */
public final class Benchmark {
    private static final String[] POSITIONS = {
            Fen.START_POSITION,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "2r2rk1/pp1bqppp/2n1pn2/2pp4/3P4/2P1PN2/PP1N1PPP/R2Q1RK1 w - - 2 10",
            "8/5pk1/3p2p1/2pP3p/2P1P2P/5KP1/8/8 w - - 0 36"
    };

    private Benchmark() {}

    public static Result run(int depth, PrintWriter output) {
        SearchEngine engine = new SearchEngine(32);
        long totalNodes = 0;
        long started = System.nanoTime();
        long signature = 0xcbf29ce484222325L;
        for (int index = 0; index < POSITIONS.length; index++) {
            Position position = Fen.parse(POSITIONS[index]);
            SearchResult result = engine.search(position, SearchLimits.depth(depth));
            totalNodes += result.nodes();
            signature ^= result.bestMove().encode();
            signature *= 0x100000001b3L;
            signature ^= result.score();
            signature *= 0x100000001b3L;
            output.printf("bench %d/%d depth %d bestmove %s score %d nodes %d%n",
                    index + 1, POSITIONS.length, result.depth(), result.bestMove(), result.score(), result.nodes());
        }
        long elapsed = Math.max(1, (System.nanoTime() - started) / 1_000_000L);
        long nps = totalNodes * 1_000L / elapsed;
        output.printf("bench nodes %d time %d nps %d signature %016x%n", totalNodes, elapsed, nps, signature);
        output.flush();
        return new Result(totalNodes, elapsed, nps, signature);
    }

    public record Result(long nodes, long elapsedMillis, long nodesPerSecond, long signature) {}
}
