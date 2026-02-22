import java.awt.*;
import java.util.*;
import java.util.concurrent.RecursiveAction;


public class Game {
    int gameWidth;
    int gameHeight;
    int cellSize;
    Point[] neighbours = {
            new Point(-1, 0),
            new Point(-1, 1),
            new Point(-1, -1),
            new Point(1, 0),
            new Point(1, 1),
            new Point(1, -1),
            new Point(0, 1),
            new Point(0, -1)
    };
    //Set<Point> aliveCells = new HashSet<>();
    Map<Point, Boolean> aliveCells = new HashMap<>();

    public Game(int startingCells, int gameWidth, int gameHeight, int cellSize) {
        this.gameHeight = gameHeight;
        this.gameWidth = gameWidth;
        this.cellSize = cellSize;
        Random rand = new Random();
//        for (int i = 0; i < startingCells; i++){
//            int x = rand.nextInt(this.gameWidth);
//            int y = rand.nextInt(this.gameHeight);
//            //aliveCells.add(new Point(x, y));
//            newCells.put(new Point(x, y), false);
       // }
    }

//    private class applyRulesTask extends RecursiveAction {
//
//        @Override
//        protected void compute() {
//            Map<Point, Boolean> aliveCellsNextState = new HashMap<>();
//            Set<Point> deadCellsToCheck = new HashSet<>();
//            Set<Point> aliveSet = aliveCells.keySet();
//            for (Point cell : aliveSet) {
//                int aliveNeighbours = 0;
//                for (Point neighbour : neighbours) {
//                    boolean neighbourState = aliveCells.containsKey(addPoint(cell, neighbour));
//                    if (neighbourState) {
//                        aliveNeighbours += 1;
//                    } else {
//                        if (!aliveCells.get(cell)) {
//                            deadCellsToCheck.add(addPoint(cell, neighbour));
//                        }
//                    }
//                }
//                if (aliveNeighbours == 3 || aliveNeighbours == 2) {
//                    aliveCellsNextState.put(cell, true);
//                }
//            }
//            for (Point cell : deadCellsToCheck) {
//                int aliveNeighbours = 0;
//                for (Point neighbour : neighbours) {
//                    boolean neighbourState = aliveCells.containsKey(addPoint(cell, neighbour));
//                    if (neighbourState) {
//                        aliveNeighbours += 1;
//                    }
//                }
//                if (aliveNeighbours == 3) {
//                    aliveCellsNextState.put(cell, false);
//                }
//
//            }
//            aliveCells = aliveCellsNextState;
//            deadCellsToCheck.clear();
//        }
//    }

    public void spawnCell(Point point) {
        aliveCells.put(point, false);
    }

    public void applyRules() {
        Map<Point, Boolean> aliveCellsNextState = new HashMap<>();
        Set<Point> deadCellsToCheck = new HashSet<>();
        Set<Point> aliveSet = aliveCells.keySet();
        for (Point cell : aliveSet) {
            int aliveNeighbours = 0;
            for (Point neighbour : neighbours) {
                boolean neighbourState = aliveCells.containsKey(addPoint(cell, neighbour));
                if (neighbourState) {
                    aliveNeighbours += 1;
                } else {
                   // if (!aliveCells.get(cell)) {
                        deadCellsToCheck.add(addPoint(cell, neighbour));
                   // }
                }
            }
            if (aliveNeighbours == 3 || aliveNeighbours == 2) {
                aliveCellsNextState.put(cell, true);
            }
        }
        for (Point cell : deadCellsToCheck) {
            int aliveNeighbours = 0;
            for (Point neighbour : neighbours) {
                boolean neighbourState = aliveCells.containsKey(addPoint(cell, neighbour));
                if (neighbourState) {
                    aliveNeighbours += 1;
                }
            }
            if (aliveNeighbours == 3) {
                aliveCellsNextState.put(cell, false);
            }

        }
        aliveCells = aliveCellsNextState;
        deadCellsToCheck.clear();
    }

    private Point addPoint(Point point1, Point point2){
        return new Point(point1.x + point2.x, point1.y + point2.y);
    }
}
