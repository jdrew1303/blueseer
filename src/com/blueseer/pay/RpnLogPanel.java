package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RpnDtos.RpnLogEntryDTO;
import com.blueseer.pay.RpnDtos.RpnLogFilterDTO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DateFormatter;

/** S-16 RPN Logs & Reminders. */
public class RpnLogPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IRpnGateway gateway;
    private final IEmployeeRepository employeeRepository;

    private JFormattedTextField dcDateFrom;
    private JFormattedTextField dcDateTo;
    private JComboBox<EmployeeSummaryDTO> cbEmployeeFilter;
    private DefaultTableModel model;
    private JLabel lblOverdueBanner;

    public RpnLogPanel() {
        this(new InMemoryRpnGateway(PayrollStubStore.shared()), new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public RpnLogPanel(IRpnGateway gateway, IEmployeeRepository employeeRepository) {
        this.gateway = gateway;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
        reload();
        refreshOverdueBanner();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshEmployees();
            reload();
            refreshOverdueBanner();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("RPN Logs & Reminders"));
        content.setPreferredSize(new Dimension(600, 400));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("From:"));
        dcDateFrom = newDateField(LocalDate.of(2026, 1, 1));
        north.add(dcDateFrom);
        north.add(new JLabel("To:"));
        dcDateTo = newDateField(LocalDate.of(2026, 12, 31));
        north.add(dcDateTo);
        north.add(new JLabel("Employee:"));
        cbEmployeeFilter = new JComboBox<>();
        cbEmployeeFilter.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "All Employees" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        north.add(cbEmployeeFilter);
        content.add(north, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[] {"Date", "Employee", "Request Type", "Result"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblOverdueBanner = new JLabel("RPNs have not been retrieved for the current pay period");
        lblOverdueBanner.setOpaque(true);
        lblOverdueBanner.setBackground(new Color(255, 214, 102));
        lblOverdueBanner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        south.add(lblOverdueBanner);
        JButton btRetrieveNow = new JButton("Retrieve Now");
        btRetrieveNow.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            new RpnBulkRetrievalDialog(owner, gateway, DEMO_COMPANY).setVisible(true);
            reload();
            refreshOverdueBanner();
        });
        south.add(btRetrieveNow);
        content.add(south, BorderLayout.SOUTH);

        dcDateFrom.getDocument().addDocumentListener(reloadListener());
        dcDateTo.getDocument().addDocumentListener(reloadListener());
        cbEmployeeFilter.addActionListener(e -> reload());

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

    private void refreshEmployees() {
        EmployeeSummaryDTO previous = (EmployeeSummaryDTO) cbEmployeeFilter.getSelectedItem();
        cbEmployeeFilter.removeAllItems();
        cbEmployeeFilter.addItem(null);
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            cbEmployeeFilter.addItem(summary);
        }
        if (previous != null) {
            cbEmployeeFilter.setSelectedItem(previous);
        }
    }

    private void reload() {
        if (model == null) {
            return;
        }
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeFilter.getSelectedItem();
        RpnLogFilterDTO filter = new RpnLogFilterDTO(DEMO_COMPANY, asDate(dcDateFrom), asDate(dcDateTo), selected == null ? null : selected.id());
        model.setRowCount(0);
        for (RpnLogEntryDTO entry : gateway.getRpnLog(filter)) {
            model.addRow(new Object[] {entry.date(), entry.employeeId() == null ? "(all)" : entry.employeeId().value(),
                    entry.requestType(), entry.result()});
        }
    }

    private void refreshOverdueBanner() {
        lblOverdueBanner.setVisible(gateway.isRpnOverdueForCurrentPeriod(DEMO_COMPANY));
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
