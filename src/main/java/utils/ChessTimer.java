package utils;

import javax.swing.Timer;
import java.util.function.Consumer;
import model.GameState;

public class ChessTimer {
    private int whiteTimeMs;
    private int blackTimeMs;
    private final int incrementMs;
    private final Timer timer;
    private GameState state;
    
    private Consumer<String> whiteLabelUpdater;
    private Consumer<String> blackLabelUpdater;
    private Runnable onTimeUp;

    public ChessTimer(int baseMinutes, int incrementSeconds, GameState state) {
        this.whiteTimeMs = baseMinutes * 60 * 1000;
        this.blackTimeMs = baseMinutes * 60 * 1000;
        this.incrementMs = incrementSeconds * 1000;
        this.state = state;
        
        timer = new Timer(100, e -> updateTime());
    }
    
    public void setWhiteLabelUpdater(Consumer<String> updater) {
        this.whiteLabelUpdater = updater;
    }
    
    public void setBlackLabelUpdater(Consumer<String> updater) {
        this.blackLabelUpdater = updater;
    }
    
    public void setOnTimeUp(Runnable callback) {
        this.onTimeUp = callback;
    }
    
    public void setState(GameState state) {
        this.state = state;
    }

    public void start() {
        timer.start();
        updateLabels();
    }
    
    public void stop() {
        timer.stop();
    }

    private void updateTime() {
        if (state.isGameOver()) {
            timer.stop();
            return;
        }

        if (state.isWhiteToMove()) {
            whiteTimeMs -= 100;
            if (whiteTimeMs <= 0) {
                whiteTimeMs = 0;
                timeUp();
            }
        } else {
            blackTimeMs -= 100;
            if (blackTimeMs <= 0) {
                blackTimeMs = 0;
                timeUp();
            }
        }
        updateLabels();
    }
    
    public void addIncrement(boolean forWhite) {
        if (forWhite) whiteTimeMs += incrementMs;
        else blackTimeMs += incrementMs;
        updateLabels();
    }

    private void timeUp() {
        timer.stop();
        if (onTimeUp != null) onTimeUp.run();
    }

    private void updateLabels() {
        if (whiteLabelUpdater != null) whiteLabelUpdater.accept(formatTime(whiteTimeMs));
        if (blackLabelUpdater != null) blackLabelUpdater.accept(formatTime(blackTimeMs));
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / 1000) / 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
