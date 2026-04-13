package ParallelGridBitGrid;

import javax.swing.*;
import java.awt.*;

public class Main {

    private static final int WINDOW_WIDTH = 1680;
    private static final int WINDOW_HEIGHT = 980;

    public static void main(String[] args) {

        JFrame frame = new JFrame("Game of Life");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLocation(0, 0);
        frame.setMaximumSize(new Dimension(1920, 1080));

        frame.getContentPane().setLayout(
                new BoxLayout(frame.getContentPane(), BoxLayout.X_AXIS)
        );

        GameManager manager = new GameManager(WINDOW_WIDTH, WINDOW_HEIGHT, 2, 1000000, false);
        JPanel controlPanel = GUI.createControlPanel(manager);

        frame.getContentPane().add(manager);
        frame.getContentPane().add(controlPanel);

        frame.pack();
        frame.setVisible(true);

        manager.requestFocusInWindow();
        manager.start();
    }
}