package chatty.gui.components.modern;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

/**
 * Simple panel that paints a smooth gradient background with rounded corners
 * and optional overlay highlight. Used only for visual decoration.
 */
public class GradientPanel extends JPanel {

    private final Color start;
    private final Color end;
    private final int cornerRadius;
    private final boolean drawOutline;

    public GradientPanel(Color start, Color end, int cornerRadius) {
        this(start, end, cornerRadius, true);
    }

    public GradientPanel(Color start, Color end, int cornerRadius, boolean drawOutline) {
        this.start = start;
        this.end = end;
        this.cornerRadius = cornerRadius;
        this.drawOutline = drawOutline;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        g2.setPaint(new java.awt.GradientPaint(0, 0, start, 0, height, end));
        RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, width - 1, height - 1, cornerRadius, cornerRadius);
        g2.fill(shape);

        if (drawOutline) {
            g2.setColor(new Color(255, 255, 255, 80));
            g2.draw(shape);
        }

        g2.setColor(new Color(255, 255, 255, 30));
        g2.fill(new RoundRectangle2D.Float(1, 1, width - 3, Math.max(6, height / 3f), cornerRadius, cornerRadius));

        g2.dispose();
        super.paintComponent(g);
    }
}
