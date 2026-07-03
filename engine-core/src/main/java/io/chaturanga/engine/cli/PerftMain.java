package io.chaturanga.engine.cli;

import io.chaturanga.engine.Fen;
import io.chaturanga.engine.Perft;
import io.chaturanga.engine.Position;

public final class PerftMain {
    private PerftMain() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: PerftMain <depth> [fen]");
            System.exit(2);
        }
        int depth = Integer.parseInt(args[0]);
        String fen = args.length > 1
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : Fen.START_POSITION;
        Position position = Fen.parse(fen);
        Perft perft = new Perft();
        long started = System.nanoTime();
        perft.divide(position, depth).forEach((move, nodes) -> System.out.println(move + ": " + nodes));
        long nodes = perft.count(position, depth);
        long elapsed = Math.max(1, (System.nanoTime() - started) / 1_000_000L);
        System.out.printf("Nodes: %d Time: %d ms NPS: %d%n", nodes, elapsed, nodes * 1_000L / elapsed);
    }
}
