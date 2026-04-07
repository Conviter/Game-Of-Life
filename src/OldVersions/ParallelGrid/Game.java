package OldVersions.ParallelGrid;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

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


    Long2ObjectOpenHashMap<LongOpenHashSet> aliveCellsCollection;
    Long2ObjectOpenHashMap<Long2IntOpenHashMap> neighbourCountCollection;
    Long2ObjectOpenHashMap<LongOpenHashSet> aliveCellsNextStateCollection;
    int[] filledIndex;


    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;
        //this.aliveCells = new long[startingCells];
        this.startingCellSize = startingCells;

        int thread_count = Runtime.getRuntime().availableProcessors();

        System.out.println(thread_count);

        aliveCellsCollection = new Long2ObjectOpenHashMap<>(thread_count);
        aliveCellsNextStateCollection = new Long2ObjectOpenHashMap<>(thread_count);
        neighbourCountCollection = new Long2ObjectOpenHashMap<>(thread_count);
        filledIndex = new int[thread_count];

        Random random = new Random();

        spawnCountOfCells(1000000);

//        for (int i = 0; i < startingCells; i++) {
//            int x = random.nextInt(this.gameWidth);
//            int y = random.nextInt(this.gameHeight);
//            addCell(cordsToLong(x, y));
//
//        }
    }

    private void addCell(long cell){

        int xGrid = longToIntX(cell) / 512;
        int yGrid = longToIntY(cell) / 512;
        if (aliveCellsCollection.containsKey(cordsToLong(xGrid, yGrid))){
            aliveCellsCollection.get(cordsToLong(xGrid, yGrid)).add(cell);
        } else {
            aliveCellsCollection.put(cordsToLong(xGrid, yGrid), new LongOpenHashSet(startingCellSize));
            aliveCellsCollection.get(cordsToLong(xGrid, yGrid)).add(cell);
        }
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

    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------

    public class ApplyRulesTask extends RecursiveAction {
        Long2IntOpenHashMap neighbourCount = new Long2IntOpenHashMap(startingCellSize);
        LongOpenHashSet aliveCells;
        LongOpenHashSet aliveCellsNextState = new LongOpenHashSet(startingCellSize);

        public ApplyRulesTask(LongOpenHashSet aliveCells) {
            this.aliveCells = aliveCells;
        }

        @Override
        protected void compute() {
            for (long cell : aliveCells) {
                neighbourCount.addTo(cell, 10);
                int x = longToIntX(cell);
                int y = longToIntY(cell);

                for (int j = 0; j < neighbourOffsetX.length; j++) {
                    int neighbourX = x + neighbourOffsetX[j];
                    int neighbourY = y + neighbourOffsetY[j];
                    long key = cordsToLong(neighbourX, neighbourY);
                    neighbourCount.addTo(key, 1);
                }
            }

            for (Long2IntOpenHashMap.Entry map : neighbourCount.long2IntEntrySet()) {
                int count = map.getIntValue();
                long cell = map.getLongKey();
                if (count == 12 || count == 3 || count == 13) {
                    aliveCellsNextState.add(cell);
                }
            }

            aliveCells.clear();
            aliveCells.addAll(aliveCellsNextState);

            neighbourCount.clear();
            aliveCellsNextState.clear();
        }
    }

    public void applyRules() {
        long pre = System.currentTimeMillis();
        aliveCellsCollection.values().parallelStream().forEach(gridCells -> {
            ApplyRulesTask task = new ApplyRulesTask(gridCells);
            task.compute();
        });


        long post = System.currentTimeMillis();
        //System.out.println("Map :"+(post - pre));


    }
}