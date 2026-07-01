
package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Queen extends Piece{
    public Queen(Board board , int col , int row , boolean isWhite){
        super(board);
        this.col=col;
        this.row=row;
        this.xPos=col*board.tileSize;
        this.yPos=row*board.tileSize;
        this.isWhite=isWhite;
        this.name = "Pawn";
        this.sprite=sheet.getSubimage(1*sheetScale,isWhite?0:sheetScale,sheetScale,sheetScale).getScaledInstance(board.tileSize,board.tileSize, BufferedImage.SCALE_SMOOTH);
    }
    public boolean moveCollidesWithPiece(int col, int row) {
        if(Math.abs(this.col-col)==Math.abs(this.row-row)) {
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
        }
        if(this.col==col || this.row==row){
            //left
            if(this.col>col){
                for(int c=this.col-1;c>col;c--)
                    if(board.getPiece(c,this.row)!=null)
                        return true;
            }
            //false
            if(this.col<col){
                for(int c=this.col+1;c<col;c++)
                    if(board.getPiece(c,this.row)!=null)
                        return true;
            }
            //up
            if(this.row>row){
                for(int r=this.row-1;r>row;r--)
                    if(board.getPiece(this.col,r)!=null)
                        return true;
            }
            //down
            if(this.row<row){
                for(int r=this.row+1;r<row;r++)
                    if(board.getPiece(this.col,r)!=null)
                        return true;
            }

        }

        return false;
    }
    public  boolean isValidMovement(int col , int row){
        return (this.col==col || this.row==row)|| Math.abs(this.col-col)==Math.abs(this.row-row);
    }
}
