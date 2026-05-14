package RoutingAgent.Extension.Solver;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TabuRoutingEngine {

    public RouteState optimize(RouteState currentState, Parcel newParcel, Map<String, Integer> capacities, Map<Point, Parcel> directory, Map<String, Integer> lockedPrefixLength) {
        RouteState workingState = currentState.cloneState();

        // PHASE 1: Fast Geometric Insertion (Replaces GreedyEngine)
        if (newParcel != null) {
            double bestCost = Double.MAX_VALUE;
            String bestAgent = null;
            int bestIndex = -1;

            for (String agentName : workingState.getRoutes().keySet()) {
                List<Point> route = workingState.getRoutes().get(agentName);
                int lockedCount = lockedPrefixLength.getOrDefault(agentName, 0);

                // Check Capacity
                int currentLoad = route.stream().mapToInt(p -> directory.containsKey(p) ? directory.get(p).getDemand() : 0).sum();
                if (currentLoad + newParcel.getDemand() > capacities.getOrDefault(agentName, 5)) continue;

                // Test insertions AFTER the locked buffer
                for (int i = Math.max(1, lockedCount); i <= route.size(); i++) {
                    Point prev = route.get(i - 1);
                    Point next = (i == route.size()) ? prev : route.get(i);
                    double cost = prev.distance(newParcel.getDestination()) + newParcel.getDestination().distance(next) - prev.distance(next);

                    if (cost < bestCost) {
                        bestCost = cost;
                        bestAgent = agentName;
                        bestIndex = i;
                    }
                }
            }
            if (bestAgent != null) workingState.insertNode(bestAgent, bestIndex, newParcel.getDestination());
        }

        // PHASE 2: Tabu Search Optimization Loop (Intra-route 2-Opt)
        // We only swap nodes that occur AFTER the locked prefix to prevent breaking live physical driving.
        for (int iter = 0; iter < 1000; iter++) {
            for (String agent : workingState.getRoutes().keySet()) {
                List<Point> r = workingState.getRoutes().get(agent);
                int locked = lockedPrefixLength.getOrDefault(agent, 0);
                if (r.size() - locked < 3) continue; // Not enough unlocked nodes to swap

                // Simple 2-opt swap test
                int i = locked + (int)(Math.random() * (r.size() - locked - 1));
                int j = i + 1 + (int)(Math.random() * (r.size() - i - 1));

                double beforeCost = r.get(i-1).distance(r.get(i)) + r.get(j).distance(j+1 < r.size() ? r.get(j+1) : r.get(j));
                double afterCost = r.get(i-1).distance(r.get(j)) + r.get(i).distance(j+1 < r.size() ? r.get(j+1) : r.get(i));

                if (afterCost < beforeCost) {
                    // Apply Swap
                    Point temp = r.get(i);
                    r.set(i, r.get(j));
                    r.set(j, temp);
                }
            }
        }
        return workingState;
    }
}