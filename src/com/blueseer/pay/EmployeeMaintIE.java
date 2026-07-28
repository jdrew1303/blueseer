package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.CsoDetailsDTO;
import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.EmployeeDtos.EmergencyStatusDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.EmployeeDtos.HrDetailsDTO;
import com.blueseer.pay.EmployeeDtos.PayMethod;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.EmployeeDtos.RevenueDetailsDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;
import javax.swing.text.DateFormatter;

/**
 * S-04 Employee Maintenance shell + S-05 Personal Details + S-06 Revenue
 * Details tabs, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Same JTabbedPane-in-a-bordered-panel shell as the existing
 * com.blueseer.hrm.EmployeeMaint, but wired to this module's own §4.1
 * interfaces rather than emp_mstr's generic HR fields directly.
 *
 * <p>Two constructors, per the roadmap's Track A design: the no-arg one
 * (required for MainFrame's reflection-based menu loader) wires up an
 * in-memory stub so the screen is fully interactive before any real
 * repository/service implementation exists; the injectable one is what
 * Track E's real wiring will use.
 */
public class EmployeeMaintIE extends JPanel {

    private static final Pattern PPS_PATTERN = Pattern.compile("^\\d{7}[A-Za-z]{1,2}$");
    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final DepartmentDTO MANAGE_DEPARTMENTS_SENTINEL = new DepartmentDTO(null, "", "");

    private final IEmployeeRepository employeeRepository;
    private final IRevenueDetailsService revenueDetailsService;
    private final IAdditionDeductionService additionDeductionService;
    private final IDepartmentController departmentController;
    private final IRpnGateway rpnGateway;

    private JComboBox<EmployeeSummaryDTO> cbSurnameLookup;
    private JButton btAdd;
    private JButton btUpdate;
    private JButton btDelete;
    private JTabbedPane jTabbedPane1;

    // Personal Details tab
    private JTextField tbSurname;
    private JTextField tbFirstName;
    private JTextArea taAddress;
    private JFormattedTextField dcDateOfBirth;
    private JTextField tbEmail;
    private JCheckBox cbDirector;
    private JComboBox<DepartmentDTO> cbDepartment;
    private JTextField tbPpsNumber;
    private JTextField tbEmploymentId;
    private JTextField tbWorksNumber;
    private JFormattedTextField tbHourlyRate;
    private JFormattedTextField tbFixedPay;
    private JRadioButton rbCash;
    private JRadioButton rbCheque;
    private JRadioButton rbCreditTransfer;
    private JPanel bankDetailsPanel;
    private JTextField tbBank;
    private JTextField tbBranch;
    private JTextField tbSortCode;
    private JTextField tbAccountNumber;
    private JTextField tbCreditUnionRef;

    // Revenue Details tab
    private JLabel lblEmergencyBanner;
    private JFormattedTextField dcStartDate;
    private JFormattedTextField tbStartWeek;
    private JComboBox<String> cbPrsiClass;
    private JCheckBox cbUscExempt;
    private JCheckBox cbPrsiExempt;

    // Additions / Deductions tabs (S-07 / S-08)
    private LineItemTablePanel additionsPanel;
    private LineItemTablePanel deductionsPanel;

    // Mid-Year Cumulatives tab (S-10)
    private JFormattedTextField tbPriorGrossPay;
    private JFormattedTextField tbPriorTaxPaid;
    private JFormattedTextField tbPriorPrsiPaid;
    private JFormattedTextField tbPriorUscPaid;
    private JTextField tbLeaveDate;

    // HR Details tab (S-11)
    private JTextField tbJobTitle;
    private JComboBox<String> cbContractType;
    private JTextField tbEmergencyContactName;
    private JFormattedTextField tbEmergencyContactPhone;

    // CSO Details tab (S-12)
    private JComboBox<String> cbOccupationCode;
    private JComboBox<String> cbHoursCategory;

    private EmployeeId currentEmployeeId;
    private boolean isNewEmployee;
    private boolean isRefreshingLookup;

    public EmployeeMaintIE() {
        this(sharedStubStore());
    }

    private EmployeeMaintIE(PayrollStubStore store) {
        this(new InMemoryEmployeeRepository(store), new InMemoryRevenueDetailsService(store),
                new InMemoryAdditionDeductionService(store), new InMemoryDepartmentController(store),
                new InMemoryRpnGateway(store));
    }

    private static PayrollStubStore sharedStubStore() {
        return PayrollStubStore.shared();
    }

