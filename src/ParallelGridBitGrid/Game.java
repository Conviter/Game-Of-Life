package ParallelGridBitGrid;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Random;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;


public class Game {

    // -------------------------------------------------
    // Configuration
    // -------------------------------------------------

    int gameWidth;
    int gameHeight;
    int cellSize;
    int startingCellSize;
    private final int GRID_SIZE = 1 << 9;
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

    private static final int[] offsets = {-1, 0,
                                          -1, 1,
                                          -1, -1,
                                           1, 0,
                                           1, 1,
                                           1, -1,
                                           0, 1,
                                           0, -1};

    // -------------------------------------------------
    // State
    // -------------------------------------------------

    Long2ObjectOpenHashMap<long[]> cells = new Long2ObjectOpenHashMap<>();

    AtomicInteger totalAlive = new AtomicInteger();
    int updateTime;
    int updateTimeTotal;
    int updateCount;

    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;
        this.startingCellSize = startingCells;

        spawnCountOfCells(startingCells);
    }

    // -------------------------------------------------
    // Helper Methods
    // -------------------------------------------------

    private void addCell(int x, int y, int gridX, int gridY) {
        totalAlive.incrementAndGet();
        long gridCord = cordsToLong(gridX, gridY);
        long[] grid = cells.get(gridCord);
        if (grid == null){
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


    private void setAlive(int x, int y, long[] grid){
        int index = y * 512 + x;
        int word = (index >> 6);      // index / 64
        int bit  = index & 63;
        grid[word] |= (1L << bit);
    }

    private void setDead(int x, int y, long[] grid){
        int index = y * 512 + x;
        int word  = index >> 6;  // /64
        int bit   = index & 63;

        grid[word] &= ~(1L << bit);
    }

    public static boolean getState(int x, int y, long[] grid){
        int index = y * 512 + x;
        int arrayIndex = index >> 6;
        int bitIndex = index & 63;

        return (grid[arrayIndex] & (1L << bitIndex)) != 0;
    }

    // -------------------------------------------------
    // Public API
    // -------------------------------------------------


    public void clear(){
        cells.clear();
    }

    public void clearCell(long cell){
        int x = longToIntX(cell);
        int y = longToIntY(cell);

        int gridX = Math.floorDiv(x, 512);
        int gridY = Math.floorDiv(y, 512);

        int localX = Math.floorMod(x, 512);
        int localY = Math.floorMod(y, 512);
        long[] grid = cells.get(cordsToLong(gridX, gridY));
        if(grid == null){return;}
        setDead(localX, localY, grid);
    }

    public void spawnCell(long cell) {
        int x = longToIntX(cell);
        int y = longToIntY(cell);

        int gridX = Math.floorDiv(x, 512);
        int gridY = Math.floorDiv(y, 512);

        int localX = Math.floorMod(x, 512);
        int localY = Math.floorMod(y, 512);

        long gridKey = cordsToLong(gridX, gridY);

        addCell(localX, localY, gridKey);
    }

    public void spawnCountOfCells(int count){
        Random random = new Random();
        int gridX = 0;
        int gridY = 0;
        int left = count;
        while(left > 0){
            for (int i = 0; i < 512; i++) {
                for (int j = 0; j < 512; j++) {
                    double rolled = random.nextDouble();
                    if (rolled >= 0.6) {
                        addCell(i, j, gridX, gridY);
                        left--;
                    }
                }
            }
            gridX++;
        }
    }

    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------


    public class ParallelTask {
        Long2ObjectOpenHashMap<long[]> currentMap;

        long[] cellsThisState;
        long[] cellsNextState;
        int gridX;
        int gridY;
        int aliveCells = 0;

        public ParallelTask(Long2ObjectOpenHashMap<long[]> currentMap, long[] cellsThisState, long grid) {
            this.currentMap = currentMap;
            this.cellsThisState = cellsThisState;
            this.cellsNextState = new long[4096];
            gridX = (int) (grid >> 32);
            gridY = (int) grid;
        }

        protected void compute() {
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    int neighbourCount = 0;

                    for (int k = 0; k < offsets.length; k += 2) {
                        int nx = i + offsets[k];
                        int ny = j + offsets[k + 1];

                        long[] neighbourGrid = cellsThisState;

                        if (nx < 0 || nx >= GRID_SIZE || ny < 0 || ny >= GRID_SIZE) {
                            int neighbourGridX = gridX + Math.floorDiv(nx, GRID_SIZE);
                            int neighbourGridY = gridY + Math.floorDiv(ny, GRID_SIZE);

                            long neighbourKey = Game.cordsToLong(neighbourGridX, neighbourGridY);

                            neighbourGrid = currentMap.get(neighbourKey);
                            if (neighbourGrid == null) continue;

                            nx = Math.floorMod(nx, GRID_SIZE);
                            ny = Math.floorMod(ny, GRID_SIZE);
                        }

                        if (getState(nx, ny, neighbourGrid)) {
                            neighbourCount++;
                        }
                    }

                    boolean alive = getState(i, j, cellsThisState);
                    if ((alive && neighbourCount == 2) || neighbourCount == 3) {
                        setAlive(i, j, cellsNextState);
                        aliveCells++;
                    }
                }
            }
        }
    }

    public void applyRules() {
        long pre = System.currentTimeMillis();
        totalAlive.set(0);

        Long2ObjectOpenHashMap<long[]> next = new Long2ObjectOpenHashMap<>(cells.size());

        cells.long2ObjectEntrySet().parallelStream().forEach(entry -> {
            ParallelTask task = new ParallelTask(cells, entry.getValue(), entry.getLongKey());
            task.compute();

            next.put(entry.getLongKey(), task.cellsNextState);

            totalAlive.addAndGet(task.aliveCells);
        });
        cells = next;

        updateTime = (int)(System.currentTimeMillis() - pre);
        updateCount++;
        updateTimeTotal += updateTime;
    }
}


