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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocTagsParserTest {

    @Test
    void parsesConsecutiveTaggedBlocksWithClosingTags() {
        String docTags = "<text><loc_10><loc_20><loc_300><loc_45>Acme Bakery Supplies</text>"
                + "<text><loc_10><loc_60><loc_300><loc_85>Invoice #INV-1001</text>";

        List<DocTagsParser.Block> blocks = DocTagsParser.parse(docTags);

        assertEquals(2, blocks.size());
        assertEquals("text", blocks.get(0).tagType());
        assertEquals(10, blocks.get(0).left());
        assertEquals(20, blocks.get(0).top());
        assertEquals(300, blocks.get(0).right());
        assertEquals(45, blocks.get(0).bottom());
        assertEquals("Acme Bakery Supplies", blocks.get(0).text());
        assertEquals("Invoice #INV-1001", blocks.get(1).text());
    }

    @Test
    void handlesBlocksWithNoClosingTag() {
        // Some block types (e.g. table cells) aren't wrapped in a closing tag -
        // content just runs until the next <tag><loc_.../> sequence.
        String docTags = "<otsl><loc_0><loc_0><loc_100><loc_20>Qty"
                + "<otsl><loc_100><loc_0><loc_200><loc_20>Description";

        List<DocTagsParser.Block> blocks = DocTagsParser.parse(docTags);

        assertEquals(2, blocks.size());
        assertEquals("Qty", blocks.get(0).text());
        assertEquals("Description", blocks.get(1).text());
    }

    @Test
    void returnsEmptyListForUnparseableInput() {
        assertTrue(DocTagsParser.parse("not a doctags response at all").isEmpty());
        assertTrue(DocTagsParser.parse(null).isEmpty());
        assertTrue(DocTagsParser.parse("").isEmpty());
    }

    @Test
    void joinsBlockTextSkippingBlanks() {
        String docTags = "<text><loc_0><loc_0><loc_10><loc_10>First</text>"
                + "<text><loc_0><loc_10><loc_10><loc_20></text>"
                + "<text><loc_0><loc_20><loc_10><loc_30>Second</text>";

        List<DocTagsParser.Block> blocks = DocTagsParser.parse(docTags);
        String plainText = DocTagsParser.toPlainText(blocks);

        assertEquals("First\nSecond", plainText);
    }
}
