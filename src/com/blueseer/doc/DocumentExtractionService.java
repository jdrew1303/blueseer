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

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolBase;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.Prompt;
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig;
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.AttachmentContent;
import ai.koog.prompt.message.AttachmentSource;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.MessagePart;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps JetBrains' Koog framework to send a document photo/scan to the
 * locally-configured LLM (LM Studio or Ollama, both OpenAI-compatible) and
 * parse the reply as JSON into a plain Java record/POJO.
 *
 * Koog's own {@code executeStructured} is a Kotlin-only suspend/reified-
 * generic function with no Java-callable overload (confirmed via javap
 * against the real 1.0.0 jars - see docs/epics/agentic-document-import.md).
 * The only Java-friendly entry point for actually running a prompt is the
 * blocking {@code PromptExecutor.execute(Prompt, LLModel)}, which returns
 * plain text. So structured extraction is done at this layer instead: the
 * prompt asks for a strict JSON reply, Jackson parses it, and one corrective
 * follow-up is sent if parsing fails - the same idea as Koog's fixingParser,
 * just implemented here rather than called through their API.
 */
public class DocumentExtractionService {

    public static class DocumentExtractionException extends Exception {

        public DocumentExtractionException(String message) {
            super(message);
        }

        public DocumentExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PromptExecutor buildExecutor(docData.llm_config cfg) {
        return buildExecutor(cfg.provider(), cfg.baseurl());
    }

    private static PromptExecutor buildExecutor(docData.layout_llm_config cfg) {
        return buildExecutor(cfg.provider(), cfg.baseurl());
    }

    private static PromptExecutor buildExecutor(String provider, String baseurl) {
        if ("OLLAMA".equalsIgnoreCase(provider)) {
            return PromptExecutor.builder().ollama(baseurl).build();
        }
        OpenAIClientSettings settings = new OpenAIClientSettings(
                baseurl, new ConnectionTimeoutConfig(), "v1/chat/completions", "", "", "", "");
        return PromptExecutor.builder().openAI("not-needed", settings).build();
    }

    private static LLModel buildModel(docData.llm_config cfg) {
        return buildModel(cfg.model());
    }

    private static LLModel buildModel(docData.layout_llm_config cfg) {
        return buildModel(cfg.model());
    }

    private static LLModel buildModel(String modelName) {
        // OpenAIEndpoint.Completions is required, not optional decoration:
        // OpenAILLMClient.determineParams picks its request-param strategy by
        // checking model.supports(OpenAIEndpoint.Completions/.Responses) and
        // throws LLMClientException("Cannot determine proper LLM params...")
        // if neither capability is declared (confirmed by decompiling the
        // real 1.0.0 jar - the model *id* string isn't what's being checked).
        // LLMCapability.Tools is declared unconditionally too (harmless when
        // unused) since AbstractOpenAILLMClient.getResponse requires it on
        // the model whenever a non-empty tool list is passed to execute().
        return new LLModel(LLMProvider.OpenAI, modelName,
                List.of(LLMCapability.Vision.Image.INSTANCE, LLMCapability.OpenAIEndpoint.Completions.INSTANCE,
                        LLMCapability.Completion.INSTANCE, LLMCapability.Tools.INSTANCE));
    }

    /**
     * Resolves a single ambiguous piece of extracted text (a supplier name, a
     * raw item description) by giving an agent real tool access to BlueSeer's
     * own lookups (com.blueseer.doc.ERPTools), rather than BlueSeer matching
     * it after the fact with hand-rolled SQL. Uses Koog's default "single
     * run" agent strategy (no explicit graph/planner strategy set), which
     * handles the whole ask-model / call-tool / feed-result-back loop
     * internally - AIAgent.run(Input) is a plain blocking Java method
     * regardless of how many tool calls happen inside it.
     */
    public static String resolveWithTools(String systemPrompt, String query) throws DocumentExtractionException {
        return resolveWithTools(systemPrompt, query, docData.getLlmConfig());
    }

