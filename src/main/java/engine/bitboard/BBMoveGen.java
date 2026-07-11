package engine.bitboard;

/**
 * Fast move generator using bitboards.
 *
 * Moves are encoded as ints (zero heap allocation during search):
 *
 *   Bits  0- 5: from square (0-63)
 *   Bits  6-11: to square   (0-63)
 *   Bits 12-15: moving piece type index (0-11, as in BBPosition)
 *   Bits 16-19: captured piece type index (0-11 or 15 = NO_PIECE)
 *   Bits 20-21: flags: 0=NORMAL, 1=CASTLING, 2=EN_PASSANT, 3=PROMOTION
 *   Bits 22-25: promotion piece type index (0-5; only valid when flag=3)
 */
public final class BBMoveGen {

    // ---- Move encoding constants ----
    public static final int FLAG_NORMAL    = 0;
    public static final int FLAG_CASTLE    = 1;
    public static final int FLAG_EN_PASSANT = 2;
    public static final int FLAG_PROMOTION  = 3;

    public static final int NO_CAPTURE    = 12; // Must match BBPosition.NO_PIECE

    // ---- Decode helpers ----
    public static int fromSq(int move)    { return move & 0x3F; }
    public static int toSq(int move)      { return (move >>> 6) & 0x3F; }
    public static int movingPiece(int move){ return (move >>> 12) & 0xF; }
    public static int capturedPiece(int move){ return (move >>> 16) & 0xF; }
    public static int flag(int move)      { return (move >>> 20) & 0x3; }
    public static int promoPiece(int move){ return (move >>> 22) & 0xF; }

    public static boolean isCapture(int move)   { return capturedPiece(move) != NO_CAPTURE; }
    public static boolean isPromotion(int move) { return flag(move) == FLAG_PROMOTION; }
    public static boolean isEnPassant(int move) { return flag(move) == FLAG_EN_PASSANT; }
    public static boolean isCastling(int move)  { return flag(move) == FLAG_CASTLE; }

    // ---- Move encoding ----
    private static int encode(int from, int to, int piece, int cap, int flags, int promo) {
        return from | (to << 6) | (piece << 12) | (cap << 16) | (flags << 20) | (promo << 22);
    }

    private static int encodeNormal(int from, int to, int piece, int cap) {
        return from | (to << 6) | (piece << 12) | (cap << 16);
    }

    // =========================================================================
    //  Public generate API
    // =========================================================================

    /**
     * Generate all pseudo-legal moves (captures and quiet) into `moves`.
     * @return number of moves written.
     */
    public static int generateMoves(BBPosition pos, int[] moves) {
        return pos.whiteToMove ? generateWhite(pos, moves, false)
                               : generateBlack(pos, moves, false);
    }

    /**
     * Generate only captures (for quiescence search).
     * @return number of moves written.
     */
    public static int generateCaptures(BBPosition pos, int[] moves) {
        return pos.whiteToMove ? generateWhite(pos, moves, true)
                               : generateBlack(pos, moves, true);
    }

    // =========================================================================
    //  White move generation
    // =========================================================================

    private static int generateWhite(BBPosition pos, int[] moves, boolean capturesOnly) {
        int count = 0;
        long us   = pos.whitePieces;
        long them = pos.blackPieces;
        long occ  = pos.allPieces;
        long free = ~occ;

        // ---- Pawns ----
        count = genWhitePawns(pos, moves, count, capturesOnly, occ, them, free);

        // ---- Knights ----
        long knights = pos.pieces[BBPosition.W_KNIGHT];
        while (knights != 0) {
            int sq = Bitboard.lsb(knights); knights = Bitboard.popLsb(knights);
            long targets = BBAttacks.KNIGHT_ATTACKS[sq] & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.W_KNIGHT, pos);
        }

