package RoutingAgent.Extension.Solver;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * TabuRoutingEngine
 * * The core mathematical optimization engine for the VRP Extension.
 * Unlike standard static solvers, this engine is designed for real-time dynamic rerouting.
 * It operates in two phases:
 * 1. Fast Geometric Insertion: Quickly slots a newly dropped parcel into the cheapest valid spot.
 * 2. True Tabu Search: Refines the global map using Relocate and 2-Opt moves to untangle crossed lines
 * and optimize sector boundaries, utilizing a Tabu List to escape local minima.
 */
public class TabuRoutingEngine {

    /**
     * Represents a mathematical transformation applied to the route state.
     */
    private static class Move {
        enum Type {
            TWO_OPT,   // Reverses a segment within a single agent's route to untangle loops
            RELOCATE   // Moves a single parcel from one agent to another
        }

        Type type;
        String agentA;
        String agentB;
        int indexA;
        int indexB;
        Point node;

        Move(Type type, String agentA, String agentB, int indexA, int indexB, Point node) {
            this.type = type;
            this.agentA = agentA;
            this.agentB = agentB;
            this.indexA = indexA;
            this.indexB = indexB;
            this.node = node;
        }
    }

    /**
     * Overloaded entry point without the logging consumer.
     */
    public RouteState optimize(RouteState currentState, Parcel newParcel, Map<String, Integer> capacities,
                               Map<Point, Parcel> directory, Map<String, Integer> lockedPrefixLength,
                               Set<Point> dynamicNodes) {
        return optimize(currentState, newParcel, capacities, directory, lockedPrefixLength, dynamicNodes, null);
    }

