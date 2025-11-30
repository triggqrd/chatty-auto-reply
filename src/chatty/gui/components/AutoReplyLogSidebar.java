package chatty.gui.components;

import chatty.Helper;
import chatty.gui.components.AutoReplyLogStore.AutoReplyLogEntry;
import chatty.util.DateTime;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Sidebar widget showing recent auto-reply activity with per-day headers.
 */
public class AutoReplyLogSidebar extends JPanel implements AutoReplyLogStore.Listener {

    private static final DateTimeFormatter HEADER_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu");

    private final AutoReplyLogStore store;
    private final DefaultListModel<LogListItem> model = new DefaultListModel<>();
    private final JList<LogListItem> list = new JList<>(model);
    private boolean listening;

    public AutoReplyLogSidebar(AutoReplyLogStore store) {
        this.store = store;
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 6, 6, 6));

        JLabel title = new JLabel("Auto Reply Log");
        title.setBorder(new EmptyBorder(4, 2, 4, 2));
        header.add(title, BorderLayout.WEST);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.setOpaque(false);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> store.clear());
        controls.add(clearButton);
        header.add(controls, BorderLayout.EAST);

        list.setCellRenderer(new LogListRenderer());
        list.setFocusable(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        setPreferredSize(new Dimension(300, 360));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!listening) {
            store.addListener(this);
            listening = true;
        }
    }

    @Override
    public void removeNotify() {
        if (listening) {
            store.removeListener(this);
            listening = false;
        }
        super.removeNotify();
    }

    @Override
    public void onLogUpdated(List<AutoReplyLogEntry> entries, AutoReplyLogEntry newEntry) {
        SwingUtilities.invokeLater(() -> rebuild(entries));
    }

    private void rebuild(List<AutoReplyLogEntry> entries) {
        model.clear();
        LocalDate lastDate = null;
        for (AutoReplyLogEntry entry : entries) {
            LocalDate date = Instant.ofEpochMilli(entry.getDisplayTimeMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            if (!date.equals(lastDate)) {
                model.addElement(LogListItem.header(date.format(HEADER_FORMATTER)));
                lastDate = date;
            }
            model.addElement(LogListItem.entry(entry));
        }
        if (!model.isEmpty()) {
            int lastIndex = model.getSize() - 1;
            list.ensureIndexIsVisible(lastIndex);
            list.setSelectedIndex(lastIndex);
        }
    }

    private static class LogListItem {

        private final boolean header;
        private final String headerText;
        private final AutoReplyLogEntry entry;

        private LogListItem(boolean header, String headerText, AutoReplyLogEntry entry) {
            this.header = header;
            this.headerText = headerText;
            this.entry = entry;
        }

        static LogListItem header(String text) {
            return new LogListItem(true, text, null);
        }

        static LogListItem entry(AutoReplyLogEntry entry) {
            return new LogListItem(false, null, entry);
        }
    }

    private static class LogListRenderer implements ListCellRenderer<LogListItem> {

        private final JPanel headerPanel = new JPanel(new BorderLayout());
        private final JLabel headerLabel = new JLabel();
        private final JPanel entryPanel = new JPanel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel detailLabel = new JLabel();

        LogListRenderer() {
            headerPanel.setOpaque(true);
            headerLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
            headerPanel.add(headerLabel, BorderLayout.CENTER);

            entryPanel.setLayout(new BoxLayout(entryPanel, BoxLayout.Y_AXIS));
            entryPanel.setOpaque(true);
            entryPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
            titleLabel.setOpaque(false);
            detailLabel.setOpaque(false);
            entryPanel.add(titleLabel);
            entryPanel.add(detailLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LogListItem> list, LogListItem value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value.header) {
                headerLabel.setText(value.headerText);
                paintSelection(list, headerPanel, isSelected);
                return headerPanel;
            }

            AutoReplyLogEntry entry = value.entry;
            String time = DateTime.format(entry.getDisplayTimeMillis());
            String trigger = Helper.htmlspecialchars_encode(entry.getTrigger());
            String profile = Helper.htmlspecialchars_encode(entry.getProfile());
            String channel = Helper.htmlspecialchars_encode(entry.getChannel());
            String channelLabel = channel.isEmpty() ? "" : " • #" + channel;
            String reply = Helper.htmlspecialchars_encode(entry.getReply());

            titleLabel.setText(String.format(
                    "<html><b>%s</b> • %s<br><span style='color:#aaaaaa'>Profile: %s%s</span></html>",
                    time,
                    trigger,
                    profile,
                    channelLabel));
            detailLabel.setText(String.format("<html><span style='color:#dddddd'>Sent: %s</span></html>", reply));

            paintSelection(list, entryPanel, isSelected);
            return entryPanel;
        }

        private void paintSelection(JList<?> list, JComponent component, boolean isSelected) {
            if (isSelected) {
                component.setBackground(list.getSelectionBackground());
                component.setForeground(list.getSelectionForeground());
            }
            else {
                component.setBackground(list.getBackground());
                component.setForeground(list.getForeground());
            }
        }
    }
}
