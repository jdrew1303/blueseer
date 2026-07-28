package com.blueseer.pay;

import com.blueseer.pay.CompanySetupDtos.CompanySetupDTO;
import com.blueseer.pay.CompanySetupDtos.PayFrequencyCombo;
import com.blueseer.pay.CompanySetupDtos.ValidationResult;
import com.blueseer.pay.CompanySetupDtos.WizardStep;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * S-01 New Company Wizard, per
 * docs/architecture/irish-payroll-2026-screen-specs.md. Modal, step-by-step
 * (company details -&gt; registration -&gt; pay frequency -&gt; password),
 * matching the exemplar's own step sequence.
 */
public class NewCompanyWizardDialog extends JDialog {

    private static final Pattern REG_NUMBER_PATTERN = Pattern.compile("^\\d{7}[A-Za-z]{1,2}$");
    private static final WizardStep[] STEPS = WizardStep.values();

    private final ICompanySetupController controller;
    private CompanyId createdCompanyId;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel stepCardPanel = new JPanel(cardLayout);
    private int currentStepIndex = 0;

    private JTextField tbCompanyName;
    private JTextArea taAddress;
    private JTextField tbRegNumber;
    private JLabel lblRegWarning;
    private JRadioButton rbWeeklyMonthly;
    private JRadioButton rbFortnightlyMonthly;
    private JPasswordField pfPassword1;
    private JPasswordField pfPassword2;

    private JLabel lblStepError;
    private JButton btBack;
    private JButton btNext;
    private JButton btFinish;

    public NewCompanyWizardDialog(Frame owner) {
        this(owner, new InMemoryCompanySetupController());
    }

    public NewCompanyWizardDialog(Frame owner, ICompanySetupController controller) {
        super(owner, "New Company Wizard", true);
        this.controller = controller;
        initComponents();
        setSize(420, 380);
        setLocation(40, 60);
    }

    public CompanyId getCreatedCompanyId() {
        return createdCompanyId;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setResizable(false);

        stepCardPanel.add(buildCompanyDetailsCard(), "company-details");
        stepCardPanel.add(buildRegistrationCard(), "registration");
        stepCardPanel.add(buildPayFrequencyCard(), "pay-frequency");
        stepCardPanel.add(buildPasswordCard(), "password");
        add(stepCardPanel, BorderLayout.CENTER);

        lblStepError = new JLabel(" ");
        lblStepError.setForeground(Color.RED);
        lblStepError.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(lblStepError, BorderLayout.NORTH);

        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btBack = new JButton("Back");
        btNext = new JButton("Next");
        btFinish = new JButton("Finish");
        JButton btCancel = new JButton("Cancel");
        btBack.addActionListener(e -> onBack());
        btNext.addActionListener(e -> onNext());
        btFinish.addActionListener(e -> onFinish());
        btCancel.addActionListener(e -> {
            createdCompanyId = null;
            dispose();
        });
        navButtonPanel.add(btBack);
        navButtonPanel.add(btNext);
        navButtonPanel.add(btFinish);
        navButtonPanel.add(btCancel);
        add(navButtonPanel, BorderLayout.SOUTH);

        updateNavButtons();
    }

