package chatty.gui.components.modern;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;

/**
 * Rounded border with slight shadow for text components and panels.
 */
public class RoundedBorder extends AbstractBorder {

    private final int radius;
    private final Color borderColor;
    private final Color shadowColor;

    public RoundedBorder(int radius, Color borderColor, Color shadowColor) {
        this.radius = radius;
        this.borderColor = borderColor;
        this.shadowColor = shadowColor;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D outline = new RoundRectangle2D.Float(x + 1, y + 1, width - 3, height - 3, radius, radius);
        g2.setColor(shadowColor);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(outline);

        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, radius, radius));
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(radius / 3, radius / 3, radius / 3, radius / 3);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.right = insets.top = insets.bottom = radius / 3;
        return insets;
    }
}
