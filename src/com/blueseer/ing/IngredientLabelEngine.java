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

import static bsmf.MainFrame.bslog;
import static bsmf.MainFrame.db;
import static bsmf.MainFrame.ds;
import static bsmf.MainFrame.pass;
import static bsmf.MainFrame.url;
import static bsmf.MainFrame.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recursively expands a finished good's BOM into a deduplicated, descending-
 * weight ingredient list formatted per EU FIC (Regulation (EU) 1169/2011) and
 * the common Irish/EU mandatory-warning rules, then attaches batch/best-
 * before information.
 *
 * IMPORTANT: the {@code warn_rule} table and the 2%/allergen compound-
 * ingredient logic below are a starting-point implementation, not a legal
 * sign-off - a qualified food-labelling/regulatory professional should
 * review this against the client's actual products before it goes live on
 * real packaging.
 *
 * Assumes every BOM line below the finished good is expressed in a single,
 * consistent weight-basis unit of measure (e.g. all grams, or all kg) - this
 * is standard practice for recipe-level BOMs, but is not itself validated
 * here; mixing weight and count-based UOMs (e.g. "EA") in the same tree
 * would produce a meaningless total.
 */
public class IngredientLabelEngine {

    /** One EU-FIC-mandated-emphasis-aware run of ingredient list text. */
    public record Segment(String text, boolean isAllergen) {
    }

