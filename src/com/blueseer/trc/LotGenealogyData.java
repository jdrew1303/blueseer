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
package com.blueseer.trc;

import bsmf.MainFrame;
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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lot genealogy / recall traceability, built entirely on transaction history
 * BlueSeer already keeps in {@code tran_mstr} - no new schema. Two facts make
 * this possible:
 *
 * <p>1. A production consumption row (written by {@code OVData.wip_iss_mtl_gl}
 * for {@code tr_type = 'ISS-PRD'}/{@code 'ISS-SUB'}) stores {@code tr_item}/
 * {@code tr_serial} as the raw-material item/lot actually consumed and
 * {@code tr_lot} as the parent finished-good's own batch/serial number - see
 * the "tr_lot should be parent serial number...ties in components" comment
 * at the insert site. This link is only populated when production is
 * reported through a screen that lets an operator pick the specific
 * component lot ({@code ProdEntryMaint}/{@code ProdEntryByPlanMaint}) -
 * {@code BackFlushMaint}/{@code JobScanIO} post consumption without it.
 *
 * <p>2. A shipment row (written by {@code shpData._addTranMstrShipper} for
 * {@code tr_type = 'ISS-SALES'}) stores {@code tr_serial} as the batch
 * shipped and {@code tr_addrcode} as the customer - this link does not
 * depend on how production was reported, so batch-to-customer tracing
 * always works regardless of the gap above.
 */
public class LotGenealogyData {

    public record BatchHit(String batchSerial, String batchItem, String batchDesc, String firstConsumedDate) {
    }

    public record CustomerShipment(String finishedItem, String itemDesc, String custCode, String custName,
            String custPhone, String custEmail, String shipDate, double qty, String order) {
    }

    public record SupplierLotTrace(String rawItem, String rawSerial, ArrayList<BatchHit> batches) {
    }

    /**
     * Forward trace, scenario 1: given a raw-material lot/serial as received,
     * find every production batch that consumed it. Raw material item is
     * optional (blank matches any item) - {@code in_serial}/{@code tr_serial}
     * values come from a single global sequence ({@code OVData.getNextNbr}),
     * so a bare serial number is normally unambiguous on its own, but
     * narrowing by item is supported for the rare case of a manually-typed
     * duplicate.
     */
    public static SupplierLotTrace traceSupplierLot(String rawItem, String rawSerial) {
        ArrayList<BatchHit> batches = new ArrayList<>();
        String resolvedItem = rawItem == null ? "" : rawItem;
        String sql = "select tr_lot as batch_serial, tr_item, min(tr_eff_date) as first_date "
                + "from tran_mstr "
                + "where tr_serial = ? and tr_lot <> '' and tr_lot is not null "
                + "and tr_type in ('ISS-PRD','ISS-SUB','ISS-SCRAP') "
                + (resolvedItem.isBlank() ? "" : "and tr_item = ? ")
                + "group by tr_lot, tr_item order by first_date;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rawSerial);
            if (!resolvedItem.isBlank()) {
                ps.setString(2, resolvedItem);
            }
            try (ResultSet res = ps.executeQuery()) {
                Set<String> seenItems = new LinkedHashSet<>();
                while (res.next()) {
                    String consumedItem = res.getString("tr_item");
                    seenItems.add(consumedItem);
                    String batchSerial = res.getString("batch_serial");
                    batches.add(new BatchHit(batchSerial, itemOfBatch(con, batchSerial),
                            itemDescOfBatch(con, batchSerial), res.getString("first_date")));
                }
                if (resolvedItem.isBlank() && seenItems.size() > 1) {
                    MainFrame.bslog(new SQLException("Serial " + rawSerial
                            + " was consumed under more than one raw-material item ("
                            + String.join(", ", seenItems) + ") - results may span unrelated items."));
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return new SupplierLotTrace(resolvedItem, rawSerial, batches);
    }

    /**
     * Forward trace, scenario 2: given a finished-good batch/serial, find
     * every customer shipment that included it - the "who needs to be
     * called" list for an actual recall.
     */
    public static ArrayList<CustomerShipment> traceBatchCustomers(String batchSerial) {
        ArrayList<CustomerShipment> list = new ArrayList<>();
        String sql = "select tr.tr_item, im.it_desc, tr.tr_addrcode, cm.cm_name, cm.cm_phone, cm.cm_email, "
                + "tr.tr_eff_date, tr.tr_qty, tr.tr_order "
                + "from tran_mstr tr "
                + "left outer join item_mstr im on im.it_item = tr.tr_item "
                + "left outer join cm_mstr cm on cm.cm_code = tr.tr_addrcode "
                + "where tr.tr_serial = ? and tr.tr_type = 'ISS-SALES' "
                + "order by tr.tr_eff_date;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, batchSerial);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    list.add(new CustomerShipment(res.getString("tr_item"), res.getString("it_desc"),
                            res.getString("tr_addrcode"), res.getString("cm_name"), res.getString("cm_phone"),
                            res.getString("cm_email"), res.getString("tr_eff_date"),
                            // shipment relief rows are stored negative (see shpData._addTranMstrShipper) -
                            // report the positive quantity actually shipped
                            Math.abs(res.getDouble("tr_qty")), res.getString("tr_order")));
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return list;
    }

    private static String itemOfBatch(Connection con, String batchSerial) throws SQLException {
        String sql = "select tr_item from tran_mstr where tr_serial = ? and tr_type = 'RCT-FG' limit 1;";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, batchSerial);
            try (ResultSet res = ps.executeQuery()) {
                return res.next() ? res.getString("tr_item") : "";
            }
        }
    }

    private static String itemDescOfBatch(Connection con, String batchSerial) throws SQLException {
        String sql = "select im.it_desc from tran_mstr tr inner join item_mstr im on im.it_item = tr.tr_item "
                + "where tr.tr_serial = ? and tr.tr_type = 'RCT-FG' limit 1;";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, batchSerial);
            try (ResultSet res = ps.executeQuery()) {
                return res.next() ? res.getString("it_desc") : "";
            }
        }
    }
}
