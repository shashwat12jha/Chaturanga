package io.chaturanga.engine;

import java.util.Arrays;

/**
 * Mutable search position backed by twelve bitboards and a mailbox.
 * Search uses make/unmake; callers can use copy() at ownership boundaries.
 */
public final class Position {
    public static final int CASTLE_WHITE_KING = 1;
    public static final int CASTLE_WHITE_QUEEN = 2;
    public static final int CASTLE_BLACK_KING = 4;
    public static final int CASTLE_BLACK_QUEEN = 8;

    private final long[] pieces = new long[12];
    private final byte[] board = new byte[64];
    private final long[] keyHistory = new long[2048];

    private long whitePieces;
    private long blackPieces;
    private long occupied;
    private int sideToMove;
    private int castlingRights;
    private int enPassantSquare;
    private int halfmoveClock;
    private int fullmoveNumber;
    private long zobristKey;
    private int historySize;

    public Position() {
        Arrays.fill(board, (byte) Piece.NONE);
        enPassantSquare = -1;
        fullmoveNumber = 1;
    }

    public static Position start() {
        return Fen.parse(Fen.START_POSITION);
    }

    public Position copy() {
        Position copy = new Position();
        System.arraycopy(pieces, 0, copy.pieces, 0, pieces.length);
        System.arraycopy(board, 0, copy.board, 0, board.length);
        System.arraycopy(keyHistory, 0, copy.keyHistory, 0, historySize);
        copy.whitePieces = whitePieces;
        copy.blackPieces = blackPieces;
        copy.occupied = occupied;
        copy.sideToMove = sideToMove;
        copy.castlingRights = castlingRights;
        copy.enPassantSquare = enPassantSquare;
        copy.halfmoveClock = halfmoveClock;
        copy.fullmoveNumber = fullmoveNumber;
        copy.zobristKey = zobristKey;
        copy.historySize = historySize;
        return copy;
    }

    public int pieceAt(int square) { return board[square]; }
    public long pieces(int piece) { return pieces[piece]; }
    public long colorPieces(int color) { return color == Piece.WHITE ? whitePieces : blackPieces; }
    public long occupied() { return occupied; }
    public int sideToMove() { return sideToMove; }
    public int castlingRights() { return castlingRights; }
    public int enPassantSquare() { return enPassantSquare; }
    public int halfmoveClock() { return halfmoveClock; }
    public int fullmoveNumber() { return fullmoveNumber; }
    public long zobristKey() { return zobristKey; }

    void setSideToMove(int side) { sideToMove = side; }
    void setCastlingRights(int rights) { castlingRights = rights; }
    void setEnPassantSquare(int square) { enPassantSquare = square; }
    void setHalfmoveClock(int clock) { halfmoveClock = clock; }
    void setFullmoveNumber(int number) { fullmoveNumber = number; }

    void putInitialPiece(int piece, int square) {
        if (board[square] != Piece.NONE) throw new IllegalArgumentException("Duplicate FEN square");
        addPiece(piece, square);
    }

    void finishInitialization() {
        zobristKey = computeZobrist();
        historySize = 1;
        keyHistory[0] = zobristKey;
    }

    public void makeMove(Move move, Undo undo) {
        int from = move.from();
        int to = move.to();
        int moving = board[from];
        if (moving == Piece.NONE || Piece.color(moving) != sideToMove) {
            throw new IllegalArgumentException("No side-to-move piece on " + Move.squareName(from));
        }

        undo.movedPiece = moving;
        undo.castlingRights = castlingRights;
        undo.enPassantSquare = enPassantSquare;
        undo.halfmoveClock = halfmoveClock;
        undo.fullmoveNumber = fullmoveNumber;
        undo.zobristKey = zobristKey;
        undo.historySize = historySize;
        undo.captureSquare = move.flag() == Move.EN_PASSANT
                ? to + (sideToMove == Piece.WHITE ? -8 : 8) : to;
        undo.capturedPiece = board[undo.captureSquare];

        if (enPassantSquare != -1) zobristKey ^= Zobrist.EN_PASSANT_FILE[enPassantSquare & 7];
        enPassantSquare = -1;

        removePiece(moving, from);
        if (undo.capturedPiece != Piece.NONE) removePiece(undo.capturedPiece, undo.captureSquare);

        int placedPiece = move.isPromotion() ? Piece.of(sideToMove, move.promotion()) : moving;
        addPiece(placedPiece, to);

        if (move.flag() == Move.KING_CASTLE) {
            int rookFrom = sideToMove == Piece.WHITE ? 7 : 63;
            int rookTo = sideToMove == Piece.WHITE ? 5 : 61;
            movePiece(Piece.of(sideToMove, Piece.ROOK), rookFrom, rookTo);
        } else if (move.flag() == Move.QUEEN_CASTLE) {
            int rookFrom = sideToMove == Piece.WHITE ? 0 : 56;
            int rookTo = sideToMove == Piece.WHITE ? 3 : 59;
            movePiece(Piece.of(sideToMove, Piece.ROOK), rookFrom, rookTo);
        }

        zobristKey ^= Zobrist.CASTLING[castlingRights];
        updateCastlingRights(moving, from, undo.capturedPiece, undo.captureSquare);
        zobristKey ^= Zobrist.CASTLING[castlingRights];

        if (move.flag() == Move.DOUBLE_PAWN_PUSH) {
            enPassantSquare = sideToMove == Piece.WHITE ? from + 8 : from - 8;
            zobristKey ^= Zobrist.EN_PASSANT_FILE[enPassantSquare & 7];
        }

        halfmoveClock = Piece.type(moving) == Piece.PAWN || undo.capturedPiece != Piece.NONE
                ? 0 : halfmoveClock + 1;
        if (sideToMove == Piece.BLACK) fullmoveNumber++;
        sideToMove ^= 1;
        zobristKey ^= Zobrist.SIDE_TO_MOVE;

        if (historySize >= keyHistory.length) throw new IllegalStateException("Position history overflow");
        keyHistory[historySize++] = zobristKey;
    }