        // ---- Bishops ----
        long bishops = pos.pieces[BBPosition.W_BISHOP];
        while (bishops != 0) {
            int sq = Bitboard.lsb(bishops); bishops = Bitboard.popLsb(bishops);
            long targets = BBAttacks.bishopAttacks(sq, occ) & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.W_BISHOP, pos);
        }

        // ---- Rooks ----
        long rooks = pos.pieces[BBPosition.W_ROOK];
        while (rooks != 0) {
            int sq = Bitboard.lsb(rooks); rooks = Bitboard.popLsb(rooks);
            long targets = BBAttacks.rookAttacks(sq, occ) & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.W_ROOK, pos);
        }

        // ---- Queens ----
        long queens = pos.pieces[BBPosition.W_QUEEN];
        while (queens != 0) {
            int sq = Bitboard.lsb(queens); queens = Bitboard.popLsb(queens);
            long targets = BBAttacks.queenAttacks(sq, occ) & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.W_QUEEN, pos);
        }

        // ---- King ----
        long king = pos.pieces[BBPosition.W_KING];
        if (king != 0) {
            int sq = Bitboard.lsb(king);
            long targets = BBAttacks.KING_ATTACKS[sq] & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.W_KING, pos);
            if (!capturesOnly) count = genWhiteCastling(pos, moves, count, sq, occ);
        }

        return count;
    }

    private static int genWhitePawns(BBPosition pos, int[] moves, int count,
                                      boolean capturesOnly, long occ, long them, long free) {
        long pawns = pos.pieces[BBPosition.W_PAWN];

        // Captures (NE and NW)
        long capNE = Bitboard.shiftNE(pawns) & them;
        long capNW = Bitboard.shiftNW(pawns) & them;

        // Promotions via capture NE
        long promoCapNE = capNE & Bitboard.RANK_8;
        capNE          &= ~Bitboard.RANK_8;
        while (promoCapNE != 0) {
            int to = Bitboard.lsb(promoCapNE); promoCapNE = Bitboard.popLsb(promoCapNE);
            int from = to + 7; // NE was sq-7, so from = to+7
            count = addPromos(moves, count, from, to, BBPosition.W_PAWN, pos.pieceAt[to]);
        }
        while (capNE != 0) {
            int to = Bitboard.lsb(capNE); capNE = Bitboard.popLsb(capNE);
            int from = to + 7;
            moves[count++] = encodeNormal(from, to, BBPosition.W_PAWN, pos.pieceAt[to] & 0xFF);
        }

        // Promotions via capture NW
        long promoCapNW = capNW & Bitboard.RANK_8;
        capNW          &= ~Bitboard.RANK_8;
        while (promoCapNW != 0) {
            int to = Bitboard.lsb(promoCapNW); promoCapNW = Bitboard.popLsb(promoCapNW);
            int from = to + 9;
            count = addPromos(moves, count, from, to, BBPosition.W_PAWN, pos.pieceAt[to]);
        }
        while (capNW != 0) {
            int to = Bitboard.lsb(capNW); capNW = Bitboard.popLsb(capNW);
            int from = to + 9;
            moves[count++] = encodeNormal(from, to, BBPosition.W_PAWN, pos.pieceAt[to] & 0xFF);
        }

        // En passant
        if (pos.enPassantSquare >= 0) {
            int epSq = pos.enPassantSquare;
            long epBit = Bitboard.bit(epSq);
            long epCapturers = BBAttacks.PAWN_ATTACKS[1][epSq] & pawns; // squares that attack epSq from black's perspective = white pawns that can attack it
            while (epCapturers != 0) {
                int from = Bitboard.lsb(epCapturers); epCapturers = Bitboard.popLsb(epCapturers);
                moves[count++] = encode(from, epSq, BBPosition.W_PAWN, BBPosition.B_PAWN, FLAG_EN_PASSANT, 0);
            }
        }

        if (!capturesOnly) {
            // Single push
            long push1 = Bitboard.shiftN(pawns) & free;
            // Double push (from rank 2, i.e. row 6)
            long push2 = Bitboard.shiftN(push1 & Bitboard.RANK_7) & free;

            // Promotions via push
            long promoPush = push1 & Bitboard.RANK_8;
            push1         &= ~Bitboard.RANK_8;

            while (promoPush != 0) {
                int to = Bitboard.lsb(promoPush); promoPush = Bitboard.popLsb(promoPush);
                count = addPromos(moves, count, to + 8, to, BBPosition.W_PAWN, NO_CAPTURE);
            }
            while (push1 != 0) {
                int to = Bitboard.lsb(push1); push1 = Bitboard.popLsb(push1);
                moves[count++] = encodeNormal(to + 8, to, BBPosition.W_PAWN, NO_CAPTURE);
            }
            while (push2 != 0) {
                int to = Bitboard.lsb(push2); push2 = Bitboard.popLsb(push2);
                moves[count++] = encodeNormal(to + 16, to, BBPosition.W_PAWN, NO_CAPTURE);
            }
        }
        return count;
    }

    private static int genWhiteCastling(BBPosition pos, int[] moves, int count, int kSq, long occ) {
        // White king is normally at sq 60 (e1 in our convention: row 7, col 4)
        if ((pos.castlingRights & BBPosition.CR_WHITE_KS) != 0) {
            // f1=61, g1=62 must be empty; king doesn't pass through check
            if ((occ & (Bitboard.bit(61) | Bitboard.bit(62))) == 0
                && !pos.isAttackedBy(kSq, false)
                && !pos.isAttackedBy(61, false)) {
                moves[count++] = encode(kSq, 62, BBPosition.W_KING, NO_CAPTURE, FLAG_CASTLE, 0);
            }
        }
        if ((pos.castlingRights & BBPosition.CR_WHITE_QS) != 0) {
            // b1=57, c1=58, d1=59 must be empty
            if ((occ & (Bitboard.bit(57) | Bitboard.bit(58) | Bitboard.bit(59))) == 0
                && !pos.isAttackedBy(kSq, false)
                && !pos.isAttackedBy(59, false)) {
                moves[count++] = encode(kSq, 58, BBPosition.W_KING, NO_CAPTURE, FLAG_CASTLE, 0);
            }
        }
        return count;
    }

    // =========================================================================
    //  Black move generation (mirror of white)
    // =========================================================================

    private static int generateBlack(BBPosition pos, int[] moves, boolean capturesOnly) {
        int count = 0;
        long us   = pos.blackPieces;
        long them = pos.whitePieces;
        long occ  = pos.allPieces;
        long free = ~occ;

        // ---- Pawns ----
        count = genBlackPawns(pos, moves, count, capturesOnly, occ, them, free);

        // ---- Knights ----
        long knights = pos.pieces[BBPosition.B_KNIGHT];
        while (knights != 0) {
            int sq = Bitboard.lsb(knights); knights = Bitboard.popLsb(knights);
            long targets = BBAttacks.KNIGHT_ATTACKS[sq] & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.B_KNIGHT, pos);
        }

        // ---- Bishops ----
        long bishops = pos.pieces[BBPosition.B_BISHOP];
        while (bishops != 0) {
            int sq = Bitboard.lsb(bishops); bishops = Bitboard.popLsb(bishops);
            long targets = BBAttacks.bishopAttacks(sq, occ) & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.B_BISHOP, pos);
        }

        // ---- Rooks ----
        long rooks = pos.pieces[BBPosition.B_ROOK];
        while (rooks != 0) {
            int sq = Bitboard.lsb(rooks); rooks = Bitboard.popLsb(rooks);
            long targets = BBAttacks.rookAttacks(sq, occ) & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.B_ROOK, pos);
        }

        // ---- Queens ----
        long queens = pos.pieces[BBPosition.B_QUEEN];
        while (queens != 0) {
            int sq = Bitboard.lsb(queens); queens = Bitboard.popLsb(queens);
            long targets = BBAttacks.queenAttacks(sq, occ) & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.B_QUEEN, pos);
        }

        // ---- King ----
        long king = pos.pieces[BBPosition.B_KING];
        if (king != 0) {
            int sq = Bitboard.lsb(king);
            long targets = BBAttacks.KING_ATTACKS[sq] & ~us;
            if (capturesOnly) targets &= them;
            count = addMoves(moves, count, sq, targets, BBPosition.B_KING, pos);
            if (!capturesOnly) count = genBlackCastling(pos, moves, count, sq, occ);
        }

        return count;
    }

    private static int genBlackPawns(BBPosition pos, int[] moves, int count,
                                      boolean capturesOnly, long occ, long them, long free) {
        long pawns = pos.pieces[BBPosition.B_PAWN];

        // Captures (SE and SW)
        long capSE = Bitboard.shiftSE(pawns) & them;
        long capSW = Bitboard.shiftSW(pawns) & them;

        // Promotions via capture SE
        long promoCapSE = capSE & Bitboard.RANK_1;
        capSE          &= ~Bitboard.RANK_1;
        while (promoCapSE != 0) {
            int to = Bitboard.lsb(promoCapSE); promoCapSE = Bitboard.popLsb(promoCapSE);
            int from = to - 9;
            count = addPromos(moves, count, from, to, BBPosition.B_PAWN, pos.pieceAt[to]);
        }
        while (capSE != 0) {
            int to = Bitboard.lsb(capSE); capSE = Bitboard.popLsb(capSE);
            int from = to - 9;
            moves[count++] = encodeNormal(from, to, BBPosition.B_PAWN, pos.pieceAt[to] & 0xFF);
        }

        // Promotions via capture SW
        long promoCapSW = capSW & Bitboard.RANK_1;
        capSW          &= ~Bitboard.RANK_1;
        while (promoCapSW != 0) {
            int to = Bitboard.lsb(promoCapSW); promoCapSW = Bitboard.popLsb(promoCapSW);
            int from = to - 7;
            count = addPromos(moves, count, from, to, BBPosition.B_PAWN, pos.pieceAt[to]);
        }
        while (capSW != 0) {
            int to = Bitboard.lsb(capSW); capSW = Bitboard.popLsb(capSW);
            int from = to - 7;
            moves[count++] = encodeNormal(from, to, BBPosition.B_PAWN, pos.pieceAt[to] & 0xFF);
        }

        // En passant
        if (pos.enPassantSquare >= 0) {
            int epSq = pos.enPassantSquare;
            long epCapturers = BBAttacks.PAWN_ATTACKS[0][epSq] & pawns;
            while (epCapturers != 0) {
                int from = Bitboard.lsb(epCapturers); epCapturers = Bitboard.popLsb(epCapturers);
                moves[count++] = encode(from, epSq, BBPosition.B_PAWN, BBPosition.W_PAWN, FLAG_EN_PASSANT, 0);
            }
        }

        if (!capturesOnly) {
            long push1 = Bitboard.shiftS(pawns) & free;
            long push2 = Bitboard.shiftS(push1 & Bitboard.RANK_2) & free;

            long promoPush = push1 & Bitboard.RANK_1;
            push1         &= ~Bitboard.RANK_1;

            while (promoPush != 0) {
                int to = Bitboard.lsb(promoPush); promoPush = Bitboard.popLsb(promoPush);
                count = addPromos(moves, count, to - 8, to, BBPosition.B_PAWN, NO_CAPTURE);
            }
            while (push1 != 0) {
                int to = Bitboard.lsb(push1); push1 = Bitboard.popLsb(push1);
                moves[count++] = encodeNormal(to - 8, to, BBPosition.B_PAWN, NO_CAPTURE);
            }
            while (push2 != 0) {
                int to = Bitboard.lsb(push2); push2 = Bitboard.popLsb(push2);
                moves[count++] = encodeNormal(to - 16, to, BBPosition.B_PAWN, NO_CAPTURE);
            }
        }
        return count;
    }

    private static int genBlackCastling(BBPosition pos, int[] moves, int count, int kSq, long occ) {
        // Black king normally at sq 4 (e8: row 0, col 4)
        if ((pos.castlingRights & BBPosition.CR_BLACK_KS) != 0) {
            if ((occ & (Bitboard.bit(5) | Bitboard.bit(6))) == 0
                && !pos.isAttackedBy(kSq, true)
                && !pos.isAttackedBy(5, true)) {
                moves[count++] = encode(kSq, 6, BBPosition.B_KING, NO_CAPTURE, FLAG_CASTLE, 0);
            }
        }
        if ((pos.castlingRights & BBPosition.CR_BLACK_QS) != 0) {
            if ((occ & (Bitboard.bit(1) | Bitboard.bit(2) | Bitboard.bit(3))) == 0
                && !pos.isAttackedBy(kSq, true)
                && !pos.isAttackedBy(3, true)) {
                moves[count++] = encode(kSq, 2, BBPosition.B_KING, NO_CAPTURE, FLAG_CASTLE, 0);
            }
        }
        return count;
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    /** Add all target-square moves from `sq` to `moves[]`. */
    private static int addMoves(int[] moves, int count, int sq, long targets, int piece, BBPosition pos) {
        while (targets != 0) {
            int to = Bitboard.lsb(targets); targets = Bitboard.popLsb(targets);
            int cap = pos.pieceAt[to] & 0xFF;
            moves[count++] = encodeNormal(sq, to, piece, cap);
        }
        return count;
    }

    /** Add all four promotion moves. cap = captured piece type index or NO_CAPTURE. */
    private static int addPromos(int[] moves, int count, int from, int to, int piece, int cap) {
        int capVal = cap & 0xFF;
        // Queen promo (most likely best, list first)
        moves[count++] = encode(from, to, piece, capVal, FLAG_PROMOTION, BBPosition.W_QUEEN % 6 + (piece >= 6 ? 6 : 0));
        moves[count++] = encode(from, to, piece, capVal, FLAG_PROMOTION, BBPosition.W_ROOK  % 6 + (piece >= 6 ? 6 : 0));
        moves[count++] = encode(from, to, piece, capVal, FLAG_PROMOTION, BBPosition.W_BISHOP % 6 + (piece >= 6 ? 6 : 0));
        moves[count++] = encode(from, to, piece, capVal, FLAG_PROMOTION, BBPosition.W_KNIGHT % 6 + (piece >= 6 ? 6 : 0));
        return count;
    }

    /**
     * Filter pseudo-legal moves to keep only legal ones.
     * A move is legal if it does not leave own king in check.
     */
    public static int filterLegal(BBPosition pos, int[] moves, int count, int[] legal) {
        int legalCount = 0;
        for (int i = 0; i < count; i++) {
            long undo = BBMoveApplier.make(pos, moves[i]);
            boolean inCheck = pos.isInCheck(!pos.whiteToMove); // side that just moved
            BBMoveApplier.unmake(pos, moves[i], undo);
            if (!inCheck) legal[legalCount++] = moves[i];
        }
        return legalCount;
    }
}
