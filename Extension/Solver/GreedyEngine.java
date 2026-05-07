package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.Map;
import java.util.List;
import java.util.Set;

public class GreedyEngine {

    public RouteState insertNewParcel(Parcel newParcel, RouteState currentState, Map<String, Integer> capacities, Map<Point, Parcel> directory, Set<Point> inVanParcels) {
        RouteState newState = currentState.cloneState();
        double bestCost = Double.MAX_VALUE;
        String bestAgent = null;
        int bestIndex = -1;
        boolean bestNeedsDepot = false;

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
                continue;
            }

            for (int i = 1; i <= route.size(); i++) {
                Point prev = route.get(i - 1);
                Point next = (i == route.size()) ? depot : route.get(i);

                boolean hasVisitedDepot = false;
                for (int k = 0; k < i; k++) {
                    if (route.get(k).equals(depot)) {
                        hasVisitedDepot = true;
                        break;
                    }
                }

                double cost;
                boolean needsNewDepotNode = !hasVisitedDepot;

                // --- FIX 2: Bypassing Depot Detour for In-Van Parcels ---
                if (inVanParcels != null && inVanParcels.contains(newParcel.getDestination())) {
                    needsNewDepotNode = false;
                }

                if (needsNewDepotNode) {
                    cost = prev.distance(depot) + depot.distance(newParcel.getDestination()) + newParcel.getDestination().distance(next) - prev.distance(next);
                } else {
                    cost = prev.distance(newParcel.getDestination()) + newParcel.getDestination().distance(next) - prev.distance(next);
                }

                if (cost < bestCost) {
                    bestCost = cost;
                    bestAgent = agentName;
                    bestIndex = i;
                    bestNeedsDepot = needsNewDepotNode;
                }
            }
        }

        if (bestAgent != null) {
            if (bestNeedsDepot) {
                newState.insertNode(bestAgent, bestIndex, depot);
                newState.insertNode(bestAgent, bestIndex + 1, newParcel.getDestination());
            } else {
                newState.insertNode(bestAgent, bestIndex, newParcel.getDestination());
            }
        } else {
            System.err.println("GREEDY ERROR: No capacity available across entire fleet for parcel at " + newParcel.getDestination());
        }

        return newState;
    }
}