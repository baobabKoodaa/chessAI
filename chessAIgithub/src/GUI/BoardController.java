
package GUI;

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
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public class BoardController extends JPanel implements ActionListener, MouseListener, MouseMotionListener
{
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
    
    public BoardController()
    {
        addMouseListener(this);
        addMouseMotionListener(this);
        
        sqSize = 50;
        topLeftX = 80;
        topLeftY = 30;
        
        positions = new ArrayList<>();
        positions.add(genStartPosition());
        currentPositionIndex = -1;
        nextPosition();
        repaint();
    }
    
    public String[][] genStartPosition() {
        // gen pawns
        String[][] pieces = new String[6][6];
        for (int x=0; x<6; x++) {
            pieces[x][1] = new String("bp");
            pieces[x][4] = new String("wp");
        }
        // gen rooks
        pieces[0][0] = new String("br");
        pieces[5][0] = new String("br");
        pieces[5][5] = new String("wr");
        pieces[0][5] = new String("wr");
        // gen knights
        pieces[1][0] = new String("bn");
        pieces[4][0] = new String("bn");
        pieces[4][5] = new String("wn");
        pieces[1][5] = new String("wn");
        // gen queens
        pieces[2][0] = new String("bq");
        pieces[2][5] = new String("wq");
        // gen kings
        pieces[3][0] = new String("bk");
        pieces[3][5] = new String("wk");
        return pieces;
    }
    
    public void addNewPosition(String[][] p) {
        positions.add(p);
        if (waitingForNextPosition) {
            waitingForNextPosition = false;
            nextPosition();
            repaint();
        }
    }
    
    public void nextPosition() {
        if (currentPositionIndex < positions.size()-1) {
            pieces = positions.get(++currentPositionIndex);
        } else {
            flashNotice = "Waiting for next move..";
            if (gameOver != null) flashNotice = gameOver;
            waitingForNextPosition = true;
        }
    }
    public void prevPosition() {
        if (currentPositionIndex >= 1) {
            pieces = positions.get(--currentPositionIndex);
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
        if (waitingMoveFromPlayer) {
            int a = (prevX - topLeftX) / sqSize;
            int b = (prevY - topLeftY) / sqSize;
            int i = (clickX - topLeftX) / sqSize;
            int j = (clickY - topLeftY) / sqSize;
            if (!targetingSameSquare(a,b,i,j)) {
                if (insideChessGrid(a,b,i,j)) {
                    pieces[i][j] = pieces[a][b];
                    pieces[a][b] = null;
                    clickX = 0;
                    clickY = 0;
                    drawSelectionAtLastClick = false;
                } else if (insideChessGrid(i,j)) {
                    drawSelectionAtLastClick = true;
                }
            } else {
                drawSelectionAtLastClick = (drawSelectionAtLastClick == true ? false : true);
            }
        }
        if (clickY > 346 && clickY < 424) {
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