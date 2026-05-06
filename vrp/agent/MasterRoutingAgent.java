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

public class MasterRoutingAgent extends Agent {

    private WorldState worldState;
    private MainWindow window;
    private int dispatchedAgentCount = 0;
    private int returnedAgentCount   = 0;

    // Written from EDT, read/cleared on JADE thread — volatile is sufficient.
    private volatile double[] pendingParcel = null;
    private volatile boolean  replanning   = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        worldState = (WorldState) args[0];
        window     = (MainWindow) args[1];

        window.setMRA(this);    // give MainWindow a back-reference for Add-Parcel calls

        spawnDeliveryAgents();

        // Give DAs 600 ms to initialise before route dispatch
        addBehaviour(new WakerBehaviour(this, 600) {
            @Override protected void onWake() { dispatchRoutes(); }
        });

        addBehaviour(new DeliveryListenerBehaviour());

        // Poll for parcel-add requests every 150 ms
        addBehaviour(new TickerBehaviour(this, 150) {
            @Override protected void onTick() {
                double[] coords = pendingParcel;
                if (coords == null || replanning) return;
                pendingParcel = null;
                replanning = true;
                doAddParcel(coords[0], coords[1]);
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
        dispatchedAgentCount = 0;
        for (AgentState agent : worldState.agents) {
            if (agent.remainingRoute.isEmpty()) continue;
            sendToAgent(agent.name(), serialiseRoute(agent));
            dispatchedAgentCount++;
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
        pendingParcel = new double[]{x, y};
    }

    private void doAddParcel(double x, double y) {
        int cid = worldState.customers.stream().mapToInt(c -> c.id).max().orElse(0) + 1;
        int pid = worldState.parcels.stream().mapToInt(p -> p.id).max().orElse(-1) + 1;
        CustomerNode newCustomer = new CustomerNode(cid, x, y);
        Parcel       newParcel   = new Parcel(pid, newCustomer);
        worldState.customers.add(newCustomer);
        worldState.parcels.add(newParcel);

        // Pick the active agent closest to the new customer that still has route
        // stops remaining and has free vehicle capacity.
        final double nx = x, ny = y;
        AgentState target = worldState.agents.stream()
                .filter(a -> a.status == AgentStatus.MOVING
                          && !a.remainingRoute.isEmpty()
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

        // Freeze only the chosen agent — all others continue unaffected.
        sendToAgent(target.name(), Protocol.FREEZE);

        final AgentState frozenAgent    = target;
        final Parcel     parcelToInsert = newParcel;

        addBehaviour(new WakerBehaviour(this, 300) {
            @Override protected void onWake() {
                // Splice PICKUP_AT_WAREHOUSE → DELIVER before the terminal RETURN stop.
                insertParcelIntoRoute(frozenAgent, parcelToInsert);
                // Send updated route then immediately resume.
                sendToAgent(frozenAgent.name(), serialiseRoute(frozenAgent));
                sendToAgent(frozenAgent.name(), Protocol.RESUME);
                window.log(frozenAgent.name() + " re-routed — warehouse pickup then C" + parcelToInsert.destination.id);
                window.refreshMap();
                window.setAddParcelEnabled(true);
                replanning = false;
            }
        });
    }

    private void insertParcelIntoRoute(AgentState agent, Parcel parcel) {
        java.util.List<RouteStop> route = agent.remainingRoute;
        // Pull off the terminal RETURN stop so we can append after the new delivery.
        RouteStop returnStop = null;
        if (!route.isEmpty()
                && route.get(route.size() - 1).type == StopType.RETURN_TO_WAREHOUSE) {
            returnStop = route.remove(route.size() - 1);
        }
        // Agent must visit the warehouse to collect the parcel before delivering it.
        route.add(new RouteStop(StopType.PICKUP_AT_WAREHOUSE, agent.homeWarehouse));
        route.add(new RouteStop(StopType.DELIVER, parcel.destination, parcel));
        route.add(returnStop != null ? returnStop
                : new RouteStop(StopType.RETURN_TO_WAREHOUSE, agent.homeWarehouse));
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
                if (a != null) window.log(a.name() + " returned to warehouse");
                returnedAgentCount++;
                if (returnedAgentCount >= dispatchedAgentCount) {
                    scheduleCompletion();
                }
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

        private void scheduleCompletion() {
            // Wait for the longest still-running return animation before closing out
            long now = System.currentTimeMillis();
            long longestRemaining = worldState.agents.stream()
                    .mapToLong(a -> (a.moveStartMs + a.moveDurationMs) - now)
                    .max().orElse(0);
            long delay = Math.max(longestRemaining, 0) + 600;

            addBehaviour(new WakerBehaviour(MasterRoutingAgent.this, delay) {
                @Override protected void onWake() {
                    window.log("=== All " + worldState.parcels.size()
                            + " parcels delivered — simulation complete ===");
                    window.onSimulationComplete();
                }
            });
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void sendToAgent(String agentName, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
    }
}
