package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.ALNSEngine;
import RoutingAgent.Extension.Solver.GreedyEngine;
import RoutingAgent.Extension.Solver.Parcel;
import RoutingAgent.Extension.Solver.RouteState;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates delivery agents: FREEZE fleet, merge STATUS into a {@link RouteState},
 * runs greedy + ALNS on distance, then dispatches per-agent ROUTE proposals.
 */
public class MasterRoutingAgent extends Agent {

    public static final String CID_FREEZE = "vrp-freeze";
    public static final String CID_ROUTE = "vrp-route";
    public static final String FREEZE_CONTENT = "FREEZE";
    private static final String PREFIX_STATUS = "STATUS:";
    private static final String PREFIX_ROUTE = "ROUTE:";

    private final GreedyEngine greedyEngine = new GreedyEngine();
    private final ALNSEngine alnsEngine = new ALNSEngine();
    private final List<AID> fleet = new ArrayList<>();
    private Point depot = new Point(50, 50);
    private long freezeTimeoutMs = 8000L;
    private long alnsTimeMs = 1500L;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1 && args[0] instanceof String) {
            for (String name : ((String) args[0]).split(",")) {
                String n = name.trim();
                if (!n.isEmpty()) fleet.add(new AID(n, AID.ISLOCALNAME));
            }
        }
        if (args != null && args.length >= 2 && args[1] instanceof String) {
            String[] d = ((String) args[1]).split(",");
            if (d.length >= 2) {
                depot.x = Integer.parseInt(d[0].trim());
                depot.y = Integer.parseInt(d[1].trim());
            }
        }
        if (fleet.isEmpty()) {
            fleet.add(new AID("DA1", AID.ISLOCALNAME));
            fleet.add(new AID("DA2", AID.ISLOCALNAME));
        }
        System.out.println("MRA online. Depot (" + depot.x + "," + depot.y + ") fleet=" + fleet.size() + ".");
    }

    /** GUI or another agent can trigger dynamic replanning. */
    public void injectDynamicParcel(Parcel newParcel) {
        addBehaviour(new FreezeOptimizeDispatchBehaviour(newParcel));
    }

    private final class FreezeOptimizeDispatchBehaviour extends Behaviour {
        private final Parcel parcel;
        private int phase = 0;
        private final Map<String, AgentStatus> pending = new LinkedHashMap<>();
        private long waitUntil = 0L;
        private RouteState optimized;

        FreezeOptimizeDispatchBehaviour(Parcel parcel) {
            this.parcel = parcel;
        }

        @Override
        public void action() {
            switch (phase) {
                case 0 -> sendFreeze();
                case 1 -> collectReplies();
                case 2 -> runSolversAndDispatch();
                default -> { /* done */ }
            }
        }

        private void sendFreeze() {
            System.out.println("\nMRA: FREEZE for parcel " + parcel.getId() + " (" + fleet.size() + " agents).");
            pending.clear();
            for (AID aid : fleet) pending.put(aid.getLocalName(), null);

            ACLMessage m = new ACLMessage(ACLMessage.REQUEST);
            m.setContent(FREEZE_CONTENT);
            m.setConversationId(CID_FREEZE);
            for (AID aid : fleet) m.addReceiver(aid);
            send(m);
            waitUntil = System.currentTimeMillis() + freezeTimeoutMs;
            phase = 1;
        }

        private void collectReplies() {
            MessageTemplate tpl = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId(CID_FREEZE));
            ACLMessage msg;
            while ((msg = receive(tpl)) != null) {
                if (msg.getSender() == null) continue;
                String name = msg.getSender().getLocalName();
                if (!pending.containsKey(name)) continue;
                String c = msg.getContent();
                if (c != null && c.startsWith(PREFIX_STATUS)) {
                    try {
                        pending.put(name, parseStatus(c));
                    } catch (Exception ex) {
                        System.err.println("MRA: bad STATUS from " + name + ": " + c);
                    }
                }
            }
            boolean all = pending.values().stream().allMatch(s -> s != null);
            if (all || System.currentTimeMillis() > waitUntil) {
                if (!all) {
                    System.err.println("MRA: freeze timeout; missing "
                            + pending.entrySet().stream().filter(e -> e.getValue() == null).count() + " replies.");
                }
                phase = 2;
            } else {
                block();
            }
        }

        private void runSolversAndDispatch() {
            RouteState state = mergeFleetState(pending);
            System.out.println("MRA: pre-insert distance=" + state.getTotalDistance());
            RouteState greedy = greedyEngine.insertNewParcel(parcel, state);
            System.out.println("MRA: post-greedy distance=" + greedy.getTotalDistance());
            optimized = alnsEngine.optimize(greedy, alnsTimeMs);
            System.out.println("MRA: post-ALNS distance=" + optimized.getTotalDistance());
            dispatch(optimized, pending);
            phase = 3;
        }

        @Override
        public boolean done() {
            return phase >= 3;
        }
    }

    private static class AgentStatus {
        final Point location;
        final int freeCapacity;
        final int maxCapacity;
        final List<Point> tailStops;

        AgentStatus(Point location, int freeCapacity, int maxCapacity, List<Point> tailStops) {
            this.location = location;
            this.freeCapacity = freeCapacity;
            this.maxCapacity = maxCapacity;
            this.tailStops = tailStops;
        }
    }

    /** {@code STATUS:x:y:free:max:x1:y1,x2:y2,...} */
    private static AgentStatus parseStatus(String content) {
        String rest = content.substring(PREFIX_STATUS.length());
        String[] headTail = rest.split(":", 5);
        if (headTail.length < 4) throw new IllegalArgumentException("STATUS head");
        int x = Integer.parseInt(headTail[0].trim());
        int y = Integer.parseInt(headTail[1].trim());
        int free = Integer.parseInt(headTail[2].trim());
        int max = Integer.parseInt(headTail[3].trim());
        List<Point> stops = new ArrayList<>();
        if (headTail.length == 5 && !headTail[4].isBlank()) {
            for (String token : headTail[4].split(",")) {
                String t = token.trim();
                if (t.isEmpty()) continue;
                String[] xy = t.split(":");
                if (xy.length >= 2) {
                    stops.add(new Point(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())));
                }
            }
        }
        return new AgentStatus(new Point(x, y), free, max, stops);
    }

    private RouteState mergeFleetState(Map<String, AgentStatus> byName) {
        RouteState state = new RouteState();
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            AgentStatus st = byName.get(name);
            Point start = st != null ? st.location : new Point(depot);
            state.addAgent(name, start);
            List<Point> route = state.getRoutes().get(name);
            if (st != null) {
                for (Point p : st.tailStops) {
                    state.insertNode(name, route.size(), new Point(p));
                }
            }
        }
        return state;
    }

    private void dispatch(RouteState state, Map<String, AgentStatus> lastKnown) {
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            List<Point> pts = state.getRoutes().get(name);
            if (pts == null || pts.size() < 2) {
                ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
                m.addReceiver(aid);
                m.setConversationId(CID_ROUTE);
                m.setContent(PREFIX_ROUTE);
                send(m);
                continue;
            }
            List<Point> stops = new ArrayList<>(pts);
            Point first = stops.get(0);
            while (stops.size() > 1 && stops.get(1).equals(first)) {
                stops.remove(1);
            }
            StringBuilder coords = new StringBuilder();
            for (int i = 1; i < stops.size(); i++) {
                Point p = stops.get(i);
                if (i > 1) coords.append(',');
                coords.append(p.x).append(':').append(p.y);
            }
            AgentStatus prev = lastKnown.get(name);
            int free = prev != null ? prev.freeCapacity : 0;
            int max = prev != null ? prev.maxCapacity : 0;
            if (prev == null) {
                max = 5;
                free = 5;
            }
            String payload = PREFIX_ROUTE + free + ":" + max + "|" + coords;

            ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
            m.addReceiver(aid);
            m.setConversationId(CID_ROUTE);
            m.setContent(payload);
            send(m);
            System.out.println("MRA -> " + name + ": " + payload);
        }
    }
}
