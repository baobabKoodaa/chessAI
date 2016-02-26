
package GUI;

import Evaluators.Evaluator;
import Evaluators.YourEvaluator;
import Framework.Main;
import Framework.Position;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public class BoardController extends JPanel implements ActionListener, MouseListener, MouseMotionListener
{
   public static final boolean DEBUG = true;
    
   public static final int BLACK = 0;
   public static final int WHITE = 1; 
    
   public boolean waitingMoveFromPlayer;
   public boolean drawSelectionAtLastClick;
   public int clickX;
   public int clickY;
   
   public int sqSize;
   public int topLeftX;
   public int topLeftY;
   
   public String[][] pieces;
   List<String[][]> positions;
   int currentPositionIndex;
   
   boolean waitingForNextPosition;
   String flashNotice;
   String gameOver;
   
   YourEvaluator debugger;
   YourEvaluator workhorse;
   
   int mode;
   public static final int MANUAL_PLAY = 1;
   public static final int BOT_DUEL = 2;
   
   boolean requestedMoveAlready;
    
    public BoardController(String arg)
    {
        addMouseListener(this);
        addMouseMotionListener(this);
        
        if (arg.equals("botDuel")) mode = BOT_DUEL;
        if (arg.equals("manualPlay")) mode = MANUAL_PLAY;
        
        debugger = new YourEvaluator();
        debugger.STATE = debugger.DEBUG;
        workhorse = new YourEvaluator();
        
        sqSize = 50;
        topLeftX = 80;
        topLeftY = 30;
        
        requestedMoveAlready = false;
        
        positions = new ArrayList<>();
        currentPositionIndex = -1;
        pieces = genStartPosition();

        nextPosition();
        repaint();
        
    }
    
    public String[][] genStartPosition() {
        // gen pawns
        String[][] pos = new String[7][7];
        pos[6][6] = "WHITE"; // turn
        for (int x=0; x<6; x++) {
            pos[x][1] = new String("bp");
            pos[x][4] = new String("wp");
        }
        // gen rooks
        pos[0][0] = new String("br");
        pos[5][0] = new String("br");
        pos[5][5] = new String("wr");
        pos[0][5] = new String("wr");
        // gen knights
        pos[1][0] = new String("bn");
        pos[4][0] = new String("bn");
        pos[4][5] = new String("wn");
        pos[1][5] = new String("wn");
        // gen queens
        pos[2][0] = new String("bq");
        pos[2][5] = new String("wq");
        // gen kings
        pos[3][0] = new String("bk");
        pos[3][5] = new String("wk");
        return pos;
    }
    
    public void addNewPosition(String[][] p) {
        positions.add(flip(p));
        if (waitingForNextPosition) {
            waitingForNextPosition = false;
            requestedMoveAlready = false;
            nextPosition();
            repaint();
        }
    }
    
    private String[][] flip(String[][] original) {
        String[][] flipped = new String[7][7];
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                flipped[x][5-y] = original[x][y];
            }
        }
        flipped[6][6] = original[6][6];
        return flipped;
    }
    
    public void printEvaluationOfCurrentPosition() {
        String[][] strGrid = flip(positions.get(currentPositionIndex));
        Position p = Position.intoPosition(strGrid);
        debugger.eval(p);
    }
    
    private String[][] copy(String[][] original) {
        String[][] copy = new String[original.length][original[0].length];
        for (int y=0; y<copy.length; y++) {
            for (int x=0; x<copy[0].length; x++) {
                copy[x][y] = original[x][y];
            }
        }
        return copy;
    }
    
    public void nextPosition() {
        if (positions.size() > 0 && currentPositionIndex < positions.size()-1) {
            pieces = copy(positions.get(++currentPositionIndex));
            if (DEBUG) printEvaluationOfCurrentPosition();
        } else {
            if (mode == BOT_DUEL) flashNotice = "Waiting for next move from computer..";
            if (mode == MANUAL_PLAY) System.out.println("Waiting for next move from computer..");
            if (gameOver != null) flashNotice = gameOver;
            waitingForNextPosition = true;
            if (mode == MANUAL_PLAY && !requestedMoveAlready && currentPositionIndex >= 0) {
                requestedMoveAlready = true;
                String[][] strGrid = flip(positions.get(currentPositionIndex));
                Position p = Position.intoPosition(strGrid);
                Main.requestMove(workhorse, p);
            }
        }
    }
    public void prevPosition() {
        if (currentPositionIndex >= 1) {
            pieces = copy(positions.get(--currentPositionIndex));
            if (DEBUG) printEvaluationOfCurrentPosition();
        } else {
            flashNotice = "Already at starting position!";
        }
    }
    public void gameOver(String result) {
        gameOver = result;
    }

    public void paintComponent(Graphics g)
    {
        // saattaa olla väärässä järjestyksessä, super.paintComp pitäis olla tod näk viimeisenä.
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g; // tätä ei käytetä?
        
        for (int i=0; i<6; i++) {
            boolean fill = (i%2 == 1 ? true : false);
            for (int j=0; j<6; j++) {
                int x = topLeftX + i*sqSize;
                int y = topLeftY + j*sqSize;
                if (fill) {
                    g.setColor(Color.gray);
                    g.fillRect(x, y, sqSize, sqSize);
                    g.setColor(Color.black);
                }
                String p = pieces[i][j];
                if (p != null) {
                    g.drawImage(givImage(p), x+3, y+3, this);
                }
                g.drawRect(x, y, sqSize, sqSize);
                fill = (fill ? false : true);
            }
        }

        if (drawSelectionAtLastClick) {
            int x = ((clickX - topLeftX) / sqSize) * sqSize + topLeftX;
            int y = ((clickY - topLeftY) / sqSize) * sqSize + topLeftY;
            g.setColor(Color.yellow);
            g.drawRect(x, y, sqSize, sqSize);
            g.drawRect(x+1, y+1, sqSize, sqSize);
            g.setColor(Color.black);
        }
        
        if (flashNotice != null) {
            g.drawChars(flashNotice.toCharArray(), 0, flashNotice.length(), 160, 450);
            flashNotice = null;
        }
        
        g.drawImage(givImage("arrows"), 105, 256, this);

    }
    
    private Image givImage(String piece) {
        try {
            File input =                 new File("src/images/" + piece + ".svg.png");
            if (!input.exists()) input = new File("src/images/" + piece + ".png");
            Image img = ImageIO.read(input);
            return img;
        } catch (Exception e) {
            System.out.println("nullaa");
            return null;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
        int prevX = clickX;
        int prevY = clickY;
        clickX = e.getX();
        clickY = e.getY();
        if (mode == MANUAL_PLAY) {
            int a = (prevX - topLeftX) / sqSize;
            int b = (prevY - topLeftY) / sqSize;
            int i = (clickX - topLeftX) / sqSize;
            int j = (clickY - topLeftY) / sqSize;
            if (!targetingSameSquare(a,b,i,j)) {
                if (insideChessGrid(a,b,i,j)) {                    
                    /** Erase future from the previous timeline, if there was any */
                    while (positions.size() > currentPositionIndex+1) {
                        positions.remove(currentPositionIndex+1);
                    }
                    /** Update position */
                    pieces[i][j] = pieces[a][b];
                    pieces[a][b] = null;
                    pieces[6][6] = (pieces[6][6].equals("WHITE") ? "BLACK" : "WHITE");
                    
                    /** Save new position */
                    positions.add(copy(pieces));
                    nextPosition();
                    
                    clickX = 0;
                    clickY = 0;
                    drawSelectionAtLastClick = false;
                } else if (insideChessGrid(i,j)) {
                    drawSelectionAtLastClick = true;
                }
            } else if (insideChessGrid(a,b,i,j)) {
                if (!drawSelectionAtLastClick) drawSelectionAtLastClick = true;
                else {
                    drawSelectionAtLastClick = false;
                    clickX = 1;
                    clickY = 1;
                }
            }
        }
            
        if (clickY > 340 && clickY < 424) {
            if (clickX < 234 && clickX > 126) {
                prevPosition();
            } else if (clickX > 234 && clickX < 339) {
                nextPosition();
            }

        }
        
        repaint();
    }
    
    private boolean insideChessGrid(int... values) {
        for (int v : values) {
            if (v < 0 || v > 5) return false;
        }
        return true;
    }
    
    private boolean targetingSameSquare(int a, int b, int i, int j) {
        return (a == i && b == j);
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseEntered(MouseEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseExited(MouseEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mouseMoved(MouseEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}