package model;

import java.util.Objects;

public class Move {
    public final int fromCol;
    public final int fromRow;
    public final int toCol;
    public final int toRow;
    public final Piece piece;
    public final Piece captured;
    public final MoveType type;
    public final PieceType promotionPiece;

    public Move(int fromCol, int fromRow, int toCol, int toRow, Piece piece, Piece captured, MoveType type, PieceType promotionPiece) {
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.piece = piece;
        this.captured = captured;
        this.type = type;
        this.promotionPiece = promotionPiece;
    }

    public Move(int fromCol, int fromRow, int toCol, int toRow, Piece piece, Piece captured, MoveType type) {
        this(fromCol, fromRow, toCol, toRow, piece, captured, type, null);
    }
    
    public Move(int fromCol, int fromRow, int toCol, int toRow, Piece piece) {
        this(fromCol, fromRow, toCol, toRow, piece, null, MoveType.NORMAL, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return fromCol == move.fromCol && fromRow == move.fromRow && toCol == move.toCol && toRow == move.toRow &&
                type == move.type && promotionPiece == move.promotionPiece &&
                Objects.equals(piece, move.piece) && Objects.equals(captured, move.captured);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromCol, fromRow, toCol, toRow, piece, captured, type, promotionPiece);
    }
}
