package engine;

import model.GameState;
import model.Piece;
import model.PieceType;

/**
 * Static Exchange Evaluation (SEE).
 *
 * Evaluates whether a sequence of captures on a target square is
 * material-winning or material-losing, without doing a full search.
 *
 * Used for:
 *   1. Move ordering (winning captures first, losing captures last)
 *   2. Quiescence search delta pruning
 *   3. Futility pruning decisions
 *
 * Algorithm: simulate the full recapture sequence on the target square,
 * using the least-valuable attacker (LVA) at each step, with alpha-beta
 * style minimax on the gained material.
 */
public class SEEEvaluator {

    // Piece values for SEE (simplified, matching PieceType.getValue())
    private static final int[] SEE_VALUE = {
        0,   // placeholder index 0
        100, // PAWN
        320, // KNIGHT
        330, // BISHOP
        500, // ROOK
        900, // QUEEN
        20000 // KING
    };

    /**
     * Evaluate a capture from (fromCol, fromRow) → (toCol, toRow).
     *
     * @return positive value = capture is winning for the attacker,
     *         negative = losing, 0 = roughly equal.
     */
    public int see(GameState state, int fromCol, int fromRow, int toCol, int toRow) {
        Piece attacker = state.getPiece(fromCol, fromRow);
        Piece target   = state.getPiece(toCol, toRow);
        if (attacker == null) return 0;
        int targetValue = (target != null) ? pieceValue(target.type) : 0;

        // Build working board (bit-mask style via a simple occupancy array)
        Piece[] board = state.getBoard().clone();

        int gain[] = new int[32];
        int depth  = 0;
        boolean side = attacker.isWhite; // side currently capturing

        gain[depth] = targetValue;

        // Remove attacker from board (it moves to the target square)
        board[fromRow * 8 + fromCol] = null;
        board[toRow  * 8 + toCol  ] = attacker;

        // Alternate captures until no more attackers
        while (true) {
            depth++;
            side = !side;

            int[] lva = findLVA(board, toCol, toRow, side);
            if (lva == null) break; // no more attackers

            int attackerVal = pieceValue(board[lva[1] * 8 + lva[0]].type);
            gain[depth] = attackerVal - gain[depth - 1];

            // Remove this attacker
            Piece prev = board[lva[1] * 8 + lva[0]];
            board[lva[1] * 8 + lva[0]] = null;
            board[toRow  * 8 + toCol ] = prev;

            // Alpha-beta style pruning: if gain is already negative, stop
            if (Math.max(-gain[depth - 1], gain[depth]) < 0) break;
        }

        // Minimax propagation
        while (--depth > 0) {
            gain[depth - 1] = -Math.max(-gain[depth - 1], gain[depth]);
        }
        return gain[0];
    }

    /**
     * Quick check: is this capture SEE >= 0 (not a losing capture)?
     */
    public boolean isGoodCapture(GameState state, int fromCol, int fromRow, int toCol, int toRow) {
        return see(state, fromCol, fromRow, toCol, toRow) >= 0;
    }

    /** Return SEE score for a given Move (convenience wrapper). */
    public int seeForMove(GameState state, model.Move move) {
        return see(state, move.fromCol, move.fromRow, move.toCol, move.toRow);
    }

    // ---- Helpers ----

    /**
     * Find the least-valuable attacker (LVA) for the given side targeting (toCol, toRow).
     * Returns [col, row] of the LVA, or null if no attacker exists.
     */
    private int[] findLVA(Piece[] board, int toCol, int toRow, boolean white) {
        int bestValue = Integer.MAX_VALUE;
        int[] bestPos = null;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r * 8 + c];
                if (p == null || p.isWhite != white) continue;

                if (attacks(board, c, r, toCol, toRow, p)) {
                    int val = pieceValue(p.type);
                    if (val < bestValue) {
                        bestValue = val;
                        bestPos   = new int[]{c, r};
                    }
                }
            }
        }
        return bestPos;
    }

    /** Check whether piece at (fc, fr) attacks square (tc, tr) on the given board. */
    private boolean attacks(Piece[] board, int fc, int fr, int tc, int tr, Piece p) {
        switch (p.type) {
            case PAWN: {
                int dir = p.isWhite ? -1 : 1;
                return (fr + dir == tr) && (Math.abs(fc - tc) == 1);
            }
            case KNIGHT: {
                int dc = Math.abs(fc - tc), dr = Math.abs(fr - tr);
                return (dc == 1 && dr == 2) || (dc == 2 && dr == 1);
            }
            case BISHOP:
                return isDiagonalClear(board, fc, fr, tc, tr);
            case ROOK:
                return isStraightClear(board, fc, fr, tc, tr);
            case QUEEN:
                return isDiagonalClear(board, fc, fr, tc, tr) || isStraightClear(board, fc, fr, tc, tr);
            case KING: {
                int dc = Math.abs(fc - tc), dr = Math.abs(fr - tr);
                return dc <= 1 && dr <= 1;
            }
            default: return false;
        }
    }

    private boolean isDiagonalClear(Piece[] board, int fc, int fr, int tc, int tr) {
        if (Math.abs(fc - tc) != Math.abs(fr - tr)) return false;
        int stepC = Integer.signum(tc - fc);
        int stepR = Integer.signum(tr - fr);
        int c = fc + stepC, r = fr + stepR;
        while (c != tc || r != tr) {
            if (board[r * 8 + c] != null) return false;
            c += stepC;
            r += stepR;
        }
        return true;
    }

    private boolean isStraightClear(Piece[] board, int fc, int fr, int tc, int tr) {
        if (fc != tc && fr != tr) return false;
        int stepC = Integer.signum(tc - fc);
        int stepR = Integer.signum(tr - fr);
        int c = fc + stepC, r = fr + stepR;
        while (c != tc || r != tr) {
            if (board[r * 8 + c] != null) return false;
            c += stepC;
            r += stepR;
        }
        return true;
    }

    /** Map PieceType to SEE value. */
    public static int pieceValue(PieceType type) {
        switch (type) {
            case PAWN:   return 100;
            case KNIGHT: return 320;
            case BISHOP: return 330;
            case ROOK:   return 500;
            case QUEEN:  return 900;
            case KING:   return 20000;
            default:     return 0;
        }
    }
}
