package RoutingAgent.vrp.algorithm;

import RoutingAgent.vrp.model.*;

import java.util.*;

public class ClarkeWrightPlanner {

    /**
     * Plans routes for all agents using the Clarke-Wright Savings algorithm.
     * Resets all agent and parcel state before planning.
     */
    public static void plan(WorldState world) {
        List<AgentState> agents = world.agents;
        int agentCount = agents.size();
        if (agentCount == 0) return;

        // --- Step 1: Reset all agent state ---
        for (AgentState a : agents) {
            a.inVehicle.clear();
            a.remainingRoute.clear();
            a.status = AgentStatus.STANDBY;
            a.currentX = a.homeWarehouse.x;
            a.currentY = a.homeWarehouse.y;
            a.prevX = a.homeWarehouse.x;
            a.prevY = a.homeWarehouse.y;
            a.moveStartMs = 0;
            a.moveDurationMs = 0;
        }

        // --- Step 2: Reset all parcel state ---
        for (Parcel p : world.parcels) {
            p.status = ParcelStatus.UNASSIGNED;
            p.assignedAgentId = -1;
        }

        List<Parcel> parcels = world.parcels;
        if (parcels.isEmpty()) return;

        // --- Step 3: Clarke-Wright Savings algorithm ---

        // Maximum capacity across all agents — used for merge limit
        int routeCap = agents.stream()
                .mapToInt(a -> a.capacity)
                .max()
                .orElse(Integer.MAX_VALUE);

        // Each parcel starts as its own singleton route
        // routeOf maps a Parcel → the LinkedList<Parcel> it currently belongs to
        IdentityHashMap<Parcel, LinkedList<Parcel>> routeOf = new IdentityHashMap<>();
        // The canonical set of active routes (use identity comparison via IdentityHashMap keys)
        IdentityHashMap<LinkedList<Parcel>, Boolean> activeRoutes = new IdentityHashMap<>();

        for (Parcel p : parcels) {
            LinkedList<Parcel> singleton = new LinkedList<>();
            singleton.add(p);
            routeOf.put(p, singleton);
            activeRoutes.put(singleton, Boolean.TRUE);
        }

        double depotX = world.warehouse.x;
        double depotY = world.warehouse.y;

        // Compute savings for all ordered pairs (i, j): s = dist(depot,i) + dist(depot,j) - dist(i,j)
        // Using ordered pairs so (i→j) and (j→i) are both considered
        int n = parcels.size();
        List<double[]> savings = new ArrayList<>(n * (n - 1));
        for (int i = 0; i < n; i++) {
            Parcel pi = parcels.get(i);
            double ddi = dist(depotX, depotY, pi.destination.x, pi.destination.y);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                Parcel pj = parcels.get(j);
                double ddj = dist(depotX, depotY, pj.destination.x, pj.destination.y);
                double dij = dist(pi.destination.x, pi.destination.y, pj.destination.x, pj.destination.y);
                double s = ddi + ddj - dij;
                // Store: [saving, index_i, index_j]
                savings.add(new double[]{s, i, j});
            }
        }

        // Sort savings descending
        savings.sort((a, b) -> Double.compare(b[0], a[0]));

        // Merge routes greedily
        for (double[] entry : savings) {
            Parcel pi = parcels.get((int) entry[1]);
            Parcel pj = parcels.get((int) entry[2]);

            LinkedList<Parcel> ri = routeOf.get(pi);
            LinkedList<Parcel> rj = routeOf.get(pj);

            // Merge condition: pi is the last in ri, pj is the first in rj,
            // ri and rj are distinct routes, and combined size fits within routeCap
            if (ri == rj) continue;
            if (ri.getLast() != pi) continue;
            if (rj.getFirst() != pj) continue;
            if (ri.size() + rj.size() > routeCap) continue;

            // Merge rj into ri
            for (Parcel moved : rj) {
                routeOf.put(moved, ri);
            }
            ri.addAll(rj);
            activeRoutes.remove(rj);
        }

        // Collect final list of routes
        List<LinkedList<Parcel>> routes = new ArrayList<>(activeRoutes.keySet());

        // --- Step 4: Assign routes to agents (greedy best-fit) ---

        // Sort routes by size descending
        routes.sort((a, b) -> b.size() - a.size());

        int[] agentLoad = new int[agentCount];
        List<List<Parcel>> agentParcels = new ArrayList<>(agentCount);
        for (int i = 0; i < agentCount; i++) {
            agentParcels.add(new ArrayList<>());
        }

        for (LinkedList<Parcel> route : routes) {
            int routeSize = route.size();

            // Find agent with enough free capacity and the most free space (best-fit: most room)
            int bestIdx = -1;
            int bestFree = -1;
            for (int i = 0; i < agentCount; i++) {
                int free = agents.get(i).capacity - agentLoad[i];
                if (free >= routeSize && free > bestFree) {
                    bestFree = free;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                // Assign entire route to this agent
                agentParcels.get(bestIdx).addAll(route);
                agentLoad[bestIdx] += routeSize;
            } else {
                // No agent can take the whole route — split parcel-by-parcel into any agent with capacity
                for (Parcel p : route) {
                    for (int i = 0; i < agentCount; i++) {
                        int free = agents.get(i).capacity - agentLoad[i];
                        if (free >= 1) {
                            agentParcels.get(i).add(p);
                            agentLoad[i]++;
                            break;
                        }
                    }
                    // If no agent has any capacity left, the parcel remains UNASSIGNED
                }
            }
        }

        // --- Step 5: Build RouteStops from CW-ordered agentParcels lists ---
        for (int i = 0; i < agentCount; i++) {
            AgentState agent = agents.get(i);
            List<Parcel> assigned = agentParcels.get(i);
            if (assigned.isEmpty()) continue;

            for (Parcel p : assigned) {
                p.assignedAgentId = agent.id;
                p.status = ParcelStatus.COMMITTED;
                agent.inVehicle.add(p);
                agent.remainingRoute.add(new RouteStop(StopType.DELIVER, p.destination, p));
            }

            agent.remainingRoute.add(new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse));
            agent.status = AgentStatus.MOVING;
        }
    }

    /** Total route distance: agent's current position through all remaining stops in order. */
    public static double routeDistance(AgentState agent) {
        if (agent.remainingRoute.isEmpty()) return 0;
        double total = 0;
        double cx = agent.currentX, cy = agent.currentY;
        for (RouteStop s : agent.remainingRoute) {
            total += Math.hypot(s.target.x - cx, s.target.y - cy);
            cx = s.target.x;
            cy = s.target.y;
        }
        return total;
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
