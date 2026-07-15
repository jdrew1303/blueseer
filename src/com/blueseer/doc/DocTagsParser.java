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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "DocTags" output of a document-layout model (e.g. IBM's
 * granite-docling-258M): a tagged transcription where each block looks
 * like {@code <text><loc_50><loc_20><loc_450><loc_60>Some text</text>} -
 * a tag name, four location numbers on a normalized 0-500 grid (regardless
 * of the source image's actual pixel size), then the block's own text
 * content up to the next tag. Same regex approach as the reference
 * WebGPU/Transformers.js sample this was modeled on - the four loc numbers
 * are the only structured part of the format that's actually documented;
 * everything else is free text between tags.
 */
public final class DocTagsParser {

    private static final Pattern TAG_PATTERN =
            Pattern.compile("<(\\w+)><loc_(\\d+)><loc_(\\d+)><loc_(\\d+)><loc_(\\d+)>");

    private DocTagsParser() {
    }

    /**
     * One tagged block. left/top/right/bottom are on the model's normalized
     * 0-500 grid, not source-image pixels - dividing by 500 and multiplying
     * by the actual image width/height (as the reference sample does) is
     * left to whoever draws the highlight, since this class has no idea
     * what image it was run against.
     */
    public record Block(String tagType, int left, int top, int right, int bottom, String text) {
    }

    public static List<Block> parse(String docTagsText) {
        List<Block> blocks = new ArrayList<>();
        if (docTagsText == null || docTagsText.isBlank()) {
            return blocks;
        }
        Matcher matcher = TAG_PATTERN.matcher(docTagsText);
        List<int[]> locs = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        List<Integer> contentStarts = new ArrayList<>();
        while (matcher.find()) {
            tags.add(matcher.group(1));
            locs.add(new int[]{Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5))});
            contentStarts.add(matcher.end());
        }
        for (int i = 0; i < tags.size(); i++) {
            int contentEnd = i + 1 < tags.size() ? nextTagStart(docTagsText, contentStarts.get(i)) : docTagsText.length();
            String content = docTagsText.substring(contentStarts.get(i), contentEnd);
            String closing = "</" + tags.get(i) + ">";
            if (content.endsWith(closing)) {
                content = content.substring(0, content.length() - closing.length());
            }
            int[] loc = locs.get(i);
            blocks.add(new Block(tags.get(i), loc[0], loc[1], loc[2], loc[3], content.trim()));
        }
        return blocks;
    }

    private static int nextTagStart(String text, int fromIndex) {
        Matcher next = TAG_PATTERN.matcher(text);
        return next.find(fromIndex) ? next.start() : text.length();
    }

    /**
     * Joins every block's own text into plain, tag-free text suitable for
     * feeding to a general-purpose (non-layout) LLM in the classify/extract
     * pass - the tag syntax itself is specific to layout-model output and
     * would just be unfamiliar noise to a model being asked for JSON.
     */
    public static String toPlainText(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (block.text().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(block.text());
        }
        return sb.toString();
    }
}
