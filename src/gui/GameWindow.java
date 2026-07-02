package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.JFileChooser;
import io.PGNExporter;
import utils.ChessTimer;

public class GameWindow {
    
    private void rebuildMoveHistory(JTextArea area, BoardPanel board) {
        area.setText("");
        rules.SANFormatter sanFormatter = new rules.SANFormatter();
        int fullMove = 1;
        for (int i = 0; i < board.getState().getMoveHistory().size(); i++) {
            model.Move m = board.getState().getMoveHistory().get(i);
            String san = sanFormatter.toSAN(m, false, false); // Placeholder check/mate info
            if (m.piece.isWhite) {
                area.append(fullMove + ". " + san + " ");
            } else {
                area.append(san + "\n");
                fullMove++;
            }
        }
    }

    public GameWindow(int baseMinutes, int incrementSeconds) {
        JFrame frame = new JFrame("Chaturanga Chess - Refactored");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(30, 30, 30));

        // --- Right Panel (Move History & PGN) ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        rightPanel.setBackground(new Color(30, 30, 30));
        rightPanel.setPreferredSize(new Dimension(280, 750));

        JLabel historyTitle = new JLabel("Move History", SwingConstants.CENTER);
        historyTitle.setForeground(new Color(220, 220, 220));
        historyTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        historyTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        rightPanel.add(historyTitle, BorderLayout.NORTH);

        JTextArea moveHistoryArea = new JTextArea();
        moveHistoryArea.setEditable(false);
        moveHistoryArea.setFont(new Font("Consolas", Font.PLAIN, 15));
        moveHistoryArea.setBackground(new Color(45, 45, 45));
        moveHistoryArea.setForeground(new Color(220, 220, 220));
        moveHistoryArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JScrollPane scrollPane = new JScrollPane(moveHistoryArea);
        scrollPane.setBorder(new LineBorder(new Color(60, 60, 60), 1));
        scrollPane.getVerticalScrollBar().setBackground(new Color(45, 45, 45));
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        JButton exportBtn = new JButton("Export PGN");
        exportBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        exportBtn.setBackground(new Color(60, 100, 180));
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setFocusPainted(false);
        exportBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportBtn.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(new Color(30, 30, 30));
        btnPanel.setBorder(new EmptyBorder(15, 0, 0, 0));
        btnPanel.add(exportBtn);
        rightPanel.add(btnPanel, BorderLayout.SOUTH);
        exportBtn.addActionListener(e -> PGNExporter.export(moveHistoryArea, frame));

        // --- Left Panel (Timers) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, 750));
        leftPanel.setBackground(new Color(30, 30, 30));
        leftPanel.setBorder(new EmptyBorder(20, 10, 20, 10));

        JLabel blackTimerLabel = new JLabel("00:00");
        JPanel blackPanel = createPlayerPanel("Black", blackTimerLabel, new Color(45, 45, 45), Color.WHITE);
        
        JLabel whiteTimerLabel = new JLabel("00:00");
        JPanel whitePanel = createPlayerPanel("White", whiteTimerLabel, new Color(240, 240, 240), Color.BLACK);

        leftPanel.add(blackPanel, BorderLayout.NORTH);
        leftPanel.add(whitePanel, BorderLayout.SOUTH);

        // --- Center Panel (Board) ---
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(30, 30, 30));
        centerPanel.setBorder(new EmptyBorder(20, 0, 20, 0));
        BoardPanel board = new BoardPanel();
        board.setBorder(new LineBorder(new Color(60, 60, 60), 4));
        centerPanel.add(board);

        // --- Toolbar ---
        JPanel toolbar = new JPanel();
        toolbar.setBackground(new Color(45, 45, 45));
        
        JButton btnRestart = new JButton("Restart");
        JButton btnUndo = new JButton("Undo");
        JButton btnRedo = new JButton("Redo");
        JButton btnSave = new JButton("Save FEN");
        JButton btnLoad = new JButton("Load FEN");
        
        toolbar.add(btnRestart);
        toolbar.add(btnUndo);
        toolbar.add(btnRedo);
        toolbar.add(btnSave);
        toolbar.add(btnLoad);
        
        frame.add(toolbar, BorderLayout.NORTH);
        
        btnRestart.addActionListener(e -> board.restart());
        btnUndo.addActionListener(e -> board.undo());
        btnRedo.addActionListener(e -> board.redo());
        
        btnSave.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("save.fen"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.writeString(chooser.getSelectedFile().toPath(), io.FENParser.toFEN(board.getState()));
                    JOptionPane.showMessageDialog(frame, "Game saved!");
                } catch (IOException ex) {}
            }
        });
        
        btnLoad.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    String fen = Files.readString(chooser.getSelectedFile().toPath()).trim();
                    board.loadGame(fen);
                } catch (IOException ex) {}
            }
        });

        // --- Timer Setup ---
        ChessTimer chessTimer = new ChessTimer(baseMinutes, incrementSeconds, board.getState());
        chessTimer.setWhiteLabelUpdater(whiteTimerLabel::setText);
        chessTimer.setBlackLabelUpdater(blackTimerLabel::setText);
        chessTimer.setOnTimeUp(() -> {
            JOptionPane.showMessageDialog(frame, "Time's up!");
            board.getState().setGameOver(true);
        });
        
        rules.SANFormatter sanFormatter = new rules.SANFormatter();
        
        board.setOnMoveListener(move -> {
            chessTimer.addIncrement(move.piece.isWhite);
            chessTimer.setState(board.getState()); // Update state reference
            
            String san = sanFormatter.toSAN(move, false, board.getState().isGameOver());
            
            if (move.piece.isWhite) {
                moveHistoryArea.append(board.getState().getFullMoveNumber() + ". " + san + " ");
            } else {
                moveHistoryArea.append(san + "\n");
            }
        });
        
        board.setOnStateChangedListener(() -> {
            chessTimer.setState(board.getState());
            rebuildMoveHistory(moveHistoryArea, board);
        });

        chessTimer.start();

        frame.add(leftPanel, BorderLayout.WEST);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(rightPanel, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    private static JPanel createPlayerPanel(String playerName, JLabel timerLabel, Color bgColor, Color fgColor) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(bgColor);
        panel.setBorder(new LineBorder(new Color(80, 80, 80), 2, true));
        panel.setPreferredSize(new Dimension(160, 100));

        JLabel nameLabel = new JLabel(playerName);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        nameLabel.setForeground(fgColor);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(10, 0, 5, 0));

        timerLabel.setFont(new Font("Consolas", Font.BOLD, 32));
        timerLabel.setForeground(fgColor);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        panel.add(nameLabel);
        panel.add(timerLabel);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(30, 30, 30));
        wrapper.add(panel);
        return wrapper;
    }
}
