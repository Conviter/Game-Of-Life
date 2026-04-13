package ParallelGridBitGrid;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public class Renderer {

    private final int screenWidth;
    private final int screenHeight;

    private boolean drawGrid;

    private static final Color GRID_ZOOMED_IN = new Color(150, 150, 150, 40);
    private static final Color GRID_ZOOMED_OUT = new Color(150, 150, 150, 20);

    private final BufferedImage image;
    private final int[] pixels;

    private int drawingTime;

    public Renderer(int screenWidth, int screenHeight, boolean drawGrid) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.drawGrid = drawGrid;

        image = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    public void toggleGrid() {
        drawGrid = !drawGrid;
    }

    public int getDrawingTime() {
        return drawingTime;
    }

    public void render(Graphics g, Game game, Point camera, double cellSize, int zoomLevel,
                       boolean drawingArea, Point selectionStart, Point selectionEnd) {

        long pre = System.currentTimeMillis();

        if (cellSize >= 1.0) {
            drawCellsAccurate(g, game, camera, cellSize);
        } else {
            drawCellsSampled(g, game, camera, cellSize, zoomLevel);
        }

        if (drawGrid) drawGrid(g, camera, cellSize);

        drawSelection(g, cellSize, camera, drawingArea, selectionStart, selectionEnd);

        drawingTime = (int) (System.currentTimeMillis() - pre);

        drawData(g, game, cellSize);
    }

    private void drawCellsAccurate(Graphics g, Game game, Point camera, double cellSize) {

        Arrays.fill(pixels, 0);
        final int WHITE = 0xFFFFFFFF;

        final double cs = cellSize;
        final int camX = camera.x;
        final int camY = camera.y;

        int worldMinX = (int) Math.floor((-camX) / cs);
        int worldMinY = (int) Math.floor((-camY) / cs);
        int worldMaxX = (int) Math.ceil((screenWidth - camX) / cs);
        int worldMaxY = (int) Math.ceil((screenHeight - camY) / cs);

        int minGridX = Math.floorDiv(worldMinX, Game.GRID_SIZE);
        int minGridY = Math.floorDiv(worldMinY, Game.GRID_SIZE);
        int maxGridX = Math.floorDiv(worldMaxX, Game.GRID_SIZE);
        int maxGridY = Math.floorDiv(worldMaxY, Game.GRID_SIZE);

        for (int gridY = minGridY; gridY <= maxGridY; gridY++) {
            for (int gridX = minGridX; gridX <= maxGridX; gridX++) {

                long key = Utility.cordsToLong(gridX, gridY);
                long[] gridBits = game.cells.get(key);

                if (gridBits == null) continue;

                for (int wordIndex = 0; wordIndex < gridBits.length; wordIndex++) {

                    long bits = gridBits[wordIndex];
                    if (bits == 0) continue;

                    int localY = wordIndex >> 3;
                    int longXIndex = wordIndex & 7;

                    while (bits != 0) {
                        int bitIndex = Long.numberOfTrailingZeros(bits);
                        int localX = (longXIndex << 6) + bitIndex;

                        int worldX = (gridX << 9) + localX;
                        int worldY = (gridY << 9) + localY;

                        int screenX = (int) (worldX * cs + camX);
                        int screenY = (int) (worldY * cs + camY);

                        int size = (int) cs;

                        if (screenX >= screenWidth || screenY >= screenHeight ||
                                screenX + size <= 0 || screenY + size <= 0) {
                            bits &= bits - 1;
                            continue;
                        }

                        int startX = Math.max(0, screenX);
                        int endX = Math.min(screenWidth, screenX + size);

                        int startY = Math.max(0, screenY);
                        int endY = Math.min(screenHeight, screenY + size);

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

    private void drawCellsSampled(Graphics g, Game game, Point camera, double cellSize, int zoomLevel) {

        Arrays.fill(pixels, 0);

        int cellsPerPixel = Math.abs(zoomLevel) + 1;
        double invCellSize = 1.0 / cellSize;

        for (int screenY = 0; screenY < screenHeight; screenY++) {

            int worldY = (int) Math.floor((screenY - camera.y) * invCellSize);

            for (int screenX = 0; screenX < screenWidth; screenX++) {

                int worldX = (int) Math.floor((screenX - camera.x) * invCellSize);

                int gridX = Math.floorDiv(worldX, Game.GRID_SIZE);
                int gridY = Math.floorDiv(worldY, Game.GRID_SIZE);

                long gridCord = Utility.cordsToLong(gridX, gridY);
                long[] grid = game.cells.get(gridCord);

                if (grid == null) continue;

                int localX = Math.floorMod(worldX, Game.GRID_SIZE);
                int localY = Math.floorMod(worldY, Game.GRID_SIZE);

                int aliveCount = 0;

                if (localX + cellsPerPixel < Game.GRID_SIZE && localY + cellsPerPixel < Game.GRID_SIZE) {

                    for (int offsetX = 0; offsetX < cellsPerPixel; offsetX++) {
                        for (int offsetY = 0; offsetY < cellsPerPixel; offsetY++) {
                            if (Game.getState(localX + offsetX, localY + offsetY, grid)) {
                                aliveCount++;
                            }
                        }
                    }

                } else {

                    long currentGridCord = gridCord;
                    long[] currentGrid = grid;

                    for (int offsetX = 0; offsetX < cellsPerPixel; offsetX++) {
                        for (int offsetY = 0; offsetY < cellsPerPixel; offsetY++) {

                            int sampledX = worldX + offsetX;
                            int sampledY = worldY + offsetY;

                            int gridOffsetX = Math.floorDiv(sampledX, Game.GRID_SIZE);
                            int gridOffsetY = Math.floorDiv(sampledY, Game.GRID_SIZE);

                            long thisGridCord = Utility.cordsToLong(gridOffsetX, gridOffsetY);

                            if (thisGridCord != currentGridCord) {
                                currentGridCord = thisGridCord;
                                currentGrid = game.cells.get(currentGridCord);
                            }

                            if (currentGrid == null) continue;

                            int lx = Math.floorMod(sampledX, Game.GRID_SIZE);
                            int ly = Math.floorMod(sampledY, Game.GRID_SIZE);

                            if (Game.getState(lx, ly, currentGrid)) aliveCount++;
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

    private void drawGrid(Graphics g, Point camera, double cellSize) {

        if (cellSize < 3) return;

        g.setColor(cellSize < 6 ? GRID_ZOOMED_OUT : GRID_ZOOMED_IN);

        int startX = Utility.mod(camera.x, (int) cellSize);
        int startY = Utility.mod(camera.y, (int) cellSize);

        for (int x = startX; x < screenWidth; x += (int) cellSize)
            g.drawLine(x, 0, x, screenHeight);

        for (int y = startY; y < screenHeight; y += (int) cellSize)
            g.drawLine(0, y, screenWidth, y);
    }

    private void drawSelection(Graphics g, double cellSize, Point camera,
                               boolean drawingArea, Point selectionStart, Point selectionEnd) {

        if (!drawingArea || selectionStart == null || selectionEnd == null) return;

        int minX = Math.min(selectionStart.x, selectionEnd.x);
        int minY = Math.min(selectionStart.y, selectionEnd.y);
        int maxX = Math.max(selectionStart.x, selectionEnd.x);
        int maxY = Math.max(selectionStart.y, selectionEnd.y);

        int x = (int) (minX * cellSize + camera.x);
        int y = (int) (minY * cellSize + camera.y);

        int w = (int) ((maxX - minX + 1) * cellSize);
        int h = (int) ((maxY - minY + 1) * cellSize);

        g.setColor(Color.WHITE);
        g.drawRect(x, y, w, h);
    }

    private void drawData(Graphics g, Game game, double cellSize) {

        String aliveCells = String.format("%,d", game.totalAlive.get());

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 350 + aliveCells.length() * 7, 30);

        g.setColor(Color.WHITE);
        g.drawString("Alive Cells: " + aliveCells, 10, 15);
        g.drawString("Update Time: " + game.updateTime + "ms", 80 + aliveCells.length() * 7, 15);
        g.drawString("Drawing Time: " + drawingTime + "ms", 200 + aliveCells.length() * 7, 15);
        g.drawString("Cell Size: " + cellSize, 10, 28);
    }
}