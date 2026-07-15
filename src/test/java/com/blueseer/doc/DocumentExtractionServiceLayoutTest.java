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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the optional two-pass "layout model" pipeline
 * (convertToDocTags, then extractStructuredFromText against its plain-text
 * output) end-to-end against a fake local server, the same technique used
 * throughout DocumentExtractionServiceTest for the single-pass path.
 */
public class DocumentExtractionServiceLayoutTest {

    private HttpServer server;
    private int port;

    public record TestExtraction(String supplier, double total) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String chatCompletionResponse(String assistantContent) {
        String escaped = assistantContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test-model\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},"
                + "\"finish_reason\":\"stop\"}]}";
    }

    @Test
    void convertToDocTagsReturnsRawModelText() throws Exception {
        String docTags = "<text><loc_10><loc_10><loc_300><loc_40>Acme Bakery Supplies</text>";
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = chatCompletionResponse(docTags).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        docData.layout_llm_config cfg = new docData.layout_llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "docling-model");
        String result = DocumentExtractionService.convertToDocTags(new byte[]{1, 2, 3}, "jpeg", cfg);

        assertEquals(docTags, result);
    }

    @Test
    void convertToDocTagsThrowsWhenNotConfigured() {
        docData.layout_llm_config unconfigured = new docData.layout_llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "");

        assertThrows(DocumentExtractionService.DocumentExtractionException.class,
                () -> DocumentExtractionService.convertToDocTags(new byte[]{1, 2, 3}, "jpeg", unconfigured));
    }

    @Test
    void extractStructuredFromTextSendsNoImageAttachment() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = chatCompletionResponse("{\"supplier\":\"Acme Bakery Supplies\",\"total\":25.0}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        docData.llm_config cfg = new docData.llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "test-model", true);
        TestExtraction result = DocumentExtractionService.extractStructuredFromText(
                "Acme Bakery Supplies\nTotal: 25.00", "You are an invoice reader.",
                "{\"supplier\": string, \"total\": number}", TestExtraction.class, cfg);

        assertEquals("Acme Bakery Supplies", result.supplier());
        assertTrue(capturedBody.get().contains("Acme Bakery Supplies"), "expected the DocTags plain text to be in the request");
        assertTrue(!capturedBody.get().contains("\"image\""), "expected no image attachment on the text-only path");
    }

    @Test
    void fullTwoPassPipelineFlowsDocTagsIntoExtraction() throws Exception {
        String docTags = "<text><loc_10><loc_10><loc_300><loc_40>Acme Bakery Supplies</text>"
                + "<text><loc_10><loc_50><loc_300><loc_80>Total: 25.00</text>";
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            int n = callCount.incrementAndGet();
            String body = n == 1 ? chatCompletionResponse(docTags)
                    : chatCompletionResponse("{\"supplier\":\"Acme Bakery Supplies\",\"total\":25.0}");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        docData.layout_llm_config layoutCfg = new docData.layout_llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "docling-model");
        docData.llm_config extractionCfg = new docData.llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "test-model", true);

        String rawDocTags = DocumentExtractionService.convertToDocTags(new byte[]{1, 2, 3}, "jpeg", layoutCfg);
        List<DocTagsParser.Block> blocks = DocTagsParser.parse(rawDocTags);
        String plainText = DocTagsParser.toPlainText(blocks);
        TestExtraction result = DocumentExtractionService.extractStructuredFromText(
                plainText, "You are an invoice reader.", "{\"supplier\": string, \"total\": number}",
                TestExtraction.class, extractionCfg);

        assertEquals(2, callCount.get());
        assertEquals(2, blocks.size());
        assertEquals("Acme Bakery Supplies", result.supplier());
        assertEquals(25.0, result.total());
    }
}
