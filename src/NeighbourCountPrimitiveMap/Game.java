package NeighbourCountPrimitiveMap;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.*;


public class Game {

    // -------------------------------------------------
    // Configuration
    // -------------------------------------------------

    int gameWidth;
    int gameHeight;
    int cellSize;


    // -------------------------------------------------
    // Cell Representation
    // -------------------------------------------------

    public record Cell(int x, int y) {
        public Cell addCell(Cell other) {
            return new Cell(this.x + other.x, this.y + other.y);
        }
    }

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
    // Neighbour Offsets (Moore Neighborhood)
    // -------------------------------------------------

    private static final Cell[] NEIGHBOURS = {
            new Cell(-1, 0),
            new Cell(-1, 1),
            new Cell(-1, -1),
            new Cell(1, 0),
            new Cell(1, 1),
            new Cell(1, -1),
            new Cell(0, 1),
            new Cell(0, -1)
    };

    // -------------------------------------------------
    // State
    // -------------------------------------------------
    //long[] aliveCells;

    LongOpenHashSet aliveCells;
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
        //this.aliveCells = new long[startingCells];


        this.aliveCells = new LongOpenHashSet(startingCells);
        this.aliveCellsNextState = new LongOpenHashSet(startingCells*2);
        this.neighbourCount  = new Long2IntOpenHashMap(startingCells);


        Random random = new Random();

        for (int i = 0; i < startingCells; i++) {
            int x = random.nextInt(this.gameWidth);
            int y = random.nextInt(this.gameHeight);
            aliveCells.add(cordsToLong(x, y));
           // addCell(cordsToLong(x, y));

        }
    }

//    private void addCell(long cell){
//        if (filledIndex == aliveCells.length){
//            long[] newAliveCells = new long[aliveCells.length * 2];
//            System.arraycopy(aliveCells, 0, newAliveCells, 0, aliveCells.length);
//            aliveCells = newAliveCells;
//        }
//        aliveCells[filledIndex] = cell;
//        filledIndex += 1;
//    }


    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    public void spawnCell(long cell) {
        aliveCells.add(cell);
        //addCell(cell);
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------
    public void applyRules() {
        long pre = System.currentTimeMillis();
        for (long cell : aliveCells){
            neighbourCount.put(cell, neighbourCount.getOrDefault(cell, 0) + 10);
            int x = longToIntX(cell);
            int y = longToIntY(cell);
            for (Cell offset : NEIGHBOURS) {
                int neighbourX = x + offset.x;
                int neighbourY = y + offset.y;
                long key = cordsToLong(neighbourX, neighbourY);
                neighbourCount.put(key, neighbourCount.getOrDefault(key, 0) + 1);
            }
        }
        for (long set : neighbourCount.keySet()){
//            if ((aliveCells.contains(set) && neighbourCount.get(set) == 2) || neighbourCount.get(set) == 3){
//                aliveCellsNextState.add(set);
//            }
            if (neighbourCount.get(set) == 12 || neighbourCount.get(set) == 3 || neighbourCount.get(set) == 13){
                aliveCellsNextState.add(set);
            }
        }
            aliveCells = aliveCellsNextState.clone();
//        if (aliveCells.length <= aliveCellsNextState.size()){
//            aliveCells = new long[aliveCellsNextState.size() * 2];
//        }
//        System.arraycopy(aliveCellsNextState.toLongArray(), 0, aliveCells, 0, aliveCellsNextState.size());
//        filledIndex = aliveCellsNextState.size();

        neighbourCount.clear();
        aliveCellsNextState.clear();
        long post = System.currentTimeMillis();
        System.out.println((post - pre));
    }
}