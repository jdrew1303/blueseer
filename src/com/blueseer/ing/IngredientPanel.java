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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ingredient Data" tab for ItemMaint: EU/Irish FIC regulatory metadata for
 * an item used as a raw material/ingredient elsewhere (legal name, additive
 * category/E-number, allergens, and - for a purchased item that is itself a
 * compound ingredient BlueSeer has no BOM visibility into - the supplier's
 * declared sub-ingredient breakdown), plus QUID/reconstitution settings for
 * an item that is itself a finished good with its own BOM. ItemMaint calls
 * loadData()/saveData() alongside its own item_mstr load/save, the same way
 * it already does for attachments (see ItemMaint.getRecord/addRecord/
 * updateRecord).
 *
 * Explanatory text lives in tooltips behind a small help icon next to each
 * field rather than as permanently-visible paragraphs (see {@link
 * #helpIcon}), so the form stays a compact two-column grid instead of a
 * tall single-column stack of label/field/paragraph/label/field/...
 */
public class IngredientPanel extends JPanel {

    private final javax.swing.JTextField tbLegalName = new javax.swing.JTextField();
    private final javax.swing.JTextField tbCategory = new javax.swing.JTextField();
    private final javax.swing.JTextField tbENumber = new javax.swing.JTextField();
    private final javax.swing.JTextField tbWtPerUom = new javax.swing.JTextField("1");
    private final JCheckBox cbIsAdditive = new JCheckBox("This ingredient is itself a food additive");
    private final JCheckBox cbIsCompound = new JCheckBox("Purchased compound ingredient (supplier recipe, no BOM here)");
    private final javax.swing.JTextArea taNotes = new javax.swing.JTextArea(3, 20);

    private final Map<String, JCheckBox> allergenBoxes = new LinkedHashMap<>();
    private final JPanel allergenPanel = new JPanel(new MigLayout("wrap 3", "[]10[]10[]", "[]"));

    private final DefaultTableModel subModel = new DefaultTableModel(
            new Object[]{"Sub-ingredient name", "E-number", "Allergen"}, 0) {
        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? Boolean.class : String.class;
        }
    };
    private final JTable subTable = new JTable(subModel);
    private final JPanel subPanel = new JPanel(new BorderLayout(0, 5));

    private final JCheckBox cbMoistLoss = new JCheckBox("This item's production process loses moisture");

    // Storage/usage instructions (FIC Article 9(1)(g)/(j)) - mandatory particulars
    // in their own right, not specific to nutrition, but living in this same tab
    // since it's already this item's "regulatory label data" home.
    private final javax.swing.JTextArea taStorageInstr = new javax.swing.JTextArea(2, 20);
    private final javax.swing.JTextArea taUsageInstr = new javax.swing.JTextArea(2, 20);

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

    // BOM-driven QUID + reconstitution table: rows are THIS item's own
    // flattened ingredients (see IngredientLabelEngine.getBomIngredients),
    // never free-typed, so what can be flagged always matches the recipe.
    private final DefaultTableModel bomModel = new DefaultTableModel(
            new Object[]{"Item code", "Description", "Needs QUID %", "Reconstitutes into"}, 0) {
        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 2 || col == 3;
        }
    };
    private final JTable bomTable = new JTable(bomModel);
    private final JComboBox<String> reconCombo = new JComboBox<>();
    private final JPanel bomPanel = new JPanel(new BorderLayout(0, 5));

    public IngredientPanel() {
        // A plain content panel inside a JScrollPane, not this outer JPanel directly -
        // this tab grew substantially with the nutrition declaration + storage/usage
        // sections below, well past what fits in the tab area unscrolled.
        setLayout(new BorderLayout());
        JPanel content = new JPanel(new MigLayout("insets 10", "[]5[grow,fill]"));
        add(new JScrollPane(content), BorderLayout.CENTER);

        content.add(new JLabel("Legal ingredient name"));
        content.add(tbLegalName, "wrap");

        content.add(labelWithHelp("Weight (g) per 1 unit of measure",
                "Leave as 1 if this item is already tracked by weight (kg/g) consistent with the rest of "
                + "the recipe. Set this for a <i>volume</i>-tracked ingredient (mL/L) so it sorts and sums "
                + "correctly against solids - e.g. water tracked in mL: 1; a lighter oil tracked in mL: ~0.92."));
        content.add(tbWtPerUom, "wrap");

        content.add(cbIsAdditive, "wrap");
        cbIsAdditive.setToolTipText(helpHtml(
                "Leave unchecked for a plain ingredient (sugar, flour, water, ...). Check this only when this "
                + "item <i>is</i> the additive itself - e.g. an item called \"Sodium Benzoate\" used in a recipe. "
                + "It will then appear in the printed ingredient list at its own position (sorted by its own "
                + "quantity, like any other ingredient), formatted per EU convention as \"Category (E-number)\", "
                + "e.g. \"Preservative (E211)\"."));
        content.add(new JLabel("Additive category"));
        content.add(tbCategory, "wrap");
        content.add(new JLabel("E-number"));
        content.add(tbENumber, "wrap");
        tbCategory.setEnabled(false);
        tbENumber.setEnabled(false);
        cbIsAdditive.addActionListener(e -> {
            tbCategory.setEnabled(cbIsAdditive.isSelected());
            tbENumber.setEnabled(cbIsAdditive.isSelected());
        });

        for (ingData.ing_allergen_ref ref : ingData.getAllergenRef()) {
            JCheckBox cb = new JCheckBox(shortAllergenLabel(ref.allergen_code(), ref.allergen_desc()));
            cb.setToolTipText(ref.allergen_desc());
            allergenBoxes.put(ref.allergen_code(), cb);
            allergenPanel.add(cb);
        }
        content.add(wrapTitled("Allergens (EU Annex II)", allergenPanel), "span 2, growx, wrap");

        content.add(cbIsCompound, "wrap");
        cbIsCompound.setToolTipText(helpHtml(
                "Check only for a <i>purchased</i> item that is itself a compound ingredient BlueSeer has no BOM "
                + "for (e.g. bought-in chocolate chips). It's declared under its own name, followed by its "
                + "supplier-declared sub-ingredients in brackets - omitted only when under 2% of the finished "
                + "product and none of its sub-ingredients is an allergen. An in-house sub-recipe with its own "
                + "BOM doesn't need this - it's always fully expanded automatically."));

        subTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        JButton btnAddRow = new JButton("Add row");
        btnAddRow.addActionListener(e -> subModel.addRow(new Object[]{"", "", Boolean.FALSE}));
        JButton btnRemoveRow = new JButton("Remove row");
        btnRemoveRow.addActionListener(e -> {
            int row = subTable.getSelectedRow();
            if (row >= 0) {
                subModel.removeRow(row);
            }
        });
        JPanel subButtons = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));
        subButtons.add(btnAddRow);
        subButtons.add(btnRemoveRow);
        subPanel.add(new JScrollPane(subTable), BorderLayout.CENTER);
        subPanel.add(subButtons, BorderLayout.SOUTH);
        subPanel.setPreferredSize(new java.awt.Dimension(10, 140));
        subPanel.setVisible(false);
        content.add(wrapTitled("Supplier-declared sub-ingredients", subPanel), "span 2, grow, wrap");

        cbIsCompound.addActionListener(e -> subPanel.setVisible(cbIsCompound.isSelected()));

        content.add(cbMoistLoss, "wrap");
        cbMoistLoss.setToolTipText(helpHtml(
                "Only relevant when this item is itself printed as a finished-good label (e.g. this cake, not an "
                + "ingredient used inside something else). Leave unchecked for a cold/no-cook product (QUID % is "
                + "calculated against the total raw ingredient weight). Check it for a cooked/baked/dried product "
                + "(QUID % is instead calculated against this item's Net Weight, since the raw mix no longer "
                + "reflects what's actually in the finished product)."));

        bomTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        bomTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        bomTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(reconCombo) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                    int row, int col) {
                String selfCode = String.valueOf(bomModel.getValueAt(row, 0));
                reconCombo.removeAllItems();
                reconCombo.addItem("");
                for (int i = 0; i < bomModel.getRowCount(); i++) {
                    String code = String.valueOf(bomModel.getValueAt(i, 0));
                    if (!code.equals(selfCode)) {
                        reconCombo.addItem(code);
                    }
                }
                reconCombo.setSelectedItem(value == null ? "" : value);
                return reconCombo;
            }
        });
        bomPanel.add(new JScrollPane(bomTable), BorderLayout.CENTER);
        bomPanel.setPreferredSize(new java.awt.Dimension(10, 160));
        bomPanel.setVisible(false);
        JLabel bomTitle = new JLabel("This item's ingredients (QUID % / reconstitution)");
        JPanel bomTitleRow = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));
        bomTitleRow.add(bomTitle);
        bomTitleRow.add(helpIcon(
                "Lists this item's own flattened BOM ingredients - only shown when this item is a finished/"
                + "manufactured good with a recipe of its own. <b>Needs QUID %</b>: check for an ingredient named "
                + "in this product's name, emphasized on the pack, or characterizing it (e.g. the ham in a ham "
                + "sandwich) - its percentage is then printed on the label. <b>Reconstitutes into</b>: for a "
                + "diluent (e.g. water) used solely to rehydrate a concentrated/dehydrated ingredient in "
                + "<i>this</i> recipe (e.g. milk powder) - its weight is folded into the chosen ingredient instead "
                + "of appearing as its own list entry. Both are specific to this one product; the same raw "
                + "material may be flagged differently (or not at all) on a different product's label."));
        JPanel bomWrapper = new JPanel(new BorderLayout());
        bomWrapper.add(bomTitleRow, BorderLayout.NORTH);
        bomWrapper.add(bomPanel, BorderLayout.CENTER);
        content.add(bomWrapper, "span 2, grow, wrap");

        // ------------------------------------------------------------
        // Nutrition Declaration (EU FIC Article 30-35, Annexes XIII-XV) -
        // manual entry only (client decision: no external/public dataset
        // seeding, so entering a value here forces getting the real
        // datasheet from the supplier first). SALT/ENERGY are excluded -
        // both are always calculated by NutritionLabelEngine, never entered.
        // ------------------------------------------------------------
        for (nutData.nut_mstr nm : nutData.getNutMstrList()) {
            if (com.blueseer.ing.NutritionLabelEngine.DERIVED_CODES.contains(nm.nutrient_code())) {
                continue;
            }
            nutRowCodes.add(nm.nutrient_code());
            nutModel.addRow(new Object[]{nm.display_name(), "", DATA_SOURCE_OPTIONS[0], ""});
        }
        nutTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        nutTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(DATA_SOURCE_OPTIONS)));
        JScrollPane nutScroll = new JScrollPane(nutTable);
        nutScroll.setPreferredSize(new java.awt.Dimension(10, 220));
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

        // Placement/exemption/portion configuration (item_nut_cfg)
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

        // ------------------------------------------------------------
        // Storage/usage instructions (FIC Article 9(1)(g)/(j)) - mandatory
        // particulars in their own right, applied to every label via the
        // $STORAGE/$USAGE tokens, independent of the nutrition panel above.
        // ------------------------------------------------------------
        JPanel storageUsagePanel = new JPanel(new MigLayout("insets 0", "[]5[grow,fill]", "[]"));
        storageUsagePanel.add(new JLabel("Storage conditions"));
        storageUsagePanel.add(new JScrollPane(taStorageInstr), "grow, wrap");
        storageUsagePanel.add(new JLabel("Usage instructions"));
        storageUsagePanel.add(new JScrollPane(taUsageInstr), "grow, wrap");
        content.add(wrapTitled("Storage & Usage Instructions", storageUsagePanel), "span 2, grow, wrap");

        content.add(new JLabel("Notes"));
        content.add(new JScrollPane(taNotes), "span 2, grow, wrap");
    }

    private void updateExemptReasonEnabled() {
        tbExemptReason.setEnabled(nutData.item_nut_cfg.NONE.equals(ddNutPlacement.getSelectedItem()));
    }

    /** A field label followed by a small help icon carrying the long-form explanation as a tooltip. */
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

    // EU Annex II short names for checkbox labels - allergen_desc carries the full legal
    // wording (shown as the tooltip instead) since a couple of entries (tree nuts' bracketed
    // examples, the sulphites concentration threshold) run 90+ characters and blow out the
    // MigLayout grid width if used as the visible label.
    private static final Map<String, String> ALLERGEN_SHORT_LABEL = Map.ofEntries(
            Map.entry("GLUTEN", "Cereals containing gluten"),
            Map.entry("CRUSTACEANS", "Crustaceans"),
            Map.entry("EGGS", "Eggs"),
            Map.entry("FISH", "Fish"),
            Map.entry("PEANUTS", "Peanuts"),
            Map.entry("SOYBEANS", "Soybeans"),
            Map.entry("MILK", "Milk"),
            Map.entry("NUTS", "Tree nuts"),
            Map.entry("CELERY", "Celery"),
            Map.entry("MUSTARD", "Mustard"),
            Map.entry("SESAME", "Sesame seeds"),
            Map.entry("SULPHITES", "Sulphur dioxide/sulphites"),
            Map.entry("LUPIN", "Lupin"),
            Map.entry("MOLLUSCS", "Molluscs"));

    private static String shortAllergenLabel(String code, String desc) {
        String known = ALLERGEN_SHORT_LABEL.get(code);
        if (known != null) {
            return known;
        }
        int paren = desc.indexOf(" and products");
        return paren > 0 ? desc.substring(0, paren) : desc;
    }

    public void clear() {
        tbLegalName.setText("");
        tbCategory.setText("");
        tbENumber.setText("");
        tbWtPerUom.setText("1");
        cbIsAdditive.setSelected(false);
        tbCategory.setEnabled(false);
        tbENumber.setEnabled(false);
        cbIsCompound.setSelected(false);
        subPanel.setVisible(false);
        cbMoistLoss.setSelected(false);
        taNotes.setText("");
        for (JCheckBox cb : allergenBoxes.values()) {
            cb.setSelected(false);
        }
        subModel.setRowCount(0);
        bomModel.setRowCount(0);
        bomPanel.setVisible(false);
        taStorageInstr.setText("");
        taUsageInstr.setText("");
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
        ingData.ing_mstr rec = ingData.getIngMstr(item);
        if (rec.m() != null && rec.m().length > 0 && rec.m()[0].equals(com.blueseer.utl.BlueSeerUtils.SuccessBit)) {
            tbLegalName.setText(rec.ing_legalname());
            tbCategory.setText(rec.ing_category());
            tbENumber.setText(rec.ing_enumber());
            tbWtPerUom.setText(String.valueOf(rec.ing_wt_per_uom_g() <= 0 ? 1.0 : rec.ing_wt_per_uom_g()));
            boolean isAdditive = !rec.ing_category().isBlank() || !rec.ing_enumber().isBlank();
            cbIsAdditive.setSelected(isAdditive);
            tbCategory.setEnabled(isAdditive);
            tbENumber.setEnabled(isAdditive);
            cbIsCompound.setSelected("1".equals(rec.ing_iscompound()));
            subPanel.setVisible(cbIsCompound.isSelected());
            taNotes.setText(rec.ing_notes());
            taStorageInstr.setText(rec.ing_storage_instr());
            taUsageInstr.setText(rec.ing_usage_instr());
        }
        for (String code : ingData.getAllergenCodes(item)) {
            JCheckBox cb = allergenBoxes.get(code);
            if (cb != null) {
                cb.setSelected(true);
            }
        }
        for (ingData.ing_subingredient sub : ingData.getSubIngredients(item)) {
            subModel.addRow(new Object[]{sub.sub_name(), sub.sub_enumber(), "1".equals(sub.is_allergen())});
        }
        cbMoistLoss.setSelected(ingData.getMoistLoss(item));

        List<IngredientLabelEngine.BomIngredient> bomList = new IngredientLabelEngine().getBomIngredients(item);
        bomPanel.setVisible(!bomList.isEmpty());
        if (!bomList.isEmpty()) {
            java.util.Set<String> quidSet = new java.util.HashSet<>(ingData.getQuidItemCodes(item));
            Map<String, String> reconMap = ingData.getReconMap(item);
            for (IngredientLabelEngine.BomIngredient bi : bomList) {
                bomModel.addRow(new Object[]{bi.item(), bi.desc(), quidSet.contains(bi.item()),
                        reconMap.getOrDefault(bi.item(), "")});
            }
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
        String category = cbIsAdditive.isSelected() ? tbCategory.getText() : "";
        String enumber = cbIsAdditive.isSelected() ? tbENumber.getText() : "";
        double wtPerUom;
        try {
            wtPerUom = Double.parseDouble(tbWtPerUom.getText().trim());
        } catch (NumberFormatException nfe) {
            wtPerUom = 1.0;
        }
        ingData.ing_mstr rec = new ingData.ing_mstr(null, item, tbLegalName.getText(), category,
                enumber, cbIsCompound.isSelected() ? "1" : "0", "1", taNotes.getText(), wtPerUom <= 0 ? 1.0 : wtPerUom,
                taStorageInstr.getText(), taUsageInstr.getText());
        ingData.addUpdateIngMstr(rec);

        ArrayList<String> codes = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : allergenBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                codes.add(entry.getKey());
            }
        }
        ingData.setAllergenCodes(item, codes);

        ArrayList<ingData.ing_subingredient> subs = new ArrayList<>();
        for (int i = 0; i < subModel.getRowCount(); i++) {
            String name = String.valueOf(subModel.getValueAt(i, 0));
            if (name.isBlank()) {
                continue;
            }
            String subEnumber = String.valueOf(subModel.getValueAt(i, 1));
            boolean isAllergen = Boolean.TRUE.equals(subModel.getValueAt(i, 2));
            subs.add(new ingData.ing_subingredient(null, item, i, name, subEnumber.equals("null") ? "" : subEnumber,
                    isAllergen ? "1" : "0"));
        }
        ingData.setSubIngredients(item, subs);

        ingData.setMoistLoss(item, cbMoistLoss.isSelected());

        if (bomTable.isEditing()) {
            bomTable.getCellEditor().stopCellEditing();
        }
        ArrayList<String> quidItems = new ArrayList<>();
        Map<String, String> reconMap = new LinkedHashMap<>();
        for (int i = 0; i < bomModel.getRowCount(); i++) {
            String code = String.valueOf(bomModel.getValueAt(i, 0));
            boolean needsQuid = Boolean.TRUE.equals(bomModel.getValueAt(i, 2));
            String target = String.valueOf(bomModel.getValueAt(i, 3));
            if (needsQuid) {
                quidItems.add(code);
            }
            if (!target.isBlank() && !target.equals("null")) {
                reconMap.put(code, target);
            }
        }
        ingData.setQuidItemCodes(item, quidItems);
        ingData.setReconMap(item, reconMap);

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
