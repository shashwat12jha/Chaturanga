package io.chaturanga.engine;

import java.util.List;

/** Dependency-free correctness suite runnable with the stock JDK. */
public final class EngineCoreTest {
    private static int assertions;
    private static final MoveGenerator GENERATOR = new MoveGenerator();

    public static void main(String[] args) {
        testFenRoundTrip();
        testFenValidation();
        testMakeUnmake();
        testSpecialMoves();
        testPerft();
        testSearch();
        System.out.println("PASS: " + assertions + " assertions");
    }

    private static void testFenRoundTrip() {
        String[] positions = {
                Fen.START_POSITION,
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 17 42",
                "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2"
        };
        for (String fen : positions) equal(fen, Fen.format(Fen.parse(fen)), "FEN round trip");
    }

    private static void testFenValidation() {
        rejects("8/8/8/8/8/8/8/8 w - - 0 1");
        rejects("8/8/8/8/8/8/4k3/4K2 w - - 0 1");
        rejects("8/8/8/8/8/8/4k3/4K3 white - - 0 1");
        rejects("8/8/8/8/8/8/4k3/4K3 w KK - 0 1");
        rejects("8/8/8/8/8/8/4k3/4K3 w - e4 0 1");
    }

    private static void testMakeUnmake() {
        Position position = Position.start();
        String initialFen = Fen.format(position);
        long initialKey = position.zobristKey();
        List<Move> moves = GENERATOR.generateLegalMoves(position);
        equal(20, moves.size(), "start legal move count");
        for (Move move : moves) {
            Undo undo = new Undo();
            position.makeMove(move, undo);
            check(position.zobristKey() != initialKey, "move changes hash");
            position.unmakeMove(move, undo);
            equal(initialFen, Fen.format(position), "unmake restores FEN");
            equal(initialKey, position.zobristKey(), "unmake restores hash");
        }
    }

    private static void testSpecialMoves() {
        Position castles = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        Move kingSide = GENERATOR.parseUciMove(castles, "e1g1");
        check(kingSide != Move.NONE && kingSide.flag() == Move.KING_CASTLE, "white king castle generated");
        Undo undo = new Undo();
        castles.makeMove(kingSide, undo);
        equal(Piece.WHITE_KING, castles.pieceAt(Move.parseSquare("g1")), "castled king square");
        equal(Piece.WHITE_ROOK, castles.pieceAt(Move.parseSquare("f1")), "castled rook square");
        castles.unmakeMove(kingSide, undo);

        Position ep = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
        Move epMove = GENERATOR.parseUciMove(ep, "e5d6");
        check(epMove != Move.NONE && epMove.flag() == Move.EN_PASSANT, "en-passant generated");
        ep.makeMove(epMove, undo);
        equal(Piece.WHITE_PAWN, ep.pieceAt(Move.parseSquare("d6")), "en-passant destination");
        equal(Piece.NONE, ep.pieceAt(Move.parseSquare("d5")), "en-passant capture removed");
        ep.unmakeMove(epMove, undo);

        Position promotion = Fen.parse("4k3/P7/8/8/8/8/8/4K3 w - - 73 1");
        Move promote = GENERATOR.parseUciMove(promotion, "a7a8q");
        check(promote != Move.NONE && promote.isPromotion(), "promotion generated");
        promotion.makeMove(promote, undo);
        equal(Piece.WHITE_QUEEN, promotion.pieceAt(Move.parseSquare("a8")), "promotion piece");
        equal(0, promotion.halfmoveClock(), "promotion resets halfmove clock");
        promotion.unmakeMove(promote, undo);

        check(Fen.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1").isInsufficientMaterial(), "bare kings draw");
        check(Fen.parse("4k3/8/8/8/8/8/8/2B1K3 w - - 0 1").isInsufficientMaterial(), "bishop versus king draw");
        check(!Fen.parse("4k3/8/8/8/8/8/8/1NB1K3 w - - 0 1").isInsufficientMaterial(), "bishop and knight can mate");
    }

    private static void testPerft() {
        Perft perft = new Perft();
        verifyPerft(perft, Fen.START_POSITION, new long[]{20, 400, 8_902, 197_281});
        verifyPerft(perft,
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                new long[]{48, 2_039, 97_862});
        verifyPerft(perft,
                "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                new long[]{14, 191, 2_812, 43_238});
    }

    private static void testSearch() {
        ClassicalEvaluator evaluator = new ClassicalEvaluator();
        equal(0, evaluator.evaluate(Position.start()), "starting position evaluates equally");

        SearchEngine engine = new SearchEngine(8);
        Position tactical = Fen.parse("4k3/8/8/8/4q3/8/4Q3/4K3 w - - 0 1");
        String tacticalFen = Fen.format(tactical);
        long tacticalKey = tactical.zobristKey();
        SearchResult capture = engine.search(tactical, SearchLimits.depth(3));
        equal("e2e4", capture.bestMove().toUci(), "engine captures hanging queen");
        equal(tacticalFen, Fen.format(tactical), "search preserves root FEN");
        equal(tacticalKey, tactical.zobristKey(), "search preserves root hash");

        engine.clear();
        Position mateInOne = Fen.parse("7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
        SearchResult mate = engine.search(mateInOne, SearchLimits.depth(3));
        equal("g6g7", mate.bestMove().toUci(), "engine finds mate in one");
        check(mate.score() >= SearchEngine.MATE_SCORE - 2, "mate score uses ply distance");

        engine.clear();
        Position stalemate = Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        SearchResult draw = engine.search(stalemate, SearchLimits.depth(3));
        check(draw.bestMove() == Move.NONE, "stalemate has no best move");
        equal(0, draw.score(), "stalemate score is zero");

        engine.clear();
        long started = System.nanoTime();
        SearchResult timed = engine.search(Position.start(), SearchLimits.moveTime(150));
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        check(timed.bestMove() != Move.NONE, "timed search returns a move");
        check(timed.depth() >= 1, "timed search completes an iteration");
        check(elapsed < 1_000, "timed search stops promptly (elapsed " + elapsed + " ms)");
        System.out.printf("search d%d %,d nodes (%d ms) pv %s%n",
                timed.depth(), timed.nodes(), elapsed, timed.principalVariation());
    }

    private static void verifyPerft(Perft perft, String fen, long[] expected) {
        Position position = Fen.parse(fen);
        for (int depth = 1; depth <= expected.length; depth++) {
            long started = System.nanoTime();
            long actual = perft.count(position, depth);
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            equal(expected[depth - 1], actual, "perft depth " + depth + " for " + fen);
            System.out.printf("perft d%d %,d nodes (%d ms)%n", depth, actual, elapsed);
        }
    }

    private static void rejects(String fen) {
        try {
            Fen.parse(fen);
            throw new AssertionError("Expected invalid FEN: " + fen);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(long expected, long actual, String message) {
        assertions++;
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void equal(String expected, String actual, String message) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
