package engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.FENParser;
import model.*;
import rules.*;

import java.util.List;

public class FullTest {

    private final MoveApplier   applier   = new MoveApplier();
    private final MoveGenerator generator = new MoveGenerator();
    private final CheckDetector checkDet  = new CheckDetector();
    private final DrawDetector  drawDet   = new DrawDetector();
    private final Evaluator     evaluator = new Evaluator();
    private final SearchEngine  engine    = new SearchEngine();

    // ========= Castling =========
    @Test
    void testCastling() {
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

        // White KS: rook h1->f1, king e1->g1
        check("WKS: rook on f1", fen, 4, 7, 6, 7, MoveType.KINGSIDE_CASTLE,
            s -> s.getPiece(5, 7) != null && s.getPiece(5, 7).type == PieceType.ROOK && s.getPiece(7, 7) == null);

        // White QS: rook a1->d1, king e1->c1
        check("WQS: rook on d1", fen, 4, 7, 2, 7, MoveType.QUEENSIDE_CASTLE,
            s -> s.getPiece(3, 7) != null && s.getPiece(3, 7).type == PieceType.ROOK && s.getPiece(0, 7) == null);

        // Black KS: rook h8->f8, king e8->g8
        String fenB = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1";
        check("BKS: rook on f8", fenB, 4, 0, 6, 0, MoveType.KINGSIDE_CASTLE,
            s -> s.getPiece(5, 0) != null && s.getPiece(5, 0).type == PieceType.ROOK && s.getPiece(7, 0) == null);

        // Black QS: rook a8->d8, king e8->c8
        check("BQS: rook on d8", fenB, 4, 0, 2, 0, MoveType.QUEENSIDE_CASTLE,
            s -> s.getPiece(3, 0) != null && s.getPiece(3, 0).type == PieceType.ROOK && s.getPiece(0, 0) == null);

        // Castling rights revoked after king moves
        check("After WKS: white cannot castle again",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", 4, 7, 6, 7, MoveType.KINGSIDE_CASTLE,
            s -> !s.getCastlingRight(0) && !s.getCastlingRight(1));
    }

    // ========= En Passant =========
    @Test
    void testEnPassant() {
        String fen = "8/8/8/3pP3/8/8/8/4K2k w - d6 0 1";
        check("EP: pawn lands on d6", fen, 4, 3, 3, 2, MoveType.EN_PASSANT,
            s -> s.getPiece(3, 2) != null && s.getPiece(3, 2).type == PieceType.PAWN);
        check("EP: captured pawn removed from d5", fen, 4, 3, 3, 2, MoveType.EN_PASSANT,
            s -> s.getPiece(3, 3) == null);
    }

    // ========= Promotion =========
    @Test
    void testPromotion() {
        String fen = "8/P7/8/8/8/8/8/4K2k w - - 0 1";
        check("Promo: queen on a8", fen, 0, 1, 0, 0, MoveType.PROMOTION,
            s -> s.getPiece(0, 0) != null && s.getPiece(0, 0).type == PieceType.QUEEN);

        String fenB = "4k3/8/8/8/8/8/7p/4K3 b - - 0 1";
        check("Black promo to queen on h1", fenB, 7, 6, 7, 7, MoveType.PROMOTION,
            s -> s.getPiece(7, 7) != null && s.getPiece(7, 7).type == PieceType.QUEEN && !s.getPiece(7, 7).isWhite);
    }

    // ========= Checkmate & Stalemate =========
    @Test
    void testCheckmateDetection() {
        String foolsMate = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";
        GameState fm = FENParser.fromFEN(foolsMate);
        assertTrue(generator.generateLegalMoves(fm).isEmpty(), "Fool's mate: White has no legal moves");
        assertTrue(checkDet.isKingInCheck(true, fm), "Fool's mate: White king in check");

        String stalemate = "k7/8/1Q6/8/8/8/8/7K b - - 0 1";
        GameState sm = FENParser.fromFEN(stalemate);
        assertTrue(generator.generateLegalMoves(sm).isEmpty(), "Stalemate: Black has no legal moves");
        assertFalse(checkDet.isKingInCheck(false, sm), "Stalemate: Black NOT in check");
    }

