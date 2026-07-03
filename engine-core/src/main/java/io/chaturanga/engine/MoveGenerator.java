package io.chaturanga.engine;

import java.util.ArrayList;
import java.util.List;

/** Complete orthodox-chess legal move generator. */
public final class MoveGenerator {
    private static final int[] PROMOTIONS = {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT};

    public List<Move> generateLegalMoves(Position position) {
        List<Move> pseudo = generatePseudoLegalMoves(position, false);
        List<Move> legal = new ArrayList<>(pseudo.size());
        int movingSide = position.sideToMove();
        Undo undo = new Undo();
        for (Move move : pseudo) {
            position.makeMove(move, undo);
            if (!isInCheck(position, movingSide)) legal.add(move);
            position.unmakeMove(move, undo);
        }
        return legal;
    }

    public List<Move> generateLegalTacticalMoves(Position position) {
        List<Move> pseudo = generatePseudoLegalMoves(position, true);
        List<Move> legal = new ArrayList<>(pseudo.size());
        int movingSide = position.sideToMove();
        Undo undo = new Undo();
        for (Move move : pseudo) {
            position.makeMove(move, undo);
            if (!isInCheck(position, movingSide)) legal.add(move);
            position.unmakeMove(move, undo);
        }
        return legal;
    }

    public boolean isInCheck(Position position, int color) {
        long king = position.pieces(Piece.of(color, Piece.KING));
        if (king == 0 || Long.bitCount(king) != 1) {
            throw new IllegalStateException("Position must have exactly one king per side");
        }
        return isSquareAttacked(position, Long.numberOfTrailingZeros(king), color ^ 1);
    }

    public boolean isSquareAttacked(Position position, int square, int byColor) {
        long target = 1L << square;
        long pawns = position.pieces(Piece.of(byColor, Piece.PAWN));
        while (pawns != 0) {
            int from = Long.numberOfTrailingZeros(pawns);
            if ((AttackTables.PAWN[byColor][from] & target) != 0) return true;
            pawns &= pawns - 1;
        }
        if ((AttackTables.KNIGHT[square] & position.pieces(Piece.of(byColor, Piece.KNIGHT))) != 0) return true;
        if ((AttackTables.KING[square] & position.pieces(Piece.of(byColor, Piece.KING))) != 0) return true;

        long bishopsAndQueens = position.pieces(Piece.of(byColor, Piece.BISHOP))
                | position.pieces(Piece.of(byColor, Piece.QUEEN));
        if ((AttackTables.bishop(square, position.occupied()) & bishopsAndQueens) != 0) return true;
        long rooksAndQueens = position.pieces(Piece.of(byColor, Piece.ROOK))
                | position.pieces(Piece.of(byColor, Piece.QUEEN));
        return (AttackTables.rook(square, position.occupied()) & rooksAndQueens) != 0;
    }

