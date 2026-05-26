package RoutingAgent.Extension.Solver;

import java.awt.Point;
import java.io.Serializable;

// Must implement Serializable so JADE can send it in messages
public class Parcel implements Serializable {
    private String id;
    private Point destination;
    private int demand;

    public Parcel(String id, int x, int y, int demand) {
        this.id = id;
        this.destination = new Point(x, y);
        this.demand = demand;
    }

    public String getId() { return id; }
    public Point getDestination() { return destination; }
    public int getDemand() { return demand; }
}
