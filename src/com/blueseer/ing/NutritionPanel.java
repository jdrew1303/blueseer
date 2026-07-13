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
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Nutrition Data" tab for ItemMaint: the EU/Irish FIC nutrition declaration
 * (per-ingredient nutrient values, {@code ing_nutrient}) plus the finished-
 * item's panel placement/exemption/portion configuration ({@code
 * item_nut_cfg}) - see {@link NutritionLabelEngine} for how these are rolled
 * up into a calculated panel. Split out from {@link IngredientPanel} into its
 * own sibling tab (rather than one long combined tab) once the grid plus
 * settings section made the combined tab require three levels of nested
 * scrolling to reach the bottom fields.
 *
 * Deliberately does NOT touch {@code ing_mstr} (legal name/category/E-number/
 * storage-usage text, still owned by {@link IngredientPanel}) - both
 * {@code ing_nutrient} and {@code item_nut_cfg} are separate tables keyed by
 * item, so this panel's save can run independently without racing
 * {@link IngredientPanel}'s save over shared columns.
 */
public class NutritionPanel extends JPanel {

    // Nutrient Data entry grid - one row per nut_mstr entry excluding the two
    // always-derived codes (SALT/ENERGY, see NutritionLabelEngine.DERIVED_CODES) -
    // manual entry only (client decision: no external/public dataset seeding).
    private final DefaultTableModel nutModel = new DefaultTableModel(
            new Object[]{"Nutrient", "Value per 100g", "Source", "Notes"}, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return col != 0;
        }
    };
    private final JTable nutTable = new JTable(nutModel);
    // Row index -> nutrient_code, since the visible column 0 shows the
    // human-readable display_name (e.g. "of which saturates") instead.
    private final List<String> nutRowCodes = new ArrayList<>();
    private static final String[] DATA_SOURCE_OPTIONS = {"SUPPLIER_SPEC", "LAB_ANALYSIS", "CALCULATED", "DATABASE_REF"};

    // Nutrition label placement/exemption/portion config (item_nut_cfg) - see
    // nutData.item_nut_cfg for the field meanings.
    private final JComboBox<String> ddNutPlacement = new JComboBox<>(
            new String[]{nutData.item_nut_cfg.MAIN_LABEL, nutData.item_nut_cfg.SEPARATE_LABEL, nutData.item_nut_cfg.NONE});
    private final javax.swing.JTextField tbExemptReason = new javax.swing.JTextField();
    private final JCheckBox cbShowRiPct = new JCheckBox("Show % Reference Intake");
    private final javax.swing.JTextField tbPortionSizeG = new javax.swing.JTextField();
    private final javax.swing.JTextField tbPortionDesc = new javax.swing.JTextField();
    private final javax.swing.JTextField tbPortionsPerPack = new javax.swing.JTextField();

    public NutritionPanel() {
        setLayout(new BorderLayout());
        JPanel content = new JPanel(new MigLayout("insets 10", "[]5[grow,fill]"));
        add(new JScrollPane(content), BorderLayout.CENTER);

        // ------------------------------------------------------------
        // Nutrition Declaration (EU FIC Article 30-35, Annexes XIII-XV) -
        // manual entry only (client decision: no external/public dataset
        // seeding, so entering a value here forces getting the real
        // datasheet from the supplier first). SALT/ENERGY are excluded -
        // both are always calculated by NutritionLabelEngine, never entered.
        // ------------------------------------------------------------
        for (nutData.nut_mstr nm : nutData.getNutMstrList()) {
            if (NutritionLabelEngine.DERIVED_CODES.contains(nm.nutrient_code())) {
                continue;
            }
            nutRowCodes.add(nm.nutrient_code());
            nutModel.addRow(new Object[]{nm.display_name(), "", DATA_SOURCE_OPTIONS[0], ""});
        }
        nutTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        nutTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(DATA_SOURCE_OPTIONS)));
        JScrollPane nutScroll = new JScrollPane(nutTable);
        nutScroll.setPreferredSize(new java.awt.Dimension(10, 420));
        JPanel nutTitleRow = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));
        nutTitleRow.add(new JLabel("Nutrient values (per 100g, as sold)"));
        nutTitleRow.add(helpIcon(
                "Enter this ingredient's nutrient values per 100g exactly as declared on the supplier's own "
                + "datasheet - source SUPPLIER_SPEC, LAB_ANALYSIS, CALCULATED, or DATABASE_REF is a provenance "
                + "note only, not used in the calculation. Enter <b>Sodium</b>, not Salt - Salt is always derived "
                + "centrally (Sodium &times; 2.5) so it can't be entered directly here."));
        JPanel nutWrapper = new JPanel(new BorderLayout(0, 5));
        nutWrapper.add(nutTitleRow, BorderLayout.NORTH);
        nutWrapper.add(nutScroll, BorderLayout.CENTER);
        content.add(wrapTitled("Nutrition Declaration", nutWrapper), "span 2, grow, wrap");

        // Placement/exemption/portion configuration (item_nut_cfg) - only meaningful
        // for a FINISHED item (this is where the calculated panel actually prints),
        // but harmless to show/save on a raw ingredient too.
        JPanel nutCfgPanel = new JPanel(new MigLayout("insets 0", "[]5[grow,fill]", "[]"));
        nutCfgPanel.add(labelWithHelp("Nutrition label placement",
                "<b>Main label</b>: adds the calculated nutrition panel to this item's existing ingredient-list "
                + "label. <b>Separate label</b>: prints the panel on its own, via a second label template selected "
                + "from the Print Label screen's label dropdown - no extra setup needed here. <b>Not shown</b>: "
                + "the panel is never printed for this item - requires an exemption reason below (e.g. Annex V "
                + "point 19 - direct supply in small quantities to the final consumer or local retail) so there's "
                + "an audit trail for why, rather than an unexplained gap."));
        nutCfgPanel.add(ddNutPlacement, "wrap");
        ddNutPlacement.addActionListener(e -> updateExemptReasonEnabled());
        nutCfgPanel.add(new JLabel("Exemption reason (required if \"Not shown\")"));
        nutCfgPanel.add(tbExemptReason, "wrap");
        nutCfgPanel.add(cbShowRiPct, "span 2, wrap");
        cbShowRiPct.setToolTipText(helpHtml(
                "Voluntary (Article 32(4)) - shows each nutrient's percentage of the Reference Intake of an "
                + "average adult (8 400 kJ/2 000 kcal), alongside the mandatory disclaimer statement."));
        nutCfgPanel.add(labelWithHelp("Portion size (g)",
                "Optional (Article 33). Leave blank to show only the mandatory per-100g figures. Set this and "
                + "the two fields below to add a per-portion column - e.g. for a multi-pack, one portion's "
                + "weight in grams."));
        nutCfgPanel.add(tbPortionSizeG, "wrap");
        nutCfgPanel.add(new JLabel("Portion description"));
        nutCfgPanel.add(tbPortionDesc, "wrap");
        nutCfgPanel.add(labelWithHelp("Portions per pack",
                "Article 33 requires stating the number of portions/units in the pack whenever a per-portion "
                + "column is shown - e.g. 6 for a box of 6 muffins."));
        nutCfgPanel.add(tbPortionsPerPack, "wrap");
        content.add(wrapTitled("Nutrition Label Settings", nutCfgPanel), "span 2, growx, wrap");
    }

    private void updateExemptReasonEnabled() {
        tbExemptReason.setEnabled(nutData.item_nut_cfg.NONE.equals(ddNutPlacement.getSelectedItem()));
    }

    private static JPanel labelWithHelp(String label, String helpHtmlBody) {
        JPanel row = new JPanel(new MigLayout("insets 0", "[]3[]", "[]"));
        row.add(new JLabel(label));
        row.add(helpIcon(helpHtmlBody));
        return row;
    }

    private static JLabel helpIcon(String helpHtmlBody) {
        JLabel icon = new JLabel(FontIcon.of(MaterialDesignH.HELP_CIRCLE_OUTLINE, 15, java.awt.Color.GRAY));
        icon.setToolTipText(helpHtml(helpHtmlBody));
        icon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return icon;
    }

    private static String helpHtml(String body) {
        return "<html><div style='width:360px'>" + body + "</div></html>";
    }

    private static JPanel wrapTitled(String title, JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(javax.swing.BorderFactory.createTitledBorder(title));
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    public void clear() {
        for (int row = 0; row < nutModel.getRowCount(); row++) {
            nutModel.setValueAt("", row, 1);
            nutModel.setValueAt(DATA_SOURCE_OPTIONS[0], row, 2);
            nutModel.setValueAt("", row, 3);
        }
        ddNutPlacement.setSelectedItem(nutData.item_nut_cfg.MAIN_LABEL);
        tbExemptReason.setText("");
        cbShowRiPct.setSelected(true);
        tbPortionSizeG.setText("");
        tbPortionDesc.setText("");
        tbPortionsPerPack.setText("");
        updateExemptReasonEnabled();
    }

    public void loadData(String item) {
        clear();
        if (item == null || item.isBlank()) {
            return;
        }
        Map<String, nutData.ing_nutrient> ingNutrients = nutData.getIngNutrients(item);
        for (int row = 0; row < nutRowCodes.size(); row++) {
            nutData.ing_nutrient v = ingNutrients.get(nutRowCodes.get(row));
            if (v != null) {
                nutModel.setValueAt(String.valueOf(v.value_per_100g()), row, 1);
                nutModel.setValueAt(v.data_source(), row, 2);
                nutModel.setValueAt(v.notes(), row, 3);
            }
        }

        nutData.item_nut_cfg cfg = nutData.getItemNutCfg(item);
        ddNutPlacement.setSelectedItem(cfg.nut_placement());
        tbExemptReason.setText(cfg.nut_exempt_reason());
        cbShowRiPct.setSelected(cfg.nut_show_ri_pct());
        tbPortionSizeG.setText(cfg.nut_portion_size_g() == null ? "" : String.valueOf(cfg.nut_portion_size_g()));
        tbPortionDesc.setText(cfg.nut_portion_desc());
        tbPortionsPerPack.setText(cfg.nut_portions_per_pack() == null ? "" : String.valueOf(cfg.nut_portions_per_pack()));
        updateExemptReasonEnabled();
    }

    public void saveData(String item) {
        if (item == null || item.isBlank()) {
            return;
        }
        if (nutTable.isEditing()) {
            nutTable.getCellEditor().stopCellEditing();
        }
        ArrayList<nutData.ing_nutrient> nutrients = new ArrayList<>();
        for (int row = 0; row < nutRowCodes.size(); row++) {
            String valueText = String.valueOf(nutModel.getValueAt(row, 1));
            if (valueText.isBlank() || valueText.equals("null")) {
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(valueText.trim());
            } catch (NumberFormatException nfe) {
                continue;
            }
            String source = String.valueOf(nutModel.getValueAt(row, 2));
            String notes = String.valueOf(nutModel.getValueAt(row, 3));
            nutrients.add(new nutData.ing_nutrient(item, nutRowCodes.get(row), value,
                    source.equals("null") ? DATA_SOURCE_OPTIONS[0] : source, notes.equals("null") ? "" : notes));
        }
        nutData.setIngNutrients(item, nutrients);

        Double portionSizeG = null;
        if (!tbPortionSizeG.getText().isBlank()) {
            try {
                portionSizeG = Double.parseDouble(tbPortionSizeG.getText().trim());
            } catch (NumberFormatException ignored) {
                // leave null - blank/unparsable means "no portion column"
            }
        }
        Integer portionsPerPack = null;
        if (!tbPortionsPerPack.getText().isBlank()) {
            try {
                portionsPerPack = Integer.parseInt(tbPortionsPerPack.getText().trim());
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }
        nutData.item_nut_cfg cfg = new nutData.item_nut_cfg(item, String.valueOf(ddNutPlacement.getSelectedItem()),
                tbExemptReason.getText(), cbShowRiPct.isSelected(), portionSizeG, tbPortionDesc.getText(),
                portionsPerPack);
        nutData.addUpdateItemNutCfg(cfg);
    }
}
