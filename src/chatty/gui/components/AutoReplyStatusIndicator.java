package chatty.gui.components;

import chatty.AutoReplyManager.AutoReplyConfig;
import chatty.AutoReplyManager.AutoReplyProfile;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

/**
 * Small status widget that exposes the current auto reply profile and allows
 * toggling the feature on or off.
 */
public class AutoReplyStatusIndicator extends JPanel {

    private final JToggleButton toggle = new JToggleButton();
    private final JLabel profileLabel = new JLabel();
    private Consumer<Boolean> toggleHandler = enabled -> { };
    private Runnable openSettingsHandler = () -> { };
    private boolean updating;

    public AutoReplyStatusIndicator() {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        setOpaque(false);

        toggle.setMargin(new Insets(2, 8, 2, 8));
        toggle.setFocusPainted(false);
        toggle.addActionListener(e -> {
            if (!updating) {
                toggleHandler.accept(toggle.isSelected());
            }
        });

        profileLabel.setOpaque(true);
        profileLabel.setBorder(new EmptyBorder(2, 10, 2, 10));
        profileLabel.setForeground(Color.WHITE);
        profileLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        profileLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openSettingsHandler.run();
            }
        });

        add(toggle);
        add(profileLabel);
        refresh(null);
    }

    public void refresh(AutoReplyConfig config) {
        updating = true;
        boolean isEnabled = config == null || config.isEnabled();
        toggle.setSelected(isEnabled);
        toggle.setText(isEnabled ? "Auto Reply On" : "Auto Reply Off");
        toggle.setToolTipText(isEnabled ? "Disable auto reply" : "Enable auto reply");

        profileLabel.setText(buildProfileText(config));
        profileLabel.setToolTipText("Click to configure auto reply");
        profileLabel.setBackground(isEnabled ? new Color(0x3C, 0x50, 0x3F) : new Color(0x55, 0x55, 0x55));
        updating = false;
    }

    public void setToggleHandler(Consumer<Boolean> handler) {
        toggleHandler = handler != null ? handler : enabled -> { };
    }

    public void setOpenSettingsHandler(Runnable handler) {
        openSettingsHandler = handler != null ? handler : () -> { };
    }

    private String buildProfileText(AutoReplyConfig config) {
        if (config == null) {
            return "Profile: none";
        }
        String activeId = config.getActiveProfileId();
        List<AutoReplyProfile> profiles = config.getProfiles();
        if (profiles.isEmpty()) {
            return "Profile: none";
        }
        for (AutoReplyProfile profile : profiles) {
            if (profile.getId().equals(activeId)) {
                return "Profile: " + profile.getName();
            }
        }
        return "Profile: " + profiles.get(0).getName();
    }
}
