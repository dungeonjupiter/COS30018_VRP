package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackerPanel extends JPanel {
    private Map<String, Point> currentLocs = new HashMap<>();
    private Map<String, List<Point>> remainingPaths = new HashMap<>();
    private Map<String, Integer> capacities = new HashMap<>();
    private Map<String, List<Point>> initialPlanned = new HashMap<>();
    private List<Point> previewNodes = new ArrayList<>();
    private List<Point> warehouses = new ArrayList<>(List.of(new Point(50, 50)));
    private Map<Point, Parcel> parcelDir = new HashMap<>();

    private static final int OFFSET_X = 220;
    private static final int OFFSET_Y = 60;
    private static final int SCALE = 6;

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193)
    };

    public TrackerPanel() { setBackground(new Color(30, 30, 35)); }

    public void setWarehouses(List<Point> whs) {
        this.warehouses = whs != null ? new ArrayList<>(whs) : new ArrayList<>();
        repaint();
    }

    public void updateData(Map<String, Point> locs, Map<String, List<Point>> actual, Map<String, List<Point>> remaining, Map<String, Integer> caps, Map<String, List<Point>> initial, List<Point> unassigned, Map<Point, Parcel> directory) {
        this.currentLocs = locs;
        this.remainingPaths = remaining;
        this.capacities = caps;
        this.initialPlanned = initial;
        this.previewNodes = unassigned;
        this.parcelDir = directory;
        repaint();
    }

    private boolean isAnyDepot(Point p) {
        for (Point wh : warehouses) {
            if (p.equals(wh)) return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGridAndWarehouses(g2d);
        drawLegend(g2d);

        if (previewNodes != null && !previewNodes.isEmpty()) {
            g2d.setColor(new Color(180, 180, 180));
            for (Point p : previewNodes) {
                int px = (p.x * SCALE) + OFFSET_X;
                int py = (p.y * SCALE) + OFFSET_Y;
                g2d.fillOval(px - 6, py - 6, 12, 12);
                g2d.setColor(Color.WHITE);
                g2d.drawOval(px - 7, py - 7, 14, 14);
                g2d.setColor(new Color(180, 180, 180));
            }
        }

        int colorIdx = 0;
        for (String name : currentLocs.keySet()) {
            Color c = agentColors[colorIdx % agentColors.length];
            Point live = currentLocs.get(name);
            List<Point> rem = remainingPaths.get(name);
            List<Point> initRoute = initialPlanned.get(name);

            if (rem != null && !rem.isEmpty()) {
                Point prev = live;
                for (Point p : rem) {
                    boolean isInitialNode = (initRoute != null && initRoute.contains(p));
                    drawRouteLine(g2d, prev, p, c, isInitialNode);

                    if (!isAnyDepot(p)) {
                        int px = (p.x * SCALE) + OFFSET_X;
                        int py = (p.y * SCALE) + OFFSET_Y;
                        g2d.setColor(c);
                        g2d.fillOval(px - 7, py - 7, 14, 14);
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(px - 2, py - 2, 4, 4);
                    }
                    prev = p;
                }
            }

            if (live != null) {
                int lx = (live.x * SCALE) + OFFSET_X;
                int ly = (live.y * SCALE) + OFFSET_Y;
                g2d.setColor(Color.WHITE);
                g2d.fillRect(lx - 8, ly - 8, 16, 16);
                g2d.setColor(c);
                g2d.fillRect(lx - 6, ly - 6, 12, 12);
            }
            colorIdx++;
        }
    }

    private void drawRouteLine(Graphics2D g2d, Point p1, Point p2, Color c, boolean isInitial) {
        int x1 = (p1.x * SCALE) + OFFSET_X;
        int y1 = (p1.y * SCALE) + OFFSET_Y;
        int x2 = (p2.x * SCALE) + OFFSET_X;
        int y2 = (p2.y * SCALE) + OFFSET_Y;

        if (isInitial) g2d.setStroke(new BasicStroke(3.5f));
        else g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{8}, 0));

        g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));
        g2d.drawLine(x1, y1, x2, y2);
        drawArrow(g2d, x1, y1, x2, y2);
    }

    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 5);
        arrowHead.addPoint(-10, -5);
        arrowHead.addPoint(10, -5);
        java.awt.geom.AffineTransform tx = new java.awt.geom.AffineTransform();
        tx.translate(midX, midY);
        tx.rotate(angle - Math.PI / 2.0);
        g2.fill(tx.createTransformedShape(arrowHead));
    }

    private void drawGridAndWarehouses(Graphics2D g2d) {
        g2d.setColor(new Color(50, 50, 55));
        for (int i = 0; i <= 100; i += 10) {
            g2d.drawLine(OFFSET_X, OFFSET_Y + (i * SCALE), OFFSET_X + (100 * SCALE), OFFSET_Y + (i * SCALE));
            g2d.drawLine(OFFSET_X + (i * SCALE), OFFSET_Y, OFFSET_X + (i * SCALE), OFFSET_Y + (100 * SCALE));
        }
        g2d.setColor(Color.RED);
        int whIdx = 1;
        for (Point wh : warehouses) {
            int wx = (wh.x * SCALE) + OFFSET_X - 10;
            int wy = (wh.y * SCALE) + OFFSET_Y - 10;
            g2d.fillRect(wx, wy, 20, 20);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2d.drawString("W" + whIdx, wx + 4, wy + 14);
            g2d.setColor(Color.RED);
            whIdx++;
        }
    }

    private void drawLegend(Graphics2D g2d) {
        int numAgents = currentLocs.size();
        int boxHeight = 50 + (warehouses.size() * 18) + (numAgents * 20);

        g2d.setColor(new Color(20, 20, 25, 210));
        g2d.fillRoundRect(10, 10, 210, boxHeight, 15, 15);
        g2d.setColor(new Color(80, 80, 90));
        g2d.drawRoundRect(10, 10, 210, boxHeight, 15, 15);

        int ly = 30;
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2d.drawString("LIVE LEGEND:", 20, ly);
        ly += 20;

        int whIdx = 1;
        for (Point wh : warehouses) {
            g2d.setColor(Color.RED);
            g2d.fillRect(20, ly - 9, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2d.drawString("Warehouse W" + whIdx + " (" + wh.x + "," + wh.y + ")", 35, ly);
            ly += 18;
            whIdx++;
        }

        int idx = 0;
        for (String name : currentLocs.keySet()) {
            int currentLoad = 0;
            List<Point> rem = remainingPaths.get(name);
            if (rem != null && parcelDir != null) {
                for (Point p : rem) {
                    if (parcelDir.containsKey(p)) currentLoad += parcelDir.get(p).getDemand();
                }
            }

            ly += 20;
            g2d.setColor(agentColors[idx % agentColors.length]);
            g2d.fillRect(20, ly - 9, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.drawString(name + " (Load: " + currentLoad + "/" + capacities.getOrDefault(name, 0) + ")", 35, ly);
            idx++;
        }
    }
}
