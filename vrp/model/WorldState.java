package RoutingAgent.vrp.model;

import java.util.ArrayList;
import java.util.List;

public class WorldState {
    public Warehouse warehouse;
    public final List<CustomerNode> customers = new ArrayList<>();
    public final List<Parcel> parcels = new ArrayList<>();
    public final List<AgentState> agents = new ArrayList<>();

    public CustomerNode findCustomer(int id) {
        return customers.stream().filter(c -> c.id == id).findFirst().orElse(null);
    }

    public AgentState findAgent(int id) {
        return agents.stream().filter(a -> a.id == id).findFirst().orElse(null);
    }

    public long deliveredCount() {
        return parcels.stream().filter(p -> p.status == ParcelStatus.DELIVERED).count();
    }
}
