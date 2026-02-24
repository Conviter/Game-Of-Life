import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GamePanel extends JPanel implements Runnable,
        KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    // -----------------------
    // Config
    // -----------------------

    private final int screenWidth;
    private final int screenHeight;

    private int cellSize;
    private boolean drawGrid;

    private static final Color GRID_ZOOMED_IN  = new Color(150, 150, 150, 40);
    private static final Color GRID_ZOOMED_OUT = new Color(150, 150, 150, 20);

    // -----------------------
    // Game State
    // -----------------------

    private final Game game;
    private Timer timer;

    private final Point camera = new Point(0, 0);


    // -----------------------
    // Input State
    // -----------------------

    private enum Tool { BRUSH, AREA }
    private Tool currentTool = Tool.BRUSH;

    private boolean draggingCamera = false;
    private boolean drawingArea = false;
    private boolean paintingBrush = false;

    private Point lastMouse;
    private Point selectionStart;
    private Point selectionEnd;

    private int paintSize = 2;
    private double density = 1.0;

    private final Set<Game.Cell> paintedCells = new HashSet<>();
    private final Random random = new Random();

    // -----------------------
    // Constructor
    // -----------------------

    public GamePanel(int screenWidth, int screenHeight,
                     int cellSize, int startingCells,
                     boolean drawGrid) {

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.cellSize = cellSize;
        this.drawGrid = drawGrid;

        this.game = new Game(startingCells,
                screenWidth / 2,
                screenHeight / 2,
                cellSize);

        setPreferredSize(new Dimension(screenWidth, screenHeight));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusable(true);

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
    }
    // -----------------------
    // Update
    // -----------------------

    public void updateSelection(String selected) {
        if ("Brush".equals(selected)) {
            currentTool = Tool.BRUSH;
        } else if ("Area".equals(selected)) {
            currentTool = Tool.AREA;
        }
    }

    public void updateDensity(int density) {
        this.density = density / 100.0;
    }

    public void updatePaintSize(int size) {
        this.paintSize = Math.max(0, size);
    }

    public void updateTimer(int time) {

        if (timer == null) return;

        if (time == 0) {
            timer.stop();
            return;
        }

        if (!timer.isRunning()) {
            timer.start();
        }

        timer.setDelay(1000 / time);
    }

    public void updateCellSize(int size) {
        if (size == 0) return;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        double worldX = (centerX - camera.x) / (double) cellSize;
        double worldY = (centerY - camera.y) / (double) cellSize;
        this.cellSize = size;
        camera.x = (int) (centerX - worldX * cellSize);
        camera.y = (int) (centerY - worldY * cellSize);
    }
    // -----------------------
    // Game Loop
    // -----------------------

    public void startGameThread() {
        Thread gameThread = new Thread(this);
        gameThread.start();
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

        drawCells(g);

        if (drawGrid) drawGrid(g);

        drawSelection(g);
    }

    private void drawCells(Graphics g) {
        g.setColor(Color.GRAY);
        for (Game.Cell cell : game.aliveCells) {
            g.fillRect(
                    worldToScreenX(cell.x()),
                    worldToScreenY(cell.y()),
                    cellSize,
                    cellSize
            );
        }
    }

    private void drawGrid(Graphics g) {
        g.setColor(cellSize < 3 ? GRID_ZOOMED_OUT : GRID_ZOOMED_IN);

        int startX = mod(camera.x, cellSize);
        int startY = mod(camera.y, cellSize);

        for (int x = startX; x < screenWidth; x += cellSize)
            g.drawLine(x, 0, x, screenHeight);

        for (int y = startY; y < screenHeight; y += cellSize)
            g.drawLine(0, y, screenWidth, y);
    }

    private void drawSelection(Graphics g) {
        if (!drawingArea || selectionStart == null || selectionEnd == null)
            return;

        int minX = Math.min(selectionStart.x, selectionEnd.x);
        int minY = Math.min(selectionStart.y, selectionEnd.y);
        int maxX = Math.max(selectionStart.x, selectionEnd.x);
        int maxY = Math.max(selectionStart.y, selectionEnd.y);

        int x = worldToScreenX(minX);
        int y = worldToScreenY(minY);
        int w = (maxX - minX + 1) * cellSize;
        int h = (maxY - minY + 1) * cellSize;

        g.setColor(Color.WHITE);
        g.drawRect(x, y, w, h);
    }

    // -----------------------
    // Mouse Input
    // -----------------------

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        //e.getScrollAmount();
        updateCellSize(cellSize - e.getWheelRotation());
        repaint();

    }

    @Override
    public void mousePressed(MouseEvent e) {

        if (SwingUtilities.isLeftMouseButton(e)) {

            if (currentTool == Tool.BRUSH) {
                paintingBrush = true;
                paintBrush(e);
            } else {
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
        }
        else if (paintingBrush) {
            paintBrush(e);
        }
        else if (drawingArea) {
            selectionEnd = screenToWorld(e.getPoint());
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        if (drawingArea && selectionStart != null && selectionEnd != null) {
            fillSelection();
        }

        draggingCamera = false;
        paintingBrush = false;
        drawingArea = false;

        selectionStart = null;
        selectionEnd = null;
        paintedCells.clear();

        repaint();
    }

    // -----------------------
    // Actions
    // -----------------------

    private void paintBrush(MouseEvent e) {
        Point world = screenToWorld(e.getPoint());

        for (int dx = -paintSize; dx <= paintSize; dx++) {
            for (int dy = -paintSize; dy <= paintSize; dy++) {

                Game.Cell cell = new Game.Cell(world.x + dx, world.y + dy);

                if (paintedCells.add(cell) && random.nextDouble() <= density) {
                    game.spawnCell(cell);
                }
            }
        }
        repaint();
    }

    private void fillSelection() {
        int minX = Math.min(selectionStart.x, selectionEnd.x);
        int minY = Math.min(selectionStart.y, selectionEnd.y);
        int maxX = Math.max(selectionStart.x, selectionEnd.x);
        int maxY = Math.max(selectionStart.y, selectionEnd.y);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (random.nextDouble() <= density)
                    game.spawnCell(new Game.Cell(x, y));
            }
        }
    }

    private void moveCamera(MouseEvent e) {
        Point current = e.getPoint();
        camera.x += current.x - lastMouse.x;
        camera.y += current.y - lastMouse.y;
        lastMouse = current;
        repaint();
    }

    // -----------------------
    // Helper
    // -----------------------

    private Point screenToWorld(Point p) {
        return new Point(
                (p.x - camera.x) / cellSize,
                (p.y - camera.y) / cellSize
        );
    }

    private int worldToScreenX(int worldX) {
        return worldX * cellSize + camera.x;
    }

    private int worldToScreenY(int worldY) {
        return worldY * cellSize + camera.y;
    }

    private int mod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    // -----------------------
    // Unused
    // -----------------------

    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {}

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void mouseMoved(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}