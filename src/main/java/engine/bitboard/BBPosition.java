package engine.bitboard;

import model.GameState;
import model.Piece;
import model.PieceType;

/**
 * Bitboard representation of a chess position.
 *
 * Piece indices (used in `pieces[]` array):
 *   0=White Pawns   1=White Knights  2=White Bishops
 *   3=White Rooks   4=White Queens   5=White King
 *   6=Black Pawns   7=Black Knights  8=Black Bishops
 *   9=Black Rooks  10=Black Queens  11=Black King
 *
 * Occupancy boards (derived, kept in sync):
 *   whitePieces, blackPieces, allPieces
 *
 * Castling rights packed in one byte:
 *   bit 0 = White KS, bit 1 = White QS, bit 2 = Black KS, bit 3 = Black QS
 */
public final class BBPosition {

    // ---- Piece constants ----
    public static final int W_PAWN   = 0;
    public static final int W_KNIGHT = 1;
    public static final int W_BISHOP = 2;
    public static final int W_ROOK   = 3;
    public static final int W_QUEEN  = 4;
    public static final int W_KING   = 5;
    public static final int B_PAWN   = 6;
    public static final int B_KNIGHT = 7;
    public static final int B_BISHOP = 8;
    public static final int B_ROOK   = 9;
    public static final int B_QUEEN  = 10;
    public static final int B_KING   = 11;
    public static final int NO_PIECE = 12;

    // Castling right bitmasks
    public static final int CR_WHITE_KS = 1;
    public static final int CR_WHITE_QS = 2;
    public static final int CR_BLACK_KS = 4;
    public static final int CR_BLACK_QS = 8;

    // ---- State ----
    public final long[] pieces = new long[12];
    public long whitePieces;
    public long blackPieces;
    public long allPieces;

    public boolean whiteToMove;
    public int     castlingRights;   // 4-bit
    public int     enPassantSquare;  // -1 if none, else square index (0-63)
    public int     halfMoveClock;
    public int     fullMoveNumber;

    // ---- Per-square piece-type lookup (kept in sync) ----
    /** pieceAt[sq] = piece constant (0-11) or NO_PIECE (12) */
    public final byte[] pieceAt = new byte[64];

    // =========================================================================
    //  Factory: convert from existing GameState
    // =========================================================================

    public static BBPosition fromGameState(GameState gs) {
        BBPosition pos = new BBPosition();
        java.util.Arrays.fill(pos.pieceAt, (byte) NO_PIECE);

        for (int sq = 0; sq < 64; sq++) {
            Piece p = gs.getPiece(sq);
            if (p == null) continue;
            int idx = toPieceIndex(p);
            pos.pieces[idx] |= Bitboard.bit(sq);
            pos.pieceAt[sq] = (byte) idx;
        }

        pos.rebuildOccupancy();
        pos.whiteToMove      = gs.isWhiteToMove();
        pos.castlingRights   = packCastling(gs);
        pos.enPassantSquare  = gs.getEnPassantTarget(); // already 0-63 or -1
        pos.halfMoveClock    = gs.getHalfMoveClock();
        pos.fullMoveNumber   = gs.getFullMoveNumber();
        return pos;
    }

    // =========================================================================
    //  Factory: convert back to GameState (for GUI / legacy compatibility)
    // =========================================================================

    public GameState toGameState() {
        GameState gs = new GameState();
        for (int sq = 0; sq < 64; sq++) {
            int idx = pieceAt[sq] & 0xFF;
            if (idx == NO_PIECE) continue;
            gs.setPiece(sq, toPiece(idx));
        }
        gs.setWhiteToMove(whiteToMove);
        gs.setCastlingRight(0, (castlingRights & CR_WHITE_KS) != 0);
        gs.setCastlingRight(1, (castlingRights & CR_WHITE_QS) != 0);
        gs.setCastlingRight(2, (castlingRights & CR_BLACK_KS) != 0);
        gs.setCastlingRight(3, (castlingRights & CR_BLACK_QS) != 0);
        gs.setEnPassantTarget(enPassantSquare);
        gs.setHalfMoveClock(halfMoveClock);
        gs.setFullMoveNumber(fullMoveNumber);
        return gs;
    }

    // =========================================================================
    //  Copy (fast — just copy 12 longs + primitives)
    // =========================================================================

