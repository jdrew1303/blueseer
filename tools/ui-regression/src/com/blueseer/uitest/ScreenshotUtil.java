package com.blueseer.uitest;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Captures the whole (virtual) screen with java.awt.Robot so screenshots line
 * up regardless of which window manager decorations are in play.
 */
final class ScreenshotUtil {

    private final Robot robot;

    ScreenshotUtil() {
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            throw new RuntimeException(ex);
        }
    }

    void capture(File outFile) {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        BufferedImage image = robot.createScreenCapture(bounds);
        try {
            outFile.getParentFile().mkdirs();
            ImageIO.write(image, "png", outFile);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
