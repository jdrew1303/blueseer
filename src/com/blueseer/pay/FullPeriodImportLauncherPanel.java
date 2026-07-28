package com.blueseer.pay;

import com.blueseer.pay.ImportDtos.ImportProfile;

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-29 menu entry point - same reasoning as {@link ImportHoursLauncherPanel}, different {@link ImportProfile}. */
public class FullPeriodImportLauncherPanel extends JPanel {

    public FullPeriodImportLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Import a full period's pay data from CSV:"));
        JButton btImport = new JButton("Import Full Period");
        btImport.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            PayrollStubStore store = PayrollStubStore.shared();
            IImportController controller = new InMemoryImportController(store, new InMemoryEmployeeRepository(store),
                    new InMemoryPayEntryController(store, PayrollEngineFactory.defaultCalculationService()));
            new ImportHoursWizard(owner, controller, ImportProfile.FULL_PERIOD).setVisible(true);
        });
        center.add(btImport);
        add(center);
    }
}
