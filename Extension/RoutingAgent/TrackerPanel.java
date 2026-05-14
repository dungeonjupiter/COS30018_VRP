package RoutingAgent.Extension.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackerPanel extends JPanel {
    private Map<String, Point> currentLocs = new HashMap<>();
    private Map<String, List<Point>> remainingPaths = new HashMap<>();
    private Map<String, Integer> capacities = new HashMap<>();
    private Map<String, List<Point>> initialPlanned = new HashMap<>();

    private final Point depot = new Point(50, 50);
    private static final int PADDING = 60;
    private static final int SCALE = 6;

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193)
    };

    public TrackerPanel() { setBackground(new Color(30, 30, 35)); }

    public void updateData(Map<String, Point> locs, Map<String, List<Point>> actual, Map<String, List<Point>> remaining, Map<String, Integer> caps, Map<String, List<Point>> initial) {
        this.currentLocs = locs;
        this.remainingPaths = remaining;
        this.capacities = caps;
        this.initialPlanned = initial;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGridAndDepot(g2d);
        drawLegend(g2d);

        int colorIdx = 0;
        for (String name : currentLocs.keySet()) {
            Color c = agentColors[colorIdx % agentColors.length];
            Point live = currentLocs.get(name);
            List<Point> rem = remainingPaths.get(name);
            List<Point> initRoute = initialPlanned.get(name);

            if (rem != null && !rem.isEmpty()) {
                Point prev = live;
                for (Point p : rem) {
                    // Logic: If the point was part of the original morning route, bold it. Else dotted.
                    boolean isInitialNode = (initRoute != null && initRoute.contains(p));
                    drawRouteLine(g2d, prev, p, c, isInitialNode);
                    prev = p;
                }
            }

            // Draw live truck blip
            if (live != null) {
                int lx = (live.x * SCALE) + PADDING;
                int ly = (live.y * SCALE) + PADDING;
                g2d.setColor(Color.WHITE);
                g2d.fillOval(lx - 8, ly - 8, 16, 16);
                g2d.setColor(c);
                g2d.fillOval(lx - 6, ly - 6, 12, 12);
            }
            colorIdx++;
        }
    }

    private void drawRouteLine(Graphics2D g2d, Point p1, Point p2, Color c, boolean isInitial) {
        int x1 = (p1.x * SCALE) + PADDING;
        int y1 = (p1.y * SCALE) + PADDING;
        int x2 = (p2.x * SCALE) + PADDING;
        int y2 = (p2.y * SCALE) + PADDING;

        if (isInitial) {
            g2d.setStroke(new BasicStroke(3.5f)); // Bold for original
        } else {
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{8}, 0)); // Dotted for dynamic
        }

        g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));
        g2d.drawLine(x1, y1, x2, y2);
        drawArrow(g2d, x1, y1, x2, y2);
    }

    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double Math_PI = Math.PI;
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;

        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 5);
        arrowHead.addPoint(-10, -5);
        arrowHead.addPoint(10, -5);

        java.awt.geom.AffineTransform tx = new java.awt.geom.AffineTransform();
        tx.translate(midX, midY);
        tx.rotate(angle - Math_PI / 2.0);
        g2.fill(tx.createTransformedShape(arrowHead));
    }

    private void drawGridAndDepot(Graphics2D g2d) {
        g2d.setColor(new Color(50, 50, 55));
        for (int i = 0; i <= 100; i += 10) {
            g2d.drawLine(PADDING, PADDING + (i * SCALE), PADDING + (100 * SCALE), PADDING + (i * SCALE));
            g2d.drawLine(PADDING + (i * SCALE), PADDING, PADDING + (i * SCALE), PADDING + (100 * SCALE));
        }
        g2d.setColor(Color.RED);
        g2d.fillRect((depot.x * SCALE) + PADDING - 10, (depot.y * SCALE) + PADDING - 10, 20, 20);
    }

    private void drawLegend(Graphics2D g2d) {
        int ly = 20;
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2d.drawString("LEGEND:", 10, ly);
        ly += 20;

        g2d.setColor(Color.RED);
        g2d.fillRect(10, ly, 10, 10);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2d.drawString("Warehouse (50,50)", 25, ly + 10);

        int idx = 0;
        for (String name : currentLocs.keySet()) {
            ly += 20;
            g2d.setColor(agentColors[idx % agentColors.length]);
            g2d.fillOval(10, ly, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.drawString(name + " (Capacity: " + capacities.getOrDefault(name, 0) + ")", 25, ly + 10);
            idx++;
        }
    }
}