package OldVersions.NeighbourCountPrimitiveMap;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.*;


public class Game {

    // -------------------------------------------------
    // Configuration
    // -------------------------------------------------

    int gameWidth;
    int gameHeight;
    int cellSize;
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


    long[] aliveCells;
    Long2IntOpenHashMap neighbourCount;
    LongOpenHashSet aliveCellsNextState;
    int filledIndex;


    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;
        this.aliveCells = new long[startingCells];


        this.aliveCellsNextState = new LongOpenHashSet(startingCells*2);
        this.neighbourCount  = new Long2IntOpenHashMap(startingCells);


        Random random = new Random();

        for (int i = 0; i < startingCells; i++) {
            int x = random.nextInt(this.gameWidth);
            int y = random.nextInt(this.gameHeight);
            addCell(cordsToLong(x, y));

        }
    }

    private void addCell(long cell){
        if (filledIndex == aliveCells.length){
            long[] newAliveCells = new long[aliveCells.length * 2];
            System.arraycopy(aliveCells, 0, newAliveCells, 0, aliveCells.length);
            aliveCells = newAliveCells;
        }
        aliveCells[filledIndex] = cell;
        filledIndex += 1;
    }


    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    public void spawnCell(long cell) {
       // aliveCells.add(cell);
        addCell(cell);
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------

    public void applyRules() {
        long pre = System.currentTimeMillis();
        for (long cell : aliveCells){
            neighbourCount.addTo(cell, 10);
            int x = longToIntX(cell);
            int y = longToIntY(cell);
            for (int i = 0; i < neighbourOffsetX.length; i++) {
                int neighbourX = x + neighbourOffsetX[i];
                int neighbourY = y + neighbourOffsetY[i];
                long key = cordsToLong(neighbourX, neighbourY);
                neighbourCount.addTo(key, 1);
            }
        }



        for (Long2IntOpenHashMap.Entry map : neighbourCount.long2IntEntrySet()){
            int count = map.getIntValue();
            long cell = map.getLongKey();
            if (count == 12 ||count == 3 || count == 13){
                aliveCellsNextState.add(cell);
            }
        }


        aliveCells = aliveCellsNextState.toLongArray();
        filledIndex = aliveCellsNextState.size();

        neighbourCount.clear();
        aliveCellsNextState.clear();

        long post = System.currentTimeMillis();
        updateTime = (int) (post - pre);

    }
}