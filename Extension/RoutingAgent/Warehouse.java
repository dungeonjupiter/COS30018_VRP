package RoutingAgent.Extension.RoutingAgent;

import java.awt.Color;
import java.awt.Point;

public class Warehouse {

    /** Fixed display colours for up to 3 warehouses. */
    public static final Color[] COLORS = {
            new Color(220,  53,  69),   // WH-0 — red
            new Color( 23, 162, 184),   // WH-1 — cyan
            new Color(253, 126,  20)    // WH-2 — orange
    };

    private final int    id;
    private final int    x, y;
    private final String name;

    public Warehouse(int id, int x, int y) {
        this.id   = id;
        this.x    = x;
        this.y    = y;
        this.name = "WH-" + id;
    }

    public int    getId()   { return id; }
    public int    getX()    { return x; }
    public int    getY()    { return y; }
    public String getName() { return name; }
    public Point  getPos()  { return new Point(x, y); }
    public Color  getColor(){ return COLORS[id % COLORS.length]; }

    @Override public String toString() { return name + "(" + x + "," + y + ")"; }
}
