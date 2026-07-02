package io;

import model.GameState;
import model.Piece;
import model.PieceType;

public class FENParser {

    public static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static GameState fromFEN(String fenString) {
        GameState state = new GameState();
        String[] parts = fenString.split(" ");
        String position = parts[0];
        int row = 0;
        int col = 0;

        for (int i = 0; i < position.length(); i++) {
            char ch = position.charAt(i);

            if (ch == '/') {
                row++;
                col = 0;
            } else if (Character.isDigit(ch)) {
                col += Character.getNumericValue(ch);
            } else {
                boolean isWhite = Character.isUpperCase(ch);
                PieceType type = PieceType.fromFenChar(ch);
                if (type != null) {
                    boolean hasMoved = false; // We can't know for sure from just the piece layout, must infer from castling rights
                    state.setPiece(col, row, new Piece(type, isWhite, hasMoved));
                }
                col++;
            }
        }
        
        if (parts.length > 1) {
            state.setWhiteToMove(parts[1].equals("w"));
        }

        if (parts.length > 2) {
            String castling = parts[2];
            state.setCastlingRight(0, castling.contains("K"));
            state.setCastlingRight(1, castling.contains("Q"));
            state.setCastlingRight(2, castling.contains("k"));
            state.setCastlingRight(3, castling.contains("q"));
            
            // Adjust hasMoved for Rooks and Kings based on castling rights
            Piece wk = state.getPiece(4, 7);
            if (wk != null && wk.type == PieceType.KING && wk.isWhite) {
                wk.hasMoved = !(state.getCastlingRight(0) || state.getCastlingRight(1));
            }
            Piece bk = state.getPiece(4, 0);
            if (bk != null && bk.type == PieceType.KING && !bk.isWhite) {
                bk.hasMoved = !(state.getCastlingRight(2) || state.getCastlingRight(3));
            }
            
            Piece wkr = state.getPiece(7, 7);
            if (wkr != null && wkr.type == PieceType.ROOK && wkr.isWhite) {
                wkr.hasMoved = !state.getCastlingRight(0);
            }
            Piece wqr = state.getPiece(0, 7);
            if (wqr != null && wqr.type == PieceType.ROOK && wqr.isWhite) {
                wqr.hasMoved = !state.getCastlingRight(1);
            }
            Piece bkr = state.getPiece(7, 0);
            if (bkr != null && bkr.type == PieceType.ROOK && !bkr.isWhite) {
                bkr.hasMoved = !state.getCastlingRight(2);
            }
            Piece bqr = state.getPiece(0, 0);
            if (bqr != null && bqr.type == PieceType.ROOK && !bqr.isWhite) {
                bqr.hasMoved = !state.getCastlingRight(3);
            }
        }

        if (parts.length > 3) {
            if (parts[3].equals("-")) {
                state.setEnPassantTarget(-1);
            } else {
                int file = parts[3].charAt(0) - 'a';
                int rank = '8' - parts[3].charAt(1);
                state.setEnPassantTarget(rank * 8 + file);
            }
        }

        if (parts.length > 4) {
            state.setHalfMoveClock(Integer.parseInt(parts[4]));
        }

        if (parts.length > 5) {
            state.setFullMoveNumber(Integer.parseInt(parts[5]));
        }

        return state;
    }

    public static String toFEN(GameState state) {
        StringBuilder fen = new StringBuilder();

        // Piece Placement
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = state.getPiece(col, row);
                if (piece == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(piece.type.getFenChar(piece.isWhite));
                }
            }
            if (empty > 0) {
                fen.append(empty);
            }
            if (row != 7) {
                fen.append('/');
            }
        }

        // Active Color
        fen.append(state.isWhiteToMove() ? " w " : " b ");

        // Castling
        StringBuilder castling = new StringBuilder();
        if (state.getCastlingRight(0)) castling.append("K");
        if (state.getCastlingRight(1)) castling.append("Q");
        if (state.getCastlingRight(2)) castling.append("k");
        if (state.getCastlingRight(3)) castling.append("q");

        if (castling.length() == 0) {
            fen.append("- ");
        } else {
            fen.append(castling).append(" ");
        }

        // En Passant
        int epTarget = state.getEnPassantTarget();
        if (epTarget == -1) {
            fen.append("- ");
        } else {
            int col = epTarget % 8;
            int row = epTarget / 8;
            char file = (char) ('a' + col);
            char rank = (char) ('8' - row);
            fen.append(file).append(rank).append(" ");
        }

        // Halfmove & Fullmove
        fen.append(state.getHalfMoveClock()).append(" ").append(state.getFullMoveNumber());

        return fen.toString();
    }
}
