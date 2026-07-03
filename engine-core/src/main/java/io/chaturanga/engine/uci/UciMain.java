package io.chaturanga.engine.uci;

import io.chaturanga.engine.Benchmark;
import io.chaturanga.engine.Fen;
import io.chaturanga.engine.Move;
import io.chaturanga.engine.MoveGenerator;
import io.chaturanga.engine.Perft;
import io.chaturanga.engine.Piece;
import io.chaturanga.engine.Position;
import io.chaturanga.engine.SearchEngine;
import io.chaturanga.engine.SearchInfo;
import io.chaturanga.engine.SearchLimits;
import io.chaturanga.engine.SearchResult;
import io.chaturanga.engine.Undo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** Universal Chess Interface entry point. */
public final class UciMain {
    private final BufferedReader input;
    private final PrintWriter output;
    private final MoveGenerator generator = new MoveGenerator();
    private Position position = Position.start();
    private SearchEngine engine = new SearchEngine(32);
    private Thread searchThread;

    private UciMain(BufferedReader input, PrintWriter output) {
        this.input = input;
        this.output = output;
    }

    public static void main(String[] args) throws IOException {
        new UciMain(new BufferedReader(new InputStreamReader(System.in)),
                new PrintWriter(System.out, true)).loop();
    }

    private void loop() throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            String command = line.trim();
            if (command.isEmpty()) continue;
            try {
                if (!handle(command)) break;
            } catch (RuntimeException exception) {
                output.println("info string error: " + exception.getMessage());
            }
        }
        stopSearch(true);
    }

    private boolean handle(String command) {
        if (command.equals("uci")) {
            output.println("id name Chaturanga Classical 1.0");
            output.println("id author Shashwat Kumar Jha");
            output.println("option name Hash type spin default 32 min 1 max 1024");
            output.println("option name Clear Hash type button");
            output.println("uciok");
        } else if (command.equals("isready")) {
            output.println("readyok");
        } else if (command.equals("ucinewgame")) {
            stopSearch(true);
            engine.clear();
            position = Position.start();
        } else if (command.startsWith("setoption")) {
            setOption(command);
        } else if (command.startsWith("position")) {
            stopSearch(true);
            setPosition(command);
        } else if (command.startsWith("go")) {
            startSearch(command);
        } else if (command.equals("stop")) {
            stopSearch(true);
        } else if (command.equals("quit")) {
            stopSearch(true);
            return false;
        } else if (command.equals("d")) {
            output.println("Fen: " + Fen.format(position));
            output.printf("Key: %016x%n", position.zobristKey());
        } else if (command.startsWith("perft")) {
            runPerft(command);
        } else if (command.startsWith("bench")) {
            int depth = command.split("\\s+").length > 1
                    ? Integer.parseInt(command.split("\\s+")[1]) : 5;
            Benchmark.run(depth, output);
        }
        output.flush();
        return true;
    }

    private void setOption(String command) {
        stopSearch(true);
        if (command.contains("name Clear Hash")) {
            engine.clear();
            return;
        }
        String marker = "name Hash value ";
        int index = command.indexOf(marker);
        if (index >= 0) {
            int megabytes = Math.max(1, Math.min(1024,
                    Integer.parseInt(command.substring(index + marker.length()).trim())));
            engine = new SearchEngine(megabytes);
        }
    }

    private void setPosition(String command) {
        String payload = command.substring("position".length()).trim();
        String movesText = null;
        int movesIndex = payload.indexOf(" moves ");
        if (movesIndex >= 0) {
            movesText = payload.substring(movesIndex + 7).trim();
            payload = payload.substring(0, movesIndex).trim();
        }

        Position next;
        if (payload.equals("startpos")) {
            next = Position.start();
        } else if (payload.startsWith("fen ")) {
            next = Fen.parse(payload.substring(4));
        } else {
            throw new IllegalArgumentException("Expected startpos or fen");
        }

        if (movesText != null && !movesText.isEmpty()) {
            for (String uci : movesText.split("\\s+")) {
                Move move = generator.parseUciMove(next, uci);
                if (move == Move.NONE) throw new IllegalArgumentException("Illegal move " + uci);
                next.makeMove(move, new Undo());
            }
        }
        position = next;
    }

    private void startSearch(String command) {
        stopSearch(true);
        SearchLimits limits = parseLimits(command);
        Position searchPosition = position.copy();
        engine.setInfoListener(this::writeInfo);
        searchThread = new Thread(() -> {
            SearchResult result = engine.search(searchPosition, limits);
            output.println("bestmove " + result.bestMove().toUci());
            output.flush();
        }, "chaturanga-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private SearchLimits parseLimits(String command) {
        String[] tokens = command.split("\\s+");
        int depth = 64;
        long moveTime = 0;
        long nodes = 0;
        long whiteTime = 0, blackTime = 0, whiteIncrement = 0, blackIncrement = 0;
        int movesToGo = 30;
        for (int i = 1; i < tokens.length; i++) {
            if (i + 1 >= tokens.length) continue;
            switch (tokens[i]) {
                case "depth" -> depth = Integer.parseInt(tokens[++i]);
                case "movetime" -> moveTime = Long.parseLong(tokens[++i]);
                case "nodes" -> nodes = Long.parseLong(tokens[++i]);
                case "wtime" -> whiteTime = Long.parseLong(tokens[++i]);
                case "btime" -> blackTime = Long.parseLong(tokens[++i]);
                case "winc" -> whiteIncrement = Long.parseLong(tokens[++i]);
                case "binc" -> blackIncrement = Long.parseLong(tokens[++i]);
                case "movestogo" -> movesToGo = Math.max(1, Integer.parseInt(tokens[++i]));
                default -> { }
            }
        }
        if (moveTime == 0) {
            long remaining = position.sideToMove() == Piece.WHITE ? whiteTime : blackTime;
            long increment = position.sideToMove() == Piece.WHITE ? whiteIncrement : blackIncrement;
            if (remaining > 0) {
                moveTime = Math.max(10, Math.min(remaining / 2,
                        remaining / movesToGo + increment * 3 / 4 - 20));
            }
        }
        return new SearchLimits(depth, moveTime, nodes);
    }

    private void writeInfo(SearchInfo info) {
        long nps = info.nodes() * 1_000L / Math.max(1, info.elapsedMillis());
        String score;
        if (Math.abs(info.score()) >= SearchEngine.MATE_SCORE - 1_000) {
            int plies = SearchEngine.MATE_SCORE - Math.abs(info.score());
            int moves = Math.max(1, (plies + 1) / 2);
            score = "mate " + (info.score() < 0 ? -moves : moves);
        } else {
            score = "cp " + info.score();
        }
        StringBuilder pvText = new StringBuilder();
        for (Move move : info.principalVariation()) pvText.append(move).append(' ');
        output.printf("info depth %d score %s nodes %d nps %d time %d hashfull %d pv %s%n",
                info.depth(), score, info.nodes(), nps, info.elapsedMillis(),
                info.hashfullPermill(), pvText.toString().trim());
        output.flush();
    }

    private void stopSearch(boolean wait) {
        Thread active = searchThread;
        if (active == null || !active.isAlive()) return;
        engine.stop();
        if (wait) {
            try {
                active.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        searchThread = null;
    }

    private void runPerft(String command) {
        String[] tokens = command.split("\\s+");
        int depth = tokens.length > 1 ? Integer.parseInt(tokens[1]) : 1;
        Perft perft = new Perft();
        long started = System.nanoTime();
        long total = 0;
        for (var entry : perft.divide(position, depth).entrySet()) {
            output.println(entry.getKey() + ": " + entry.getValue());
            total += entry.getValue();
        }
        long elapsed = Math.max(1, (System.nanoTime() - started) / 1_000_000L);
        output.printf("Nodes searched: %d Time: %d ms NPS: %d%n", total, elapsed, total * 1_000L / elapsed);
    }
}
