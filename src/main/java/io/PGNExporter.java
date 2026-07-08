package io;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PGNExporter {

    public static void export(JTextArea moveHistoryArea, JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save PGN File");
        
        // Suggest a default filename
        fileChooser.setSelectedFile(new File("game.pgn"));
        
        int userSelection = fileChooser.showSaveDialog(frame);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            
            // Add .pgn extension if missing
            if (!fileToSave.getName().toLowerCase().endsWith(".pgn")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".pgn");
            }

            try (FileWriter writer = new FileWriter(fileToSave)) {
                // Write standard PGN headers
                String dateStr = new SimpleDateFormat("yyyy.MM.dd").format(new Date());
                writer.write("[Event \"Casual Game\"]\n");
                writer.write("[Site \"Chaturanga Chess App\"]\n");
                writer.write("[Date \"" + dateStr + "\"]\n");
                writer.write("[Round \"-\"]\n");
                writer.write("[White \"Player 1\"]\n");
                writer.write("[Black \"Player 2\"]\n");
                writer.write("[Result \"*\"]\n\n");
                
                // Write the moves
                writer.write(moveHistoryArea.getText());
                
                JOptionPane.showMessageDialog(frame, "PGN successfully exported to:\n" + fileToSave.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Failed to save PGN file:\n" + e.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
