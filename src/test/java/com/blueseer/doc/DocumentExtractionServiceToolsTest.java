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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the full agentic tool-calling loop (not just plain prompt/response)
 * works end-to-end: the fake server first replies with an OpenAI-style
 * tool_calls response, Koog's default "single run" agent strategy is
 * expected to execute the requested ERPTools method itself and send a
 * follow-up request containing the tool's result, and only then does the
 * fake server return the final answer. If Koog's tool-call wiring didn't
 * actually work, this test would either hang, error, or never reach the
 * second fake-server response.
 */
public class DocumentExtractionServiceToolsTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private volatile String capturedSecondRequestBody;

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

    private static final String TOOL_CALL_RESPONSE = "{"
            + "\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fake\","
            + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null,"
            + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":"
            + "{\"name\":\"searchItemsByDescription\",\"arguments\":\"{\\\"description\\\":\\\"Chocolate Sponge Cake\\\"}\"}}]},"
            + "\"finish_reason\":\"tool_calls\"}]"
            + "}";

    private static final String FINAL_RESPONSE = "{"
            + "\"id\":\"chatcmpl-2\",\"object\":\"chat.completion\",\"created\":2,\"model\":\"fake\","
            + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Resolved to CAKE001.\"},"
            + "\"finish_reason\":\"stop\"}]"
            + "}";

    @Test
    void agentExecutesToolCallAndReturnsFinalAnswer() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            int n = callCount.incrementAndGet();
            String body;
            if (n == 1) {
                body = TOOL_CALL_RESPONSE;
            } else {
                byte[] reqBytes = exchange.getRequestBody().readAllBytes();
                capturedSecondRequestBody = new String(reqBytes, StandardCharsets.UTF_8);
                body = FINAL_RESPONSE;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        docData.llm_config cfg = new docData.llm_config("LMSTUDIO", "http://127.0.0.1:" + port, "fake-model", true);

        String result = DocumentExtractionService.resolveWithTools(
                "You may call tools to resolve ambiguous item descriptions.",
                "What item is 'Chocolate Sponge Cake'?", cfg);

        assertEquals("Resolved to CAKE001.", result);
        assertEquals(2, callCount.get(), "expected the agent to call the fake server twice: once to get the tool_calls response, once with the tool result fed back");
        assertTrue(capturedSecondRequestBody != null && capturedSecondRequestBody.contains("call_1"),
                "expected the follow-up request to reference the original tool_call_id");
        assertTrue(capturedSecondRequestBody.contains("\"tool\""),
                "expected the follow-up request to include a tool-role message with the executed tool's result");
    }
}
