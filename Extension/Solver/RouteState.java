package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteState {
    private Map<String, List<Point>> routes = new HashMap<>();

    public RouteState() {}

    public RouteState cloneState() {
        RouteState copy = new RouteState();
        for (Map.Entry<String, List<Point>> entry : this.routes.entrySet()) {
            copy.routes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    public Map<String, List<Point>> getRoutes() { return routes; }

    public void addAgent(String agentName, Point currentLocation) {
        List<Point> initialRoute = new ArrayList<>();
        initialRoute.add(currentLocation);
        routes.put(agentName, initialRoute);
    }

    public void insertNode(String agentName, int index, Point node) {
        routes.get(agentName).add(index, node);
    }

}