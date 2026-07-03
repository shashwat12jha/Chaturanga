package io.chaturanga.engine;

/** Reusable state required to reverse one move exactly. */
public final class Undo {
    int movedPiece;
    int capturedPiece;
    int captureSquare;
    int castlingRights;
    int enPassantSquare;
    int halfmoveClock;
    int fullmoveNumber;
    int historySize;
    long zobristKey;
}
