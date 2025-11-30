package chatty.gui.components.modern;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

/**
 * A modern rounded button with gradient background, subtle shadow and
 * hover/pressed states. Purely visual customization without changing
 * behavior.
 */
public class ModernButton extends JButton {

    private Color accent = new Color(118, 93, 255);
    private Color accentDark = new Color(88, 63, 225);
    private Color accentLight = new Color(152, 134, 255);
    private int cornerRadius = 14;

    public ModernButton(String text) {
        super(text);
        init();
    }

    private void init() {
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setBorder(new EmptyBorder(10, 14, 10, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setForeground(Color.WHITE);
    }

    public void setAccent(Color accent) {
        this.accent = accent;
        this.accentDark = accent.darker();
        this.accentLight = accent.brighter();
    }

    public void setCornerRadius(int cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color start = accent;
        Color end = accentDark;
        if (getModel().isPressed()) {
            start = accentDark.darker();
            end = accentDark;
        }
        else if (getModel().isRollover()) {
            start = accentLight;
            end = accent;
        }

        int width = getWidth();
        int height = getHeight();

        // Shadow
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fill(new RoundRectangle2D.Float(2, 4, width - 4, height - 6, cornerRadius + 4, cornerRadius + 4));

        // Background
        g2.setPaint(new java.awt.GradientPaint(0, 0, start, 0, height, end));
        g2.fill(new RoundRectangle2D.Float(0, 0, width, height - 2, cornerRadius, cornerRadius));

        // Border
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f, width - 1.2f, height - 3, cornerRadius, cornerRadius));

        g2.dispose();
        super.paintComponent(g);
    }
}
