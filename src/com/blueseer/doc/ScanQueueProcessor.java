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
import com.blueseer.doc.schema.DocumentClassification;
import com.blueseer.doc.schema.InvoiceExtraction;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background worker for the multi-document Scan to Import queue (epic
 * follow-up: Inbox/Pending Review/Outbox/Sent/Error, matching the layout
 * the client mocked up). Lets more than one document be dropped in at once
 * - each just sits in the Inbox until this picks it up - rather than one
 * document tying up the whole screen synchronously the way the original
 * single-document flow did.
 *
 * A single daemon thread, not a thread pool: local LLM runtimes (LM
 * Studio/Ollama) are typically single-model, single-request-at-a-time
 * servers on commodity hardware, so processing the queue one document at a
 * time is the realistic throughput anyway, and it keeps this dead simple -
 * no concurrent-access coordination needed around the one DB row being
 * updated at a time.
 */
public final class ScanQueueProcessor {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final long POLL_INTERVAL_MS = 2000;

    private ScanQueueProcessor() {
    }

    /**
     * Idempotent - safe to call every time the Scan to Import screen is
     * constructed (BlueSeer's panel cache means that's normally just once
     * per app session, but this guards it regardless); only the first call
     * actually starts the thread.
     */
    public static void ensureStarted() {
        if (STARTED.compareAndSet(false, true)) {
            Thread worker = new Thread(ScanQueueProcessor::runLoop, "scan-queue-processor");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static void runLoop() {
        while (true) {
            try {
                processOldestInboxItem();
            } catch (Exception ex) {
                MainFrame.bslog(ex);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void processOldestInboxItem() {
        List<docData.QueueItem> inbox = docData.listQueueItems(docData.QUEUE_INBOX);
        if (inbox.isEmpty()) {
            return;
        }
        docData.QueueItem item = inbox.get(0);
        byte[] bytes = docData.readQueueFileBytes(item);
        if (bytes == null) {
            docData.markQueueError(item.id(), "Couldn't read the saved file.");
            return;
        }
        try {
            ExtractionResult result = runExtraction(bytes, item.ext(), false);
            String docType = result.classification() != null
                    ? result.classification().documentType() : DocumentClassification.OTHER;
            Object toSave = result.invoice() != null ? result.invoice() : result.classification();
            String json = DocumentExtractionService.toJson(toSave);
            // Blocks (not just their plain text) are what re-derive highlight
            // boxes when the item is reopened later, without calling the
            // layout model again - so the blocks themselves are what's saved.
            String blocksJson = result.blocks().isEmpty() ? "" : DocumentExtractionService.toJson(result.blocks());
            docData.markQueueProcessed(item.id(), docType, json, blocksJson);
        } catch (DocumentExtractionService.DocumentExtractionException ex) {
            docData.markQueueError(item.id(), ex.getMessage());
        }
    }

    /**
     * Result of one classify(+extract) run - blocks is only ever non-empty
     * when the optional document-layout model produced DocTags for this
     * document (see docData.layout_llm_config); empty otherwise (the
     * ordinary single-pass, image-straight-to-JSON path).
     */
    public record ExtractionResult(DocumentClassification classification, InvoiceExtraction invoice,
            List<DocTagsParser.Block> blocks) {
    }

    /**
     * Runs classify+extract (or, with forceInvoice, extraction alone,
     * skipping classification entirely - the manual "Use This Type"
     * override path in the review screen). When a document-layout model is
     * configured, this is genuinely two model calls per step: image ->
     * DocTags (the layout model), then DocTags plain text -> JSON (the
     * regular extraction model, text-only) - a layout model like
     * granite-docling-258M can't classify/extract JSON itself, see
     * DocumentExtractionService's class javadoc. Falls back to sending the
     * image straight to the extraction model, exactly as before, whenever
     * no layout model is configured. Shared between this background
     * processor and ScanToImportPanel's own re-extraction (e.g. the
     * document-type override), so both go through the exact same pipeline.
     */
    public static ExtractionResult runExtraction(byte[] imageBytes, String ext, boolean forceInvoice)
            throws DocumentExtractionService.DocumentExtractionException {
        docData.layout_llm_config layoutCfg = docData.getLayoutLlmConfig();
        List<DocTagsParser.Block> blocks = List.of();
        String plainText = null;
        if (layoutCfg.configured()) {
            String rawDocTags = DocumentExtractionService.convertToDocTags(imageBytes, ext, layoutCfg);
            blocks = DocTagsParser.parse(rawDocTags);
            plainText = DocTagsParser.toPlainText(blocks);
        }

        DocumentClassification classification = null;
        if (!forceInvoice) {
            classification = plainText != null
                    ? DocumentExtractionService.extractStructuredFromText(plainText, DocumentClassification.SYSTEM_INSTRUCTIONS,
                            DocumentClassification.JSON_SHAPE, DocumentClassification.class)
                    : DocumentExtractionService.extractStructured(imageBytes, ext, DocumentClassification.SYSTEM_INSTRUCTIONS,
                            DocumentClassification.JSON_SHAPE, DocumentClassification.class);
        }

        InvoiceExtraction invoice = null;
        if (forceInvoice || (classification != null && DocumentClassification.INVOICE.equals(classification.documentType()))) {
            invoice = plainText != null
                    ? DocumentExtractionService.extractStructuredFromText(plainText, InvoiceExtraction.SYSTEM_INSTRUCTIONS,
                            InvoiceExtraction.JSON_SHAPE, InvoiceExtraction.class)
                    : DocumentExtractionService.extractStructured(imageBytes, ext, InvoiceExtraction.SYSTEM_INSTRUCTIONS,
                            InvoiceExtraction.JSON_SHAPE, InvoiceExtraction.class);
        }

        return new ExtractionResult(classification, invoice, blocks);
    }
}
