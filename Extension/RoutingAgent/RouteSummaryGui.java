package RoutingAgent.Extension.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouteSummaryGui extends JFrame {
    private final Map<String, List<Point>> initialRoutes;
    private final Map<String, List<Point>> actualRoutes;
    private boolean showingInitial = false;

    private final SummaryPanel mapPanel;

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193), new Color(23, 162, 184)
    };

    public RouteSummaryGui(Map<String, List<Point>> initial, Map<String, List<Point>> actual) {
        super("End of Day Route Summary");
        this.initialRoutes = initial;
        this.actualRoutes = actual;

        setSize(750, 750);
        setLayout(new BorderLayout());

        mapPanel = new SummaryPanel();
        mapPanel.setBackground(new Color(30, 30, 35));
        add(mapPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(40, 40, 45));

        JToggleButton toggleBtn = new JToggleButton("Currently Viewing: ACTUAL DRIVEN ROUTES (Final)");
        toggleBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleBtn.setBackground(new Color(40, 167, 69));
        toggleBtn.setForeground(Color.WHITE);
        toggleBtn.setFocusPainted(false);

        toggleBtn.addActionListener(e -> {
            showingInitial = !showingInitial;
            if (showingInitial) {
                toggleBtn.setText("Currently Viewing: PHASE 1 PLANNED ROUTES");
                toggleBtn.setBackground(new Color(0, 123, 255));
            } else {
                toggleBtn.setText("Currently Viewing: ACTUAL DRIVEN ROUTES (Final)");
                toggleBtn.setBackground(new Color(40, 167, 69));
            }
            mapPanel.repaint();
        });

        controlPanel.add(toggleBtn);
        add(controlPanel, BorderLayout.NORTH);

        setLocationRelativeTo(null);
    }

    private class SummaryPanel extends JPanel {
        private final int SCALE = 6;
        private final int PADDING = 20;
        private final Point depot = new Point(50, 50);

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Draw Grid
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

            Map<String, List<Point>> dataToDraw = showingInitial ? initialRoutes : actualRoutes;
            int colorIndex = 0;

            for (Map.Entry<String, List<Point>> entry : dataToDraw.entrySet()) {
                String name = entry.getKey();
                List<Point> rawPath = entry.getValue();
                if (rawPath == null || rawPath.isEmpty()) continue;

                // --- THE FIX IS HERE ---
                // We removed the "!showingInitial" restriction.
                // Now, if ANY route (Planned or Actual) doesn't end at the Warehouse, the visualizer forcefully bridges the gap!
                List<Point> path = new ArrayList<>(rawPath);
                if (!path.get(path.size() - 1).equals(depot)) {
                    path.add(new Point(depot));
                }

                Color agentColor = agentColors[colorIndex % agentColors.length];
                g2d.setColor(agentColor);

                int depotVisits = 0;
                boolean isDynamicSegment = false;

                int prevX = (path.get(0).x * SCALE) + PADDING;
                int prevY = (path.get(0).y * SCALE) + PADDING;

                for (int i = 1; i < path.size(); i++) {
                    Point prevNode = path.get(i - 1);
                    Point currNode = path.get(i);

                    int currX = (currNode.x * SCALE) + PADDING;
                    int currY = (currNode.y * SCALE) + PADDING;

                    // --- INTELLIGENT STROKE LOGIC ---
                    if (!showingInitial) {
                        if (currNode.equals(depot) && !prevNode.equals(depot)) {
                            depotVisits++;
                        }
                        if (depotVisits >= 1 && !currNode.equals(depot)) {
                            isDynamicSegment = true;
                        }

                        if (isDynamicSegment) {
                            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{8}, 0));
                        } else {
                            g2d.setStroke(new BasicStroke(3));
                        }
                    } else {
                        // Planned Phase 1 routes get a clean dashed line
                        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5}, 0));
                    }

                    // Draw the segment
                    g2d.drawLine(prevX, prevY, currX, currY);

                    // --- DIRECTIONAL ARROW LOGIC ---
                    boolean drawArrow = false;
                    if (showingInitial) {
                        drawArrow = true;
                    } else {
                        if (i % 15 == 0 || i == path.size() - 1) {
                            drawArrow = true;
                        }
                    }

                    if (drawArrow && (currX != prevX || currY != prevY)) {
                        drawArrowHead(g2d, prevX, prevY, currX, currY, agentColor);
                    }

                    // Draw circles for planned customer nodes
                    if (!currNode.equals(depot) && showingInitial) {
                        g2d.fillOval(currX - 5, currY - 5, 10, 10);
                    }

                    prevX = currX;
                    prevY = currY;
                }

                // Name Tag
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2d.drawString(name, (path.get(0).x * SCALE) + PADDING - 10, (path.get(0).y * SCALE) + PADDING - 15);

                colorIndex++;
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
            g2d.fillPolygon(new int[]{x2, x3, x4}, new int[]{y2, y3, y4}, 3);
        }
    }

    public void display() { setVisible(true); }
}