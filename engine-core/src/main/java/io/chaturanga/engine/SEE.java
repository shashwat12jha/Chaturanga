package io.chaturanga.engine;

public final class SEE {
    private static final int[] PIECE_VALUES = {
        0, 100, 320, 335, 500, 925, 20000, 0, // White pieces
        0, 100, 320, 335, 500, 925, 20000, 0  // Black pieces
    };

    /** Returns SEE score for the given move */
    public static int evaluate(Position pos, Move move) {
        int from = move.from();
        int to = move.to();
        int type = Piece.type(pos.pieceAt(from));
        int target = pos.pieceAt(to);
        
        int gain[] = new int[32];
        int d = 0;
        gain[0] = target != Piece.NONE ? PIECE_VALUES[Piece.type(target)] : 0;
        if (move.isPromotion()) {
            gain[0] += PIECE_VALUES[move.promotion()] - PIECE_VALUES[Piece.PAWN];
        }
        
        long occupied = pos.occupied();
        occupied &= ~(1L << from);
        occupied |= (1L << to);
        
        int side = Piece.color(pos.pieceAt(from)) ^ 1;
        int attacker = type;
        
        while (true) {
            d++;
            long attackers = attackersTo(pos, to, occupied, side);
            if (attackers == 0) break;
            
            int lvaPiece = Piece.NONE;
            int lvaSquare = -1;
            for (int pt = Piece.PAWN; pt <= Piece.KING; pt++) {
                long ptAttackers = attackers & pos.pieces(Piece.of(side, pt));
                if (ptAttackers != 0) {
                    lvaPiece = pt;
                    lvaSquare = Long.numberOfTrailingZeros(ptAttackers);
                    break;
                }
            }
            if (lvaPiece == Piece.NONE) break;
            
            gain[d] = PIECE_VALUES[attacker] - gain[d - 1];
            if (Math.max(-gain[d - 1], gain[d]) < 0) break;
            
            attacker = lvaPiece;
            occupied &= ~(1L << lvaSquare);
            side ^= 1;
        }
        
        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }
        return gain[0];
    }
    
    private static long attackersTo(Position pos, int sq, long occupied, int color) {
        long attackers = 0;
        
        long pawns = pos.pieces(Piece.of(color, Piece.PAWN)) & occupied;
        attackers |= pawns & AttackTables.PAWN[color ^ 1][sq];
        
        long knights = pos.pieces(Piece.of(color, Piece.KNIGHT)) & occupied;
        attackers |= knights & AttackTables.KNIGHT[sq];
        
        long kings = pos.pieces(Piece.of(color, Piece.KING)) & occupied;
        attackers |= kings & AttackTables.KING[sq];
        
        long bishopsAndQueens = (pos.pieces(Piece.of(color, Piece.BISHOP)) | pos.pieces(Piece.of(color, Piece.QUEEN))) & occupied;
        attackers |= bishopsAndQueens & AttackTables.bishop(sq, occupied);
        
        long rooksAndQueens = (pos.pieces(Piece.of(color, Piece.ROOK)) | pos.pieces(Piece.of(color, Piece.QUEEN))) & occupied;
        attackers |= rooksAndQueens & AttackTables.rook(sq, occupied);
        
        return attackers;
    }
}
