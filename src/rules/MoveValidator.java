package rules;

import java.util.ArrayList;
import java.util.List;

import model.GameState;
import model.Move;
import model.MoveType;
import model.Piece;
import model.PieceType;

public class MoveValidator {

    private final CheckDetector checkDetector = new CheckDetector();
    private final CastlingValidator castlingValidator = new CastlingValidator();
    
    public boolean isLegal(Move move, GameState state) {
        if (move.piece == null || move.piece.isWhite != state.isWhiteToMove()) {
            return false; // Can only move own pieces on own turn
        }

        if (state.isGameOver()) {
            return false;
        }

        // Cannot capture own piece
        if (move.captured != null && move.captured.isWhite == move.piece.isWhite) {
            return false;
        }

        // Must be pseudo-legal
        if (!isPseudoLegal(move, state)) {
            return false;
        }
        
        // Cannot capture King
        if (move.captured != null && move.captured.type == PieceType.KING) {
            return false;
        }

        // King must not be in check after move
        // Instead of actually applying the move (which is complex and modifies state), 
        // we can test check directly or clone state
        GameState nextState = new MoveApplier().apply(move, state);
        return !checkDetector.isKingInCheck(move.piece.isWhite, nextState);
    }
    
    public boolean isPseudoLegal(Move move, GameState state) {
        switch (move.piece.type) {
            case PAWN: return isValidPawnMove(move, state);
            case KNIGHT: return isValidKnightMove(move);
            case BISHOP: return isValidBishopMove(move, state);
            case ROOK: return isValidRookMove(move, state);
            case QUEEN: return isValidQueenMove(move, state);
            case KING: return isValidKingMove(move, state);
            default: return false;
        }
    }

    private boolean isValidPawnMove(Move move, GameState state) {
        int dir = move.piece.isWhite ? -1 : 1;
        int startRow = move.piece.isWhite ? 6 : 1;
        
        int dCol = move.toCol - move.fromCol;
        int dRow = move.toRow - move.fromRow;
        
        // Normal push
        if (dCol == 0 && dRow == dir && state.getPiece(move.toCol, move.toRow) == null) {
            return true;
        }
        
        // Double push
        if (dCol == 0 && dRow == 2 * dir && move.fromRow == startRow) {
            return state.getPiece(move.fromCol, move.fromRow + dir) == null && state.getPiece(move.toCol, move.toRow) == null;
        }
        
        // Capture
        if (Math.abs(dCol) == 1 && dRow == dir) {
            // Normal capture
            if (state.getPiece(move.toCol, move.toRow) != null) {
                return true;
            }
            // En passant
            if (state.getEnPassantTarget() == move.toRow * 8 + move.toCol) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidKnightMove(Move move) {
        int dx = Math.abs(move.toCol - move.fromCol);
        int dy = Math.abs(move.toRow - move.fromRow);
        return dx * dy == 2;
    }

    private boolean isValidBishopMove(Move move, GameState state) {
        int dx = Math.abs(move.toCol - move.fromCol);
        int dy = Math.abs(move.toRow - move.fromRow);
        if (dx != dy || dx == 0) return false;
        return isPathClear(move, state);
    }

    private boolean isValidRookMove(Move move, GameState state) {
        if (move.fromCol != move.toCol && move.fromRow != move.toRow) return false;
        if (move.fromCol == move.toCol && move.fromRow == move.toRow) return false;
        return isPathClear(move, state);
    }

    private boolean isValidQueenMove(Move move, GameState state) {
        int dx = Math.abs(move.toCol - move.fromCol);
        int dy = Math.abs(move.toRow - move.fromRow);
        if (dx != dy && move.fromCol != move.toCol && move.fromRow != move.toRow) return false;
        if (dx == 0 && dy == 0) return false;
        return isPathClear(move, state);
    }

    private boolean isValidKingMove(Move move, GameState state) {
        int dx = Math.abs(move.toCol - move.fromCol);
        int dy = Math.abs(move.toRow - move.fromRow);
        
        if (dx <= 1 && dy <= 1 && (dx > 0 || dy > 0)) {
            return true;
        }
        
        // Castling
        if (dy == 0 && dx == 2) {
            if (move.toCol == 6) {
                return castlingValidator.canCastle(move.piece.isWhite, true, state);
            } else if (move.toCol == 2) {
                return castlingValidator.canCastle(move.piece.isWhite, false, state);
            }
        }
        
        return false;
    }

    private boolean isPathClear(Move move, GameState state) {
        int dCol = Integer.compare(move.toCol, move.fromCol);
        int dRow = Integer.compare(move.toRow, move.fromRow);
        
        int c = move.fromCol + dCol;
        int r = move.fromRow + dRow;
        
        while (c != move.toCol || r != move.toRow) {
            if (state.getPiece(c, r) != null) {
                return false;
            }
            c += dCol;
            r += dRow;
        }
        return true;
    }

    public List<Move> getLegalMoves(Piece piece, GameState state) {
        // Implementation for getting all legal moves for a piece (needed for highlights)
        // Simplified version for now
        return new ArrayList<>();
    }
}
