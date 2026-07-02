package rules;

import model.Move;
import model.MoveType;
import model.PieceType;

public class SANFormatter {
    
    public String toSAN(Move move, boolean isCheck, boolean isCheckmate) {
        if (move.type == MoveType.KINGSIDE_CASTLE) return "O-O";
        if (move.type == MoveType.QUEENSIDE_CASTLE) return "O-O-O";
        
        StringBuilder sb = new StringBuilder();
        
        if (move.piece.type != PieceType.PAWN) {
            sb.append(move.piece.type.getFenChar(true));
            // Disambiguation is omitted for brevity right now, requires full move gen
        }
        
        if (move.captured != null || move.type == MoveType.EN_PASSANT) {
            if (move.piece.type == PieceType.PAWN) {
                sb.append((char) ('a' + move.fromCol));
            }
            sb.append('x');
        }
        
        sb.append((char) ('a' + move.toCol));
        sb.append((char) ('8' - move.toRow));
        
        if (move.type == MoveType.PROMOTION && move.promotionPiece != null) {
            sb.append('=').append(move.promotionPiece.getFenChar(true));
        }
        
        if (isCheckmate) {
            sb.append('#');
        } else if (isCheck) {
            sb.append('+');
        }
        
        return sb.toString();
    }
}
