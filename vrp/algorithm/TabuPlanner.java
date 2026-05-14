package RoutingAgent.vrp.algorithm;

import RoutingAgent.vrp.model.*;

import java.util.*;

public class TabuPlanner {

    private static final int MAX_ITER    = 200;
    private static final int TABU_TENURE = 7;

    /**
     * Plans routes using Tabu Search, starting from a Greedy initial solution.
     * Explores RELOCATE moves (cross-agent) and 2-opt moves (intra-route).
     * Resets agent state before planning (delegated to GreedyPlanner bootstrap).
     */
    public static void plan(WorldState world) {
        GreedyPlanner.plan(world);

        List<AgentState> agents = world.agents;
        if (agents.isEmpty()) return;
        int n = agents.size();

        List<ArrayList<Parcel>> curr = extractAssignment(agents);
        double currentCost = totalCost(agents, curr);

        List<ArrayList<Parcel>> best = deepCopy(curr);
        double bestCost = currentCost;

        // tabu: "parcelId:srcAgentIdx" → expiry iteration (exclusive)
        Map<String, Integer> tabu = new HashMap<>();

        for (int iter = 0; iter < MAX_ITER; iter++) {
            double bestDelta = Double.MAX_VALUE;
            int[]  bestMove  = null;  // type 0=RELOCATE [0,src,sPos,dst,dPos], 1=TWO_OPT [1,ag,i,j,-1]
            String bestKey   = null;

            // ── RELOCATE moves ────────────────────────────────────────────────
            for (int src = 0; src < n; src++) {
                ArrayList<Parcel> srcRoute = curr.get(src);
                if (srcRoute.isEmpty()) continue;
                double srcOld = routeCost(agents.get(src), srcRoute);

                for (int sPos = 0; sPos < srcRoute.size(); sPos++) {
                    Parcel p = srcRoute.get(sPos);
                    double srcNew      = routeCostWithout(agents.get(src), srcRoute, sPos);
                    double removeSaving = srcOld - srcNew;

                    for (int dst = 0; dst < n; dst++) {
                        if (dst == src) continue;
                        if (curr.get(dst).size() >= agents.get(dst).capacity) continue;

                        ArrayList<Parcel> dstRoute = curr.get(dst);
                        double dstOld = routeCost(agents.get(dst), dstRoute);

                        for (int dPos = 0; dPos <= dstRoute.size(); dPos++) {
                            double dstNew = routeCostWithInsert(agents.get(dst), dstRoute, dPos, p);
                            double delta  = (dstNew - dstOld) - removeSaving;

                            String  key     = p.id + ":" + src;
                            boolean isTabu  = tabu.getOrDefault(key, 0) > iter;
                            boolean aspires = (currentCost + delta) < bestCost;

                            if (delta < bestDelta && (!isTabu || aspires)) {
                                bestDelta = delta;
                                bestMove  = new int[]{0, src, sPos, dst, dPos};
                                bestKey   = key;
                            }
                        }
                    }
                }
            }

            // ── 2-OPT moves ───────────────────────────────────────────────────
            for (int ag = 0; ag < n; ag++) {
                ArrayList<Parcel> route = curr.get(ag);
                int sz = route.size();
                if (sz < 2) continue;
                double agOld = routeCost(agents.get(ag), route);

                for (int i = 0; i < sz - 1; i++) {
                    for (int j = i + 1; j < sz; j++) {
                        double agNew  = routeCostWith2Opt(agents.get(ag), route, i, j);
                        double delta  = agNew - agOld;
                        if (delta < bestDelta) {
                            bestDelta = delta;
                            bestMove  = new int[]{1, ag, i, j, -1};
                            bestKey   = null;
                        }
                    }
                }
            }

            if (bestMove == null) break;

            // ── Apply best move ───────────────────────────────────────────────
            if (bestMove[0] == 0) {
                Parcel p = curr.get(bestMove[1]).remove(bestMove[2]);
                curr.get(bestMove[3]).add(bestMove[4], p);
                if (bestKey != null) tabu.put(bestKey, iter + TABU_TENURE);
            } else {
                Collections.reverse(curr.get(bestMove[1]).subList(bestMove[2], bestMove[3] + 1));
            }

            currentCost += bestDelta;
            if (currentCost < bestCost) {
                bestCost = currentCost;
                best = deepCopy(curr);
            }
        }

        applyToWorld(agents, best);
    }

    // ── cost helpers ──────────────────────────────────────────────────────────

