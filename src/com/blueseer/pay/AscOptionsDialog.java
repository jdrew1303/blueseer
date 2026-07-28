package com.blueseer.pay;

import com.blueseer.pay.ITaxYearRules.AscGroup;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.math.BigDecimal;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * S-08's ASC options modal - opened via a row's {@code btAscOptions} icon
 * when Description == "ASC". Override amount/percentage are mutually
 * exclusive, matching the exemplar's own ASC-specific UI behaviour.
 */
public class AscOptionsDialog extends JDialog {

    private JCheckBox cbSingleScheme;
    private JRadioButton rbNoOverride;
    private JRadioButton rbOverrideAmount;
    private JRadioButton rbOverridePercentage;
    private JTextField tbAscOverrideAmount;
    private JTextField tbAscOverridePercentage;

    private boolean saved;
    private AscGroup resultGroup;
    private BigDecimal resultOverrideAmount;
    private BigDecimal resultOverridePercentage;

    public AscOptionsDialog(Frame owner, boolean singleScheme, BigDecimal overrideAmount, BigDecimal overridePercentage) {
        super(owner, "ASC Options", true);
        initComponents(singleScheme, overrideAmount, overridePercentage);
        setSize(340, 220);
        setLocation(60, 80);
    }

    public boolean wasSaved() {
        return saved;
    }

    public AscGroup getResultGroup() {
        return resultGroup;
    }

    public BigDecimal getResultOverrideAmount() {
        return resultOverrideAmount;
    }

    public BigDecimal getResultOverridePercentage() {
        return resultOverridePercentage;
    }

    private void initComponents(boolean singleScheme, BigDecimal overrideAmount, BigDecimal overridePercentage) {
        setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        cbSingleScheme = new JCheckBox("Member of the Single Scheme");
        cbSingleScheme.setSelected(singleScheme);

        rbNoOverride = new JRadioButton("System-calculated", overrideAmount == null && overridePercentage == null);
        rbOverrideAmount = new JRadioButton("Override amount", overrideAmount != null);
        rbOverridePercentage = new JRadioButton("Override percentage", overridePercentage != null);
        ButtonGroup group = new ButtonGroup();
        group.add(rbNoOverride);
        group.add(rbOverrideAmount);
        group.add(rbOverridePercentage);

        tbAscOverrideAmount = new JTextField(overrideAmount != null ? overrideAmount.toPlainString() : "", 10);
        tbAscOverridePercentage = new JTextField(overridePercentage != null ? overridePercentage.toPlainString() : "", 10);

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addComponent(cbSingleScheme)
                .addComponent(rbNoOverride)
                .addGroup(layout.createSequentialGroup().addComponent(rbOverrideAmount).addComponent(tbAscOverrideAmount))
                .addGroup(layout.createSequentialGroup().addComponent(rbOverridePercentage).addComponent(tbAscOverridePercentage)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(cbSingleScheme)
                .addComponent(rbNoOverride)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(rbOverrideAmount).addComponent(tbAscOverrideAmount))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(rbOverridePercentage).addComponent(tbAscOverridePercentage)));

        add(panel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btOk = new JButton("OK");
        btOk.addActionListener(e -> onOk());
        JButton btCancel = new JButton("Cancel");
        btCancel.addActionListener(e -> dispose());
        buttons.add(btOk);
        buttons.add(btCancel);
        add(buttons, BorderLayout.SOUTH);
    }

    private void onOk() {
        resultGroup = cbSingleScheme.isSelected() ? AscGroup.SINGLE_SCHEME : AscGroup.STANDARD_ACCRUAL;
        resultOverrideAmount = rbOverrideAmount.isSelected() ? parseOrNull(tbAscOverrideAmount.getText()) : null;
        resultOverridePercentage = rbOverridePercentage.isSelected() ? parseOrNull(tbAscOverridePercentage.getText()) : null;
        saved = true;
        dispose();
    }

    private static BigDecimal parseOrNull(String text) {
        try {
            return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
