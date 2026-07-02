package main;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChessTimer {
    private int whiteTimeRemaining; // stored in milliseconds
    private int blackTimeRemaining; // stored in milliseconds
    private int incrementMs;
    private boolean isWhiteTurn = true;
    private Timer timer;
    private JLabel whiteLabel;
    private JLabel blackLabel;
    private Board board;

    public ChessTimer(int baseMinutes, int incrementSeconds, JLabel whiteLabel, JLabel blackLabel, Board board) {
        this.whiteTimeRemaining = baseMinutes * 60 * 1000;
        this.blackTimeRemaining = baseMinutes * 60 * 1000;
        this.incrementMs = incrementSeconds * 1000;
        this.whiteLabel = whiteLabel;
        this.blackLabel = blackLabel;
        this.board = board;

        updateLabels();

        timer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (board.isGameOver) {
                    timer.stop();
                    return;
                }

                if (isWhiteTurn) {
                    whiteTimeRemaining -= 100;
                    if (whiteTimeRemaining <= 0) {
                        whiteTimeRemaining = 0;
                        timer.stop();
                        updateLabels();
                        board.handleTimeout(true);
                    }
                } else {
                    blackTimeRemaining -= 100;
                    if (blackTimeRemaining <= 0) {
                        blackTimeRemaining = 0;
                        timer.stop();
                        updateLabels();
                        board.handleTimeout(false);
                    }
                }
                updateLabels();
            }
        });
    }

    public void start() {
        timer.start();
    }
    
    public void stop() {
        timer.stop();
    }

    public void switchTurn() {
        if (isWhiteTurn) {
            whiteTimeRemaining += incrementMs;
        } else {
            blackTimeRemaining += incrementMs;
        }
        isWhiteTurn = !isWhiteTurn;
        updateLabels();
    }

    private void updateLabels() {
        whiteLabel.setText(formatTime(whiteTimeRemaining));
        blackLabel.setText(formatTime(blackTimeRemaining));
    }

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int tenths = (ms % 1000) / 100;
        if (minutes > 0) {
            return String.format("%02d:%02d", minutes, seconds);
        } else {
            return String.format("%02d.%d", seconds, tenths);
        }
    }
}
