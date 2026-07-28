package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RemittanceDtos.DateRange;
import com.blueseer.pay.RemittanceDtos.PaymentRecordRowDTO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DateFormatter;

/** S-68 Revenue Payments Record. */
public class RevenuePaymentsRecordPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IRemittanceController controller;

    private JFormattedTextField dcDateFrom;
    private JFormattedTextField dcDateTo;
    private DefaultTableModel model;

    public RevenuePaymentsRecordPanel() {
        this(new InMemoryRemittanceController(PayrollStubStore.shared()));
    }

    public RevenuePaymentsRecordPanel(IRemittanceController controller) {
        this.controller = controller;
        initComponents();
        reload();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Revenue Payments Record"));
        content.setPreferredSize(new Dimension(560, 360));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("From:"));
        dcDateFrom = newDateField(LocalDate.of(2026, 1, 1));
        north.add(dcDateFrom);
        north.add(new JLabel("To:"));
        dcDateTo = newDateField(LocalDate.of(2026, 12, 31));
        north.add(dcDateTo);
        content.add(north, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[] {"Date", "Amount", "Reference", "Method"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btPrint = new JButton("Print");
        btPrint.addActionListener(e -> JOptionPane.showMessageDialog(this, "Printing is handled by the browser/OS print dialog on the rendered report - no data to print yet.",
                "Print", JOptionPane.INFORMATION_MESSAGE));
        south.add(btPrint);
        content.add(south, BorderLayout.SOUTH);

        dcDateFrom.getDocument().addDocumentListener(reloadListener());
        dcDateTo.getDocument().addDocumentListener(reloadListener());

        add(content);
    }

    private javax.swing.event.DocumentListener reloadListener() {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }
        };
    }

    private void reload() {
        if (model == null) {
            return;
        }
        model.setRowCount(0);
        DateRange range = new DateRange(asDate(dcDateFrom), asDate(dcDateTo));
        for (PaymentRecordRowDTO row : controller.getPaymentsRecord(DEMO_COMPANY, range)) {
            model.addRow(new Object[] {row.date(), row.amount().toPlainString(), row.reference(), row.method()});
        }
    }

    private static JFormattedTextField newDateField(LocalDate initial) {
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(Date.from(initial.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        field.setColumns(10);
        return field;
    }

    private static LocalDate asDate(JFormattedTextField field) {
        Object value = field.getValue();
        if (value instanceof Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }
}
