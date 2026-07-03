package io.chaturanga.engine.cli;

import io.chaturanga.engine.Benchmark;

import java.io.PrintWriter;

public final class BenchMain {
    private BenchMain() {}

    public static void main(String[] args) {
        int depth = args.length == 0 ? 5 : Integer.parseInt(args[0]);
        Benchmark.run(depth, new PrintWriter(System.out, true));
    }
}
