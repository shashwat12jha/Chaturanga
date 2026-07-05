package gui;

import engine.EvalBreakdown;

import javax.swing.*;
import java.awt.*;

/**
 * Chess.com-style vertical evaluation bar.
 *
 * White fills from the bottom, black fills from the top.
 * The bar uses a smooth sigmoid-scaled score so extreme advantages
 * push to the edges but never hit the absolute limit.
 */
public class EvalBar extends JPanel {

    private double evalPercent = 0.0; // -100 (full Black) to +100 (full White)
    private String evalText    = "0.0"; // e.g. "+1.2" or "M5"

    private static final Color WHITE_COLOR = new Color(240, 240, 240);
    private static final Color BLACK_COLOR = new Color(30, 30, 30);
    private static final Color BORDER_COLOR = new Color(80, 80, 80);

    public EvalBar() {
        setPreferredSize(new Dimension(28, 600));
        setBackground(BLACK_COLOR);
        setToolTipText("Evaluation Bar");
    }

    /** Update from an EvalBreakdown — call on the EDT. */
    public void update(EvalBreakdown breakdown) {
        this.evalPercent = breakdown.toEvalBarPercent();
        double cpAbs = Math.abs(breakdown.total) / 100.0;
        this.evalText = (breakdown.total >= 0 ? "+" : "-") + String.format("%.1f", cpAbs);
        repaint();
    }

    /** Direct update with raw centipawn value. */
    public void update(int centipawns) {
        double t = centipawns / 800.0;
        this.evalPercent = t / (1.0 + Math.abs(t)) * 100.0;
        double cpAbs = Math.abs(centipawns) / 100.0;
        this.evalText = (centipawns >= 0 ? "+" : "-") + String.format("%.1f", cpAbs);
        repaint();
    }

    /** Reset to starting position (equal). */
    public void reset() {
        evalPercent = 0.0;
        evalText    = "0.0";
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background = Black side
        g2.setColor(BLACK_COLOR);
        g2.fillRect(0, 0, w, h);

        // White portion — fills from the bottom
        // evalPercent = +100 → full white (100% height), 0 → 50%, -100 → 0%
        double whiteFraction = (evalPercent + 100.0) / 200.0; // 0.0 – 1.0
        int whiteHeight = (int)(h * whiteFraction);

        g2.setColor(WHITE_COLOR);
        g2.fillRect(0, h - whiteHeight, w, whiteHeight);

        // Border
        g2.setColor(BORDER_COLOR);
        g2.drawRect(0, 0, w - 1, h - 1);

        // Midline
        g2.setColor(new Color(120, 120, 120, 180));
        g2.drawLine(0, h / 2, w, h / 2);

        // Eval text — draw near the midpoint
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        g2.setColor(evalPercent >= 0 ? BLACK_COLOR : WHITE_COLOR);
        FontMetrics fm = g2.getFontMetrics();
        int tx = w / 2 - fm.stringWidth(evalText) / 2;
        int ty = evalPercent >= 0
                ? h - whiteHeight + fm.getAscent() + 3
                : h - whiteHeight - 3;
        g2.drawString(evalText, tx, Math.max(fm.getAscent(), Math.min(h - 3, ty)));
    }
}
