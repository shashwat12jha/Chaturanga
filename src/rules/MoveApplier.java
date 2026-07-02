package rules;

import model.GameState;
import model.Move;
import model.Piece;
import model.PieceType;

public class MoveApplier {
    
    public GameState apply(Move move, GameState state) {
        GameState nextState = state.copy();
        
        int fIdx = move.fromRow * 8 + move.fromCol;
        int tIdx = move.toRow * 8 + move.toCol;
        
        Piece p = nextState.getPiece(fIdx);
        nextState.setPiece(fIdx, null);
        
        // Handle captures
        if (move.type == model.MoveType.EN_PASSANT) {
            int capRow = move.fromRow;
            int capCol = move.toCol;
            nextState.setPiece(capRow * 8 + capCol, null);
        } else if (move.captured != null) {
            // Already overwritten by setting piece at destination, handled below
        }
        
        // Handle castling
        if (move.type == model.MoveType.KINGSIDE_CASTLE) {
            Piece rook = nextState.getPiece(move.fromRow, 7);
            nextState.setPiece(move.fromRow, 7, null);
            nextState.setPiece(move.fromRow, 5, rook);
            rook.hasMoved = true;
        } else if (move.type == model.MoveType.QUEENSIDE_CASTLE) {
            Piece rook = nextState.getPiece(move.fromRow, 0);
            nextState.setPiece(move.fromRow, 0, null);
            nextState.setPiece(move.fromRow, 3, rook);
            rook.hasMoved = true;
        }
        
        // Handle promotion
        if (move.type == model.MoveType.PROMOTION && move.promotionPiece != null) {
            p = new Piece(move.promotionPiece, p.isWhite, true);
        } else {
            p.hasMoved = true;
        }
        
        nextState.setPiece(tIdx, p);
        
        // Update En Passant Target
        if (p.type == PieceType.PAWN && Math.abs(move.toRow - move.fromRow) == 2) {
            int epRow = (move.fromRow + move.toRow) / 2;
            nextState.setEnPassantTarget(epRow * 8 + move.fromCol);
        } else {
            nextState.setEnPassantTarget(-1);
        }
        
        // Update Castling Rights
        if (p.type == PieceType.KING) {
            if (p.isWhite) {
                nextState.setCastlingRight(0, false);
                nextState.setCastlingRight(1, false);
            } else {
                nextState.setCastlingRight(2, false);
                nextState.setCastlingRight(3, false);
            }
        } else if (p.type == PieceType.ROOK) {
            if (p.isWhite) {
                if (move.fromCol == 7 && move.fromRow == 7) nextState.setCastlingRight(0, false);
                if (move.fromCol == 0 && move.fromRow == 7) nextState.setCastlingRight(1, false);
            } else {
                if (move.fromCol == 7 && move.fromRow == 0) nextState.setCastlingRight(2, false);
                if (move.fromCol == 0 && move.fromRow == 0) nextState.setCastlingRight(3, false);
            }
        }
        
        // If a rook is captured, its castling right is lost
        if (move.toCol == 7 && move.toRow == 7) nextState.setCastlingRight(0, false);
        if (move.toCol == 0 && move.toRow == 7) nextState.setCastlingRight(1, false);
        if (move.toCol == 7 && move.toRow == 0) nextState.setCastlingRight(2, false);
        if (move.toCol == 0 && move.toRow == 0) nextState.setCastlingRight(3, false);
        
        // Clocks
        if (p.type == PieceType.PAWN || move.captured != null) {
            nextState.setHalfMoveClock(0);
        } else {
            nextState.setHalfMoveClock(nextState.getHalfMoveClock() + 1);
        }
        
        if (!state.isWhiteToMove()) {
            nextState.incrementFullMoveNumber();
        }
        
        nextState.setWhiteToMove(!state.isWhiteToMove());
        
        nextState.addMove(move);
        nextState.recordPosition(io.FENParser.toFEN(nextState));
        
        return nextState;
    }
}
