package main;
import pieces.*;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class Board  extends JPanel {
    public int tileSize = 85;
    public String fenStringPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    int cols = 8 , rows = 8;
    Input input = new Input(this);
    public  Piece selectedPiece;
    ArrayList<Piece> pieceList= new ArrayList<>();
        public  Board(){
            this.setPreferredSize(new Dimension(cols*tileSize,rows*tileSize));
            this.addMouseListener(input);
            this.addMouseMotionListener(input);
            loadPositionFromFEN(fenStringPosition);
        }


        public Piece getPiece(int col,int row){
            for(Piece piece : pieceList){
                if(piece.col==col && piece.row==row) return piece;
            }
            return null;
        }

        Piece findKing(boolean isWhite){
            for(Piece piece : pieceList){
                if(isWhite==piece.isWhite && piece.name.equals("King")) return piece;

            }
            return null;
        }
        public void loadPositionFromFEN(String fenString){
            pieceList.clear();
            String[] parts = fenString.split(" ");
            String position = parts[0];
            int row = 0;
            int col = 0;

            for (int i = 0; i < position.length(); i++) {
                char ch = position.charAt(i);

                if (ch == '/') {
                    row++;
                    col = 0;
                } else if (Character.isDigit(ch)) {
                    col += Character.getNumericValue(ch);
                } else {
                    boolean isWhite = Character.isUpperCase(ch);
                    char pieceChar = Character.toLowerCase(ch);

                    switch (pieceChar) {
                        case 'r':
                            pieceList.add(new Rook(this, col, row, isWhite));
                            break;

                        case 'n':
                            pieceList.add(new Knight( this, col, row, isWhite));
                            break;

                        case 'b':
                            pieceList.add(new Bishop( this, col, row, isWhite));
                            break;

                        case 'q':
                            pieceList.add(new Queen( this, col, row, isWhite));
                            break;

                        case 'k':
                            pieceList.add(new King( this, col, row, isWhite));
                            break;

                        case 'p':
                            pieceList.add(new Pawn( this, col, row, isWhite));
                            break;
                    }

                    col++;
                }
            }
            isWhiteToMove = parts[1].equals("w");
            // castling
            Piece bqr = getPiece(0, 0);
            if (bqr instanceof Rook) {
                bqr.isFirstMove = parts[2].contains("q");
            }

            Piece bkr = getPiece(7, 0);
            if (bkr instanceof Rook) {
                bkr.isFirstMove = parts[2].contains("k");
            }

            Piece wqr = getPiece(0, 7);
            if (wqr instanceof Rook) {
                wqr.isFirstMove = parts[2].contains("Q");
            }

            Piece wkr = getPiece(7, 7);
            if (wkr instanceof Rook) {
                wkr.isFirstMove = parts[2].contains("K");
            }

            // en passant square
            if (parts[3].equals("-")) {
                enPassantTile = -1;
            } else {
                enPassantTile = (7 - (parts[3].charAt(1) - '1')) * 8
                        + (parts[3].charAt(0) - 'a');
            }
        }
        public  void paintComponent(Graphics g){
            Graphics2D g2d = (Graphics2D) g;
            //paint board
            for(int r = 0 ; r < rows; r++){
                for(int c = 0 ; c < cols ; c++){
                    g2d.setColor((((r+c)%2)==0)?new Color(227, 198, 181):new Color(157, 105,77));
                    g2d.fillRect(c*tileSize,r*tileSize,tileSize,tileSize);
                }
            }
            //paint hightlights
            if(selectedPiece!=null)
            for(int r = 0 ; r < rows; r++){
                for(int c = 0 ; c < cols ; c++){
                   if (isValidMove(new Move(this,selectedPiece,c,r))){
                       g2d.setColor(new Color(68,180,57,190));
                       g2d.fillRect(c*tileSize,r*tileSize,tileSize,tileSize);
                   }
                }
            }
            //paint pieces
            for (Piece piece : pieceList){
                piece.paint(g2d);
            }
         }
        public int enPassantTile =-1;
    private boolean isWhiteToMove=true;
    private boolean isGameOver=!true;
    int halfMoveClock = 0;
    public void makeMove(Move move) {
            boolean isPawnMove= move.piece.name.equals("Pawn");
            boolean capture = move.capture!=null;

            if(isPawnMove || capture) halfMoveClock=0;
            else halfMoveClock++;

        if (halfMoveClock >= 100) {
            JOptionPane.showMessageDialog(this, "Draw by 50-Move Rule", "Game Over Son", JOptionPane.INFORMATION_MESSAGE);

            isGameOver = true;

        }
            if(move.piece.name.equals("Pawn")) movePawn(move);
            else enPassantTile = -1;
            if(move.piece.name.equals("King")) {
                moveKing(move);
            }

                move.piece.isFirstMove = false;
                move.piece.col = move.newCol;
                move.piece.row = move.newRow;
                move.piece.xPos = move.newCol * tileSize;
                move.piece.yPos = move.newRow * tileSize;
                capture(move.capture);
                isWhiteToMove=!isWhiteToMove;

                String currentFEN = generateFEN();
                positionHistory.put(currentFEN, positionHistory.getOrDefault(currentFEN, 0) + 1);
                updateGameState(currentFEN);
    }
    HashMap<String, Integer> positionHistory = new HashMap<>();
    public String generateFEN() {
        StringBuilder fen = new StringBuilder();

        // ---------------- Piece Placement ----------------
        for (int row = 0; row < 8; row++) {
            int empty = 0;

            for (int col = 0; col < 8; col++) {

                Piece piece = getPiece(col, row);

                if (piece == null) {
                    empty++;
                } else {

                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }

                    char c = switch (piece.name) {
                        case "Pawn" -> 'p';
                        case "Knight" -> 'n';
                        case "Bishop" -> 'b';
                        case "Rook" -> 'r';
                        case "Queen" -> 'q';
                        case "King" -> 'k';
                        default -> '?';
                    };

                    if (piece.isWhite)
                        c = Character.toUpperCase(c);

                    fen.append(c);
                }
            }

            if (empty > 0)
                fen.append(empty);

            if (row != 7)
                fen.append('/');
        }

        // ---------------- Side to Move ----------------
        fen.append(isWhiteToMove ? " w " : " b ");

        // ---------------- Castling Rights ----------------
        StringBuilder castling = new StringBuilder();

        Piece whiteKing = findKing(true);
        Piece blackKing = findKing(false);

        if (whiteKing != null && !whiteKing.isFirstMove) {

            Piece rook = getPiece(7, 7);
            if (rook != null &&
                    rook.name.equals("Rook") &&
                    rook.isWhite &&
                    !rook.isFirstMove)
                castling.append("K");

            rook = getPiece(0, 7);
            if (rook != null &&
                    rook.name.equals("Rook") &&
                    rook.isWhite &&
                    !rook.isFirstMove)
                castling.append("Q");
        }

        if (blackKing != null && !blackKing.isFirstMove) {

            Piece rook = getPiece(7, 0);
            if (rook != null &&
                    rook.name.equals("Rook") &&
                    !rook.isWhite &&
                    !rook.isFirstMove)
                castling.append("k");

            rook = getPiece(0, 0);
            if (rook != null &&
                    rook.name.equals("Rook") &&
                    !rook.isWhite &&
                    !rook.isFirstMove)
                castling.append("q");
        }

        if (castling.length() == 0)
            fen.append("- ");
        else
            fen.append(castling).append(" ");

        // ---------------- En Passant ----------------
        if (enPassantTile == -1) {
            fen.append("- ");
        } else {
            int col = enPassantTile % 8;
            int row = enPassantTile / 8;

            char file = (char) ('a' + col);
            char rank = (char) ('8' - row);

            fen.append(file).append(rank).append(" ");
        }

        // ---------------- Halfmove & Fullmove ----------------
        fen.append("0 1");

        return fen.toString();
    }

    private void updateGameState(String currentFEN) {
        Piece king = findKing(isWhiteToMove);
        if(checkScanner.isGameOver(king)){
            if(checkScanner.isKingChecked(new Move(this,king,king.col,king.row))){
                if(isWhiteToMove)
                    JOptionPane.showMessageDialog(this, "Checkmate! White Wins!", "Game Over Son", JOptionPane.INFORMATION_MESSAGE);
                else
                    JOptionPane.showMessageDialog(this, "Checkmate! Black Wins!", "Game Over Son", JOptionPane.INFORMATION_MESSAGE);
            }
            else{
                JOptionPane.showMessageDialog(this, "Stalemate! It's a draw!", "Game Over Son", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        else if(positionHistory.get(currentFEN)>=3) {
            isGameOver=true;
            JOptionPane.showMessageDialog(this, "3-Fold Repetition ! It's a draw!", "Game Over Son", JOptionPane.INFORMATION_MESSAGE);
        }

    }


    private void movePawn(Move move) {

            //enPassant
        int colorIndex = move.piece.isWhite?1:-1;
        if(getTileNum(move.newCol,move.newRow)==enPassantTile){
            move.capture=getPiece(move.newCol,move.newRow+colorIndex);
        }
        if(Math.abs(move.piece.row-move.newRow)==2){
            enPassantTile = getTileNum(move.newCol,move.newRow+colorIndex);
        }
        else enPassantTile = -1;

        //promotion

        colorIndex=move.piece.isWhite?0:7;
        if(move.newRow==colorIndex) promotePawn(move);

    }

    private void promotePawn(Move move) {
        //pieceList.add(new Queen(this,move.newCol,move.newRow,move.piece.isWhite));
        String[] options = {"Queen", "Rook", "Bishop", "Knight"};
        int choice = JOptionPane.showOptionDialog(this, "Choose piece to promote to:",
                "Pawn Promotion", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        switch (choice){
            case 0 : {
                pieceList.add(new Queen(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            }
            case 1 : {
                pieceList.add(new Rook(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            }
            case 2 : {
                pieceList.add(new Bishop(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            }
            case 3 : {
                pieceList.add(new Knight(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            }
        }
        capture(move.piece);
    }


    public void capture(Piece piece){
            pieceList.remove(piece);
    }

    public CheckScanner checkScanner = new CheckScanner(this);

    public boolean isValidMove(Move move) {
        if(move.piece.isWhite!=isWhiteToMove) return false;
        if(isGameOver) return false;

            if(sameTeam(move.piece,move.capture)){
                return false;
            }
            if(!move.piece.isValidMovement(move.newCol,move.newRow)) return false;
            if(move.piece.moveCollidesWithPiece(move.newCol,move.newRow)) return false;
            if(checkScanner.isKingChecked(move )) return false;
            if(move.capture != null && move.capture.name.equals("King")) return false;
            return true;
    }

    public int getTileNum(int col , int row){
        return row*rows + col;
    }

    public boolean sameTeam(Piece p1 , Piece p2){
            if(p1==null || p2==null) return false;
            return p1.isWhite==p2.isWhite;
    }


    private void moveKing(Move move){
            if(Math.abs(move.piece.col-move.newCol)==2){
                Piece rook;
                if(move.piece.col<move.newCol){
                    rook=getPiece(7,move.piece.row);
                    rook.col = 5;
                }
                else  {
                    rook=getPiece(0,move.piece.row);
                    rook.col = 3;
                }
                rook.xPos=rook.col*tileSize;
                rook.isFirstMove = false;
            }
            else  if  (inSufficientMaterial(true) && inSufficientMaterial(false)){
                System.out.println("Insufficient Material");
                isGameOver=true;
            }
    }
    private boolean inSufficientMaterial(boolean isWhite){
        ArrayList<String> names = pieceList.stream()
                .filter(p->p.isWhite==isWhite)
                .map(p->p.name)
                .collect(Collectors.toCollection(ArrayList::new));
        if(names.contains("Queen")||names.contains("Pawn")||names.contains("Rook")) return false;
        return names.size()<3;


    }
}
