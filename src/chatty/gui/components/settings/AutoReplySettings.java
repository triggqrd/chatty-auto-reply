package chatty.gui.components.settings;

import chatty.AutoReplyManager;
import chatty.AutoReplyManager.AutoReplyConfig;
import chatty.AutoReplyManager.AutoReplyProfile;
import chatty.AutoReplyManager.AutoReplyTrigger;
import chatty.AutoReplyManager.PatternType;
import chatty.AutoReplyManager.ReplySelection;
import chatty.gui.GuiUtil;
import chatty.lang.Language;
import chatty.util.StringUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;

/**
 * Settings panel for configuring auto reply profiles and triggers.
 */
public class AutoReplySettings extends SettingsPanel {

    private final SettingsDialog dialog;

    private AutoReplyConfig config = new AutoReplyConfig();

    private final DefaultListModel<AutoReplyProfile> profileModel = new DefaultListModel<>();
    private final JList<AutoReplyProfile> profileList = new JList<>(profileModel);

    private final JComboBox<ActiveProfileItem> activeProfileCombo = new JComboBox<>();
    private final JSpinner globalCooldownSpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));
    private final JCheckBox selfIgnoreCheck = new JCheckBox(Language.getString("settings.autoReply.selfIgnore"));
    private final JCheckBox defaultNotificationCheck = new JCheckBox(Language.getString("settings.autoReply.defaultNotification"));
    private final JComboBox<String> defaultSoundCombo = new JComboBox<>(new String[]{"", "off", "ding.wav"});
    private final JPanel triggerEditorsPanel = new JPanel();
    private final List<TriggerEditorPanel> triggerEditors = new ArrayList<>();
    private final JLabel validationLabel = new JLabel();

    public AutoReplySettings(SettingsDialog dialog) {
        super(true);
        this.dialog = dialog;
        buildUi();
        installListeners();
        updateActiveProfileCombo();
        validationLabel.setForeground(Color.GRAY);
        validationLabel.setText(Language.getString("settings.autoReply.triggers.help"));
    }

    private void buildUi() {
        JPanel globalPanel = addTitledPanel(Language.getString("settings.section.autoReplyGlobal"), 0);
        globalPanel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        globalPanel.add(new JLabel(Language.getString("settings.autoReply.activeProfile")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        activeProfileCombo.setRenderer(new ActiveProfileRenderer());
        globalPanel.add(activeProfileCombo, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        globalPanel.add(new JLabel(Language.getString("settings.autoReply.globalCooldown")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        globalCooldownSpinner.setPreferredSize(new Dimension(120, globalCooldownSpinner.getPreferredSize().height));
        globalPanel.add(globalCooldownSpinner, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        globalPanel.add(selfIgnoreCheck, gbc);

        gbc.gridy++;
        globalPanel.add(defaultNotificationCheck, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        globalPanel.add(new JLabel(Language.getString("settings.autoReply.defaultSound")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        defaultSoundCombo.setEditable(true);
        defaultSoundCombo.setRenderer(new SoundRenderer());
        globalPanel.add(defaultSoundCombo, gbc);

        GridBagConstraints containerGbc = getGbc(1);
        containerGbc.fill = GridBagConstraints.BOTH;
        containerGbc.weighty = 1;
        containerGbc.insets = new Insets(10, 7, 4, 7);
        JPanel content = new JPanel(new GridBagLayout());
        addPanel(content, containerGbc);

        JPanel profilesPanel = createProfilesPanel();
        JPanel triggersPanel = createTriggersPanel();

        GridBagConstraints contentConstraints = new GridBagConstraints();
        // Give the profiles panel a smaller default portion of horizontal space so triggers get more room
        contentConstraints.insets = new Insets(0, 0, 0, 5);
        contentConstraints.gridx = 0;
        contentConstraints.gridy = 0;
        contentConstraints.fill = GridBagConstraints.BOTH;
        // Favor triggers panel by default while still allowing resizing
        contentConstraints.weightx = 0.22;
        contentConstraints.weighty = 1;
        content.add(profilesPanel, contentConstraints);

        contentConstraints.insets = new Insets(0, 5, 0, 0);
        contentConstraints.gridx = 1;
        contentConstraints.weightx = 0.78;
        content.add(triggersPanel, contentConstraints);
    }

    private JPanel createProfilesPanel() {
        JPanel panel = createTitledPanel(Language.getString("settings.section.autoReplyProfiles"));
        panel.setLayout(new GridBagLayout());

        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setCellRenderer(new ProfileRenderer());
        profileList.setVisibleRowCount(10);
        JScrollPane scroll = new JScrollPane(profileList);
        // Narrower default width but still resizable with the container
        scroll.setPreferredSize(new Dimension(140, 220));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        panel.add(scroll, gbc);

        JPanel buttons = new JPanel();
        JButton add = new JButton(Language.getString("settings.autoReply.profiles.add"));
        GuiUtil.smallButtonInsets(add);
        add.addActionListener(e -> addProfile());
        buttons.add(add);

        JButton rename = new JButton(Language.getString("settings.autoReply.profiles.rename"));
        GuiUtil.smallButtonInsets(rename);
        rename.addActionListener(e -> renameProfile());
        buttons.add(rename);

        JButton duplicate = new JButton(Language.getString("settings.autoReply.profiles.duplicate"));
        GuiUtil.smallButtonInsets(duplicate);
        duplicate.addActionListener(e -> duplicateProfile());
        buttons.add(duplicate);

        JButton delete = new JButton(Language.getString("settings.autoReply.profiles.delete"));
        GuiUtil.smallButtonInsets(delete);
        delete.addActionListener(e -> deleteProfile());
        buttons.add(delete);

        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        panel.add(buttons, gbc);

        JLabel help = new JLabel(Language.getString("settings.autoReply.profiles.help"));
        help.setForeground(Color.GRAY);
        gbc.gridy = 2;
        panel.add(help, gbc);

        // Prefer a modest minimum width so it doesn't dominate the layout
        panel.setMinimumSize(new Dimension(120, 100));
        panel.setPreferredSize(new Dimension(160, 300));

        return panel;
    }

    private JPanel createTriggersPanel() {
        JPanel panel = createTitledPanel(Language.getString("settings.section.autoReplyTriggers"));
        panel.setLayout(new GridBagLayout());

        triggerEditorsPanel.setLayout(new BoxLayout(triggerEditorsPanel, BoxLayout.Y_AXIS));
        triggerEditorsPanel.setOpaque(false);
        JScrollPane triggerScroll = new JScrollPane(triggerEditorsPanel);
        // Slightly larger preferred size for the triggers area; actual layout will favor this panel
        triggerScroll.setPreferredSize(new Dimension(520, 260));
        triggerScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Make mouse wheel scroll more responsive: larger unit increment and a sensible block increment
        triggerScroll.getVerticalScrollBar().setUnitIncrement(24);
        triggerScroll.getVerticalScrollBar().setBlockIncrement( Math.max(100, triggerScroll.getViewport().getViewRect().height - 20) );
        // Update block increment on viewport resize so page-scroll behaves consistently
        triggerScroll.getViewport().addChangeListener(e -> {
            int h = triggerScroll.getViewport().getViewRect().height;
            triggerScroll.getVerticalScrollBar().setBlockIncrement(Math.max(100, h - 20));
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(triggerScroll, gbc);

        JPanel buttons = new JPanel();
        JButton add = new JButton(Language.getString("settings.autoReply.triggers.add"));
        GuiUtil.smallButtonInsets(add);
        add.addActionListener(e -> addTrigger());
        buttons.add(add);

        gbc.gridy++;
        gbc.weighty = 0;
        gbc.insets = new Insets(8, 0, 0, 0);
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(buttons, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 0, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        validationLabel.setForeground(Color.GRAY);
        panel.add(validationLabel, gbc);

        return panel;
    }

    private void installListeners() {
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showProfile(profileList.getSelectedValue());
            }
        });

        defaultSoundCombo.addActionListener(e -> updateDefaultSound());
        installComboEditorListener(defaultSoundCombo, this::updateDefaultSound);

        globalCooldownSpinner.addChangeListener(e -> config.setGlobalCooldown(((Number) globalCooldownSpinner.getValue()).longValue()));
        selfIgnoreCheck.addActionListener(e -> config.setSelfIgnore(selfIgnoreCheck.isSelected()));
        defaultNotificationCheck.addActionListener(e -> config.setDefaultNotification(defaultNotificationCheck.isSelected()));
        activeProfileCombo.addActionListener(e -> {
            ActiveProfileItem item = (ActiveProfileItem) activeProfileCombo.getSelectedItem();
            if (item != null) {
                config.setActiveProfileId(item.id);
                profileList.repaint();
                refreshValidation();
            }
        });
    }

    private void installComboEditorListener(JComboBox<String> combo, Runnable action) {
        Component editorComponent = combo.getEditor().getEditorComponent();
        if (editorComponent instanceof JTextField) {
            ((JTextField) editorComponent).getDocument().addDocumentListener(documentListener(action));
        }
    }

    private DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    public void setData(AutoReplyConfig config) {
        this.config = config.copy();
        profileModel.clear();
        for (AutoReplyProfile profile : this.config.getProfiles()) {
            profileModel.addElement(profile);
        }
        if (!profileModel.isEmpty()) {
            int index = 0;
            String activeId = this.config.getActiveProfileId();
            for (int i = 0; i < profileModel.size(); i++) {
                if (profileModel.getElementAt(i).getId().equals(activeId)) {
                    index = i;
                    break;
                }
            }
            profileList.setSelectedIndex(index);
        } else {
            showProfile(null);
        }
        updateActiveProfileCombo();
        globalCooldownSpinner.setValue(Long.valueOf(this.config.getGlobalCooldown()));
        selfIgnoreCheck.setSelected(this.config.isSelfIgnore());
        defaultNotificationCheck.setSelected(this.config.isDefaultNotification());
        String defaultSound = this.config.getDefaultSound();
        defaultSoundCombo.setSelectedItem(defaultSound == null ? "" : defaultSound);
    }

    public AutoReplyConfig getData() {
        updateDefaultSound();
        config.setGlobalCooldown(((Number) globalCooldownSpinner.getValue()).longValue());
        config.setSelfIgnore(selfIgnoreCheck.isSelected());
        config.setDefaultNotification(defaultNotificationCheck.isSelected());
        ActiveProfileItem active = (ActiveProfileItem) activeProfileCombo.getSelectedItem();
        if (active != null) {
            config.setActiveProfileId(active.id);
        }
        return config.copy();
    }

    private void addProfile() {
        String name = promptProfileName(null);
        if (name == null) {
            return;
        }
        AutoReplyProfile profile = AutoReplyProfile.create(name);
        config.getProfiles().add(profile);
        profileModel.addElement(profile);
        profileList.setSelectedValue(profile, true);
        updateActiveProfileCombo();
    }

    private void renameProfile() {
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (profile == null) {
            return;
        }
        String name = promptProfileName(profile.getName());
        if (name == null) {
            return;
        }
        profile.setName(name);
        profileList.repaint();
        updateActiveProfileCombo();
    }

    private void duplicateProfile() {
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (profile == null) {
            return;
        }
        AutoReplyProfile copy = profile.duplicate();
        copy.setName(profile.getName() + " " + Language.getString("settings.autoReply.profile.copySuffix"));
        config.getProfiles().add(copy);
        profileModel.addElement(copy);
        profileList.setSelectedValue(copy, true);
        updateActiveProfileCombo();
    }

    private void deleteProfile() {
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (profile == null) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(dialog,
                String.format(Language.getString("settings.autoReply.profiles.delete.confirm"), profile.getName()),
                Language.getString("settings.autoReply.profiles.delete"),
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        config.getProfiles().remove(profile);
        profileModel.removeElement(profile);
        if (Objects.equals(config.getActiveProfileId(), profile.getId())) {
            config.setActiveProfileId("default");
        }
        updateActiveProfileCombo();
        if (!profileModel.isEmpty()) {
            profileList.setSelectedIndex(0);
        } else {
            showProfile(null);
        }
    }

    private void addTrigger() {
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (profile == null) {
            return;
        }
        AutoReplyTrigger trigger = AutoReplyTrigger.create();
        trigger.setNotificationEnabled(config.isDefaultNotification());
        trigger.setSound(config.getDefaultSound());
        profile.addTrigger(trigger);
        addTriggerEditor(profile, trigger);
        triggerEditorsPanel.revalidate();
        triggerEditorsPanel.repaint();
        refreshValidation();
    }

    private void addTriggerEditor(AutoReplyProfile profile, AutoReplyTrigger trigger) {
        TriggerEditorPanel editor = new TriggerEditorPanel(profile, trigger);
        triggerEditors.add(editor);
        triggerEditorsPanel.add(editor);
    }

    private void duplicateTrigger(TriggerEditorPanel editor) {
        AutoReplyTrigger copy = editor.trigger.copyWithNewId();
        editor.profile.addTrigger(copy);
        addTriggerEditor(editor.profile, copy);
        triggerEditorsPanel.revalidate();
        triggerEditorsPanel.repaint();
        refreshValidation();
    }

    private void removeTrigger(TriggerEditorPanel editor) {
        String label = StringUtil.isNullOrEmpty(editor.trigger.getPattern())
                ? Language.getString("settings.autoReply.trigger.unnamed")
                : editor.trigger.getPattern();
        int result = JOptionPane.showConfirmDialog(dialog,
                String.format(Language.getString("settings.autoReply.triggers.delete.confirm"), label),
                Language.getString("settings.autoReply.triggers.delete"),
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        editor.profile.getTriggers().remove(editor.trigger);
        triggerEditors.remove(editor);
        triggerEditorsPanel.remove(editor);
        triggerEditorsPanel.revalidate();
        triggerEditorsPanel.repaint();
        refreshValidation();
    }

    private void showProfile(AutoReplyProfile profile) {
        triggerEditors.clear();
        triggerEditorsPanel.removeAll();
        if (profile != null) {
            for (AutoReplyTrigger trigger : profile.getTriggers()) {
                addTriggerEditor(profile, trigger);
            }
        }
        triggerEditorsPanel.revalidate();
        triggerEditorsPanel.repaint();
        refreshValidation();
    }

    private void refreshValidation() {
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (profile == null) {
            validationLabel.setForeground(Color.GRAY);
            validationLabel.setText(Language.getString("settings.autoReply.triggers.help"));
            return;
        }
        for (AutoReplyTrigger trigger : profile.getTriggers()) {
            String error = validateTrigger(trigger);
            if (error != null) {
                validationLabel.setForeground(Color.RED);
                validationLabel.setText(error);
                return;
            }
        }
        validationLabel.setForeground(Color.GRAY);
        validationLabel.setText(Language.getString("settings.autoReply.triggers.help"));
    }

    private String validateTrigger(AutoReplyTrigger trigger) {
        if (trigger == null || !trigger.isEnabled()) {
            return null;
        }
        if (StringUtil.isNullOrEmpty(trigger.getPattern())) {
            return Language.getString("settings.autoReply.validation.pattern");
        }
        if (StringUtil.isNullOrEmpty(trigger.getReply())) {
            return Language.getString("settings.autoReply.validation.reply");
        }
        if (trigger.getMaxDelayMillis() < trigger.getMinDelayMillis()) {
            return Language.getString("settings.autoReply.validation.delay");
        }
        long minUsers = trigger.getMinUniqueUsers();
        long minMentions = trigger.getMinMentionsPerUser();
        long window = trigger.getTimeWindowSec();
        if ((minUsers > 0 || minMentions > 0) && window == 0) {
            return Language.getString("settings.autoReply.validation.thresholds");
        }
        return null;
    }

    private void updateDefaultSound() {
        if (config == null) {
            return;
        }
        String value = getComboValue(defaultSoundCombo);
        config.setDefaultSound(StringUtil.isNullOrEmpty(value) ? null : value);
    }

    private void updateActiveProfileCombo() {
        String selectedId = config != null ? config.getActiveProfileId() : null;
        ActiveProfileItem current = (ActiveProfileItem) activeProfileCombo.getSelectedItem();
        if (StringUtil.isNullOrEmpty(selectedId) && current != null) {
            selectedId = current.id;
        }
        DefaultComboBoxModel<ActiveProfileItem> model = new DefaultComboBoxModel<>();
        model.addElement(new ActiveProfileItem("default", Language.getString("settings.autoReply.profile.default")));
        for (AutoReplyProfile profile : config.getProfiles()) {
            model.addElement(new ActiveProfileItem(profile.getId(), profile.getName()));
        }
        activeProfileCombo.setModel(model);
        selectActiveProfile(selectedId);
    }

    private void selectActiveProfile(String id) {
        for (int i = 0; i < activeProfileCombo.getItemCount(); i++) {
            ActiveProfileItem item = activeProfileCombo.getItemAt(i);
            if (item.id.equals(id)) {
                activeProfileCombo.setSelectedIndex(i);
                config.setActiveProfileId(item.id);
                profileList.repaint();
                return;
            }
        }
        if (activeProfileCombo.getItemCount() > 1) {
            ActiveProfileItem fallback = activeProfileCombo.getItemAt(1);
            activeProfileCombo.setSelectedIndex(1);
            config.setActiveProfileId(fallback.id);
        }
        else {
            activeProfileCombo.setSelectedIndex(0);
            config.setActiveProfileId("default");
        }
        profileList.repaint();
    }

    private String promptProfileName(String initial) {
        while (true) {
            String input = (String) JOptionPane.showInputDialog(dialog,
                    Language.getString("settings.autoReply.prompt.profileName"),
                    Language.getString("settings.autoReply.profileName"),
                    JOptionPane.PLAIN_MESSAGE, null, null, initial);
            if (input == null) {
                return null;
            }
            input = input.trim();
            if (!input.isEmpty()) {
                return input;
            }
            JOptionPane.showMessageDialog(dialog,
                    Language.getString("settings.autoReply.error.nameRequired"),
                    Language.getString("settings.autoReply.profileName"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private String getComboValue(JComboBox<String> combo) {
        Object editorItem = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        if (editorItem == null) {
            return "";
        }
        return editorItem.toString().trim();
    }

    private static class ActiveProfileItem {

        final String id;
        final String label;

        ActiveProfileItem(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class ProfileRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component comp = super.getListCellRendererComponent(list,
                    value instanceof AutoReplyProfile ? ((AutoReplyProfile) value).getName() : "",
                    index, isSelected, cellHasFocus);
            if (value instanceof AutoReplyProfile) {
                AutoReplyProfile profile = (AutoReplyProfile) value;
                if (profile.getId().equals(config.getActiveProfileId())) {
                    comp.setFont(comp.getFont().deriveFont(Font.BOLD));
                }
            }
            return comp;
        }
    }

    private static class ActiveProfileRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String label = value instanceof ActiveProfileItem ? ((ActiveProfileItem) value).label : "";
            return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
        }
    }

    private class TriggerEditorPanel extends JPanel {

        private final AutoReplyProfile profile;
        private final AutoReplyTrigger trigger;
        private final JTextField patternField;
        private final JTextArea repliesArea;
        private final JTextField allowField;
        private final JSpinner minDelaySpinner;
        private final JSpinner maxDelaySpinner;
        private final JSpinner cooldownSpinner;
        private final JSpinner minUsersSpinner;
        private final JSpinner minMentionsSpinner;
        private final JSpinner timeWindowSpinner;
        private final JComboBox<String> soundCombo;
        private final JCheckBox enabledCheck;
        private final JCheckBox notifyCheck;
        private final JRadioButton randomReplyMode;
        private final JRadioButton sequentialReplyMode;
        private final JCheckBox loopRepliesCheck;

        TriggerEditorPanel(AutoReplyProfile profile, AutoReplyTrigger trigger) {
            this.profile = profile;
            this.trigger = trigger;

                setLayout(new GridBagLayout());
                setAlignmentX(Component.LEFT_ALIGNMENT);
                setOpaque(false);
                // Slightly reduced padding to make each trigger editor more compact while preserving clarity
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 70, 70)),
                    new EmptyBorder(6, 6, 6, 6)));

                GridBagConstraints gbc = new GridBagConstraints();
                // Tighter insets reduce wasted space between controls
                gbc.insets = new Insets(2, 3, 2, 3);
                gbc.anchor = GridBagConstraints.WEST;

            enabledCheck = new JCheckBox(Language.getString("settings.autoReply.trigger.enabledToggle"));
            enabledCheck.setSelected(trigger.isEnabled());
            enabledCheck.addActionListener(e -> {
                trigger.setEnabled(enabledCheck.isSelected());
                refreshValidation();
            });
            gbc.gridx = 0;
            gbc.gridy = 0;
            add(enabledCheck, gbc);

            JComboBox<PatternType> patternTypeCombo = new JComboBox<>(PatternType.values());
            patternTypeCombo.setRenderer(new PatternRenderer());
            patternTypeCombo.setSelectedItem(trigger.getPatternType());
            patternTypeCombo.addActionListener(e -> trigger.setPatternType((PatternType) patternTypeCombo.getSelectedItem()));
            gbc.gridx = 1;
            // Keep the pattern type combo compact so more space is available for the pattern field
            patternTypeCombo.setPreferredSize(new Dimension(120, patternTypeCombo.getPreferredSize().height));
            add(patternTypeCombo, gbc);

            patternField = new JTextField(trigger.getPattern());
            patternField.getDocument().addDocumentListener(documentListener(() -> {
                trigger.setPattern(patternField.getText().trim());
                refreshValidation();
            }));
            gbc.gridx = 2;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(patternField, gbc);

            JButton duplicateButton = new JButton(Language.getString("settings.autoReply.triggers.duplicate"));
            GuiUtil.smallButtonInsets(duplicateButton);
            duplicateButton.addActionListener(e -> duplicateTrigger(this));
            gbc.gridx = 3;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            add(duplicateButton, gbc);

            JButton deleteButton = new JButton(Language.getString("settings.autoReply.triggers.delete"));
            GuiUtil.smallButtonInsets(deleteButton);
            deleteButton.addActionListener(e -> removeTrigger(this));
            gbc.gridx = 4;
            add(deleteButton, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            // Tighten vertical spacing for the reply label and field
            add(new JLabel(Language.getString("settings.autoReply.trigger.reply")), gbc);

            repliesArea = new JTextArea(trigger.getReply(), 3, 20);
            repliesArea.setLineWrap(true);
            repliesArea.setWrapStyleWord(true);
            repliesArea.getDocument().addDocumentListener(documentListener(() -> {
                trigger.setReply(repliesArea.getText());
                refreshValidation();
            }));
            JScrollPane repliesScroll = new JScrollPane(repliesArea);
            repliesScroll.setPreferredSize(new Dimension(220, 70));
            // Make reply area scroll reasonably responsive as well
            repliesScroll.getVerticalScrollBar().setUnitIncrement(16);
            gbc.gridx = 1;
            gbc.gridwidth = 4;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.BOTH;
            add(repliesScroll, gbc);

            ButtonGroup replyModeGroup = new ButtonGroup();
            randomReplyMode = new JRadioButton(Language.getString("settings.autoReply.trigger.replyMode.random"));
            sequentialReplyMode = new JRadioButton(Language.getString("settings.autoReply.trigger.replyMode.sequential"));
            loopRepliesCheck = new JCheckBox(Language.getString("settings.autoReply.trigger.replyLoop"));
            randomReplyMode.setOpaque(false);
            sequentialReplyMode.setOpaque(false);
            loopRepliesCheck.setOpaque(false);
            replyModeGroup.add(randomReplyMode);
            replyModeGroup.add(sequentialReplyMode);
            if (trigger.getReplySelection() == ReplySelection.SEQUENTIAL) {
                sequentialReplyMode.setSelected(true);
            }
            else {
                randomReplyMode.setSelected(true);
            }
            loopRepliesCheck.setSelected(trigger.isLoopReplies());
            loopRepliesCheck.addActionListener(e -> trigger.setLoopReplies(loopRepliesCheck.isSelected()));
            ActionListener replyModeListener = e -> {
                trigger.setReplySelection(sequentialReplyMode.isSelected()
                        ? ReplySelection.SEQUENTIAL
                        : ReplySelection.RANDOM);
                loopRepliesCheck.setEnabled(sequentialReplyMode.isSelected());
            };
            randomReplyMode.addActionListener(replyModeListener);
            sequentialReplyMode.addActionListener(replyModeListener);
            loopRepliesCheck.setEnabled(sequentialReplyMode.isSelected());

            JPanel replyModeRow = new JPanel(new GridBagLayout());
            replyModeRow.setOpaque(false);
            GridBagConstraints modeGbc = new GridBagConstraints();
            modeGbc.insets = new Insets(0, 4, 0, 4);
            modeGbc.gridy = 0;
            modeGbc.anchor = GridBagConstraints.WEST;
            modeGbc.gridx = 0;
            replyModeRow.add(new JLabel(Language.getString("settings.autoReply.trigger.replyMode")), modeGbc);
            modeGbc.gridx = 1;
            replyModeRow.add(randomReplyMode, modeGbc);
            modeGbc.gridx = 2;
            replyModeRow.add(sequentialReplyMode, modeGbc);
            modeGbc.gridx = 3;
            modeGbc.weightx = 1;
            replyModeRow.add(loopRepliesCheck, modeGbc);

            gbc.gridy = 2;
            gbc.gridx = 0;
            gbc.gridwidth = 5;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(replyModeRow, gbc);

            long minDelay = trigger.getMinDelayMillis();
            long maxDelay = Math.max(trigger.getMaxDelayMillis(), minDelay);
            SpinnerNumberModel minDelayModel = new SpinnerNumberModel(minDelay, 0L, 3600000L, 100L);
            SpinnerNumberModel maxDelayModel = new SpinnerNumberModel(maxDelay, minDelay, 3600000L, 100L);
            minDelaySpinner = new JSpinner(minDelayModel);
            maxDelaySpinner = new JSpinner(maxDelayModel);
            minDelaySpinner.addChangeListener(e -> {
                long value = ((Number) minDelaySpinner.getValue()).longValue();
                trigger.setMinDelayMillis(value);
                maxDelayModel.setMinimum(value);
                if (((Number) maxDelaySpinner.getValue()).longValue() < value) {
                    maxDelaySpinner.setValue(value);
                }
                refreshValidation();
            });
            maxDelaySpinner.addChangeListener(e -> {
                long value = ((Number) maxDelaySpinner.getValue()).longValue();
                trigger.setMaxDelayMillis(value);
                refreshValidation();
            });

            cooldownSpinner = new JSpinner(new SpinnerNumberModel(trigger.getCooldown(), 0L, Long.MAX_VALUE, 1L));
            cooldownSpinner.addChangeListener(e -> trigger.setCooldown(((Number) cooldownSpinner.getValue()).longValue()));

            JPanel timingRow = new JPanel(new GridBagLayout());
            timingRow.setOpaque(false);
            addLabeledSpinner(timingRow, Language.getString("settings.autoReply.trigger.minDelay"), minDelaySpinner, 0);
            addLabeledSpinner(timingRow, Language.getString("settings.autoReply.trigger.maxDelay"), maxDelaySpinner, 2);
            addLabeledSpinner(timingRow, Language.getString("settings.autoReply.trigger.cooldown"), cooldownSpinner, 4);

            gbc.gridy = 3;
            gbc.gridx = 0;
            gbc.gridwidth = 5;
            gbc.weightx = 1;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(timingRow, gbc);

            minUsersSpinner = new JSpinner(new SpinnerNumberModel(trigger.getMinUniqueUsers(), 0L, Long.MAX_VALUE, 1L));
            minUsersSpinner.addChangeListener(e -> {
                trigger.setMinUniqueUsers(((Number) minUsersSpinner.getValue()).longValue());
                refreshValidation();
            });

            minMentionsSpinner = new JSpinner(new SpinnerNumberModel(trigger.getMinMentionsPerUser(), 0L, Long.MAX_VALUE, 1L));
            minMentionsSpinner.addChangeListener(e -> {
                trigger.setMinMentionsPerUser(((Number) minMentionsSpinner.getValue()).longValue());
                refreshValidation();
            });

            timeWindowSpinner = new JSpinner(new SpinnerNumberModel(trigger.getTimeWindowSec(), 0L, Long.MAX_VALUE, 1L));
            timeWindowSpinner.addChangeListener(e -> {
                trigger.setTimeWindowSec(((Number) timeWindowSpinner.getValue()).longValue());
                refreshValidation();
            });

            JPanel thresholdRow = new JPanel(new GridBagLayout());
            thresholdRow.setOpaque(false);
            addLabeledSpinner(thresholdRow, Language.getString("settings.autoReply.trigger.minUniqueUsers"), minUsersSpinner, 0);
            addLabeledSpinner(thresholdRow, Language.getString("settings.autoReply.trigger.minMentionsPerUser"), minMentionsSpinner, 2);
            addLabeledSpinner(thresholdRow, Language.getString("settings.autoReply.trigger.timeWindow"), timeWindowSpinner, 4);

            gbc.gridy = 4;
            add(thresholdRow, gbc);

            gbc.gridy = 5;
            gbc.gridwidth = 1;
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            add(new JLabel(Language.getString("settings.autoReply.trigger.allowedAuthors")), gbc);

            allowField = new JTextField(String.join(", ", trigger.getAllowAuthors()));
            allowField.getDocument().addDocumentListener(documentListener(() -> trigger.setAllowAuthors(parseAllowList(allowField.getText()))));
            gbc.gridx = 1;
            gbc.gridwidth = 2;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(allowField, gbc);

            notifyCheck = new JCheckBox(Language.getString("settings.autoReply.trigger.notify"));
            notifyCheck.setSelected(trigger.isNotificationEnabled());
            notifyCheck.addActionListener(e -> trigger.setNotificationEnabled(notifyCheck.isSelected()));
            gbc.gridx = 3;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            add(notifyCheck, gbc);

            soundCombo = new JComboBox<>(new String[]{"", "off", "ding.wav"});
            soundCombo.setEditable(true);
            soundCombo.setRenderer(new SoundRenderer());
            soundCombo.setSelectedItem(trigger.getSound() == null ? "" : trigger.getSound());
            soundCombo.addActionListener(e -> updateSound());
            installComboEditorListener(soundCombo, this::updateSound);
            gbc.gridx = 4;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(soundCombo, gbc);
        }

        private void addLabeledSpinner(JPanel panel, String label, JSpinner spinner, int gridx) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 4, 0, 4);
            gbc.gridy = 0;
            gbc.gridx = gridx;
            gbc.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel(label), gbc);
            gbc.gridx = gridx + 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1;
            // Slightly narrower spinner so rows stay compact
            spinner.setPreferredSize(new Dimension(92, spinner.getPreferredSize().height));
            panel.add(spinner, gbc);
        }

        private void updateSound() {
            String value = getComboValue(soundCombo);
            trigger.setSound(StringUtil.isNullOrEmpty(value) ? null : value);
        }

        private List<String> parseAllowList(String text) {
            List<String> result = new ArrayList<>();
            if (StringUtil.isNullOrEmpty(text)) {
                return result;
            }
            String normalized = text.replace('\n', ',');
            for (String part : normalized.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
    }

    private static class PatternRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String label = value instanceof PatternType ? Language.getString(((PatternType) value).getLabelKey()) : "";
            return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
        }
    }

    private static class SoundRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String label;
            if (value == null || value.toString().trim().isEmpty()) {
                label = Language.getString("settings.autoReply.sound.inherit");
            } else if ("off".equalsIgnoreCase(value.toString().trim())) {
                label = Language.getString("settings.autoReply.sound.off");
            } else {
                label = value.toString();
            }
            return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
        }
    }
}
