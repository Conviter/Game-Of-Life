package OldVersions.PrimitiveMap;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Random;


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

    private record Cell(int x, int y) {}

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

    LongOpenHashSet aliveCells;
    LongOpenHashSet deadCellsToCheck;
    LongOpenHashSet aliveCellsNextState;

    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;

        this.aliveCells = new LongOpenHashSet(startingCells);
        this.deadCellsToCheck = new LongOpenHashSet(startingCells);
        this.aliveCellsNextState = new LongOpenHashSet(startingCells);

        Random random = new Random();

        for (int i = 0; i < startingCells; i++) {
            int x = random.nextInt(this.gameWidth);
            int y = random.nextInt(this.gameHeight);
            aliveCells.add(cordsToLong(x, y));
        }
    }

    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    public void spawnCell(long cell) {
        aliveCells.add(cell);
    }

    public void applyRules() {
        long pre = System.currentTimeMillis();

        aliveCellsNextState.clear();
        deadCellsToCheck.clear();


        killCells();
        reviveCells();

        // Move next state into current state
        aliveCells = new LongOpenHashSet(aliveCellsNextState);

        long post = System.currentTimeMillis();
        System.out.println(post - pre);
    }

    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------

    public void killCells() {
        for (long cell : aliveCells) {
            int x = longToIntX(cell);
            int y = longToIntY(cell);

            int aliveNeighbours = 0;

            for (Cell offset : NEIGHBOURS) {
                //Cell neighbour = cell.addCell(offset);
                int neighbourX = x + offset.x;
                int neighbourY = y + offset.y;

                if (aliveCells.contains(cordsToLong(neighbourX, neighbourY))) {
                    aliveNeighbours++;
                } else {
                    deadCellsToCheck.add(cordsToLong(neighbourX, neighbourY));
                }
            }

            // Survives with 2 or 3 neighbours
            if (aliveNeighbours == 2 || aliveNeighbours == 3) {
                aliveCellsNextState.add(cell);
            }
        }
    }

    public void reviveCells() {
        for (long cell : deadCellsToCheck) {
            int x = longToIntX(cell);
            int y = longToIntY(cell);
            int aliveNeighbours = 0;

            for (Cell offset : NEIGHBOURS) {
                int neighbourX = x + offset.x;
                int neighbourY = y + offset.y;
                if (aliveCells.contains(cordsToLong(neighbourX, neighbourY))) {
                    aliveNeighbours++;
                }
            }

            // Revives with exactly 3 neighbours
            if (aliveNeighbours == 3) {
                aliveCellsNextState.add(cell);
            }
        }
    }
}