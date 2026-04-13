package ParallelGridBitGrid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Game {

    public static final int GRID_SIZE = 512;

    private static final int[] OFFSETS = {
            -1, 0,
            -1, 1,
            -1, -1,
            1, 0,
            1, 1,
            1, -1,
            0, 1,
            0, -1
    };

    public Long2ObjectOpenHashMap<long[]> cells = new Long2ObjectOpenHashMap<>();

    public AtomicInteger totalAlive = new AtomicInteger();

    public int updateTime;
    public int updateTimeTotal;
    public int updateCount;

    public Game(int startingCells) {
        spawnCountOfCells(startingCells);
    }

    // -----------------------
    // Bit Operations
    // -----------------------

    private void setAlive(int x, int y, long[] grid) {
        int index = y * GRID_SIZE + x;
        grid[index >> 6] |= (1L << (index & 63));
    }

    private void setDead(int x, int y, long[] grid) {
        int index = y * GRID_SIZE + x;
        grid[index >> 6] &= ~(1L << (index & 63));
    }

    public static boolean getState(int x, int y, long[] grid) {
        int index = y * GRID_SIZE + x;
        return (grid[index >> 6] & (1L << (index & 63))) != 0;
    }

    // -----------------------
    // Public API
    // -----------------------

    public void clear() {
        cells.clear();
        totalAlive.set(0);
    }

    public void clearCell(long cell) {
        int x = Utility.longToIntX(cell);
        int y = Utility.longToIntY(cell);

        int gridX = Math.floorDiv(x, GRID_SIZE);
        int gridY = Math.floorDiv(y, GRID_SIZE);

        int localX = Math.floorMod(x, GRID_SIZE);
        int localY = Math.floorMod(y, GRID_SIZE);

        long[] grid = cells.get(Utility.cordsToLong(gridX, gridY));
        if (grid == null) return;

        setDead(localX, localY, grid);
    }

    public void spawnCell(long cell) {
        int x = Utility.longToIntX(cell);
        int y = Utility.longToIntY(cell);

        int gridX = Math.floorDiv(x, GRID_SIZE);
        int gridY = Math.floorDiv(y, GRID_SIZE);

        int localX = Math.floorMod(x, GRID_SIZE);
        int localY = Math.floorMod(y, GRID_SIZE);

        long gridKey = Utility.cordsToLong(gridX, gridY);

        addCell(localX, localY, gridKey);
    }

    public void spawnCountOfCells(int count) {
        Random random = new Random();
        int gridX = 0;
        int gridY = 0;
        int left = count;

        while (left > 0) {
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    if (random.nextDouble() >= 0.6) {
                        addCell(i, j, gridX, gridY);
                        left--;
                        if (left <= 0) return;
                    }
                }
            }
            gridX++;
        }
    }

    // -----------------------
    // Internal Add Cell
    // -----------------------

    private void addCell(int x, int y, int gridX, int gridY) {
        totalAlive.incrementAndGet();

        long gridCord = Utility.cordsToLong(gridX, gridY);
        long[] grid = cells.get(gridCord);

        if (grid == null) {
            grid = new long[4096];
            cells.put(gridCord, grid);
        }

        setAlive(x, y, grid);
    }

    private void addCell(int localX, int localY, long gridKey) {
        totalAlive.incrementAndGet();

        long[] grid = cells.get(gridKey);

        if (grid == null) {
            grid = new long[4096];
            cells.put(gridKey, grid);
        }

        setAlive(localX, localY, grid);
    }

    // -----------------------
    // Parallel Rule Task
    // -----------------------

    public class ParallelTask {

        Long2ObjectOpenHashMap<long[]> currentMap;
        long[] cellsThisState;
        long[] cellsNextState;
        int gridX;
        int gridY;
        int aliveCells = 0;

        public ParallelTask(Long2ObjectOpenHashMap<long[]> currentMap, long[] cellsThisState, long gridKey) {
            this.currentMap = currentMap;
            this.cellsThisState = cellsThisState;
            this.cellsNextState = new long[4096];
            this.gridX = (int) (gridKey >> 32);
            this.gridY = (int) gridKey;
        }

        protected void compute() {
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int y = 0; y < GRID_SIZE; y++) {

                    int neighbourCount = 0;

                    for (int k = 0; k < OFFSETS.length; k += 2) {
                        int neighbourX = x + OFFSETS[k];
                        int neighbourY = y + OFFSETS[k + 1];

                        long[] neighbourGrid = cellsThisState;

                        if (neighbourX < 0 || neighbourX >= GRID_SIZE ||
                                neighbourY < 0 || neighbourY >= GRID_SIZE) {

                            int neighbourGridX = gridX + Math.floorDiv(neighbourX, GRID_SIZE);
                            int neighbourGridY = gridY + Math.floorDiv(neighbourY, GRID_SIZE);

                            long neighbourKey = Utility.cordsToLong(neighbourGridX, neighbourGridY);

                            neighbourGrid = currentMap.get(neighbourKey);
                            if (neighbourGrid == null) continue;

                            neighbourX = Math.floorMod(neighbourX, GRID_SIZE);
                            neighbourY = Math.floorMod(neighbourY, GRID_SIZE);
                        }

                        if (getState(neighbourX, neighbourY, neighbourGrid)) {
                            neighbourCount++;
                        }
                    }

                    boolean alive = getState(x, y, cellsThisState);

                    if ((alive && neighbourCount == 2) || neighbourCount == 3) {
                        setAlive(x, y, cellsNextState);
                        aliveCells++;
                    }
                }
            }
        }
    }

    // -----------------------
    // Tick
    // -----------------------

    public void applyRules() {
        long pre = System.currentTimeMillis();

        totalAlive.set(0);

        Long2ObjectOpenHashMap<long[]> next = new Long2ObjectOpenHashMap<>(cells.size());

        cells.long2ObjectEntrySet().parallelStream().forEach(entry -> {
            ParallelTask task = new ParallelTask(cells, entry.getValue(), entry.getLongKey());
            task.compute();

            synchronized (next) {
                next.put(entry.getLongKey(), task.cellsNextState);
            }

            totalAlive.addAndGet(task.aliveCells);
        });

        cells = next;

        updateTime = (int) (System.currentTimeMillis() - pre);
        updateCount++;
        updateTimeTotal += updateTime;
    }
}