    /**
     * Same as {@link #resolveWithTools(String, String)} but takes the
     * runtime config explicitly - lets this be exercised in a unit test
     * against a fake local server, same pattern as
     * {@link #extractStructured(byte[], String, String, String, Class, docData.llm_config)}.
     */
    public static String resolveWithTools(String systemPrompt, String query, docData.llm_config cfg) throws DocumentExtractionException {
        if (!cfg.enabled()) {
            throw new DocumentExtractionException("Document import isn't turned on for this system - see System Control.");
        }
        try {
            PromptExecutor executor = buildExecutor(cfg);
            LLModel model = buildModel(cfg);
            ToolRegistry registry = new ToolRegistry();
            registry.addAll(new ERPTools().asTools().toArray(new ToolBase<?, ?>[0]));
            AIAgent<String, String> agent = AIAgent.builder()
                    .promptExecutor(executor)
                    .llmModel(model)
                    .toolRegistry(registry)
                    .systemPrompt(systemPrompt)
                    .build();
            return agent.run(query);
        } catch (Exception e) {
            throw new DocumentExtractionException("Couldn't reach the local AI service - check it's running.", e);
        }
    }

    /**
     * Fixed prompt a document-layout model like granite-docling-258M
     * expects - unlike the classify/extract calls elsewhere in this class,
     * a layout model isn't a general instruction-follower, so this isn't
     * built from caller-supplied system instructions; it only ever does
     * this one conversion task. Matches the prompt used by IBM's own
     * reference WebGPU/Transformers.js sample this was modeled on.
     */
    private static final String DOCLING_PROMPT = "Convert this page to docling.";

    /**
     * Runs the document-layout model's one job: convert a page image into
     * raw "DocTags" text (see {@link DocTagsParser}), so the classify/
     * extract pass can run against that text instead of the image, and
     * extracted fields can later be correlated back to a source-document
     * bounding box for highlighting. No JSON parsing or retry here - unlike
     * extractStructured, there's no "wrong shape" to correct a layout model
     * back onto; whatever it returns is handed to DocTagsParser as-is,
     * which degrades gracefully (zero blocks) on unparseable output rather
     * than throwing.
     */
    public static String convertToDocTags(byte[] imageBytes, String imageFormat, docData.layout_llm_config cfg)
            throws DocumentExtractionException {
        if (!cfg.configured()) {
            throw new DocumentExtractionException("No document layout model is configured - see Scan to Import Settings.");
        }
        try {
            PromptExecutor executor = buildExecutor(cfg);
            LLModel model = buildModel(cfg);
            Message.User userMessage = buildUserMessage(DOCLING_PROMPT, imageBytes, imageFormat);
            Prompt prompt = new Prompt(List.of(userMessage), "doc-import-layout");
            Message.Assistant response = executor.execute(prompt, model);
            return response.textContent();
        } catch (Exception e) {
            throw new DocumentExtractionException("Couldn't reach the local document layout model - check it's running.", e);
        }
    }

