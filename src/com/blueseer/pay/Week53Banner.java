package com.blueseer.pay;

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * S-30 Week 53 Handling, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Not a standalone screen - embedded conditionally at the top of S-18, S-19,
 * and S-22, shown/hidden by each host via {@link #setWeek53(boolean)}. The
 * copy deliberately distinguishes the in-period effect (Week 1 basis,
 * automatic) from the year-end effect (a Revenue-side review the employee's
 * own accountant handles), per C-21's finding that conflating the two would
 * misrepresent what this payroll run actually does.
 */
final class Week53Banner extends JPanel {

    Week53Banner() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel lblNotice = new JLabel(
                "This payment will be taxed on a Week 1 basis; no additional credits are applied this period.");
        JButton btLearnMore = new JButton("Learn More");
        btLearnMore.addActionListener(e -> showExplanation());
        add(lblNotice);
        add(btLearnMore);
        setVisible(false);
    }

    void setWeek53(boolean isWeek53Period) {
        setVisible(isWeek53Period);
    }

    private void showExplanation() {
        JOptionPane.showMessageDialog(this,
                "This period: this payment is taxed on a Week 1 (non-cumulative) basis using the latest RPN - "
                        + "no additional credits are applied and there is nothing extra for you to do.\n\n"
                        + "At year end: Revenue separately reviews the employee's credits and rate bands for the "
                        + "full year (an Income Tax return matter their own accountant handles when filing) - "
                        + "this payroll run does not perform that review.",
                "About Week 53", JOptionPane.INFORMATION_MESSAGE);
    }
}
