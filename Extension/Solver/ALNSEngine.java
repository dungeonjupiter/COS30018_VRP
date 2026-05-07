package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ALNSEngine {

    public RouteState optimize(RouteState startState, long timeLimitMs, Map<String, Integer> capacities, Map<Point, Parcel> directory) {
        long startTime = System.currentTimeMillis();
        RouteState currentBest = startState.cloneState();
        RouteState workingState = startState.cloneState();

        int iterations = 0;

        while ((System.currentTimeMillis() - startTime) < timeLimitMs) {
            iterations++;
            List<Point> unassigned = new ArrayList<>();

            // 1. Destroy
            workingState.randomRemoval(2, unassigned);

            // 2. Repair
            GreedyEngine repairEngine = new GreedyEngine();
            for (Point p : unassigned) {
                Parcel actualParcel = directory.get(p);
                int demand = actualParcel != null ? actualParcel.getDemand() : 1;
                workingState = repairEngine.insertNewParcel(new Parcel("temp", p.x, p.y, demand), workingState, capacities, directory);
            }

            // 3. Evaluate
            if (workingState.getTotalDistance() < currentBest.getTotalDistance()) {
                currentBest = workingState.cloneState();
            } else {
                workingState = currentBest.cloneState();
            }
        }

        System.out.printf("ALNS finished %d iterations. Best distance: %.2f\n", iterations, currentBest.getTotalDistance());
        return currentBest;
    }
}