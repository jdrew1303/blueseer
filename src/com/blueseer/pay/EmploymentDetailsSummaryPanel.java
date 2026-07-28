package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.ReportDtos.BatchGenerationResult;
import com.blueseer.pay.ReportDtos.EmploymentDetailsSummaryRequestDTO;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** S-47 Employment Details Summary (P60 replacement). */
public class EmploymentDetailsSummaryPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IReportController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<Integer> cbTaxYear;
    private DefaultListModel<EmployeeSummaryDTO> employeeListModel;
    private JList<EmployeeSummaryDTO> listEmployees;
    private JCheckBox cbBatchMode;
    private JButton btGenerate;

    public EmploymentDetailsSummaryPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()), new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public EmploymentDetailsSummaryPanel(IReportController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
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
        content.setBorder(BorderFactory.createTitledBorder("Employment Details Summary"));
        content.setPreferredSize(new java.awt.Dimension(500, 420));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Tax Year:"));
        cbTaxYear = new JComboBox<>();
        for (Integer year : PayrollEngineFactory.registeredTaxYears()) {
            cbTaxYear.addItem(year);
        }
        north.add(cbTaxYear);
        content.add(north, BorderLayout.NORTH);

        JPanel west = new JPanel(new BorderLayout());
        employeeListModel = new DefaultListModel<>();
        listEmployees = new JList<>(employeeListModel);
        listEmployees.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        listEmployees.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")");
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        west.add(new JScrollPane(listEmployees), BorderLayout.CENTER);

        cbBatchMode = new JCheckBox("Generate for all employees");
        cbBatchMode.addActionListener(e -> listEmployees.setEnabled(!cbBatchMode.isSelected()));
        west.add(cbBatchMode, BorderLayout.SOUTH);
        content.add(west, BorderLayout.WEST);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btGenerate = new JButton("Generate");
        btGenerate.addActionListener(e -> onGenerate());
        south.add(btGenerate);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void refreshEmployees() {
        employeeListModel.clear();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            employeeListModel.addElement(summary);
        }
    }

    private void onGenerate() {
        Integer year = (Integer) cbTaxYear.getSelectedItem();
        int taxYear = year == null ? 2026 : year;
        boolean batchMode = cbBatchMode.isSelected();
        List<EmployeeId> selectedIds = new ArrayList<>();
        for (EmployeeSummaryDTO summary : listEmployees.getSelectedValuesList()) {
            selectedIds.add(summary.id());
        }
        if (!batchMode && selectedIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one employee, or tick \"Generate for all employees\".",
                    "Employment Details Summary", JOptionPane.WARNING_MESSAGE);
            return;
        }

        EmploymentDetailsSummaryRequestDTO req = new EmploymentDetailsSummaryRequestDTO(DEMO_COMPANY, taxYear, selectedIds, batchMode);
        btGenerate.setEnabled(false);
        SwingWorker<BatchGenerationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected BatchGenerationResult doInBackground() {
                return controller.generateEmploymentDetailsSummary(req);
            }

            @Override
            protected void done() {
                btGenerate.setEnabled(true);
                try {
                    BatchGenerationResult result = get();
                    StringBuilder message = new StringBuilder();
                    message.append(result.generatedCount()).append(" generated, ").append(result.skippedCount()).append(" skipped.");
                    for (String reason : result.skipReasons()) {
                        message.append('\n').append(reason);
                    }
                    JOptionPane.showMessageDialog(EmploymentDetailsSummaryPanel.this, message.toString(),
                            "Employment Details Summary", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EmploymentDetailsSummaryPanel.this, "Generation failed: " + ex.getMessage(),
                            "Employment Details Summary", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
