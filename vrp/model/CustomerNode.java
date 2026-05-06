package RoutingAgent.vrp.model;

public class CustomerNode extends Location {

    public CustomerNode(int id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String toString() {
        return "C" + id + "(" + x + ", " + y + ")";
    }
}
