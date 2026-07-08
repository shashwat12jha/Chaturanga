package gui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import model.PieceType;

public class PieceRenderer {
    private BufferedImage spriteSheet;
    private int sheetScale;
    private int tileSize;
    
    private Image[][] scaledPieces = new Image[2][6]; // [Color][Type] -> 0=White, 1=Black; Types: K=0, Q=1, R=2, B=3, N=4, P=5

    public PieceRenderer(int tileSize) {
        this.tileSize = tileSize;
        loadSpriteSheet();
        scalePieces();
    }

    private void loadSpriteSheet() {
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/res/pieces.png");
            if (is == null) {
                is = getClass().getResourceAsStream("/pieces.png");
            }
            if (is != null) {
                spriteSheet = ImageIO.read(is);
            } else {
                File f1 = new File("src/main/resources/res/pieces.png");
                File f2 = new File("src/main/resources/pieces.png");
                File f3 = new File("src/res/pieces.png");
                
                if (f1.exists()) {
                    spriteSheet = ImageIO.read(f1);
                } else if (f2.exists()) {
                    spriteSheet = ImageIO.read(f2);
                } else if (f3.exists()) {
                    spriteSheet = ImageIO.read(f3);
                } else {
                    System.err.println("Could not find pieces.png in classpath or src/main/resources/");
                }
            }
            if (spriteSheet != null) {
                sheetScale = spriteSheet.getWidth() / 6;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void scalePieces() {
        if (spriteSheet == null) return;
        
        for (int isWhiteIdx = 0; isWhiteIdx < 2; isWhiteIdx++) {
            boolean isWhite = (isWhiteIdx == 0);
            int yPos = isWhite ? 0 : sheetScale;
            
            scaledPieces[isWhiteIdx][0] = getScaled(0, yPos); // King
            scaledPieces[isWhiteIdx][1] = getScaled(sheetScale, yPos); // Queen
            scaledPieces[isWhiteIdx][3] = getScaled(2 * sheetScale, yPos); // Bishop
            scaledPieces[isWhiteIdx][4] = getScaled(3 * sheetScale, yPos); // Knight
            scaledPieces[isWhiteIdx][2] = getScaled(4 * sheetScale, yPos); // Rook
            scaledPieces[isWhiteIdx][5] = getScaled(5 * sheetScale, yPos); // Pawn
        }
    }

    private Image getScaled(int x, int y) {
        return spriteSheet.getSubimage(x, y, sheetScale, sheetScale)
                .getScaledInstance(tileSize, tileSize, BufferedImage.SCALE_SMOOTH);
    }
    
    private int getTypeIndex(PieceType type) {
        switch (type) {
            case KING: return 0;
            case QUEEN: return 1;
            case BISHOP: return 3;
            case KNIGHT: return 4;
            case ROOK: return 2;
            case PAWN: return 5;
            default: return -1;
        }
    }

    public void drawPiece(Graphics2D g2d, PieceType type, boolean isWhite, int x, int y) {
        if (spriteSheet == null) return;
        
        int colorIdx = isWhite ? 0 : 1;
        int typeIdx = getTypeIndex(type);
        if (typeIdx != -1) {
            g2d.drawImage(scaledPieces[colorIdx][typeIdx], x, y, null);
        }
    }
}
