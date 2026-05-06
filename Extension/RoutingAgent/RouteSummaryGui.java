package RoutingAgent.Extension.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class RouteSummaryGui extends JFrame {
    private final Map<String, List<Point>> initialRoutes;
    private final Map<String, List<Point>> actualRoutes;
    private boolean showingInitial = true;

    // The panel must be initialized before any buttons try to use it
    private final SummaryPanel mapPanel;

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193), new Color(23, 162, 184)
    };

    public RouteSummaryGui(Map<String, List<Point>> initial, Map<String, List<Point>> actual) {
        super("End of Day Route Summary");
        this.initialRoutes = initial;
        this.actualRoutes = actual;

        setSize(700, 720);
        setLayout(new BorderLayout());

        // --- 1. INITIALIZE MAP PANEL FIRST ---
        // We must build the map before the button tries to reference it!
        mapPanel = new SummaryPanel();
        mapPanel.setBackground(new Color(30, 30, 35));
        add(mapPanel, BorderLayout.CENTER);

        // --- 2. TOP CONTROLS ---
        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(40, 40, 45));

        JToggleButton toggleBtn = new JToggleButton("Currently Viewing: PHASE 1 PLANNED ROUTES");
        toggleBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleBtn.setBackground(new Color(0, 123, 255));
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
            mapPanel.repaint(); // Now Java knows mapPanel definitely exists!
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

            // Draw Grid
            g2d.setColor(new Color(50, 50, 55));
            for (int i = 0; i <= 100; i += 10) {
                g2d.drawLine(PADDING, (i * SCALE) + PADDING, (100 * SCALE) + PADDING, (i * SCALE) + PADDING);
                g2d.drawLine((i * SCALE) + PADDING, PADDING, (i * SCALE) + PADDING, (100 * SCALE) + PADDING);
            }

            // Draw Depot
            g2d.setColor(Color.WHITE);
            int dX = (depot.x * SCALE) + PADDING, dY = (depot.y * SCALE) + PADDING;
            g2d.fillRect(dX - 8, dY - 8, 16, 16);
            g2d.drawString("DEPOT", dX - 18, dY - 12);

            // Select which dataset to draw based on the toggle button
            Map<String, List<Point>> dataToDraw = showingInitial ? initialRoutes : actualRoutes;
            int colorIndex = 0;

            for (Map.Entry<String, List<Point>> entry : dataToDraw.entrySet()) {
                String name = entry.getKey();
                List<Point> path = entry.getValue();
                if (path == null || path.isEmpty()) continue;

                g2d.setColor(agentColors[colorIndex % agentColors.length]);

                // Planned routes are dashed. Actual driven paths are solid.
                if (showingInitial) {
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6}, 0));
                } else {
                    g2d.setStroke(new BasicStroke(3));
                }

                int prevX = (path.get(0).x * SCALE) + PADDING;
                int prevY = (path.get(0).y * SCALE) + PADDING;

                for (int i = 1; i < path.size(); i++) {
                    int currX = (path.get(i).x * SCALE) + PADDING;
                    int currY = (path.get(i).y * SCALE) + PADDING;

                    g2d.drawLine(prevX, prevY, currX, currY);

                    // Draw Customer Nodes (Only necessary on Planned routes, as Actual routes trace every physical step)
                    if (showingInitial && !path.get(i).equals(depot)) {
                        g2d.fillOval(currX - 4, currY - 4, 8, 8);
                    }

                    prevX = currX;
                    prevY = currY;
                }

                // Draw Name Tag at the start of the path
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2d.drawString(name, (path.get(0).x * SCALE) + PADDING - 10, (path.get(0).y * SCALE) + PADDING - 15);

                colorIndex++;
            }
        }
    }

    public void display() { setVisible(true); }
}