package ParallelGridBitGrid;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class InputHandler implements KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    public enum Tool { BRUSH, AREA, ERASER, CLEAR }

    private Tool currentTool = Tool.BRUSH;

    private boolean draggingCamera = false;
    private boolean drawingArea = false;
    private boolean paintingBrush = false;
    private boolean erasing = false;

    private Point lastMouse;
    private Point selectionStart;
    private Point selectionEnd;

    private int paintSize = 2;
    private double density = 1.0;

    private final LongOpenHashSet paintedCells = new LongOpenHashSet(100000);
    private final Random random = new Random();

    private final Game game;
    private final Point camera;
    private final Component component;

    private double cellSize;
    private int zoomLevel;

    public InputHandler(Game game, Point camera, Component component, double cellSize, int zoomLevel) {
        this.game = game;
        this.camera = camera;
        this.component = component;
        this.cellSize = cellSize;
        this.zoomLevel = zoomLevel;
    }

    public void setTool(Tool tool) {
        this.currentTool = tool;
    }

    public void setDensity(int densityPercent) {
        this.density = densityPercent / 100.0;
    }

    public void setPaintSize(int size) {
        this.paintSize = Math.max(0, size);
    }

    public boolean isDrawingArea() {
        return drawingArea;
    }

    public Point getSelectionStart() {
        return selectionStart;
    }

    public Point getSelectionEnd() {
        return selectionEnd;
    }

    public void updateZoom(double cellSize, int zoomLevel) {
        this.cellSize = cellSize;
        this.zoomLevel = zoomLevel;
    }

    // -----------------------
    // Coordinate Helpers
    // -----------------------

    private Point screenToWorld(Point p) {
        int worldX = (int) Math.floor((p.x - camera.x) / cellSize);
        int worldY = (int) Math.floor((p.y - camera.y) / cellSize);
        return new Point(worldX, worldY);
    }

    // -----------------------
    // Painting
    // -----------------------

    private void paintBrush(MouseEvent e) {
        Point world = screenToWorld(e.getPoint());

        for (int dx = -paintSize; dx <= paintSize; dx++) {
            for (int dy = -paintSize; dy <= paintSize; dy++) {

                int brushX = world.x + dx;
                int brushY = world.y + dy;

                long cell = Utility.cordsToLong(brushX, brushY);

                if (paintedCells.add(cell) && random.nextDouble() <= density) {
                    game.spawnCell(cell);
                }
            }
        }
        component.repaint();
    }

    private void eraseBrush(MouseEvent e) {
        Point world = screenToWorld(e.getPoint());

        for (int dx = -paintSize; dx <= paintSize; dx++) {
            for (int dy = -paintSize; dy <= paintSize; dy++) {

                int brushX = world.x + dx;
                int brushY = world.y + dy;

                long cell = Utility.cordsToLong(brushX, brushY);
                game.clearCell(cell);
            }
        }
        component.repaint();
    }

    private void fillSelection() {
        int minX = Math.min(selectionStart.x, selectionEnd.x);
        int minY = Math.min(selectionStart.y, selectionEnd.y);
        int maxX = Math.max(selectionStart.x, selectionEnd.x);
        int maxY = Math.max(selectionStart.y, selectionEnd.y);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (random.nextDouble() <= density) {
                    game.spawnCell(Utility.cordsToLong(x, y));
                }
            }
        }
    }

    private void clearSelection() {
        int minX = Math.min(selectionStart.x, selectionEnd.x);
        int minY = Math.min(selectionStart.y, selectionEnd.y);
        int maxX = Math.max(selectionStart.x, selectionEnd.x);
        int maxY = Math.max(selectionStart.y, selectionEnd.y);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                game.clearCell(Utility.cordsToLong(x, y));
            }
        }
    }

    private void moveCamera(MouseEvent e) {
        Point current = e.getPoint();
        camera.x += current.x - lastMouse.x;
        camera.y += current.y - lastMouse.y;
        lastMouse = current;
        component.repaint();
    }

    // -----------------------
    // Mouse Events
    // -----------------------

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // handled by GameManager (zoom needs center-locking)
    }

    @Override
    public void mousePressed(MouseEvent e) {

        if (SwingUtilities.isLeftMouseButton(e)) {

            if (currentTool == Tool.BRUSH) {
                paintingBrush = true;
                paintBrush(e);

            } else if (currentTool == Tool.AREA) {
                drawingArea = true;
                selectionStart = screenToWorld(e.getPoint());
                selectionEnd = selectionStart;

            } else if (currentTool == Tool.ERASER) {
                erasing = true;
                eraseBrush(e);

            } else if (currentTool == Tool.CLEAR) {
                drawingArea = true;
                selectionStart = screenToWorld(e.getPoint());
                selectionEnd = selectionStart;
            }

        } else if (SwingUtilities.isRightMouseButton(e)) {
            draggingCamera = true;
            lastMouse = e.getPoint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        if (draggingCamera) {
            moveCamera(e);

        } else if (paintingBrush) {
            paintBrush(e);

        } else if (drawingArea) {
            selectionEnd = screenToWorld(e.getPoint());
            component.repaint();

        } else if (erasing) {
            eraseBrush(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        if (drawingArea && selectionStart != null && selectionEnd != null) {
            if (currentTool == Tool.CLEAR) {
                clearSelection();
            } else {
                fillSelection();
            }
        }

        draggingCamera = false;
        paintingBrush = false;
        drawingArea = false;
        erasing = false;

        selectionStart = null;
        selectionEnd = null;
        paintedCells.clear();

        component.repaint();
    }

    // -----------------------
    // Unused
    // -----------------------

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyPressed(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void mouseMoved(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}