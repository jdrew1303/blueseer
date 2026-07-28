package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.DateFormatter;

/**
 * S-07a Addition/Deduction End-Date utility, per
 * docs/architecture/irish-payroll-2026-screen-specs.md. Shared by S-07
 * (Additions) and S-08 (Deductions) - the caller refreshes its own End Date
 * cell for the row after {@link #wasSaved()} returns true.
 */
public class AdditionDeductionEndDateDialog extends JDialog {

    private final IAdditionDeductionService service;
    private final EmployeeId employeeId;
    private final LineItemId lineId;

    private JFormattedTextField dcEndDate;
    private boolean saved;
    private LocalDate resultEndDate;

    public AdditionDeductionEndDateDialog(Frame owner, IAdditionDeductionService service,
            EmployeeId employeeId, LineItemId lineId, String description, LocalDate currentEndDate) {
        super(owner, "Set End Date", true);
        this.service = service;
        this.employeeId = employeeId;
        this.lineId = lineId;
        initComponents(description, currentEndDate);
        setSize(320, 150);
        setLocation(60, 80);
    }

    public boolean wasSaved() {
        return saved;
    }

    public LocalDate getResultEndDate() {
        return resultEndDate;
    }

    private void initComponents(String description, LocalDate currentEndDate) {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblDescTitle = new JLabel("Line item:");
        JLabel lblDescription = new JLabel(description);
        JLabel lblEndDateTitle = new JLabel("End Date");
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        format.setLenient(false);
        dcEndDate = new JFormattedTextField(new DateFormatter(format));
        dcEndDate.setColumns(10);
        if (currentEndDate != null) {
            dcEndDate.setValue(Date.from(currentEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup().addComponent(lblDescTitle).addComponent(lblDescription))
                .addGroup(layout.createSequentialGroup().addComponent(lblEndDateTitle).addComponent(dcEndDate)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDescTitle).addComponent(lblDescription))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEndDateTitle).addComponent(dcEndDate)));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btOk = new JButton("OK");
        btOk.addActionListener(e -> onOk());
        JButton btCancel = new JButton("Cancel");
        btCancel.addActionListener(e -> dispose());
        buttons.add(btOk);
        buttons.add(btCancel);

        setLayout(new java.awt.BorderLayout());
        add(panel, java.awt.BorderLayout.CENTER);
        add(buttons, java.awt.BorderLayout.SOUTH);
    }

    private void onOk() {
        Object value = dcEndDate.getValue();
        LocalDate endDate = value instanceof Date date ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
        service.setAdditionDeductionEndDate(employeeId, lineId, endDate);
        resultEndDate = endDate;
        saved = true;
        dispose();
    }
}
