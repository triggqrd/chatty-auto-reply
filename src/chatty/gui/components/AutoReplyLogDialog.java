package chatty.gui.components;

import chatty.AutoReplyEvent;
import chatty.AutoReplyService;
import chatty.gui.DockedDialogHelper;
import chatty.gui.DockedDialogManager;
import chatty.gui.MainGui;
import chatty.gui.components.modern.CardPanel;
import chatty.gui.components.modern.GradientPanel;
import chatty.gui.components.modern.ModernButton;
import chatty.util.DateTime;
import chatty.util.dnd.DockContent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Docked dialog that logs auto-reply events.
 */
public class AutoReplyLogDialog extends JDialog implements AutoReplyService.Listener {

    private static final int MAX_ENTRIES = 100;

    private final DockedDialogHelper helper;
    private final JList<String> list;
    private final DefaultListModel<String> data;

    public AutoReplyLogDialog(MainGui main, DockedDialogManager dockedDialogs) {
        super(main);
        setTitle("Auto Reply Log");

        list = new JList<String>() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        data = new DefaultListModel<>();
        list.setModel(data);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(new Color(24, 26, 39));
        list.setForeground(new Color(229, 233, 245));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(10, 12, 10, 12));
                label.setOpaque(true);
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                if (isSelected) {
                    label.setBackground(new Color(118, 93, 255, 150));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(new Color(255, 255, 255, 12));
                    label.setForeground(new Color(220, 224, 236));
                }
                return label;
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        GradientPanel header = new GradientPanel(
                new Color(92, 101, 158, 240),
                new Color(65, 73, 128, 230),
                16,
                false);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        header.setLayout(new BorderLayout());

        JLabel title = new JLabel("Auto Reply Log");
        title.setForeground(new Color(238, 241, 255));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 1f));
        header.add(title, BorderLayout.CENTER);

        ModernButton clearButton = new ModernButton("Clear");
        clearButton.setAccent(new Color(118, 93, 255));
        clearButton.addActionListener(e -> data.clear());
        header.add(clearButton, BorderLayout.EAST);

        CardPanel logContainer = new CardPanel();
        logContainer.setBackgroundColor(new Color(26, 28, 44, 235));
        logContainer.setBorder(new EmptyBorder(10, 12, 12, 12));
        logContainer.setLayout(new BorderLayout(0, 10));
        logContainer.add(header, BorderLayout.NORTH);
        logContainer.add(scroll, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.add(logContainer, BorderLayout.CENTER);

        GradientPanel background = new GradientPanel(
                new Color(35, 38, 56),
                new Color(19, 20, 35),
                18,
                false);
        background.setLayout(new BorderLayout(10, 10));
        background.setBorder(new EmptyBorder(12, 12, 12, 12));
        background.add(mainPanel, BorderLayout.CENTER);

        add(background);

        DockContent content = dockedDialogs.createStyledContent(background, "Auto Reply Log", "-autoreplylog-");
        helper = dockedDialogs.createHelper(new DockedDialogHelper.DockedDialog() {
            @Override
            public void setVisible(boolean visible) {
                AutoReplyLogDialog.super.setVisible(visible);
            }

            @Override
            public boolean isVisible() {
                return AutoReplyLogDialog.super.isVisible();
            }

            @Override
            public void addComponent(Component comp) {
                // Component already added in constructor
            }

            @Override
            public void removeComponent(Component comp) {
                // Handled by dialog
            }

            @Override
            public Window getWindow() {
                return AutoReplyLogDialog.this;
            }

            @Override
            public DockContent getContent() {
                return content;
            }
        });

        // Keyboard shortcut to clear (Ctrl+L)
        background.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control L"), "clear");
        background.getActionMap().put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                data.clear();
            }
        });

        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setSize(new Dimension(500, 350));
    }

    @Override
    public void autoReplySent(AutoReplyEvent event) {
        SwingUtilities.invokeLater(() -> {
            String entry = formatEntry(event);
            data.addElement(entry);

            // Keep list size manageable
            while (data.getSize() > MAX_ENTRIES) {
                data.removeElementAt(0);
            }

            // Scroll to newest entry
            list.setSelectedIndex(data.getSize() - 1);
        });
    }

    private String formatEntry(AutoReplyEvent event) {
        String time = DateTime.format(event.getSentAtMillis());
        long delay = event.getDelayMillis();

        return String.format(
                "[%s] profile=\"%s\" trigger=\"%s\" user=%s channel=%s delay=%d ms",
                time,
                event.getProfileName(),
                event.getTriggerName(),
                event.getUser(),
                event.getChannel(),
                delay
        );
    }

    public void clear() {
        data.clear();
    }
}
