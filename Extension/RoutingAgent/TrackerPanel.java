package RoutingAgent.Extension.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackerPanel extends JPanel {
    private Map<String, Point> currentLocs = new HashMap<>();
    private Map<String, List<Point>> actualRoutes = new HashMap<>();
    private Map<String, List<Point>> remainingPaths = new HashMap<>();

    private final Point depot = new Point(50, 50);
    private static final int PADDING = 50;
    private static final int SCALE = 6; // Scales the 100x100 grid

    private final Color[] agentColors = {
            new Color(220, 53, 69), new Color(0, 123, 255), new Color(40, 167, 69),
            new Color(253, 126, 20), new Color(111, 66, 193), new Color(23, 162, 184)
    };

    public TrackerPanel() {
        setBackground(new Color(30, 30, 35));
    }

    public void updateData(Map<String, Point> locs, Map<String, List<Point>> actual, Map<String, List<Point>> remaining) {
        this.currentLocs = locs;
        this.actualRoutes = actual;
        this.remainingPaths = remaining;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Draw Grid Background
        g2d.setColor(new Color(50, 50, 55));
        for (int i = 0; i <= 100; i += 10) {
            g2d.drawLine(PADDING, PADDING + (i * SCALE), PADDING + (100 * SCALE), PADDING + (i * SCALE));
            g2d.drawLine(PADDING + (i * SCALE), PADDING, PADDING + (i * SCALE), PADDING + (100 * SCALE));
        }

        // 2. Draw Warehouse Depot
        g2d.setColor(Color.RED);
        g2d.fillRect((depot.x * SCALE) + PADDING - 8, (depot.y * SCALE) + PADDING - 8, 16, 16);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2d.drawString("DEPOT", (depot.x * SCALE) + PADDING - 15, (depot.y * SCALE) + PADDING - 12);

        int colorIndex = 0;
        for (String name : currentLocs.keySet()) {
            Color agentColor = agentColors[colorIndex % agentColors.length];
            Point liveLoc = currentLocs.get(name);
            List<Point> remaining = remainingPaths.get(name);

            // 3. Draw Remaining Path (Dotted Lines for Ghost Route)
            if (remaining != null && !remaining.isEmpty()) {
                g2d.setColor(new Color(agentColor.getRed(), agentColor.getGreen(), agentColor.getBlue(), 180));
                Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
                g2d.setStroke(dashed);

                Point prev = liveLoc;
                for (Point p : remaining) {
                    int x1 = (prev.x * SCALE) + PADDING;
                    int y1 = (prev.y * SCALE) + PADDING;
                    int x2 = (p.x * SCALE) + PADDING;
                    int y2 = (p.y * SCALE) + PADDING;
                    g2d.drawLine(x1, y1, x2, y2);

                    // Draw Destination Node
                    g2d.fillOval(x2 - 5, y2 - 5, 10, 10);
                    prev = p;
                }
            }

            // 4. Draw Live Agent Blip (The moving truck)
            if (liveLoc != null) {
                int lx = (liveLoc.x * SCALE) + PADDING;
                int ly = (liveLoc.y * SCALE) + PADDING;

                g2d.setColor(Color.WHITE);
                g2d.fillOval(lx - 7, ly - 7, 14, 14);
                g2d.setColor(agentColor);
                g2d.fillOval(lx - 5, ly - 5, 10, 10);

                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2d.drawString(name, lx - 10, ly - 12);
            }
            colorIndex++;
        }
    }
}