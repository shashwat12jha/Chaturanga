package main;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PGNExporter {
    public static void export(JTextArea moveHistoryArea, JFrame parentFrame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export PGN");
        fileChooser.setSelectedFile(new File("game.pgn"));
        int userSelection = fileChooser.showSaveDialog(parentFrame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (FileWriter fileWriter = new FileWriter(fileToSave)) {
                fileWriter.write(moveHistoryArea.getText());
                JOptionPane.showMessageDialog(parentFrame, "PGN successfully exported!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(parentFrame, "Error exporting PGN: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
