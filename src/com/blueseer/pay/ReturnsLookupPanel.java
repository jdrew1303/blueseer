package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RemittanceDtos.ReturnSearchResultDTO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** S-69 Returns Look-Up. */
public class ReturnsLookupPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IRemittanceController controller;

    private JTextField tbSearchQuery;
    private DefaultTableModel model;

    public ReturnsLookupPanel() {
        this(new InMemoryRemittanceController(PayrollStubStore.shared()));
    }

    public ReturnsLookupPanel(IRemittanceController controller) {
        this.controller = controller;
        initComponents();
        onSearch();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Returns Look-Up"));
        content.setPreferredSize(new Dimension(560, 360));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tbSearchQuery = new JTextField(20);
        tbSearchQuery.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onSearch();
                }
            }
        });
        north.add(tbSearchQuery);
        JButton btSearch = new JButton("Search");
        btSearch.addActionListener(e -> onSearch());
        north.add(btSearch);
        content.add(north, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[] {"Return Type", "Period", "Submission Date", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        add(content);
    }

    private void onSearch() {
        model.setRowCount(0);
        for (ReturnSearchResultDTO row : controller.searchReturns(DEMO_COMPANY, tbSearchQuery.getText())) {
            model.addRow(new Object[] {row.returnType(), row.period(), row.submissionDate(), row.status()});
        }
    }
}
