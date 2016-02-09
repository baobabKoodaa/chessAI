
package GUI;

import java.awt.Color;
import javax.swing.JFrame;

public class MainFrame {
    public JFrame f;
    public BoardController c;

    public MainFrame() {
        f = new JFrame();
        c = new BoardController();
        c.setBackground(Color.white);
        f.add(c);
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(500, 500);
        f.setLocationRelativeTo(null);
        
    }

}