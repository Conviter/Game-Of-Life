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
        int index = y * 512 + x;
        int word = (index >> 6);      // index / 64
        int bit  = index & 63;
        grid[word] |= (1L << bit);
    }



    private void addCell(int localX, int localY, long gridKey) {
        totalAlive.incrementAndGet();
        long[] grid = cells.get(gridKey);
        if (grid == null) {
            grid = new long[4096];
            cells.put(gridKey, grid);
        }
        int index = localY * 512 + localX;
        int word  = index >> 6;  // /64
        int bit   = index & 63;
        grid[word] |= (1L << bit);
    }


    private void setAlive(int x, int y){

    }

    private void setDead(int x, int y){

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
        int index = localY * 512 + localX;
        int word  = index >> 6;  // /64
        int bit   = index & 63;

        grid[word] &= ~(1L << bit);
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
                    int x = i;
                    int y = j;
                    double rolled = random.nextDouble();
                    if (rolled >= 0.6) {
                        addCell(x, y, gridX, gridY);
                        left--;
                    }
                }
            }
            gridX++;
        }
    }

    public void wipeBoard(){
        cells.clear();
        totalAlive.set(0);
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------



    public class ParallelTask extends RecursiveAction {
        long[] cellsThisState;
        long[] cellsNextState;
        int gridX;
        int gridY;
        int aliveCells = 0;

        public ParallelTask(long[] cellsThisState, long grid) {
            this.cellsThisState = cellsThisState;
            this.cellsNextState = new long[4096];
            gridX = (int) (grid >> 32);
            gridY = (int) grid;
        }

        @Override
        protected void compute() {
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    int neighbourCount = 0;

                    for (int k = 0; k < offsets.length; k += 2) {
                        int nx = i + offsets[k];
                        int ny = j + offsets[k + 1];

                        long[] neighbourGrid = cellsThisState; // default: same chunk

                        // check if neighbor is outside current chunk
                        if (nx < 0 || nx >= GRID_SIZE || ny < 0 || ny >= GRID_SIZE) {
                            int neighbourGridX = gridX + Math.floorDiv(nx, GRID_SIZE);
                            int neighbourGridY = gridY + Math.floorDiv(ny, GRID_SIZE);

                            long neighbourKey = Game.cordsToLong(neighbourGridX, neighbourGridY);
                            neighbourGrid = cells.get(neighbourKey);

                            if (neighbourGrid == null) continue; // dead if neighbor chunk doesn't exist

                            // wrap nx/ny into neighbour chunk
                            nx = Math.floorMod(nx, GRID_SIZE);
                            ny = Math.floorMod(ny, GRID_SIZE);
                        }

                        int index = ny * 512 + nx;
                        int word = index >> 6;
                        int bit = index & 63;
                        if ((neighbourGrid[word] & (1L << bit)) != 0) {
                            neighbourCount++;
                        }
                    }

                    int index = j * 512 + i;
                    int arrayIndex = index >> 6;
                    int bitIndex = index & 63;

                    boolean alive = (cellsThisState[arrayIndex] & (1L << bitIndex)) != 0;
                    if ((alive && neighbourCount == 2) || neighbourCount == 3) {
                        cellsNextState[arrayIndex] |= (1L << bitIndex);
                        aliveCells++;
                    }
                }
            }
        }
    }

    public void applyRules() {
        long pre = System.currentTimeMillis();
        totalAlive.set(0);


        cells.long2ObjectEntrySet().parallelStream().forEach(gridCells -> {
            ParallelTask task = new ParallelTask(gridCells.getValue(), gridCells.getLongKey());
            task.compute();
            //next.addAll(task.births);
            cells.put(gridCells.getLongKey(), task.cellsNextState);
            totalAlive.addAndGet(task.aliveCells);
        });

        updateTime = (int)(System.currentTimeMillis() - pre);
        updateCount++;
        updateTimeTotal += updateTime;
//        if (updateCount == 100){
//            wipeBoard();
//            spawnCountOfCells(5000000);
//        } else if (updateCount == 200){
//            wipeBoard();
//            spawnCountOfCells(5000000);
//        } else if (updateCount == 300){
//            wipeBoard();
//            spawnCountOfCells(5000000);
//        } else if(updateCount == 400){
//            wipeBoard();
//            System.out.println("Processing speed per generation: " +  (updateTimeTotal / updateCount));
//        }
        //System.out.println(updateTime);
    }
}


