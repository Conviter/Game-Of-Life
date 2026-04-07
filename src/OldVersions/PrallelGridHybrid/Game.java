package OldVersions.PrallelGridHybrid;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinTask;
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

    Long2ObjectOpenHashMap<LongOpenHashSet> cells = new Long2ObjectOpenHashMap<>();
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

    private void addCell(long cell) {
        totalAlive.incrementAndGet();
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
        perAxis = (int) (perAxis / 0.6);
        int cellsLeft = count;

        for (int i = 0; i < perAxis; i++) {
            for (int j = 0; j < perAxis; j++) {
                if (cellsLeft == 0){
                    return;
                }
                double rolled = random.nextDouble();
                if (rolled >= 0.6){
                    addCell(cordsToLong(i, j));
                    cellsLeft--;
                }
            }
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
        LongOpenHashSet cellsThisState;
        LongOpenHashSet cellsNextState;
        Long2IntOpenHashMap neighbourCount;
        int gridX;
        int gridY;
        long thisGrid;
        int aliveCells = 0;
        int minGridX;
        int maxGridX;
        int minGridY;
        int maxGridY;
        private final long[] cellsArr = new long[4];

        public ParallelTask(LongOpenHashSet cellsThisState, long grid) {
            this.cellsThisState = cellsThisState;
            cellsNextState = new LongOpenHashSet(cellsThisState.size() * 4);
            neighbourCount = new Long2IntOpenHashMap(cellsThisState.size() * 4);
            gridX = (int) (grid >> 32);
            gridY = (int) grid;
            thisGrid = grid;
        }

        @Override
        protected void compute() {
            minGridX = gridX * GRID_SIZE;
            maxGridX = minGridX + GRID_SIZE - 1;
            minGridY = gridY * GRID_SIZE;
            maxGridY = minGridY + GRID_SIZE - 1;

            //long pre = System.currentTimeMillis();
            for (long cell : cellsThisState) {
                int x = (int)(cell >> 32);
                int y = (int)cell;


                if (x != minGridX && x != maxGridX && y != minGridY && y != maxGridY) {
                    neighbourCount.addTo(cell, 10);
                    int len = offsets.length;
                    for (int j = 0; j < len; j += 2) {
                        int neighbourX = (x + offsets[j]);
                        int neighbourY = (y + offsets[j + 1]);
                        long key = (((long) neighbourX) << 32) | (neighbourY & 0xffffffffL);
                        if (neighbourX != minGridX && neighbourX != maxGridX && neighbourY != minGridY && neighbourY != maxGridY) {
                            neighbourCount.addTo(key, 1);
                        }
                    }
                }
            }

            for (int j = 0; j < 512; j++) {
                int x = minGridX + j;
                int y = minGridY + j;
                cellsArr[0] = cordsToLong(x, minGridY);
                cellsArr[1] = cordsToLong(x, maxGridY);
                cellsArr[2] = cordsToLong(minGridX, y);
                cellsArr[3] = cordsToLong(maxGridX, y);

                for (long cell : cellsArr) {
                    boolean state = cellsThisState.contains(cell);
                    int neighbourCount = 0;
                    int cellX = (int)(cell >> 32);
                    int cellY = (int)cell;

                    int len = offsets.length;
                    for (int k = 0; k < len; k += 2) {
                        int neighbourX = (cellX + offsets[k]);
                        int neighbourY = (cellY + offsets[k + 1]);
                        long neighbourCord = (((long) neighbourX) << 32) | (neighbourY & 0xffffffffL);

                        long neighbourGridCord = (((long) neighbourX >> 9) << 32) | ((neighbourY>>9) & 0xffffffffL);
                        LongOpenHashSet neighbourGrid = cells.get(neighbourGridCord);


                        if (thisGrid == neighbourGridCord && state && neighbourX != minGridX && neighbourX != maxGridX && neighbourY != minGridY && neighbourY != maxGridY) {
                            this.neighbourCount.addTo(neighbourCord, 1);
                        }

                        if (neighbourGrid != null && neighbourGrid.contains(neighbourCord)) {
                            neighbourCount++;
                        }
                    }
                    if ((state && neighbourCount == 2) || neighbourCount == 3) {
                        cellsNextState.add(cell);
                        aliveCells++;
                    }
                }

            }

//            long post = System.currentTimeMillis();
//            System.out.println((post - pre));

            var it = neighbourCount.long2IntEntrySet().fastIterator();
            while (it.hasNext()) {
                var entry = it.next();

                if (entry.getIntValue() == 13 || entry.getIntValue() == 3 || entry.getIntValue() == 12) {
                    cellsNextState.add(entry.getLongKey());
                    aliveCells++;
                }
            }
        }
    }

    public void applyRules() {
        long pre = System.currentTimeMillis();
        totalAlive.set(0);

        Long2ObjectOpenHashMap<LongOpenHashSet> nextCells =
                new Long2ObjectOpenHashMap<>(cells.size());

        List<ParallelTask> tasks = new ArrayList<>(cells.size());

        for (var entry : cells.long2ObjectEntrySet()) {
            tasks.add(new ParallelTask(entry.getValue(), entry.getLongKey()));
        }

        ForkJoinTask.invokeAll(tasks);

        for (ParallelTask task : tasks) {
            if (!task.cellsNextState.isEmpty()) {
                nextCells.put(task.thisGrid, task.cellsNextState);
            }
            totalAlive.addAndGet(task.aliveCells);
        }

        cells = nextCells;

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


