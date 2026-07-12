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

/*
 * The original source for this file was lost; this was reconstructed by
 * decompiling the previously-shipped lib/bsmf.jar with CFR 0.152 and
 * verified to compile and behave identically to that jar before replacing
 * it here.
 */
package bsmf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

public class BackGroundPanel
extends JPanel {
    public static final int SCALED = 0;
    public static final int TILED = 1;
    public static final int ACTUAL = 2;
    private Paint painter;
    private Image image;
    private int style = 2;
    private float alignmentX = 0.5f;
    private float alignmentY = 0.5f;
    private boolean isTransparentAdd = true;

    public BackGroundPanel(Image image) {
        this(image, 2);
    }

    public BackGroundPanel() {
        this.setLayout(new BorderLayout());
    }

    public BackGroundPanel(Image image, int style) {
        this.setImage(image);
        this.setStyle(style);
        this.setLayout(new BorderLayout());
    }

    public BackGroundPanel(Image image, int style, float alignmentX, float alignmentY) {
        this.setImage(image);
        this.setStyle(style);
        this.setImageAlignmentX(alignmentX);
        this.setImageAlignmentY(alignmentY);
        this.setLayout(new BorderLayout());
    }

    public BackGroundPanel(Paint painter) {
        this.setPaint(painter);
        this.setLayout(new BorderLayout());
    }

    public void setImage(Image image) {
        this.image = image;
        this.repaint();
    }

    public void setStyle(int style) {
        this.style = style;
        this.repaint();
    }

    public void setPaint(Paint painter) {
        this.painter = painter;
        this.repaint();
    }

    public void setImageAlignmentX(float alignmentX) {
        this.alignmentX = alignmentX > 1.0f ? 1.0f : (alignmentX < 0.0f ? 0.0f : alignmentX);
        this.repaint();
    }

    public void setImageAlignmentY(float alignmentY) {
        this.alignmentY = alignmentY > 1.0f ? 1.0f : (alignmentY < 0.0f ? 0.0f : alignmentY);
        this.repaint();
    }

    public void add(JComponent component) {
        this.add(component, null);
    }

    @Override
    public Dimension getPreferredSize() {
        if (this.image == null) {
            return super.getPreferredSize();
        }
        return new Dimension(this.image.getWidth(null), this.image.getHeight(null));
    }

    public void add(JComponent component, Object constraints) {
        if (this.isTransparentAdd) {
            this.makeComponentTransparent(component);
        }
        super.add((Component)component, constraints);
    }

    public void setTransparentAdd(boolean isTransparentAdd) {
        this.isTransparentAdd = isTransparentAdd;
    }

    private void makeComponentTransparent(JComponent component) {
        component.setOpaque(false);
        if (component instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane)component;
            JViewport viewport = scrollPane.getViewport();
            viewport.setOpaque(false);
            Component c = viewport.getView();
            if (c instanceof JComponent) {
                ((JComponent)c).setOpaque(false);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (this.painter != null) {
            Dimension d = this.getSize();
            Graphics2D g2 = (Graphics2D)g;
            g2.setPaint(this.painter);
            g2.fill(new Rectangle(0, 0, d.width, d.height));
        }
        if (this.image == null) {
            return;
        }
        switch (this.style) {
            case 0: {
                this.drawScaled(g);
                break;
            }
            case 1: {
                this.drawTiled(g);
                break;
            }
            case 2: {
                this.drawActual(g);
                break;
            }
            default: {
                this.drawScaled(g);
            }
        }
    }

    private void drawV(Graphics2D g2) {
        Random random = new Random();
        int mywidth = this.getWidth();
        int myheight = this.getHeight();
        int adj = (int)((double)myheight * 0.33);
        int[] xPoints = new int[]{0, mywidth / 2, mywidth, mywidth, mywidth / 2, 0};
        int[] yPoints = new int[]{-1 * adj, myheight - adj, -1 * adj, 100, myheight, 100};
        GeneralPath star = new GeneralPath();
        star.moveTo(xPoints[0], yPoints[0]);
        for (int count = 1; count < xPoints.length; ++count) {
            star.lineTo(xPoints[count], yPoints[count]);
        }
        star.closePath();
        g2.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
        g2.setColor(new Color(255, 255, 255));
        g2.fill(star);
    }

    private void drawRectangle(Graphics2D g2) {
        double height = 400.0;
        double width = 300.0;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(40.0, 40.0);
        path.curveTo(0.0, 0.0, 8.0, 0.0, 8.0, 0.0);
        path.lineTo(width - 8.0, 0.0);
        path.curveTo(width, 0.0, width, 8.0, width, 8.0);
        path.lineTo(width, height - 8.0);
        path.curveTo(width, height, width - 8.0, height, width - 8.0, height);
        path.lineTo(8.0, height);
        path.curveTo(0.0, height, 0.0, height - 8.0, 0.0, height - 8.0);
        path.closePath();
        g2.fill(path);
    }

    private void drawScaled(Graphics g) {
        Dimension d = this.getSize();
        g.drawImage(this.image, 0, 0, d.width, d.height, null);
    }

    private void drawTiled(Graphics g) {
        Dimension d = this.getSize();
        int width = this.image.getWidth(null);
        int height = this.image.getHeight(null);
        for (int x = 0; x < d.width; x += width) {
            for (int y = 0; y < d.height; y += height) {
                g.drawImage(this.image, x, y, null, null);
            }
        }
    }

    private void drawActual(Graphics g) {
        Dimension d = this.getSize();
        Insets insets = this.getInsets();
        int width = d.width - insets.left - insets.right;
        int height = d.height - insets.top - insets.left;
        float x = (float)(width - this.image.getWidth(null)) * this.alignmentX;
        float y = (float)(height - this.image.getHeight(null)) * this.alignmentY;
        g.drawImage(this.image, (int)x + insets.left, (int)y + insets.top, this);
    }
}

