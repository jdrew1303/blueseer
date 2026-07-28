package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailPayslipsRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailRecipientDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendStatus;
import com.blueseer.pay.PayslipDistributionDtos.PrintPayslipsRequestDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** S-37 Emailing Payslips, opened from S-36's btEmail. */
public class EmailPayslipsDialog extends JDialog {

    private static final String PLACEHOLDER_FROM_ADDRESS = "payroll@democompany.ie";
    private static final int COL_EMPLOYEE = 0;
    private static final int COL_EMAIL = 1;
    private static final int COL_STATUS = 2;

    private final IPayslipDistributionController controller;
    private final PrintPayslipsRequestDTO printReq;
    private final List<EmployeeId> employeeOrder;

    private JCheckBox cbPasswordProtect;
    private JPasswordField pfPdfPassword;
    private DefaultTableModel tableModel;
    private JTable tblRecipients;
    private JButton btSendEmails;

    public EmailPayslipsDialog(Window owner, IPayslipDistributionController controller,
            IEmployeeRepository employeeRepository, PrintPayslipsRequestDTO printReq) {
        super(owner, "Email Payslips", ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.printReq = printReq;
        this.employeeOrder = new ArrayList<>(printReq.employeeIds());
        initComponents(employeeRepository);
        setSize(600, 450);
        setLocationRelativeTo(owner);
    }

    private void initComponents(IEmployeeRepository employeeRepository) {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Email Payslips"));
        content.setPreferredSize(new java.awt.Dimension(560, 400));

        JPanel north = new JPanel();
        north.setLayout(new javax.swing.BoxLayout(north, javax.swing.BoxLayout.Y_AXIS));

        JPanel fromRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        fromRow.add(new JLabel("From:"));
        JLabel lblFromAddress = new JLabel(PLACEHOLDER_FROM_ADDRESS);
        fromRow.add(lblFromAddress);
        north.add(fromRow);

        JPanel pwRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        cbPasswordProtect = new JCheckBox("Password-protect PDF");
        pfPdfPassword = new JPasswordField(15);
        pfPdfPassword.setEnabled(false);
        cbPasswordProtect.addActionListener(e -> pfPdfPassword.setEnabled(cbPasswordProtect.isSelected()));
        pwRow.add(cbPasswordProtect);
        pwRow.add(pfPdfPassword);
        north.add(pwRow);

        content.add(north, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[] {"Employee", "Email Address", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_EMAIL;
            }
        };
        for (EmployeeId id : employeeOrder) {
            EmployeeRecordDTO rec = employeeRepository.loadEmployeeRecord(id);
            String name = rec == null ? "" : rec.personalDetails().surname() + ", " + rec.personalDetails().firstName();
            String email = rec == null ? "" : rec.personalDetails().email();
            tableModel.addRow(new Object[] {name, email == null ? "" : email, "Pending"});
        }
        tblRecipients = new JTable(tableModel);
        content.add(new JScrollPane(tblRecipients), BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btSendEmails = new JButton("Send Emails");
        btSendEmails.addActionListener(e -> onSendEmails());
        south.add(btSendEmails);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void onSendEmails() {
        btSendEmails.setEnabled(false);
        List<EmailRecipientDTO> recipients = new ArrayList<>();
        for (int row = 0; row < employeeOrder.size(); row++) {
            String email = String.valueOf(tableModel.getValueAt(row, COL_EMAIL));
            recipients.add(new EmailRecipientDTO(employeeOrder.get(row), String.valueOf(tableModel.getValueAt(row, COL_EMPLOYEE)), email));
        }
        char[] pwChars = pfPdfPassword.getPassword();
        String password = cbPasswordProtect.isSelected() && pwChars.length > 0 ? new String(pwChars) : null;

        EmailPayslipsRequestDTO req = new EmailPayslipsRequestDTO(printReq.companyId(), printReq.periodNumber(), recipients,
                printReq.includeZeroPayment(), printReq.type(), cbPasswordProtect.isSelected(), password);

        SwingWorker<EmailSendResult, Void> worker = new SwingWorker<>() {
            @Override
            protected EmailSendResult doInBackground() {
                return controller.sendPayslipEmails(req, (id, status) -> SwingUtilities.invokeLater(() -> updateRowStatus(id, status)));
            }

            @Override
            protected void done() {
                btSendEmails.setEnabled(true);
                try {
                    EmailSendResult result = get();
                    javax.swing.JOptionPane.showMessageDialog(EmailPayslipsDialog.this,
                            result.sentCount() + " sent, " + result.failedCount() + " failed.", "Email Payslips",
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(EmailPayslipsDialog.this, "Send failed: " + ex.getMessage(),
                            "Email Payslips", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateRowStatus(EmployeeId id, EmailSendStatus status) {
        int row = employeeOrder.indexOf(id);
        if (row >= 0) {
            tableModel.setValueAt(status == EmailSendStatus.SENT ? "Sent" : "Failed", row, COL_STATUS);
        }
    }
}
