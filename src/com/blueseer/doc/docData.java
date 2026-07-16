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
package com.blueseer.doc;

import bsmf.MainFrame;
import static bsmf.MainFrame.bslog;
import static bsmf.MainFrame.db;
import static bsmf.MainFrame.ds;
import static bsmf.MainFrame.pass;
import static bsmf.MainFrame.url;
import static bsmf.MainFrame.user;
import com.blueseer.utl.BlueSeerUtils;
import static com.blueseer.utl.BlueSeerUtils.cleanDirString;
import com.blueseer.utl.OVData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for agentic document import: the one-time local-LLM runtime
 * configuration (System Control, never staff-facing), the import audit
 * trail, and the per-supplier item-description memory that lets a repeat
 * invoice line auto-fill without asking again. Follows the same
 * conventions as com.blueseer.ing.ingData: PreparedStatement everywhere,
 * no string-concatenated SQL.
 */
public class docData {

    public record llm_config(String provider, String baseurl, String model, boolean enabled) {
    }

    /**
     * Optional second model for Scan to Import - a document-layout model
     * (e.g. IBM's granite-docling-258M) that converts a page image into
     * "DocTags" text carrying a bounding box per block, used to show where
     * an extracted field actually came from in the source document. Blank
     * model means "not configured" - see docs/patchsqlv_docimport_layoutmodel
     * and DocumentExtractionService's class javadoc for why this is a
     * genuinely separate model from ov_llm_provider/baseurl/model rather
     * than a mode switch on the same one.
     */
    public record layout_llm_config(String provider, String baseurl, String model) {
        public boolean configured() {
            return model != null && !model.isBlank();
        }
    }

    private static final llm_config DEFAULT_CONFIG = new llm_config("LMSTUDIO", "http://localhost:1234", "", false);
    private static final layout_llm_config DEFAULT_LAYOUT_CONFIG =
            new layout_llm_config("LMSTUDIO", "http://localhost:1234", "");

