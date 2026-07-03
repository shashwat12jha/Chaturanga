package io.chaturanga.engine;

/** Compact piece and color constants used by the engine core. */
public final class Piece {
    public static final int WHITE = 0;
    public static final int BLACK = 1;

    public static final int PAWN = 0;
    public static final int KNIGHT = 1;
    public static final int BISHOP = 2;
    public static final int ROOK = 3;
    public static final int QUEEN = 4;
    public static final int KING = 5;

    public static final int WHITE_PAWN = 0;
    public static final int WHITE_KNIGHT = 1;
    public static final int WHITE_BISHOP = 2;
    public static final int WHITE_ROOK = 3;
    public static final int WHITE_QUEEN = 4;
    public static final int WHITE_KING = 5;
    public static final int BLACK_PAWN = 6;
    public static final int BLACK_KNIGHT = 7;
    public static final int BLACK_BISHOP = 8;
    public static final int BLACK_ROOK = 9;
    public static final int BLACK_QUEEN = 10;
    public static final int BLACK_KING = 11;

    public static final int NONE = -1;

    private Piece() {}

    public static int of(int color, int type) {
        return color * 6 + type;
    }

    public static int color(int piece) {
        return piece / 6;
    }

    public static int type(int piece) {
        return piece % 6;
    }

    public static boolean isColor(int piece, int color) {
        return piece != NONE && color(piece) == color;
    }

    public static char toFen(int piece) {
        char symbol = "pnbrqk".charAt(type(piece));
        return color(piece) == WHITE ? Character.toUpperCase(symbol) : symbol;
    }

    public static int fromFen(char symbol) {
        int type = switch (Character.toLowerCase(symbol)) {
            case 'p' -> PAWN;
            case 'n' -> KNIGHT;
            case 'b' -> BISHOP;
            case 'r' -> ROOK;
            case 'q' -> QUEEN;
            case 'k' -> KING;
            default -> throw new IllegalArgumentException("Invalid FEN piece: " + symbol);
        };
        return of(Character.isUpperCase(symbol) ? WHITE : BLACK, type);
    }
}
