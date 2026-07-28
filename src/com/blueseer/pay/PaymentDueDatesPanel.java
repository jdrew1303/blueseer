package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RemittanceDtos.PaymentDueDateDTO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** S-67 Payment Due Dates. */
public class PaymentDueDatesPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final int TAX_YEAR = 2026;

    public PaymentDueDatesPanel() {
        this(new InMemoryRemittanceController(PayrollStubStore.shared()));
    }

    public PaymentDueDatesPanel(IRemittanceController controller) {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Payment Due Dates"));
        content.setPreferredSize(new Dimension(480, 320));

        DefaultTableModel model = new DefaultTableModel(new Object[] {"Period", "Amount Category", "Due Date"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (PaymentDueDateDTO row : controller.getPaymentDueDates(DEMO_COMPANY, TAX_YEAR)) {
            model.addRow(new Object[] {row.period(), row.amountCategory(), row.dueDate()});
        }
        JTable table = new JTable(model);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        add(content);
    }
}
