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

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Wraps existing BlueSeer data-access lookups as Koog tools an extraction
 * agent can call itself to resolve ambiguous data (a supplier name, a raw
 * item description) against the real database, rather than BlueSeer
 * matching everything after the fact with hand-rolled SQL. Deliberately
 * thin - each method delegates straight to an existing, unmodified lookup
 * in {@link docData} (which in turn only ever reads venData/invData's own
 * tables); no business logic lives here, and nothing in venData/invData/
 * purData is touched, so this stays purely additive to core BlueSeer.
 *
 * Real (not `description = "..."` on `@Tool`, which isn't a real
 * parameter on Koog's actual annotation) - description lives on the
 * separate `@LLMDescription` annotation instead, confirmed against the
 * real 1.0.0 jar.
 */
public class ERPTools implements ToolSet {

    @Tool
    @LLMDescription("Searches for the internal BlueSeer vendor code given a supplier name read off a document. Returns the vendor code, or an empty string if no single vendor matches confidently.")
    public String findVendorIdByName(
            @LLMDescription("The supplier/vendor name as printed on the document") String supplierName) {
        return docData.findVendorByName(supplierName);
    }

    @Tool
    @LLMDescription("Searches BlueSeer's item catalog for items whose description resembles the given text. Returns up to 5 candidates as \"ITEMCODE - description\" lines (one per line), or an empty string if nothing matches.")
    public String searchItemsByDescription(
            @LLMDescription("A raw item description as printed on the document (e.g. an invoice line item)") String description) {
        return docData.searchItemsByDescription(description);
    }
}
