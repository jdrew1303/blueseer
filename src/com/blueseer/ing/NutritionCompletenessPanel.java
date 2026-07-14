/*
The MIT License (MIT)

Copyright (c) Terry Evans Vaughn

All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.blueseer.ing;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Catalog-wide nutrition-declaration completeness report (see {@link
 * NutritionCompletenessReport}) - a standalone screen, not a tab within
 * ItemMaint, since it reports across every finished item rather than one at
 * a time. Written as a plain {@code JPanel} with a public no-arg
 * constructor so it can be registered as a {@code menu_mstr.menu_panel}
 * through BlueSeer's existing Admin &gt; Menu Maintenance screen
 * ({@code com.blueseer.adm.MenuMaint}) exactly like any other menu-driven
 * panel - no direct database changes to the menu/permission tables needed
 * from here.
 *
 * The report is manually triggered ("Run Report"), not run on screen open -
 * {@link NutritionLabelEngine#generate} does a DB round-trip per ingredient
 * per finished item, so this can be a real query load on a large catalog.
 */
public class NutritionCompletenessPanel extends JPanel {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Item", "Description", "Placement", "Status", "Missing Ingredients / Notes"}, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JButton btRun = new JButton("Run Report");
    private final JButton btExport = new JButton("Export CSV");

    private static final String STATUS_COMPLETE = "Complete";
    private static final String STATUS_INCOMPLETE = "Incomplete";
    private static final String STATUS_EXEMPT = "Exempt";

    public NutritionCompletenessPanel() {
        setLayout(new BorderLayout(0, 8));

        JPanel toolbar = new JPanel(new MigLayout("insets 8", "[]5[]push[]", "[]"));
        toolbar.add(btRun);
        toolbar.add(btExport);
        toolbar.add(summaryLabel, "align right");
        add(toolbar, BorderLayout.NORTH);

        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(400);
        table.setDefaultRenderer(Object.class, new StatusRowRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        btExport.setEnabled(false);
        btRun.addActionListener(e -> runReport());
        btExport.addActionListener(e -> exportCsv());
    }

    /** Off the EDT - generate() does DB I/O per ingredient per item, not something to run on the UI thread. */
    private void runReport() {
        btRun.setEnabled(false);
        btExport.setEnabled(false);
        summaryLabel.setText("Running...");
        model.setRowCount(0);
        new SwingWorker<List<NutritionCompletenessReport.Row>, Void>() {
            @Override
            protected List<NutritionCompletenessReport.Row> doInBackground() {
                return NutritionCompletenessReport.checkAll();
            }

            @Override
            protected void done() {
                try {
                    List<NutritionCompletenessReport.Row> rows = get();
                    populate(rows);
                } catch (Exception ex) {
                    bsmf.MainFrame.bslog(ex);
                    JOptionPane.showMessageDialog(NutritionCompletenessPanel.this,
                            "Failed to run report: " + ex.getMessage());
                } finally {
                    btRun.setEnabled(true);
                }
            }
        }.execute();
    }

    private void populate(List<NutritionCompletenessReport.Row> rows) {
        int complete = 0;
        int incomplete = 0;
        int exempt = 0;
        for (NutritionCompletenessReport.Row row : rows) {
            String status = row.exempt() ? STATUS_EXEMPT : row.dataComplete() ? STATUS_COMPLETE : STATUS_INCOMPLETE;
            if (row.exempt()) {
                exempt++;
            } else if (row.dataComplete()) {
                complete++;
            } else {
                incomplete++;
            }
            String detail = row.exempt()
                    ? row.exemptReason()
                    : String.join("; ", concat(row.incompleteIngredients(), row.internalNotes()));
            model.addRow(new Object[]{row.item(), row.desc(), row.placement(), status, detail});
        }
        summaryLabel.setText(rows.size() + " items - " + complete + " complete, " + incomplete + " incomplete, "
                + exempt + " exempt");
        btExport.setEnabled(!rows.isEmpty());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("nutrition_completeness_report.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                if (col > 0) {
                    fw.write(",");
                }
                fw.write(csvEscape(model.getColumnName(col)));
            }
            fw.write("\n");
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    if (col > 0) {
                        fw.write(",");
                    }
                    fw.write(csvEscape(String.valueOf(model.getValueAt(row, col))));
                }
                fw.write("\n");
            }
        } catch (IOException ex) {
            bsmf.MainFrame.bslog(ex);
            JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage());
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Tints a row red when Incomplete, amber when Exempt, so the reviewer's eye goes straight to what needs attention. */
    private class StatusRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                String status = String.valueOf(model.getValueAt(row, 3));
                if (STATUS_INCOMPLETE.equals(status)) {
                    c.setBackground(new Color(255, 220, 220));
                } else if (STATUS_EXEMPT.equals(status)) {
                    c.setBackground(new Color(255, 245, 210));
                } else {
                    c.setBackground(Color.WHITE);
                }
            }
            return c;
        }
    }
}
