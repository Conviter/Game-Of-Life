package NeighbourCountPrimitiveMap;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Objects;

public class Main {

    // -------------------------
    // Constants
    // -------------------------

    private static final int WINDOW_WIDTH = 1680;
    private static final int WINDOW_HEIGHT = 980;

    // -------------------------
    // Entry Point
    // -------------------------

    public static void main(String[] args) {

        JFrame frame = createFrame();
        GamePanel gamePanel = new GamePanel(WINDOW_WIDTH, WINDOW_HEIGHT, 2, 100000, false);
        JPanel controlPanel = createControlPanel(gamePanel);

        frame.getContentPane().add(gamePanel);
        frame.getContentPane().add(controlPanel);

        frame.pack();
        frame.setVisible(true);

        gamePanel.requestFocusInWindow();
        gamePanel.startGameThread();
    }

    // -------------------------
    // Frame Setup
    // -------------------------

    private static JFrame createFrame() {
        JFrame frame = new JFrame("game");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLocation(0, 0);
        frame.setMaximumSize(new Dimension(1920, 1080));

        frame.getContentPane().setLayout(
                new BoxLayout(frame.getContentPane(), BoxLayout.X_AXIS)
        );

        return frame;
    }

    // -------------------------
    // Control Panel Setup
    // -------------------------

    private static JPanel createControlPanel(GamePanel gamePanel) {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Labels
        JLabel labelUps = createLabel("Updates Per Second");
        JLabel labelZoom = createLabel("Zoom Level");
        JLabel labelDensity = createLabel("Painting Density");
        JLabel labelBrushSize = createLabel("Painting Brush Size");
        JLabel labelTool = createLabel("Tool");

        // Sliders
        JSlider sliderUps = createSlider(10, gamePanel::updateTimer);
        JSlider sliderZoom = createSlider(1, gamePanel::updateCellSize);
        JSlider sliderBrushSize = createSlider(40, gamePanel::updatePaintSize);
        JSlider sliderDensity = createSlider(40, gamePanel::updateDensity);

        // Apply initial values
        gamePanel.updatePaintSize(40);
        gamePanel.updateDensity(40);

        // Tool Selector
        JComboBox<String> toolBox = createToolBox(gamePanel);

        // Add components in structured order
        panel.add(labelUps);
        panel.add(sliderUps);

        panel.add(labelZoom);
        panel.add(sliderZoom);

        panel.add(labelDensity);
        panel.add(sliderDensity);

        panel.add(labelBrushSize);
        panel.add(sliderBrushSize);

        panel.add(labelTool);
        panel.add(toolBox);

        return panel;
    }

    // -------------------------
    // Component Factories
    // -------------------------

    private static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JSlider createSlider(int initialValue, SliderUpdateAction action) {
        JSlider slider = getBaseSlider();
        slider.setValue(initialValue);

        slider.addChangeListener((ChangeEvent e) -> {
            JSlider source = (JSlider) e.getSource();
            action.onUpdate(source.getValue());
        });

        return slider;
    }

    private static JComboBox<String> createToolBox(GamePanel gamePanel) {

        String[] tools = {"Brush", "Area"};
        JComboBox<String> box = new JComboBox<>(tools);

        box.setMaximumSize(new Dimension(100, 25));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.addItemListener((ItemEvent e) ->
                gamePanel.updateSelection(Objects.requireNonNull(box.getSelectedItem()).toString())
        );

        return box;
    }

    // -------------------------
    // Base Slider Config
    // -------------------------

    public static JSlider getBaseSlider() {
        JSlider slider = new JSlider();

        slider.setMinimum(0);
        slider.setMaximum(100);
        slider.setMinorTickSpacing(1);
        slider.setMajorTickSpacing(20);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setSnapToTicks(true);
        slider.setOrientation(JSlider.HORIZONTAL);

        return slider;
    }

    // -------------------------
    // Functional Interface
    // -------------------------

    private interface SliderUpdateAction {
        void onUpdate(int value);
    }
}