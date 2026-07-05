package model;

public enum PieceType {
    KING('k', 0),
    QUEEN('q', 900),
    ROOK('r', 500),
    BISHOP('b', 330),
    KNIGHT('n', 320),
    PAWN('p', 100);

    private final char fenChar;
    private final int value;

    PieceType(char fenChar, int value) {
        this.fenChar = fenChar;
        this.value = value;
    }

    public char getFenChar(boolean isWhite) {
        return isWhite ? Character.toUpperCase(fenChar) : fenChar;
    }

    public int getValue() {
        return value;
    }

    public static PieceType fromFenChar(char c) {
        c = Character.toLowerCase(c);
        for (PieceType type : values()) {
            if (type.fenChar == c) {
                return type;
            }
        }
        return null;
    }
}
