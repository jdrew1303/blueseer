package com.blueseer.pay;

import com.blueseer.pay.JournalDtos.AccountingTarget;
import com.blueseer.pay.JournalDtos.MappingCompletenessDTO;
import com.blueseer.pay.JournalDtos.NativeGlPostResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.FileOutputStream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** S-63 Journal Export. */
public class JournalExportPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IJournalController controller;
    private final IPayslipDistributionController periodSource;

    private JComboBox<PeriodDTO> cbPeriod;
    private JComboBox<String> cbAccountingTarget;
    private JLabel lblMappingStatus;
    private JButton btCreateExportFile;

    public JournalExportPanel() {
        this(new InMemoryJournalController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    public JournalExportPanel(IJournalController controller, IPayslipDistributionController periodSource) {
        this.controller = controller;
        this.periodSource = periodSource;
        initComponents();
        refreshPeriods();
        refreshStatus();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshPeriods();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Journal Export"));
        content.setPreferredSize(new Dimension(520, 220));

        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));

        JPanel periodRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        periodRow.add(new JLabel("Period:"));
        cbPeriod = new JComboBox<>();
        cbPeriod.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber() + " (" + value.payDate() + ")"));
        cbPeriod.addActionListener(e -> refreshStatus());
        periodRow.add(cbPeriod);
        form.add(periodRow);

        JPanel targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        targetRow.add(new JLabel("Accounting Target:"));
        cbAccountingTarget = new JComboBox<>();
        for (AccountingTarget t : AccountingTarget.values()) {
            cbAccountingTarget.addItem(t.label());
        }
        cbAccountingTarget.addActionListener(e -> refreshStatus());
        targetRow.add(cbAccountingTarget);
        form.add(targetRow);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblMappingStatus = new JLabel("-");
        statusRow.add(lblMappingStatus);
        form.add(statusRow);

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btCreateExportFile = new JButton("Create Export File");
        btCreateExportFile.addActionListener(e -> onCreateExport());
        south.add(btCreateExportFile);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private AccountingTarget selectedTarget() {
        return AccountingTarget.fromLabel(String.valueOf(cbAccountingTarget.getSelectedItem()));
    }

    private void refreshPeriods() {
        PeriodDTO previous = (PeriodDTO) cbPeriod.getSelectedItem();
        cbPeriod.removeAllItems();
        for (PeriodDTO period : periodSource.getProcessedPeriods(DEMO_COMPANY)) {
            cbPeriod.addItem(period);
        }
        if (previous != null) {
            cbPeriod.setSelectedItem(previous);
        }
    }

    private void refreshStatus() {
        if (lblMappingStatus == null) {
            return;
        }
        AccountingTarget target = selectedTarget();
        MappingCompletenessDTO completeness = controller.checkMappingCompleteness(DEMO_COMPANY, target);
        boolean isNative = target == AccountingTarget.NATIVE_BLUESEER_GL;
        btCreateExportFile.setText(isNative ? "Post to General Ledger" : "Create Export File");
        if (completeness.complete()) {
            lblMappingStatus.setText("Mapping complete for " + target.label() + ".");
            btCreateExportFile.setEnabled(true);
        } else {
            lblMappingStatus.setText("Mapping incomplete - missing: " + String.join(", ", completeness.missingCategories())
                    + " (set up in Payroll Journal Mapping).");
            btCreateExportFile.setEnabled(false);
        }
    }

    private void onCreateExport() {
        PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
        if (period == null) {
            return;
        }
        AccountingTarget target = selectedTarget();
        if (target == AccountingTarget.NATIVE_BLUESEER_GL) {
            NativeGlPostResult result = controller.postToNativeGl(DEMO_COMPANY, period.periodNumber());
            JOptionPane.showMessageDialog(this, result.message(), "Journal Export",
                    result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            return;
        }
        byte[] bytes = controller.createExportFile(DEMO_COMPANY, period.periodNumber(), target);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("journal-period-" + period.periodNumber() + ".csv"));
        int result = chooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (FileOutputStream fos = new FileOutputStream(chooser.getSelectedFile())) {
                fos.write(bytes);
                JOptionPane.showMessageDialog(this, "Export file saved.", "Journal Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to write file: " + ex.getMessage(), "Journal Export", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
