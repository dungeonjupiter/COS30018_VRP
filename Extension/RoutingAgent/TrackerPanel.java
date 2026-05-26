package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import RoutingAgent.Extension.Solver.TabuRoutingEngine;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * TrackerPanel — Live map panel.
 *
 * Changes from original:
 *   - Accepts List<Warehouse> and draws each with its own colour + label.
 *   - Each agent gets a unique colour; legend shows warehouse via [WH-x].
 *   - Legend shows warehouses first (with parcel count), then agents with [WH-x].
 *   - Warehouse nodes in routes are skipped from customer-dot drawing (same
 *     logic as the original depot skip, generalised for multiple depots).
 *   - updateData() has a new overload that accepts warehouses + agentWhIds.
 *     The old single-parameter signature still works (backward compatible).
 *
 * All original drawing methods (drawRouteLine, drawArrow, grid) are UNCHANGED.
 */
public class TrackerPanel extends JPanel {

    // ── Data ──────────────────────────────────────────────────────────────────
    private Map<String, Point>       currentLocs    = new HashMap<>();
    private Map<String, List<Point>> remainingPaths = new HashMap<>();
    private Map<String, Integer>     capacities     = new HashMap<>();
    private Map<String, List<Point>> initialPlanned = new HashMap<>();
    private List<Point>              previewNodes   = new ArrayList<>();
    private Map<Point, Parcel>       parcelDir      = new HashMap<>();
    private List<Warehouse>          warehouses     = new ArrayList<>();
    private Map<String, Integer>     agentWhIds     = new HashMap<>();

    // ── Layout constants (unchanged) ──────────────────────────────────────────
    private static final int OFFSET_X = 220;
    private static final int OFFSET_Y = 60;
    private static final int SCALE    = 6;

    private static final TabuRoutingEngine CAPACITY_ENGINE = new TabuRoutingEngine();

    /** Distinct colours per agent (not shared by warehouse). */
    private static final Color[] AGENT_PALETTE = {
            new Color(  0, 123, 255),
            new Color(220,  53,  69),
            new Color( 40, 167,  69),
            new Color(253, 126,  20),
            new Color(111,  66, 193),
            new Color( 23, 162, 184),
            new Color(255, 193,   7),
            new Color(232,  62, 140),
            new Color(102, 205, 170),
            new Color(153, 102, 255),
            new Color(255, 159,  64),
            new Color( 72, 201, 176)
    };

    public TrackerPanel() { setBackground(new Color(30, 30, 35)); }

    // ── updateData overloads ──────────────────────────────────────────────────

    /** Multi-warehouse version — called by new MainWindow. */
    public void updateData(Map<String, Point> locs,
                           Map<String, List<Point>> actual,
                           Map<String, List<Point>> remaining,
                           Map<String, Integer> caps,
                           Map<String, List<Point>> initial,
                           List<Point> unassigned,
                           Map<Point, Parcel> directory,
                           List<Warehouse> whs,
                           Map<String, Integer> agentWarehouseIds) {
        this.currentLocs    = locs;
        this.remainingPaths = remaining;
        this.capacities     = caps;
        this.initialPlanned = initial;
        this.previewNodes   = unassigned;
        this.parcelDir      = directory;
        this.warehouses     = (whs != null) ? whs : new ArrayList<>();
        this.agentWhIds     = (agentWarehouseIds != null) ? agentWarehouseIds : new HashMap<>();
        repaint();
    }

    /** Single-warehouse backward-compatible overload. */
    public void updateData(Map<String, Point> locs,
                           Map<String, List<Point>> actual,
                           Map<String, List<Point>> remaining,
                           Map<String, Integer> caps,
                           Map<String, List<Point>> initial,
                           List<Point> unassigned,
                           Map<Point, Parcel> directory) {
        updateData(locs, actual, remaining, caps, initial, unassigned, directory,
                List.of(new Warehouse(0, 50, 50)), new HashMap<>());
    }

    // ── Paint ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGridAndWarehouses(g2d);  // replaces original drawGridAndDepot
        drawLegend(g2d);

        // Preview nodes (unchanged)
        if (previewNodes != null && !previewNodes.isEmpty()) {
            for (Point p : previewNodes) {
                // Bug 1 fix: never draw a customer dot at a warehouse position.
                // Without this check, warehouse nodes appear counted as customers
                // on the map and in any size-based display logic.
                if (isWarehousePoint(p)) continue;
                int px = p.x * SCALE + OFFSET_X;
                int py = p.y * SCALE + OFFSET_Y;
                g2d.setColor(new Color(180, 180, 180));
                g2d.fillOval(px - 6, py - 6, 12, 12);
                g2d.setColor(Color.WHITE);
                g2d.drawOval(px - 7, py - 7, 14, 14);
            }
        }

