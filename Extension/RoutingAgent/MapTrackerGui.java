package RoutingAgent.Extension.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapTrackerGui extends JFrame {
    private final TrackerPanel mapPanel;

    // Data storage for live tracking
    private final Map<String, Point> currentLocs = new HashMap<>();
    private final Map<String, List<Point>> drivenPaths = new HashMap<>();
    private final Map<String, List<Point>> remainingPaths = new HashMap<>();

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193), new Color(23, 162, 184)
    };

    public MapTrackerGui() {
        super("Live Fleet Radar - Advanced Visualization");
        setSize(750, 750);
        setLayout(new BorderLayout());

        mapPanel = new TrackerPanel();
        mapPanel.setBackground(new Color(30, 30, 35));
        add(mapPanel, BorderLayout.CENTER);

        setLocationRelativeTo(null);
    }

    public void updateAgent(String name, Point currentLoc, List<Point> remainingStops) {
        currentLocs.put(name, currentLoc);
        remainingPaths.put(name, new ArrayList<>(remainingStops));

        // Update the historical breadcrumb trail
        List<Point> history = drivenPaths.computeIfAbsent(name, k -> new ArrayList<>());
        if (history.isEmpty() || !history.get(history.size() - 1).equals(currentLoc)) {
            history.add(currentLoc);
        }

        mapPanel.repaint();
    }

    public void display() { setVisible(true); }

    private class TrackerPanel extends JPanel {
        private final int SCALE = 6;
        private final int PADDING = 20;
        private final Point depot = new Point(50, 50);

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Draw Background Grid
            g2d.setColor(new Color(50, 50, 55));
            for (int i = 0; i <= 100; i += 10) {
                g2d.drawLine(PADDING, (i * SCALE) + PADDING, (100 * SCALE) + PADDING, (i * SCALE) + PADDING);
                g2d.drawLine((i * SCALE) + PADDING, PADDING, (i * SCALE) + PADDING, (100 * SCALE) + PADDING);
            }

            // 2. Draw Depot
            g2d.setColor(Color.WHITE);
            int dX = (depot.x * SCALE) + PADDING, dY = (depot.y * SCALE) + PADDING;
            g2d.fillRect(dX - 10, dY - 10, 20, 20);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2d.drawString("WAREHOUSE", dX - 35, dY - 15);

            int colorIndex = 0;

            for (String name : drivenPaths.keySet()) {
                List<Point> history = drivenPaths.get(name);
                if (history == null || history.isEmpty()) continue;

                Color agentColor = agentColors[colorIndex % agentColors.length];
                Point liveLoc = currentLocs.get(name);

                // ==========================================
                // LAYER 1: FUTURE PATH (Faded / Transparent)
                // ==========================================
                List<Point> future = remainingPaths.get(name);
                if (future != null && !future.isEmpty() && liveLoc != null) {
                    // Create a 40% opacity version of the agent's color
                    g2d.setColor(new Color(agentColor.getRed(), agentColor.getGreen(), agentColor.getBlue(), 100));
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5}, 0));

                    int prevX = (liveLoc.x * SCALE) + PADDING;
                    int prevY = (liveLoc.y * SCALE) + PADDING;

                    for (Point p : future) {
                        int currX = (p.x * SCALE) + PADDING;
                        int currY = (p.y * SCALE) + PADDING;
                        g2d.drawLine(prevX, prevY, currX, currY);

                        // Draw future customer nodes
                        if (!p.equals(depot)) {
                            g2d.fillOval(currX - 4, currY - 4, 8, 8);
                        }
                        prevX = currX;
                        prevY = currY;
                    }
                }

                // ==========================================
                // LAYER 2: DRIVEN HISTORY (Phase Shifting & Arrows)
                // ==========================================
                g2d.setColor(agentColor);
                int depotVisits = 0;
                boolean isDynamicSegment = false;

                int prevX = (history.get(0).x * SCALE) + PADDING;
                int prevY = (history.get(0).y * SCALE) + PADDING;

                for (int i = 1; i < history.size(); i++) {
                    Point prevNode = history.get(i - 1);
                    Point currNode = history.get(i);

                    int currX = (currNode.x * SCALE) + PADDING;
                    int currY = (currNode.y * SCALE) + PADDING;

                    // Depot tracking logic for Phase 1 vs Phase 2
                    if (currNode.equals(depot) && !prevNode.equals(depot)) {
                        depotVisits++;
                    }
                    if (depotVisits >= 1 && !currNode.equals(depot)) {
                        isDynamicSegment = true;
                    }

                    // Stroke Shift
                    if (isDynamicSegment) {
                        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{8}, 0));
                    } else {
                        g2d.setStroke(new BasicStroke(3));
                    }

                    g2d.drawLine(prevX, prevY, currX, currY);

                    // Directional Arrows (every 15 points, or the very leading edge of the line)
                    if (i % 15 == 0 || i == history.size() - 1) {
                        if (currX != prevX || currY != prevY) {
                            drawArrowHead(g2d, prevX, prevY, currX, currY, agentColor);
                        }
                    }

                    prevX = currX;
                    prevY = currY;
                }

                // ==========================================
                // LAYER 3: LIVE AGENT BLIP (The Truck)
                // ==========================================
                if (liveLoc != null) {
                    int lx = (liveLoc.x * SCALE) + PADDING;
                    int ly = (liveLoc.y * SCALE) + PADDING;

                    // White border ring, solid colored center
                    g2d.setColor(Color.WHITE);
                    g2d.fillOval(lx - 7, ly - 7, 14, 14);
                    g2d.setColor(agentColor);
                    g2d.fillOval(lx - 5, ly - 5, 10, 10);

                    // Agent Name Tag
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                    g2d.drawString(name, lx - 10, ly - 12);
                }

                colorIndex++;
            }
        }

        private void drawArrowHead(Graphics2D g2d, int x1, int y1, int x2, int y2, Color color) {
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int arrowSize = 10;
            int x3 = (int) (x2 - arrowSize * Math.cos(angle - Math.PI / 6));
            int y3 = (int) (y2 - arrowSize * Math.sin(angle - Math.PI / 6));
            int x4 = (int) (x2 - arrowSize * Math.cos(angle + Math.PI / 6));
            int y4 = (int) (y2 - arrowSize * Math.sin(angle + Math.PI / 6));

            g2d.setColor(color);
            g2d.fillPolygon(new int[]{x2, x3, x4}, new int[]{y2, y3, y4}, 3);
        }
    }
}