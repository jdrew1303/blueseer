package com.blueseer.pay;

import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.swing.JRViewer;

import java.awt.BorderLayout;
import java.awt.Window;
import javax.swing.JDialog;

/**
 * Read-only Jasper preview opened by S-36's btPreview - reuses the actual
 * payslip stationery rendering, not the S-23 workings pipeline. Takes a
 * {@link Window} rather than a {@link java.awt.Frame} owner since S-49 hosts
 * {@code PrintPayslipsPanel} inside a plain {@code JDialog} (not
 * {@code MainFrame}) for its "pre-scoped final payslip" flow - a
 * {@code Frame}-only owner would throw a {@code ClassCastException} there.
 */
public class PayslipPreviewDialog extends JDialog {

    public PayslipPreviewDialog(Window owner, JasperPrint print) {
        super(owner, "Payslip Preview", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        add(new JRViewer(print), BorderLayout.CENTER);
        setSize(700, 600);
        setLocation(40, 40);
    }
}
