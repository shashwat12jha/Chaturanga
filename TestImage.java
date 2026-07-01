import javax.imageio.ImageIO;
import java.io.File;

public class Test {
    public static void main(String[] args) throws Exception {
        File f = new File("C:/Users/shash/OneDrive/Desktop/Chaturanga/src/res/pieces.png");
        System.out.println("Exists: " + f.exists());
        System.out.println("Size: " + f.length());
        java.awt.image.BufferedImage img = ImageIO.read(f);
        System.out.println("Image: " + img);
    }
}
