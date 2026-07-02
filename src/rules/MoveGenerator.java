package rules;

import java.util.ArrayList;
import java.util.List;

import model.GameState;
import model.Move;
import model.MoveType;
import model.Piece;
import model.PieceType;

public class MoveGenerator {
    
    private final MoveValidator validator = new MoveValidator();

    public List<Move> generateLegalMoves(GameState state) {
        List<Move> pseudoLegalMoves = generatePseudoLegalMoves(state);
        List<Move> legalMoves = new ArrayList<>();
        
        for (Move move : pseudoLegalMoves) {
            if (validator.isLegal(move, state)) {
                legalMoves.add(move);
            }
        }
        return legalMoves;
    }
    
    public List<Move> generatePseudoLegalMoves(GameState state) {
        List<Move> moves = new ArrayList<>();
        boolean isWhite = state.isWhiteToMove();
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state.getPiece(c, r);
                if (p != null && p.isWhite == isWhite) {
                    generatePieceMoves(c, r, p, state, moves);
                }
            }
        }
        return moves;
    }
    
    private void generatePieceMoves(int c, int r, Piece p, GameState state, List<Move> moves) {
        switch (p.type) {
            case PAWN: generatePawnMoves(c, r, p, state, moves); break;
            case KNIGHT: generateKnightMoves(c, r, p, state, moves); break;
            case BISHOP: generateSliderMoves(c, r, p, state, moves, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}); break;
            case ROOK: generateSliderMoves(c, r, p, state, moves, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}); break;
            case QUEEN: generateSliderMoves(c, r, p, state, moves, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}); break;
            case KING: generateKingMoves(c, r, p, state, moves); break;
        }
    }

    private void generatePawnMoves(int c, int r, Piece p, GameState state, List<Move> moves) {
        int dir = p.isWhite ? -1 : 1;
        int startRow = p.isWhite ? 6 : 1;
        int promoRow = p.isWhite ? 0 : 7;
        
        // Single push
        if (r + dir >= 0 && r + dir < 8 && state.getPiece(c, r + dir) == null) {
            addPawnMove(c, r, c, r + dir, p, null, MoveType.NORMAL, promoRow, moves);
            
            // Double push
            if (r == startRow && state.getPiece(c, r + 2 * dir) == null) {
                moves.add(new Move(c, r, c, r + 2 * dir, p, null, MoveType.NORMAL, null));
            }
        }
        
        // Captures
        for (int dc : new int[]{-1, 1}) {
            if (c + dc >= 0 && c + dc < 8 && r + dir >= 0 && r + dir < 8) {
                Piece cap = state.getPiece(c + dc, r + dir);
                if (cap != null && cap.isWhite != p.isWhite) {
                    addPawnMove(c, r, c + dc, r + dir, p, cap, MoveType.NORMAL, promoRow, moves);
                } else if (state.getEnPassantTarget() == (r + dir) * 8 + (c + dc)) {
                    cap = state.getPiece(c + dc, r); // The pawn being captured
                    addPawnMove(c, r, c + dc, r + dir, p, cap, MoveType.EN_PASSANT, promoRow, moves);
                }
            }
        }
    }
    
    private void addPawnMove(int fc, int fr, int tc, int tr, Piece p, Piece cap, MoveType type, int promoRow, List<Move> moves) {
        if (tr == promoRow) {
            moves.add(new Move(fc, fr, tc, tr, p, cap, MoveType.PROMOTION, PieceType.QUEEN));
            moves.add(new Move(fc, fr, tc, tr, p, cap, MoveType.PROMOTION, PieceType.ROOK));
            moves.add(new Move(fc, fr, tc, tr, p, cap, MoveType.PROMOTION, PieceType.BISHOP));
            moves.add(new Move(fc, fr, tc, tr, p, cap, MoveType.PROMOTION, PieceType.KNIGHT));
        } else {
            moves.add(new Move(fc, fr, tc, tr, p, cap, type, null));
        }
    }

    private void generateKnightMoves(int c, int r, Piece p, GameState state, List<Move> moves) {
        int[][] jumps = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        for (int[] jump : jumps) {
            int tc = c + jump[0];
            int tr = r + jump[1];
            if (tc >= 0 && tc < 8 && tr >= 0 && tr < 8) {
                Piece cap = state.getPiece(tc, tr);
                if (cap == null || cap.isWhite != p.isWhite) {
                    moves.add(new Move(c, r, tc, tr, p, cap, MoveType.NORMAL, null));
                }
            }
        }
    }

    private void generateSliderMoves(int c, int r, Piece p, GameState state, List<Move> moves, int[][] dirs) {
        for (int[] dir : dirs) {
            int tc = c + dir[0];
            int tr = r + dir[1];
            while (tc >= 0 && tc < 8 && tr >= 0 && tr < 8) {
                Piece cap = state.getPiece(tc, tr);
                if (cap == null) {
                    moves.add(new Move(c, r, tc, tr, p, null, MoveType.NORMAL, null));
                } else {
                    if (cap.isWhite != p.isWhite) {
                        moves.add(new Move(c, r, tc, tr, p, cap, MoveType.NORMAL, null));
                    }
                    break; // Blocked
                }
                tc += dir[0];
                tr += dir[1];
            }
        }
    }

    private void generateKingMoves(int c, int r, Piece p, GameState state, List<Move> moves) {
        int[][] steps = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] step : steps) {
            int tc = c + step[0];
            int tr = r + step[1];
            if (tc >= 0 && tc < 8 && tr >= 0 && tr < 8) {
                Piece cap = state.getPiece(tc, tr);
                if (cap == null || cap.isWhite != p.isWhite) {
                    moves.add(new Move(c, r, tc, tr, p, cap, MoveType.NORMAL, null));
                }
            }
        }
        
        // Castling (pseudo-legal check is handled in validator)
        if (p.isWhite) {
            if (state.getCastlingRight(0)) moves.add(new Move(c, r, 6, r, p, null, MoveType.KINGSIDE_CASTLE, null));
            if (state.getCastlingRight(1)) moves.add(new Move(c, r, 2, r, p, null, MoveType.QUEENSIDE_CASTLE, null));
        } else {
            if (state.getCastlingRight(2)) moves.add(new Move(c, r, 6, r, p, null, MoveType.KINGSIDE_CASTLE, null));
            if (state.getCastlingRight(3)) moves.add(new Move(c, r, 2, r, p, null, MoveType.QUEENSIDE_CASTLE, null));
        }
    }
}
