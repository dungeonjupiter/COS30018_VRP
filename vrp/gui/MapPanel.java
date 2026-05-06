package RoutingAgent.vrp.gui;

import RoutingAgent.vrp.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

public class MapPanel extends JPanel {

    private WorldState worldState;
    private Timer animTimer;

    private static final int PADDING = 50;
    private static final int WAREHOUSE_SIZE = 18;
    private static final int CUSTOMER_RADIUS = 6;

    private static final Color BG_COLOR        = new Color(248, 248, 250);
    private static final Color GRID_COLOR      = new Color(225, 225, 230);
    private static final Color WAREHOUSE_COLOR = new Color(210, 40, 40);
    private static final Color CUSTOMER_COLOR  = new Color(50, 100, 210);
    private static final Color LABEL_COLOR     = new Color(30, 30, 30);

    static final Color[] AGENT_COLORS = {
        new Color(230, 120,  20),  // orange
        new Color( 20, 160,  80),  // green
        new Color(160,  20, 200),  // purple
        new Color(200, 160,   0),  // gold
        new Color( 20, 180, 200),  // teal
    };

    public MapPanel() {
        setBackground(BG_COLOR);
    }

    public void setWorldState(WorldState state) {
        this.worldState = state;
        repaint();
    }

    public void startAnimation() {
        if (animTimer != null && animTimer.isRunning()) return;
        animTimer = new Timer(50, e -> repaint());
        animTimer.start();
    }

    public void stopAnimation() {
        if (animTimer != null) animTimer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (worldState == null || worldState.warehouse == null) {
            drawPlaceholder(g2);
            return;
        }

        double[] b = computeBounds();
        double[][] anim = computeAnimPositions();
        drawGrid(g2, b);
        drawRoutes(g2, b, anim);
        drawAgents(g2, b, anim);  // on top of routes, beneath node labels
        drawCustomers(g2, b);
        drawWarehouse(g2, b);
        drawLegend(g2);
    }

    // ── animation ────────────────────────────────────────────────────────────

