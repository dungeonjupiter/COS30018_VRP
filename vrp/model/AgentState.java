package RoutingAgent.vrp.model;

import java.util.ArrayList;
import java.util.List;

public class AgentState {
    public final int id;
    public final int capacity;
    public final Warehouse homeWarehouse;

    public double currentX;
    public double currentY;
    public AgentStatus status;

    // Animation state — written by DeliveryAgent before each move, read by MapPanel on EDT
    public double prevX;
    public double prevY;
    public long moveStartMs;    // System.currentTimeMillis() when current leg began
    public long moveDurationMs; // expected travel time for current leg in ms

    // Parcels physically loaded in the vehicle — cannot be reassigned on reroute
    public final List<Parcel> inVehicle = new ArrayList<>();

    // Upcoming stops yet to be visited
    public final List<RouteStop> remainingRoute = new ArrayList<>();

    public AgentState(int id, int capacity, Warehouse homeWarehouse) {
        this.id = id;
        this.capacity = capacity;
        this.homeWarehouse = homeWarehouse;
        this.currentX = homeWarehouse.x;
        this.currentY = homeWarehouse.y;
        this.prevX = homeWarehouse.x;
        this.prevY = homeWarehouse.y;
        this.status = AgentStatus.STANDBY;
        this.moveStartMs = 0;
        this.moveDurationMs = 0;
    }

    public int freeCapacity() {
        return capacity - inVehicle.size();
    }

    public String name() {
        return "DA" + id;
    }

    @Override
    public String toString() {
        return name() + "[cap=" + capacity + ", loaded=" + inVehicle.size() + ", status=" + status + "]";
    }
}
