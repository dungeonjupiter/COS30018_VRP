package RoutingAgent.Extension.Solver;

import RoutingAgent.Extension.RoutingAgent.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class ALNSEngine {

    public RouteState optimize(RouteState startState, long timeLimitMs) {
        long startTime = System.currentTimeMillis();
        RouteState currentBest = startState.cloneState();
        RouteState workingState = startState.cloneState();

        int iterations = 0;

        while ((System.currentTimeMillis() - startTime) < timeLimitMs) {
            iterations++;
            List<Point> unassigned = new ArrayList<>();

            // 1. Destroy (Rip 2 parcels out randomly)
            workingState.randomRemoval(2, unassigned);

            // 2. Repair (Put them back using Greedy logic)
            GreedyEngine repairEngine = new GreedyEngine();
            for (Point p : unassigned) {
                // Fake a parcel to reuse our greedy logic
                workingState = repairEngine.insertNewParcel(new Parcel("temp", p.x, p.y, 1), workingState);
            }

            // 3. Evaluate
            if (workingState.getTotalDistance() < currentBest.getTotalDistance()) {
                currentBest = workingState.cloneState();
            } else {
                workingState = currentBest.cloneState();
            }
        }

        System.out.println("ALNS finished " + iterations + " iterations. Best distance: " + currentBest.getTotalDistance());
        return currentBest;
    }
}