    /**
     * Main optimization loop.
     * * @param currentState The current geographical state of all routes.
     * @param newParcel A newly injected parcel (can be null if just refining existing routes).
     * @param capacities The maximum payload capacity of each agent.
     * @param directory Map of coordinate points to their Parcel objects.
     * @param lockedPrefixLength Number of nodes each agent has ALREADY driven. These cannot be altered.
     * @param dynamicNodes Set of nodes added mid-simulation.
     * @param logger Consumer for sending debug messages back to the UI.
     * @return The optimized RouteState.
     */
    public RouteState optimize(RouteState currentState, Parcel newParcel, Map<String, Integer> capacities,
                               Map<Point, Parcel> directory, Map<String, Integer> lockedPrefixLength,
                               Set<Point> dynamicNodes, Consumer<String> logger) {

        RouteState workingState = currentState.cloneState();

        // ==========================================
        // PHASE 1: Fast Geometric Insertion (Global Cheapest)
        // ==========================================
        // If a new parcel was just dropped, find the absolute cheapest valid place
        // to insert it across ALL agents' future routes before running full Tabu.
        if (newParcel != null) {
            double bestCost = Double.MAX_VALUE;
            String bestAgent = null;
            int bestIndex = -1;
            int bestAgentLoad = Integer.MAX_VALUE;

            for (String agentName : workingState.getRoutes().keySet()) {
                List<Point> route = workingState.getRoutes().get(agentName);

                // Agents are moving, only insert parcels AFTER their locked prefix.
                int lockedCount = lockedPrefixLength.getOrDefault(agentName, 0);
                int cap = capacities.getOrDefault(agentName, 5);
                int currentLoad = peakRouteLoad(route, directory, cap, dynamicNodes);

                // Test inserting the new node into every possible future gap
                for (int i = lockedCount + 1; i <= route.size(); i++) {
                    List<Point> testRoute = new ArrayList<>(route);
                    testRoute.add(i, newParcel.getDestination());

                    // Reject if this insertion violates the agent's hard capacity
                    if (peakRouteLoad(testRoute, directory, cap, dynamicNodes) > cap) continue;

                    double cost = calculateRouteCost(testRoute, dynamicNodes);
                    boolean betterCost = cost < bestCost - 1e-6;
                    boolean tieBreak   = Math.abs(cost - bestCost) <= 1e-6 && currentLoad < bestAgentLoad;

                    if (betterCost || tieBreak) {
                        bestCost = cost;
                        bestAgent = agentName;
                        bestIndex = i;
                        bestAgentLoad = currentLoad;
                    }
                }
            }

            // Apply the best insertion found
            if (bestAgent != null) {
                workingState.insertNode(bestAgent, bestIndex, newParcel.getDestination());
            } else if (logger != null) {
                logger.accept("TabuEngine ALERT: No capacity found for dynamic parcel " + newParcel.getId()
                        + ". Please deploy a Standby Agent!");
            }
        }

        // ==========================================
        // PHASE 2: True Tabu Search Optimization
        // ==========================================
        int maxGenerations = 800;
        int tabuTenure = 15;
        // Tabu List tracks recently moved nodes to prevent infinite cycling
        Map<Point, Integer> tabuList = new HashMap<>();

        RouteState globalBestState = workingState.cloneState();
        double globalBestCost = calculateTotalCost(globalBestState, dynamicNodes);
        double currentCost = globalBestCost;

        if (logger != null) {
            logger.accept(String.format("TabuEngine: Starting optimization... Initial Route Cost: %.1f", globalBestCost));
        }

        for (int gen = 0; gen < maxGenerations; gen++) {
            Move bestMove = null;
            double bestMoveDelta = Double.MAX_VALUE;

            // --- NEIGHBORHOOD 1: RELOCATE (Steal parcel from another agent) ---
            for (String agentA : workingState.getRoutes().keySet()) {
                List<Point> routeA = workingState.getRoutes().get(agentA);
                int lockedA = lockedPrefixLength.getOrDefault(agentA, 0);

                for (int i = lockedA + 1; i < routeA.size(); i++) {
                    Point nodeToMove = routeA.get(i);
                    // Never attempt to relocate the central depot
                    if (nodeToMove.equals(new Point(50, 50))) continue;

                    double oldCostA = calculateRouteCost(routeA, dynamicNodes);

                    List<Point> testRouteA = new ArrayList<>(routeA);
                    testRouteA.remove(i);
                    double newCostA = calculateRouteCost(testRouteA, dynamicNodes);

                    // Try moving it to Agent B
                    for (String agentB : workingState.getRoutes().keySet()) {
                        if (agentA.equals(agentB)) continue;

                        List<Point> routeB = workingState.getRoutes().get(agentB);
                        int lockedB = lockedPrefixLength.getOrDefault(agentB, 0);
                        int capB = capacities.getOrDefault(agentB, 5);

                        double oldCostB = calculateRouteCost(routeB, dynamicNodes);

                        for (int j = lockedB + 1; j <= routeB.size(); j++) {
                            List<Point> testRouteB = new ArrayList<>(routeB);
                            testRouteB.add(j, nodeToMove);

                            if (peakRouteLoad(testRouteB, directory, capB, dynamicNodes) > capB) continue;

                            double newCostB = calculateRouteCost(testRouteB, dynamicNodes);

                            // Calculate overall system improvement (Negative delta is good)
                            double delta = (newCostA + newCostB) - (oldCostA + oldCostB);

                            boolean isTabu = tabuList.getOrDefault(nodeToMove, -1) >= gen;
                            // Aspiration Criterion: Allow if it's Tabu but beats the global best
                            if (currentCost + delta < globalBestCost) isTabu = false;

                            if (!isTabu && delta < bestMoveDelta) {
                                bestMoveDelta = delta;
                                bestMove = new Move(Move.Type.RELOCATE, agentA, agentB, i, j, nodeToMove);
                            }
                        }
                    }
                }
            }

            // --- NEIGHBORHOOD 2: 2-OPT (Untangle geometry within a single route) ---
            for (String agent : workingState.getRoutes().keySet()) {
                List<Point> route = workingState.getRoutes().get(agent);
                int locked = lockedPrefixLength.getOrDefault(agent, 0);

                if (route.size() - locked < 2) continue;
                double oldCost = calculateRouteCost(route, dynamicNodes);

                for (int i = locked + 1; i < route.size() - 1; i++) {
                    for (int j = i + 1; j < route.size(); j++) {
                        List<Point> testRoute = new ArrayList<>(route);

                        // Reverse the nodes between index i and j
                        int left = i, right = j;
                        while (left < right) {
                            Point temp = testRoute.get(left);
                            testRoute.set(left, testRoute.get(right));
                            testRoute.set(right, temp);
                            left++;
                            right--;
                        }

                        double newCost = calculateRouteCost(testRoute, dynamicNodes);
                        double delta = newCost - oldCost;
                        Point node = route.get(i);

                        boolean isTabu = tabuList.getOrDefault(node, -1) >= gen;
                        if (currentCost + delta < globalBestCost) isTabu = false;

                        if (!isTabu && delta < bestMoveDelta) {
                            bestMoveDelta = delta;
                            bestMove = new Move(Move.Type.TWO_OPT, agent, agent, i, j, node);
                        }
                    }
                }
            }

            // --- COMMIT BEST MOVE ---
            if (bestMove != null) {
                if (bestMove.type == Move.Type.RELOCATE) {
                    List<Point> rA = workingState.getRoutes().get(bestMove.agentA);
                    List<Point> rB = workingState.getRoutes().get(bestMove.agentB);
                    Point p = rA.remove(bestMove.indexA);
                    rB.add(bestMove.indexB, p);
                } else if (bestMove.type == Move.Type.TWO_OPT) {
                    List<Point> r = workingState.getRoutes().get(bestMove.agentA);
                    int left = bestMove.indexA, right = bestMove.indexB;
                    while (left < right) {
                        Point temp = r.get(left);
                        r.set(left, r.get(right));
                        r.set(right, temp);
                        left++;
                        right--;
                    }
                }

                currentCost += bestMoveDelta;
                // Add the moved node to the Tabu List so it cannot be undone immediately
                tabuList.put(bestMove.node, gen + tabuTenure);

                // Update Global Best if we found a new absolute lowest cost
                if (currentCost < globalBestCost) {
                    globalBestCost = currentCost;
                    globalBestState = workingState.cloneState();
                }
            } else {
                break; // Local minimum reached, no non-Tabu moves available
            }

            if (gen > 0 && gen % 200 == 0 && logger != null) {
                logger.accept(String.format("TabuEngine: Generation %d reached. Current Best Cost: %.1f", gen, globalBestCost));
            }
        }

        if (logger != null) {
            logger.accept(String.format("TabuEngine: Optimization Complete. Final Optimized Cost: %.1f", globalBestCost));
        }
        return globalBestState;
    }

