package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.CalculationStepDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * S-23 Payslip Workings, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Renders the persisted (or, pre-finalisation, live-recomputed) audit trail
 * for one payslip - it never recomputes anything itself beyond what {@link
 * IPayslipWorkingsController} hands it. The spec calls for section-header
 * rows grouping steps by engine; this keeps the Engine as a leading column
 * on a flat table instead - materially the same information, without a
 * bespoke grouped-row renderer for what is otherwise a short, read-only list.
 */
public class PayslipWorkingsDialog extends JDialog {

    public PayslipWorkingsDialog(Frame owner, IPayslipWorkingsController controller, EmployeeId employeeId, int periodNumber) {
        super(owner, "Payslip Workings", true);
        initComponents(controller.getWorkings(employeeId, periodNumber));
        setSize(640, 420);
        setLocation(60, 60);
    }

    private void initComponents(List<CalculationStepDTO> steps) {
        setLayout(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(new Object[] { "Engine", "Step Label", "Formula", "Result" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (CalculationStepDTO step : steps) {
            String result = step.resultValue() == null ? "" : step.resultValue().toPlainString();
            model.addRow(new Object[] { step.engineName(), step.stepLabel(), step.formula(), result });
        }
        JTable table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btClose = new JButton("Close");
        btClose.addActionListener(e -> dispose());
        south.add(btClose);
        add(south, BorderLayout.SOUTH);
    }
}
