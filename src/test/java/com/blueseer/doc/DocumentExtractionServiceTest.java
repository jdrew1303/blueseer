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
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the real Koog wiring end-to-end against a fake local server that
 * speaks the OpenAI-compatible chat-completions protocol - standing in for
 * LM Studio/Ollama, since no real local LLM runtime is available in a CI/
 * test environment. This is what proves DocumentExtractionService's Koog
 * integration actually works (request goes out, response comes back, JSON
 * gets parsed), not just that it compiles.
 */
public class DocumentExtractionServiceTest {

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
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1700000000,"
                + "\"model\":\"test-model\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}"
                + "}";
    }

    private void serveOnce(String responseBody) {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    private docData.llm_config fakeConfig() {
        return new docData.llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "test-model", true);
    }

    @Test
    void extractsValidJsonOnFirstReply() throws Exception {
        serveOnce(chatCompletionResponse("{\"supplier\":\"Acme Flour Co\",\"total\":42.5}"));

        TestExtraction result = DocumentExtractionService.extractStructured(
                new byte[]{1, 2, 3}, "jpeg", "You are an invoice reader.",
                "{\"supplier\": string, \"total\": number}", TestExtraction.class, fakeConfig());

        assertEquals("Acme Flour Co", result.supplier());
        assertEquals(42.5, result.total());
    }

    @Test
    void stripsMarkdownCodeFenceBeforeParsing() throws Exception {
        serveOnce(chatCompletionResponse("```json\n{\"supplier\":\"Acme Flour Co\",\"total\":42.5}\n```"));

        TestExtraction result = DocumentExtractionService.extractStructured(
                new byte[]{1, 2, 3}, "jpeg", "You are an invoice reader.",
                "{\"supplier\": string, \"total\": number}", TestExtraction.class, fakeConfig());

        assertEquals("Acme Flour Co", result.supplier());
    }

    @Test
    void retriesOnceOnMalformedJsonThenSucceeds() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = callCount.getAndIncrement() == 0
                    ? chatCompletionResponse("this is not json at all")
                    : chatCompletionResponse("{\"supplier\":\"Acme Flour Co\",\"total\":42.5}");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        TestExtraction result = DocumentExtractionService.extractStructured(
                new byte[]{1, 2, 3}, "jpeg", "You are an invoice reader.",
                "{\"supplier\": string, \"total\": number}", TestExtraction.class, fakeConfig());

        assertEquals("Acme Flour Co", result.supplier());
        assertEquals(2, callCount.get());
    }

    @Test
    void throwsPlainLanguageErrorWhenServerUnreachable() {
        server.stop(0);
        server = null;
        docData.llm_config unreachable = new docData.llm_config("LMSTUDIO", "http://127.0.0.1:1", "test-model", true);

        DocumentExtractionService.DocumentExtractionException ex = assertThrows(
                DocumentExtractionService.DocumentExtractionException.class,
                () -> DocumentExtractionService.extractStructured(
                        new byte[]{1, 2, 3}, "jpeg", "You are an invoice reader.",
                        "{\"supplier\": string}", TestExtraction.class, unreachable));

        assertEquals("Couldn't reach the local AI service - check it's running.", ex.getMessage());
    }

    @Test
    void throwsPlainLanguageErrorWhenDisabled() {
        docData.llm_config disabled = new docData.llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "test-model", false);

        assertThrows(DocumentExtractionService.DocumentExtractionException.class,
                () -> DocumentExtractionService.extractStructured(
                        new byte[]{1, 2, 3}, "jpeg", "You are an invoice reader.",
                        "{\"supplier\": string}", TestExtraction.class, disabled));
    }
}
