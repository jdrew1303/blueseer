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
package com.blueseer.doc.schema;

/**
 * First step of the central "Scan to Import" screen's pipeline: a cheap,
 * small LLM call to identify what kind of paper this is, before running the
 * (more expensive, schema-specific) full extraction and deciding which
 * existing BlueSeer screen to route into. Keeping this a separate call from
 * the real extraction means a wrong guess is caught early and cheaply,
 * rather than after a full field-by-field extraction against the wrong
 * schema.
 *
 * New document types get a new enum-ish string here, a matching schema
 * record next to this one, and a routing target in ScanToImportPanel - the
 * invoice/Receiver Maintenance path is the only one wired up so far.
 */
public record DocumentClassification(String documentType, String reasoning) {

    public static final String INVOICE = "INVOICE";
    public static final String OTHER = "OTHER";

    public static final String SYSTEM_INSTRUCTIONS =
            "Look at this photographed document and identify what kind of paper it is. "
            + "Reply with documentType set to one of: \"INVOICE\" (a supplier invoice or packing slip "
            + "listing items received with quantities/prices), or \"OTHER\" (anything else). "
            + "Briefly explain your reasoning.";

    public static final String JSON_SHAPE =
            "{\"documentType\": \"INVOICE\"|\"OTHER\", \"reasoning\": string}";
}
