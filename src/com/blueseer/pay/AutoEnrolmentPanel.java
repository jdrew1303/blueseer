package com.blueseer.pay;

import com.blueseer.pay.AutoEnrolmentDtos.AeContributionPeriodDTO;
import com.blueseer.pay.AutoEnrolmentDtos.AeEligibilityStatusDTO;
import com.blueseer.pay.AutoEnrolmentDtos.AecsSubmissionResult;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** S-61 Auto-Enrolment (MyFuture Fund). */
public class AutoEnrolmentPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IAutoEnrolmentController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JLabel lblEligibilityStatus;
    private JLabel lblCurrentContributionTier;
    private JCheckBox cbHasExistingPensionCoverage;
    private JCheckBox cbOptedOutOrSuspended;
    private JButton btViewContributionHistory;
    private JButton btTriggerAecsSubmission;
    private JLabel lblAepnStatus;

    public AutoEnrolmentPanel() {
        this(new InMemoryAutoEnrolmentController(PayrollStubStore.shared()), new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public AutoEnrolmentPanel(IAutoEnrolmentController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
        refreshStatus();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshEmployees();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Auto-Enrolment (MyFuture Fund)"));
        content.setPreferredSize(new Dimension(560, 380));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> refreshStatus());

        JLabel lblStatusLabel = new JLabel("Eligibility Status:");
        lblEligibilityStatus = new JLabel("-");

        JLabel lblTierLabel = new JLabel("Current Contribution Tier:");
        lblCurrentContributionTier = new JLabel("-");

        JLabel lblCoverageLabel = new JLabel("Has Existing Pension Coverage:");
        cbHasExistingPensionCoverage = new JCheckBox();
        cbHasExistingPensionCoverage.addActionListener(e -> onFlagsChanged());

        JLabel lblOptOutLabel = new JLabel("Opted Out / Suspended:");
        cbOptedOutOrSuspended = new JCheckBox();
        cbOptedOutOrSuspended.addActionListener(e -> onOptOutToggled());

        JLabel lblAepnLabel = new JLabel("AEPN Status:");
        lblAepnStatus = new JLabel("No pending notifications");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblStatusLabel).addComponent(lblTierLabel)
                                .addComponent(lblCoverageLabel).addComponent(lblOptOutLabel).addComponent(lblAepnLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(lblEligibilityStatus).addComponent(lblCurrentContributionTier)
                                .addComponent(cbHasExistingPensionCoverage).addComponent(cbOptedOutOrSuspended).addComponent(lblAepnStatus))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStatusLabel).addComponent(lblEligibilityStatus))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTierLabel).addComponent(lblCurrentContributionTier))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCoverageLabel).addComponent(cbHasExistingPensionCoverage))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblOptOutLabel).addComponent(cbOptedOutOrSuspended))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAepnLabel).addComponent(lblAepnStatus)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btViewContributionHistory = new JButton("View Contribution History");
        btViewContributionHistory.addActionListener(e -> onViewHistory());
        btTriggerAecsSubmission = new JButton("Trigger AECS Submission");
        btTriggerAecsSubmission.addActionListener(e -> onTriggerAecs());
        south.add(btViewContributionHistory);
        south.add(btTriggerAecsSubmission);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void refreshEmployees() {
        EmployeeSummaryDTO previous = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            cbEmployeeSelector.addItem(summary);
        }
        if (previous != null) {
            cbEmployeeSelector.setSelectedItem(previous);
        }
        refreshStatus();
    }

    private void refreshStatus() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || lblEligibilityStatus == null) {
            return;
        }
        AeEligibilityStatusDTO status = controller.getEligibilityStatus(selected.id());
        lblEligibilityStatus.setText(status.status());
        lblCurrentContributionTier.setText(status.contributionTierLabel());
        cbHasExistingPensionCoverage.setSelected(status.hasExistingPensionCoverage());
        cbOptedOutOrSuspended.setSelected(status.optedOutOrSuspended());
    }

    private void onFlagsChanged() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        controller.setParticipationFlags(selected.id(), cbHasExistingPensionCoverage.isSelected(), cbOptedOutOrSuspended.isSelected());
        refreshStatus();
    }

    private void onOptOutToggled() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        if (cbOptedOutOrSuspended.isSelected()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Employees should not be pressured by employers to opt-out or suspend their participation. "
                            + "Confirm this action was employee-initiated.",
                    "Confirm Opt-Out / Suspension", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                cbOptedOutOrSuspended.setSelected(false);
                return;
            }
        }
        onFlagsChanged();
    }

    private void onViewHistory() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        List<AeContributionPeriodDTO> history = controller.getContributionHistory(selected.id());
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Period", "Employee €", "Employer €", "State €", "Total €"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (AeContributionPeriodDTO row : history) {
            model.addRow(new Object[] {row.periodNumber(), row.employeeAmount().toPlainString(), row.employerAmount().toPlainString(),
                    row.stateAmount().toPlainString(), row.totalAmount().toPlainString()});
        }
        JTable table = new JTable(model);
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Contribution History - " + selected.surname() + ", " + selected.firstName(),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.getContentPane().add(new JScrollPane(table));
        dialog.setSize(520, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void onTriggerAecs() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        btTriggerAecsSubmission.setEnabled(false);
        SwingWorker<AecsSubmissionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AecsSubmissionResult doInBackground() {
                return controller.submitAecs(DEMO_COMPANY, 1);
            }

            @Override
            protected void done() {
                btTriggerAecsSubmission.setEnabled(true);
                try {
                    AecsSubmissionResult result = get();
                    JOptionPane.showMessageDialog(AutoEnrolmentPanel.this, result.message(), "AECS Submission", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AutoEnrolmentPanel.this, "AECS submission failed: " + ex.getMessage(), "AECS Submission", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