    /** Returns [n][2] array of interpolated {x, y} per agent at the current instant. */
    private double[][] computeAnimPositions() {
        int n = worldState.agents.size();
        double[][] pos = new double[n][2];
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            AgentState a = worldState.agents.get(i);
            double t = (a.moveDurationMs > 0)
                    ? Math.min(1.0, (double)(now - a.moveStartMs) / a.moveDurationMs)
                    : 1.0;
            pos[i][0] = a.prevX + (a.currentX - a.prevX) * t;
            pos[i][1] = a.prevY + (a.currentY - a.prevY) * t;
        }
        return pos;
    }

    // ── coordinate transform ─────────────────────────────────────────────────

    private double[] computeBounds() {
        double minX = worldState.warehouse.x, maxX = minX;
        double minY = worldState.warehouse.y, maxY = minY;

        for (CustomerNode c : worldState.customers) {
            minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
            minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
        }

        double mx = Math.max(5, (maxX - minX) * 0.10);
        double my = Math.max(5, (maxY - minY) * 0.10);
        return new double[]{minX - mx, maxX + mx, minY - my, maxY + my};
    }

    int sx(double cx, double[] b) {
        double range = b[1] - b[0];
        if (range == 0) return getWidth() / 2;
        return PADDING + (int) ((cx - b[0]) / range * (getWidth() - 2 * PADDING));
    }

    int sy(double cy, double[] b) {
        double range = b[3] - b[2];
        if (range == 0) return getHeight() / 2;
        return getHeight() - PADDING - (int) ((cy - b[2]) / range * (getHeight() - 2 * PADDING));
    }

    // ── draw helpers ─────────────────────────────────────────────────────────

    private void drawGrid(Graphics2D g2, double[] b) {
        g2.setColor(GRID_COLOR);
        g2.setStroke(new BasicStroke(0.5f));
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double cx = b[0] + (b[1] - b[0]) * i / steps;
            double cy = b[2] + (b[3] - b[2]) * i / steps;
            int x = sx(cx, b), y = sy(cy, b);
            g2.drawLine(x, PADDING, x, getHeight() - PADDING);
            g2.drawLine(PADDING, y, getWidth() - PADDING, y);
        }
    }

    private void drawRoutes(Graphics2D g2, double[] b, double[][] anim) {
        if (worldState.agents.isEmpty()) return;
        for (int i = 0; i < worldState.agents.size(); i++) {
            AgentState agent = worldState.agents.get(i);
            if (agent.remainingRoute.isEmpty()) continue;

            Color color = AGENT_COLORS[i % AGENT_COLORS.length];
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Draw current leg: animated position → currentX/Y (already popped from route)
            int animPx = sx(anim[i][0], b), animPy = sy(anim[i][1], b);
            int curPx  = sx(agent.currentX, b), curPy = sy(agent.currentY, b);
            g2.drawLine(animPx, animPy, curPx, curPy);
            drawArrowHead(g2, animPx, animPy, curPx, curPy);

            // Draw remaining planned legs
            int prevX = curPx, prevY = curPy;
            for (RouteStop stop : agent.remainingRoute) {
                int nx = sx(stop.target.x, b);
                int ny = sy(stop.target.y, b);
                g2.drawLine(prevX, prevY, nx, ny);
                drawArrowHead(g2, prevX, prevY, nx, ny);
                prevX = nx;
                prevY = ny;
            }
        }
    }

    private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 10) return;
        double ux = dx / len, uy = dy / len;
        // Arrow at midpoint, pointing in travel direction
        int mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
        int arm = 7;
        double cosA = Math.cos(Math.toRadians(28)), sinA = Math.sin(Math.toRadians(28));
        int ax1 = mx - (int) (arm * (ux * cosA - uy * sinA));
        int ay1 = my - (int) (arm * (uy * cosA + ux * sinA));
        int ax2 = mx - (int) (arm * (ux * cosA + uy * sinA));
        int ay2 = my - (int) (arm * (uy * cosA - ux * sinA));
        g2.drawLine(mx, my, ax1, ay1);
        g2.drawLine(mx, my, ax2, ay2);
    }

    private void drawAgents(Graphics2D g2, double[] b, double[][] anim) {
        for (int i = 0; i < worldState.agents.size(); i++) {
            AgentState a = worldState.agents.get(i);
            // Only render while the agent has pending work
            if (a.remainingRoute.isEmpty() && a.status == AgentStatus.STANDBY) continue;

            Color color = AGENT_COLORS[i % AGENT_COLORS.length];
            int x = sx(anim[i][0], b);
            int y = sy(anim[i][1], b);
            int r = 9;

            g2.setColor(color);
            g2.fillOval(x - r, y - r, r * 2, r * 2);

            g2.setColor(color.darker());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(x - r, y - r, r * 2, r * 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            g2.drawString(a.name(), x - 8, y + 3);
        }
    }

    private void drawCustomers(Graphics2D g2, double[] b) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        g2.setFont(font);
        for (CustomerNode c : worldState.customers) {
            int x = sx(c.x, b), y = sy(c.y, b);
            int r = CUSTOMER_RADIUS;

            g2.setColor(CUSTOMER_COLOR);
            g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));

            g2.setColor(CUSTOMER_COLOR.darker());
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));

            g2.setColor(LABEL_COLOR);
            g2.drawString(String.valueOf(c.id), x + r + 2, y + 4);
        }
    }

    private void drawWarehouse(Graphics2D g2, double[] b) {
        int x = sx(worldState.warehouse.x, b), y = sy(worldState.warehouse.y, b);
        int half = WAREHOUSE_SIZE / 2;

        g2.setColor(WAREHOUSE_COLOR);
        g2.fill(new Rectangle2D.Double(x - half, y - half, WAREHOUSE_SIZE, WAREHOUSE_SIZE));

        g2.setColor(WAREHOUSE_COLOR.darker());
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Rectangle2D.Double(x - half, y - half, WAREHOUSE_SIZE, WAREHOUSE_SIZE));

        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        g2.drawString("WH", x - 8, y + 4);
    }

    private void drawLegend(Graphics2D g2) {
        int lx = PADDING + 4, ly = getHeight() - PADDING + 12;
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        g2.setColor(WAREHOUSE_COLOR);
        g2.fillRect(lx, ly, 10, 10);
        g2.setColor(LABEL_COLOR);
        g2.drawString("Warehouse", lx + 14, ly + 9);

        lx += 90;
        g2.setColor(CUSTOMER_COLOR);
        g2.fillOval(lx, ly + 1, 8, 8);
        g2.setColor(LABEL_COLOR);
        g2.drawString("Customer", lx + 12, ly + 9);

        // Agent route color swatches (only shown after planning)
        if (worldState != null) {
            lx += 80;
            for (int i = 0; i < worldState.agents.size(); i++) {
                AgentState a = worldState.agents.get(i);
                if (a.remainingRoute.isEmpty()) continue;
                Color c = AGENT_COLORS[i % AGENT_COLORS.length];
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawLine(lx, ly + 5, lx + 14, ly + 5);
                g2.setColor(LABEL_COLOR);
                g2.drawString(a.name(), lx + 18, ly + 9);
                lx += 52;
            }
        }
    }

    private void drawPlaceholder(Graphics2D g2) {
        g2.setColor(new Color(170, 170, 170));
        g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 15));
        String msg = "Load a map file to begin";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
    }
}
