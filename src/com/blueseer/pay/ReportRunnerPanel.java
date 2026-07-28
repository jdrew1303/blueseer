package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.ReportDtos.ReportHandle;

import net.sf.jasperreports.swing.JRViewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

/**
 * Shared "filter panel + btRun + sort-alphabetically prompt + Jasper preview
 * + Print/Copy/HTML/Email action bar" shape common to every §3.8 Reports Hub
 * screen (S-40 spec: "every screen in this section shares one interaction
 * shape"). Host screens own their own filter controls (period pickers,
 * sub-type selectors, etc.) and supply a {@link ReportSupplier} that builds
 * the {@link ReportHandle} for the current filter state.
 */
public class ReportRunnerPanel extends JPanel {

    /** Builds a {@link ReportHandle} for the current filter state. */
    public interface ReportSupplier {
        ReportHandle run(boolean sortAlphabetically) throws Exception;
    }

    private final IReportController controller;
    private final ReportSupplier supplier;
    private final BooleanSupplier summaryOnlyGate;

    private JButton btRun;
    private JScrollPane previewScroll;
    private JButton btPrint;
    private JButton btCopy;
    private JButton btHtml;
    private JButton btEmail;
    private ReportHandle currentHandle;

    public ReportRunnerPanel(IReportController controller, ReportSupplier supplier) {
        this(controller, supplier, null);
    }

    /**
     * @param summaryOnlyGate only S-40 supplies this - when non-null and
     *                        returns false at print time, a second prompt
     *                        ("Also print departmental information?") is
     *                        shown first, per S-40's own spec.
     */
    public ReportRunnerPanel(IReportController controller, ReportSupplier supplier, BooleanSupplier summaryOnlyGate) {
        this.controller = controller;
        this.supplier = supplier;
        this.summaryOnlyGate = summaryOnlyGate;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btRun = new JButton("Run");
        btRun.addActionListener(e -> onRun());
        north.add(btRun);
        add(north, BorderLayout.NORTH);

        previewScroll = new JScrollPane();
        previewScroll.setPreferredSize(new Dimension(560, 260));
        add(previewScroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btPrint = new JButton("Print");
        btPrint.setEnabled(false);
        btPrint.addActionListener(e -> onPrint());
        btCopy = new JButton("Copy");
        btCopy.setEnabled(false);
        btCopy.addActionListener(e -> onCopy());
        btHtml = new JButton("HTML");
        btHtml.setEnabled(false);
        btHtml.addActionListener(e -> onHtml());
        btEmail = new JButton("Email");
        btEmail.setEnabled(false);
        btEmail.addActionListener(e -> onEmail());
        south.add(btPrint);
        south.add(btCopy);
        south.add(btHtml);
        south.add(btEmail);
        add(south, BorderLayout.SOUTH);
    }

    /** Discards the current preview - used by S-45 when its sub-type selector changes. */
    public void clearPreview() {
        currentHandle = null;
        previewScroll.setViewportView(null);
        btPrint.setEnabled(false);
        btCopy.setEnabled(false);
        btHtml.setEnabled(false);
        btEmail.setEnabled(false);
    }

    private void onRun() {
        boolean sort = JOptionPane.showConfirmDialog(this, "Sort alphabetically?", "Run Report", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        btRun.setEnabled(false);
        SwingWorker<ReportHandle, Void> worker = new SwingWorker<>() {
            @Override
            protected ReportHandle doInBackground() throws Exception {
                return supplier.run(sort);
            }

            @Override
            protected void done() {
                btRun.setEnabled(true);
                try {
                    currentHandle = get();
                    previewScroll.setViewportView(new JRViewer(currentHandle.print()));
                    btPrint.setEnabled(true);
                    btCopy.setEnabled(true);
                    btHtml.setEnabled(true);
                    btEmail.setEnabled(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ReportRunnerPanel.this, "Report failed: " + ex.getMessage(), "Run Report", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onPrint() {
        boolean includeDepartmental = false;
        if (summaryOnlyGate != null && !summaryOnlyGate.getAsBoolean()) {
            includeDepartmental = JOptionPane.showConfirmDialog(this, "Also print departmental information?", "Print",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }
        controller.printReport(currentHandle, includeDepartmental);
        JOptionPane.showMessageDialog(this, "Report sent to the system print dialog.", "Print", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onCopy() {
        controller.copyReportToClipboard(currentHandle);
        JOptionPane.showMessageDialog(this, "Report copied to the clipboard.", "Copy", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onHtml() {
        File file = controller.exportReportHtml(currentHandle);
        JOptionPane.showMessageDialog(this, "HTML export written to " + file.getAbsolutePath(), "HTML", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onEmail() {
        EmailSendResult result = controller.emailReport(currentHandle);
        JOptionPane.showMessageDialog(this, result.sentCount() + " sent, " + result.failedCount() + " failed.", "Email", JOptionPane.INFORMATION_MESSAGE);
    }
}
