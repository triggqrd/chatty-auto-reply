package chatty.gui.components.settings;

import chatty.AutoReplyManager;
import chatty.AutoReplyManager.AutoReplyConfig;
import chatty.AutoReplyManager.AutoReplyProfile;
import chatty.AutoReplyManager.AutoReplyTrigger;
import chatty.AutoReplyManager.PatternType;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
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

    private final DefaultListModel<AutoReplyTrigger> triggerModel = new DefaultListModel<>();
    private final JList<AutoReplyTrigger> triggerList = new JList<>(triggerModel);

    private final JComboBox<ActiveProfileItem> activeProfileCombo = new JComboBox<>();
    private final JSpinner globalCooldownSpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));
    private final JCheckBox selfIgnoreCheck = new JCheckBox(Language.getString("settings.autoReply.selfIgnore"));
    private final JCheckBox defaultNotificationCheck = new JCheckBox(Language.getString("settings.autoReply.defaultNotification"));
    private final JComboBox<String> defaultSoundCombo = new JComboBox<>(new String[]{"", "off", "ding.wav"});

    private final JTextField patternField = new JTextField();
    private final JComboBox<PatternType> patternTypeCombo = new JComboBox<>(PatternType.values());
    private final JTextArea replyArea = new JTextArea(4, 30);
    private final JSpinner triggerCooldownSpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));
    private final JTextArea overridesArea = new JTextArea(4, 30);
    private final JTextArea allowArea = new JTextArea(3, 20);
    private final JTextArea blockArea = new JTextArea(3, 20);
    private final JCheckBox triggerNotificationCheck = new JCheckBox(Language.getString("settings.autoReply.trigger.notify"));
    private final JComboBox<String> triggerSoundCombo = new JComboBox<>(new String[]{"", "off", "ding.wav"});
    private final JLabel validationLabel = new JLabel();

    private boolean updatingTriggerForm;

    public AutoReplySettings(SettingsDialog dialog) {
        super(true);
        this.dialog = dialog;
        buildUi();
        installListeners();
        updateActiveProfileCombo();
        setTriggerFormEnabled(false);
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
        contentConstraints.insets = new Insets(0, 0, 0, 5);
        contentConstraints.gridx = 0;
        contentConstraints.gridy = 0;
        contentConstraints.fill = GridBagConstraints.BOTH;
        contentConstraints.weightx = 0.35;
        contentConstraints.weighty = 1;
        content.add(profilesPanel, contentConstraints);

        contentConstraints.insets = new Insets(0, 5, 0, 0);
        contentConstraints.gridx = 1;
        contentConstraints.weightx = 0.65;
        content.add(triggersPanel, contentConstraints);
    }

    private JPanel createProfilesPanel() {
        JPanel panel = createTitledPanel(Language.getString("settings.section.autoReplyProfiles"));
        panel.setLayout(new GridBagLayout());

        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setCellRenderer(new ProfileRenderer());
        profileList.setVisibleRowCount(10);
        JScrollPane scroll = new JScrollPane(profileList);
        scroll.setPreferredSize(new Dimension(200, 220));

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

        return panel;
    }

    private JPanel createTriggersPanel() {
        JPanel panel = createTitledPanel(Language.getString("settings.section.autoReplyTriggers"));
        panel.setLayout(new GridBagLayout());

        triggerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        triggerList.setCellRenderer(new TriggerRenderer());
        triggerList.setVisibleRowCount(10);
        JScrollPane triggerScroll = new JScrollPane(triggerList);
        triggerScroll.setPreferredSize(new Dimension(300, 200));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 0.5;
        panel.add(triggerScroll, gbc);

        JPanel buttons = new JPanel();
        JButton add = new JButton(Language.getString("settings.autoReply.triggers.add"));
        GuiUtil.smallButtonInsets(add);
        add.addActionListener(e -> addTrigger());
        buttons.add(add);

        JButton duplicate = new JButton(Language.getString("settings.autoReply.triggers.duplicate"));
        GuiUtil.smallButtonInsets(duplicate);
        duplicate.addActionListener(e -> duplicateTrigger());
        buttons.add(duplicate);

        JButton delete = new JButton(Language.getString("settings.autoReply.triggers.delete"));
        GuiUtil.smallButtonInsets(delete);
        delete.addActionListener(e -> deleteTrigger());
        buttons.add(delete);

        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        panel.add(buttons, gbc);

        JPanel form = new JPanel(new GridBagLayout());
        Insets insets = new Insets(3, 3, 3, 3);
        GridBagConstraints formGbc = new GridBagConstraints();
        formGbc.insets = insets;
        formGbc.gridx = 0;
        formGbc.gridy = 0;
        formGbc.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.patternType")), formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.HORIZONTAL;
        formGbc.weightx = 1;
        patternTypeCombo.setRenderer(new PatternRenderer());
        patternTypeCombo.setPrototypeDisplayValue(PatternType.REGEX);
        form.add(patternTypeCombo, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        formGbc.fill = GridBagConstraints.NONE;
        formGbc.weightx = 0;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.pattern")), formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.HORIZONTAL;
        formGbc.weightx = 1;
        form.add(patternField, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        formGbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.reply")), formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.BOTH;
        formGbc.weighty = 0.3;
        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        JScrollPane replyScroll = new JScrollPane(replyArea);
        replyScroll.setPreferredSize(new Dimension(200, 80));
        form.add(replyScroll, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        formGbc.weighty = 0;
        formGbc.anchor = GridBagConstraints.WEST;
        formGbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.cooldown")), formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.HORIZONTAL;
        triggerCooldownSpinner.setPreferredSize(new Dimension(120, triggerCooldownSpinner.getPreferredSize().height));
        form.add(triggerCooldownSpinner, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        formGbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel overridesLabel = new JLabel(Language.getString("settings.autoReply.trigger.authorOverrides"));
        overridesLabel.setToolTipText(Language.getString("settings.autoReply.trigger.authorOverrides.tip"));
        form.add(overridesLabel, formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.BOTH;
        formGbc.weighty = 0.3;
        overridesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, overridesArea.getFont().getSize()));
        JScrollPane overridesScroll = new JScrollPane(overridesArea);
        overridesScroll.setPreferredSize(new Dimension(200, 70));
        form.add(overridesScroll, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.allowList")), formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.BOTH;
        formGbc.weighty = 0.2;
        JScrollPane allowScroll = new JScrollPane(allowArea);
        form.add(allowScroll, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.blockList")), formGbc);

        formGbc.gridx = 1;
        JScrollPane blockScroll = new JScrollPane(blockArea);
        form.add(blockScroll, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        formGbc.gridwidth = 2;
        formGbc.anchor = GridBagConstraints.WEST;
        formGbc.fill = GridBagConstraints.NONE;
        triggerNotificationCheck.setToolTipText(Language.getString("settings.autoReply.trigger.notify.tip"));
        form.add(triggerNotificationCheck, formGbc);

        formGbc.gridy++;
        formGbc.gridwidth = 1;
        formGbc.gridx = 0;
        form.add(new JLabel(Language.getString("settings.autoReply.trigger.sound")), formGbc);

        formGbc.gridx = 1;
        formGbc.fill = GridBagConstraints.HORIZONTAL;
        triggerSoundCombo.setEditable(true);
        triggerSoundCombo.setRenderer(new SoundRenderer());
        form.add(triggerSoundCombo, formGbc);

        formGbc.gridy++;
        formGbc.gridx = 0;
        formGbc.gridwidth = 2;
        formGbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(validationLabel, formGbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.5;
        panel.add(form, gbc);

        return panel;
    }

    private void installListeners() {
        profileList.addListSelectionListener(profileSelectionListener());
        triggerList.addListSelectionListener(triggerSelectionListener());

        patternField.getDocument().addDocumentListener(documentListener(this::updatePattern));
        replyArea.getDocument().addDocumentListener(documentListener(this::updateReply));
        overridesArea.getDocument().addDocumentListener(documentListener(this::updateOverrides));
        allowArea.getDocument().addDocumentListener(documentListener(this::updateAllowList));
        blockArea.getDocument().addDocumentListener(documentListener(this::updateBlockList));

        patternTypeCombo.addActionListener(e -> updatePatternType());
        triggerCooldownSpinner.addChangeListener(e -> updateTriggerCooldown());
        triggerNotificationCheck.addActionListener(e -> updateTriggerNotification());
        triggerSoundCombo.addActionListener(e -> updateTriggerSound());
        defaultSoundCombo.addActionListener(e -> updateDefaultSound());

        installComboEditorListener(triggerSoundCombo, this::updateTriggerSound);
        installComboEditorListener(defaultSoundCombo, this::updateDefaultSound);

        globalCooldownSpinner.addChangeListener(e -> config.setGlobalCooldown(((Number) globalCooldownSpinner.getValue()).longValue()));
        selfIgnoreCheck.addActionListener(e -> config.setSelfIgnore(selfIgnoreCheck.isSelected()));
        defaultNotificationCheck.addActionListener(e -> config.setDefaultNotification(defaultNotificationCheck.isSelected()));
        activeProfileCombo.addActionListener(e -> {
            ActiveProfileItem item = (ActiveProfileItem) activeProfileCombo.getSelectedItem();
            if (item != null) {
                config.setActiveProfileId(item.id);
                profileList.repaint();
            }
        });
    }

    private ListSelectionListener profileSelectionListener() {
        return e -> {
            if (!e.getValueIsAdjusting()) {
                showProfile(profileList.getSelectedValue());
            }
        };
    }

    private ListSelectionListener triggerSelectionListener() {
        return e -> {
            if (!e.getValueIsAdjusting()) {
                showTrigger(triggerList.getSelectedValue());
            }
        };
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
            triggerModel.clear();
            setTriggerFormEnabled(false);
        }
        updateActiveProfileCombo();
        globalCooldownSpinner.setValue(Long.valueOf(this.config.getGlobalCooldown()));
        selfIgnoreCheck.setSelected(this.config.isSelfIgnore());
        defaultNotificationCheck.setSelected(this.config.isDefaultNotification());
        String defaultSound = this.config.getDefaultSound();
        defaultSoundCombo.setSelectedItem(defaultSound == null ? "" : defaultSound);
    }

    public AutoReplyConfig getData() {
        updatePattern();
        updateReply();
        updateOverrides();
        updateAllowList();
        updateBlockList();
        updateTriggerCooldown();
        updateTriggerNotification();
        updateTriggerSound();
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
            triggerModel.clear();
            setTriggerFormEnabled(false);
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
        triggerModel.addElement(trigger);
        triggerList.setSelectedValue(trigger, true);
    }

    private void duplicateTrigger() {
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (trigger == null || profile == null) {
            return;
        }
        AutoReplyTrigger copy = trigger.copyWithNewId();
        profile.addTrigger(copy);
        triggerModel.addElement(copy);
        triggerList.setSelectedValue(copy, true);
    }

    private void deleteTrigger() {
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        AutoReplyProfile profile = profileList.getSelectedValue();
        if (trigger == null || profile == null) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(dialog,
                String.format(Language.getString("settings.autoReply.triggers.delete.confirm"),
                        trigger.getPattern().isEmpty() ? Language.getString("settings.autoReply.trigger.unnamed") : trigger.getPattern()),
                Language.getString("settings.autoReply.triggers.delete"),
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        int previousIndex = triggerList.getSelectedIndex();
        profile.getTriggers().remove(trigger);
        triggerModel.removeElement(trigger);
        if (!triggerModel.isEmpty()) {
            int newIndex = Math.min(triggerModel.size() - 1, Math.max(0, previousIndex));
            triggerList.setSelectedIndex(newIndex);
        } else {
            clearTriggerForm();
        }
    }

    private void showProfile(AutoReplyProfile profile) {
        triggerModel.clear();
        if (profile == null) {
            clearTriggerForm();
            return;
        }
        for (AutoReplyTrigger trigger : profile.getTriggers()) {
            triggerModel.addElement(trigger);
        }
        if (!triggerModel.isEmpty()) {
            triggerList.setSelectedIndex(0);
        } else {
            clearTriggerForm();
        }
    }

    private void showTrigger(AutoReplyTrigger trigger) {
        updatingTriggerForm = true;
        if (trigger == null) {
            clearTriggerForm();
            updatingTriggerForm = false;
            return;
        }
        setTriggerFormEnabled(true);
        patternField.setText(trigger.getPattern());
        patternTypeCombo.setSelectedItem(trigger.getPatternType());
        replyArea.setText(trigger.getReply());
        triggerCooldownSpinner.setValue(Long.valueOf(trigger.getCooldown()));
        overridesArea.setText(formatOverrides(trigger.getAuthorOverrides()));
        allowArea.setText(formatList(trigger.getAllowAuthors()));
        blockArea.setText(formatList(trigger.getBlockAuthors()));
        triggerNotificationCheck.setSelected(trigger.isNotificationEnabled());
        triggerSoundCombo.setSelectedItem(trigger.getSound() == null ? "" : trigger.getSound());
        updatingTriggerForm = false;
        updateValidationForTrigger(trigger);
    }

    private void clearTriggerForm() {
        updatingTriggerForm = true;
        patternField.setText("");
        patternTypeCombo.setSelectedItem(PatternType.PLAIN);
        replyArea.setText("");
        triggerCooldownSpinner.setValue(0L);
        overridesArea.setText("");
        allowArea.setText("");
        blockArea.setText("");
        triggerNotificationCheck.setSelected(false);
        triggerSoundCombo.setSelectedItem("");
        setTriggerFormEnabled(false);
        updatingTriggerForm = false;
        validationLabel.setText(Language.getString("settings.autoReply.triggers.help"));
    }

    private void setTriggerFormEnabled(boolean enabled) {
        patternField.setEnabled(enabled);
        patternTypeCombo.setEnabled(enabled);
        replyArea.setEnabled(enabled);
        triggerCooldownSpinner.setEnabled(enabled);
        overridesArea.setEnabled(enabled);
        allowArea.setEnabled(enabled);
        blockArea.setEnabled(enabled);
        triggerNotificationCheck.setEnabled(enabled);
        triggerSoundCombo.setEnabled(enabled);
    }

    private void updatePattern() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setPattern(patternField.getText().trim());
            triggerList.repaint();
            updateValidationForTrigger(trigger);
        }
    }

    private void updatePatternType() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setPatternType((PatternType) patternTypeCombo.getSelectedItem());
            triggerList.repaint();
        }
    }

    private void updateReply() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setReply(replyArea.getText());
        }
    }

    private void updateTriggerCooldown() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setCooldown(((Number) triggerCooldownSpinner.getValue()).longValue());
        }
    }

    private void updateTriggerNotification() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setNotificationEnabled(triggerNotificationCheck.isSelected());
        }
    }

    private void updateTriggerSound() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            String value = getComboValue(triggerSoundCombo);
            trigger.setSound(StringUtil.isNullOrEmpty(value) ? null : value);
        }
    }

    private void updateDefaultSound() {
        if (config == null) {
            return;
        }
        String value = getComboValue(defaultSoundCombo);
        config.setDefaultSound(StringUtil.isNullOrEmpty(value) ? null : value);
    }

    private void updateOverrides() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger == null) {
            return;
        }
        String text = overridesArea.getText();
        Map<String, String> overrides = new LinkedHashMap<>();
        boolean valid = true;
        if (!StringUtil.isNullOrEmpty(text)) {
            for (String line : StringUtil.splitLines(text)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int index = trimmed.indexOf('=');
                if (index <= 0) {
                    valid = false;
                    continue;
                }
                String key = trimmed.substring(0, index).trim();
                String value = trimmed.substring(index + 1).trim();
                if (key.isEmpty()) {
                    valid = false;
                    continue;
                }
                overrides.put(key, value);
            }
        }
        if (valid) {
            trigger.setAuthorOverrides(overrides);
            updateValidationForTrigger(trigger);
        } else {
            validationLabel.setText(Language.getString("settings.autoReply.validation.overrideFormat"));
        }
    }

    private void updateAllowList() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setAllowAuthors(parseList(allowArea.getText()));
        }
    }

    private void updateBlockList() {
        if (updatingTriggerForm) {
            return;
        }
        AutoReplyTrigger trigger = triggerList.getSelectedValue();
        if (trigger != null) {
            trigger.setBlockAuthors(parseList(blockArea.getText()));
        }
    }

    private List<String> parseList(String input) {
        List<String> result = new ArrayList<>();
        if (StringUtil.isNullOrEmpty(input)) {
            return result;
        }
        String replaced = input.replace('\n', ',');
        for (String part : replaced.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String formatOverrides(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return "";
        }
        return overrides.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String formatList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join("\n", list);
    }

    private void updateValidationForTrigger(AutoReplyTrigger trigger) {
        if (trigger == null) {
            validationLabel.setText(Language.getString("settings.autoReply.triggers.help"));
            return;
        }
        if (StringUtil.isNullOrEmpty(trigger.getPattern())) {
            validationLabel.setText(Language.getString("settings.autoReply.validation.pattern"));
        } else {
            validationLabel.setText(Language.getString("settings.autoReply.triggers.help"));
        }
    }

    private void updateActiveProfileCombo() {
        ActiveProfileItem current = (ActiveProfileItem) activeProfileCombo.getSelectedItem();
        String selectedId = current != null ? current.id : config.getActiveProfileId();
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
        activeProfileCombo.setSelectedIndex(0);
        config.setActiveProfileId("default");
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

    private class TriggerRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String label = "";
            if (value instanceof AutoReplyTrigger) {
                AutoReplyTrigger trigger = (AutoReplyTrigger) value;
                String pattern = trigger.getPattern();
                if (StringUtil.isNullOrEmpty(pattern)) {
                    pattern = Language.getString("settings.autoReply.trigger.unnamed");
                }
                label = pattern + " (" + Language.getString(trigger.getPatternType().getLabelKey()) + ")";
            }
            return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
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
