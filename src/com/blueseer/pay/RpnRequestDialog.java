package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.RpnDtos.RpnDataDTO;
import com.blueseer.pay.RpnDtos.RpnRequestOutcome;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** S-14 RPN Request (new starter). */
public class RpnRequestDialog extends JDialog {

    private static final String CONNECTING = "connecting";
    private static final String REGISTRATION_REQUIRED = "registration-required";
    private static final String BLOCKED_NO_PPS = "blocked-no-pps";
    private static final String BLOCKED_FIRST_EMPLOYMENT = "blocked-first-employment";
    private static final String RESULT = "result";

    private final IRpnGateway gateway;
    private final EmployeeId employeeId;

    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JButton btUpdate;
    private JButton btClose;
    private DefaultTableModel resultModel;
    private RpnDataDTO pendingData;
    private boolean rpnApplied;

    public RpnRequestDialog(Window owner, IRpnGateway gateway, EmployeeId employeeId) {
        super(owner, "Request RPN - New Starter", ModalityType.APPLICATION_MODAL);
        this.gateway = gateway;
        this.employeeId = employeeId;
        initComponents();
        setSize(460, 320);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        JPanel connecting = new JPanel(new BorderLayout());
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        connecting.add(progress, BorderLayout.CENTER);
        connecting.add(new JLabel("Contacting Revenue...", JLabel.CENTER), BorderLayout.NORTH);
        cardPanel.add(connecting, CONNECTING);

        JPanel regRequired = new JPanel(new BorderLayout());
        regRequired.add(new JLabel("<html><body style='width: 320px'>It is detected that the employee's new employment "
                + "has not yet been registered with Revenue.</body></html>"), BorderLayout.CENTER);
        JPanel regButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton btConfirmRegister = new JButton("Confirm Registration");
        btConfirmRegister.addActionListener(e -> {
            cardLayout.show(cardPanel, CONNECTING);
            runOnWorker(gateway::confirmEmploymentRegistration);
        });
        JButton btCancelReg = new JButton("Cancel");
        btCancelReg.addActionListener(e -> dispose());
        regButtons.add(btConfirmRegister);
        regButtons.add(btCancelReg);
        regRequired.add(regButtons, BorderLayout.SOUTH);
        cardPanel.add(regRequired, REGISTRATION_REQUIRED);

        JPanel noPps = new JPanel(new BorderLayout());
        noPps.add(new JLabel("<html><body style='width: 320px'>An RPN response can only be received "
                + "where a PPS number has been provided.</body></html>"), BorderLayout.CENTER);
        cardPanel.add(noPps, BLOCKED_NO_PPS);

        JPanel firstEmployment = new JPanel(new BorderLayout());
        firstEmployment.add(new JLabel("<html><body style='width: 320px'>The employee must self-register via the "
                + "Jobs and Pensions online service before an RPN can exist.</body></html>"), BorderLayout.CENTER);
        cardPanel.add(firstEmployment, BLOCKED_FIRST_EMPLOYMENT);

        JPanel result = new JPanel(new BorderLayout());
        resultModel = new DefaultTableModel(new Object[] {"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        result.add(new JScrollPane(new JTable(resultModel)), BorderLayout.CENTER);
        cardPanel.add(result, RESULT);

        add(cardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btUpdate = new JButton("Update");
        btUpdate.setEnabled(false);
        btUpdate.addActionListener(e -> onUpdate());
        btClose = new JButton("Close");
        btClose.addActionListener(e -> dispose());
        south.add(btUpdate);
        south.add(btClose);
        add(south, BorderLayout.SOUTH);

        cardLayout.show(cardPanel, CONNECTING);
    }

    /** Called once, right after construction, to fire the initial request - kept separate from the constructor so callers control timing. */
    public void start() {
        runOnWorker(gateway::requestNewStarterRpn);
    }

    private void runOnWorker(java.util.function.Function<EmployeeId, RpnRequestOutcome> call) {
        SwingWorker<RpnRequestOutcome, Void> worker = new SwingWorker<>() {
            @Override
            protected RpnRequestOutcome doInBackground() {
                return call.apply(employeeId);
            }

            @Override
            protected void done() {
                try {
                    route(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(RpnRequestDialog.this, "RPN request failed: " + ex.getMessage(),
                            "Request RPN", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void route(RpnRequestOutcome outcome) {
        btUpdate.setEnabled(false);
        if (outcome instanceof RpnRequestOutcome.Success success) {
            pendingData = success.data();
            resultModel.setRowCount(0);
            resultModel.addRow(new Object[] {"Tax Credit", pendingData.annualTaxCredit()});
            resultModel.addRow(new Object[] {"Cut-Off Point", pendingData.annualCutOffPoint()});
            resultModel.addRow(new Object[] {"PRSI Class", pendingData.prsiClass()});
            btUpdate.setEnabled(true);
            cardLayout.show(cardPanel, RESULT);
        } else if (outcome instanceof RpnRequestOutcome.RegistrationRequired) {
            cardLayout.show(cardPanel, REGISTRATION_REQUIRED);
        } else if (outcome instanceof RpnRequestOutcome.NoPpsBlocked) {
            cardLayout.show(cardPanel, BLOCKED_NO_PPS);
        } else if (outcome instanceof RpnRequestOutcome.FirstEmploymentBlocked) {
            cardLayout.show(cardPanel, BLOCKED_FIRST_EMPLOYMENT);
        } else if (outcome instanceof RpnRequestOutcome.ServiceError error) {
            JOptionPane.showMessageDialog(this, "Service error: " + error.message(), "Request RPN", JOptionPane.ERROR_MESSAGE);
            dispose();
        }
    }

    private void onUpdate() {
        if (pendingData == null) {
            return;
        }
        gateway.applyRpnData(employeeId, pendingData);
        rpnApplied = true;
        JOptionPane.showMessageDialog(this, "Confirm that the new employee has been updated with the Revenue information.",
                "Request RPN", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    public boolean wasRpnApplied() {
        return rpnApplied;
    }
}
