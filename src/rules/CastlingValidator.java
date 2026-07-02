package rules;

import model.GameState;
import model.Piece;
import model.PieceType;

public class CastlingValidator {

    private final CheckDetector checkDetector = new CheckDetector();

    public boolean canCastle(boolean isWhite, boolean kingside, GameState state) {
        int row = isWhite ? 7 : 0;
        
        // 1. King must not be in check
        if (checkDetector.isKingInCheck(isWhite, state)) {
            return false;
        }

        // 2. Castling rights must be valid (meaning King and Rook haven't moved)
        int castlingIndex = (isWhite ? 0 : 2) + (kingside ? 0 : 1);
        if (!state.getCastlingRight(castlingIndex)) {
            return false;
        }

        if (kingside) {
            // Check squares f (col 5) and g (col 6)
            if (state.getPiece(5, row) != null || state.getPiece(6, row) != null) {
                return false; // Squares not empty
            }
            if (checkDetector.isSquareAttacked(5, row, !isWhite, state)) {
                return false; // Passes through check
            }
            if (checkDetector.isSquareAttacked(6, row, !isWhite, state)) {
                return false; // Lands in check
            }
            
            // The rook must be at col 7
            Piece rook = state.getPiece(7, row);
            if (rook == null || rook.type != PieceType.ROOK || rook.isWhite != isWhite) {
                return false;
            }
        } else {
            // Check squares d (col 3), c (col 2), and b (col 1)
            if (state.getPiece(3, row) != null || state.getPiece(2, row) != null || state.getPiece(1, row) != null) {
                return false; // Squares not empty
            }
            if (checkDetector.isSquareAttacked(3, row, !isWhite, state)) {
                return false; // Passes through check
            }
            if (checkDetector.isSquareAttacked(2, row, !isWhite, state)) {
                return false; // Lands in check
            }
            
            // The rook must be at col 0
            Piece rook = state.getPiece(0, row);
            if (rook == null || rook.type != PieceType.ROOK || rook.isWhite != isWhite) {
                return false;
            }
        }

        return true;
    }
}
