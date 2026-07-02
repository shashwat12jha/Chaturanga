package main;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {}

        String[] timeOptions = {"10+0", "3+2", "5+5", "1+1", "2+1"};
        String selectedTime = (String) JOptionPane.showInputDialog(null, "Select Time Control:", "Time Control",
                JOptionPane.QUESTION_MESSAGE, null, timeOptions, timeOptions[0]);
        
        if (selectedTime == null) {
            System.exit(0);
        }

        int baseMinutes = Integer.parseInt(selectedTime.split("\\+")[0]);
        int incrementSeconds = Integer.parseInt(selectedTime.split("\\+")[1]);

        JFrame frame = new JFrame("Chaturanga Chess");
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
        Board board = new Board(moveHistoryArea);
        board.setBorder(new LineBorder(new Color(60, 60, 60), 4));
        centerPanel.add(board);

        ChessTimer chessTimer = new ChessTimer(baseMinutes, incrementSeconds, whiteTimerLabel, blackTimerLabel, board);
        board.setChessTimer(chessTimer);

        frame.add(leftPanel, BorderLayout.WEST);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(rightPanel, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        chessTimer.start();
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

        // Wrapper to center the panel properly
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(30, 30, 30));
        wrapper.add(panel);
        return wrapper;
    }
}