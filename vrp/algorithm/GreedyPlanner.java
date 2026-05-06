package RoutingAgent.vrp.algorithm;

import RoutingAgent.vrp.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GreedyPlanner {

    /**
     * Assigns all parcels round-robin to agents, then orders each agent's
     * delivery sequence by nearest-neighbour from the warehouse.
     * Resets agents to warehouse position before planning.
     */
    public static void plan(WorldState world) {
        List<AgentState> agents = world.agents;
        int agentCount = agents.size();
        if (agentCount == 0) return;

        // Reset all agent state
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
        for (Parcel p : world.parcels) {
            p.status = ParcelStatus.UNASSIGNED;
            p.assignedAgentId = -1;
        }

        // Round-robin assignment: parcel i → agent (i % N), skip full agents
        int idx = 0;
        for (Parcel p : world.parcels) {
            AgentState a = agents.get(idx % agentCount);
            if (a.freeCapacity() > 0) {
                p.assignedAgentId = a.id;
                p.status = ParcelStatus.COMMITTED;
                a.inVehicle.add(p);
            }
            idx++;
        }

        // Nearest-neighbour route ordering per agent
        for (AgentState agent : agents) {
            if (agent.inVehicle.isEmpty()) continue;

            List<Parcel> pool = new ArrayList<>(agent.inVehicle);
            double cx = agent.homeWarehouse.x;
            double cy = agent.homeWarehouse.y;

            while (!pool.isEmpty()) {
                Parcel nearest = nearestFrom(pool, cx, cy);
                pool.remove(nearest);
                agent.remainingRoute.add(
                        new RouteStop(StopType.DELIVER, nearest.destination, nearest));
                cx = nearest.destination.x;
                cy = nearest.destination.y;
            }

            agent.remainingRoute.add(
                    new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse));
            agent.status = AgentStatus.MOVING;
        }
    }

    /**
     * Mid-simulation replan: collect all undelivered parcels + any UNASSIGNED ones,
     * re-assign round-robin to active (non-STANDBY) agents, rebuild routes from
     * each agent's current position.
     */
    public static void replan(WorldState world) {
        List<AgentState> active = world.agents.stream()
                .filter(a -> a.status != AgentStatus.STANDBY)
                .collect(Collectors.toList());
        if (active.isEmpty()) return;

        // Collect all undelivered parcels and clear agent routes
        List<Parcel> pool = new ArrayList<>();
        for (AgentState a : active) {
            pool.addAll(a.inVehicle);
            a.inVehicle.clear();
            a.remainingRoute.clear();
        }
        // Pick up any UNASSIGNED parcels (just added dynamically)
        for (Parcel p : world.parcels) {
            if (p.status == ParcelStatus.UNASSIGNED) {
                p.assignedAgentId = -1;
                pool.add(p);
            }
        }
        if (pool.isEmpty()) return;

        // Reset assignments
        for (Parcel p : pool) { p.assignedAgentId = -1; p.status = ParcelStatus.COMMITTED; }

        // Round-robin to active agents
        int idx = 0;
        for (Parcel p : pool) {
            AgentState a = active.get(idx % active.size());
            if (a.freeCapacity() > 0) { p.assignedAgentId = a.id; a.inVehicle.add(p); }
            idx++;
        }

        // Nearest-neighbour route from current position (not warehouse)
        for (AgentState agent : active) {
            if (agent.inVehicle.isEmpty()) continue;
            List<Parcel> agentPool = new ArrayList<>(agent.inVehicle);
            double cx = agent.currentX, cy = agent.currentY;
            while (!agentPool.isEmpty()) {
                Parcel nearest = nearestFrom(agentPool, cx, cy);
                agentPool.remove(nearest);
                agent.remainingRoute.add(new RouteStop(StopType.DELIVER, nearest.destination, nearest));
                cx = nearest.destination.x; cy = nearest.destination.y;
            }
            agent.remainingRoute.add(new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse));
        }
    }

    /** Total route distance: agent's current position → all stops in order. */
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

    private static Parcel nearestFrom(List<Parcel> pool, double cx, double cy) {
        Parcel best = null;
        double bestDist = Double.MAX_VALUE;
        for (Parcel p : pool) {
            double d = Math.hypot(p.destination.x - cx, p.destination.y - cy);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
