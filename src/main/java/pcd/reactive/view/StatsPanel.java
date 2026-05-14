package pcd.reactive.view;

import pcd.common.FSReport;

import javax.swing.*;
import java.awt.*;

/**
 * A minimal panel that displays file size distribution as a text list.
 *
 * <p>Updated via {@link #setReport(FSReport)}; calling that method triggers
 * a repaint automatically. All painting happens on the EDT.
 */
public class StatsPanel extends JPanel {

    private static final Color BG = Color.WHITE;
    private static final Color TEXT = Color.BLACK;

    private FSReport report;

    public StatsPanel() {
        setBackground(BG);
        setPreferredSize(new Dimension(700, 280));
        setMinimumSize(new Dimension(400, 200));
    }

    /**
     * Replace the current data and repaint.
     */
    public void setReport(FSReport report) {
        this.report = report;
        repaint();
    }

    /**
     * Clear all data.
     */
    public void clear() {
        this.report = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        int w = getWidth(), h = getHeight();
        g2.setColor(BG);
        g2.fillRect(0, 0, w, h);

        if (report == null) {
            drawPlaceholder(g2, w, h);
            g2.dispose();
            return;
        }

        long[] counts = report.bandCounts();
        int numBands = report.numBands();
        long maxFS = report.maxFileSize();

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(TEXT);
        g2.drawString("File Size Distribution", 20, 25);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        int y = 45;
        long bandSize = maxFS / numBands;

        for (int i = 0; i < counts.length; i++) {
            String rangeLbl;
            if (i < numBands) {
                rangeLbl = i * bandSize + " – " + (i + 1) * bandSize;
            } else {
                rangeLbl = "> " + maxFS;
            }
            String line = String.format("%-18s : %,d files", rangeLbl, counts[i]);
            g2.drawString(line, 20, y);
            y += 20;
        }

        g2.dispose();
    }

    private void drawPlaceholder(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(150, 150, 150));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        FontMetrics fm = g2.getFontMetrics();
        String msg = "Start a scan to see the file size distribution";
        g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
    }
}
