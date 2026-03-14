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

    Long2ObjectOpenHashMap<LongOpenHashSet> cells = new Long2ObjectOpenHashMap<>();
    int totalAlive;
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

    private void addCell(long cell) {
        totalAlive++;
        int xGrid = longToIntX(cell) >> 9;
        int yGrid = longToIntY(cell) >> 9;
        long gridCord = cordsToLong(xGrid, yGrid);
        LongOpenHashSet set = cells.get(gridCord);

        if (set == null) {
            set = new LongOpenHashSet(250000);
            cells.put(gridCord, set);
        }
        set.add(cell);
    }


    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    public void spawnCell(long cell) {
        addCell(cell);
    }

    public void spawnCountOfCells(int count){
        Random random = new Random();

        int perAxis = (int) Math.sqrt(count);
        perAxis = (int) (perAxis / 0.7);
        int cellsLeft = count;

        for (int i = 0; i < perAxis; i++) {
            for (int j = 0; j < perAxis; j++) {
                if (cellsLeft == 0){
                    return;
                }
                double rolled = random.nextDouble();
                if (rolled >= 0.7){
                    addCell(cordsToLong(i, j));
                    cellsLeft--;
                }
            }
        }
    }

    public void wipeBoard(){
        cells.clear();
        totalAlive = 0;
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------



    public class ParallelTask extends RecursiveAction {
        LongOpenHashSet cellsThisState;
        LongOpenHashSet cellsNextState;
        LongOpenHashSet deadNeighboursToCheck = new LongOpenHashSet(startingCellSize);
        LongOpenHashSet births = new LongOpenHashSet(startingCellSize);
        int aliveCells = 0;

        public ParallelTask(LongOpenHashSet cellsThisState) {
            this.cellsThisState = cellsThisState;
             cellsNextState = new LongOpenHashSet(cellsThisState.size() * 2);
        }

        @Override
        protected void compute() {
            for (long cell : cellsThisState) {

                int x = longToIntX(cell);
                int y = longToIntY(cell);

                int neighbourCount = 0;

                for (int j = 0; j < offsets.length; j+=2) {

                    int neighbourX = (x + offsets[j]);
                    int neighbourY = (y + offsets[j+1]);

                    LongOpenHashSet grid = cells.get(cordsToLong(neighbourX >>9, neighbourY>>9));

                    long neighbourCord = cordsToLong(neighbourX, neighbourY);

                    if (grid != null && grid.contains(neighbourCord)) {
                        neighbourCount++;
                    } else {

                        deadNeighboursToCheck.add(neighbourCord);
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

                for (int j = 0; j < offsets.length; j+=2) {

                    int neighbourX = x + offsets[j];
                    int neighbourY = y + offsets[j+1];

                    LongOpenHashSet grid = cells.get(cordsToLong(neighbourX >> 9, neighbourY >> 9));

                    if (grid != null && grid.contains(cordsToLong(neighbourX, neighbourY))) {
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
            LongOpenHashSet next = task.cellsNextState;
            next.addAll(task.births);
            nextCells.put(gridCells.getLongKey(), next);
            totalAlive += task.aliveCells;
        });

        cells = nextCells;

        long post = System.currentTimeMillis();
        updateTime = (int)(post - pre);
        updateTimeTotal+= updateTime;
//        updateCount++;
//        if (updateCount == 100){
//            wipeBoard();
//            spawnCountOfCells(1000000);
//        } else if (updateCount == 200){
//            wipeBoard();
//            spawnCountOfCells(1000000);
//        } else if (updateCount == 300){
//            wipeBoard();
//            spawnCountOfCells(1000000);
//        } else if(updateCount == 400){
//            wipeBoard();
//            System.out.println("Processing speed per generation: " +  (updateTimeTotal / updateCount));
//        }
        //System.out.println(updateTime);
    }
}