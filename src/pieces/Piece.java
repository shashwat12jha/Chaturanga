package pieces;
import  main.Board;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Piece {
    public int col,row;
    public  int xPos , yPos;
    public boolean isWhite;
    public String name;
    public int value;
    BufferedImage sheet;
   //loading img
    {
        try {
            java.io.InputStream is = Piece.class.getResourceAsStream("/res/pieces.png");
            if (is == null) {
                is = Piece.class.getResourceAsStream("/pieces.png");
            }
            if (is != null) {
                sheet = ImageIO.read(is);
            } else {
                java.io.File f = new java.io.File("src/res/pieces.png");
                if (!f.exists()) {
                    f = new java.io.File("C:/Users/shash/OneDrive/Desktop/Chaturanga/src/res/pieces.png");
                }
                sheet = ImageIO.read(f);
            }

        } catch (Exception e) {
            System.err.println("Could not load pieces.png!");
            e.printStackTrace();
        }
    }

    protected int sheetScale = sheet != null ? sheet.getWidth()/6 : 0;
    Image sprite;
    Board board;
    public  Piece(Board board){
        this.board=board;
    }

    public boolean isValidMovement(int col,int row){
        return true;
    }
    public  boolean moveCollidesWithPiece(int col , int row){
        return false;
    }
    public boolean isFirstMove = true;

    public  void paint(Graphics2D g2d){
        g2d.drawImage(sprite,xPos,yPos,null);
    }
}
