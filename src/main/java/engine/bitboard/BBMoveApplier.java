package engine.bitboard;

/**
 * Make / Unmake moves directly on BBPosition with no heap allocation.
 *
 * make() returns an "undo token" (packed long) that unmake() uses to fully
 * restore the position.  No copy of BBPosition is needed.
 *
 * Undo token layout (64 bits):
 *   bits  0- 6: old en passant square + 1  (0 = none)
 *   bits  7-10: old castling rights (4 bits)
 *   bits 11-17: old half-move clock (7 bits, max 100)
 *   bits 18-21: captured piece index (0-11 or BBMoveGen.NO_CAPTURE=15)
 */
public final class BBMoveApplier {

    private BBMoveApplier() {}

    // =========================================================================
    //  Make
    // =========================================================================

    /**
     * Apply `move` to `pos` in-place.
     * @return undo token to be passed to unmake().
     */
    public static long make(BBPosition pos, int move) {
        int from  = BBMoveGen.fromSq(move);
        int to    = BBMoveGen.toSq(move);
        int piece = BBMoveGen.movingPiece(move);
        int cap   = BBMoveGen.capturedPiece(move);
        int flag  = BBMoveGen.flag(move);

        // Pack undo info
        long undo = (long)(pos.enPassantSquare + 1)       // bits 0-6
                  | ((long) pos.castlingRights   << 7)     // bits 7-10
                  | ((long) pos.halfMoveClock    << 11)    // bits 11-17
                  | ((long) cap                  << 18);   // bits 18-21

        // ---- Remove moving piece from source ----
        removePiece(pos, from, piece);

        // ---- Handle captures ----
        if (cap != BBMoveGen.NO_CAPTURE && flag != BBMoveGen.FLAG_EN_PASSANT) {
            removePiece(pos, to, cap);
        }

        // ---- Handle special moves ----
        switch (flag) {
            case BBMoveGen.FLAG_EN_PASSANT: {
                // Remove the captured pawn (different square from `to`)
                int capSq = pos.whiteToMove ? to + 8 : to - 8;
                int capPawn = pos.whiteToMove ? BBPosition.B_PAWN : BBPosition.W_PAWN;
                removePiece(pos, capSq, capPawn);
                placePiece(pos, to, piece);
                break;
            }
            case BBMoveGen.FLAG_CASTLE: {
                placePiece(pos, to, piece);
                // Move the rook
                if (pos.whiteToMove) {
                    if (to == 62) { removePiece(pos, 63, BBPosition.W_ROOK); placePiece(pos, 61, BBPosition.W_ROOK); } // KS
                    else          { removePiece(pos, 56, BBPosition.W_ROOK); placePiece(pos, 59, BBPosition.W_ROOK); } // QS
                } else {
                    if (to == 6)  { removePiece(pos, 7,  BBPosition.B_ROOK); placePiece(pos, 5,  BBPosition.B_ROOK); } // KS
                    else          { removePiece(pos, 0,  BBPosition.B_ROOK); placePiece(pos, 3,  BBPosition.B_ROOK); } // QS
                }
                break;
            }
            case BBMoveGen.FLAG_PROMOTION: {
                int promoPieceIdx = BBMoveGen.promoPiece(move);
                placePiece(pos, to, promoPieceIdx);
                break;
            }
            default: {
                placePiece(pos, to, piece);
                break;
            }
        }

        // ---- Update en passant square ----
        pos.enPassantSquare = -1;
        if ((piece == BBPosition.W_PAWN || piece == BBPosition.B_PAWN)
                && Math.abs(to - from) == 16) {
            // Double pawn push — set ep square to the skipped square
            pos.enPassantSquare = (from + to) / 2;
        }

        // ---- Update castling rights ----
        pos.castlingRights &= CASTLING_SPOILERS[from] & CASTLING_SPOILERS[to];

        // ---- Update half-move clock ----
        if (piece == BBPosition.W_PAWN || piece == BBPosition.B_PAWN
                || cap != BBMoveGen.NO_CAPTURE) {
            pos.halfMoveClock = 0;
        } else {
            pos.halfMoveClock++;
        }

        // ---- Full-move number ----
        if (!pos.whiteToMove) pos.fullMoveNumber++;

        // ---- Flip side to move ----
        pos.whiteToMove = !pos.whiteToMove;

        // ---- Rebuild aggregate occupancy ----
        pos.rebuildOccupancy();

        return undo;
    }

