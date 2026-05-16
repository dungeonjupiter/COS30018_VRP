package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouteSummaryGui extends JFrame {
    private final Map<String, List<Point>> initialRoutes;
    private final Map<String, List<Point>> actualRoutes;
    private final Map<Point, Parcel> directory;
    private boolean showingInitial = false;

    private final SummaryPanel mapPanel;

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193), new Color(23, 162, 184)
    };

    public RouteSummaryGui(Map<String, List<Point>> initial, Map<String, List<Point>> actual, Map<Point, Parcel> directory) {
        super("End of Day Route Summary");
        this.initialRoutes = initial;
        this.actualRoutes = actual;
        this.directory = directory;

        setSize(900, 800); // Widened to accommodate the shifted map
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

    private class SummaryPanel extends JPanel {
        private final Point depot = new Point(50, 50);
        // THE FIX: Separate X and Y offsets. Shifted X 220 pixels right to clear the legend.
        private static final int OFFSET_X = 220;
        private static final int OFFSET_Y = 60;
        private static final int SCALE = 6;

        private List<Point> smoothActualRoute(List<Point> rawGps) {
            if (rawGps == null || rawGps.isEmpty()) return rawGps;
            List<Point> smoothed = new ArrayList<>();
            smoothed.add(rawGps.get(0));

            for (int i = 1; i < rawGps.size() - 1; i++) {
                Point p = rawGps.get(i);
                if (p.equals(depot) || (directory != null && directory.containsKey(p))) {
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

            drawGridAndDepot(g2d);
            drawLegend(g2d);

            Map<String, List<Point>> activeMap = showingInitial ? initialRoutes : actualRoutes;
            if (activeMap == null || activeMap.isEmpty()) return;

            int colorIndex = 0;
            for (String name : activeMap.keySet()) {
                Color agentColor = agentColors[colorIndex % agentColors.length];
                List<Point> path = activeMap.get(name);
                if (!showingInitial) path = smoothActualRoute(path);

                if (path == null || path.isEmpty()) {
                    colorIndex++;
                    continue;
                }

                g2d.setColor(agentColor);
                g2d.setStroke(new BasicStroke(2.5f));

                int prevX = (path.get(0).x * SCALE) + OFFSET_X;
                int prevY = (path.get(0).y * SCALE) + OFFSET_Y;

                for (int i = 1; i < path.size(); i++) {
                    Point currNode = path.get(i);
                    int currX = (currNode.x * SCALE) + OFFSET_X;
                    int currY = (currNode.y * SCALE) + OFFSET_Y;

                    g2d.drawLine(prevX, prevY, currX, currY);

                    boolean isStop = (directory != null && directory.containsKey(currNode));
                    boolean isEnd = (i == path.size() - 1);

                    if (isStop || (!currNode.equals(depot) && showingInitial)) {
                        if (currX != prevX || currY != prevY) drawArrowHead(g2d, prevX, prevY, currX, currY, agentColor);
                        g2d.fillOval(currX - 7, currY - 7, 14, 14);
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(currX - 2, currY - 2, 4, 4);
                        g2d.setColor(agentColor);
                    } else if (isEnd) {
                        drawArrowHead(g2d, prevX, prevY, currX, currY, agentColor);
                    }
                    prevX = currX;
                    prevY = currY;
                }

                Point furthestNode = path.get(0);
                double maxDist = -1;
                for (Point p : path) {
                    double dist = p.distance(depot);
                    if (dist > maxDist) {
                        maxDist = dist;
                        furthestNode = p;
                    }
                }

                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2d.drawString(name, (furthestNode.x * SCALE) + OFFSET_X - 10, (furthestNode.y * SCALE) + OFFSET_Y - 15);

                colorIndex++;
            }
        }

        private void drawLegend(Graphics2D g2d) {
            Map<String, List<Point>> activeMap = showingInitial ? initialRoutes : actualRoutes;
            int numAgents = (activeMap != null) ? activeMap.size() : 0;

            // Draw Legend Background Box
            int boxHeight = 50 + (numAgents * 20);
            g2d.setColor(new Color(20, 20, 25, 210));
            g2d.fillRoundRect(10, 10, 180, boxHeight, 15, 15);
            g2d.setColor(new Color(80, 80, 90));
            g2d.drawRoundRect(10, 10, 180, boxHeight, 15, 15);

            int ly = 30;
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2d.drawString("SUMMARY LEGEND:", 20, ly);
            ly += 20;

            g2d.setColor(Color.RED);
            g2d.fillRect(20, ly - 9, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2d.drawString("Warehouse (50,50)", 35, ly);

            if (activeMap != null) {
                int idx = 0;
                for (String name : activeMap.keySet()) {
                    ly += 20;
                    g2d.setColor(agentColors[idx % agentColors.length]);
                    g2d.fillRect(20, ly - 9, 10, 10);
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(name + " Route", 35, ly);
                    idx++;
                }
            }
        }

        private void drawArrowHead(Graphics2D g2d, int x1, int y1, int x2, int y2, Color color) {
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int arrowSize = 12;
            int x3 = (int) (x2 - arrowSize * Math.cos(angle - Math.PI / 6));
            int y3 = (int) (y2 - arrowSize * Math.sin(angle - Math.PI / 6));
            int x4 = (int) (x2 - arrowSize * Math.cos(angle + Math.PI / 6));
            int y4 = (int) (y2 - arrowSize * Math.sin(angle + Math.PI / 6));

            g2d.setColor(color);
            Polygon arrow = new Polygon();
            arrow.addPoint(x2, y2);
            arrow.addPoint(x3, y3);
            arrow.addPoint(x4, y4);
            g2d.fillPolygon(arrow);
        }

        private void drawGridAndDepot(Graphics2D g2d) {
            g2d.setColor(new Color(50, 50, 55));
            for (int i = 0; i <= 100; i += 10) {
                g2d.drawLine(OFFSET_X, OFFSET_Y + (i * SCALE), OFFSET_X + (100 * SCALE), OFFSET_Y + (i * SCALE));
                g2d.drawLine(OFFSET_X + (i * SCALE), OFFSET_Y, OFFSET_X + (i * SCALE), OFFSET_Y + (100 * SCALE));
            }
            g2d.setColor(Color.RED);
            g2d.fillRect((depot.x * SCALE) + OFFSET_X - 10, (depot.y * SCALE) + OFFSET_Y - 10, 20, 20);
        }
    }
}