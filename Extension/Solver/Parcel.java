package RoutingAgent.Extension.Solver;

import java.awt.Point;
import java.io.Serializable;

/**
 * Parcel — delivery task model.
 *
 * Added field vs original: {@code sourceWarehouseId}
 *   - Default 0, so all single-warehouse code still compiles unchanged.
 *   - Used by MRA to determine which warehouse a DA must visit before delivery.
 *   - TabuRoutingEngine never reads this field, so solver logic is untouched.
 */
public class Parcel implements Serializable {

    private String id;
    private Point  destination;
    private int    demand;
    private int    sourceWarehouseId = 0;   // NEW — which warehouse holds this parcel

    /** Original constructor — sourceWarehouseId defaults to 0. */
    public Parcel(String id, int x, int y, int demand) {
        this.id          = id;
        this.destination = new Point(x, y);
        this.demand      = demand;
    }

    /** New constructor — specify source warehouse explicitly. */
    public Parcel(String id, int x, int y, int demand, int sourceWarehouseId) {
        this(id, x, y, demand);
        this.sourceWarehouseId = sourceWarehouseId;
    }

    public String getId()                { return id; }
    public Point  getDestination()       { return destination; }
    public int    getDemand()            { return demand; }
    public int    getSourceWarehouseId() { return sourceWarehouseId; }

    public void setSourceWarehouseId(int id) { this.sourceWarehouseId = id; }
}
