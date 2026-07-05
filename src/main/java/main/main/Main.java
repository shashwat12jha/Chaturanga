package main;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import gui.GameWindow;

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

        new GameWindow(baseMinutes, incrementSeconds);
    }
}