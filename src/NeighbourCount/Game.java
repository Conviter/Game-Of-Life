package NeighbourCount;

import java.lang.reflect.Array;
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

    Set<Cell> aliveCells;
    Map<Cell, Integer> neighbourCount;
    Set<Cell> aliveCellsNextState;
    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;

        this.aliveCells = new HashSet<>(startingCells);
        this.aliveCellsNextState = new HashSet<>(startingCells);
        this.neighbourCount  = new HashMap<>(startingCells);


        Random random = new Random();

        for (int i = 0; i < startingCells; i++) {
            int x = random.nextInt(this.gameWidth);
            int y = random.nextInt(this.gameHeight);
            aliveCells.add(new Cell(x, y));
        }
    }

    // -------------------------------------------------
    // Public API
    // -------------------------------------------------

    public void spawnCell(Cell cell) {
        aliveCells.add(cell);
    }


    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------
    public void applyRules() {
        long pre = System.currentTimeMillis();
        for (Cell cell : aliveCells){
            for (Cell offset : NEIGHBOURS) {
                Cell neighbour = cell.addCell(offset);
                neighbourCount.put(neighbour, neighbourCount.getOrDefault(neighbour, 0) + 1);
            }
        }

        for (Map.Entry<Cell, Integer> cell : neighbourCount.entrySet()){
            if ((aliveCells.contains(cell.getKey()) && cell.getValue() == 2) || cell.getValue() == 3){
                aliveCellsNextState.add(cell.getKey());
            }
        }
        aliveCells = aliveCellsNextState;
        neighbourCount.clear();
        aliveCellsNextState = new HashSet<>();
        long post = System.currentTimeMillis();
        System.out.println((post - pre));
    }
}