    private static double routeCost(AgentState agent, List<Parcel> parcels) {
        double cost = 0, cx = agent.homeWarehouse.x, cy = agent.homeWarehouse.y;
        for (Parcel p : parcels) {
            cost += dist(cx, cy, p.destination.x, p.destination.y);
            cx = p.destination.x; cy = p.destination.y;
        }
        return cost + dist(cx, cy, agent.homeWarehouse.x, agent.homeWarehouse.y);
    }

    private static double routeCostWithout(AgentState agent, List<Parcel> parcels, int skipIdx) {
        double cost = 0, cx = agent.homeWarehouse.x, cy = agent.homeWarehouse.y;
        for (int k = 0; k < parcels.size(); k++) {
            if (k == skipIdx) continue;
            Parcel p = parcels.get(k);
            cost += dist(cx, cy, p.destination.x, p.destination.y);
            cx = p.destination.x; cy = p.destination.y;
        }
        return cost + dist(cx, cy, agent.homeWarehouse.x, agent.homeWarehouse.y);
    }

    private static double routeCostWithInsert(AgentState agent, List<Parcel> parcels, int insIdx, Parcel ins) {
        double cost = 0, cx = agent.homeWarehouse.x, cy = agent.homeWarehouse.y;
        int k = 0;
        for (; k < insIdx; k++) {
            Parcel p = parcels.get(k);
            cost += dist(cx, cy, p.destination.x, p.destination.y);
            cx = p.destination.x; cy = p.destination.y;
        }
        cost += dist(cx, cy, ins.destination.x, ins.destination.y);
        cx = ins.destination.x; cy = ins.destination.y;
        for (; k < parcels.size(); k++) {
            Parcel p = parcels.get(k);
            cost += dist(cx, cy, p.destination.x, p.destination.y);
            cx = p.destination.x; cy = p.destination.y;
        }
        return cost + dist(cx, cy, agent.homeWarehouse.x, agent.homeWarehouse.y);
    }

    private static double routeCostWith2Opt(AgentState agent, List<Parcel> parcels, int i, int j) {
        double cost = 0, cx = agent.homeWarehouse.x, cy = agent.homeWarehouse.y;
        for (int k = 0; k < parcels.size(); k++) {
            int idx = (k >= i && k <= j) ? (i + j - k) : k;
            Parcel p = parcels.get(idx);
            cost += dist(cx, cy, p.destination.x, p.destination.y);
            cx = p.destination.x; cy = p.destination.y;
        }
        return cost + dist(cx, cy, agent.homeWarehouse.x, agent.homeWarehouse.y);
    }

    private static double totalCost(List<AgentState> agents, List<ArrayList<Parcel>> assignment) {
        double total = 0;
        for (int i = 0; i < agents.size(); i++) total += routeCost(agents.get(i), assignment.get(i));
        return total;
    }

    // ── assignment helpers ────────────────────────────────────────────────────

    private static List<ArrayList<Parcel>> extractAssignment(List<AgentState> agents) {
        List<ArrayList<Parcel>> result = new ArrayList<>();
        for (AgentState a : agents) {
            ArrayList<Parcel> list = new ArrayList<>();
            for (RouteStop s : a.remainingRoute) {
                if (s.type == StopType.DELIVER && s.parcel != null) list.add(s.parcel);
            }
            result.add(list);
        }
        return result;
    }

    private static List<ArrayList<Parcel>> deepCopy(List<ArrayList<Parcel>> src) {
        List<ArrayList<Parcel>> copy = new ArrayList<>();
        for (ArrayList<Parcel> list : src) copy.add(new ArrayList<>(list));
        return copy;
    }

    private static void applyToWorld(List<AgentState> agents, List<ArrayList<Parcel>> assignment) {
        for (int i = 0; i < agents.size(); i++) {
            AgentState agent  = agents.get(i);
            List<Parcel> parcels = assignment.get(i);

            agent.inVehicle.clear();
            agent.remainingRoute.clear();

            for (Parcel p : parcels) {
                p.assignedAgentId = agent.id;
                p.status = ParcelStatus.COMMITTED;
                agent.inVehicle.add(p);
                agent.remainingRoute.add(new RouteStop(StopType.DELIVER, p.destination, p));
            }

            if (!parcels.isEmpty()) {
                agent.remainingRoute.add(new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse));
                agent.status = AgentStatus.MOVING;
            } else {
                agent.status = AgentStatus.STANDBY;
            }
        }
    }

    /** Total route distance for GUI display — same API as GreedyPlanner. */
    public static double routeDistance(AgentState agent) {
        if (agent.remainingRoute.isEmpty()) return 0;
        double total = 0, cx = agent.currentX, cy = agent.currentY;
        for (RouteStop s : agent.remainingRoute) {
            total += Math.hypot(s.target.x - cx, s.target.y - cy);
            cx = s.target.x; cy = s.target.y;
        }
        return total;
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
