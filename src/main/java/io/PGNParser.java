package io;

import model.GameState;
import model.Move;
import model.MoveType;
import model.PieceType;
import rules.MoveApplier;
import rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

public class PGNParser {

    public static List<Move> parsePGN(String pgn, GameState initialState) {
        List<Move> parsedMoves = new ArrayList<>();
        GameState state = initialState.copy();
        MoveGenerator moveGen = new MoveGenerator();
        MoveApplier moveApplier = new MoveApplier();

        // Strip comments, tags, and newlines
        pgn = pgn.replaceAll("\\[.*?\\]", ""); // tags
        pgn = pgn.replaceAll("\\{.*?\\}", ""); // comments
        pgn = pgn.replaceAll("\\s+", " ");
        
        String[] tokens = pgn.split(" ");
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;
            // Skip move numbers like "1." or "14..."
            if (token.matches(".*\\d+\\.*+")) continue;
            // Skip results
            if (token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*")) continue;

            Move m = parseSAN(token, state, moveGen);
            if (m != null) {
                parsedMoves.add(m);
                state = moveApplier.apply(m, state);
            } else {
                System.err.println("Failed to parse SAN: " + token + " in fen: " + io.FENParser.toFEN(state));
                break;
            }
        }
        return parsedMoves;
    }

    private static Move parseSAN(String san, GameState state, MoveGenerator moveGen) {
        if (san.equals("O-O") || san.equals("0-0")) {
            return findMove(state, moveGen, MoveType.KINGSIDE_CASTLE, -1, -1, null, -1, -1, null);
        }
        if (san.equals("O-O-O") || san.equals("0-0-0")) {
            return findMove(state, moveGen, MoveType.QUEENSIDE_CASTLE, -1, -1, null, -1, -1, null);
        }

        String clean = san.replaceAll("[+#x=]", "");
        
        PieceType promo = null;
        if (san.contains("=")) {
            char p = san.charAt(san.indexOf('=') + 1);
            promo = charToPieceType(p);
        } else if (clean.length() >= 3 && Character.isUpperCase(clean.charAt(clean.length()-1)) && Character.isLowerCase(clean.charAt(clean.length()-3))) {
            // e.g. e8Q without =
            promo = charToPieceType(clean.charAt(clean.length()-1));
            clean = clean.substring(0, clean.length()-1);
        }

        int toCol = clean.charAt(clean.length() - 2) - 'a';
        int toRow = 8 - (clean.charAt(clean.length() - 1) - '0');

        PieceType type = PieceType.PAWN;
        int fromCol = -1;
        int fromRow = -1;

        if (clean.length() > 2) {
            char first = clean.charAt(0);
            if (Character.isUpperCase(first)) {
                type = charToPieceType(first);
                if (clean.length() == 4) {
                    char dis = clean.charAt(1);
                    if (dis >= 'a' && dis <= 'h') fromCol = dis - 'a';
                    else if (dis >= '1' && dis <= '8') fromRow = 8 - (dis - '0');
                } else if (clean.length() == 5) {
                    fromCol = clean.charAt(1) - 'a';
                    fromRow = 8 - (clean.charAt(2) - '0');
                }
            } else {
                // Pawn capture, e.g. exd5 -> clean = ed5
                if (clean.length() == 3) {
                    fromCol = clean.charAt(0) - 'a';
                }
            }
        }

        return findMove(state, moveGen, null, toCol, toRow, type, fromCol, fromRow, promo);
    }

    private static Move findMove(GameState state, MoveGenerator gen, MoveType mType, 
                                 int toCol, int toRow, PieceType pType, 
                                 int fromCol, int fromRow, PieceType promo) {
        List<Move> legal = gen.generateLegalMoves(state);
        for (Move m : legal) {
            if (mType != null) {
                if (m.type == mType) return m;
                continue;
            }
            if (m.toCol == toCol && m.toRow == toRow && m.piece.type == pType) {
                if (fromCol != -1 && m.fromCol != fromCol) continue;
                if (fromRow != -1 && m.fromRow != fromRow) continue;
                if (promo != null && m.promotionPiece != promo) continue;
                return m;
            }
        }
        return null;
    }

    private static PieceType charToPieceType(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'N' -> PieceType.KNIGHT;
            case 'B' -> PieceType.BISHOP;
            case 'R' -> PieceType.ROOK;
            case 'Q' -> PieceType.QUEEN;
            case 'K' -> PieceType.KING;
            default -> PieceType.PAWN;
        };
    }
}
