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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates a per-100g (and, optionally, per-portion) EU FIC nutrition
 * declaration (Regulation (EU) 1169/2011 Article 30-35, Annexes XIII-XV) for
 * a finished item, reusing {@link IngredientLabelEngine}'s BOM recursion
 * rather than re-implementing it - see {@link IngredientLabelEngine#flattenRecursive}
 * for the shared gram-normalized flatten this engine calls directly, and
 * {@link IngredientLabelEngine#getFinishedNetWeightG} for the same
 * moisture-loss-aware finished-weight denominator the ingredient engine
 * already relies on for its own QUID/compound-ingredient math.
 *
 * IMPORTANT: the rounding tiers and %RI handling below implement FIC as
 * researched (the consolidated regulation text plus the Commission's
 * tolerance/rounding guidance document, Dec 2012) but are not a legal
 * sign-off - see the epic's NUTR-15 story. In particular, which items
 * actually qualify for an Annex V exemption (driving {@code
 * nutData.item_nut_cfg.nut_placement = NONE}) is a business judgment this
 * engine has no way to make on its own; it only renders whatever placement
 * is already configured.
 *
 * <h2>Compound ingredients are handled differently here than in {@link IngredientLabelEngine}</h2>
 * The ingredient-list engine decomposes a purchased compound ingredient
 * (ing_mstr.ing_iscompound) into its declared sub-ingredients above a 2%
 * threshold, because FIC requires disclosing what a compound is made of.
 * Nutrition has no equivalent requirement - a specific purchased SKU's real
 * nutrition (e.g. one supplier's chocolate chips) can't be accurately
 * reconstructed from a generic breakdown estimate, so this engine always
 * uses the compound ingredient's own supplier-declared {@code ing_nutrient}
 * values as a single leaf, never recursing into its sub-ingredients.
 *
 * <h2>Sodium and salt</h2>
 * Supplier spec sheets almost always declare sodium, not salt, so this
 * engine only reads the SODIUM nutrient code from ing_nutrient and derives
 * {@code salt = sodium x 2.5} centrally (Article 30(1)) - SALT is never
 * itself a valid data-entry target (see {@link #DERIVED_CODES}).
 *
 * <h2>Energy</h2>
 * ENERGY is likewise never a data-entry target - it's calculated from the
 * other accumulated nutrients using the Annex XIV conversion factors on
 * {@code nut_mstr.energy_kj_per_g}/{@code energy_kcal_per_g} (zero on "of
 * which" sub-line nutrients so they don't double-count against their parent
 * fat/carbohydrate figure), with one explicit carve-out Annex XIV requires:
 * carbohydrate's conversion factor applies to carbohydrate <i>except
 * polyols</i>, since polyols (already included in the total carbohydrate
 * figure per Annex XV) get their own, lower factor instead.
 */
public class NutritionLabelEngine {

    /** Never a valid ing_nutrient data-entry target - both are always computed by this engine. */
    public static final Set<String> DERIVED_CODES = Set.of("SALT", "ENERGY");

    /**
     * One declared-or-derived nutrient's row in the panel, in Annex XV order.
     * The four energy-only fields are non-null solely on the ENERGY row - kJ
     * and kcal need their own numbers (not just a pre-formatted display
     * string) so the ZPL table can print them as separate rows rather than
     * the combined "1801 kJ / 428 kcal" text {@link #per100Display} carries
     * for the HTML/plain-text renderers.
     */
    public record NutrientAmount(String code, String displayName, boolean isSubLine, boolean isMandatory,
            String per100Display, String perPortionDisplay, Integer riPercentPer100, Integer riPercentPerPortion,
            Long kjPer100, Long kcalPer100, Long kjPerPortion, Long kcalPerPortion) {
    }

    public record NutritionLabelResult(List<NutrientAmount> nutrients, boolean dataComplete,
            List<String> incompleteIngredients, List<String> internalNotes, Double portionSizeG,
            String portionDesc, Integer portionsPerPack, boolean showRiPct) {

        private static final String RI_DISCLAIMER = "Reference intake of an average adult (8 400 kJ/2 000 kcal)";

        public String toHtml() {
            StringBuilder sb = new StringBuilder("<table>");
            for (NutrientAmount n : nutrients) {
                sb.append("<tr><td>").append(n.isSubLine() ? "&nbsp;&nbsp;of which " + escapeHtml(stripOfWhich(n.displayName())) : escapeHtml(n.displayName()))
                        .append("</td><td>").append(escapeHtml(n.per100Display()));
                if (n.riPercentPer100() != null) {
                    sb.append(" (").append(n.riPercentPer100()).append("% RI)");
                }
                sb.append("</td>");
                if (n.perPortionDisplay() != null) {
                    sb.append("<td>").append(escapeHtml(n.perPortionDisplay()));
                    if (n.riPercentPerPortion() != null) {
                        sb.append(" (").append(n.riPercentPerPortion()).append("% RI)");
                    }
                    sb.append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</table>");
            if (showRiPct) {
                sb.append("<p>").append(escapeHtml(RI_DISCLAIMER)).append("</p>");
            }
            return sb.toString();
        }

        public String toPlainText() {
            StringBuilder sb = new StringBuilder();
            for (NutrientAmount n : nutrients) {
                sb.append(n.isSubLine() ? "  of which " + stripOfWhich(n.displayName()) : n.displayName())
                        .append(": ").append(n.per100Display());
                if (n.riPercentPer100() != null) {
                    sb.append(" (").append(n.riPercentPer100()).append("% RI)");
                }
                sb.append("\n");
            }
            if (showRiPct) {
                sb.append(RI_DISCLAIMER).append("\n");
            }
            return sb.toString();
        }

        /** One row of the printed nutrition table: a label plus its per-100g value and (if configured) per-portion value, side by side in their own columns. */
        private record TableRow(String label, boolean indented, String per100Value, String portionValue) {
        }

        // Sub-line ("of which ...") rows get a real x-offset, not just leading spaces in the
        // text, so they read as indented under their parent regardless of the printer font's
        // actual glyph-space width.
        private static final int SUBLINE_INDENT = 20;

        /**
         * Builds the actual ruled ZPL table for the calculated panel: a
         * label column, a "per 100g" column, and (only when a portion size
         * is configured) a "per serving" column alongside it - side by side
         * rather than stacked as separate rows, since a nutrient only ever
         * needs one row this way. Energy still gets its own kJ row and kcal
         * row (those are different units, not different serving sizes), but
         * each of those rows now also carries both columns. Row/column
         * widths use the same real-font measurement as {@link
         * IngredientLabelEngine}'s ingredient list ({@link ZplTextUtils}) so
         * a long label wraps within its own column instead of overrunning
         * the value column(s).
         *
         * Returns the Y just past the table (and the RI disclaimer below
         * it), so the next label section can start right there - see
         * {@link ZplTextUtils.Rendered}.
         */
        public ZplTextUtils.Rendered toZplLabelBody(int x, int y, int width, int fontHeight, int lineSpacing) {
            boolean hasPortion = portionSizeG != null && portionSizeG > 0;

            List<TableRow> rows = new ArrayList<>();
            for (NutrientAmount n : nutrients) {
                String label = n.isSubLine() ? "of which " + stripOfWhich(n.displayName()) : n.displayName();
                if (n.kjPer100() != null) {
                    // Energy: kJ and kcal are separate units, so they're always their own rows -
                    // each still carries both the 100g and portion columns like any other row.
                    rows.add(new TableRow(label + " (kJ)", n.isSubLine(),
                            n.kjPer100() + " kJ" + riSuffix(n.riPercentPer100()),
                            hasPortion ? n.kjPerPortion() + " kJ" + riSuffix(n.riPercentPerPortion()) : null));
                    rows.add(new TableRow(label + " (kcal)", n.isSubLine(),
                            n.kcalPer100() + " kcal" + riSuffix(n.riPercentPer100()),
                            hasPortion ? n.kcalPerPortion() + " kcal" + riSuffix(n.riPercentPerPortion()) : null));
                } else {
                    rows.add(new TableRow(label, n.isSubLine(),
                            n.per100Display() + riSuffix(n.riPercentPer100()),
                            hasPortion ? n.perPortionDisplay() + riSuffix(n.riPercentPerPortion()) : null));
                }
            }

            int labelColWidth = (int) (width * (hasPortion ? 0.44 : 0.60));
            int valueColsWidth = width - labelColWidth;
            int per100ColWidth = hasPortion ? valueColsWidth / 2 : valueColsWidth;
            int per100ColX = x + labelColWidth + 8;
            int portionColX = per100ColX + per100ColWidth;
            int portionColWidth = valueColsWidth - per100ColWidth;
            int rowGap = fontHeight + lineSpacing;

            StringBuilder zpl = new StringBuilder();
            ZplTextUtils.appendDivider(zpl, x, y, width);
            int cy = y + 14;

            if (hasPortion) {
                ZplTextUtils.appendZplField(zpl, per100ColX, cy, fontHeight, "Per 100g", false);
                ZplTextUtils.appendZplField(zpl, portionColX, cy, fontHeight, "Per serving", false);
                cy += rowGap;
                ZplTextUtils.appendDivider(zpl, x, cy, width);
                cy += 10;
            }
            int tableTop = cy;

            for (TableRow row : rows) {
                int labelX = x + 4 + (row.indented() ? SUBLINE_INDENT : 0);
                List<String> labelLines = ZplTextUtils.wrapPlain(row.label(), fontHeight, labelColWidth - 8 - (row.indented() ? SUBLINE_INDENT : 0));
                List<String> per100Lines = ZplTextUtils.wrapPlain(row.per100Value(), fontHeight, per100ColWidth - 8);
                List<String> portionLines = row.portionValue() != null
                        ? ZplTextUtils.wrapPlain(row.portionValue(), fontHeight, portionColWidth - 8) : List.of();
                for (int i = 0; i < labelLines.size(); i++) {
                    ZplTextUtils.appendZplField(zpl, labelX, cy + i * rowGap, fontHeight, labelLines.get(i), false);
                }
                for (int i = 0; i < per100Lines.size(); i++) {
                    ZplTextUtils.appendZplField(zpl, per100ColX, cy + i * rowGap, fontHeight, per100Lines.get(i), false);
                }
                for (int i = 0; i < portionLines.size(); i++) {
                    ZplTextUtils.appendZplField(zpl, portionColX, cy + i * rowGap, fontHeight, portionLines.get(i), false);
                }
                cy += rowGap * Math.max(1, Math.max(labelLines.size(), Math.max(per100Lines.size(), portionLines.size())));
            }
            ZplTextUtils.appendVerticalDivider(zpl, x + labelColWidth, tableTop, cy - tableTop);
            if (hasPortion) {
                ZplTextUtils.appendVerticalDivider(zpl, portionColX - 8, tableTop, cy - tableTop);
            }
            ZplTextUtils.appendDivider(zpl, x, cy, width);
            cy += 14;

            if (showRiPct) {
                for (String dl : ZplTextUtils.wrapPlain(RI_DISCLAIMER, fontHeight, width)) {
                    ZplTextUtils.appendZplField(zpl, x, cy, fontHeight, dl, false);
                    cy += rowGap;
                }
            }
            return new ZplTextUtils.Rendered(zpl.toString(), cy);
        }

        private static String riSuffix(Integer riPercent) {
            return riPercent != null ? " (" + riPercent + "% RI)" : "";
        }

        private static String stripOfWhich(String displayName) {
            return displayName.startsWith("of which ") ? displayName.substring("of which ".length()) : displayName;
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** Fixed Annex XIII Part B adult reference intakes - regulatory constants, not client-adjustable data. */
    private static final Map<String, Double> RI_CONSTANTS = Map.of(
            "FAT", 70.0, "SATURATES", 20.0, "CARBOHYDRATE", 260.0, "SUGARS", 90.0, "PROTEIN", 50.0, "SALT", 6.0);
    private static final double RI_ENERGY_KJ = 8400.0;

    public NutritionLabelResult generate(String finishedItem) {
        List<String> internalNotes = new ArrayList<>();
        List<nutData.nut_mstr> nutMstrList = nutData.getNutMstrList();
        Map<String, Double> flatQty = new LinkedHashMap<>();
        double finishedWeightG = 0;
        IngredientLabelEngine ingEngine = new IngredientLabelEngine();
        Map<String, String> reconMap = ingData.getReconMap(finishedItem);
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            ingEngine.flattenRecursive(con, finishedItem, 1.0, flatQty, reconMap);
            finishedWeightG = ingEngine.getFinishedNetWeightG(con, finishedItem);
        } catch (SQLException s) {
            bslog(s);
        }

        nutData.item_nut_cfg cfg = nutData.getItemNutCfg(finishedItem);

        if (finishedWeightG <= 0) {
            internalNotes.add("item_mstr.it_net_wt is not set for " + finishedItem
                    + " - nutrition values cannot be expressed per 100g, so no panel can be calculated.");
            return new NutritionLabelResult(List.of(), false, List.of(), internalNotes, null, "", null, false);
        }

        // Required *input* codes to complete the mandatory declaration (Article 30(1)):
        // every mandatory nut_mstr row except the derived ones (SALT/ENERGY - neither
        // can ever be entered directly), plus SODIUM (the actual input SALT is derived
        // from - see class javadoc).
        Set<String> requiredInputCodes = new LinkedHashSet<>();
        for (nutData.nut_mstr nm : nutMstrList) {
            if (nm.is_mandatory() && !DERIVED_CODES.contains(nm.nutrient_code())) {
                requiredInputCodes.add(nm.nutrient_code());
            }
        }
        requiredInputCodes.add("SODIUM");

        Map<String, Double> totalGInFinished = new LinkedHashMap<>();
        List<String> incompleteIngredients = new ArrayList<>();
        for (Map.Entry<String, Double> entry : flatQty.entrySet()) {
            String item = entry.getKey();
            double gramsInBatch = entry.getValue();
            Map<String, nutData.ing_nutrient> ingNutrients = nutData.getIngNutrients(item);
            boolean complete = ingNutrients.keySet().containsAll(requiredInputCodes);
            if (!complete) {
                incompleteIngredients.add(item);
            }
            for (nutData.nut_mstr nm : nutMstrList) {
                if (DERIVED_CODES.contains(nm.nutrient_code())) {
                    continue;
                }
                nutData.ing_nutrient v = ingNutrients.get(nm.nutrient_code());
                double valuePer100g = v == null ? 0 : v.value_per_100g();
                double gramsOfNutrient = gramsInBatch / 100.0 * valuePer100g;
                totalGInFinished.merge(nm.nutrient_code(), gramsOfNutrient, Double::sum);
            }
        }
        boolean dataComplete = incompleteIngredients.isEmpty();
        if (!dataComplete) {
            internalNotes.add("Missing mandatory nutrient data for: " + String.join(", ", incompleteIngredients)
                    + " - the calculated panel below is incomplete and should not be printed as-is (see NUTR-13).");
        }

        Map<String, Double> per100 = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : totalGInFinished.entrySet()) {
            per100.put(e.getKey(), e.getValue() / finishedWeightG * 100.0);
        }

        // Salt derived from sodium (Article 30(1)) - never stored/entered directly.
        double saltPer100 = per100.getOrDefault("SODIUM", 0.0) * 2.5;

        // Energy (Annex XIV): carbohydrate's factor applies to carbohydrate EXCEPT
        // polyols, which get their own, separate factor - polyols are already part of
        // the total carbohydrate figure (Annex XV "of which"), so summing both the full
        // carbohydrate figure AND polyols at their own factor would double-count them.
        double fatG = per100.getOrDefault("FAT", 0.0);
        double carbTotalG = per100.getOrDefault("CARBOHYDRATE", 0.0);
        double polyolsG = per100.getOrDefault("POLYOLS", 0.0);
        double proteinG = per100.getOrDefault("PROTEIN", 0.0);
        double fibreG = per100.getOrDefault("FIBRE", 0.0);
        double carbExceptPolyolsG = Math.max(0, carbTotalG - polyolsG);
        nutData.nut_mstr fatSpec = findSpec(nutMstrList, "FAT");
        nutData.nut_mstr carbSpec = findSpec(nutMstrList, "CARBOHYDRATE");
        nutData.nut_mstr proteinSpec = findSpec(nutMstrList, "PROTEIN");
        nutData.nut_mstr fibreSpec = findSpec(nutMstrList, "FIBRE");
        nutData.nut_mstr polyolsSpec = findSpec(nutMstrList, "POLYOLS");
        double energyKJPer100 = fatG * factor(fatSpec, true) + carbExceptPolyolsG * factor(carbSpec, true)
                + proteinG * factor(proteinSpec, true) + fibreG * factor(fibreSpec, true)
                + polyolsG * factor(polyolsSpec, true);
        double energyKcalPer100 = fatG * factor(fatSpec, false) + carbExceptPolyolsG * factor(carbSpec, false)
                + proteinG * factor(proteinSpec, false) + fibreG * factor(fibreSpec, false)
                + polyolsG * factor(polyolsSpec, false);

        Double portionSizeG = cfg.nut_portion_size_g();
        double portionFactor = portionSizeG != null && portionSizeG > 0 ? portionSizeG / 100.0 : 0;
        boolean hasPortion = portionFactor > 0;

        List<NutrientAmount> nutrients = new ArrayList<>();
        for (nutData.nut_mstr nm : nutMstrList) {
            if (nm.nutrient_code().equals("SODIUM")) {
                continue; // internal only - never itself declared, see class javadoc
            }
            double valuePer100;
            if (nm.nutrient_code().equals("SALT")) {
                valuePer100 = saltPer100;
            } else if (nm.nutrient_code().equals("ENERGY")) {
                valuePer100 = 0; // formatted specially below
            } else {
                valuePer100 = per100.getOrDefault(nm.nutrient_code(), 0.0);
            }

            String per100Display;
            String perPortionDisplay = null;
            Integer riPer100 = null;
            Integer riPerPortion = null;
            Long kjPer100 = null;
            Long kcalPer100 = null;
            Long kjPerPortion = null;
            Long kcalPerPortion = null;

            if (nm.nutrient_code().equals("ENERGY")) {
                per100Display = formatEnergy(energyKJPer100, energyKcalPer100);
                kjPer100 = Math.round(energyKJPer100);
                kcalPer100 = Math.round(energyKcalPer100);
                if (hasPortion) {
                    perPortionDisplay = formatEnergy(energyKJPer100 * portionFactor, energyKcalPer100 * portionFactor);
                    kjPerPortion = Math.round(energyKJPer100 * portionFactor);
                    kcalPerPortion = Math.round(energyKcalPer100 * portionFactor);
                }
                if (cfg.nut_show_ri_pct()) {
                    riPer100 = riPercent(energyKJPer100, RI_ENERGY_KJ);
                    if (hasPortion) {
                        riPerPortion = riPercent(energyKJPer100 * portionFactor, RI_ENERGY_KJ);
                    }
                }
            } else {
                per100Display = formatGrams(valuePer100, nm);
                if (hasPortion) {
                    perPortionDisplay = formatGrams(valuePer100 * portionFactor, nm);
                }
                if (cfg.nut_show_ri_pct() && nm.has_ri() && RI_CONSTANTS.containsKey(nm.nutrient_code())) {
                    double ri = RI_CONSTANTS.get(nm.nutrient_code());
                    riPer100 = riPercent(valuePer100, ri);
                    if (hasPortion) {
                        riPerPortion = riPercent(valuePer100 * portionFactor, ri);
                    }
                }
            }

            boolean isSubLine = nm.display_name().startsWith("of which ");
            nutrients.add(new NutrientAmount(nm.nutrient_code(), nm.display_name(), isSubLine, nm.is_mandatory(),
                    per100Display, perPortionDisplay, riPer100, riPerPortion,
                    kjPer100, kcalPer100, kjPerPortion, kcalPerPortion));
        }

        return new NutritionLabelResult(nutrients, dataComplete, incompleteIngredients, internalNotes,
                portionSizeG, cfg.nut_portion_desc(), cfg.nut_portions_per_pack(), cfg.nut_show_ri_pct());
    }

    private static nutData.nut_mstr findSpec(List<nutData.nut_mstr> list, String code) {
        for (nutData.nut_mstr nm : list) {
            if (nm.nutrient_code().equals(code)) {
                return nm;
            }
        }
        return null;
    }

    private static double factor(nutData.nut_mstr spec, boolean kj) {
        if (spec == null) {
            return 0;
        }
        return kj ? spec.energy_kj_per_g() : spec.energy_kcal_per_g();
    }

    static int riPercent(double value, double referenceIntake) {
        if (referenceIntake <= 0) {
            return 0;
        }
        return (int) Math.round(value / referenceIntake * 100.0);
    }

    private static String formatEnergy(double kj, double kcal) {
        return roundToNearestInt(kj) + " kJ / " + roundToNearestInt(kcal) + " kcal";
    }

    private static long roundToNearestInt(double v) {
        return Math.round(v);
    }

    /**
     * Applies the confirmed three-tier rounding regime (Commission guidance
     * document, Dec 2012 - see nut_mstr's schema comment for the exact
     * bands): at/above high_threshold_g rounds to high_precision_g, between
     * trace_cutoff_g and high_threshold_g rounds to mid_precision_g, at/below
     * trace_cutoff_g is shown as the nutrient's own trace_label_g (e.g.
     * "&lt;0.5g") rather than a potentially-misleading rounded figure - "0g"
     * only when the true value is exactly (or negligibly close to) zero.
     */
    static String formatGrams(double valueG, nutData.nut_mstr spec) {
        double v = Math.max(0, valueG);
        if (v <= spec.trace_cutoff_g()) {
            if (v < spec.trace_cutoff_g() / 100.0) {
                return "0g";
            }
            return "<" + trimTrailingZeros(spec.trace_label_g()) + "g";
        }
        double precision = v >= spec.high_threshold_g() ? spec.high_precision_g() : spec.mid_precision_g();
        double rounded = roundToIncrement(v, precision);
        return formatFixed(rounded, decimalPlaces(precision)) + "g";
    }

    static double roundToIncrement(double value, double increment) {
        if (increment <= 0) {
            return value;
        }
        BigDecimal bdValue = BigDecimal.valueOf(value);
        BigDecimal bdIncrement = BigDecimal.valueOf(increment);
        BigDecimal steps = bdValue.divide(bdIncrement, 0, RoundingMode.HALF_UP);
        return steps.multiply(bdIncrement).doubleValue();
    }

    private static int decimalPlaces(double increment) {
        if (increment >= 1) {
            return 0;
        }
        if (increment >= 0.1) {
            return 1;
        }
        if (increment >= 0.01) {
            return 2;
        }
        return 3;
    }

    private static String formatFixed(double v, int decimals) {
        return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    private static String trimTrailingZeros(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }
}