    // =========================================================================
    //  Unmake
    // =========================================================================

    /**
     * Undo a previously made move, fully restoring `pos`.
     * Must be called with the same `move` and `undo` returned by make().
     */
    public static void unmake(BBPosition pos, int move, long undo) {
        // Flip side back
        pos.whiteToMove = !pos.whiteToMove;

        int from  = BBMoveGen.fromSq(move);
        int to    = BBMoveGen.toSq(move);
        int piece = BBMoveGen.movingPiece(move);
        int cap   = BBMoveGen.capturedPiece(move);
        int flag  = BBMoveGen.flag(move);

        // ---- Restore simple state from undo token ----
        pos.enPassantSquare = (int)( undo        & 0x7FL) - 1;
        pos.castlingRights  = (int)((undo >>>  7) & 0xFL);
        pos.halfMoveClock   = (int)((undo >>> 11) & 0x7FL);
        if (!pos.whiteToMove) pos.fullMoveNumber--;  // flip-back means we undo black's move

        // ---- Remove piece from destination ----
        int currentAtTo = flag == BBMoveGen.FLAG_PROMOTION
                          ? BBMoveGen.promoPiece(move)
                          : piece;
        removePiece(pos, to, currentAtTo);

        // ---- Restore moving piece to source ----
        placePiece(pos, from, piece);

        // ---- Restore captures ----
        switch (flag) {
            case BBMoveGen.FLAG_EN_PASSANT: {
                int capSq = pos.whiteToMove ? to + 8 : to - 8;
                int capPawn = pos.whiteToMove ? BBPosition.B_PAWN : BBPosition.W_PAWN;
                placePiece(pos, capSq, capPawn);
                break;
            }
            case BBMoveGen.FLAG_CASTLE: {
                if (pos.whiteToMove) {
                    if (to == 62) { removePiece(pos, 61, BBPosition.W_ROOK); placePiece(pos, 63, BBPosition.W_ROOK); }
                    else          { removePiece(pos, 59, BBPosition.W_ROOK); placePiece(pos, 56, BBPosition.W_ROOK); }
                } else {
                    if (to == 6)  { removePiece(pos, 5,  BBPosition.B_ROOK); placePiece(pos, 7,  BBPosition.B_ROOK); }
                    else          { removePiece(pos, 3,  BBPosition.B_ROOK); placePiece(pos, 0,  BBPosition.B_ROOK); }
                }
                break;
            }
            default: {
                if (cap != BBMoveGen.NO_CAPTURE) {
                    placePiece(pos, to, cap);
                }
                break;
            }
        }

        pos.rebuildOccupancy();
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private static void removePiece(BBPosition pos, int sq, int piece) {
        long mask = Bitboard.bit(sq);
        pos.pieces[piece] &= ~mask;
        pos.pieceAt[sq]    = (byte) BBPosition.NO_PIECE;
    }

    private static void placePiece(BBPosition pos, int sq, int piece) {
        long mask = Bitboard.bit(sq);
        pos.pieces[piece] |= mask;
        pos.pieceAt[sq]    = (byte) piece;
    }

    /**
     * Castling right spoilers per square.
     * Moving from/to any rook/king square revokes that castling right.
     */
    private static final int[] CASTLING_SPOILERS = new int[64];
    static {
        java.util.Arrays.fill(CASTLING_SPOILERS, 0xF); // default: spoil nothing
        // White KS: king=60, rook=63
        CASTLING_SPOILERS[60] &= ~BBPosition.CR_WHITE_KS & ~BBPosition.CR_WHITE_QS;
        CASTLING_SPOILERS[63] &= ~BBPosition.CR_WHITE_KS;
        CASTLING_SPOILERS[56] &= ~BBPosition.CR_WHITE_QS;
        // Black KS: king=4, rook=7
        CASTLING_SPOILERS[4]  &= ~BBPosition.CR_BLACK_KS & ~BBPosition.CR_BLACK_QS;
        CASTLING_SPOILERS[7]  &= ~BBPosition.CR_BLACK_KS;
        CASTLING_SPOILERS[0]  &= ~BBPosition.CR_BLACK_QS;
    }
}
