package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameState {
    private Piece[] board; // 64 squares
    private boolean whiteToMove;
    private boolean[] castlingRights; // [White KingSide, White QueenSide, Black KingSide, Black QueenSide]
    private int enPassantTarget; // -1 if none, 0-63 tile index
    private int halfMoveClock;
    private int fullMoveNumber;
    private Map<String, Integer> positionHistory;
    private List<Move> moveHistory;
    private boolean gameOver;
    private GameResult result;

    public GameState() {
        this.board = new Piece[64];
        this.whiteToMove = true;
        this.castlingRights = new boolean[]{true, true, true, true};
        this.enPassantTarget = -1;
        this.halfMoveClock = 0;
        this.fullMoveNumber = 1;
        this.positionHistory = new HashMap<>();
        this.moveHistory = new ArrayList<>();
        this.gameOver = false;
        this.result = GameResult.IN_PROGRESS;
    }

    private GameState(GameState other) {
        this.board = new Piece[64];
        for (int i = 0; i < 64; i++) {
            if (other.board[i] != null) {
                this.board[i] = other.board[i].copy();
            }
        }
        this.whiteToMove = other.whiteToMove;
        this.castlingRights = other.castlingRights.clone();
        this.enPassantTarget = other.enPassantTarget;
        this.halfMoveClock = other.halfMoveClock;
        this.fullMoveNumber = other.fullMoveNumber;
        this.positionHistory = new HashMap<>(other.positionHistory);
        this.moveHistory = new ArrayList<>(other.moveHistory); // Shallow copy is fine for moves as they are immutable
        this.gameOver = other.gameOver;
        this.result = other.result;
    }

    public GameState copy() {
        return new GameState(this);
    }

    public Piece getPiece(int col, int row) {
        if (col < 0 || col > 7 || row < 0 || row > 7) return null;
        return board[row * 8 + col];
    }

    public void setPiece(int col, int row, Piece piece) {
        if (col < 0 || col > 7 || row < 0 || row > 7) return;
        board[row * 8 + col] = piece;
    }

    public Piece getPiece(int index) {
        if (index < 0 || index > 63) return null;
        return board[index];
    }

    public void setPiece(int index, Piece piece) {
        if (index < 0 || index > 63) return;
        board[index] = piece;
    }

    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    public void setWhiteToMove(boolean whiteToMove) {
        this.whiteToMove = whiteToMove;
    }

    public boolean getCastlingRight(int index) {
        return castlingRights[index];
    }

    public void setCastlingRight(int index, boolean value) {
        castlingRights[index] = value;
    }
    
    public boolean[] getCastlingRights() {
        return castlingRights;
    }

    public int getEnPassantTarget() {
        return enPassantTarget;
    }

    public void setEnPassantTarget(int enPassantTarget) {
        this.enPassantTarget = enPassantTarget;
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }

    public void setHalfMoveClock(int halfMoveClock) {
        this.halfMoveClock = halfMoveClock;
    }

    public int getFullMoveNumber() {
        return fullMoveNumber;
    }

    public void setFullMoveNumber(int fullMoveNumber) {
        this.fullMoveNumber = fullMoveNumber;
    }

    public void incrementFullMoveNumber() {
        this.fullMoveNumber++;
    }

    public Map<String, Integer> getPositionHistory() {
        return positionHistory;
    }

    public void recordPosition(String fen) {
        positionHistory.put(fen, positionHistory.getOrDefault(fen, 0) + 1);
    }

    public List<Move> getMoveHistory() {
        return moveHistory;
    }

    public void addMove(Move move) {
        moveHistory.add(move);
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public Piece[] getBoard() {
        return board;
    }
}
