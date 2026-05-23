package RoutingAgent.Extension.Solver;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class TabuRoutingEngine {

    private static class Move {
        enum Type { TWO_OPT, RELOCATE }
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

    public RouteState optimize(RouteState currentState, Parcel newParcel, Map<String, Integer> capacities,
                               Map<Point, Parcel> directory, Map<String, Integer> lockedPrefixLength,
                               Set<Point> dynamicNodes, Map<String, Point> agentDepots, Consumer<String> logger) {
        RouteState workingState = currentState.cloneState();

        if (newParcel != null) {
            double bestCost = Double.MAX_VALUE;
            String bestAgent = null;
            int bestIndex = -1;
            Point parcelWarehouse = newParcel.getOriginWarehouse();

            for (String agentName : workingState.getRoutes().keySet()) {
                Point agentDepot = agentDepots.getOrDefault(agentName, new Point(50, 50));
                if (!agentDepot.equals(parcelWarehouse)) continue;

                List<Point> route = workingState.getRoutes().get(agentName);
                int lockedCount = lockedPrefixLength.getOrDefault(agentName, 0);
                int currentLoad = calculateLoad(route, directory);

                if (currentLoad + newParcel.getDemand() > capacities.getOrDefault(agentName, 5)) continue;

                for (int i = lockedCount + 1; i <= route.size(); i++) {
                    List<Point> testRoute = new ArrayList<>(route);
                    testRoute.add(i, newParcel.getDestination());

                    double cost = calculateRouteCost(testRoute, dynamicNodes, agentDepot);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestAgent = agentName;
                        bestIndex = i;
                    }
                }
            }
            if (bestAgent != null) {
                workingState.insertNode(bestAgent, bestIndex, newParcel.getDestination());
            } else if (logger != null) {
                logger.accept("TabuEngine ALERT: No capacity found for dynamic parcel " + newParcel.getId()
                        + " at warehouse (" + parcelWarehouse.x + "," + parcelWarehouse.y + "). Please deploy a Standby Agent!");
            }
        }

        int maxGenerations = 800;
        int tabuTenure = 15;
        Map<Point, Integer> tabuList = new HashMap<>();

        RouteState globalBestState = workingState.cloneState();
        double globalBestCost = calculateTotalCost(globalBestState, dynamicNodes, agentDepots);
        double currentCost = globalBestCost;

        if (logger != null) logger.accept(String.format("TabuEngine: Starting optimization... Initial Route Cost: %.1f", globalBestCost));

        for (int gen = 0; gen < maxGenerations; gen++) {
            Move bestMove = null;
            double bestMoveDelta = Double.MAX_VALUE;

            for (String agentA : workingState.getRoutes().keySet()) {
                List<Point> routeA = workingState.getRoutes().get(agentA);
                int lockedA = lockedPrefixLength.getOrDefault(agentA, 0);
                Point depotA = agentDepots.getOrDefault(agentA, new Point(50, 50));

                for (int i = lockedA + 1; i < routeA.size(); i++) {
                    Point nodeToMove = routeA.get(i);
                    if (nodeToMove.equals(depotA)) continue;

                    Parcel parcel = directory.get(nodeToMove);
                    if (parcel != null && !parcel.getOriginWarehouse().equals(depotA)) continue;

                    int demand = parcel != null ? parcel.getDemand() : 0;
                    double oldCostA = calculateRouteCost(routeA, dynamicNodes, depotA);

                    List<Point> testRouteA = new ArrayList<>(routeA);
                    testRouteA.remove(i);
                    double newCostA = calculateRouteCost(testRouteA, dynamicNodes, depotA);

                    for (String agentB : workingState.getRoutes().keySet()) {
                        if (agentA.equals(agentB)) continue;

                        Point depotB = agentDepots.getOrDefault(agentB, new Point(50, 50));
                        if (parcel != null && !parcel.getOriginWarehouse().equals(depotB)) continue;

                        List<Point> routeB = workingState.getRoutes().get(agentB);
                        int lockedB = lockedPrefixLength.getOrDefault(agentB, 0);
                        int loadB = calculateLoad(routeB, directory);

                        if (loadB + demand > capacities.getOrDefault(agentB, 5)) continue;

                        double oldCostB = calculateRouteCost(routeB, dynamicNodes, depotB);

                        for (int j = lockedB + 1; j <= routeB.size(); j++) {
                            List<Point> testRouteB = new ArrayList<>(routeB);
                            testRouteB.add(j, nodeToMove);
                            double newCostB = calculateRouteCost(testRouteB, dynamicNodes, depotB);

                            double delta = (newCostA + newCostB) - (oldCostA + oldCostB);
                            boolean isTabu = tabuList.getOrDefault(nodeToMove, -1) >= gen;
                            if (currentCost + delta < globalBestCost) isTabu = false;

                            if (!isTabu && delta < bestMoveDelta) {
                                bestMoveDelta = delta;
                                bestMove = new Move(Move.Type.RELOCATE, agentA, agentB, i, j, nodeToMove);
                            }
                        }
                    }
                }
            }

            for (String agent : workingState.getRoutes().keySet()) {
                List<Point> route = workingState.getRoutes().get(agent);
                int locked = lockedPrefixLength.getOrDefault(agent, 0);
                Point depot = agentDepots.getOrDefault(agent, new Point(50, 50));

                if (route.size() - locked < 2) continue;
                double oldCost = calculateRouteCost(route, dynamicNodes, depot);

                for (int i = locked + 1; i < route.size() - 1; i++) {
                    for (int j = i + 1; j < route.size(); j++) {
                        List<Point> testRoute = new ArrayList<>(route);
                        int left = i, right = j;
                        while (left < right) {
                            Point temp = testRoute.get(left);
                            testRoute.set(left, testRoute.get(right));
                            testRoute.set(right, temp);
                            left++; right--;
                        }

                        double newCost = calculateRouteCost(testRoute, dynamicNodes, depot);
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
                        left++; right--;
                    }
                }

                currentCost += bestMoveDelta;
                tabuList.put(bestMove.node, gen + tabuTenure);

                if (currentCost < globalBestCost) {
                    globalBestCost = currentCost;
                    globalBestState = workingState.cloneState();
                }
            } else {
                break;
            }

            if (gen > 0 && gen % 200 == 0 && logger != null) {
                logger.accept(String.format("TabuEngine: Generation %d reached. Current Best Cost: %.1f", gen, globalBestCost));
            }
        }

        if (logger != null) logger.accept(String.format("TabuEngine: Optimization Complete. Final Optimized Cost: %.1f", globalBestCost));
        return globalBestState;
    }

    private int calculateLoad(List<Point> route, Map<Point, Parcel> directory) {
        int load = 0;
        for (Point p : route) {
            if (directory.containsKey(p)) load += directory.get(p).getDemand();
        }
        return load;
    }

    private double calculateRouteCost(List<Point> route, Set<Point> dynamicNodes, Point depot) {
        if (route.isEmpty()) return 0;
        double cost = 0;
        boolean hasInventory = false;

        if (route.get(0).equals(depot)) hasInventory = true;

        for (int i = 0; i < route.size() - 1; i++) {
            Point current = route.get(i);
            Point next = route.get(i + 1);

            if (current.equals(depot)) hasInventory = true;

            if (dynamicNodes != null && dynamicNodes.contains(next) && !hasInventory) {
                cost += current.distance(depot);
                cost += depot.distance(next);
                hasInventory = true;
            } else {
                cost += current.distance(next);
            }
        }

        if (!route.get(route.size() - 1).equals(depot)) {
            cost += route.get(route.size() - 1).distance(depot);
        }
        return cost;
    }

    private double calculateTotalCost(RouteState state, Set<Point> dynamicNodes, Map<String, Point> agentDepots) {
        double total = 0;
        for (Map.Entry<String, List<Point>> entry : state.getRoutes().entrySet()) {
            Point depot = agentDepots.getOrDefault(entry.getKey(), new Point(50, 50));
            total += calculateRouteCost(entry.getValue(), dynamicNodes, depot);
        }
        return total;
    }
}