    private JPanel buildCompanyDetailsCard() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(new JLabel("Company Name"));
        tbCompanyName = new JTextField();
        top.add(tbCompanyName);
        panel.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(new JLabel("Address"), BorderLayout.NORTH);
        taAddress = new JTextArea(4, 20);
        taAddress.setLineWrap(true);
        taAddress.setWrapStyleWord(true);
        center.add(new JScrollPane(taAddress), BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRegistrationCard() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(new JLabel("Employer Registered Number"));
        tbRegNumber = new JTextField();
        tbRegNumber.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String value = tbRegNumber.getText();
                lblRegWarning.setVisible(!value.isEmpty() && !REG_NUMBER_PATTERN.matcher(value).matches());
            }
        });
        top.add(tbRegNumber);
        panel.add(top, BorderLayout.CENTER);

        JPanel south = new JPanel(new GridLayout(2, 1));
        JLabel lblRegHelp = new JLabel("Often the same as your VAT number");
        lblRegHelp.setFont(lblRegHelp.getFont().deriveFont(java.awt.Font.ITALIC, 11f));
        south.add(lblRegHelp);
        lblRegWarning = new JLabel("Format is normally 7 digits followed by 1-2 letters");
        lblRegWarning.setForeground(new Color(180, 120, 0));
        lblRegWarning.setVisible(false);
        south.add(lblRegWarning);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPayFrequencyCard() {
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rbWeeklyMonthly = new JRadioButton("Weekly & Monthly");
        rbFortnightlyMonthly = new JRadioButton("Fortnightly & Monthly");
        ButtonGroup group = new ButtonGroup();
        group.add(rbWeeklyMonthly);
        group.add(rbFortnightlyMonthly);
        panel.add(rbWeeklyMonthly);
        panel.add(rbFortnightlyMonthly);
        return panel;
    }

    private JPanel buildPasswordCard() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel fields = new JPanel(new GridLayout(4, 1));
        fields.add(new JLabel("Password"));
        pfPassword1 = new JPasswordField();
        fields.add(pfPassword1);
        fields.add(new JLabel("Confirm Password"));
        pfPassword2 = new JPasswordField();
        fields.add(pfPassword2);
        panel.add(fields, BorderLayout.CENTER);

        JLabel lblPasswordRule = new JLabel("Minimum 4 alphanumeric characters");
        lblPasswordRule.setFont(lblPasswordRule.getFont().deriveFont(java.awt.Font.ITALIC, 11f));
        panel.add(lblPasswordRule, BorderLayout.SOUTH);
        return panel;
    }

    private void onBack() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            cardLayout.show(stepCardPanel, STEPS[currentStepIndex].name().toLowerCase().replace('_', '-'));
            lblStepError.setText(" ");
            updateNavButtons();
        }
    }

    private void onNext() {
        WizardStep step = STEPS[currentStepIndex];
        ValidationResult result = controller.validateStep(step, buildPartialDto());
        if (!result.valid()) {
            lblStepError.setText(String.join("; ", result.fieldErrors().values()));
            return;
        }
        lblStepError.setText(" ");
        if (currentStepIndex < STEPS.length - 1) {
            currentStepIndex++;
            cardLayout.show(stepCardPanel, STEPS[currentStepIndex].name().toLowerCase().replace('_', '-'));
            updateNavButtons();
        }
    }

    private void onFinish() {
        char[] p1 = pfPassword1.getPassword();
        char[] p2 = pfPassword2.getPassword();
        if (!Arrays.equals(p1, p2)) {
            lblStepError.setText("Passwords do not match");
            return;
        }
        ValidationResult result = controller.validateStep(WizardStep.PASSWORD, buildPartialDto());
        if (!result.valid()) {
            lblStepError.setText(String.join("; ", result.fieldErrors().values()));
            return;
        }
        createdCompanyId = controller.createCompany(buildPartialDto());
        JOptionPane.showMessageDialog(this, "Company created.", "New Company Wizard", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private void updateNavButtons() {
        btBack.setEnabled(currentStepIndex > 0);
        btNext.setEnabled(currentStepIndex < STEPS.length - 1);
        btFinish.setEnabled(currentStepIndex == STEPS.length - 1);
    }

    private CompanySetupDTO buildPartialDto() {
        PayFrequencyCombo combo = rbWeeklyMonthly.isSelected() ? PayFrequencyCombo.WEEKLY_AND_MONTHLY
                : rbFortnightlyMonthly.isSelected() ? PayFrequencyCombo.FORTNIGHTLY_AND_MONTHLY : null;
        return new CompanySetupDTO(
                tbCompanyName.getText(),
                taAddress.getText(),
                tbRegNumber.getText(),
                combo,
                new String(pfPassword1.getPassword()));
    }
}
