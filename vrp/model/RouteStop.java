package RoutingAgent.vrp.model;

public class RouteStop {
    public final StopType type;
    public final Location target;
    public final Parcel parcel;

    public RouteStop(StopType type, Location target, Parcel parcel) {
        this.type = type;
        this.target = target;
        this.parcel = parcel;
    }

    public RouteStop(StopType type, Location target) {
        this(type, target, null);
    }

    @Override
    public String toString() {
        return type + "@" + target;
    }
}
