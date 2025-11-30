package chatty.gui.components;

import chatty.Helper;
import chatty.gui.components.AutoReplyLogStore.AutoReplyLogEntry;
import chatty.gui.components.modern.CardPanel;
import chatty.gui.components.modern.GradientPanel;
import chatty.gui.components.modern.ModernButton;
import chatty.util.DateTime;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
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
    private static final Color ACCENT = new Color(118, 93, 255);
    private static final Color ACCENT_ALT = new Color(82, 197, 255);
    private static final Color CARD_BASE = new Color(32, 36, 54, 200);
    private static final Color CARD_BASE_HEADER = new Color(40, 44, 66, 210);

    private final AutoReplyLogStore store;
    private final DefaultListModel<LogListItem> model = new DefaultListModel<>();
    private final JList<LogListItem> list = new JList<>(model);
    private final ModernButton toggleButton;
    private final ModernButton clearButton;
    private final Runnable toggleAction;
    private boolean listening;
    private int hoverIndex = -1;
    private boolean collapsed;

    public AutoReplyLogSidebar(AutoReplyLogStore store, Runnable toggleAction) {
        this.store = store;
        this.toggleAction = toggleAction;
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 12, 10));

        GradientPanel header = new GradientPanel(new Color(54, 60, 90), new Color(36, 40, 64), 18);
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel("Auto Reply Log");
        title.setBorder(new EmptyBorder(4, 2, 4, 2));
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 1f));
        header.add(title, BorderLayout.WEST);

        JPanel headerButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        headerButtons.setOpaque(false);
        toggleButton = new ModernButton("Hide Log");
        toggleButton.setToolTipText("Collapse or expand the log sidebar");
        toggleButton.addActionListener(e -> this.toggleAction.run());
        clearButton = new ModernButton("Clear");
        clearButton.setAccent(ACCENT_ALT);
        clearButton.addActionListener(e -> store.clear());
        headerButtons.add(toggleButton);
        headerButtons.add(clearButton);
        header.add(headerButtons, BorderLayout.EAST);

        list.setCellRenderer(new LogListRenderer());
        list.setFocusable(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionBackground(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 100));
        list.setSelectionForeground(Color.WHITE);
        list.setBorder(new EmptyBorder(8, 0, 8, 0));
        list.setOpaque(false);
        list.setFixedCellHeight(-1);
        installHoverListeners();

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setViewportBorder(new EmptyBorder(6, 4, 6, 4));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        setPreferredSize(new Dimension(300, 360));
    }

    private void installHoverListeners() {
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index != hoverIndex) {
                    hoverIndex = index;
                    list.repaint();
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverIndex != -1) {
                    hoverIndex = -1;
                    list.repaint();
                }
            }
        });
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        toggleButton.setText(collapsed ? "Show Log" : "Hide Log");
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

    private class LogListRenderer implements ListCellRenderer<LogListItem> {

        private final CardPanel headerPanel = new CardPanel();
        private final JLabel headerLabel = new JLabel();
        private final CardPanel entryPanel = new CardPanel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel detailLabel = new JLabel();

        LogListRenderer() {
            headerPanel.setLayout(new BorderLayout());
            headerPanel.setBorder(new EmptyBorder(10, 14, 10, 14));
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
            headerLabel.setForeground(Color.WHITE);
            headerPanel.add(headerLabel, BorderLayout.CENTER);

            entryPanel.setLayout(new BoxLayout(entryPanel, BoxLayout.Y_AXIS));
            entryPanel.setBorder(new EmptyBorder(10, 12, 10, 12));
            titleLabel.setForeground(Color.WHITE);
            detailLabel.setForeground(new Color(215, 220, 240));
            entryPanel.add(titleLabel);
            entryPanel.add(detailLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LogListItem> list, LogListItem value, int index, boolean isSelected, boolean cellHasFocus) {
            boolean hovered = index == hoverIndex && !isSelected;
            if (value.header) {
                headerLabel.setText(value.headerText);
                paintCard(headerPanel, isSelected, hovered, true);
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
                    "<html><b>%s</b> • %s<br><span style='color:#b8c0d8'>Profile: %s%s</span></html>",
                    time,
                    trigger,
                    profile,
                    channelLabel));
            detailLabel.setText(String.format("<html><span style='color:#e0e4f5'>Sent: %s</span></html>", reply));

            paintCard(entryPanel, isSelected, hovered, false);
            return entryPanel;
        }

        private void paintCard(CardPanel panel, boolean isSelected, boolean hovered, boolean header) {
            Color background = header ? CARD_BASE_HEADER : CARD_BASE;
            Color border = new Color(255, 255, 255, 50);
            if (isSelected) {
                background = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 190);
                border = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 210);
            }
            else if (hovered) {
                background = new Color(background.getRed(), background.getGreen(), background.getBlue(), 230);
                border = new Color(255, 255, 255, 120);
            }
            panel.setBackgroundColor(background);
            panel.setBorderColor(border);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(16, 18, 30), 0, getHeight(), new Color(10, 12, 20)));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }
}
