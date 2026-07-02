package model;

import java.util.Objects;

public class Piece {
    public final PieceType type;
    public final boolean isWhite;
    public boolean hasMoved;

    public Piece(PieceType type, boolean isWhite, boolean hasMoved) {
        this.type = type;
        this.isWhite = isWhite;
        this.hasMoved = hasMoved;
    }

    public Piece(PieceType type, boolean isWhite) {
        this(type, isWhite, false);
    }
    
    public Piece copy() {
        return new Piece(type, isWhite, hasMoved);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Piece piece = (Piece) o;
        return isWhite == piece.isWhite && hasMoved == piece.hasMoved && type == piece.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, isWhite, hasMoved);
    }
}
