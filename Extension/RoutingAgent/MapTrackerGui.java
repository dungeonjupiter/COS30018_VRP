package RoutingAgent.Extension.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapTrackerGui extends JFrame {
    private final MapPanel mapPanel;
    private final Point depot = new Point(50, 50);

    // Thread-safe map to hold real-time GPS data from the agents
    private final Map<String, AgentData> fleetData = new ConcurrentHashMap<>();

    // Professional color palette for different agents
    private final Color[] agentColors = {
            new Color(220, 53, 69),  // Red
            new Color(0, 123, 255),  // Blue
            new Color(40, 167, 69),  // Green
            new Color(253, 126, 20), // Orange
            new Color(111, 66, 193), // Purple
            new Color(23, 162, 184), // Teal
            new Color(232, 62, 140)  // Pink
    };

    public MapTrackerGui() {
        super("Live Fleet Radar");
        setSize(700, 680); // Slightly wider to accommodate the legend comfortably
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        mapPanel = new MapPanel();
        mapPanel.setBackground(new Color(30, 30, 35)); // Dark mode radar look
        add(mapPanel, BorderLayout.CENTER);
    }

    // Called by the MRA when it receives a GPS ping from a DA
    public void updateAgent(String agentName, Point location, List<Point> route) {
        fleetData.computeIfAbsent(agentName, k -> new AgentData(getAssignColor(fleetData.size())))
                .update(location, route);

        // Force the map to redraw with the new coordinates
        SwingUtilities.invokeLater(mapPanel::repaint);
    }

    private Color getAssignColor(int index) {
        return agentColors[index % agentColors.length];
    }

    // Inner class to store live data
    private static class AgentData {
        Point currentLocation = new Point(50, 50);
        List<Point> remainingRoute = new ArrayList<>();
        Color color;

        AgentData(Color c) { this.color = c; }
        void update(Point loc, List<Point> route) {
            this.currentLocation = loc;
            this.remainingRoute = route;
        }
    }

    // Inner class that handles the actual painting
    private class MapPanel extends JPanel {
        private final int SCALE = 6; // 100x100 grid becomes 600x600 pixels
        private final int PADDING = 20; // Margin from edges

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Draw Grid Lines
            g2d.setColor(new Color(50, 50, 55));
            for (int i = 0; i <= 100; i += 10) {
                g2d.drawLine(PADDING, (i * SCALE) + PADDING, (100 * SCALE) + PADDING, (i * SCALE) + PADDING);
                g2d.drawLine((i * SCALE) + PADDING, PADDING, (i * SCALE) + PADDING, (100 * SCALE) + PADDING);
            }

            // 2. Draw Depot
            g2d.setColor(Color.WHITE);
            int dX = (depot.x * SCALE) + PADDING;
            int dY = (depot.y * SCALE) + PADDING;
            g2d.fillRect(dX - 8, dY - 8, 16, 16);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2d.drawString("DEPOT", dX - 18, dY - 12);

            // 3. Draw Agents and Routes
            for (Map.Entry<String, AgentData> entry : fleetData.entrySet()) {
                String name = entry.getKey();
                AgentData data = entry.getValue();
                Point loc = data.currentLocation;

                int currentX = (loc.x * SCALE) + PADDING;
                int currentY = (loc.y * SCALE) + PADDING;

                // Draw Dashed Route Lines
                g2d.setColor(data.color);
                g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6}, 0));

                int prevX = currentX;
                int prevY = currentY;

                for (Point target : data.remainingRoute) {
                    int tX = (target.x * SCALE) + PADDING;
                    int tY = (target.y * SCALE) + PADDING;

                    g2d.drawLine(prevX, prevY, tX, tY);

                    // Draw parcel destination depending on if it's the depot or a customer
                    if (target.equals(depot)) {
                        g2d.drawRect(tX - 6, tY - 6, 12, 12); // Square for returning to depot
                    } else {
                        g2d.fillOval(tX - 4, tY - 4, 8, 8); // Circle for customer
                    }

                    prevX = tX;
                    prevY = tY;
                }

                // Draw Agent blip
                g2d.setStroke(new BasicStroke(1));
                g2d.setColor(data.color);
                g2d.fillOval(currentX - 7, currentY - 7, 14, 14);
                g2d.setColor(Color.WHITE);
                g2d.drawString(name, currentX + 10, currentY + 4);
            }

            // ==========================================
            // 4. DRAW DYNAMIC LEGEND
            // ==========================================
            int legendWidth = 160;
            int legendItemHeight = 22;
            // Calculate total height based on number of agents + title + warehouse
            int legendHeight = 45 + ((fleetData.size() + 1) * legendItemHeight);
            int startX = getWidth() - legendWidth - 15; // Align to top right
            int startY = 15;

            // Draw semi-transparent background box
            g2d.setColor(new Color(20, 20, 25, 220));
            g2d.fillRoundRect(startX, startY, legendWidth, legendHeight, 10, 10);
            g2d.setColor(new Color(100, 100, 110));
            g2d.drawRoundRect(startX, startY, legendWidth, legendHeight, 10, 10);

            // Draw Legend Title
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2d.drawString("LIVE FLEET LEGEND", startX + 15, startY + 22);
            g2d.drawLine(startX + 15, startY + 28, startX + legendWidth - 15, startY + 28);

            // Draw Warehouse Entry
            int currentY = startY + 45;
            g2d.setColor(Color.WHITE);
            g2d.fillRect(startX + 15, currentY - 10, 12, 12);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2d.drawString("Warehouse (Depot)", startX + 35, currentY);
            currentY += legendItemHeight;

            // Loop through live fleet data and draw each Agent Entry
            for (Map.Entry<String, AgentData> entry : fleetData.entrySet()) {
                g2d.setColor(entry.getValue().color);
                g2d.fillOval(startX + 15, currentY - 10, 12, 12);

                g2d.setColor(Color.LIGHT_GRAY);
                g2d.drawString(entry.getKey(), startX + 35, currentY);

                currentY += legendItemHeight;
            }
        }
    }

    public void display() { setVisible(true); }
}