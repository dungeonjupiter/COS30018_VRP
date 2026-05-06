package RoutingAgent.vrp.model;

public class Warehouse extends Location {

    public Warehouse(double x, double y) {
        super(0, x, y);
    }

    @Override
    public String toString() {
        return "Warehouse(" + x + ", " + y + ")";
    }
}
