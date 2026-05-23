package RoutingAgent.Extension.Solver;

import java.awt.Point;
import java.io.Serializable;

public class Parcel implements Serializable {
    private static final Point DEFAULT_WAREHOUSE = new Point(50, 50);

    private String id;
    private Point destination;
    private int demand;
    private Point originWarehouse;

    public Parcel(String id, int x, int y, int demand) {
        this(id, x, y, demand, DEFAULT_WAREHOUSE);
    }

    public Parcel(String id, int x, int y, int demand, Point originWarehouse) {
        this.id = id;
        this.destination = new Point(x, y);
        this.demand = demand;
        this.originWarehouse = originWarehouse != null ? new Point(originWarehouse) : DEFAULT_WAREHOUSE;
    }

    public String getId() { return id; }
    public Point getDestination() { return destination; }
    public int getDemand() { return demand; }
    public Point getOriginWarehouse() { return originWarehouse; }
}