    public Move parseUciMove(Position position, String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) return Move.NONE;
        for (Move move : generateLegalMoves(position)) {
            if (move.toUci().equals(uci.toLowerCase())) return move;
        }
        return Move.NONE;
    }

    private List<Move> generatePseudoLegalMoves(Position position, boolean capturesOnly) {
        List<Move> moves = new ArrayList<>(64);
        int side = position.sideToMove();
        generatePawns(position, side, capturesOnly, moves);
        generateLeaper(position, Piece.KNIGHT, AttackTables.KNIGHT, side, capturesOnly, moves);
        generateSliders(position, Piece.BISHOP, side, capturesOnly, moves);
        generateSliders(position, Piece.ROOK, side, capturesOnly, moves);
        generateSliders(position, Piece.QUEEN, side, capturesOnly, moves);
        generateKing(position, side, capturesOnly, moves);
        return moves;
    }

    private void generatePawns(Position position, int side, boolean capturesOnly, List<Move> moves) {
        long pawns = position.pieces(Piece.of(side, Piece.PAWN));
        int forward = side == Piece.WHITE ? 8 : -8;
        int startRank = side == Piece.WHITE ? 1 : 6;
        int promotionRank = side == Piece.WHITE ? 6 : 1;
        while (pawns != 0) {
            int from = Long.numberOfTrailingZeros(pawns);
            pawns &= pawns - 1;
            int rank = from >>> 3;

            int to = from + forward;
            if (to >= 0 && to < 64 && position.pieceAt(to) == Piece.NONE
                    && (!capturesOnly || rank == promotionRank)) {
                if (rank == promotionRank) {
                    addPromotions(from, to, false, moves);
                } else {
                    moves.add(new Move(from, to, Move.QUIET));
                    int doubleTo = from + 2 * forward;
                    if (rank == startRank && position.pieceAt(doubleTo) == Piece.NONE) {
                        moves.add(new Move(from, doubleTo, Move.DOUBLE_PAWN_PUSH));
                    }
                }
            }

            long attacks = AttackTables.PAWN[side][from];
            while (attacks != 0) {
                int target = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                int captured = position.pieceAt(target);
                if (captured != Piece.NONE && Piece.color(captured) != side && Piece.type(captured) != Piece.KING) {
                    if (rank == promotionRank) addPromotions(from, target, true, moves);
                    else moves.add(new Move(from, target, Move.CAPTURE));
                } else if (target == position.enPassantSquare()) {
                    int captureSquare = target + (side == Piece.WHITE ? -8 : 8);
                    if (position.pieceAt(captureSquare) == Piece.of(side ^ 1, Piece.PAWN)) {
                        moves.add(new Move(from, target, Move.EN_PASSANT));
                    }
                }
            }
        }
    }

    private void addPromotions(int from, int to, boolean capture, List<Move> moves) {
        int flag = capture ? Move.PROMOTION_CAPTURE : Move.PROMOTION;
        for (int promotion : PROMOTIONS) moves.add(new Move(from, to, flag, promotion));
    }

    private void generateLeaper(Position position, int type, long[] attacksBySquare,
                                 int side, boolean capturesOnly, List<Move> moves) {
        long pieces = position.pieces(Piece.of(side, type));
        long friendly = position.colorPieces(side);
        while (pieces != 0) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1;
            addTargets(position, from, attacksBySquare[from] & ~friendly, side, capturesOnly, moves);
        }
    }

    private void generateSliders(Position position, int type, int side,
                                 boolean capturesOnly, List<Move> moves) {
        long pieces = position.pieces(Piece.of(side, type));
        long friendly = position.colorPieces(side);
        while (pieces != 0) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1;
            long targets = switch (type) {
                case Piece.BISHOP -> AttackTables.bishop(from, position.occupied());
                case Piece.ROOK -> AttackTables.rook(from, position.occupied());
                case Piece.QUEEN -> AttackTables.queen(from, position.occupied());
                default -> throw new IllegalArgumentException("Not a slider");
            };
            addTargets(position, from, targets & ~friendly, side, capturesOnly, moves);
        }
    }

    private void generateKing(Position position, int side, boolean capturesOnly, List<Move> moves) {
        long king = position.pieces(Piece.of(side, Piece.KING));
        int from = Long.numberOfTrailingZeros(king);
        addTargets(position, from, AttackTables.KING[from] & ~position.colorPieces(side), side, capturesOnly, moves);
        if (capturesOnly) return;

        int opponent = side ^ 1;
        int rights = position.castlingRights();
        if (side == Piece.WHITE && from == 4) {
            if ((rights & Position.CASTLE_WHITE_KING) != 0
                    && position.pieceAt(7) == Piece.WHITE_ROOK
                    && position.pieceAt(5) == Piece.NONE && position.pieceAt(6) == Piece.NONE
                    && !isSquareAttacked(position, 4, opponent)
                    && !isSquareAttacked(position, 5, opponent)
                    && !isSquareAttacked(position, 6, opponent)) {
                moves.add(new Move(4, 6, Move.KING_CASTLE));
            }
            if ((rights & Position.CASTLE_WHITE_QUEEN) != 0
                    && position.pieceAt(0) == Piece.WHITE_ROOK
                    && position.pieceAt(1) == Piece.NONE && position.pieceAt(2) == Piece.NONE
                    && position.pieceAt(3) == Piece.NONE
                    && !isSquareAttacked(position, 4, opponent)
                    && !isSquareAttacked(position, 3, opponent)
                    && !isSquareAttacked(position, 2, opponent)) {
                moves.add(new Move(4, 2, Move.QUEEN_CASTLE));
            }
        } else if (side == Piece.BLACK && from == 60) {
            if ((rights & Position.CASTLE_BLACK_KING) != 0
                    && position.pieceAt(63) == Piece.BLACK_ROOK
                    && position.pieceAt(61) == Piece.NONE && position.pieceAt(62) == Piece.NONE
                    && !isSquareAttacked(position, 60, opponent)
                    && !isSquareAttacked(position, 61, opponent)
                    && !isSquareAttacked(position, 62, opponent)) {
                moves.add(new Move(60, 62, Move.KING_CASTLE));
            }
            if ((rights & Position.CASTLE_BLACK_QUEEN) != 0
                    && position.pieceAt(56) == Piece.BLACK_ROOK
                    && position.pieceAt(57) == Piece.NONE && position.pieceAt(58) == Piece.NONE
                    && position.pieceAt(59) == Piece.NONE
                    && !isSquareAttacked(position, 60, opponent)
                    && !isSquareAttacked(position, 59, opponent)
                    && !isSquareAttacked(position, 58, opponent)) {
                moves.add(new Move(60, 58, Move.QUEEN_CASTLE));
            }
        }
    }

    private void addTargets(Position position, int from, long targets, int side,
                            boolean capturesOnly, List<Move> moves) {
        while (targets != 0) {
            int to = Long.numberOfTrailingZeros(targets);
            targets &= targets - 1;
            int targetPiece = position.pieceAt(to);
            if (targetPiece != Piece.NONE && Piece.type(targetPiece) == Piece.KING) continue;
            if (targetPiece == Piece.NONE) {
                if (!capturesOnly) moves.add(new Move(from, to, Move.QUIET));
            } else if (Piece.color(targetPiece) != side) {
                moves.add(new Move(from, to, Move.CAPTURE));
            }
        }
    }
}
