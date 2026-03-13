package PrallelGridNeighbourCountInverse;

import it.unimi.dsi.fastutil.longs.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.RecursiveAction;


public class Game {

    // -------------------------------------------------
    // Configuration
    // -------------------------------------------------

    int gameWidth;
    int gameHeight;
    int cellSize;
    int startingCellSize;
    int updateTime;

    // -------------------------------------------------
    // Cell Representation
    // -------------------------------------------------

    public static long cordsToLong(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    public static int longToIntX(long xy) {
        return (int) (xy >> 32);
    }

    public static int longToIntY(long xy) {
        return (int) xy;
    }


    // -------------------------------------------------
    // Neighbour Offsets
    // -------------------------------------------------

    private static final int[] neighbourOffsetX = {-1, -1, -1, 1, 1, 1, 0, 0};
    private static final int[] neighbourOffsetY = {0, 1, -1, 0, 1, -1, 1, -1};

    // -------------------------------------------------
    // State
    // -------------------------------------------------


    Long2ObjectOpenHashMap<Long2IntOpenHashMap> cells = new Long2ObjectOpenHashMap<>();
    int totalAlive;

    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;
        this.startingCellSize = startingCells;

        int thread_count = Runtime.getRuntime().availableProcessors();

        System.out.println(thread_count);

        Random random = new Random();

        for (int i = 0; i < startingCells; i++) {
            int x = random.nextInt(this.gameWidth);
            int y = random.nextInt(this.gameHeight);
            addCell(cordsToLong(x, y));

        }
    }

    private void addCell(long cell) {
        totalAlive++;
        int xGrid = longToIntX(cell) >> 9;
        int yGrid = longToIntY(cell) >> 9;
        if (cells.containsKey(cordsToLong(xGrid, yGrid))) {
            cells.get(cordsToLong(xGrid, yGrid)).put(cell, 10);
        } else {
            cells.put(cordsToLong(xGrid, yGrid), new Long2IntOpenHashMap(startingCellSize));
            cells.get(cordsToLong(xGrid, yGrid)).put(cell, 10);
        }
    }


    private void addCellToMap(Long2ObjectOpenHashMap<Long2IntOpenHashMap> map, long cell) {

        int gx = longToIntX(cell) >> 9;
        int gy = longToIntY(cell) >> 9;

        long gridKey = cordsToLong(gx, gy);

        Long2IntOpenHashMap grid = map.get(gridKey);

        if (grid == null) {
            grid = new Long2IntOpenHashMap();
            map.put(gridKey, grid);
        }

        grid.put(cell, 10);
    }


    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    public void spawnCell(long cell) {
        addCell(cell);
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------

    public boolean getNeighbour(int neighbourX, int neighbourY) {
        int gridCordX = neighbourX >> 9;
        int gridCordY = neighbourY >> 9;

        Long2IntOpenHashMap grid = cells.get(((long) gridCordX << 32) | (gridCordY & 0xffffffffL));

        return grid != null && grid.get(((long) neighbourX << 32) | (neighbourY & 0xffffffffL)) == 10;

    }


    public class ApplyRulesTask extends RecursiveAction {

        Long2IntOpenHashMap cellsThisState;
        Long2IntOpenHashMap cellsNextState = new Long2IntOpenHashMap(startingCellSize);
        LongOpenHashSet deadNeighboursToCheck = new LongOpenHashSet(startingCellSize);
        Long2IntOpenHashMap births = new Long2IntOpenHashMap();
        int gridX;
        int gridY;

        public ApplyRulesTask(Long2IntOpenHashMap cellsThisState, long gridCord) {
            this.cellsThisState = cellsThisState;
            gridX = longToIntX(gridCord);
            gridY = longToIntY(gridCord);
        }

        @Override
        protected void compute() {
            totalAlive = 0;
            for (Long2IntMap.Entry cell : cellsThisState.long2IntEntrySet()) {

                int x = longToIntX(cell.getLongKey());
                int y = longToIntY(cell.getLongKey());

                int neighbourCount = 0;

                for (int j = 0; j < neighbourOffsetX.length; j++) {

                    int neighbourX = x + neighbourOffsetX[j];
                    int neighbourY = y + neighbourOffsetY[j];

                    if (getNeighbour(neighbourX, neighbourY)) {
                        neighbourCount++;
                    } else {
                        deadNeighboursToCheck.add(cordsToLong(neighbourX, neighbourY));
                    }
                }

                // survival rule
                if (neighbourCount == 2 || neighbourCount == 3) {
                    cellsNextState.put(cell.getLongKey(), 10);
                    totalAlive++;
                }
            }

            // check births
            for (long cell : deadNeighboursToCheck) {

                int x = longToIntX(cell);
                int y = longToIntY(cell);

                int neighbourCount = 0;

                for (int j = 0; j < neighbourOffsetX.length; j++) {

                    int neighbourX = x + neighbourOffsetX[j];
                    int neighbourY = y + neighbourOffsetY[j];

                    if (getNeighbour(neighbourX, neighbourY)) {
                        neighbourCount++;
                    }
                }

                if (neighbourCount == 3) {
                    births.put(cell, 10);
                }
            }
        }
    }


    public void applyRules() {
        long pre = System.currentTimeMillis();

        Long2ObjectOpenHashMap<Long2IntOpenHashMap> nextCells =
                new Long2ObjectOpenHashMap<>(cells.size());

        cells.long2ObjectEntrySet().parallelStream().forEach(gridCells -> {
            ApplyRulesTask task = new ApplyRulesTask(gridCells.getValue(), gridCells.getLongKey());
            task.compute();
            nextCells.put(gridCells.getLongKey(), task.cellsNextState);
            nextCells.get(gridCells.getLongKey()).putAll(task.births);
        });

        cells = nextCells;

        long post = System.currentTimeMillis();
        updateTime = (int)(post - pre);
        System.out.println(updateTime);
    }
}