package ParallelGridBitGrid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class GameManager extends JPanel implements Runnable {

    private final int screenWidth;
    private final int screenHeight;

    private double cellSize;
    private int zoomLevel = 2;

    private final Game game;
    private final Renderer renderer;
    private final InputHandler inputHandler;

    private Timer timer;

    private final Point camera = new Point(0, 0);

    public GameManager(int screenWidth, int screenHeight, int cellSize, int startingCells, boolean drawGrid) {

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.cellSize = Math.max(1, Math.min(100, cellSize));

        this.game = new Game(startingCells);
        this.renderer = new Renderer(screenWidth, screenHeight, drawGrid);
        this.inputHandler = new InputHandler(game, camera, this, this.cellSize, zoomLevel);

        setPreferredSize(new Dimension(screenWidth, screenHeight));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusable(true);

        addMouseListener(inputHandler);
        addMouseMotionListener(inputHandler);
        addMouseWheelListener(new ZoomListener());
    }

    // -----------------------
    // Public API for GUI
    // -----------------------

    public void toggleGrid() {
        renderer.toggleGrid();
        repaint();
    }

    public void clear() {
        game.clear();
        repaint();
    }

    public void setTool(InputHandler.Tool tool) {
        inputHandler.setTool(tool);
    }

    public void setDensity(int density) {
        inputHandler.setDensity(density);
    }

    public void setPaintSize(int size) {
        inputHandler.setPaintSize(size);
    }

    public void updateTimer(int ups) {

        if (timer == null) return;

        if (ups == 0) {
            timer.stop();
            return;
        }

        if (!timer.isRunning()) timer.start();

        timer.setDelay(1000 / ups);
    }

    public void updateCellSize(int zoomLevel) {
        double newSize;

        if (zoomLevel >= 0) {
            newSize = zoomLevel + 1;
        } else {
            newSize = 1.0 / Math.abs(zoomLevel);
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        double worldX = (centerX - camera.x) / cellSize;
        double worldY = (centerY - camera.y) / cellSize;

        this.cellSize = newSize;
        this.zoomLevel = zoomLevel;

        camera.x = (int) Math.round(centerX - worldX * cellSize);
        camera.y = (int) Math.round(centerY - worldY * cellSize);

        inputHandler.updateZoom(cellSize, zoomLevel);

        repaint();
    }

    // -----------------------
    // Game Loop
    // -----------------------

    public void start() {
        Thread t = new Thread(this);
        t.start();
    }

    @Override
    public void run() {
        timer = new Timer(100, e -> {
            game.applyRules();
            repaint();
        });
        timer.start();
    }

    // -----------------------
    // Rendering
    // -----------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        renderer.render(
                g,
                game,
                camera,
                cellSize,
                zoomLevel,
                inputHandler.isDrawingArea(),
                inputHandler.getSelectionStart(),
                inputHandler.getSelectionEnd()
        );
    }

    // -----------------------
    // Zoom Listener
    // -----------------------

    private class ZoomListener implements MouseWheelListener {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            int rotation = e.getWheelRotation();
            int change = (rotation < 0) ? 1 : -1;

            updateCellSize(zoomLevel + change);
        }
    }
}