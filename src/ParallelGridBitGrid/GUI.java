package ParallelGridBitGrid;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Objects;

public class GUI {

    public static JPanel createControlPanel(GameManager manager) {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel labelUps = createLabel("Updates Per Second");
        JLabel labelZoom = createLabel("Zoom Level");
        JLabel labelDensity = createLabel("Painting Density");
        JLabel labelBrushSize = createLabel("Painting Brush Size");
        JLabel labelTool = createLabel("Tool");
        JLabel labelGrid = createLabel("Draw Grid");

        JSlider sliderUps = createSlider(10, manager::updateTimer);
        JSlider sliderZoom = createSlider(1, manager::updateCellSize);
        JSlider sliderBrushSize = createSlider(40, manager::setPaintSize);
        JSlider sliderDensity = createSlider(40, manager::setDensity);

        JButton buttonClear = new JButton("Clear All");
        buttonClear.addActionListener(e -> manager.clear());

        JCheckBox checkBoxGrid = new JCheckBox();
        checkBoxGrid.addActionListener(e -> manager.toggleGrid());

        JComboBox<String> toolBox = createToolBox(manager);

        manager.setPaintSize(40);
        manager.setDensity(40);

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

        panel.add(buttonClear);

        panel.add(labelGrid);
        panel.add(checkBoxGrid);

        return panel;
    }

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

    private static JComboBox<String> createToolBox(GameManager manager) {

        String[] tools = {"Brush", "Area", "Eraser", "Clear"};
        JComboBox<String> box = new JComboBox<>(tools);

        box.setMaximumSize(new Dimension(100, 25));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.addItemListener((ItemEvent e) -> {
            String selected = Objects.requireNonNull(box.getSelectedItem()).toString();

            switch (selected) {
                case "Brush" -> manager.setTool(InputHandler.Tool.BRUSH);
                case "Area" -> manager.setTool(InputHandler.Tool.AREA);
                case "Eraser" -> manager.setTool(InputHandler.Tool.ERASER);
                case "Clear" -> manager.setTool(InputHandler.Tool.CLEAR);
            }
        });

        return box;
    }

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

    private interface SliderUpdateAction {
        void onUpdate(int value);
    }
}