    // ========= Draw Detection =========
    @Test
    void testDraws() {
        GameState kvk = FENParser.fromFEN("k7/8/8/8/8/8/8/7K w - - 0 1");
        assertTrue(drawDet.isInsufficientMaterial(kvk));

        GameState kbvk = FENParser.fromFEN("k7/8/8/8/8/8/8/6BK w - - 0 1");
        assertTrue(drawDet.isInsufficientMaterial(kbvk));

        GameState kqvk = FENParser.fromFEN("k7/8/8/8/8/8/8/6QK w - - 0 1");
        assertFalse(drawDet.isInsufficientMaterial(kqvk));

        GameState fifty = FENParser.fromFEN("k7/8/8/8/8/8/8/7K w - - 100 50");
        assertTrue(drawDet.isFiftyMoveRule(fifty));
    }

    // ========= Eval Symmetry =========
    @Test
    void testEvalSymmetry() {
        GameState start = FENParser.fromFEN(FENParser.STARTING_FEN);
        int score = evaluator.evaluateStatic(start, Personality.balanced());
        assertTrue(Math.abs(score) <= 30, "Start eval near zero");

        GameState wRookUp = FENParser.fromFEN("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1");
        assertTrue(evaluator.evaluateStatic(wRookUp, Personality.balanced()) > 300);

        GameState bRookUp = FENParser.fromFEN("4k3/8/8/8/8/8/8/r3K3 b q - 0 1");
        assertTrue(evaluator.evaluateStatic(bRookUp, Personality.balanced()) < -300);
    }

    // ========= Engine Engine Integration =========
    @Test
    void testEngineVsEngine() {
        GameState start = FENParser.fromFEN(FENParser.STARTING_FEN);
        Move wMove = engine.findBestMove(start, 4, 5000);
        assertNotNull(wMove);

        GameState afterW = applier.apply(wMove, start);
        assertFalse(afterW.isWhiteToMove());

        Move bMove = engine.findBestMove(afterW, 4, 5000);
        assertNotNull(bMove);
        assertFalse(bMove.piece.isWhite);
    }

    // ========= Search Depth =========
    @Test
    void testSearchDepth() {
        String m1 = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1";
        Move mate = engine.findBestMove(FENParser.fromFEN(m1), 4, 3000);
        assertNotNull(mate);
        assertEquals(3, mate.fromCol);
        assertEquals(7, mate.fromRow);
        assertEquals(3, mate.toCol);
        assertEquals(0, mate.toRow);
    }

    // ========= Perft Verification =========
    @Test
    void testPerft() {
        GameState start = FENParser.fromFEN(FENParser.STARTING_FEN);
        assertEquals(20, perft(start, 1));
        assertEquals(400, perft(start, 2));
    }

    private long perft(GameState state, int depth) {
        if (depth == 0) return 1;
        List<Move> moves = generator.generateLegalMoves(state);
        if (depth == 1) return moves.size();
        long count = 0;
        for (Move m : moves) {
            count += perft(applier.apply(m, state), depth - 1);
        }
        return count;
    }

    // ========= Helpers =========
    interface Pred { boolean test(GameState s); }

    private void check(String name, String fen, int fc, int fr, int tc, int tr, MoveType type, Pred pred) {
        GameState state = FENParser.fromFEN(fen);
        Piece p = state.getPiece(fc, fr);
        assertNotNull(p, name + ": No piece at from square");
        Piece cap = state.getPiece(tc, tr);
        Move move = new Move(fc, fr, tc, tr, p, cap, type, PieceType.QUEEN);
        GameState next = applier.apply(move, state);
        assertTrue(pred.test(next), name);
    }
}
