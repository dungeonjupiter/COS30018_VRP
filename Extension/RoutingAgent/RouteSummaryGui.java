package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RouteSummaryGui — End-of-day route summary window.
 *
 * Fixed from original:
 *   - Removed hardcoded depot = new Point(50, 50).
 *   - Now accepts List<Warehouse> and renders all warehouses with their colours.
 *   - smoothActualRoute() now checks ALL warehouse positions, not just (50,50).
 *   - Legend shows each warehouse as a coloured entry.
 *   - Backward-compatible overload kept so old code still compiles.
 */
public class RouteSummaryGui extends JFrame {

    private final Map<String, List<Point>> initialRoutes;
    private final Map<String, List<Point>> actualRoutes;
    private final Map<Point, Parcel>       directory;
    private final List<Warehouse>          warehouses;
    private boolean showingInitial = false;

    private final SummaryPanel mapPanel;

    private final Color[] agentColors = {
            new Color(220,  53,  69), new Color(  0, 123, 255),
            new Color( 40, 167,  69), new Color(253, 126,  20),
            new Color(111,  66, 193), new Color( 23, 162, 184)
    };

    /** Full constructor — pass warehouses for correct multi-depot rendering. */
    public RouteSummaryGui(Map<String, List<Point>> initial,
                           Map<String, List<Point>> actual,
                           Map<Point, Parcel> directory,
                           List<Warehouse> warehouses) {
        super("End of Day Route Summary");
        this.initialRoutes = initial;
        this.actualRoutes  = actual;
        this.directory     = directory;
        this.warehouses    = (warehouses != null && !warehouses.isEmpty())
                ? warehouses
                : List.of(new Warehouse(0, 50, 50));

        setSize(900, 800);
        setLayout(new BorderLayout());

        mapPanel = new SummaryPanel();
        mapPanel.setBackground(new Color(30, 30, 35));
        add(mapPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(45, 45, 50));
        JButton toggleBtn = new JButton("Currently Showing: Actual Driven Routes (Click to Toggle)");
        toggleBtn.setBackground(new Color(0, 123, 255));
        toggleBtn.setForeground(Color.WHITE);
        toggleBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleBtn.addActionListener(e -> {
            showingInitial = !showingInitial;
            if (showingInitial) {
                toggleBtn.setText("Currently Showing: Initial Planned Routes (Click to Toggle)");
                toggleBtn.setBackground(new Color(253, 126, 20));
            } else {
                toggleBtn.setText("Currently Showing: Actual Driven Routes (Click to Toggle)");
                toggleBtn.setBackground(new Color(0, 123, 255));
            }
            mapPanel.repaint();
        });
        controlPanel.add(toggleBtn);
        add(controlPanel, BorderLayout.SOUTH);
        setLocationRelativeTo(null);
    }

    /** Backward-compatible overload — uses single depot at (50,50). */
    public RouteSummaryGui(Map<String, List<Point>> initial,
                           Map<String, List<Point>> actual,
                           Map<Point, Parcel> directory) {
        this(initial, actual, directory, List.of(new Warehouse(0, 50, 50)));
    }

    // ── Inner paint panel ─────────────────────────────────────────────────────

    private class SummaryPanel extends JPanel {

        private static final int OFFSET_X = 220;
        private static final int OFFSET_Y = 60;
        private static final int SCALE    = 6;

        /** True if p is any warehouse position. */
        private boolean isWarehouse(Point p) {
            for (Warehouse wh : warehouses) {
                if (wh.getPos().equals(p)) return true;
            }
            return false;
        }