    public void unmakeMove(Move move, Undo undo) {
        sideToMove ^= 1;

        int placedPiece = board[move.to()];
        removePiece(placedPiece, move.to());

        if (move.flag() == Move.KING_CASTLE) {
            int rookFrom = sideToMove == Piece.WHITE ? 7 : 63;
            int rookTo = sideToMove == Piece.WHITE ? 5 : 61;
            movePiece(Piece.of(sideToMove, Piece.ROOK), rookTo, rookFrom);
        } else if (move.flag() == Move.QUEEN_CASTLE) {
            int rookFrom = sideToMove == Piece.WHITE ? 0 : 56;
            int rookTo = sideToMove == Piece.WHITE ? 3 : 59;
            movePiece(Piece.of(sideToMove, Piece.ROOK), rookTo, rookFrom);
        }

        addPiece(undo.movedPiece, move.from());
        if (undo.capturedPiece != Piece.NONE) addPiece(undo.capturedPiece, undo.captureSquare);

        castlingRights = undo.castlingRights;
        enPassantSquare = undo.enPassantSquare;
        halfmoveClock = undo.halfmoveClock;
        fullmoveNumber = undo.fullmoveNumber;
        historySize = undo.historySize;
        zobristKey = undo.zobristKey;
    }

    public boolean isRepetition() {
        int reversiblePlies = Math.min(halfmoveClock, historySize - 1);
        int start = historySize - 3;
        int end = Math.max(0, historySize - 1 - reversiblePlies);
        for (int i = start; i >= end; i -= 2) {
            if (keyHistory[i] == zobristKey) return true;
        }
        return false;
    }

    public boolean isInsufficientMaterial() {
        if (pieces[Piece.WHITE_PAWN] != 0 || pieces[Piece.BLACK_PAWN] != 0
                || pieces[Piece.WHITE_ROOK] != 0 || pieces[Piece.BLACK_ROOK] != 0
                || pieces[Piece.WHITE_QUEEN] != 0 || pieces[Piece.BLACK_QUEEN] != 0) return false;

        int knights = Long.bitCount(pieces[Piece.WHITE_KNIGHT] | pieces[Piece.BLACK_KNIGHT]);
        long bishops = pieces[Piece.WHITE_BISHOP] | pieces[Piece.BLACK_BISHOP];
        int bishopCount = Long.bitCount(bishops);
        if (knights + bishopCount <= 1) return true;
        if (knights != 0) return false;

        int squareColor = -1;
        while (bishops != 0) {
            int square = Long.numberOfTrailingZeros(bishops);
            bishops &= bishops - 1;
            int color = ((square & 7) + (square >>> 3)) & 1;
            if (squareColor == -1) squareColor = color;
            else if (color != squareColor) return false;
        }
        return true;
    }

    private void updateCastlingRights(int moving, int from, int captured, int captureSquare) {
        if (Piece.type(moving) == Piece.KING) {
            castlingRights &= Piece.color(moving) == Piece.WHITE ? ~3 : ~12;
        }
        if (Piece.type(moving) == Piece.ROOK) clearRookRight(from);
        if (captured != Piece.NONE && Piece.type(captured) == Piece.ROOK) clearRookRight(captureSquare);
    }

    private void clearRookRight(int square) {
        castlingRights &= switch (square) {
            case 7 -> ~CASTLE_WHITE_KING;
            case 0 -> ~CASTLE_WHITE_QUEEN;
            case 63 -> ~CASTLE_BLACK_KING;
            case 56 -> ~CASTLE_BLACK_QUEEN;
            default -> -1;
        };
    }

    private void addPiece(int piece, int square) {
        long bit = 1L << square;
        pieces[piece] |= bit;
        board[square] = (byte) piece;
        if (Piece.color(piece) == Piece.WHITE) whitePieces |= bit; else blackPieces |= bit;
        occupied |= bit;
        zobristKey ^= Zobrist.PIECE_SQUARE[piece][square];
    }

    private void removePiece(int piece, int square) {
        long bit = 1L << square;
        pieces[piece] &= ~bit;
        board[square] = (byte) Piece.NONE;
        if (Piece.color(piece) == Piece.WHITE) whitePieces &= ~bit; else blackPieces &= ~bit;
        occupied &= ~bit;
        zobristKey ^= Zobrist.PIECE_SQUARE[piece][square];
    }

    private void movePiece(int piece, int from, int to) {
        if (board[from] != piece || board[to] != Piece.NONE) {
            throw new IllegalStateException("Invalid castling rook placement");
        }
        removePiece(piece, from);
        addPiece(piece, to);
    }

    private long computeZobrist() {
        long key = 0;
        for (int piece = 0; piece < 12; piece++) {
            long bits = pieces[piece];
            while (bits != 0) {
                int square = Long.numberOfTrailingZeros(bits);
                key ^= Zobrist.PIECE_SQUARE[piece][square];
                bits &= bits - 1;
            }
        }
        if (sideToMove == Piece.BLACK) key ^= Zobrist.SIDE_TO_MOVE;
        key ^= Zobrist.CASTLING[castlingRights];
        if (enPassantSquare != -1) key ^= Zobrist.EN_PASSANT_FILE[enPassantSquare & 7];
        return key;
    }
}
