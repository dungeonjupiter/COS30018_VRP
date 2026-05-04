package RoutingAgent.Extension.RoutingAgent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Distance-oriented delivery: reports position and free/max capacity on FREEZE;
 * executes ROUTE as a sequence of customer coordinates until each stop is reached.
 */
public class DeliveryAgent extends Agent {

    public static final String CID_FREEZE = "vrp-freeze";
    public static final String CID_ROUTE = "vrp-route";
    public static final String FREEZE_CONTENT = "FREEZE";
    private static final String PREFIX_STATUS = "STATUS:";
    private static final String PREFIX_ROUTE = "ROUTE:";

    private static final int MOVE_STEP = 2;
    private static final long TICK_MS = 400L;

    private int maxCapacity = 5;
    private int freeCapacity = 5;
    private Point currentLocation = new Point(50, 50);
    private final List<Point> remainingStops = new ArrayList<>();
    private MovementBehaviour moveSim;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1 && args[0] instanceof String) {
            String[] parts = ((String) args[0]).split(",");
            if (parts.length >= 2) {
                currentLocation.x = Integer.parseInt(parts[0].trim());
                currentLocation.y = Integer.parseInt(parts[1].trim());
            }
            if (parts.length >= 3) {
                maxCapacity = Integer.parseInt(parts[2].trim());
                freeCapacity = Math.min(freeCapacity, maxCapacity);
            }
        }
        System.out.println(getLocalName() + " online at (" + currentLocation.x + "," + currentLocation.y
                + ") capacity " + freeCapacity + "/" + maxCapacity + ".");

        MessageTemplate freezeTpl = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.and(
                        MessageTemplate.MatchContent(FREEZE_CONTENT),
                        MessageTemplate.MatchConversationId(CID_FREEZE)));
        MessageTemplate routeTpl = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                MessageTemplate.MatchConversationId(CID_ROUTE));

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(freezeTpl);
                if (msg != null) {
                    handleFreeze(msg);
                    return;
                }
                msg = receive(routeTpl);
                if (msg != null) {
                    handleRoute(msg);
                    return;
                }
                block(MessageTemplate.or(freezeTpl, routeTpl));
            }
        });
    }

    private void handleFreeze(ACLMessage freezeMsg) {
        System.out.println(getLocalName() + ": FREEZE — halting.");
        if (moveSim != null) {
            moveSim.stop();
            removeBehaviour(moveSim);
            moveSim = null;
        }

        ACLMessage reply = freezeMsg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(CID_FREEZE);
        reply.setContent(formatStatus());
        send(reply);
    }

    private String formatStatus() {
        StringBuilder sb = new StringBuilder(PREFIX_STATUS);
        sb.append(currentLocation.x).append(':').append(currentLocation.y).append(':')
                .append(freeCapacity).append(':').append(maxCapacity).append(':');
        for (int i = 0; i < remainingStops.size(); i++) {
            if (i > 0) sb.append(',');
            Point p = remainingStops.get(i);
            sb.append(p.x).append(':').append(p.y);
        }
        return sb.toString();
    }

    private void handleRoute(ACLMessage routeMsg) {
        String raw = routeMsg.getContent();
        System.out.println(getLocalName() + ": ROUTE received.");
        if (moveSim != null) {
            moveSim.stop();
            removeBehaviour(moveSim);
            moveSim = null;
        }
        remainingStops.clear();
        if (raw != null && raw.startsWith(PREFIX_ROUTE)) {
            parseRoutePayload(raw.substring(PREFIX_ROUTE.length()).trim());
        }
        if (!remainingStops.isEmpty()) {
            moveSim = new MovementBehaviour(this, TICK_MS);
            addBehaviour(moveSim);
        }
    }

    /**
     * Payload: optional {@code free:max|} then {@code x:y,x:y,...} or only coordinates.
     */
    private void parseRoutePayload(String body) {
        String coords = body;
        int pipe = body.indexOf('|');
        if (pipe >= 0) {
            String head = body.substring(0, pipe).trim();
            coords = body.substring(pipe + 1).trim();
            String[] hm = head.split(":");
            if (hm.length >= 2) {
                freeCapacity = clamp(Integer.parseInt(hm[0].trim()), 0, maxCapacity);
                if (hm.length >= 3) {
                    maxCapacity = Math.max(1, Integer.parseInt(hm[1].trim()));
                    freeCapacity = Math.min(freeCapacity, maxCapacity);
                }
            }
        }
        if (coords.isEmpty()) return;
        for (String token : coords.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            String[] xy = t.split(":");
            if (xy.length >= 2) {
                int x = Integer.parseInt(xy[0].trim());
                int y = Integer.parseInt(xy[1].trim());
                remainingStops.add(new Point(x, y));
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private class MovementBehaviour extends TickerBehaviour {
        MovementBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (remainingStops.isEmpty()) {
                stop();
                moveSim = null;
                return;
            }
            Point target = remainingStops.get(0);
            stepToward(target);
            if (currentLocation.distance(target) <= MOVE_STEP + 0.01) {
                currentLocation.setLocation(target);
                remainingStops.remove(0);
                freeCapacity = Math.min(maxCapacity, freeCapacity + 1);
                System.out.println(getLocalName() + ": delivered at (" + target.x + "," + target.y
                        + ") free=" + freeCapacity + "/" + maxCapacity + ".");
            }
        }

        private void stepToward(Point target) {
            int dx = Integer.compare(target.x - currentLocation.x, 0) * MOVE_STEP;
            int dy = Integer.compare(target.y - currentLocation.y, 0) * MOVE_STEP;
            if (Math.abs(target.x - currentLocation.x) < MOVE_STEP) dx = target.x - currentLocation.x;
            if (Math.abs(target.y - currentLocation.y) < MOVE_STEP) dy = target.y - currentLocation.y;
            currentLocation.translate(dx, dy);
        }
    }
}
