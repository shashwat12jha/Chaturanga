package io.chaturanga.engine;

/** Strict Forsyth-Edwards Notation parser and serializer. */
public final class Fen {
    public static final String START_POSITION =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private Fen() {}

    public static Position parse(String fen) {
        if (fen == null) throw new IllegalArgumentException("FEN cannot be null");
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6) throw new IllegalArgumentException("FEN must contain six fields");

        Position position = new Position();
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("FEN must contain eight ranks");

        int whiteKings = 0;
        int blackKings = 0;
        for (int fenRank = 0; fenRank < 8; fenRank++) {
            int file = 0;
            for (int i = 0; i < ranks[fenRank].length(); i++) {
                char symbol = ranks[fenRank].charAt(i);
                if (symbol >= '1' && symbol <= '8') {
                    file += symbol - '0';
                } else {
                    if (file >= 8) throw new IllegalArgumentException("Too many squares in FEN rank");
                    int piece = Piece.fromFen(symbol);
                    int square = (7 - fenRank) * 8 + file;
                    position.putInitialPiece(piece, square);
                    if (piece == Piece.WHITE_KING) whiteKings++;
                    if (piece == Piece.BLACK_KING) blackKings++;
                    file++;
                }
            }
            if (file != 8) throw new IllegalArgumentException("FEN rank does not contain eight squares");
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new IllegalArgumentException("FEN must contain exactly one king per side");
        }

        position.setSideToMove(switch (fields[1]) {
            case "w" -> Piece.WHITE;
            case "b" -> Piece.BLACK;
            default -> throw new IllegalArgumentException("Invalid active color");
        });

        int rights = 0;
        if (!fields[2].equals("-")) {
            for (char right : fields[2].toCharArray()) {
                int bit = switch (right) {
                    case 'K' -> Position.CASTLE_WHITE_KING;
                    case 'Q' -> Position.CASTLE_WHITE_QUEEN;
                    case 'k' -> Position.CASTLE_BLACK_KING;
                    case 'q' -> Position.CASTLE_BLACK_QUEEN;
                    default -> throw new IllegalArgumentException("Invalid castling field");
                };
                if ((rights & bit) != 0) throw new IllegalArgumentException("Duplicate castling right");
                rights |= bit;
            }
        }
        position.setCastlingRights(rights);

        int ep = fields[3].equals("-") ? -1 : Move.parseSquare(fields[3]);
        if (!fields[3].equals("-") && ep == -1) throw new IllegalArgumentException("Invalid en-passant square");
        if (ep != -1 && ep / 8 != (position.sideToMove() == Piece.WHITE ? 5 : 2)) {
            throw new IllegalArgumentException("En-passant square is on an impossible rank");
        }
        position.setEnPassantSquare(ep);

        try {
            int halfmove = Integer.parseInt(fields[4]);
            int fullmove = Integer.parseInt(fields[5]);
            if (halfmove < 0 || fullmove < 1) throw new NumberFormatException();
            position.setHalfmoveClock(halfmove);
            position.setFullmoveNumber(fullmove);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid move counters", exception);
        }

        position.finishInitialization();
        return position;
    }

    public static String format(Position position) {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = position.pieceAt(rank * 8 + file);
                if (piece == Piece.NONE) {
                    empty++;
                } else {
                    if (empty > 0) fen.append(empty);
                    empty = 0;
                    fen.append(Piece.toFen(piece));
                }
            }
            if (empty > 0) fen.append(empty);
            if (rank > 0) fen.append('/');
        }

        fen.append(position.sideToMove() == Piece.WHITE ? " w " : " b ");
        int rights = position.castlingRights();
        if (rights == 0) {
            fen.append('-');
        } else {
            if ((rights & Position.CASTLE_WHITE_KING) != 0) fen.append('K');
            if ((rights & Position.CASTLE_WHITE_QUEEN) != 0) fen.append('Q');
            if ((rights & Position.CASTLE_BLACK_KING) != 0) fen.append('k');
            if ((rights & Position.CASTLE_BLACK_QUEEN) != 0) fen.append('q');
        }
        fen.append(' ').append(position.enPassantSquare() == -1 ? "-" : Move.squareName(position.enPassantSquare()));
        fen.append(' ').append(position.halfmoveClock()).append(' ').append(position.fullmoveNumber());
        return fen.toString();
    }
}
