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
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Set;

/**
 * Left-hand side of the Scan to Import split-screen review (epic "core UI
 * bits" - IcePDF + JSplitPane): shows the actual source document next to the
 * fields extracted from it, so the user can check the two against each other
 * before confirming. Renders a real PDF with IcePDF (re-added to the project
 * for exactly this, see pom.xml); a plain photographed image (the more
 * common case for a small bakery's receiving desk) is just displayed
 * directly - no PDF library involved for that path.
 */
public class DocumentPreviewPanel extends JPanel {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private SwingController pdfController;

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
            JLabel imageLabel = new JLabel(new ImageIcon(scaleToFit(image, 700)));
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            add(new JScrollPane(imageLabel), BorderLayout.CENTER);
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

    public void clear() {
        pdfController = null;
        showPlaceholder();
    }
}
