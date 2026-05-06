package RoutingAgent.vrp.model;

public class Parcel {
    public final int id;
    public final CustomerNode destination;
    public ParcelStatus status;
    public int assignedAgentId;

    public Parcel(int id, CustomerNode destination) {
        this.id = id;
        this.destination = destination;
        this.status = ParcelStatus.UNASSIGNED;
        this.assignedAgentId = -1;
    }

    @Override
    public String toString() {
        return "P" + id + "→C" + destination.id;
    }
}
