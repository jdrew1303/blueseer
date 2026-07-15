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

import java.util.List;

/**
 * Structured shape for a supplier invoice/packing-slip extraction (epic
 * DOC-6). Jackson deserializes the LLM's JSON reply straight into this
 * record - no Koog-side structured-output API involved, see
 * DocumentExtractionService's class javadoc for why.
 */
public record InvoiceExtraction(String supplier, String invoiceNumber, String date, List<Line> lines) {

    public record Line(String description, double quantity, String unit, double unitCost, String confidence) {
    }

    public static final String SYSTEM_INSTRUCTIONS =
            "You are reading a supplier invoice or packing slip photographed by a small bakery's "
            + "receiving staff. Extract the supplier name, invoice/packing-slip number, date, and every "
            + "line item (raw item description exactly as printed, quantity, unit of measure, unit cost). "
            + "If a field is illegible or not present, use an empty string (or 0 for numbers) and set that "
            + "line's confidence to \"low\". Never guess a quantity or price you can't actually read.";

    public static final String JSON_SHAPE =
            "{\"supplier\": string, \"invoiceNumber\": string, \"date\": string (YYYY-MM-DD), "
            + "\"lines\": [{\"description\": string, \"quantity\": number, \"unit\": string, "
            + "\"unitCost\": number, \"confidence\": \"high\"|\"low\"}]}";
}
