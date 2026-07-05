package pieces;
import main.Board;

import java.awt.image.BufferedImage;

public class Bishop extends Piece{
    public Bishop(Board board ,int col , int row , boolean isWhite){
        super(board);
        this.col=col;
        this.row=row;
        this.xPos=col*board.tileSize;
        this.yPos=row*board.tileSize;
        this.isWhite=isWhite;
        this.name = "Bishop";
        this.sprite=sheet.getSubimage(2*sheetScale,isWhite?0:sheetScale,sheetScale,sheetScale).getScaledInstance(board.tileSize,board.tileSize, BufferedImage.SCALE_SMOOTH);
    }
    @Override
    public boolean moveCollidesWithPiece(int col, int row) {

        // Up-Left
        if (this.col > col && this.row > row) {
            for (int c = this.col - 1, r = this.row - 1;
                 c > col && r > row;
                 c--, r--) {

                if (board.getPiece(c, r) != null)
                    return true;
            }
        }

        // Up-Right
        if (this.col < col && this.row > row) {
            for (int c = this.col + 1, r = this.row - 1;
                 c < col && r > row;
                 c++, r--) {

                if (board.getPiece(c, r) != null)
                    return true;
            }
        }

        // Down-Left
        if (this.col > col && this.row < row) {
            for (int c = this.col - 1, r = this.row + 1;
                 c > col && r < row;
                 c--, r++) {

                if (board.getPiece(c, r) != null)
                    return true;
            }
        }

        // Down-Right
        if (this.col < col && this.row < row) {
            for (int c = this.col + 1, r = this.row + 1;
                 c < col && r < row;
                 c++, r++) {

                if (board.getPiece(c, r) != null)
                    return true;
            }
        }

        return false;
    }
    public  boolean isValidMovement(int col , int row){
        return Math.abs(this.col-col)==Math.abs(this.row-row);
    }
}
