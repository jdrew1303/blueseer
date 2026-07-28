package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * S-25 Directors Fees, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * A simplified variant of S-18's shape for proprietary directors, who are
 * typically paid a fee rather than hours. The PRSI class indicator is
 * read-only - {@code PayslipEngineChain} decides Class S vs Class A from the
 * employee's own director flag, this screen never chooses it.
 */
public class DirectorsFeesPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPayEntryController payEntryController;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbDirectorSelector;
    private JFormattedTextField tbFeeAmount;
    private JLabel lblPrsiClassIndicator;

    public DirectorsFeesPanel() {
        this(new InMemoryPayEntryController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public DirectorsFeesPanel(IPayEntryController payEntryController, IEmployeeRepository employeeRepository) {
        this.payEntryController = payEntryController;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshDirectorSelector();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshDirectorSelector();
        }
    }

    private void initComponents() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Directors Fees"));
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblDirector = new JLabel("Director:");
        cbDirectorSelector = new JComboBox<>();
        cbDirectorSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbDirectorSelector.addActionListener(e -> onDirectorSelected());

        JLabel lblFee = new JLabel("Fee Amount:");
        tbFeeAmount = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbFeeAmount.setColumns(10);

        JLabel lblPrsiClassLabel = new JLabel("PRSI Class:");
        lblPrsiClassIndicator = new JLabel("-");

        JLabel lblShareholdingLabel = new JLabel("Shareholding %:");
        JLabel lblShareholding = new JLabel("(not yet captured - see Revenue Details tab)");
        lblShareholding.setFont(lblShareholding.getFont().deriveFont(java.awt.Font.ITALIC));

        JButton btUpdateFile = new JButton("Update File");
        btUpdateFile.addActionListener(e -> onUpdateFile());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblDirector).addComponent(lblFee)
                                .addComponent(lblPrsiClassLabel).addComponent(lblShareholdingLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbDirectorSelector).addComponent(tbFeeAmount)
                                .addComponent(lblPrsiClassIndicator).addComponent(lblShareholding)))
                .addComponent(btUpdateFile));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDirector).addComponent(cbDirectorSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFee).addComponent(tbFeeAmount))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPrsiClassLabel).addComponent(lblPrsiClassIndicator))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblShareholdingLabel).addComponent(lblShareholding))
                .addComponent(btUpdateFile));

        add(panel);
    }

    private void refreshDirectorSelector() {
        EmployeeSummaryDTO previouslySelected = (EmployeeSummaryDTO) cbDirectorSelector.getSelectedItem();
        cbDirectorSelector.removeAllItems();
        List<EmployeeSummaryDTO> directors = new ArrayList<>();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (summary.isFormerEmployee()) {
                continue;
            }
            EmployeeRecordDTO rec = employeeRepository.loadEmployeeRecord(summary.id());
            if (rec != null && rec.personalDetails().director()) {
                directors.add(summary);
            }
        }
        for (EmployeeSummaryDTO director : directors) {
            cbDirectorSelector.addItem(director);
            if (director.equals(previouslySelected)) {
                cbDirectorSelector.setSelectedItem(director);
            }
        }
        onDirectorSelected();
    }

    private void onDirectorSelected() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbDirectorSelector.getSelectedItem();
        lblPrsiClassIndicator.setText(selected == null ? "-" : "Class S");
    }

    private void onUpdateFile() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbDirectorSelector.getSelectedItem();
        if (selected == null || tbFeeAmount.getValue() == null) {
            return;
        }
        BigDecimal fee = new BigDecimal(tbFeeAmount.getValue().toString());
        int currentPeriod = payEntryController.getCurrentPeriodNumber(DEMO_COMPANY);
        payEntryController.saveDirectorFee(selected.id(), currentPeriod, fee);
        JOptionPane.showMessageDialog(this, "Director fee updated.", "Update File", JOptionPane.INFORMATION_MESSAGE);
    }
}
