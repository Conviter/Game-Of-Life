package ParallelGridBitGrid;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;

public class GamePanel extends JPanel implements Runnable,
        KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    // -----------------------
    // Config
    // -----------------------

    private final int screenWidth;
    private final int screenHeight;

    private final int cellSize; // logical cell size (usually 1)

    private double zoom; // can go below 1
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

    private enum Tool { BRUSH, AREA, ERASER, CLEAR }
    private Tool currentTool = Tool.BRUSH;

    private boolean draggingCamera = false;
    private boolean drawingArea = false;
    private boolean paintingBrush = false;

    private Point lastMouse;
    private Point selectionStart;
    private Point selectionEnd;

    private int paintSize = 2;
    private double density = 1.0;

    private final LongOpenHashSet paintedCells = new LongOpenHashSet(100000);
    private final Random random = new Random();

    private final BufferedImage image;
    private final int[] pixels;

    private boolean erasing = false;

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

        this.zoom = cellSize;

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

        image = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    // -----------------------
    // Update
    // -----------------------

    public void toggleGrid(){
        drawGrid = !drawGrid;
    }

    public void clear(){
        game.clear();
    }

    public void updateSelection(String selected) {
        switch(selected){
            case "Brush":
                currentTool = Tool.BRUSH;
                break;
            case "Area":
                currentTool = Tool.AREA;
                break;
            case "Eraser":
                currentTool = Tool.ERASER;
                break;
            case "Clear":
                currentTool = Tool.CLEAR;
                break;
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

    public void updateZoom(double newZoom) {

        if (newZoom <= 0.001) newZoom = 0.001;
        if (newZoom >= 200) newZoom = 200;

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        double worldX = (centerX - camera.x) / zoom;
        double worldY = (centerY - camera.y) / zoom;

        zoom = newZoom;

        camera.x = (int) (centerX - worldX * zoom);
        camera.y = (int) (centerY - worldY * zoom);
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

        drawCellsAccurate();

        g.drawImage(image, 0, 0, null);

        if (drawGrid) drawGrid(g);

        drawSelection(g);
        drawData(g);
    }


    private void drawCellsAccurate() {

        Arrays.fill(pixels, 0);

        // For normal zoom, use the fast renderer
        if (zoom >= 1.0) {
            drawCellsFast();
            return;
        }

        double invZoom = 1.0 / zoom;

        for (int screenY = 0; screenY < screenHeight; screenY++) {

            int worldYStart = (int) Math.floor((screenY - camera.y) * invZoom);
            int worldYEnd   = (int) Math.floor((screenY + 1 - camera.y) * invZoom);

            for (int screenX = 0; screenX < screenWidth; screenX++) {

                int worldXStart = (int) Math.floor((screenX - camera.x) * invZoom);
                int worldXEnd   = (int) Math.floor((screenX + 1 - camera.x) * invZoom);

                int totalWorldCells = (worldXEnd - worldXStart + 1) * (worldYEnd - worldYStart + 1);
                int aliveWorldCells = 0;

                int gridXStart = Math.floorDiv(worldXStart, 512);
                int gridXEnd   = Math.floorDiv(worldXEnd, 512);

                int gridYStart = Math.floorDiv(worldYStart, 512);
                int gridYEnd   = Math.floorDiv(worldYEnd, 512);

                for (int gridX = gridXStart; gridX <= gridXEnd; gridX++) {
                    for (int gridY = gridYStart; gridY <= gridYEnd; gridY++) {

                        long[] grid = game.cells.get(Game.cordsToLong(gridX, gridY));
                        if (grid == null) continue;

                        int localXStart = (gridX == gridXStart) ? Math.floorMod(worldXStart, 512) : 0;
                        int localXEnd   = (gridX == gridXEnd)   ? Math.floorMod(worldXEnd, 512)   : 511;

                        int localYStart = (gridY == gridYStart) ? Math.floorMod(worldYStart, 512) : 0;
                        int localYEnd   = (gridY == gridYEnd)   ? Math.floorMod(worldYEnd, 512)   : 511;

                        for (int localY = localYStart; localY <= localYEnd; localY++) {

                            int startIndex = localY * 512 + localXStart;
                            int endIndex   = localY * 512 + localXEnd;

                            int startWord = startIndex >> 6;  // /64
                            int endWord   = endIndex >> 6;

                            int startBit = startIndex & 63;
                            int endBit   = endIndex & 63;

                            if (startWord == endWord) {
                                long mask = (-1L >>> (63 - (endBit - startBit))) << startBit;
                                aliveWorldCells += Long.bitCount(grid[startWord] & mask);
                            } else {
                                // first word
                                long maskStart = -1L << startBit;
                                aliveWorldCells += Long.bitCount(grid[startWord] & maskStart);

                                // middle full words
                                for (int wordIndex = startWord + 1; wordIndex < endWord; wordIndex++) {
                                    aliveWorldCells += Long.bitCount(grid[wordIndex]);
                                }

                                // last word
                                long maskEnd = -1L >>> (63 - endBit);
                                aliveWorldCells += Long.bitCount(grid[endWord] & maskEnd);
                            }
                        }
                    }
                }

                // calculate percentage of alive cells in this pixel
                double aliveRatio = (double) (aliveWorldCells * 1.3) / totalWorldCells;

                // convert to grayscale intensity (0..255)
                int grayValue = (int) (aliveRatio * 255.0);
                grayValue = Math.max(0, Math.min(255, grayValue));

                // ARGB grayscale pixel
                pixels[screenX + screenY * screenWidth] =
                        (255 << 24) | (grayValue << 16) | (grayValue << 8) | grayValue;
            }
        }
    }


    private void drawCellsFast() {

        for (var set : game.cells.long2ObjectEntrySet()) {

            int gridX = Game.longToIntX(set.getLongKey());
            int gridY = Game.longToIntY(set.getLongKey());

            long[] grid = set.getValue();

            for (int i = 0; i < grid.length; i++) {

                long cell = grid[i];

                int row = i >> 3;
                int longInRow = i & 7;

                while (cell != 0) {

                    int bit = Long.numberOfTrailingZeros(cell);

                    int localX = (longInRow << 6) + bit;
                    int localY = row;

                    int screenX = worldToScreenX(gridX * 512 + localX);
                    int screenY = worldToScreenY(gridY * 512 + localY);

                    drawCellZoomedIn(screenX, screenY);

                    cell &= cell - 1;
                }
            }
        }
    }

    private void drawCellZoomedIn(int x, int y) {

        int size = (int) Math.round(zoom);
        if (size < 1) size = 1;

        for (int dy = 0; dy < size; dy++) {
            int py = y + dy;
            if (py < 0 || py >= screenHeight) continue;

            int offset = py * screenWidth;

            for (int dx = 0; dx < size; dx++) {
                int px = x + dx;
                if (px < 0 || px >= screenWidth) continue;

                pixels[offset + px] = 0xFFFFFFFF;
            }
        }
    }

    private void drawData(Graphics g){
        String aliveCells = String.format("%,d", game.totalAlive.get());
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 200 + aliveCells.length() * 7, 30);
        g.setColor(Color.white);

        g.drawString("Alive Cells: " + aliveCells, 10, 15);
        g.drawString("Update Time: " + game.updateTime + "ms", 80 + aliveCells.length() * 7, 15);
        g.drawString("Zoom: " + String.format("%.4f", zoom), 10, 28);
    }

    private void drawGrid(Graphics g) {

        if (zoom < 3) return;

        int gridSize = (int) zoom;

        g.setColor(gridSize < 3 ? GRID_ZOOMED_OUT : GRID_ZOOMED_IN);

        int startX = mod(camera.x, gridSize);
        int startY = mod(camera.y, gridSize);

        for (int x = startX; x < screenWidth; x += gridSize)
            g.drawLine(x, 0, x, screenHeight);

        for (int y = startY; y < screenHeight; y += gridSize)
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

        int w = (int) ((maxX - minX + 1) * zoom);
        int h = (int) ((maxY - minY + 1) * zoom);

        g.setColor(Color.WHITE);
        g.drawRect(x, y, w, h);
    }

    // -----------------------
    // Mouse Input
    // -----------------------

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double factor = (e.getWheelRotation() < 0) ? 1.1 : 0.9;
        updateZoom(zoom * factor);
        repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {

            if (currentTool == Tool.BRUSH) {
                paintingBrush = true;
                paintBrush(e);
            } else if(currentTool == Tool.AREA) {
                drawingArea = true;
                selectionStart = screenToWorld(e.getPoint());
                selectionEnd = selectionStart;
            } else if(currentTool == Tool.ERASER){
                erasing = true;
                eraseBrush(e);
            } else if (currentTool == Tool.CLEAR){
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
            repaint();
        } else if(erasing){
            eraseBrush(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        if (drawingArea && selectionStart != null && selectionEnd != null) {
            if (currentTool == Tool.CLEAR){
                clearSelection();
            } else {
                fillSelection();
            }
        }

        draggingCamera = false;
        paintingBrush = false;
        drawingArea = false;

        selectionStart = null;
        selectionEnd = null;
        paintedCells.clear();

        erasing = false;

        repaint();
    }

    // -----------------------
    // Actions
    // -----------------------

    private void clearSelection(){
        int minX = Math.min(selectionStart.x, selectionEnd.x);
        int minY = Math.min(selectionStart.y, selectionEnd.y);
        int maxX = Math.max(selectionStart.x, selectionEnd.x);
        int maxY = Math.max(selectionStart.y, selectionEnd.y);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                game.clearCell(Game.cordsToLong(x, y));
            }
        }
    }

    private void eraseBrush(MouseEvent e){
        Point world = screenToWorld(e.getPoint());
        int worldX = world.x;
        int worldY = world.y;

        for (int dx = -paintSize; dx <= paintSize; dx++) {
            for (int dy = -paintSize; dy <= paintSize; dy++) {
                int brushX = worldX + dx;
                int brushY = worldY + dy;

                long cell = Game.cordsToLong(brushX, brushY);
                game.clearCell(cell);
            }
        }
        repaint();
    }

    private void paintBrush(MouseEvent e) {
        Point world = screenToWorld(e.getPoint());
        int worldX = world.x;
        int worldY = world.y;

        for (int dx = -paintSize; dx <= paintSize; dx++) {
            for (int dy = -paintSize; dy <= paintSize; dy++) {
                int brushX = worldX + dx;
                int brushY = worldY + dy;

                long cell = Game.cordsToLong(brushX, brushY);
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
                    game.spawnCell(Game.cordsToLong(x, y));
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
        int worldX = (int) Math.floor((p.x - camera.x) / zoom);
        int worldY = (int) Math.floor((p.y - camera.y) / zoom);
        return new Point(worldX, worldY);
    }

    private int worldToScreenX(int worldX) {
        return (int) Math.floor(worldX * zoom + camera.x);
    }

    private int worldToScreenY(int worldY) {
        return (int) Math.floor(worldY * zoom + camera.y);
    }

    private int mod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
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