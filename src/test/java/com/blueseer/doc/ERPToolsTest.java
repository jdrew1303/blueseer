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

import ai.koog.agents.core.tools.reflect.ToolFromCallable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves Koog's reflection-based ToolSet mechanism actually produces real
 * tool descriptors from a plain annotated Java class - not just that the
 * annotations compile, but that Koog's own machinery can see and describe
 * them (name + description sourced from @LLMDescription).
 */
public class ERPToolsTest {

    @Test
    void exposesBothToolsWithRealDescriptions() {
        List<ToolFromCallable<?>> tools = new ERPTools().asTools();
        assertEquals(2, tools.size());

        List<String> names = tools.stream().map(t -> t.getDescriptor().getName()).toList();
        assertTrue(names.contains("findVendorIdByName"));
        assertTrue(names.contains("searchItemsByDescription"));

        String vendorDesc = tools.stream()
                .filter(t -> t.getDescriptor().getName().equals("findVendorIdByName"))
                .findFirst().orElseThrow().getDescriptor().getDescription();
        assertTrue(vendorDesc.contains("vendor code"));
    }
}
