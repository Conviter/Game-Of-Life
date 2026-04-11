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

//    public void updateZoomLevel(int change){
//        if (zoomLevel + change == 0){
//            zoomLevel *= -1;
//        } else {
//            zoomLevel += change;
//        }
//    }

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

        //Loop through every stored grid chunk
        //Your world is stored in chunks of size 512 × 512 cells.
        //Each entry in game.cells represents one chunk.
        //Key: packed (gridX, gridY) coordinates
        //Value: long[] bitset storing which cells are alive inside that chunk
        for (var gridEntry : game.cells.long2ObjectEntrySet()) {

            //Decode chunk coordinates
            //This extracts the chunk position.
            int gridX = Game.longToIntX(gridEntry.getLongKey());
            int gridY = Game.longToIntY(gridEntry.getLongKey());


            //Get the chunk’s bit storage
            //This is the actual 512×512 grid, stored compactly.
            //A 512×512 grid contains:
            //262,144 cells total
            //each long stores 64 cells (64 bits)
            //So the array length is:
            //262,144 / 64 = 4096 longs
            long[] gridBits = gridEntry.getValue();

            //Loop through each 64-bit word
            //Each wordIndex represents a block of 64 cells in a row.
            for (int wordIndex = 0; wordIndex < gridBits.length; wordIndex++) {

                //Read the 64-bit word
                //This retrieves the 64-bit chunk of cells.
                //If it equals 0, that means all 64 cells are dead.
                //So we skip it for performance.
                long longBits = gridBits[wordIndex];
                if (longBits == 0) continue;

                // Convert wordIndex into (localY, longXIndex)
                //This is because the 512-wide row is stored as:
                //512 bits per row
                //512 / 64 = 8 longs per row
                //So:
                //wordIndex / 8 tells us which row (localY)
                //wordIndex % 8 tells us which long in that row (longXIndex)
                int localY = wordIndex >> 3;        // wordIndex / 8
                int longXIndex = wordIndex & 7;     // wordIndex % 8

                //Iterate through all alive bits in that long
                while (longBits != 0) {

                    //Find the next alive cell in the long
                    //This finds the index of the lowest set bit.
                    int bitIndex = Long.numberOfTrailingZeros(longBits);

                    // Convert bitIndex into localX
                    //longXIndex << 6 is longXIndex * 64
                    //then add bitIndex
                    int localX = (longXIndex << 6) + bitIndex;

                    // Convert local coordinates into world coordinates
                    int worldX = gridX * 512 + localX;
                    int worldY = gridY * 512 + localY;

                    // Convert world coordinates into screen coordinates
                    //This uses our camera offset and cellSize scaling.
                    //So now we know where on the screen that world cell should appear.
                    int screenX = worldToScreenX(worldX);
                    int screenY = worldToScreenY(worldY);

                    // reject fully offscreen cells
                    //But we still need to remove that bit from longBits (done below).
                    if (screenX >= screenWidth || screenY >= screenHeight ||
                            screenX + cellSize <= 0 || screenY + cellSize <= 0) {
                        longBits &= longBits - 1;
                        continue;
                    }

                    // Draw the cell as a block of pixels
                    //This loops over the pixel rows inside the cell square.
                    for (int pixelOffsetY = 0; pixelOffsetY < cellSize; pixelOffsetY++) {

                        //Compute actual screen Y coordinate
                        //This prevents writing outside the pixel array.
                        int pixelY = screenY + pixelOffsetY;
                        if (pixelY < 0 || pixelY >= screenHeight) continue;

                        //Convert pixelY into an index offset
                        //This precomputes y * width so we don’t recompute it for every pixel.
                        int rowOffset = pixelY * screenWidth;

                        //Loop over pixel columns inside the cell
                        //This loops across the width of the cell square.
                        for (int pixelOffsetX = 0; pixelOffsetX < cellSize; pixelOffsetX++) {

                            //Compute actual screen X coordinate
                            //Again bounds checking.
                            int pixelX = screenX + pixelOffsetX;
                            if (pixelX < 0 || pixelX >= screenWidth) continue;

                            //Write the pixel in solid white
                            pixels[rowOffset + pixelX] = 0xFFFFFFFF;
                        }
                    }

                    // Remove the bit we just processed
                    //This is a classic bit trick:
                    //It clears the lowest set bit in longBits
                    //So the loop progresses to the next alive cell in the same 64-bit word.
                    longBits &= longBits - 1;
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

                // FAST SKIP: if the chunk is empty, the pixel stays black
                if (grid == null) continue;

                int localX = Math.floorMod(worldX, 512);
                int localY = Math.floorMod(worldY, 512);

                int aliveCount = 0;

                // FAST PATH: sampled block stays fully inside this chunk
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
                    // SLOW PATH: sampled block crosses chunk boundary (rare case)

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

                double aliveRatio = (aliveCount * 1.3) / (cellsPerPixel * cellsPerPixel);
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