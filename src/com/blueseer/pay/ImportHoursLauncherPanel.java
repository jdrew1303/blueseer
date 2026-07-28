package com.blueseer.pay;

import com.blueseer.pay.ImportDtos.ImportProfile;

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * S-28 menu entry point: the wizard itself is a modal {@code JDialog}, so
 * (same reason as {@link CompanySetupLauncherPanel}) it is launched from
 * here rather than being the menu target directly.
 */
public class ImportHoursLauncherPanel extends JPanel {

    public ImportHoursLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Import hours from a text/CSV file:"));
        JButton btImport = new JButton("Import Hours");
        btImport.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            PayrollStubStore store = PayrollStubStore.shared();
            IImportController controller = new InMemoryImportController(store, new InMemoryEmployeeRepository(store),
                    new InMemoryPayEntryController(store, PayrollEngineFactory.defaultCalculationService()));
            new ImportHoursWizard(owner, controller, ImportProfile.HOURS_ONLY).setVisible(true);
        });
        center.add(btImport);
        add(center);
    }
}
