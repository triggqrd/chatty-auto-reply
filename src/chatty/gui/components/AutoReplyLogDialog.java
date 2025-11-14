package chatty.gui.components;

import chatty.AutoReplyEvent;
import chatty.AutoReplyService;
import chatty.gui.DockedDialogHelper;
import chatty.gui.DockedDialogManager;
import chatty.gui.MainGui;
import chatty.util.DateTime;
import chatty.util.dnd.DockContent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

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
        list.setCellRenderer(new DefaultListCellRenderer());

        JScrollPane scroll = new JScrollPane(list);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.getVerticalScrollBar().setUnitIncrement(20);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(null);
        mainPanel.add(scroll);
        scroll.setBounds(0, 0, 400, 300);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> data.clear());
        mainPanel.add(clearButton);

        // Will be positioned by DockedDialogHelper
        add(mainPanel);

        DockContent content = dockedDialogs.createStyledContent(mainPanel, "Auto Reply Log", "-autoreplylog-");
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
        mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control L"), "clear");
        mainPanel.getActionMap().put("clear", new AbstractAction() {
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
