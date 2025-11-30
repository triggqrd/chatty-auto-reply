package chatty.gui.components.modern;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Container with a modern rounded background and gradient header. All
 * components added to this panel are forwarded to the padded content area,
 * keeping existing layout code intact while upgrading visuals.
 */
public class ModernTitledPanel extends CardPanel {

    private final JPanel content;
    private boolean forwardingInternally;

    public ModernTitledPanel(String title) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(2, 2, 8, 2));

        GradientPanel header = new GradientPanel(
                new java.awt.Color(82, 92, 149, 230),
                new java.awt.Color(60, 69, 118, 230),
                14,
                false);
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel(title, SwingConstants.LEFT);
        titleLabel.setForeground(new java.awt.Color(238, 241, 255));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        header.add(titleLabel, BorderLayout.CENTER);

        content = new JPanel(new java.awt.GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(10, 12, 16, 12));

        addInternal(header, BorderLayout.NORTH);
        addInternal(content, BorderLayout.CENTER);
    }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (forwardingInternally) {
            super.addImpl(comp, constraints, index);
            return;
        }
        content.add(comp, constraints, index);
    }

    private void addInternal(Component comp, Object constraints) {
        forwardingInternally = true;
        super.add(comp, constraints);
        forwardingInternally = false;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        // Encourage a little breathing room for the rounded border
        return new Dimension(preferred.width, Math.max(preferred.height, 80));
    }
}

