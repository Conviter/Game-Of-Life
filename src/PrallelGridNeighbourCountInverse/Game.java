package PrallelGridNeighbourCountInverse;

import it.unimi.dsi.fastutil.longs.*;

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

    Long2ObjectOpenHashMap<LongOpenHashSet> cells = new Long2ObjectOpenHashMap<>();
    int totalAlive;
    int updateTime;

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

    // -------------------------------------------------
    // Helper Methods
    // -------------------------------------------------

    private void addCell(long cell) {
        totalAlive++;
        int xGrid = longToIntX(cell) >> 9;
        int yGrid = longToIntY(cell) >> 9;
        long gridCord = cordsToLong(xGrid, yGrid);
        if (cells.containsKey(cordsToLong(xGrid, yGrid))) {
            cells.get(gridCord).add(cell);
        } else {
            cells.put(gridCord, new LongOpenHashSet(startingCellSize));
            cells.get(gridCord).add(cell);
        }
    }

    public boolean getNeighbourState(int neighbourX, int neighbourY) {
        int gridCordX = neighbourX >> 9;
        int gridCordY = neighbourY >> 9;

        LongOpenHashSet grid = cells.get(cordsToLong(gridCordX, gridCordY));

        if (grid == null) {
            return false;
        }

        return grid.contains(cordsToLong(neighbourX, neighbourY));
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


    public class ParallelTask extends RecursiveAction {
        LongOpenHashSet cellsThisState;
        LongOpenHashSet cellsNextState = new LongOpenHashSet(startingCellSize);
        LongOpenHashSet deadNeighboursToCheck = new LongOpenHashSet(startingCellSize);
        LongOpenHashSet births = new LongOpenHashSet(startingCellSize);
        int aliveCells = 0;

        public ParallelTask(LongOpenHashSet cellsThisState) {
            this.cellsThisState = cellsThisState;
        }

        @Override
        protected void compute() {
            for (long cell : cellsThisState) {

                int x = longToIntX(cell);
                int y = longToIntY(cell);

                int neighbourCount = 0;

                for (int j = 0; j < neighbourOffsetX.length; j++) {

                    int neighbourX = x + neighbourOffsetX[j];
                    int neighbourY = y + neighbourOffsetY[j];

                    if (getNeighbourState(neighbourX, neighbourY)) {
                        neighbourCount++;
                    } else {
                        deadNeighboursToCheck.add(cordsToLong(neighbourX, neighbourY));
                    }
                }

                // survival rule
                if (neighbourCount == 2 || neighbourCount == 3) {
                    cellsNextState.add(cell);
                    aliveCells++;
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

                    if (getNeighbourState(neighbourX, neighbourY)) {
                        neighbourCount++;
                    }
                }

                if (neighbourCount == 3) {
                    aliveCells++;
                    births.add(cell);
                }
            }
        }
    }


    public void applyRules() {
        long pre = System.currentTimeMillis();

        totalAlive = 0;

        Long2ObjectOpenHashMap<LongOpenHashSet> nextCells =
                new Long2ObjectOpenHashMap<>(cells.size());

        cells.long2ObjectEntrySet().parallelStream().forEach(gridCells -> {
            ParallelTask task = new ParallelTask(gridCells.getValue());
            task.compute();
            nextCells.put(gridCells.getLongKey(), task.cellsNextState);
            nextCells.get(gridCells.getLongKey()).addAll(task.births);
            totalAlive += task.aliveCells;
        });

        cells = nextCells;

        long post = System.currentTimeMillis();
        updateTime = (int)(post - pre);
        //System.out.println(updateTime);
    }
}