    public static llm_config getLlmConfig() {
        String sql = "select ov_llm_provider, ov_llm_baseurl, ov_llm_model, ov_llm_enabled from ov_ctrl;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet res = ps.executeQuery()) {
            if (res.next()) {
                return new llm_config(res.getString("ov_llm_provider"), res.getString("ov_llm_baseurl"),
                        res.getString("ov_llm_model"), res.getBoolean("ov_llm_enabled"));
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
        return DEFAULT_CONFIG;
    }

    public static String[] saveLlmConfig(llm_config cfg) {
        String sql = "update ov_ctrl set ov_llm_provider = ?, ov_llm_baseurl = ?, ov_llm_model = ?, ov_llm_enabled = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cfg.provider());
            ps.setString(2, cfg.baseurl());
            ps.setString(3, cfg.model());
            ps.setBoolean(4, cfg.enabled());
            int rows = ps.executeUpdate();
            return rows > 0
                    ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.updateRecordSuccess}
                    : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        }
    }

    public static layout_llm_config getLayoutLlmConfig() {
        String sql = "select ov_llm_layout_provider, ov_llm_layout_baseurl, ov_llm_layout_model from ov_ctrl;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet res = ps.executeQuery()) {
            if (res.next()) {
                return new layout_llm_config(res.getString("ov_llm_layout_provider"),
                        res.getString("ov_llm_layout_baseurl"), res.getString("ov_llm_layout_model"));
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
        return DEFAULT_LAYOUT_CONFIG;
    }

    public static String[] saveLayoutLlmConfig(layout_llm_config cfg) {
        String sql = "update ov_ctrl set ov_llm_layout_provider = ?, ov_llm_layout_baseurl = ?, ov_llm_layout_model = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cfg.provider());
            ps.setString(2, cfg.baseurl());
            ps.setString(3, cfg.model());
            int rows = ps.executeUpdate();
            return rows > 0
                    ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.updateRecordSuccess}
                    : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        }
    }

    public static String[] logImport(String docType, String targetKey, String extractedJson) {
        String logId = String.valueOf(OVData.getNextNbr("docimportlog"));
        String sql = "insert into doc_import_log (log_id, log_doctype, log_target_key, log_extracted_json, log_userid, log_created) values (?,?,?,?,?,?);";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, logId);
            ps.setString(2, docType);
            ps.setString(3, targetKey);
            ps.setString(4, extractedJson);
            ps.setString(5, MainFrame.userid);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            int rows = ps.executeUpdate();
            return rows > 0
                    ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.addRecordSuccess}
                    : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
    }

    /**
     * Returns the remembered item code for this supplier + raw line text,
     * or "" if this exact wording hasn't been seen (and corrected) before.
     */
    public static String lookupItemAlias(String vend, String rawText) {
        if (vend == null || vend.isBlank() || rawText == null || rawText.isBlank()) {
            return "";
        }
        String sql = "select alias_item from doc_item_alias where alias_vend = ? and alias_rawtext = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, vend);
            ps.setString(2, rawText);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    return res.getString("alias_item");
                }
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
        return "";
    }

    /**
     * Remembers a supplier's raw line text as mapping to the given item, so
     * the next import from the same supplier auto-fills it (epic Sec 7,
     * DOC-21). Overwrites any previous mapping for the same (vend, rawText)
     * pair - the most recent human correction wins.
     */
    public static String[] saveItemAlias(String vend, String rawText, String item) {
        if (vend == null || vend.isBlank() || rawText == null || rawText.isBlank() || item == null || item.isBlank()) {
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
        String sqlDelete = "delete from doc_item_alias where alias_vend = ? and alias_rawtext = ?;";
        String sqlInsert = "insert into doc_item_alias (alias_vend, alias_rawtext, alias_item) values (?,?,?);";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            try (PreparedStatement psd = con.prepareStatement(sqlDelete)) {
                psd.setString(1, vend);
                psd.setString(2, rawText);
                psd.executeUpdate();
            }
            try (PreparedStatement psi = con.prepareStatement(sqlInsert)) {
                psi.setString(1, vend);
                psi.setString(2, rawText);
                psi.setString(3, item);
                int rows = psi.executeUpdate();
                return rows > 0
                        ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.addRecordSuccess}
                        : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
    }

    /**
     * Best-effort match of an extracted supplier name (free text read off a
     * photo) to a real vd_mstr vendor code, for pre-selecting the vendor
     * combo when the central Scan to Import screen routes into Receiver
     * Maintenance. A simple case-insensitive substring match either way -
     * good enough to save a click on an exact/near match, not intended to be
     * clever; returns "" (leaving the user to pick manually) rather than
     * guess when nothing matches or more than one vendor does.
     */
    public static String findVendorByName(String extractedName) {
        if (extractedName == null || extractedName.isBlank()) {
            return "";
        }
        String sql = "select vd_addr, vd_name from vd_mstr where instr(lower(vd_name), lower(?)) > 0 or instr(lower(?), lower(vd_name)) > 0;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, extractedName);
            ps.setString(2, extractedName);
            String match = "";
            int matches = 0;
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    matches++;
                    match = res.getString("vd_addr");
                }
            }
            return matches == 1 ? match : "";
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return "";
        }
    }

    /**
     * Fuzzy item search by description - backs the ERPTools.searchItemsByDescription
     * tool (com.blueseer.doc.ERPTools) an extraction agent calls itself to resolve
     * a raw invoice line description to a real item_mstr row, rather than BlueSeer
     * matching it after the fact. Returns up to 5 "CODE - description" candidates,
     * one per line, or "" if nothing matches within the given catalog.
     */
    public static String searchItemsByDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String sql = "select it_item, it_desc from item_mstr where instr(lower(it_desc), lower(?)) > 0 or instr(lower(?), lower(it_desc)) > 0 limit 5;";
        StringBuilder result = new StringBuilder();
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setString(2, description);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    if (result.length() > 0) {
                        result.append("\n");
                    }
                    result.append(res.getString("it_item")).append(" - ").append(res.getString("it_desc"));
                }
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return "";
        }
        return result.toString();
    }

    // --- Scan queue (multi-document Inbox/Pending Review/Outbox/Sent/Error) ---
    // Lets more than one document be dropped in at once and processed in the
    // background (see ScanQueueProcessor) instead of one document tying up
    // the whole screen synchronously. Files themselves live on disk under
    // the system's existing attachment directory (same convention as
    // OVData.addFileAttachment - a real BlueSeer file-storage location,
    // not a new one invented for this), with only the path stored here.

    public static final String QUEUE_INBOX = "INBOX";
    public static final String QUEUE_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String QUEUE_OUTBOX = "OUTBOX";
    public static final String QUEUE_SENT = "SENT";
    public static final String QUEUE_ERROR = "ERROR";

    public record QueueItem(String id, String filename, String filepath, String ext, String state,
            String docType, String extractedJson, String docTags, String error, String userid,
            Timestamp created, Timestamp updated) {
    }

    /**
     * Copies the given bytes into the attachment directory and inserts a new
     * INBOX row - ScanQueueProcessor's background thread picks it up from
     * there. Returns null (and logs) on any I/O or SQL failure rather than a
     * half-written row with no file, or a file with no row.
     */
    public static String addQueueItem(byte[] bytes, String filename, String ext) {
        String id = String.valueOf(OVData.getNextNbr("scanqueue"));
        String filepath = cleanDirString(OVData.getSystemAttachmentDirectory()) + "scanqueue_" + id + "_" + filename;
        try {
            Files.write(Path.of(filepath), bytes);
        } catch (IOException e) {
            MainFrame.bslog(e);
            return null;
        }
        String sql = "insert into doc_scan_queue (queue_id, queue_filename, queue_filepath, queue_ext, "
                + "queue_state, queue_userid, queue_created, queue_updated) values (?,?,?,?,?,?,?,?);";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ps.setString(1, id);
            ps.setString(2, filename);
            ps.setString(3, filepath);
            ps.setString(4, ext);
            ps.setString(5, QUEUE_INBOX);
            ps.setString(6, MainFrame.userid);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            int rows = ps.executeUpdate();
            return rows > 0 ? id : null;
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return null;
        }
    }

    public static byte[] readQueueFileBytes(QueueItem item) {
        try {
            return Files.readAllBytes(Path.of(item.filepath()));
        } catch (IOException e) {
            MainFrame.bslog(e);
            return null;
        }
    }

    /**
     * ScanQueueProcessor calls this once classify/extract succeeds. docTags
     * is the raw layout-model output (or "" if no layout model is
     * configured) - kept so the review screen can re-derive source
     * highlighting without re-running any model when the item is opened.
     */
    public static void markQueueProcessed(String id, String docType, String extractedJson, String docTags) {
        String sql = "update doc_scan_queue set queue_state = ?, queue_doctype = ?, queue_extracted_json = ?, "
                + "queue_doctags = ?, queue_error = '', queue_updated = ? where queue_id = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, QUEUE_PENDING_REVIEW);
            ps.setString(2, docType);
            ps.setString(3, extractedJson);
            ps.setString(4, docTags == null ? "" : docTags);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.setString(6, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
    }

    /** ScanQueueProcessor calls this when classify/extract fails outright. */
    public static void markQueueError(String id, String error) {
        String sql = "update doc_scan_queue set queue_state = ?, queue_error = ?, queue_updated = ? where queue_id = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, QUEUE_ERROR);
            ps.setString(2, error);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
    }

    /**
     * Plain state transition with no other field changes - Approve
     * (PENDING_REVIEW -> OUTBOX), Send (OUTBOX -> SENT), Retry
     * (ERROR -> INBOX, picked up by the processor again).
     */
    public static void setQueueState(String id, String state) {
        String sql = "update doc_scan_queue set queue_state = ?, queue_updated = ? where queue_id = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, state);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
    }

    /**
     * Saves the user's corrections from the review screen back onto the
     * queue row (Approve, or a document-type override) without changing its
     * state - docTags is normally whatever was already stored (preserved
     * as-is on a plain field correction) or freshly recomputed blocks (on a
     * type override, which re-runs extraction and may produce new ones).
     */
    public static void updateQueueExtractedJson(String id, String docType, String extractedJson, String docTags) {
        String sql = "update doc_scan_queue set queue_doctype = ?, queue_extracted_json = ?, queue_doctags = ?, "
                + "queue_updated = ? where queue_id = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, docType);
            ps.setString(2, extractedJson);
            ps.setString(3, docTags == null ? "" : docTags);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
    }

    /** Discards a queue item entirely (Reject in Pending Review, Discard in Error) - deletes the row and its file. */
    public static void deleteQueueItem(String id) {
        String selectSql = "select queue_filepath from doc_scan_queue where queue_id = ?;";
        String deleteSql = "delete from doc_scan_queue where queue_id = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            String filepath = null;
            try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                ps.setString(1, id);
                try (ResultSet res = ps.executeQuery()) {
                    if (res.next()) {
                        filepath = res.getString("queue_filepath");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(deleteSql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            if (filepath != null && !filepath.isBlank()) {
                Files.deleteIfExists(Path.of(filepath));
            }
        } catch (SQLException | IOException e) {
            MainFrame.bslog(e);
        }
    }

    public static List<QueueItem> listQueueItems(String state) {
        List<QueueItem> items = new ArrayList<>();
        String sql = "select queue_id, queue_filename, queue_filepath, queue_ext, queue_state, queue_doctype, "
                + "queue_extracted_json, queue_doctags, queue_error, queue_userid, queue_created, queue_updated "
                + "from doc_scan_queue where queue_state = ? order by queue_created asc;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, state);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    items.add(new QueueItem(res.getString("queue_id"), res.getString("queue_filename"),
                            res.getString("queue_filepath"), res.getString("queue_ext"), res.getString("queue_state"),
                            res.getString("queue_doctype"), res.getString("queue_extracted_json"),
                            res.getString("queue_doctags"), res.getString("queue_error"), res.getString("queue_userid"),
                            res.getTimestamp("queue_created"), res.getTimestamp("queue_updated")));
                }
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
        return items;
    }

    public static int countQueueItems(String state) {
        String sql = "select count(*) as cnt from doc_scan_queue where queue_state = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, state);
            try (ResultSet res = ps.executeQuery()) {
                return res.next() ? res.getInt("cnt") : 0;
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return 0;
        }
    }
}
