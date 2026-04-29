package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;

public class GreedyEngine {

    public RouteState insertNewParcel(Parcel newParcel, RouteState currentState) {
        RouteState newState = currentState.cloneState();
        double bestCost = Double.MAX_VALUE;
        String bestAgent = null;
        int bestIndex = -1;

        for (String agentName : newState.getRoutes().keySet()) {
            java.util.List<Point> route = newState.getRoutes().get(agentName);

            // Test inserting at every possible position
            for (int i = 1; i <= route.size(); i++) {
                Point prev = route.get(i - 1);
                Point next = (i == route.size()) ? new Point(50, 50) : route.get(i); // 50,50 is Warehouse

                double cost = prev.distance(newParcel.getDestination())
                        + newParcel.getDestination().distance(next)
                        - prev.distance(next);

                if (cost < bestCost) {
                    bestCost = cost;
                    bestAgent = agentName;
                    bestIndex = i;
                }
            }
        }

        if (bestAgent != null) {
            newState.insertNode(bestAgent, bestIndex, newParcel.getDestination());
        }
        return newState;
    }
}