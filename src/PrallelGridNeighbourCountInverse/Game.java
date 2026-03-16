package PrallelGridNeighbourCountInverse;

import it.unimi.dsi.fastutil.longs.*;

import java.util.BitSet;
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

    Long2ObjectOpenHashMap<LongOpenHashSet> allCellsThisState = new Long2ObjectOpenHashMap<>();
    Long2ObjectOpenHashMap<LongOpenHashSet> allCellsNextState = new Long2ObjectOpenHashMap<>();
    //Long2ObjectOpenHashMap<LongOpenHashSet> allDeadNeighboursToCheck = new Long2ObjectOpenHashMap<>();
    Long2ObjectOpenHashMap<long[]> allDeadNeighboursToCheck = new Long2ObjectOpenHashMap<>();
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
        LongOpenHashSet thisStateSet = allCellsThisState.get(gridCord);
        LongOpenHashSet nextStateSet = allCellsNextState.get(gridCord);
        long[] deadNeighbour = allDeadNeighboursToCheck.get(gridCord);
        //LongOpenHashSet deadNeighbour = allDeadNeighboursToCheck.get(gridCord);

        if (thisStateSet == null) {
            thisStateSet = new LongOpenHashSet(250000);
            allCellsThisState.put(gridCord, thisStateSet);
        }

        if (nextStateSet == null) {
            nextStateSet = new LongOpenHashSet(250000);
            allCellsNextState.put(gridCord, nextStateSet);
        }

        if (deadNeighbour == null) {
            //deadNeighbour = new LongOpenHashSet(250000);
            deadNeighbour = new long[250000];
            allDeadNeighboursToCheck.put(gridCord, deadNeighbour);
        }
        thisStateSet.add(cell);
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
        allCellsThisState.clear();
        totalAlive = 0;
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------



    public class ParallelTask extends RecursiveAction {
        LongOpenHashSet cellsThisState;
        LongOpenHashSet cellsNextState;
        //LongOpenHashSet deadNeighboursToCheck;
        long[] deadNeighboursToCheck;
        int filledIndex = 0;
        int aliveCells = 0;

        public ParallelTask(LongOpenHashSet cellsThisState, LongOpenHashSet cellsNextState, long[] deadNeighboursToCheck) {
            this.cellsThisState = cellsThisState;
            this.cellsNextState = cellsNextState;
            this.deadNeighboursToCheck = deadNeighboursToCheck;
        }

        @Override
        protected void compute() {
            BitSet set = new BitSet();
            for (long cell : cellsThisState) {

                int x = longToIntX(cell);
                int y = longToIntY(cell);

                int neighbourCount = 0;

                for (int j = 0; j < offsets.length; j+=2) {

                    int neighbourX = (x + offsets[j]);
                    int neighbourY = (y + offsets[j+1]);

                    LongOpenHashSet grid = allCellsThisState.get(cordsToLong(neighbourX >>9, neighbourY>>9));

                    long neighbourCord = cordsToLong(neighbourX, neighbourY);

                    if (grid != null && grid.contains(neighbourCord)) {
                        neighbourCount++;
                    } else {
                        if (!set.get((neighbourX & 511) + (neighbourY & 511) * 512)){
         //                  deadNeighboursToCheck.add(neighbourCord);
                            if (filledIndex == deadNeighboursToCheck.length){
                                long[] newAliveCells = new long[deadNeighboursToCheck.length * 2];
                                System.arraycopy(deadNeighboursToCheck, 0, newAliveCells, 0, deadNeighboursToCheck.length);
                                deadNeighboursToCheck = newAliveCells;
                            }
                            deadNeighboursToCheck[filledIndex] = neighbourCord;
                            filledIndex += 1;
                            set.set((neighbourX & 511) + (neighbourY & 511) * 512);
                        }

                    }
                }

                // survival rule
                if (neighbourCount == 2 || neighbourCount == 3) {
                    cellsNextState.add(cell);
                    aliveCells++;
                }
            }
            long pre = System.currentTimeMillis();
            // check births
   //         for (long cell : deadNeighboursToCheck) {
            for (int i = 0; i < filledIndex; i++) {
                long cell = deadNeighboursToCheck[i];

                int x = longToIntX(cell);
                int y = longToIntY(cell);

                int neighbourCount = 0;

                for (int j = 0; j < offsets.length; j+=2) {

                    int neighbourX = x + offsets[j];
                    int neighbourY = y + offsets[j+1];

                    LongOpenHashSet grid = allCellsThisState.get(cordsToLong(neighbourX >> 9, neighbourY >> 9));

                    if (grid != null && grid.contains(cordsToLong(neighbourX, neighbourY))) {
                        neighbourCount++;
                    }
                }

                if (neighbourCount == 3) {
                    aliveCells++;
                    cellsNextState.add(cell);
                }
            }
            long post = System.currentTimeMillis();
            //System.out.println(post - pre);
        }
    }


    public void applyRules() {
        long pre = System.currentTimeMillis();

        totalAlive = 0;

        Long2ObjectOpenHashMap<LongOpenHashSet> nextCells =
                new Long2ObjectOpenHashMap<>(allCellsThisState.size());

        allCellsThisState.long2ObjectEntrySet().parallelStream().forEach(gridCells -> {
            ParallelTask task = new ParallelTask(gridCells.getValue(), allCellsNextState.get(gridCells.getLongKey()), allDeadNeighboursToCheck.get(gridCells.getLongKey()));
            task.compute();
            LongOpenHashSet next = task.cellsNextState;
            //next.addAll(task.births);
            nextCells.put(gridCells.getLongKey(), next);
            task.cellsThisState.clear();
            allCellsNextState.put(gridCells.getLongKey(), task.cellsThisState);
            totalAlive += task.aliveCells;
        });

        allCellsThisState = nextCells;

        long post = System.currentTimeMillis();
        updateTime = (int)(post - pre);
        updateTimeTotal+= updateTime;
        updateCount++;
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