    public EmployeeMaintIE(IEmployeeRepository employeeRepository, IRevenueDetailsService revenueDetailsService,
            IAdditionDeductionService additionDeductionService, IDepartmentController departmentController) {
        this(employeeRepository, revenueDetailsService, additionDeductionService, departmentController,
                new InMemoryRpnGateway(PayrollStubStore.shared()));
    }

    public EmployeeMaintIE(IEmployeeRepository employeeRepository, IRevenueDetailsService revenueDetailsService,
            IAdditionDeductionService additionDeductionService, IDepartmentController departmentController,
            IRpnGateway rpnGateway) {
        this.employeeRepository = employeeRepository;
        this.revenueDetailsService = revenueDetailsService;
        this.additionDeductionService = additionDeductionService;
        this.departmentController = departmentController;
        this.rpnGateway = rpnGateway;
        initComponents();
        refreshSurnameLookup();
        clearForNewEmployee();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Employee Maintenance"));
        content.setPreferredSize(new java.awt.Dimension(720, 650));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(new JLabel("Surname:"));
        cbSurnameLookup = new JComboBox<>();
        cbSurnameLookup.setPreferredSize(new java.awt.Dimension(220, cbSurnameLookup.getPreferredSize().height));
        cbSurnameLookup.addItemListener(this::onSurnameSelected);
        topBar.add(cbSurnameLookup);

        btAdd = new JButton("Add");
        btAdd.addActionListener(e -> clearForNewEmployee());
        btUpdate = new JButton("Update");
        btUpdate.addActionListener(this::onUpdateClicked);
        btDelete = new JButton("Delete");
        btDelete.addActionListener(this::onDeleteClicked);
        topBar.add(btAdd);
        topBar.add(btUpdate);
        topBar.add(btDelete);

        content.add(topBar, BorderLayout.NORTH);

        jTabbedPane1 = new JTabbedPane();
        jTabbedPane1.addTab("Personal Details", buildPersonalDetailsTab());
        jTabbedPane1.addTab("Revenue Details", buildRevenueDetailsTab());
        jTabbedPane1.addTab("Additions", buildAdditionsTab());
        jTabbedPane1.addTab("Deductions", buildDeductionsTab());
        jTabbedPane1.addTab("Mid-Year Cumulatives", buildMidYearCumulativesTab());
        jTabbedPane1.addTab("HR Details", buildHrDetailsTab());
        jTabbedPane1.addTab("CSO Details", buildCsoDetailsTab());
        jTabbedPane1.addChangeListener(e -> {
            if (jTabbedPane1.getSelectedIndex() == 4) {
                refreshLeaveDate();
            }
        });
        content.add(jTabbedPane1, BorderLayout.CENTER);

        add(content);
    }

