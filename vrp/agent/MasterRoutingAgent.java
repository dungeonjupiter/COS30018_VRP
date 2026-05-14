package RoutingAgent.vrp.agent;

import RoutingAgent.vrp.algorithm.GreedyPlanner;
import RoutingAgent.vrp.gui.MainWindow;
import RoutingAgent.vrp.model.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MasterRoutingAgent extends Agent {

    private WorldState worldState;
    private MainWindow window;

    // ConcurrentLinkedQueue allows multiple parcels to be queued from the EDT
    // while the JADE thread drains them one at a time.
    private final ConcurrentLinkedQueue<double[]> pendingParcels = new ConcurrentLinkedQueue<>();
    private volatile Integer   pendingAgentCapacity = null;
    private volatile boolean   replanning           = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        worldState = (WorldState) args[0];
        window     = (MainWindow) args[1];

        window.setMRA(this);

        spawnDeliveryAgents();

        addBehaviour(new WakerBehaviour(this, 600) {
            @Override protected void onWake() { dispatchRoutes(); }
        });

        addBehaviour(new DeliveryListenerBehaviour());

        // Poll for pending parcel or agent requests every 150 ms.
        // The two checks are independent — adding an agent never blocks parcel insertion.
        addBehaviour(new TickerBehaviour(this, 150) {
            @Override protected void onTick() {
                if (!replanning) {
                    double[] coords = pendingParcels.poll();
                    if (coords != null) {
                        replanning = true;
                        doAddParcel(coords[0], coords[1]);
                    }
                }

                Integer cap = pendingAgentCapacity;
                if (cap != null) {
                    pendingAgentCapacity = null;
                    doAddAgent(cap);
                }
            }
        });

        window.log("MRA online — " + worldState.agents.size() + " delivery agents spawning");
    }

    // ── agent spawning ────────────────────────────────────────────────────────

    private void spawnDeliveryAgents() {
        for (AgentState a : worldState.agents) {
            try {
                AgentController ac = getContainerController().createNewAgent(
                        a.name(),
                        DeliveryAgent.class.getName(),
                        new Object[]{worldState, a.id});
                ac.start();
            } catch (Exception e) {
                window.log("Failed to spawn " + a.name() + ": " + e.getMessage());
            }
        }
    }

    // ── route dispatch ────────────────────────────────────────────────────────

    private void dispatchRoutes() {
        for (AgentState agent : worldState.agents) {
            if (agent.remainingRoute.isEmpty()) continue;
            sendToAgent(agent.name(), serialiseRoute(agent));
            window.log("Route → " + agent.name()
                    + "  [" + countDeliveries(agent) + " deliveries]");
        }
    }

    private String serialiseRoute(AgentState agent) {
        StringBuilder sb = new StringBuilder(Protocol.NEW_ROUTE);
        for (RouteStop stop : agent.remainingRoute) {
            int pid = (stop.parcel != null) ? stop.parcel.id : -1;
            sb.append("|")
              .append(stop.type).append(",")
              .append(stop.target.x).append(",")
              .append(stop.target.y).append(",")
              .append(pid);
        }
        return sb.toString();
    }

    private int countDeliveries(AgentState agent) {
        return (int) agent.remainingRoute.stream()
                .filter(s -> s.type == StopType.DELIVER).count();
    }

    // ── add parcel (Phase 5) ──────────────────────────────────────────────────

    /** Called from EDT; safe because pendingParcel is volatile. */
    public void requestAddParcel(double x, double y) {
        pendingParcels.add(new double[]{x, y});
    }

    private void doAddParcel(double x, double y) {
        int cid = worldState.customers.stream().mapToInt(c -> c.id).max().orElse(0) + 1;
        int pid = cid;
        CustomerNode newCustomer = new CustomerNode(cid, x, y);
        Parcel       newParcel   = new Parcel(pid, newCustomer);
        worldState.customers.add(newCustomer);
        worldState.parcels.add(newParcel);

        final double nx = x, ny = y;
        AgentState target = worldState.agents.stream()
                .filter(a -> (a.status == AgentStatus.MOVING || a.status == AgentStatus.STANDBY)
                          && a.freeCapacity() > 0)
                .min(java.util.Comparator.comparingDouble(a ->
                        Math.hypot(a.currentX - nx, a.currentY - ny)))
                .orElse(null);

        if (target == null) {
            window.log("No active agent available for P" + pid + " — try again shortly");
            worldState.customers.remove(newCustomer);
            worldState.parcels.remove(newParcel);
            window.setAddParcelEnabled(true);
            replanning = false;
            return;
        }

        newParcel.assignedAgentId = target.id;
        newParcel.status = ParcelStatus.COMMITTED;
        target.inVehicle.add(newParcel);
        window.log(String.format("+ Parcel P%d → C%d (%.1f, %.1f) assigned to %s",
                pid, cid, x, y, target.name()));

        sendToAgent(target.name(), Protocol.FREEZE);

        final AgentState frozenAgent    = target;
        final Parcel     parcelToInsert = newParcel;

        addBehaviour(new WakerBehaviour(this, 300) {
            @Override protected void onWake() {
                insertParcelIntoRoute(frozenAgent, parcelToInsert);
                sendToAgent(frozenAgent.name(), serialiseRoute(frozenAgent));
                sendToAgent(frozenAgent.name(), Protocol.RESUME);
                window.log(frozenAgent.name() + " re-routed — warehouse pickup then C"
                        + parcelToInsert.destination.id);
                window.refreshMap();
                window.setAddParcelEnabled(true);
                replanning = false;
            }
        });
    }

    private void insertParcelIntoRoute(AgentState agent, Parcel parcel) {
        java.util.List<RouteStop> route = agent.remainingRoute;

        // Strip RETURN_TO_WAREHOUSE tail to work on delivery stops only
        RouteStop returnStop = null;
        if (!route.isEmpty()
                && route.get(route.size() - 1).type == StopType.RETURN_TO_WAREHOUSE) {
            returnStop = route.remove(route.size() - 1);
        }
        if (returnStop == null) {
            returnStop = new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse);
        }

        double nx = parcel.destination.x, ny = parcel.destination.y;
        double wx = agent.homeWarehouse.x,  wy = agent.homeWarehouse.y;

        // currentX/Y is always the destination of the active animation leg, not the real
        // position. Interpolate to get the agent's actual visual position at planning time.
        double actualX = agent.currentX, actualY = agent.currentY;
        if (agent.moveDurationMs > 0) {
            long elapsed = System.currentTimeMillis() - agent.moveStartMs;
            if (elapsed < agent.moveDurationMs) {
                double t = (double) elapsed / agent.moveDurationMs;
                actualX = agent.prevX + (agent.currentX - agent.prevX) * t;
                actualY = agent.prevY + (agent.currentY - agent.prevY) * t;
            }
        }
        boolean atWarehouse = Math.hypot(actualX - wx, actualY - wy) < 0.5;

        // Cheapest insertion: try every gap and pick the one that adds the least distance.
        // If the agent is not at the warehouse, the pair (warehouse-pickup, deliver) is
        // inserted together so the pickup always immediately precedes the delivery.
        int    bestPos  = route.size();
        double bestCost = Double.MAX_VALUE;
        double prevX = actualX, prevY = actualY;

        for (int i = 0; i <= route.size(); i++) {
            double nextX = (i < route.size()) ? route.get(i).target.x : wx;
            double nextY = (i < route.size()) ? route.get(i).target.y : wy;

            double cost = atWarehouse
                    ? dist(prevX, prevY, nx, ny) + dist(nx, ny, nextX, nextY)
                      - dist(prevX, prevY, nextX, nextY)
                    : dist(prevX, prevY, wx, wy) + dist(wx, wy, nx, ny) + dist(nx, ny, nextX, nextY)
                      - dist(prevX, prevY, nextX, nextY);

            if (cost < bestCost) { bestCost = cost; bestPos = i; }

            if (i < route.size()) {
                prevX = route.get(i).target.x;
                prevY = route.get(i).target.y;
            }
        }

        if (atWarehouse) {
            route.add(bestPos, new RouteStop(StopType.DELIVER, parcel.destination, parcel));
        } else {
            route.add(bestPos,     new RouteStop(StopType.PICKUP_AT_WAREHOUSE, agent.homeWarehouse));
            route.add(bestPos + 1, new RouteStop(StopType.DELIVER, parcel.destination, parcel));
        }
        route.add(returnStop);
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    // ── add agent (Phase 6) ───────────────────────────────────────────────────

    /** Called from EDT; safe because pendingAgentCapacity is volatile. */
    public void requestAddAgent(int capacity) {
        pendingAgentCapacity = capacity;
    }

    private void doAddAgent(int capacity) {
        int newId = worldState.agents.stream().mapToInt(a -> a.id).max().orElse(0) + 1;
        AgentState newAgent = new AgentState(newId, capacity, worldState.warehouse);
        worldState.agents.add(newAgent);

        try {
            AgentController ac = getContainerController().createNewAgent(
                    newAgent.name(),
                    DeliveryAgent.class.getName(),
                    new Object[]{worldState, newAgent.id});
            ac.start();
            window.log(String.format("+ Agent %s added (capacity %d) — idle at warehouse",
                    newAgent.name(), capacity));
            window.refreshMap();
        } catch (Exception e) {
            window.log("Failed to add agent: " + e.getMessage());
            worldState.agents.remove(newAgent);
        }

        window.setAddAgentEnabled(true);
    }

    // ── delivery listener ─────────────────────────────────────────────────────

    private class DeliveryListenerBehaviour extends CyclicBehaviour {
        private final MessageTemplate MT =
                MessageTemplate.MatchPerformative(ACLMessage.INFORM);

        @Override
        public void action() {
            ACLMessage msg = receive(MT);
            if (msg == null) { block(); return; }

            String content = msg.getContent();
            if (content.startsWith(Protocol.DELIVERY_COMPLETE)) {
                String[] parts = content.split("\\|");
                handleDelivery(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

            } else if (content.startsWith(Protocol.ALL_DELIVERED)) {
                String[] parts = content.split("\\|");
                AgentState a = worldState.findAgent(Integer.parseInt(parts[1]));
                if (a != null) window.log(a.name() + " idle at warehouse — awaiting new parcels");

                // Delay the idle check: the returning agent's STANDBY status is set by a
                // deferred WakerBehaviour (~100 ms after ALL_DELIVERED), and the pending
                // queue may still have burst parcels in flight. 800 ms gives both time to settle.
                final AgentState returningAgent = a;
                MasterRoutingAgent.this.addBehaviour(new WakerBehaviour(MasterRoutingAgent.this, 800) {
                    @Override protected void onWake() {
                        if (returningAgent != null) reassignOverflowParcels(returningAgent);
                        if (!pendingParcels.isEmpty()) return;
                        boolean allIdle = worldState.agents.stream()
                                .allMatch(ag -> ag.status == AgentStatus.STANDBY);
                        if (allIdle) {
                            window.log("--- All agents idle — add parcels or click End Simulation ---");
                        }
                    }
                });
            }
        }

        private void handleDelivery(int agentId, int parcelId) {
            Parcel parcel = worldState.parcels.stream()
                    .filter(p -> p.id == parcelId).findFirst().orElse(null);
            if (parcel != null) parcel.status = ParcelStatus.DELIVERED;

            AgentState agent = worldState.findAgent(agentId);
            if (agent != null) agent.inVehicle.removeIf(p -> p.id == parcelId);

            long delivered = worldState.deliveredCount();
            int  custId    = (parcel != null) ? parcel.destination.id : -1;
            window.log(String.format("DA%d  P%d → C%d   [%d/%d]",
                    agentId, parcelId, custId, delivered, worldState.parcels.size()));
            window.refreshMap();
        }
    }

    // ── overflow re-dispatch ──────────────────────────────────────────────────

    private void reassignOverflowParcels(AgentState agent) {
        if (agent.status != AgentStatus.STANDBY) return;

        java.util.List<Parcel> unassigned = new java.util.ArrayList<>();
        for (Parcel p : worldState.parcels) {
            if (p.status == ParcelStatus.UNASSIGNED) unassigned.add(p);
        }
        if (unassigned.isEmpty()) return;

        int slots = agent.freeCapacity();
        if (slots <= 0) return;

        int take = Math.min(slots, unassigned.size());
        for (int i = 0; i < take; i++) {
            Parcel p = unassigned.get(i);
            p.assignedAgentId = agent.id;
            p.status = ParcelStatus.COMMITTED;
            agent.inVehicle.add(p);
            agent.remainingRoute.add(new RouteStop(StopType.DELIVER, p.destination, p));
        }
        agent.remainingRoute.add(new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse));
        agent.status = AgentStatus.MOVING;

        sendToAgent(agent.name(), serialiseRoute(agent));
        window.log(agent.name() + " re-dispatched — " + take + " overflow parcel(s) remaining");
        window.refreshMap();
    }

    // ── end simulation (user-triggered) ──────────────────────────────────────

    /** Called from the EDT via the End Simulation button. */
    public void stopSimulation() {
        long delivered = worldState.deliveredCount();
        window.log(String.format(
                "=== Simulation ended — %d/%d parcels delivered ===",
                delivered, worldState.parcels.size()));
        window.onSimulationComplete();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void sendToAgent(String agentName, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
    }
}
