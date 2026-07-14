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
import java.util.List;

/**
 * Catalog-wide nutrition-data completeness check - the per-item version of
 * this (NUTR-13) already blocks/warns at print time via {@link
 * NutritionLabelEngine#generate}, but there was no way to see gaps across
 * the whole catalog at once. Built specifically to give NUTR-15's external
 * compliance reviewer (and the client, before go-live) a single list of
 * every finished item's nutrition-declaration status, rather than clicking
 * through Item Maintenance one item at a time.
 *
 * Reuses {@link NutritionLabelEngine#generate} as-is - this is purely a
 * loop over every active finished item, not new calculation logic.
 */
public class NutritionCompletenessReport {

    /**
     * One finished item's nutrition-declaration status.
     *
     * @param placement       {@code nutData.item_nut_cfg.nut_placement} - NONE/MAIN_LABEL/SEPARATE_LABEL.
     * @param exempt          true when placement is NONE (no panel is ever calculated for this item).
     * @param exemptReason    the audit-trail reason required when exempt - see {@code item_nut_cfg}.
     * @param dataComplete    false when at least one ingredient is missing mandatory nutrient data - always
     *                        true (vacuously) when exempt, since no panel is calculated to be incomplete.
     * @param incompleteIngredients item codes missing mandatory nutrient data - empty when exempt or complete.
     * @param internalNotes   engine notes (e.g. missing net weight) - surfaced verbatim for the reviewer.
     */
    public record Row(String item, String desc, String placement, boolean exempt, String exemptReason,
            boolean dataComplete, List<String> incompleteIngredients, List<String> internalNotes) {
    }

    /**
     * Checks every ACTIVE finished (it_type = 'FIN') item. Calls {@link
     * NutritionLabelEngine#generate} once per non-exempt item, so this is a
     * DB round-trip per ingredient per item - deliberately a manually
     * triggered report (see NutritionCompletenessPanel's "Run Report"
     * button), not something run automatically on every screen open.
     */
    public static List<Row> checkAll() {
        List<Row> rows = new ArrayList<>();
        List<String[]> items = new ArrayList<>();
        String sql = "select it_item, it_desc from item_mstr where it_type = ? and it_status = ? order by it_item";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "FIN");
            ps.setString(2, "ACTIVE");
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    items.add(new String[]{res.getString("it_item"), res.getString("it_desc")});
                }
            }
        } catch (SQLException s) {
            bslog(s);
            return rows;
        }

        NutritionLabelEngine engine = new NutritionLabelEngine();
        for (String[] item : items) {
            String code = item[0];
            String desc = item[1];
            nutData.item_nut_cfg cfg = nutData.getItemNutCfg(code);
            boolean exempt = nutData.item_nut_cfg.NONE.equals(cfg.nut_placement());
            if (exempt) {
                rows.add(new Row(code, desc, cfg.nut_placement(), true, cfg.nut_exempt_reason(),
                        true, List.of(), List.of()));
                continue;
            }
            NutritionLabelEngine.NutritionLabelResult result = engine.generate(code);
            rows.add(new Row(code, desc, cfg.nut_placement(), false, "",
                    result.dataComplete(), result.incompleteIngredients(), result.internalNotes()));
        }
        return rows;
    }
}