    /**
     * Serializes an already-extracted record back to a JSON string for
     * storage on a scan-queue row (see docData.QueueItem), so a queued
     * document's result can be redisplayed without calling the LLM again.
     */
    public static <T> String toJson(T value) throws DocumentExtractionException {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new DocumentExtractionException("Couldn't save the extracted data.", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> targetType) throws DocumentExtractionException {
        try {
            return MAPPER.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            throw new DocumentExtractionException("Couldn't read the saved extracted data.", e);
        }
    }

    private static Message.User buildUserMessage(String promptText, byte[] imageBytes, String imageFormat) {
        List<MessagePart.RequestPart> parts = new ArrayList<>();
        parts.add(new MessagePart.Text(promptText));
        if (imageBytes != null && imageBytes.length > 0) {
            AttachmentContent content = new AttachmentContent.Binary.Bytes(imageBytes);
            AttachmentSource.Image image = new AttachmentSource.Image(
                    content, imageFormat, "image/" + imageFormat, "document." + imageFormat);
            parts.add(new MessagePart.Attachment(image));
        }
        return new Message.User(parts, RequestMetaInfo.Companion.getEmpty());
    }

    private static String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    /**
     * Sends the image plus extraction instructions to the configured local
     * LLM and parses the reply as JSON into targetType. Throws with a plain-
     * language message (never a raw stack trace) on any failure - unreachable
     * runtime, malformed reply even after one corrective retry, etc.
     */
    public static <T> T extractStructured(byte[] imageBytes, String imageFormat, String systemInstructions,
            String jsonShapeDescription, Class<T> targetType) throws DocumentExtractionException {
        return extractStructured(imageBytes, imageFormat, systemInstructions, jsonShapeDescription, targetType,
                docData.getLlmConfig());
    }

    /**
     * Same as {@link #extractStructured(byte[], String, String, String, Class)}
     * but takes the runtime config explicitly rather than reading it from the
     * database - lets this be exercised in a unit test against a fake local
     * HTTP server standing in for LM Studio/Ollama.
     */
    public static <T> T extractStructured(byte[] imageBytes, String imageFormat, String systemInstructions,
            String jsonShapeDescription, Class<T> targetType, docData.llm_config cfg) throws DocumentExtractionException {
        if (!cfg.enabled()) {
            throw new DocumentExtractionException("Document import isn't turned on for this system - see System Control.");
        }
        Message.User userMessage = buildUserMessage("Extract the data from this document.", imageBytes, imageFormat);
        return extractStructuredFromMessage(userMessage, systemInstructions, jsonShapeDescription, targetType, cfg);
    }

    /**
     * Same idea as {@link #extractStructured}, but the "document" is
     * already plain text rather than an image - the second pass of the
     * optional layout-model pipeline (image -> DocTags -> this), since a
     * layout model like granite-docling-258M can't classify/extract JSON
     * itself. Uses the same configured extraction model as the image path;
     * a layout model is purely an optional upstream step, not a
     * replacement for it.
     */
    public static <T> T extractStructuredFromText(String documentText, String systemInstructions,
            String jsonShapeDescription, Class<T> targetType) throws DocumentExtractionException {
        return extractStructuredFromText(documentText, systemInstructions, jsonShapeDescription, targetType,
                docData.getLlmConfig());
    }

    public static <T> T extractStructuredFromText(String documentText, String systemInstructions,
            String jsonShapeDescription, Class<T> targetType, docData.llm_config cfg) throws DocumentExtractionException {
        if (!cfg.enabled()) {
            throw new DocumentExtractionException("Document import isn't turned on for this system - see System Control.");
        }
        Message.User userMessage = buildUserMessage(
                "Extract the data from this document transcription:\n\n" + documentText, null, null);
        return extractStructuredFromMessage(userMessage, systemInstructions, jsonShapeDescription, targetType, cfg);
    }

    private static <T> T extractStructuredFromMessage(Message.User userMessage, String systemInstructions,
            String jsonShapeDescription, Class<T> targetType, docData.llm_config cfg) throws DocumentExtractionException {
        PromptExecutor executor;
        LLModel model;
        try {
            executor = buildExecutor(cfg);
            model = buildModel(cfg);
        } catch (Exception e) {
            throw new DocumentExtractionException(
                    "Couldn't set up the local AI connection - check the Document Import settings in System Control.", e);
        }

        String fullInstructions = systemInstructions
                + "\n\nRespond with ONLY a single JSON object matching this shape, no markdown fences, no commentary:\n"
                + jsonShapeDescription;
        Message.System systemMessage = new Message.System(fullInstructions, RequestMetaInfo.Companion.getEmpty());

        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);
        Prompt prompt = new Prompt(messages, "doc-import");

        String responseText;
        try {
            Message.Assistant response = executor.execute(prompt, model);
            responseText = response.textContent();
        } catch (Exception e) {
            throw new DocumentExtractionException("Couldn't reach the local AI service - check it's running.", e);
        }

        try {
            return MAPPER.readValue(stripCodeFence(responseText), targetType);
        } catch (JsonProcessingException firstFailure) {
            try {
                List<Message> retryMessages = new ArrayList<>(messages);
                retryMessages.add(new Message.Assistant(responseText, ResponseMetaInfo.Companion.getEmpty()));
                retryMessages.add(new Message.User(
                        "That wasn't valid JSON. Reply again with ONLY the corrected JSON object, matching the shape above, no other text.",
                        RequestMetaInfo.Companion.getEmpty()));
                Prompt retryPrompt = new Prompt(retryMessages, "doc-import-retry");
                Message.Assistant retryResponse = executor.execute(retryPrompt, model);
                return MAPPER.readValue(stripCodeFence(retryResponse.textContent()), targetType);
            } catch (Exception retryFailure) {
                throw new DocumentExtractionException(
                        "The AI's response couldn't be understood, even after asking it to correct itself.", retryFailure);
            }
        } catch (Exception e) {
            throw new DocumentExtractionException("Something went wrong reading the AI's response.", e);
        }
    }
}
