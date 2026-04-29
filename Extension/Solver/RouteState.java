package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RouteState {
    // Maps Agent Name -> Their current list of planned stops
    private Map<String, List<Point>> routes = new HashMap<>();
    private Random random = new Random();

    public RouteState() {}

    // Clone method is CRITICAL for algorithms so they don't overwrite each other
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

    public double getTotalDistance() {
        double total = 0;
        for (List<Point> route : routes.values()) {
            for (int i = 0; i < route.size() - 1; i++) {
                total += route.get(i).distance(route.get(i+1));
            }
        }
        return total;
    }

    // --- ALNS Destroy Methods ---
    public void randomRemoval(int count, List<Point> unassigned) {
        for (int i = 0; i < count; i++) {
            List<String> keys = new ArrayList<>(routes.keySet());
            String randomAgent = keys.get(random.nextInt(keys.size()));
            List<Point> route = routes.get(randomAgent);

            // Don't remove their starting position (index 0)
            if (route.size() > 1) {
                int removeIdx = 1 + random.nextInt(route.size() - 1);
                unassigned.add(route.remove(removeIdx));
            }
        }
    }
}