        // Agent routes — one unique colour per DA (sorted for stable assignment)
        List<String> agentNames = sortedAgentNames();
        for (int i = 0; i < agentNames.size(); i++) {
            String name = agentNames.get(i);
            Color c     = agentColor(name, i);
            Point live = currentLocs.get(name);
            List<Point> rem       = remainingPaths.get(name);
            List<Point> initRoute = initialPlanned.get(name);

            if (rem != null && !rem.isEmpty()) {
                Point prev = live;
                for (Point p : rem) {
                    boolean isInitialNode = (initRoute != null && initRoute.contains(p));
                    drawRouteLine(g2d, prev, p, c, isInitialNode);

                    // Skip warehouse nodes — don't draw a customer dot there
                    if (!isWarehousePoint(p)) {
                        int px = p.x * SCALE + OFFSET_X;
                        int py = p.y * SCALE + OFFSET_Y;
                        g2d.setColor(c);
                        g2d.fillOval(px - 7, py - 7, 14, 14);
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(px - 2, py - 2, 4, 4);
                    }
                    prev = p;
                }
            }

            // Agent marker — nudge apart when several DAs share the same coordinates
            if (live != null) {
                Point screen = screenPositionWithPeerOffset(name, live);
                int lx = screen.x;
                int ly = screen.y;
                g2d.setColor(Color.WHITE);
                g2d.fillRect(lx - 8, ly - 8, 16, 16);
                g2d.setColor(c);
                g2d.fillRect(lx - 6, ly - 6, 12, 12);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 9));
                g2d.drawString(name, lx + 8, ly - 4);
            }
        }
    }

    // ── Grid + Warehouses (replaces original drawGridAndDepot) ────────────────

    private void drawGridAndWarehouses(Graphics2D g2d) {
        // Grid (unchanged)
        g2d.setColor(new Color(50, 50, 55));
        for (int i = 0; i <= 100; i += 10) {
            g2d.drawLine(OFFSET_X, OFFSET_Y + i * SCALE,
                    OFFSET_X + 100 * SCALE, OFFSET_Y + i * SCALE);
            g2d.drawLine(OFFSET_X + i * SCALE, OFFSET_Y,
                    OFFSET_X + i * SCALE,   OFFSET_Y + 100 * SCALE);
        }

        // Draw each warehouse
        for (Warehouse wh : warehouses) {
            int    px = wh.getX() * SCALE + OFFSET_X;
            int    py = wh.getY() * SCALE + OFFSET_Y;
            Color  c  = wh.getColor();

            // Soft glow
            g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
            g2d.fillOval(px - 18, py - 18, 36, 36);

            // Filled square
            g2d.setColor(c);
            g2d.fillRect(px - 10, py - 10, 20, 20);

            // White border
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRect(px - 10, py - 10, 20, 20);
            g2d.setStroke(new BasicStroke(1f));

            // Label above warehouse
            g2d.setColor(c);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2d.drawString(wh.getName(), px - 10, py - 13);
        }
    }

    // ── Legend ────────────────────────────────────────────────────────────────

    private void drawLegend(Graphics2D g2d) {
        int numAgents = currentLocs.size();
        int numWh     = Math.max(1, warehouses.size());
        int boxHeight = 30 + (numWh * 20) + 10 + (numAgents * 20);

        g2d.setColor(new Color(20, 20, 25, 210));
        g2d.fillRoundRect(10, 10, 200, boxHeight, 15, 15);
        g2d.setColor(new Color(80, 80, 90));
        g2d.drawRoundRect(10, 10, 200, boxHeight, 15, 15);

        int ly = 30;
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2d.drawString("LIVE LEGEND:", 20, ly);

        // Warehouses section
        for (Warehouse wh : warehouses) {
            ly += 20;
            long parcelCount = parcelDir.values().stream()
                    .filter(p -> p.getSourceWarehouseId() == wh.getId())
                    .count();
            g2d.setColor(wh.getColor());
            g2d.fillRect(20, ly - 9, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2d.drawString(wh.getName() + " (" + wh.getX() + "," + wh.getY() + ")"
                    + "  p:" + parcelCount, 34, ly);
        }

        // Separator
        ly += 10;
        g2d.setColor(new Color(80, 80, 90));
        g2d.drawLine(15, ly, 205, ly);

        // Agents section (unique colour per agent)
        List<String> agentNames = sortedAgentNames();
        for (int idx = 0; idx < agentNames.size(); idx++) {
            String name = agentNames.get(idx);
            ly += 20;
            Color c = agentColor(name, idx);
            int currentLoad = peakOnboardLoad(name, remainingPaths.get(name));
            String whTag = Warehouse.displayName(agentWhIds.getOrDefault(name, 0));
            g2d.setColor(c);
            g2d.fillRect(20, ly - 9, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2d.drawString(name + " [" + whTag + "]  "
                    + currentLoad + "/" + capacities.getOrDefault(name, 0), 34, ly);
        }
    }

    // ── Original drawing helpers (UNCHANGED) ──────────────────────────────────

    private void drawRouteLine(Graphics2D g2d, Point p1, Point p2,
                               Color c, boolean isInitial) {
        int x1 = p1.x * SCALE + OFFSET_X, y1 = p1.y * SCALE + OFFSET_Y;
        int x2 = p2.x * SCALE + OFFSET_X, y2 = p2.y * SCALE + OFFSET_Y;

        if (isInitial) g2d.setStroke(new BasicStroke(3.5f));
        else           g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL, 0, new float[]{8}, 0));

        g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));
        g2d.drawLine(x1, y1, x2, y2);
        drawArrow(g2d, x1, y1, x2, y2);
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double  angle    = Math.atan2(y2 - y1, x2 - x1);
        int     midX     = (x1 + x2) / 2;
        int     midY     = (y1 + y2) / 2;
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 5);
        arrowHead.addPoint(-10, -5);
        arrowHead.addPoint(10, -5);
        java.awt.geom.AffineTransform tx = new java.awt.geom.AffineTransform();
        tx.translate(midX, midY);
        tx.rotate(angle - Math.PI / 2.0);
        g2.fill(tx.createTransformedShape(arrowHead));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True if the point is any warehouse location. */
    private boolean isWarehousePoint(Point p) {
        for (Warehouse wh : warehouses) {
            if (wh.getPos().equals(p)) return true;
        }
        return false;
    }

    /** Stable sort so colours do not swap between repaints. */
    private List<String> sortedAgentNames() {
        List<String> names = new ArrayList<>(currentLocs.keySet());
        names.sort((a, b) -> {
            int na = parseAgentNumber(a);
            int nb = parseAgentNumber(b);
            if (na >= 0 && nb >= 0) return Integer.compare(na, nb);
            if (na >= 0) return -1;
            if (nb >= 0) return 1;
            return a.compareTo(b);
        });
        return names;
    }

    private static int parseAgentNumber(String name) {
        if (name != null && name.startsWith("DA")) {
            try {
                return Integer.parseInt(name.substring(2));
            } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    /** Peak parcels onboard (matches solver — resets only at agent home warehouse). */
    private int peakOnboardLoad(String agentName, List<Point> remaining) {
        int cap = capacities.getOrDefault(agentName, 5);
        if (remaining == null || remaining.isEmpty()) return 0;
        Point home = homeWarehousePos(agentName);
        List<Point> route = new ArrayList<>();
        route.add(home);
        route.addAll(remaining);
        return CAPACITY_ENGINE.peakRouteLoad(route, parcelDir, cap, Set.of(), Set.of(home));
    }

    private Point homeWarehousePos(String agentName) {
        int whId = agentWhIds.getOrDefault(agentName, 0);
        for (Warehouse wh : warehouses) {
            if (wh.getId() == whId) return wh.getPos();
        }
        return new Point(50, 50);
    }

    /** Screen offset when multiple agents share the same map cell. */
    private Point screenPositionWithPeerOffset(String agentName, Point mapLoc) {
        int baseX = mapLoc.x * SCALE + OFFSET_X;
        int baseY = mapLoc.y * SCALE + OFFSET_Y;
        List<String> peers = new ArrayList<>();
        for (String other : sortedAgentNames()) {
            Point o = currentLocs.get(other);
            if (o != null && o.equals(mapLoc)) peers.add(other);
        }
        if (peers.size() <= 1) return new Point(baseX, baseY);
        int idx = peers.indexOf(agentName);
        if (idx < 0) idx = 0;
        double angle = (2.0 * Math.PI * idx) / peers.size();
        int r = 10;
        return new Point(baseX + (int) Math.round(r * Math.cos(angle)),
                baseY + (int) Math.round(r * Math.sin(angle)));
    }

    /** Unique colour per agent; warehouse shown in legend via [WH-x] only. */
    private Color agentColor(String agentName, int paletteIndex) {
        int idx = paletteIndex;
        int parsed = parseAgentNumber(agentName);
        if (parsed > 0) idx = parsed - 1;
        if (idx < AGENT_PALETTE.length) return AGENT_PALETTE[idx % AGENT_PALETTE.length];
        float hue = (idx * 0.381966f) % 1.0f;
        return Color.getHSBColor(hue, 0.72f, 0.95f);
    }
}
