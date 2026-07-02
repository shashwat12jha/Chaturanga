package rules;

import model.GameState;
import model.Piece;
import model.PieceType;

public class CheckDetector {

    public boolean isKingInCheck(boolean whiteKing, GameState state) {
        // Find king
        int kingCol = -1, kingRow = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.getPiece(c, r);
                if (p != null && p.type == PieceType.KING && p.isWhite == whiteKing) {
                    kingCol = c;
                    kingRow = r;
                    break;
                }
            }
            if (kingCol != -1) break;
        }
        
        if (kingCol == -1) return false; // Should not happen in a valid game

        return isSquareAttacked(kingCol, kingRow, !whiteKing, state);
    }

    public boolean isSquareAttacked(int col, int row, boolean byWhite, GameState state) {
        return hitBySlider(col, row, byWhite, state, 0, 1, PieceType.ROOK, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, 0, -1, PieceType.ROOK, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, 1, 0, PieceType.ROOK, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, -1, 0, PieceType.ROOK, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, 1, 1, PieceType.BISHOP, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, 1, -1, PieceType.BISHOP, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, -1, 1, PieceType.BISHOP, PieceType.QUEEN) ||
               hitBySlider(col, row, byWhite, state, -1, -1, PieceType.BISHOP, PieceType.QUEEN) ||
               hitByKnight(col, row, byWhite, state) ||
               hitByPawn(col, row, byWhite, state) ||
               hitByKing(col, row, byWhite, state);
    }

    private boolean hitBySlider(int col, int row, boolean byWhite, GameState state, int dCol, int dRow, PieceType type1, PieceType type2) {
        for (int i = 1; i < 8; i++) {
            int c = col + i * dCol;
            int r = row + i * dRow;
            if (c < 0 || c > 7 || r < 0 || r > 7) break;
            
            Piece p = state.getPiece(c, r);
            if (p != null) {
                if (p.isWhite == byWhite && (p.type == type1 || p.type == type2)) {
                    return true;
                }
                break; // Blocked by another piece
            }
        }
        return false;
    }

    private boolean hitByKnight(int col, int row, boolean byWhite, GameState state) {
        int[][] jumps = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        for (int[] jump : jumps) {
            int c = col + jump[0];
            int r = row + jump[1];
            if (c >= 0 && c <= 7 && r >= 0 && r <= 7) {
                Piece p = state.getPiece(c, r);
                if (p != null && p.isWhite == byWhite && p.type == PieceType.KNIGHT) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hitByPawn(int col, int row, boolean byWhite, GameState state) {
        int pawnDir = byWhite ? 1 : -1; // White pawns attack "up" the board visually (lower row index to higher, wait, white pawns start at row 6 and go to row 0. So they attack row - 1).
        // Let's verify pawn direction. White pawns start at row 6, attack row 5. So pawnDir for attacking white pawns is +1 from the target's perspective (if target is at row 5, white pawn is at row 6 = 5+1).
        // Wait, if square is (col, row), is it attacked by a pawn at (col +/- 1, row + pawnDir)?
        // Yes, if white attacks, the white pawn must be at row + 1.
        int attackRow = row + (byWhite ? 1 : -1); 
        
        if (attackRow >= 0 && attackRow <= 7) {
            Piece p1 = state.getPiece(col - 1, attackRow);
            if (p1 != null && p1.isWhite == byWhite && p1.type == PieceType.PAWN) return true;
            
            Piece p2 = state.getPiece(col + 1, attackRow);
            if (p2 != null && p2.isWhite == byWhite && p2.type == PieceType.PAWN) return true;
        }
        return false;
    }

    private boolean hitByKing(int col, int row, boolean byWhite, GameState state) {
        int[][] steps = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] step : steps) {
            int c = col + step[0];
            int r = row + step[1];
            if (c >= 0 && c <= 7 && r >= 0 && r <= 7) {
                Piece p = state.getPiece(c, r);
                if (p != null && p.isWhite == byWhite && p.type == PieceType.KING) {
                    return true;
                }
            }
        }
        return false;
    }
}
