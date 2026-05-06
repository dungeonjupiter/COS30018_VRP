package RoutingAgent.vrp.model;

public class Location {
    public final int id;
    public final double x;
    public final double y;

    public Location(int id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public double distanceTo(Location other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceTo(double ox, double oy) {
        double dx = this.x - ox;
        double dy = this.y - oy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
