import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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

    Set<Cell> aliveCells = new HashSet<>();
    Set<Cell> deadCellsToCheck = new HashSet<>();
    Set<Cell> aliveCellsNextState = new HashSet<>();

    // -------------------------------------------------
    // Constructor
    // -------------------------------------------------

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.cellSize = cellSize;

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

    public void applyRules() {
        long pre = System.currentTimeMillis();

        aliveCellsNextState.clear();
        deadCellsToCheck.clear();

        killCells();
        reviveCells();

        // Move next state into current state
        aliveCells = new HashSet<>(aliveCellsNextState);

        long post = System.currentTimeMillis();
        System.out.println(post - pre);
    }

    // -------------------------------------------------
    // Rule Logic
    // -------------------------------------------------

    public void killCells() {
        for (Cell cell : aliveCells) {

            int aliveNeighbours = 0;

            for (Cell offset : NEIGHBOURS) {
                Cell neighbour = cell.addCell(offset);

                if (aliveCells.contains(neighbour)) {
                    aliveNeighbours++;
                } else {
                    deadCellsToCheck.add(neighbour);
                }
            }

            // Survives with 2 or 3 neighbours
            if (aliveNeighbours == 2 || aliveNeighbours == 3) {
                aliveCellsNextState.add(cell);
            }
        }
    }

    public void reviveCells() {
        for (Cell cell : deadCellsToCheck) {

            int aliveNeighbours = 0;

            for (Cell offset : NEIGHBOURS) {
                if (aliveCells.contains(cell.addCell(offset))) {
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