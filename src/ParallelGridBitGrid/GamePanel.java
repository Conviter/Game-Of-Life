package ParallelGridBitGrid;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.Random;

public class GamePanel extends JPanel implements Runnable,
        KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    // -----------------------
    // Config
    // -----------------------

    private final int screenWidth;
    private final int screenHeight;

    private double cellSize; // 1..100
    private int zoomLevel = 2;
    private boolean drawGrid;

    private static final Color GRID_ZOOMED_IN  = new Color(150, 150, 150, 40);
    private static final Color GRID_ZOOMED_OUT = new Color(150, 150, 150, 20);

    // -----------------------
    // Game State
    // -----------------------

    private final Game game;
    private Timer timer;
    private int updateTime;
    private int drawingTime;
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
        this.cellSize = Math.max(1, Math.min(100, cellSize));;
        this.drawGrid = drawGrid;

        this.game = new Game(startingCells,
                screenWidth / 2,
                screenHeight / 2,
                (int) this.cellSize);

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

    public void updateCellSize(int zoomLevel) {
        double newSize = 1;
        if (zoomLevel >= 0){
            newSize = zoomLevel + 1;
        } else {
            newSize = 1.0 / Math.abs(zoomLevel);
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // world coordinate currently at screen center
        double worldX = (centerX - camera.x) / cellSize;
        double worldY = (centerY - camera.y) / cellSize;

        this.cellSize = newSize;
        System.out.println(this.cellSize);

        // adjust camera so center stays locked
        camera.x = (int) Math.round(centerX - worldX * cellSize);
        camera.y = (int) Math.round(centerY - worldY * cellSize);
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
            long pre = System.currentTimeMillis();
            game.applyRules();
            long post = System.currentTimeMillis();
            updateTime = (int) (post - pre);


            repaint();

        });
        timer.start();
    }

    // -----------------------
    // Rendering
    // -----------------------

    @Override
    protected void paintComponent(Graphics g) {
        long pre = System.currentTimeMillis();
        super.paintComponent(g);

        if (cellSize >= 1.0){
            drawCellsAccurate(g);
        } else {
            drawCellsSampled(g);
        }


        if (drawGrid) drawGrid(g);

        drawSelection(g);
        long post = System.currentTimeMillis();
        drawingTime = (int) (post - pre);
        drawData(g);
    }

    private void drawCellsAccurate(Graphics g) {
        Arrays.fill(pixels, 0);

        final int WHITE = 0xFFFFFFFF;

        // cache frequently used values
        final double cs = cellSize;
        final int camX = camera.x;
        final int camY = camera.y;

        // compute visible world bounds
        int worldMinX = (int) Math.floor((-camX) / cs);
        int worldMinY = (int) Math.floor((-camY) / cs);
        int worldMaxX = (int) Math.ceil((screenWidth - camX) / cs);
        int worldMaxY = (int) Math.ceil((screenHeight - camY) / cs);

        // convert visible world bounds to visible chunk bounds
        int minGridX = Math.floorDiv(worldMinX, 512);
        int minGridY = Math.floorDiv(worldMinY, 512);
        int maxGridX = Math.floorDiv(worldMaxX, 512);
        int maxGridY = Math.floorDiv(worldMaxY, 512);

        // loop only through visible chunks
        for (int gridY = minGridY; gridY <= maxGridY; gridY++) {
            for (int gridX = minGridX; gridX <= maxGridX; gridX++) {

                long key = Game.cordsToLong(gridX, gridY);
                long[] gridBits = game.cells.get(key);
                if (gridBits == null) continue;

                // iterate through each 64-bit word
                for (int wordIndex = 0; wordIndex < gridBits.length; wordIndex++) {

                    long bits = gridBits[wordIndex];
                    if (bits == 0) continue;

                    int localY = wordIndex >> 3;        // / 8
                    int longXIndex = wordIndex & 7;     // % 8

                    while (bits != 0) {
                        int bitIndex = Long.numberOfTrailingZeros(bits);
                        int localX = (longXIndex << 6) + bitIndex;

                        int worldX = (gridX << 9) + localX;  // gridX * 512
                        int worldY = (gridY << 9) + localY;  // gridY * 512

                        int screenX = (int) (worldX * cs + camX);
                        int screenY = (int) (worldY * cs + camY);

                        int size = (int) cs;

                        // fast reject offscreen
                        if (screenX >= screenWidth || screenY >= screenHeight ||
                                screenX + size <= 0 || screenY + size <= 0) {
                            bits &= bits - 1;
                            continue;
                        }

                        // clamp drawing bounds
                        int startX = Math.max(0, screenX);
                        int endX = Math.min(screenWidth, screenX + size);

                        int startY = Math.max(0, screenY);
                        int endY = Math.min(screenHeight, screenY + size);

                        // draw filled square using scanline fills
                        for (int py = startY; py < endY; py++) {
                            int rowOffset = py * screenWidth;
                            Arrays.fill(pixels, rowOffset + startX, rowOffset + endX, WHITE);
                        }

                        bits &= bits - 1;
                    }
                }
            }
        }

        g.drawImage(image, 0, 0, null);
    }

    private void drawCellsSampled(Graphics g) {
        Arrays.fill(pixels, 0);

        int cellsPerPixel = Math.abs(zoomLevel) + 1;

        // Precompute increments to avoid division inside loops
        double invCellSize = 1.0 / cellSize;

        for (int screenY = 0; screenY < screenHeight; screenY++) {

            // worldY for this screen row
            int worldY = (int) Math.floor((screenY - camera.y) * invCellSize);

            for (int screenX = 0; screenX < screenWidth; screenX++) {

                // worldX for this screen column
                int worldX = (int) Math.floor((screenX - camera.x) * invCellSize);

                // chunk coords for the top-left sampled cell
                int gridX = Math.floorDiv(worldX, 512);
                int gridY = Math.floorDiv(worldY, 512);

                long gridCord = Game.cordsToLong(gridX, gridY);
                long[] grid = game.cells.get(gridCord);

                // if the chunk is empty, the pixel stays black
                if (grid == null) continue;

                int localX = Math.floorMod(worldX, 512);
                int localY = Math.floorMod(worldY, 512);

                int aliveCount = 0;

                // sampled block stays fully inside this chunk
                if (localX + cellsPerPixel < 512 && localY + cellsPerPixel < 512) {

                    for (int offsetX = 0; offsetX < cellsPerPixel; offsetX++) {
                        int localOffsetX = localX + offsetX;

                        for (int offsetY = 0; offsetY < cellsPerPixel; offsetY++) {
                            int localOffsetY = localY + offsetY;

                            if (Game.getState(localOffsetX, localOffsetY, grid)) {
                                aliveCount++;
                            }
                        }
                    }

                } else {
                    // sampled block crosses chunk boundary

                    long currentGridCord = gridCord;
                    long[] currentGrid = grid;

                    for (int offsetX = 0; offsetX < cellsPerPixel; offsetX++) {
                        for (int offsetY = 0; offsetY < cellsPerPixel; offsetY++) {

                            int sampledX = worldX + offsetX;
                            int sampledY = worldY + offsetY;

                            int gridOffsetX = Math.floorDiv(sampledX, 512);
                            int gridOffsetY = Math.floorDiv(sampledY, 512);

                            long thisGridCord = Game.cordsToLong(gridOffsetX, gridOffsetY);

                            if (thisGridCord != currentGridCord) {
                                currentGridCord = thisGridCord;
                                currentGrid = game.cells.get(currentGridCord);
                            }

                            if (currentGrid == null) continue;

                            int localOffsetX = Math.floorMod(sampledX, 512);
                            int localOffsetY = Math.floorMod(sampledY, 512);

                            if (Game.getState(localOffsetX, localOffsetY, currentGrid)) {
                                aliveCount++;
                            }
                        }
                    }
                }

                double aliveRatio = (aliveCount * 1.6) / (cellsPerPixel * cellsPerPixel);
                int grayValue = (int) (aliveRatio * 255.0);
                grayValue = Math.max(0, Math.min(255, grayValue));

                pixels[screenX + screenY * screenWidth] =
                        (255 << 24) | (grayValue << 16) | (grayValue << 8) | grayValue;
            }
        }

        g.drawImage(image, 0, 0, null);
    }


    private void drawData(Graphics g){
        String aliveCells = String.format("%,d", game.totalAlive.get());

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 350 + aliveCells.length() * 7, 30);

        g.setColor(Color.white);
        g.drawString("Alive Cells: " + aliveCells, 10, 15);
        g.drawString("Update Time: " + updateTime + "ms", 80 + aliveCells.length() * 7, 15);
        g.drawString("Drawing Time: " + drawingTime + "ms", 200 + aliveCells.length() * 7, 15);
        g.drawString("Cell Size: " + cellSize, 10, 28);
    }

    private void drawGrid(Graphics g) {

        if (cellSize < 3) return;

        g.setColor(cellSize < 6 ? GRID_ZOOMED_OUT : GRID_ZOOMED_IN);

        int startX = mod(camera.x, (int) cellSize);
        int startY = mod(camera.y, (int) cellSize);

        for (int x = startX; x < screenWidth; x += (int) cellSize)
            g.drawLine(x, 0, x, screenHeight);

        for (int y = startY; y < screenHeight; y += (int) cellSize)
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

        int w = (int) ((maxX - minX + 1) * cellSize);
        int h = (int) ((maxY - minY + 1) * cellSize);

        g.setColor(Color.WHITE);
        g.drawRect(x, y, w, h);
    }

    // -----------------------
    // Mouse Input
    // -----------------------

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {

        int rotation = e.getWheelRotation();

        // wheel up = zoom in = bigger cellSize
        int change = (rotation < 0) ? 1 : -1;


        zoomLevel += change;
        updateCellSize(zoomLevel);
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
        int worldX = (int) Math.floor((p.x - camera.x) / (double) cellSize);
        int worldY = (int) Math.floor((p.y - camera.y) / (double) cellSize);
        return new Point(worldX, worldY);
    }

    private int worldToScreenX(int worldX) {
        return (int) (worldX * cellSize + camera.x);
    }

    private int worldToScreenY(int worldY) {
        return (int) (worldY * cellSize + camera.y);
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