    public record IngredientLabelResult(List<Segment> segments, List<String> warnings,
            String lotNumber, String bestBeforeDate) {

        /** Ingredient list only, HTML markup="html" ready (for JasperReports). */
        public String toHtmlIngredientList() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                Segment s = segments.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(s.isAllergen() ? "<b>" + escapeHtml(s.text()) + "</b>" : escapeHtml(s.text()));
            }
            return sb.toString();
        }

        /** Ingredient list only, plain text with allergens upper-cased (for ZPL). */
        public String toPlainIngredientList() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                Segment s = segments.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(s.isAllergen() ? s.text().toUpperCase(Locale.ROOT) : s.text());
            }
            return sb.toString();
        }

        /** Mandatory warnings only, one sentence each, plain text. */
        public String toPlainWarnings() {
            StringBuilder sb = new StringBuilder();
            for (String w : warnings) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(w).append(".");
            }
            return sb.toString();
        }

        public String toHtml() {
            StringBuilder sb = new StringBuilder(toHtmlIngredientList());
            if (!warnings.isEmpty()) {
                sb.append(".");
                for (String w : warnings) {
                    sb.append(" ").append(escapeHtml(w)).append(".");
                }
            }
            return sb.toString();
        }

        public String toPlainText() {
            StringBuilder sb = new StringBuilder(toPlainIngredientList());
            if (!warnings.isEmpty()) {
                sb.append(". ").append(toPlainWarnings());
            }
            return sb.toString();
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private record BomLine(String child, String type, double qtyPer) {
    }

    private static final double COMPOUND_INGREDIENT_THRESHOLD_PCT = 2.0;

    public IngredientLabelResult generate(String finishedItem, String lotNumber, String bestBeforeDate) {
        Map<String, Double> flatQty = new LinkedHashMap<>();
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            flattenRecursive(con, finishedItem, 1.0, flatQty);
        } catch (SQLException s) {
            bslog(s);
        }

        double totalQty = flatQty.values().stream().mapToDouble(Double::doubleValue).sum();

        // Splice purchased-compound ingredients (ing_iscompound=1) per the
        // 2% rule before dedupe/sort, since splicing can introduce new
        // ingredient names that need to merge with anything already present.
        Map<String, Double> expanded = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : flatQty.entrySet()) {
            String item = entry.getKey();
            double qty = entry.getValue();
            ingData.ing_mstr rec = ingData.getIngMstr(item);
            boolean isCompound = rec.m() != null && rec.m().length > 0
                    && rec.m()[0].equals(com.blueseer.utl.BlueSeerUtils.SuccessBit) && "1".equals(rec.ing_iscompound());
            if (isCompound && totalQty > 0 && (qty / totalQty) * 100.0 >= COMPOUND_INGREDIENT_THRESHOLD_PCT) {
                List<ingData.ing_subingredient> subs = ingData.getSubIngredients(item);
                if (!subs.isEmpty()) {
                    // Sub-ingredient quantities aren't individually known (the
                    // supplier declares an order, not exact splits) - allocate
                    // the compound's total weight evenly across its declared
                    // sub-ingredients. This preserves the compound's overall
                    // position in the descending sort reasonably well but is
                    // an approximation; a compliance reviewer should confirm
                    // this is acceptable or supply real sub-quantities.
                    double each = qty / subs.size();
                    for (ingData.ing_subingredient sub : subs) {
                        String label = sub.sub_name() + (sub.sub_enumber().isBlank() ? "" : "|E:" + sub.sub_enumber())
                                + (("1".equals(sub.is_allergen())) ? "|A" : "");
                        expanded.merge(label, each, Double::sum);
                    }
                    continue;
                }
            }
            expanded.merge(item, qty, Double::sum);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(expanded.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Segment> segments = new ArrayList<>();
        java.util.Set<String> triggeredAllergenCodes = new java.util.LinkedHashSet<>();
        java.util.Set<String> triggeredENumbers = new java.util.LinkedHashSet<>();
        java.util.Set<String> triggeredCategories = new java.util.LinkedHashSet<>();

        for (Map.Entry<String, Double> entry : sorted) {
            String key = entry.getKey();
            if (key.contains("|")) {
                // synthetic sub-ingredient label from the splice step above
                String[] parts = key.split("\\|", -1);
                String name = parts[0];
                String enumber = "";
                boolean isAllergen = false;
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].startsWith("E:")) {
                        enumber = parts[i].substring(2);
                    } else if (parts[i].equals("A")) {
                        isAllergen = true;
                    }
                }
                String text = enumber.isBlank() ? name : name + " (" + enumber + ")";
                segments.add(new Segment(text, isAllergen));
                if (!enumber.isBlank()) {
                    triggeredENumbers.add(enumber.toUpperCase(Locale.ROOT));
                }
                continue;
            }
            ingData.ing_mstr rec = ingData.getIngMstr(key);
            boolean found = rec.m() != null && rec.m().length > 0 && rec.m()[0].equals(com.blueseer.utl.BlueSeerUtils.SuccessBit);
            String legalName = found && !rec.ing_legalname().isBlank() ? rec.ing_legalname() : key;
            String category = found ? rec.ing_category() : "";
            String enumber = found ? rec.ing_enumber() : "";
            List<String> allergenCodes = ingData.getAllergenCodes(key);
            boolean isAllergen = !allergenCodes.isEmpty();

            String text = (!category.isBlank() && !enumber.isBlank()) ? category + " (" + enumber + ")"
                    : (!category.isBlank() ? category : legalName);
            segments.add(new Segment(text, isAllergen));

            triggeredAllergenCodes.addAll(allergenCodes);
            if (!enumber.isBlank()) {
                triggeredENumbers.add(enumber.toUpperCase(Locale.ROOT));
            }
            if (!category.isBlank()) {
                triggeredCategories.add(category);
            }
        }

        List<String> warnings = evaluateWarnings(triggeredAllergenCodes, triggeredENumbers, triggeredCategories);

        return new IngredientLabelResult(segments, warnings, lotNumber, bestBeforeDate);
    }

    private List<String> evaluateWarnings(java.util.Set<String> allergenCodes, java.util.Set<String> enumbers,
            java.util.Set<String> categories) {
        List<String> warnings = new ArrayList<>();
        for (ingData.warn_rule rule : ingData.getWarnRules()) {
            boolean triggered = switch (rule.trigger_type()) {
                case "ALLERGEN_CODE" -> allergenCodes.contains(rule.trigger_value().toUpperCase(Locale.ROOT));
                case "E_NUMBER" -> enumbers.contains(rule.trigger_value().toUpperCase(Locale.ROOT));
                case "CATEGORY" -> categories.stream().anyMatch(c -> c.equalsIgnoreCase(rule.trigger_value()));
                default -> false;
            };
            if (triggered) {
                warnings.add(rule.warning_text());
            }
        }
        return warnings;
    }

    private void flattenRecursive(Connection con, String item, double qtyPerUnit, Map<String, Double> flatQty)
            throws SQLException {
        for (BomLine line : getBomLines(con, item)) {
            double childQty = qtyPerUnit * line.qtyPer();
            if (line.type().equalsIgnoreCase("M")) {
                flattenRecursive(con, line.child(), childQty, flatQty);
            } else {
                flatQty.merge(line.child(), childQty, Double::sum);
            }
        }
    }

    /**
     * All lines of an item's primary BOM. Deliberately does not join
     * item_cost the way OVData.getpsmstrlist() does - a raw material with no
     * cost record set up would otherwise be silently dropped from a legal
     * ingredient list, which is a correctness bug we can't afford here.
     */
    private List<BomLine> getBomLines(Connection con, String item) throws SQLException {
        List<BomLine> lines = new ArrayList<>();
        String bomId = null;
        try (PreparedStatement ps = con.prepareStatement(
                "select bom_id from bom_mstr where bom_item = ? and bom_primary = '1';")) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    bomId = res.getString("bom_id");
                }
            }
        }
        if (bomId == null) {
            return lines;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "select ps_child, ps_type, ps_qty_per from pbm_mstr where ps_parent = ? and ps_bom = ?;")) {
            ps.setString(1, item);
            ps.setString(2, bomId);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    double qtyPer;
                    try {
                        qtyPer = Double.parseDouble(res.getString("ps_qty_per"));
                    } catch (NumberFormatException nfe) {
                        qtyPer = 0;
                    }
                    lines.add(new BomLine(res.getString("ps_child"), res.getString("ps_type"), qtyPer));
                }
            }
        }
        return lines;
    }
}
