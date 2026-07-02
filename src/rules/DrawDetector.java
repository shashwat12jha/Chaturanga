package rules;

import io.FENParser;
import model.GameState;
import model.Piece;
import model.PieceType;

public class DrawDetector {

    public boolean isFiftyMoveRule(GameState state) {
        return state.getHalfMoveClock() >= 100;
    }

    public boolean isThreefoldRepetition(GameState state) {
        String currentFen = FENParser.toFEN(state);
        // We only care about the first 4 parts of FEN for repetition (piece placement, active color, castling, en passant)
        String[] parts = currentFen.split(" ");
        String baseFen = parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3];
        
        int count = 0;
        for (String historyFen : state.getPositionHistory().keySet()) {
            String[] historyParts = historyFen.split(" ");
            String historyBase = historyParts[0] + " " + historyParts[1] + " " + historyParts[2] + " " + historyParts[3];
            if (baseFen.equals(historyBase)) {
                count += state.getPositionHistory().get(historyFen);
            }
        }
        return count >= 3;
    }

    public boolean isInsufficientMaterial(GameState state) {
        int whiteKnights = 0;
        int whiteBishops = 0;
        int whiteDarkBishops = 0;
        int whiteLightBishops = 0;
        
        int blackKnights = 0;
        int blackBishops = 0;
        int blackDarkBishops = 0;
        int blackLightBishops = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.getPiece(c, r);
                if (p != null) {
                    if (p.type == PieceType.PAWN || p.type == PieceType.ROOK || p.type == PieceType.QUEEN) {
                        return false; // Sufficient material
                    }
                    if (p.type == PieceType.KNIGHT) {
                        if (p.isWhite) whiteKnights++;
                        else blackKnights++;
                    } else if (p.type == PieceType.BISHOP) {
                        boolean isLightSquare = (r + c) % 2 == 0;
                        if (p.isWhite) {
                            whiteBishops++;
                            if (isLightSquare) whiteLightBishops++;
                            else whiteDarkBishops++;
                        } else {
                            blackBishops++;
                            if (isLightSquare) blackLightBishops++;
                            else blackDarkBishops++;
                        }
                    }
                }
            }
        }

        // K vs K
        if (whiteKnights == 0 && whiteBishops == 0 && blackKnights == 0 && blackBishops == 0) {
            return true;
        }

        // K+B vs K or K+N vs K
        if (whiteKnights + whiteBishops == 1 && blackKnights == 0 && blackBishops == 0) {
            return true;
        }
        if (blackKnights + blackBishops == 1 && whiteKnights == 0 && whiteBishops == 0) {
            return true;
        }

        // K+B vs K+B (same-colored bishops)
        if (whiteKnights == 0 && blackKnights == 0 && whiteBishops == 1 && blackBishops == 1) {
            if ((whiteLightBishops == 1 && blackLightBishops == 1) || (whiteDarkBishops == 1 && blackDarkBishops == 1)) {
                return true;
            }
        }

        return false;
    }
}