        /** Smooth GPS trace: keep only warehouse nodes and delivery stops. */
        private List<Point> smoothActualRoute(List<Point> rawGps) {
            if (rawGps == null || rawGps.isEmpty()) return rawGps;
            List<Point> smoothed = new ArrayList<>();
            smoothed.add(rawGps.get(0));
            for (int i = 1; i < rawGps.size() - 1; i++) {
                Point p = rawGps.get(i);
                if (isWarehouse(p) || (directory != null && directory.containsKey(p))) {
                    if (!smoothed.get(smoothed.size() - 1).equals(p)) smoothed.add(p);
                }
            }
            Point last = rawGps.get(rawGps.size() - 1);
            if (!smoothed.get(smoothed.size() - 1).equals(last)) smoothed.add(last);
            return smoothed;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawGridAndWarehouses(g2d);
            drawLegend(g2d);

            Map<String, List<Point>> active = showingInitial ? initialRoutes : actualRoutes;
            if (active == null || active.isEmpty()) return;

            int colorIdx = 0;
            for (String name : active.keySet()) {
                Color agentColor = agentColors[colorIdx % agentColors.length];
                List<Point> path = active.get(name);
                if (!showingInitial) path = smoothActualRoute(path);
                if (path == null || path.isEmpty()) { colorIdx++; continue; }

                g2d.setColor(agentColor);
                g2d.setStroke(new BasicStroke(2.5f));

                int prevX = path.get(0).x * SCALE + OFFSET_X;
                int prevY = path.get(0).y * SCALE + OFFSET_Y;

                for (int i = 1; i < path.size(); i++) {
                    Point curr  = path.get(i);
                    int   currX = curr.x * SCALE + OFFSET_X;
                    int   currY = curr.y * SCALE + OFFSET_Y;

                    g2d.drawLine(prevX, prevY, currX, currY);

                    boolean isStop = (directory != null && directory.containsKey(curr));
                    boolean isEnd  = (i == path.size() - 1);

                    if (isStop || (!isWarehouse(curr) && showingInitial)) {
                        if (currX != prevX || currY != prevY)
                            drawArrowHead(g2d, prevX, prevY, currX, currY, agentColor);
                        g2d.setColor(agentColor);
                        g2d.fillOval(currX - 7, currY - 7, 14, 14);
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(currX - 2, currY - 2, 4, 4);
                        g2d.setColor(agentColor);
                    } else if (isEnd) {
                        drawArrowHead(g2d, prevX, prevY, currX, currY, agentColor);
                    }
                    prevX = currX; prevY = currY;
                }

                // Agent label at furthest point from first warehouse
                Point origin = warehouses.get(0).getPos();
                Point furthest = path.get(0);
                double maxDist = -1;
                for (Point p : path) {
                    double d = p.distance(origin);
                    if (d > maxDist) { maxDist = d; furthest = p; }
                }
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2d.drawString(name,
                        furthest.x * SCALE + OFFSET_X - 10,
                        furthest.y * SCALE + OFFSET_Y - 15);

                colorIdx++;
            }
        }

        private void drawGridAndWarehouses(Graphics2D g2d) {
            // Grid
            g2d.setColor(new Color(50, 50, 55));
            for (int i = 0; i <= 100; i += 10) {
                g2d.drawLine(OFFSET_X, OFFSET_Y + i * SCALE,
                             OFFSET_X + 100 * SCALE, OFFSET_Y + i * SCALE);
                g2d.drawLine(OFFSET_X + i * SCALE, OFFSET_Y,
                             OFFSET_X + i * SCALE,   OFFSET_Y + 100 * SCALE);
            }
            // Each warehouse with its colour
            for (Warehouse wh : warehouses) {
                int   px = wh.getX() * SCALE + OFFSET_X;
                int   py = wh.getY() * SCALE + OFFSET_Y;
                Color c  = wh.getColor();
                g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
                g2d.fillOval(px - 16, py - 16, 32, 32);
                g2d.setColor(c);
                g2d.fillRect(px - 10, py - 10, 20, 20);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRect(px - 10, py - 10, 20, 20);
                g2d.setStroke(new BasicStroke(1f));
                g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2d.setColor(c);
                g2d.drawString(wh.getName(), px - 10, py - 13);
            }
        }

        private void drawLegend(Graphics2D g2d) {
            Map<String, List<Point>> active = showingInitial ? initialRoutes : actualRoutes;
            int numAgents = (active != null) ? active.size() : 0;
            int boxH = 30 + (warehouses.size() * 20) + 10 + (numAgents * 20);

            g2d.setColor(new Color(20, 20, 25, 210));
            g2d.fillRoundRect(10, 10, 190, boxH, 15, 15);
            g2d.setColor(new Color(80, 80, 90));
            g2d.drawRoundRect(10, 10, 190, boxH, 15, 15);

            int ly = 30;
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2d.drawString("SUMMARY LEGEND:", 20, ly);

            for (Warehouse wh : warehouses) {
                ly += 20;
                g2d.setColor(wh.getColor());
                g2d.fillRect(20, ly - 9, 10, 10);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2d.drawString(wh.getName() + " (" + wh.getX() + "," + wh.getY() + ")", 34, ly);
            }

            ly += 10;
            g2d.setColor(new Color(80, 80, 90));
            g2d.drawLine(15, ly, 200, ly);

            if (active != null) {
                int idx = 0;
                for (String name : active.keySet()) {
                    ly += 20;
                    g2d.setColor(agentColors[idx % agentColors.length]);
                    g2d.fillRect(20, ly - 9, 10, 10);
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    g2d.drawString(name + " Route", 34, ly);
                    idx++;
                }
            }
        }

        private void drawArrowHead(Graphics2D g2d, int x1, int y1,
                                   int x2, int y2, Color color) {
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int    sz    = 12;
            int x3 = (int)(x2 - sz * Math.cos(angle - Math.PI / 6));
            int y3 = (int)(y2 - sz * Math.sin(angle - Math.PI / 6));
            int x4 = (int)(x2 - sz * Math.cos(angle + Math.PI / 6));
            int y4 = (int)(y2 - sz * Math.sin(angle + Math.PI / 6));
            g2d.setColor(color);
            Polygon arrow = new Polygon();
            arrow.addPoint(x2, y2); arrow.addPoint(x3, y3); arrow.addPoint(x4, y4);
            g2d.fillPolygon(arrow);
        }
    }
}
