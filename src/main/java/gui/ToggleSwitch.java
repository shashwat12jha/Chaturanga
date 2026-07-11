package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ToggleSwitch extends JComponent {

    private boolean selected;
    private float thumbPosition = 0f; // 0f = off, 1f = on
    private Timer animationTimer;

    private static final int WIDTH = 40;
    private static final int HEIGHT = 20;
    private static final int BORDER = 2;

    private Color onColor = new Color(52, 199, 89);   // Sleek green
    private Color offColor = new Color(128, 0, 0);    // Maroon
    private Color thumbColor = Color.WHITE;

    private List<ActionListener> listeners = new ArrayList<>();

    public ToggleSwitch() {
        this(false);
    }

    public ToggleSwitch(boolean initialState) {
        this.selected = initialState;
        this.thumbPosition = selected ? 1f : 0f;

        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                setSelected(!selected);
                fireActionPerformed();
            }
        });
    }

    public void addActionListener(ActionListener listener) {
        listeners.add(listener);
    }

    private void fireActionPerformed() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggled");
        for (ActionListener l : listeners) {
            l.actionPerformed(event);
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean b) {
        if (this.selected == b) return;
        this.selected = b;
        animateTo(selected ? 1f : 0f);
    }

    private void animateTo(float target) {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        float step = target > thumbPosition ? 0.1f : -0.1f;

        animationTimer = new Timer(15, e -> {
            thumbPosition += step;
            if ((step > 0 && thumbPosition >= target) || (step < 0 && thumbPosition <= target)) {
                thumbPosition = target;
                animationTimer.stop();
            }
            repaint();
        });
        animationTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Interpolate background color
        int r = (int) (offColor.getRed() + thumbPosition * (onColor.getRed() - offColor.getRed()));
        int g1 = (int) (offColor.getGreen() + thumbPosition * (onColor.getGreen() - offColor.getGreen()));
        int b1 = (int) (offColor.getBlue() + thumbPosition * (onColor.getBlue() - offColor.getBlue()));
        g2.setColor(new Color(r, g1, b1));

        // Draw track
        g2.fill(new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, HEIGHT, HEIGHT));

        // Draw thumb
        int thumbSize = HEIGHT - BORDER * 2;
        int maxTravel = WIDTH - thumbSize - BORDER * 2;
        int thumbX = BORDER + (int) (thumbPosition * maxTravel);
        int thumbY = BORDER;

        g2.setColor(thumbColor);
        g2.fillOval(thumbX, thumbY, thumbSize, thumbSize);

        g2.dispose();
    }
}
