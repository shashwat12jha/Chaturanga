package io.chaturanga.engine;

/** Original tapered classical evaluation; positive scores favor the side to move. */
public final class ClassicalEvaluator {
    private static final int[] MIDGAME_VALUE = {100, 320, 335, 500, 925, 0};
    private static final int[] ENDGAME_VALUE = {120, 305, 325, 520, 900, 0};
    private static final int[] PHASE_WEIGHT = {0, 1, 1, 2, 4, 0};
    private static final int MAX_PHASE = 24;

    public int evaluate(Position position) {
        int whiteMidgame = 0;
        int blackMidgame = 0;
        int whiteEndgame = 0;
        int blackEndgame = 0;
        int phase = 0;

        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece == Piece.NONE) continue;
            int color = Piece.color(piece);
            int type = Piece.type(piece);
            int mg = MIDGAME_VALUE[type] + pieceSquare(type, square, color, false);
            int eg = ENDGAME_VALUE[type] + pieceSquare(type, square, color, true);
            if (color == Piece.WHITE) {
                whiteMidgame += mg;
                whiteEndgame += eg;
            } else {
                blackMidgame += mg;
                blackEndgame += eg;
            }
            phase += PHASE_WEIGHT[type];
        }

        int whitePawns = pawnStructure(position, Piece.WHITE);
        int blackPawns = pawnStructure(position, Piece.BLACK);
        whiteMidgame += whitePawns + kingSafety(position, Piece.WHITE);
        blackMidgame += blackPawns + kingSafety(position, Piece.BLACK);
        whiteEndgame += whitePawns;
        blackEndgame += blackPawns;

        if (Long.bitCount(position.pieces(Piece.WHITE_BISHOP)) >= 2) {
            whiteMidgame += 28;
            whiteEndgame += 38;
        }
        if (Long.bitCount(position.pieces(Piece.BLACK_BISHOP)) >= 2) {
            blackMidgame += 28;
            blackEndgame += 38;
        }

        whiteMidgame += mobility(position, Piece.WHITE) * 3;
        blackMidgame += mobility(position, Piece.BLACK) * 3;

        phase = Math.min(MAX_PHASE, phase);
        int midgameScore = whiteMidgame - blackMidgame;
        int endgameScore = whiteEndgame - blackEndgame;
        int whiteScore = (midgameScore * phase + endgameScore * (MAX_PHASE - phase)) / MAX_PHASE;
        return position.sideToMove() == Piece.WHITE ? whiteScore : -whiteScore;
    }

    private int pieceSquare(int type, int square, int color, boolean endgame) {
        int file = square & 7;
        int rank = square >>> 3;
        int relativeRank = color == Piece.WHITE ? rank : 7 - rank;
        int centerDistance = Math.abs(file * 2 - 7) + Math.abs(rank * 2 - 7);
        return switch (type) {
            case Piece.PAWN -> relativeRank * (endgame ? 14 : 8) - Math.abs(file * 2 - 7) * 2;
            case Piece.KNIGHT -> 42 - centerDistance * (endgame ? 4 : 5);
            case Piece.BISHOP -> 28 - centerDistance * 3 + relativeRank * 2;
            case Piece.ROOK -> relativeRank == 6 ? 18 : 0;
            case Piece.QUEEN -> 12 - centerDistance;
            case Piece.KING -> endgame
                    ? 48 - centerDistance * 6
                    : (relativeRank == 0 && (file <= 2 || file >= 6) ? 35 : -relativeRank * 14);
            default -> 0;
        };
    }

    private int pawnStructure(Position position, int color) {
        long pawns = position.pieces(Piece.of(color, Piece.PAWN));
        long enemyPawns = position.pieces(Piece.of(color ^ 1, Piece.PAWN));
        int[] perFile = new int[8];
        long bits = pawns;
        while (bits != 0) {
            int square = Long.numberOfTrailingZeros(bits);
            perFile[square & 7]++;
            bits &= bits - 1;
        }

        int score = 0;
        bits = pawns;
        while (bits != 0) {
            int square = Long.numberOfTrailingZeros(bits);
            bits &= bits - 1;
            int file = square & 7;
            int rank = square >>> 3;
            int relativeRank = color == Piece.WHITE ? rank : 7 - rank;
            if (perFile[file] > 1) score -= 14;
            if ((file == 0 || perFile[file - 1] == 0) && (file == 7 || perFile[file + 1] == 0)) score -= 13;
            if (isPassedPawn(enemyPawns, file, rank, color)) score += 18 + relativeRank * relativeRank * 4;
        }
        return score;
    }

    private boolean isPassedPawn(long enemyPawns, int file, int rank, int color) {
        long relevant = enemyPawns;
        while (relevant != 0) {
            int enemySquare = Long.numberOfTrailingZeros(relevant);
            relevant &= relevant - 1;
            int enemyFile = enemySquare & 7;
            int enemyRank = enemySquare >>> 3;
            if (Math.abs(enemyFile - file) <= 1
                    && (color == Piece.WHITE ? enemyRank > rank : enemyRank < rank)) return false;
        }
        return true;
    }

    private int kingSafety(Position position, int color) {
        long kingBits = position.pieces(Piece.of(color, Piece.KING));
        int kingSquare = Long.numberOfTrailingZeros(kingBits);
        int file = kingSquare & 7;
        int rank = kingSquare >>> 3;
        int shieldRank = rank + (color == Piece.WHITE ? 1 : -1);
        int score = 0;
        if (shieldRank >= 0 && shieldRank < 8) {
            for (int delta = -1; delta <= 1; delta++) {
                int shieldFile = file + delta;
                if (shieldFile >= 0 && shieldFile < 8
                        && position.pieceAt(shieldRank * 8 + shieldFile) == Piece.of(color, Piece.PAWN)) score += 12;
            }
        }
        return score;
    }

    private int mobility(Position position, int color) {
        long friendly = position.colorPieces(color);
        long occupied = position.occupied();
        int mobility = 0;
        for (int type = Piece.KNIGHT; type <= Piece.QUEEN; type++) {
            long pieces = position.pieces(Piece.of(color, type));
            while (pieces != 0) {
                int square = Long.numberOfTrailingZeros(pieces);
                pieces &= pieces - 1;
                long attacks = switch (type) {
                    case Piece.KNIGHT -> AttackTables.KNIGHT[square];
                    case Piece.BISHOP -> AttackTables.bishop(square, occupied);
                    case Piece.ROOK -> AttackTables.rook(square, occupied);
                    case Piece.QUEEN -> AttackTables.queen(square, occupied);
                    default -> 0;
                };
                mobility += Long.bitCount(attacks & ~friendly);
            }
        }
        return mobility;
    }
}
