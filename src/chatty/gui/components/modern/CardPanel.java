package chatty.gui.components.modern;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

/**
 * Rounded panel with soft shadow used for list cells and containers.
 */
public class CardPanel extends JPanel {

    private Color backgroundColor = new Color(28, 32, 48, 230);
    private Color borderColor = new Color(255, 255, 255, 40);
    private int radius = 14;

    public CardPanel() {
        setOpaque(false);
    }

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
    }

    public void setBorderColor(Color color) {
        this.borderColor = color;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        g2.setColor(new Color(0, 0, 0, 35));
        g2.fill(new RoundRectangle2D.Float(3, 4, width - 6, height - 6, radius + 6, radius + 6));

        g2.setColor(backgroundColor);
        g2.fill(new RoundRectangle2D.Float(0, 0, width - 1, height - 1, radius, radius));

        g2.setColor(borderColor);
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 2, height - 2, radius, radius));

        g2.dispose();
        super.paintComponent(g);
    }
}