    /**
     * Safety check to verify if a specific coordinate is already assigned to a route.
     */
    public boolean routeContainsDestination(RouteState state, Point dest) {
        if (state == null || dest == null) return false;
        for (List<Point> route : state.getRoutes().values()) {
            for (Point p : route) {
                if (p.equals(dest)) return true;
            }
        }
        return false;
    }

    /** Overloaded peakRouteLoad for Single Warehouse mode. */
    public int peakRouteLoad(List<Point> route, Map<Point, Parcel> directory,
                             int vehicleCapacity, Set<Point> dynamicNodes) {
        return peakRouteLoad(route, directory, vehicleCapacity, dynamicNodes, null);
    }

    /**
     * Calculates the peak onboard payload across a route.
     * Ensures agents do not exceed their physical truck capacity. Load resets to 0
     * when the agent visits a depot to restock.
     */
    public int peakRouteLoad(List<Point> route, Map<Point, Parcel> directory,
                             int vehicleCapacity, Set<Point> dynamicNodes, Set<Point> depotPositions) {
        if (vehicleCapacity <= 0) return Integer.MAX_VALUE;
        int load = 0;
        int peak = 0;
        Point virtualDepot = new Point(50, 50);
        boolean hasInventory = false;

        for (Point p : route) {
            // Inventory resets at warehouses
            if (isDepotStop(p, virtualDepot, depotPositions)) {
                load = 0;
                hasInventory = true;
            } else if (directory.containsKey(p)) {
                // If this is a dynamic parcel dropped mid-route, the agent MUST have
                // visited a depot first to actually possess the physical item.
                if (dynamicNodes != null && dynamicNodes.contains(p) && !hasInventory) {
                    load = 0;
                    hasInventory = true;
                }
                int demand = directory.get(p).getDemand();
                if (demand > vehicleCapacity) return demand; // Impossible to deliver
                load += demand;
                if (load > vehicleCapacity) return load; // Route segment exceeds capacity
                peak = Math.max(peak, load);
            }
        }
        return peak;
    }

    private static boolean isDepotStop(Point p, Point virtualDepot, Set<Point> depotPositions) {
        if (p.equals(virtualDepot)) return true;
        return depotPositions != null && depotPositions.contains(p);
    }

    private int calculateLoad(List<Point> route, Map<Point, Parcel> directory) {
        int load = 0;
        for (Point p : route) {
            if (directory.containsKey(p)) load += directory.get(p).getDemand();
        }
        return load;
    }

    /**
     * Calculates the true Euclidean distance of a route.
     * Crucially, if a dynamic node is in the route but the agent hasn't visited the depot
     * to pick it up yet, this method injects a mathematical penalty cost representing the
     * forced detour back to the warehouse.
     */
    private double calculateRouteCost(List<Point> route, Set<Point> dynamicNodes) {
        if (route.isEmpty()) return 0;
        double cost = 0;
        boolean hasInventory = false;
        Point depot = new Point(50, 50);

        if (route.get(0).equals(depot)) hasInventory = true;

        for (int i = 0; i < route.size() - 1; i++) {
            Point current = route.get(i);
            Point next = route.get(i + 1);

            if (current.equals(depot)) hasInventory = true;

            // Forced Detour Logic: Agent is at 'current', needs to deliver to 'next',
            // but 'next' is a dynamic parcel and the agent is empty-handed.
            if (dynamicNodes != null && dynamicNodes.contains(next) && !hasInventory) {
                cost += current.distance(depot); // Drive to depot
                cost += depot.distance(next);    // Drive from depot to customer
                hasInventory = true;
            } else {
                cost += current.distance(next);  // Standard point-to-point driving
            }
        }

        // Final leg back to the depot at the end of the day
        if (!route.get(route.size() - 1).equals(depot)) {
            cost += route.get(route.size() - 1).distance(depot);
        }
        return cost;
    }

    /**
     * Sums the cost of all active routes to determine the global fitness of the system.
     */
    private double calculateTotalCost(RouteState state, Set<Point> dynamicNodes) {
        double total = 0;
        for (List<Point> route : state.getRoutes().values()) {
            total += calculateRouteCost(route, dynamicNodes);
        }
        return total;
    }
}