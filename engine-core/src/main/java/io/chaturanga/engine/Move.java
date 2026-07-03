package io.chaturanga.engine;

import java.util.Objects;

/** Immutable move value. Squares use a1=0 through h8=63. */
public final class Move {
    public static final int QUIET = 0;
    public static final int DOUBLE_PAWN_PUSH = 1;
    public static final int KING_CASTLE = 2;
    public static final int QUEEN_CASTLE = 3;
    public static final int CAPTURE = 4;
    public static final int EN_PASSANT = 5;
    public static final int PROMOTION = 8;
    public static final int PROMOTION_CAPTURE = 12;

    public static final Move NONE = new Move(0, 0, QUIET, Piece.NONE);

    private final int from;
    private final int to;
    private final int flag;
    private final int promotion;

    public Move(int from, int to, int flag) {
        this(from, to, flag, Piece.NONE);
    }

    public Move(int from, int to, int flag, int promotion) {
        if (from < 0 || from > 63 || to < 0 || to > 63) {
            throw new IllegalArgumentException("Move square outside board");
        }
        this.from = from;
        this.to = to;
        this.flag = flag;
        this.promotion = promotion;
    }

    public int from() { return from; }
    public int to() { return to; }
    public int flag() { return flag; }
    public int promotion() { return promotion; }

    public boolean isCapture() {
        return flag == CAPTURE || flag == EN_PASSANT || flag == PROMOTION_CAPTURE;
    }

    public boolean isPromotion() {
        return flag == PROMOTION || flag == PROMOTION_CAPTURE;
    }

    public boolean isCastle() {
        return flag == KING_CASTLE || flag == QUEEN_CASTLE;
    }

    public int encode() {
        int promo = promotion == Piece.NONE ? 0 : promotion + 1;
        return from | (to << 6) | (flag << 12) | (promo << 16);
    }

    public static Move decode(int encoded) {
        if (encoded == 0) return NONE;
        int promoBits = (encoded >>> 16) & 7;
        return new Move(encoded & 63, (encoded >>> 6) & 63,
                (encoded >>> 12) & 15, promoBits == 0 ? Piece.NONE : promoBits - 1);
    }

    public String toUci() {
        if (this == NONE) return "0000";
        String text = squareName(from) + squareName(to);
        if (isPromotion()) text += " nbrq".charAt(promotion());
        return text;
    }

    public static int parseSquare(String square) {
        if (square == null || square.length() != 2) return -1;
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';
        return file >= 0 && file < 8 && rank >= 0 && rank < 8 ? rank * 8 + file : -1;
    }

    public static String squareName(int square) {
        return "" + (char) ('a' + square % 8) + (char) ('1' + square / 8);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Move other)) return false;
        return from == other.from && to == other.to && flag == other.flag && promotion == other.promotion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, flag, promotion);
    }

    @Override
    public String toString() {
        return toUci();
    }
}