    public BBPosition copy() {
        BBPosition c = new BBPosition();
        System.arraycopy(pieces, 0, c.pieces, 0, 12);
        System.arraycopy(pieceAt, 0, c.pieceAt, 0, 64);
        c.whitePieces    = whitePieces;
        c.blackPieces    = blackPieces;
        c.allPieces      = allPieces;
        c.whiteToMove    = whiteToMove;
        c.castlingRights = castlingRights;
        c.enPassantSquare  = enPassantSquare;
        c.halfMoveClock  = halfMoveClock;
        c.fullMoveNumber = fullMoveNumber;
        return c;
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    /** Recompute aggregate occupancy from piece bitboards. */
    public void rebuildOccupancy() {
        whitePieces = 0L;
        blackPieces = 0L;
        for (int i = 0; i < 6;  i++) whitePieces |= pieces[i];
        for (int i = 6; i < 12; i++) blackPieces |= pieces[i];
        allPieces = whitePieces | blackPieces;
    }

    /** Piece bitboard index 0-11 for a model Piece. */
    public static int toPieceIndex(Piece p) {
        int base = p.isWhite ? 0 : 6;
        switch (p.type) {
            case PAWN:   return base;
            case KNIGHT: return base + 1;
            case BISHOP: return base + 2;
            case ROOK:   return base + 3;
            case QUEEN:  return base + 4;
            case KING:   return base + 5;
            default: throw new IllegalArgumentException("Unknown piece type: " + p.type);
        }
    }

    /** Internal piece index to PieceType. */
    public static PieceType pieceTypeof(int idx) {
        switch (idx % 6) {
            case 0: return PieceType.PAWN;
            case 1: return PieceType.KNIGHT;
            case 2: return PieceType.BISHOP;
            case 3: return PieceType.ROOK;
            case 4: return PieceType.QUEEN;
            case 5: return PieceType.KING;
            default: throw new IllegalStateException();
        }
    }

    /** Internal piece index to model Piece. */
    private static Piece toPiece(int idx) {
        boolean isWhite = idx < 6;
        PieceType type = pieceTypeof(idx);
        return new Piece(type, isWhite, true);
    }

    private static int packCastling(GameState gs) {
        int cr = 0;
        if (gs.getCastlingRight(0)) cr |= CR_WHITE_KS;
        if (gs.getCastlingRight(1)) cr |= CR_WHITE_QS;
        if (gs.getCastlingRight(2)) cr |= CR_BLACK_KS;
        if (gs.getCastlingRight(3)) cr |= CR_BLACK_QS;
        return cr;
    }

    /** King square for the given side. */
    public int kingSquare(boolean white) {
        long bb = white ? pieces[W_KING] : pieces[B_KING];
        return bb == 0L ? -1 : Bitboard.lsb(bb);
    }

    /** Is the given side's king in check? */
    public boolean isInCheck(boolean white) {
        int kSq = kingSquare(white);
        if (kSq < 0) return false;
        return isAttackedBy(kSq, !white);
    }

    /** Is square `sq` attacked by the given side? */
    public boolean isAttackedBy(int sq, boolean byWhite) {
        long occ = allPieces;
        if (byWhite) {
            if ((BBAttacks.PAWN_ATTACKS[1][sq]   & pieces[W_PAWN])   != 0) return true;
            if ((BBAttacks.KNIGHT_ATTACKS[sq]     & pieces[W_KNIGHT]) != 0) return true;
            if ((BBAttacks.bishopAttacks(sq, occ) & (pieces[W_BISHOP] | pieces[W_QUEEN])) != 0) return true;
            if ((BBAttacks.rookAttacks(sq, occ)   & (pieces[W_ROOK]   | pieces[W_QUEEN])) != 0) return true;
            if ((BBAttacks.KING_ATTACKS[sq]        & pieces[W_KING])   != 0) return true;
        } else {
            if ((BBAttacks.PAWN_ATTACKS[0][sq]   & pieces[B_PAWN])   != 0) return true;
            if ((BBAttacks.KNIGHT_ATTACKS[sq]     & pieces[B_KNIGHT]) != 0) return true;
            if ((BBAttacks.bishopAttacks(sq, occ) & (pieces[B_BISHOP] | pieces[B_QUEEN])) != 0) return true;
            if ((BBAttacks.rookAttacks(sq, occ)   & (pieces[B_ROOK]   | pieces[B_QUEEN])) != 0) return true;
            if ((BBAttacks.KING_ATTACKS[sq]        & pieces[B_KING])   != 0) return true;
        }
        return false;
    }
}
