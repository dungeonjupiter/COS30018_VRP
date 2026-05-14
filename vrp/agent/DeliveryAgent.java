package RoutingAgent.vrp.agent;

import RoutingAgent.vrp.model.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class DeliveryAgent extends Agent {

    private WorldState worldState;
    private AgentState agentState;
    private volatile boolean frozen = false;

    // Incremented on each new route dispatch; stale WakerBehaviours check this
    // to avoid double-moves when a replan fires mid-journey.
    private volatile int routeGeneration = 0;

    private static final double MOVE_SPEED = 6;  // map units per second (40 % slower than original 12.0)
    private static final long   DWELL_MS   = 300;  // pause at each delivery stop

    @Override
    protected void setup() {
        Object[] args = getArguments();
        worldState = (WorldState) args[0];
        int agentId = (int) args[1];
        agentState  = worldState.findAgent(agentId);
        addBehaviour(new MessageListenerBehaviour());
    }

    // ── message listener ──────────────────────────────────────────────────────

    private class MessageListenerBehaviour extends CyclicBehaviour {
        private final MessageTemplate MT =
                MessageTemplate.MatchPerformative(ACLMessage.INFORM);

        @Override
        public void action() {
            ACLMessage msg = receive(MT);
            if (msg == null) { block(); return; }

            String content = msg.getContent();
            if (content.startsWith(Protocol.NEW_ROUTE)) {
                parseAndLoadRoute(content);
            } else if (content.equals(Protocol.FREEZE)) {
                frozen = true;
                agentState.status = AgentStatus.FROZEN;
                sendStateReport();
            } else if (content.equals(Protocol.RESUME)) {
                frozen = false;
                if (!agentState.remainingRoute.isEmpty()) {
                    agentState.status = AgentStatus.MOVING;
                    scheduleMove(0); // restart movement chain
                }
            }
        }

        private void parseAndLoadRoute(String content) {
            // If frozen mid-animation, snap currentX/Y to the actual visual position
            // so the next doMove() departs from where the dot really is, not from the
            // old destination that was never reached.
            if (frozen && agentState.moveDurationMs > 0) {
                long now = System.currentTimeMillis();
                double t = Math.min(1.0,
                        (double)(now - agentState.moveStartMs) / agentState.moveDurationMs);
                agentState.currentX = agentState.prevX + (agentState.currentX - agentState.prevX) * t;
                agentState.currentY = agentState.prevY + (agentState.currentY - agentState.prevY) * t;
                agentState.prevX = agentState.currentX;
                agentState.prevY = agentState.currentY;
                agentState.moveDurationMs = 0;
            }

            String[] parts = content.split("\\|");
            agentState.remainingRoute.clear();

            for (int i = 1; i < parts.length; i++) {
                String[] f    = parts[i].split(",");
                StopType type = StopType.valueOf(f[0]);
                double   x    = Double.parseDouble(f[1]);
                double   y    = Double.parseDouble(f[2]);
                int      pid  = Integer.parseInt(f[3]);

                Location target;
                Parcel   parcel = null;

                if (pid >= 0) {
                    final int id = pid;
                    parcel = worldState.parcels.stream()
                            .filter(p -> p.id == id).findFirst().orElse(null);
                    target = (parcel != null) ? parcel.destination : worldState.warehouse;
                } else {
                    target = worldState.warehouse;
                }
                agentState.remainingRoute.add(new RouteStop(type, target, parcel));
            }

            // Bump generation so any in-flight WakerBehaviours from the old route
            // will see a mismatch and skip their move.
            routeGeneration++;
            agentState.status = AgentStatus.MOVING;

            // Only self-start if not frozen; if frozen, RESUME will call scheduleMove.
            if (!frozen) scheduleMove(100);
        }
    }

    // ── movement ──────────────────────────────────────────────────────────────

    private void scheduleMove(long delayMs) {
        final int gen = routeGeneration;
        addBehaviour(new WakerBehaviour(this, delayMs) {
            @Override protected void onWake() {
                if (gen == routeGeneration) doMove();
                // else: stale waker from old route — discard silently
            }
        });
    }

    private void doMove() {
        if (agentState.remainingRoute.isEmpty()) return;
        if (frozen) return; // RESUME will restart the chain via scheduleMove(0)

        RouteStop next = agentState.remainingRoute.remove(0);

        // Record departure for smooth animation
        agentState.prevX = agentState.currentX;
        agentState.prevY = agentState.currentY;
        double dist = Math.hypot(next.target.x - agentState.prevX,
                                 next.target.y - agentState.prevY);
        agentState.moveDurationMs = (long) (dist / MOVE_SPEED * 1000);
        agentState.moveStartMs    = System.currentTimeMillis();
        agentState.currentX = next.target.x;
        agentState.currentY = next.target.y;

        if (next.type == StopType.DELIVER && next.parcel != null) {
            agentState.inVehicle.remove(next.parcel);
            notifyMRA(Protocol.DELIVERY_COMPLETE
                    + "|" + agentState.id + "|" + next.parcel.id);
            scheduleMove(agentState.moveDurationMs + DWELL_MS);

        } else if (next.type == StopType.PICKUP_AT_WAREHOUSE) {
            // Agent arrived at warehouse to collect a dynamically added parcel
            scheduleMove(agentState.moveDurationMs + DWELL_MS);

        } else if (next.type == StopType.RETURN_TO_WAREHOUSE) {
            // Defer both STANDBY and ALL_DELIVERED until the return animation completes.
            // Sending ALL_DELIVERED immediately would let MRA re-dispatch or mark the agent
            // idle before it physically arrives at the warehouse.
            final long returnDuration = agentState.moveDurationMs;
            final int  gen            = routeGeneration;
            addBehaviour(new WakerBehaviour(this, returnDuration + 100) {
                @Override protected void onWake() {
                    if (gen == routeGeneration) {
                        agentState.status = AgentStatus.STANDBY;
                        notifyMRA(Protocol.ALL_DELIVERED + "|" + agentState.id);
                    }
                }
            });
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void notifyMRA(String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("MRA", AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
    }

    private void sendStateReport() {
        StringBuilder sb = new StringBuilder(Protocol.STATE_REPORT)
                .append("|").append(agentState.id)
                .append("|").append(agentState.currentX)
                .append(",").append(agentState.currentY)
                .append("|").append(agentState.status);
        for (Parcel p : agentState.inVehicle) sb.append("|P").append(p.id);
        notifyMRA(sb.toString());
    }
}