    private JPanel buildPersonalDetailsTab() {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblSurname = new JLabel("Surname");
        tbSurname = new JTextField(20);
        JLabel lblFirstName = new JLabel("First Name");
        tbFirstName = new JTextField(20);
        JLabel lblAddress = new JLabel("Address");
        taAddress = new JTextArea(3, 20);
        JScrollPane addressScroll = new JScrollPane(taAddress);
        JLabel lblDob = new JLabel("Date of Birth");
        dcDateOfBirth = new JFormattedTextField(dateFormatter());
        dcDateOfBirth.setColumns(10);
        JLabel lblEmail = new JLabel("Email");
        tbEmail = new JTextField(20);
        cbDirector = new JCheckBox("Director");
        JLabel lblDepartment = new JLabel("Department");
        cbDepartment = new JComboBox<>();
        refreshDepartmentCombo();
        cbDepartment.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "(none)"
                        : value == MANAGE_DEPARTMENTS_SENTINEL ? "— Manage Departments —"
                        : value.code() + " - " + value.name()));
        cbDepartment.addActionListener(e -> {
            if (cbDepartment.getSelectedItem() == MANAGE_DEPARTMENTS_SENTINEL) {
                openDepartmentDialog();
            }
        });
        JLabel lblPps = new JLabel("PPS Number");
        tbPpsNumber = new JTextField(10);
        tbPpsNumber.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                validatePpsOnFocusLost();
            }
        });
        JLabel lblEmploymentId = new JLabel("Employment ID");
        tbEmploymentId = new JTextField(15);
        tbEmploymentId.setEditable(false);
        tbEmploymentId.setBackground(Color.LIGHT_GRAY);
        JLabel lblWorksNumber = new JLabel("Works Number");
        tbWorksNumber = new JTextField(10);
        JLabel lblHourlyRate = new JLabel("Hourly Rate");
        tbHourlyRate = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        tbHourlyRate.setColumns(8);
        JLabel lblFixedPay = new JLabel("Fixed Pay");
        tbFixedPay = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        tbFixedPay.setColumns(8);

        JLabel lblPayMethod = new JLabel("Pay Method");
        rbCash = new JRadioButton("Cash");
        rbCheque = new JRadioButton("Cheque");
        rbCreditTransfer = new JRadioButton("Credit Transfer");
        ButtonGroup payMethodGroup = new ButtonGroup();
        payMethodGroup.add(rbCash);
        payMethodGroup.add(rbCheque);
        payMethodGroup.add(rbCreditTransfer);
        ActionListener payMethodListener = e -> bankDetailsPanel.setVisible(rbCreditTransfer.isSelected());
        rbCash.addActionListener(payMethodListener);
        rbCheque.addActionListener(payMethodListener);
        rbCreditTransfer.addActionListener(payMethodListener);
        JPanel payMethodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        payMethodPanel.add(rbCash);
        payMethodPanel.add(rbCheque);
        payMethodPanel.add(rbCreditTransfer);

        bankDetailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bankDetailsPanel.setBorder(BorderFactory.createTitledBorder("Bank Details"));
        tbBank = new JTextField(12);
        tbBranch = new JTextField(12);
        tbSortCode = new JTextField(8);
        tbAccountNumber = new JTextField(8);
        tbCreditUnionRef = new JTextField(10);
        bankDetailsPanel.add(new JLabel("Bank"));
        bankDetailsPanel.add(tbBank);
        bankDetailsPanel.add(new JLabel("Branch"));
        bankDetailsPanel.add(tbBranch);
        bankDetailsPanel.add(new JLabel("Sort Code"));
        bankDetailsPanel.add(tbSortCode);
        bankDetailsPanel.add(new JLabel("Account No."));
        bankDetailsPanel.add(tbAccountNumber);
        bankDetailsPanel.add(new JLabel("Credit Union Ref"));
        bankDetailsPanel.add(tbCreditUnionRef);
        bankDetailsPanel.setVisible(false);

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.TRAILING)
                                .addComponent(lblSurname).addComponent(lblFirstName).addComponent(lblAddress)
                                .addComponent(lblDob).addComponent(lblEmail).addComponent(lblDepartment)
                                .addComponent(lblPps)
                                .addComponent(lblEmploymentId).addComponent(lblWorksNumber)
                                .addComponent(lblHourlyRate).addComponent(lblFixedPay).addComponent(lblPayMethod))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbSurname).addComponent(tbFirstName).addComponent(addressScroll)
                                .addComponent(dcDateOfBirth).addComponent(tbEmail).addComponent(cbDirector)
                                .addComponent(cbDepartment)
                                .addComponent(tbPpsNumber).addComponent(tbEmploymentId).addComponent(tbWorksNumber)
                                .addComponent(tbHourlyRate).addComponent(tbFixedPay)
                                .addComponent(payMethodPanel).addComponent(bankDetailsPanel))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSurname).addComponent(tbSurname))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFirstName).addComponent(tbFirstName))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAddress).addComponent(addressScroll))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDob).addComponent(dcDateOfBirth))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmail).addComponent(tbEmail))
                .addComponent(cbDirector)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDepartment).addComponent(cbDepartment))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPps).addComponent(tbPpsNumber))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmploymentId).addComponent(tbEmploymentId))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblWorksNumber).addComponent(tbWorksNumber))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblHourlyRate).addComponent(tbHourlyRate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFixedPay).addComponent(tbFixedPay))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPayMethod).addComponent(payMethodPanel))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(bankDetailsPanel));

        return panel;
    }

    private JPanel buildRevenueDetailsTab() {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        lblEmergencyBanner = new JLabel("This employee will automatically be placed on emergency tax until updated by an RPN");
        lblEmergencyBanner.setOpaque(true);
        lblEmergencyBanner.setBackground(new Color(255, 214, 102));
        lblEmergencyBanner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel lblStartDate = new JLabel("Start Date");
        dcStartDate = new JFormattedTextField(dateFormatter());
        dcStartDate.setColumns(10);
        dcStartDate.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                recalculateStartWeek();
            }
        });
        JLabel lblStartWeek = new JLabel("Start Week");
        tbStartWeek = new JFormattedTextField(new java.text.DecimalFormat("#0"));
        tbStartWeek.setColumns(4);
        JLabel lblPrsiClass = new JLabel("PRSI Class");
        cbPrsiClass = new JComboBox<>(new String[]{"A1", "AX", "AL", "J9"});

        JPanel exemptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cbUscExempt = new JCheckBox("USC Exempt");
        cbPrsiExempt = new JCheckBox("PRSI Exempt");
        exemptionsPanel.add(cbUscExempt);
        exemptionsPanel.add(cbPrsiExempt);

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addComponent(lblEmergencyBanner)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.TRAILING)
                                .addComponent(lblStartDate).addComponent(lblStartWeek).addComponent(lblPrsiClass))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(dcStartDate).addComponent(tbStartWeek)
                                .addComponent(cbPrsiClass).addComponent(exemptionsPanel))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(lblEmergencyBanner)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStartDate).addComponent(dcStartDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStartWeek).addComponent(tbStartWeek))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPrsiClass).addComponent(cbPrsiClass))
                .addComponent(exemptionsPanel));

        return panel;
    }

    private JPanel buildAdditionsTab() {
        additionsPanel = new LineItemTablePanel(additionDeductionService, () -> currentEmployeeId,
                "Taxable?", true, false);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(additionsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDeductionsTab() {
        deductionsPanel = new LineItemTablePanel(additionDeductionService, () -> currentEmployeeId,
                "Pre-Tax?", false, true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(deductionsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMidYearCumulativesTab() {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblSectionNote = new JLabel("For employees who joined mid-year with pay/tax history from a previous employment or system this tax year");
        lblSectionNote.setFont(lblSectionNote.getFont().deriveFont(java.awt.Font.ITALIC));

        JLabel lblPriorGross = new JLabel("Prior Gross Pay");
        tbPriorGrossPay = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        tbPriorGrossPay.setColumns(10);
        JLabel lblPriorTax = new JLabel("Prior Tax Paid");
        tbPriorTaxPaid = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        tbPriorTaxPaid.setColumns(10);
        JLabel lblPriorPrsi = new JLabel("Prior PRSI Paid");
        tbPriorPrsiPaid = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        tbPriorPrsiPaid.setColumns(10);
        JLabel lblPriorUsc = new JLabel("Prior USC Paid");
        tbPriorUscPaid = new JFormattedTextField(new java.text.DecimalFormat("#,##0.00"));
        tbPriorUscPaid.setColumns(10);

        JLabel lblLeaveDate = new JLabel("Leave Date");
        tbLeaveDate = new JTextField(10);
        tbLeaveDate.setEditable(false);
        tbLeaveDate.setBackground(Color.LIGHT_GRAY);

        JButton btUpdateCumulatives = new JButton("Update");
        btUpdateCumulatives.addActionListener(e -> onUpdateCumulatives());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addComponent(lblSectionNote)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.TRAILING)
                                .addComponent(lblPriorGross).addComponent(lblPriorTax)
                                .addComponent(lblPriorPrsi).addComponent(lblPriorUsc).addComponent(lblLeaveDate))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbPriorGrossPay).addComponent(tbPriorTaxPaid)
                                .addComponent(tbPriorPrsiPaid).addComponent(tbPriorUscPaid).addComponent(tbLeaveDate)))
                .addComponent(btUpdateCumulatives));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(lblSectionNote)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPriorGross).addComponent(tbPriorGrossPay))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPriorTax).addComponent(tbPriorTaxPaid))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPriorPrsi).addComponent(tbPriorPrsiPaid))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPriorUsc).addComponent(tbPriorUscPaid))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblLeaveDate).addComponent(tbLeaveDate))
                .addComponent(btUpdateCumulatives));

        return panel;
    }

    private JPanel buildHrDetailsTab() {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblJobTitle = new JLabel("Job Title");
        tbJobTitle = new JTextField(20);
        JLabel lblContractType = new JLabel("Contract Type");
        cbContractType = new JComboBox<>(new String[]{"Permanent", "Fixed-Term", "Contractor"});
        JLabel lblEmergencyContactName = new JLabel("Emergency Contact Name");
        tbEmergencyContactName = new JTextField(20);
        JLabel lblEmergencyContactPhone = new JLabel("Emergency Contact Phone");
        tbEmergencyContactPhone = new JFormattedTextField();
        tbEmergencyContactPhone.setColumns(15);

        JButton btUpdateHrDetails = new JButton("Update");
        btUpdateHrDetails.addActionListener(e -> onUpdateHrDetails());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.TRAILING)
                                .addComponent(lblJobTitle).addComponent(lblContractType)
                                .addComponent(lblEmergencyContactName).addComponent(lblEmergencyContactPhone))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbJobTitle).addComponent(cbContractType)
                                .addComponent(tbEmergencyContactName).addComponent(tbEmergencyContactPhone)))
                .addComponent(btUpdateHrDetails));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblJobTitle).addComponent(tbJobTitle))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblContractType).addComponent(cbContractType))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmergencyContactName).addComponent(tbEmergencyContactName))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmergencyContactPhone).addComponent(tbEmergencyContactPhone))
                .addComponent(btUpdateHrDetails));

        return panel;
    }

    private JPanel buildCsoDetailsTab() {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblOccupationCode = new JLabel("Occupation Code");
        // CSO's own occupation classification list - placeholder subset pending
        // cross-check against the CSO's published reference list, per S-12's spec.
        cbOccupationCode = new JComboBox<>(new String[]{"", "2320 - Secondary Education Teaching Professionals",
                "5432 - Chefs", "9260 - Elementary Storage Occupations"});
        JLabel lblHoursCategory = new JLabel("Hours Category");
        cbHoursCategory = new JComboBox<>(new String[]{"", "Full-Time", "Part-Time"});

        JButton btUpdateCsoDetails = new JButton("Update");
        btUpdateCsoDetails.addActionListener(e -> onUpdateCsoDetails());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.TRAILING)
                                .addComponent(lblOccupationCode).addComponent(lblHoursCategory))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbOccupationCode).addComponent(cbHoursCategory)))
                .addComponent(btUpdateCsoDetails));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblOccupationCode).addComponent(cbOccupationCode))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblHoursCategory).addComponent(cbHoursCategory))
                .addComponent(btUpdateCsoDetails));

        return panel;
    }

    private static DateFormatter dateFormatter() {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        format.setLenient(false);
        return new DateFormatter(format);
    }

    private void refreshSurnameLookup() {
        // JComboBox auto-selects the first item added to an empty model,
        // which would otherwise fire onSurnameSelected and silently reload
        // that (wrong) employee's data over whatever the form currently
        // shows - suppressed here, and the correct selection restored below.
        isRefreshingLookup = true;
        cbSurnameLookup.removeAllItems();
        List<EmployeeSummaryDTO> results = employeeRepository.searchEmployees(DEMO_COMPANY, "");
        for (EmployeeSummaryDTO summary : results) {
            cbSurnameLookup.addItem(summary);
        }
        cbSurnameLookup.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            if (value == null) {
                return new JLabel("");
            }
            JLabel label = new JLabel(value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"
                    + (value.isFormerEmployee() ? "  ↩ rejoining" : ""));
            if (value.isFormerEmployee()) {
                label.setFont(label.getFont().deriveFont(java.awt.Font.ITALIC));
            }
            return label;
        });
        if (currentEmployeeId != null) {
            for (int i = 0; i < cbSurnameLookup.getItemCount(); i++) {
                if (cbSurnameLookup.getItemAt(i).id().equals(currentEmployeeId)) {
                    cbSurnameLookup.setSelectedIndex(i);
                    break;
                }
            }
        } else {
            cbSurnameLookup.setSelectedIndex(-1);
        }
        isRefreshingLookup = false;
    }

    private void onSurnameSelected(ItemEvent e) {
        if (isRefreshingLookup || e.getStateChange() != ItemEvent.SELECTED) {
            return;
        }
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) e.getItem();
        if (selected == null) {
            return;
        }
        if (selected.isFormerEmployee()) {
            loadRejoinerRecord(selected.id());
        } else {
            loadEmployeeIntoForm(selected.id());
        }
    }

    private void loadRejoinerRecord(EmployeeId formerEmployeeId) {
        LocalDate leaveDate = employeeRepository.getLeaveDate(formerEmployeeId);
        JOptionPane.showMessageDialog(this,
                "This person has a prior employment record ending " + leaveDate + ". A new employment record will be "
                        + "created, carrying forward their name, address, and PPS number only. Additions, deductions, "
                        + "and pay details will need to be re-entered.",
                "Rejoining Employee", JOptionPane.INFORMATION_MESSAGE);

        PersonalDetailsDTO prepared = employeeRepository.prepareRejoinerRecord(formerEmployeeId);
        clearForNewEmployee();
        if (prepared == null) {
            return;
        }
        tbSurname.setText(prepared.surname());
        tbFirstName.setText(prepared.firstName());
        taAddress.setText(prepared.address());
        dcDateOfBirth.setValue(prepared.dateOfBirth() == null ? null : toDate(prepared.dateOfBirth()));
        tbEmail.setText(prepared.email());
        tbPpsNumber.setText(prepared.ppsNumber());
        tbEmploymentId.setText(prepared.employmentId());
        // currentEmployeeId stays null / isNewEmployee stays true (per clearForNewEmployee) -
        // the rejoiner is a brand new employment relationship, per S-13.
    }

    private void loadEmployeeIntoForm(EmployeeId id) {
        EmployeeRecordDTO record = employeeRepository.loadEmployeeRecord(id);
        if (record == null) {
            return;
        }
        currentEmployeeId = id;
        isNewEmployee = false;

        PersonalDetailsDTO p = record.personalDetails();
        tbSurname.setText(p.surname());
        tbFirstName.setText(p.firstName());
        taAddress.setText(p.address());
        dcDateOfBirth.setValue(p.dateOfBirth() == null ? null : toDate(p.dateOfBirth()));
        tbEmail.setText(p.email());
        cbDirector.setSelected(p.director());
        selectDepartment(p.departmentId());
        tbPpsNumber.setText(p.ppsNumber());
        tbEmploymentId.setText(p.employmentId());
        tbWorksNumber.setText(p.worksNumber());
        tbHourlyRate.setValue(p.hourlyRate());
        tbFixedPay.setValue(p.fixedPay());
        PayMethod method = p.payMethod() == null ? PayMethod.CASH : p.payMethod();
        rbCash.setSelected(method == PayMethod.CASH);
        rbCheque.setSelected(method == PayMethod.CHEQUE);
        rbCreditTransfer.setSelected(method == PayMethod.CREDIT_TRANSFER);
        bankDetailsPanel.setVisible(method == PayMethod.CREDIT_TRANSFER);
        tbBank.setText(p.bank());
        tbBranch.setText(p.branch());
        tbSortCode.setText(p.sortCode());
        tbAccountNumber.setText(p.accountNumber());
        tbCreditUnionRef.setText(p.creditUnionRef());

        RevenueDetailsDTO r = record.revenueDetails();
        if (r != null) {
            dcStartDate.setValue(r.startDate() == null ? null : toDate(r.startDate()));
            tbStartWeek.setValue(r.startWeek());
            cbPrsiClass.setSelectedItem(r.prsiClass());
        }

        additionsPanel.setRows(toRows(record.additions()));
        deductionsPanel.setRows(toRowsFromDeductions(record.deductions()));

        CumulativesDTO cum = record.cumulatives();
        tbPriorGrossPay.setValue(cum == null ? null : cum.priorGrossPay());
        tbPriorTaxPaid.setValue(cum == null ? null : cum.priorTaxPaid());
        tbPriorPrsiPaid.setValue(cum == null ? null : cum.priorPrsiPaid());
        tbPriorUscPaid.setValue(cum == null ? null : cum.priorUscPaid());
        refreshLeaveDate();

        HrDetailsDTO hr = record.hrDetails();
        tbJobTitle.setText(hr == null ? "" : hr.jobTitle());
        cbContractType.setSelectedItem(hr == null ? "Permanent" : hr.contractType());
        tbEmergencyContactName.setText(hr == null ? "" : hr.emergencyContactName());
        tbEmergencyContactPhone.setText(hr == null ? "" : hr.emergencyContactPhone());

        CsoDetailsDTO cso = record.csoDetails();
        cbOccupationCode.setSelectedItem(cso == null ? "" : cso.occupationCode());
        cbHoursCategory.setSelectedItem(cso == null ? "" : cso.hoursCategory());

        updateEmergencyBanner(id);

        btAdd.setEnabled(true);
        btUpdate.setEnabled(true);
        btDelete.setEnabled(true);
    }

    private void selectDepartment(com.blueseer.pay.PayrollIds.DepartmentId departmentId) {
        for (int i = 0; i < cbDepartment.getItemCount(); i++) {
            DepartmentDTO item = cbDepartment.getItemAt(i);
            if (item != null && item != MANAGE_DEPARTMENTS_SENTINEL && item.id().equals(departmentId)) {
                cbDepartment.setSelectedIndex(i);
                return;
            }
        }
        cbDepartment.setSelectedItem(null);
    }

    private static List<LineItemRow> toRows(List<AdditionLineDTO> lines) {
        List<LineItemRow> rows = new ArrayList<>();
        for (AdditionLineDTO line : lines) {
            LineItemRow row = new LineItemRow(line.id());
            row.description = line.description();
            row.flag = line.taxable();
            row.amount = line.amount();
            row.endDate = line.endDateOrNull();
            rows.add(row);
        }
        return rows;
    }

    private static List<LineItemRow> toRowsFromDeductions(List<DeductionLineDTO> lines) {
        List<LineItemRow> rows = new ArrayList<>();
        for (DeductionLineDTO line : lines) {
            LineItemRow row = new LineItemRow(line.id());
            row.description = line.description();
            row.flag = line.preTax();
            row.amount = line.amount();
            row.endDate = line.endDateOrNull();
            row.ascGroup = line.ascGroupOrNull();
            row.ascOverrideAmount = line.ascOverrideAmountOrNull();
            row.ascOverridePercentage = line.ascOverridePercentageOrNull();
            rows.add(row);
        }
        return rows;
    }

    private void updateEmergencyBanner(EmployeeId id) {
        EmergencyStatusDTO status = revenueDetailsService.getCurrentEmergencyStatus(id);
        lblEmergencyBanner.setVisible(status.onEmergencyBasis());
    }

    private void clearForNewEmployee() {
        currentEmployeeId = null;
        isNewEmployee = true;
        cbSurnameLookup.setSelectedIndex(-1);
        tbSurname.setText("");
        tbFirstName.setText("");
        taAddress.setText("");
        dcDateOfBirth.setValue(null);
        tbEmail.setText("");
        cbDirector.setSelected(false);
        cbDepartment.setSelectedItem(null);
        tbPpsNumber.setText("");
        tbEmploymentId.setText("(auto-populated on save)");
        tbWorksNumber.setText("");
        tbHourlyRate.setValue(null);
        tbFixedPay.setValue(null);
        rbCash.setSelected(true);
        bankDetailsPanel.setVisible(false);
        tbBank.setText("");
        tbBranch.setText("");
        tbSortCode.setText("");
        tbAccountNumber.setText("");
        tbCreditUnionRef.setText("");

        dcStartDate.setValue(null);
        tbStartWeek.setValue(null);
        cbPrsiClass.setSelectedIndex(0);
        cbUscExempt.setSelected(false);
        cbPrsiExempt.setSelected(false);
        lblEmergencyBanner.setVisible(true);

        additionsPanel.setRows(List.of());
        deductionsPanel.setRows(List.of());

        tbPriorGrossPay.setValue(null);
        tbPriorTaxPaid.setValue(null);
        tbPriorPrsiPaid.setValue(null);
        tbPriorUscPaid.setValue(null);
        tbLeaveDate.setText("");

        tbJobTitle.setText("");
        cbContractType.setSelectedIndex(0);
        tbEmergencyContactName.setText("");
        tbEmergencyContactPhone.setText("");

        cbOccupationCode.setSelectedIndex(0);
        cbHoursCategory.setSelectedIndex(0);
    }

    private void validatePpsOnFocusLost() {
        String pps = tbPpsNumber.getText();
        if (!pps.isEmpty() && !PPS_PATTERN.matcher(pps).matches()) {
            tbPpsNumber.setText("");
            JOptionPane.showMessageDialog(this,
                    "Incorrect PPS number format - the field has been cleared.",
                    "Invalid PPS Number", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void recalculateStartWeek() {
        Object value = dcStartDate.getValue();
        if (value instanceof Date date) {
            int weekOfYear = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            tbStartWeek.setValue(weekOfYear);
        }
    }

    private void onUpdateClicked(java.awt.event.ActionEvent e) {
        switch (jTabbedPane1.getSelectedIndex()) {
            case 0 -> {
                savePersonalDetails();
                showSaved();
            }
            case 1 -> {
                saveRevenueDetails();
                showSaved();
            }
            case 2 -> additionsPanel.triggerSave();
            case 3 -> deductionsPanel.triggerSave();
            case 4 -> onUpdateCumulatives();
            case 5 -> onUpdateHrDetails();
            case 6 -> onUpdateCsoDetails();
            default -> {
            }
        }
    }

    private void showSaved() {
        JOptionPane.showMessageDialog(this, "Saved.", "Employee Maintenance", JOptionPane.INFORMATION_MESSAGE);
    }

    private void savePersonalDetails() {
        EmployeeId id = currentEmployeeId != null ? currentEmployeeId : new EmployeeId(System.currentTimeMillis());
        PayMethod method = rbCheque.isSelected() ? PayMethod.CHEQUE
                : rbCreditTransfer.isSelected() ? PayMethod.CREDIT_TRANSFER : PayMethod.CASH;
        DepartmentDTO department = (DepartmentDTO) cbDepartment.getSelectedItem();
        PersonalDetailsDTO data = new PersonalDetailsDTO(id, tbSurname.getText(), tbFirstName.getText(),
                taAddress.getText(), toLocalDate(dcDateOfBirth.getValue()), tbEmail.getText(), "",
                cbDirector.isSelected(), department == null ? null : department.id(), tbPpsNumber.getText(),
                isNewEmployee ? "EMP-" + id.value() : tbEmploymentId.getText(), tbWorksNumber.getText(),
                toBigDecimal(tbHourlyRate.getValue()), toBigDecimal(tbFixedPay.getValue()), method,
                tbBank.getText(), tbBranch.getText(), tbSortCode.getText(), tbAccountNumber.getText(), tbCreditUnionRef.getText());
        SaveResult result = employeeRepository.savePersonalDetails(data);
        if (result.wasCreate()) {
            currentEmployeeId = id;
            isNewEmployee = false;
            tbEmploymentId.setText(data.employmentId());
            refreshSurnameLookup();
        }
    }

    private void saveRevenueDetails() {
        EmployeeId id = currentEmployeeId != null ? currentEmployeeId : new EmployeeId(System.currentTimeMillis());
        RevenueDetailsDTO data = new RevenueDetailsDTO(id, toLocalDate(dcStartDate.getValue()),
                toIntOrZero(tbStartWeek.getValue()), (String) cbPrsiClass.getSelectedItem(), List.of());
        revenueDetailsService.saveRevenueDetails(data);
        updateEmergencyBanner(id);
        promptForRpnRequestIfNeeded(id);
    }

    /**
     * Reproduces the exemplar's own flow: "On closing out of the new
     * employee record, you will now be prompted to send an RPN request" -
     * only offered while no RPN has been applied yet for this employment,
     * since a returning edit of an already-RPN'd employee shouldn't nag
     * every time Revenue Details is re-saved.
     */
    private void promptForRpnRequestIfNeeded(EmployeeId id) {
        EmergencyStatusDTO status = revenueDetailsService.getCurrentEmergencyStatus(id);
        if (!status.onEmergencyBasis()) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Send an RPN request now?", "Request RPN",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        RpnRequestDialog dialog = new RpnRequestDialog(SwingUtilities.getWindowAncestor(this), rpnGateway, id);
        dialog.start();
        dialog.setVisible(true);
        if (dialog.wasRpnApplied()) {
            updateEmergencyBanner(id);
        }
    }

    private void onUpdateCumulatives() {
        if (currentEmployeeId == null) {
            return;
        }
        CumulativesDTO data = new CumulativesDTO(
                toBigDecimal(tbPriorGrossPay.getValue()), toBigDecimal(tbPriorTaxPaid.getValue()),
                toBigDecimal(tbPriorPrsiPaid.getValue()), toBigDecimal(tbPriorUscPaid.getValue()));
        employeeRepository.saveMidYearCumulatives(currentEmployeeId, data);
        showSaved();
    }

    private void onUpdateHrDetails() {
        if (currentEmployeeId == null) {
            return;
        }
        HrDetailsDTO data = new HrDetailsDTO(tbJobTitle.getText(), (String) cbContractType.getSelectedItem(),
                tbEmergencyContactName.getText(), tbEmergencyContactPhone.getText());
        employeeRepository.saveHrDetails(currentEmployeeId, data);
        showSaved();
    }

    private void onUpdateCsoDetails() {
        if (currentEmployeeId == null) {
            return;
        }
        CsoDetailsDTO data = new CsoDetailsDTO((String) cbOccupationCode.getSelectedItem(), (String) cbHoursCategory.getSelectedItem());
        employeeRepository.saveCsoDetails(currentEmployeeId, data);
        showSaved();
    }

    private void refreshLeaveDate() {
        if (currentEmployeeId == null) {
            tbLeaveDate.setText("");
            return;
        }
        LocalDate leaveDate = employeeRepository.getLeaveDate(currentEmployeeId);
        tbLeaveDate.setText(leaveDate == null ? "" : leaveDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    private void refreshDepartmentCombo() {
        cbDepartment.removeAllItems();
        cbDepartment.addItem(null);
        for (DepartmentDTO dept : departmentController.listDepartments(DEMO_COMPANY)) {
            cbDepartment.addItem(dept);
        }
        cbDepartment.addItem(MANAGE_DEPARTMENTS_SENTINEL);
    }

    private void openDepartmentDialog() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        DepartmentMaintDialog dialog = new DepartmentMaintDialog(owner, departmentController, this::refreshDepartmentCombo);
        dialog.setVisible(true);
    }

    private void onDeleteClicked(java.awt.event.ActionEvent e) {
        if (currentEmployeeId == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Delete this employee?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            employeeRepository.deleteEmployee(currentEmployeeId);
            refreshSurnameLookup();
            clearForNewEmployee();
        }
    }

    private static Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Object value) {
        return value instanceof Date date ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static int toIntOrZero(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
