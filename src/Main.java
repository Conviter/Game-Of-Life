import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        JPanel panel = new JPanel();
        JLabel label = new JLabel("Updates Per Second");
        BoxLayout boxLayout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        GamePanel gamePanel = new GamePanel(1770, 980, 2, 100000, false);
        JSlider sliderUps = new JSlider();
        JSlider sliderSize = new JSlider();
        JSlider sliderPaintSize = new JSlider();
        JSlider sliderDensity = new JSlider();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setTitle("game");
        frame.setLocation(0,0);
        frame.setVisible(true);
        frame.setMaximumSize(new Dimension(1920, 1080));
        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.X_AXIS));


        sliderSize.setMinimum(1);
        sliderSize.setMaximum(100);
        sliderSize.setValue(2);
        sliderSize.setMinorTickSpacing(1);
        sliderSize.setMajorTickSpacing(20);
        sliderSize.setPaintTicks(true);
        sliderSize.setPaintLabels(true);
        sliderSize.setSnapToTicks(true);
        sliderSize.setOrientation(JSlider.VERTICAL);
        sliderSize.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider) e.getSource();
                gamePanel.updateCellSize(source.getValue());
            }
        });



        sliderUps.setMinimum(0);
        sliderUps.setMaximum(100);
        sliderUps.setValue(1);
        sliderUps.setMinorTickSpacing(1);
        sliderUps.setMajorTickSpacing(20);
        sliderUps.setPaintTicks(true);
        sliderUps.setPaintLabels(true);
        sliderUps.setSnapToTicks(true);
        sliderUps.setOrientation(JSlider.VERTICAL);
        sliderUps.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider) e.getSource();
                gamePanel.updateTimer(source.getValue());
            }
        });

        sliderPaintSize.setMinimum(1);
        sliderPaintSize.setMaximum(100);
        sliderPaintSize.setValue(1);
        sliderPaintSize.setMinorTickSpacing(1);
        sliderPaintSize.setMajorTickSpacing(20);
        sliderPaintSize.setPaintTicks(true);
        sliderPaintSize.setPaintLabels(true);
        sliderPaintSize.setSnapToTicks(true);
        sliderPaintSize.setOrientation(JSlider.VERTICAL);
        sliderPaintSize.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider) e.getSource();
                gamePanel.updatePaintSize(source.getValue());
            }
        });

        sliderDensity.setMinimum(1);
        sliderDensity.setMaximum(100);
        sliderDensity.setValue(1);
        sliderDensity.setMinorTickSpacing(1);
        sliderDensity.setMajorTickSpacing(20);
        sliderDensity.setPaintTicks(true);
        sliderDensity.setPaintLabels(true);
        sliderDensity.setSnapToTicks(true);
        sliderDensity.setOrientation(JSlider.VERTICAL);
        sliderDensity.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider) e.getSource();
                gamePanel.updateDensity(source.getValue());
            }
        });

        JCheckBox paintingCheck = new JCheckBox();
        paintingCheck.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                gamePanel.painting = !gamePanel.painting;
            }
        });
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.setLayout(boxLayout);
        frame.getContentPane().add(gamePanel);
        panel.add(label);
        panel.add(sliderUps);
        panel.add(paintingCheck);
        frame.add(sliderPaintSize);

        frame.getContentPane().add(panel);
        frame.getContentPane().add(sliderSize);
        frame.getContentPane().add(sliderDensity);
        frame.pack();
        gamePanel.requestFocusInWindow();
        gamePanel.startGameThreat();
    }
}