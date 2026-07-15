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

import bsmf.MainFrame;
import org.icepdf.ri.common.ComponentKeyBinding;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.MyAnnotationCallback;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

/**
 * Left-hand side of the Scan to Import split-screen review (epic "core UI
 * bits" - IcePDF + JSplitPane): shows the actual source document next to the
 * fields extracted from it, so the user can check the two against each other
 * before confirming. Renders a real PDF with IcePDF (re-added to the project
 * for exactly this, see pom.xml); a plain photographed image (the more
 * common case for a small bakery's receiving desk) is just displayed
 * directly - no PDF library involved for that path.
 *
 * When the optional document-layout model is configured (see
 * docData.layout_llm_config), {@link #highlightBlocks} draws a box around
 * whichever DocTags-tagged regions of the source image correlate to the
 * extracted fields - image documents only; the PDF path uses IcePDF's own
 * viewer component, which isn't straightforward to draw an accurately
 * positioned overlay on top of given its own independent zoom/scroll state.
 */
public class DocumentPreviewPanel extends JPanel {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private SwingController pdfController;
    private ImageCanvas imageCanvas;

    public DocumentPreviewPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Source Document"));
        showPlaceholder();
    }

    private void showPlaceholder() {
        removeAll();
        JLabel placeholder = new JLabel("No document loaded", SwingConstants.CENTER);
        add(placeholder, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void showDocument(byte[] bytes, String ext) {
        removeAll();
        imageCanvas = null;
        if (IMAGE_EXTENSIONS.contains(ext.toLowerCase())) {
            showImage(bytes);
        } else {
            showPdf(bytes);
        }
        revalidate();
        repaint();
    }

    private void showImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                add(new JLabel("Couldn't display this image.", SwingConstants.CENTER), BorderLayout.CENTER);
                return;
            }
            imageCanvas = new ImageCanvas(scaleToFit(image, 700));
            add(new JScrollPane(imageCanvas), BorderLayout.CENTER);
        } catch (Exception ex) {
            MainFrame.bslog(ex);
            add(new JLabel("Couldn't display this image.", SwingConstants.CENTER), BorderLayout.CENTER);
        }
    }

    private static Image scaleToFit(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth) {
            return image;
        }
        int height = (int) ((double) image.getHeight() / image.getWidth() * maxWidth);
        return image.getScaledInstance(maxWidth, height, Image.SCALE_SMOOTH);
    }

    private void showPdf(byte[] bytes) {
        try {
            if (pdfController != null) {
                pdfController.dispose();
            }
            pdfController = new SwingController();
            SwingViewBuilder factory = new SwingViewBuilder(pdfController);
            JPanel viewerPanel = factory.buildViewerPanel();
            ComponentKeyBinding.install(pdfController, viewerPanel);
            pdfController.getDocumentViewController().setAnnotationCallback(
                    new MyAnnotationCallback(pdfController.getDocumentViewController()));
            add(viewerPanel, BorderLayout.CENTER);
            pdfController.openDocument(bytes, 0, bytes.length, "Scanned document", "scan.pdf");
        } catch (Exception ex) {
            MainFrame.bslog(ex);
            add(new JLabel("Couldn't display this PDF.", SwingConstants.CENTER), BorderLayout.CENTER);
        }
    }

    /**
     * Draws a box around each given block's location on the currently
     * displayed image - a no-op if nothing's loaded or the source wasn't an
     * image (the PDF path, see class javadoc). Blocks carry normalized
     * 0-500-grid coordinates (see DocTagsParser), which map to a fraction
     * of the displayed image regardless of how much it's been scaled down.
     */
    public void highlightBlocks(List<DocTagsParser.Block> blocks) {
        if (imageCanvas != null) {
            imageCanvas.setHighlights(blocks);
        }
    }

    public void clear() {
        pdfController = null;
        imageCanvas = null;
        showPlaceholder();
    }

    private static class ImageCanvas extends JPanel {

        private static final Color HIGHLIGHT_COLOR = new Color(0xE6, 0x8A, 0x00);

        private final Image image;
        private List<DocTagsParser.Block> highlights = List.of();

        ImageCanvas(Image image) {
            this.image = image;
            setPreferredSize(new Dimension(image.getWidth(null), image.getHeight(null)));
        }

        void setHighlights(List<DocTagsParser.Block> highlights) {
            this.highlights = highlights;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, this);
            if (highlights.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(HIGHLIGHT_COLOR);
            g2.setStroke(new BasicStroke(2f));
            int imgWidth = image.getWidth(null);
            int imgHeight = image.getHeight(null);
            for (DocTagsParser.Block block : highlights) {
                int x = (int) (block.left() / 500.0 * imgWidth);
                int y = (int) (block.top() / 500.0 * imgHeight);
                int w = (int) ((block.right() - block.left()) / 500.0 * imgWidth);
                int h = (int) ((block.bottom() - block.top()) / 500.0 * imgHeight);
                g2.drawRect(x, y, w, h);
            }
            g2.dispose();
        }
    }
}
