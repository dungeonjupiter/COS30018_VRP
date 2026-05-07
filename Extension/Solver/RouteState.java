package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RouteState {
    private Map<String, List<Point>> routes = new HashMap<>();
    private Random random = new Random();
    private Point depot = new Point(50, 50);

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
        int attempts = 0;
        int removed = 0;

        while (removed < count && attempts < 50) {
            attempts++;
            List<String> keys = new ArrayList<>(routes.keySet());
            String randomAgent = keys.get(random.nextInt(keys.size()));
            List<Point> route = routes.get(randomAgent);

            if (route.size() > 1) {
                int removeIdx = 1 + random.nextInt(route.size() - 1);

                // FIX 1: PEEK at the node first!
                Point p = route.get(removeIdx);

                // Only rip it out of the route if it is an actual customer parcel
                if (!p.equals(depot)) {
                    route.remove(removeIdx);
                    unassigned.add(p);
                    removed++;
                }
            }
        }
    }
}