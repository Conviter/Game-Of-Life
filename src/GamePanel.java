import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseMotionListener, MouseListener, MouseWheelListener{
    int screenWidth = 0; //768 pixels
    int screenHeight = 0; //576 pixels
    final int FPS = 60;
    Timer timer;

    int gameWidth;
    int gameHeight;
    Point cameraPosition = new Point(0, 0);
    boolean drawGrid = true;
    int cellSize;
    Game game;
    Point oldPosition;
    boolean dragging;
    Thread gameThread;
    Color gridColorZoomedIn = new Color(150, 150, 150, 40);
    Color gridColorZoomedOut = new Color(150, 150, 150, 20);
    boolean painting = false;
    int paintSize = 2;
    double density;
    boolean isPainting = false;
    Set<Point> cellsPainted = new HashSet<>();

    public GamePanel(int screenWidth, int screenHeight, int cellSize, int startingCells, boolean drawGrid) {
        this.drawGrid = drawGrid;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.cellSize = cellSize;
        this.gameHeight = screenHeight / 2;
        this.gameWidth = screenWidth / 2;
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
        this.addKeyListener(this);
        this.setFocusable(true);
        requestFocusInWindow();
        game = new Game(startingCells, gameWidth, gameHeight, cellSize);

        this.addMouseMotionListener(this);
        this.addMouseListener(this);
        this.addMouseWheelListener(this);
    }

    public void startGameThreat(){
        gameThread = new Thread(this);
        gameThread.start();

    }


    public void updateDensity(int density){
        this.density = (double) density / 100;
    }
    public void updateTimer(int time){
        if (time == 0){
            timer.stop();
        } else {
            if (!timer.isRunning()){
                timer.start();
            }
            timer.setDelay(1000 / time);
        }

    }

    public void updateCellSize(int size){
        if(size == 0) return;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        double worldX = (centerX - cameraPosition.x) / (double) cellSize;
        double worldY = (centerY - cameraPosition.y) / (double) cellSize;
        this.cellSize = size;
        cameraPosition.x = (int)(centerX - worldX * cellSize);
        cameraPosition.y = (int)(centerY - worldY * cellSize);
    }

    @Override
    public void run() {
        this.timer = new Timer(100, e -> {
            game.applyRules();
            repaint();
        });
        timer.start();
    }

    private void drawGrid(Graphics g){
        if (cellSize < 3){
            g.setColor(gridColorZoomedOut);
        } else {
            g.setColor(gridColorZoomedIn);
        }


        // Find where the first vertical line should start
        int startX = cameraPosition.x % cellSize;
        if (startX < 0) startX += cellSize;

        int startY = cameraPosition.y % cellSize;
        if (startY < 0) startY += cellSize;

        // Draw vertical lines
        for (int x = startX; x < screenWidth; x += cellSize) {
            g.drawLine(x, 0, x, screenHeight);
        }

        // Draw horizontal lines
        for (int y = startY; y < screenHeight; y += cellSize) {
            g.drawLine(0, y, screenWidth, y);
        }
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.gray);
        for (Point cell : game.aliveCells.keySet()) {
            g.fillRect((cell.x * cellSize) + cameraPosition.x, (cell.y * cellSize) + cameraPosition.y, cellSize, cellSize);
        }
        if (drawGrid) {
            drawGrid(g);
        }

    }

    public void updatePaintSize(int size){
        paintSize = size;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        int speed = 20;
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_LEFT:
                cameraPosition.x += speed;
                break;
            case KeyEvent.VK_RIGHT:
                cameraPosition.x -= speed;
                break;
            case KeyEvent.VK_UP:
                cameraPosition.y += speed;
                break;
            case KeyEvent.VK_DOWN:
                cameraPosition.y -= speed;
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (painting && e.getButton() == MouseEvent.BUTTON1) {
            isPainting = true;
            Point position = e.getPoint();

            Graphics g = getGraphics();


            position.x = (position.x / cellSize);
            position.y = (position.y / cellSize);
            position.x -= cameraPosition.x/2;
            position.y -= cameraPosition.y/2;
            //game.aliveCells.put(position, false);

            for (int i = -paintSize; i <= paintSize; i++){
                for (int j = -paintSize; j <= paintSize; j++){
                    game.spawnCell(new Point(position.x + i, position.y + j));
                    //game.aliveCells.put(new Point(position.x + i, position.y + j), false);
                }
            }
            repaint();
        } else {
            dragging = true;
            oldPosition = e.getPoint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        isPainting = false;
        cellsPainted.clear();
        dragging = false;
        oldPosition = null;
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragging){
            Point newPosition = e.getPoint();
            int newX = newPosition.x - oldPosition.x;
            int newY = newPosition.y - oldPosition.y;
            cameraPosition.x += newX;
            cameraPosition.y += newY;

            oldPosition = newPosition;
            repaint();
        }else if (painting){
            Point position = e.getPoint();


            position.x = (position.x / cellSize);
            position.y = (position.y / cellSize);
            position.x -= cameraPosition.x / 2;
            position.y -= cameraPosition.y / 2;
            //game.aliveCells.put(position, false);
            Random rand = new Random();
            for (int i = -paintSize; i <= paintSize; i++) {
                for (int j = -paintSize; j <= paintSize; j++) {
                    if (!cellsPainted.contains(new Point(position.x + i, position.y + j))) {
                        cellsPainted.add(new Point(position.x + i, position.y + j));
                        if (rand.nextDouble() <= density) {
                            game.spawnCell(new Point(position.x + i, position.y + j));
                            //game.aliveCells.put(new Point(position.x + i, position.y + j), false);
                        }
                    }
                }
            }
            repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        //e.getScrollAmount();
        updateCellSize(cellSize+e.getWheelRotation());
        repaint();
    }
}
