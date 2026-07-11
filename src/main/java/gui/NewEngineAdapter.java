package gui;

import io.FENParser;
import io.chaturanga.engine.Fen;
import io.chaturanga.engine.Position;
import io.chaturanga.engine.SearchEngine;
import io.chaturanga.engine.SearchLimits;
import io.chaturanga.engine.SearchResult;
import model.GameState;
import model.Move;
import rules.MoveGenerator;

import java.util.List;

public class NewEngineAdapter {
    private final SearchEngine coreEngine = new SearchEngine();

    public Move findBestMove(GameState state, int depth) {
        // 1. Convert GUI GameState to FEN
        String fen = FENParser.toFEN(state);
        
        // 2. Parse FEN into new engine Position
        Position pos = Fen.parse(fen);
        
        // 3. Run the search
        coreEngine.stop(); // Clear any pending stops
        SearchLimits limits = new SearchLimits(depth, 8_000, 0);
        SearchResult result = coreEngine.search(pos, limits);
        
        if (result.bestMove() == io.chaturanga.engine.Move.NONE) {
            return null; // Checkmate or stalemate
        }
        
        io.chaturanga.engine.Move bestCoreMove = result.bestMove();
        int fromSq = bestCoreMove.from();
        int toSq = bestCoreMove.to();
        
        // Convert squares to GUI col/row
        int fromCol = fromSq % 8;
        int fromRow = 7 - (fromSq / 8);
        int toCol = toSq % 8;
        int toRow = 7 - (toSq / 8);
        
        // Find matching model.Move from the legal moves
        List<Move> legalMoves = new MoveGenerator().generateLegalMoves(state);
        for (Move guiMove : legalMoves) {
            if (guiMove.fromCol == fromCol && guiMove.fromRow == fromRow &&
                guiMove.toCol == toCol && guiMove.toRow == toRow) {
                
                // If it's a promotion, we need to ensure the promotion piece matches
                if (bestCoreMove.isPromotion()) {
                    int promoPiece = bestCoreMove.promotion();
                    // io.chaturanga.engine.Piece types: PAWN=1, KNIGHT=2, BISHOP=3, ROOK=4, QUEEN=5
                    String promoStr = "";
                    if (promoPiece == io.chaturanga.engine.Piece.KNIGHT) promoStr = "Knight";
                    else if (promoPiece == io.chaturanga.engine.Piece.BISHOP) promoStr = "Bishop";
                    else if (promoPiece == io.chaturanga.engine.Piece.ROOK) promoStr = "Rook";
                    else if (promoPiece == io.chaturanga.engine.Piece.QUEEN) promoStr = "Queen";
                    
                    if (guiMove.promotionPiece != null && guiMove.promotionPiece.name().equalsIgnoreCase(promoStr)) {
                        return guiMove;
                    }
                } else {
                    return guiMove;
                }
            }
        }
        return null;
    }

    public void stop() {
        coreEngine.stop();
    }
}
