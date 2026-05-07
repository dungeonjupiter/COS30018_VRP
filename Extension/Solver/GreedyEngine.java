package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.Map;
import java.util.List;

public class GreedyEngine {

    public RouteState insertNewParcel(Parcel newParcel, RouteState currentState, Map<String, Integer> capacities, Map<Point, Parcel> directory) {
        RouteState newState = currentState.cloneState();
        double bestCost = Double.MAX_VALUE;
        String bestAgent = null;
        int bestIndex = -1;

        Point depot = new Point(50, 50);

        for (String agentName : newState.getRoutes().keySet()) {
            List<Point> route = newState.getRoutes().get(agentName);

            // --- CAPACITY CHECK ---
            int currentLoad = 0;
            for (int i = 1; i < route.size(); i++) {
                Parcel p = directory.get(route.get(i));
                if (p != null) currentLoad += p.getDemand();
            }

            int agentMaxCap = capacities.getOrDefault(agentName, 5);
            if (currentLoad + newParcel.getDemand() > agentMaxCap) {
                continue; // Van is full! Skip this agent.
            }

            // Test inserting at every possible pure mathematical position
            for (int i = 1; i <= route.size(); i++) {
                Point prev = route.get(i - 1);
                Point next = (i == route.size()) ? depot : route.get(i);

                double cost = prev.distance(newParcel.getDestination()) + newParcel.getDestination().distance(next) - prev.distance(next);

                if (cost < bestCost) {
                    bestCost = cost;
                    bestAgent = agentName;
                    bestIndex = i;
                }
            }
        }

        if (bestAgent != null) {
            newState.insertNode(bestAgent, bestIndex, newParcel.getDestination());
        } else {
            System.err.println("GREEDY ERROR: No capacity available across entire fleet for parcel at " + newParcel.getDestination());
        }

        return newState;
    }
}