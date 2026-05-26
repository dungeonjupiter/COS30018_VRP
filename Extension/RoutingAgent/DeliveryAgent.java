package RoutingAgent.Extension.RoutingAgent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class DeliveryAgent extends Agent {
    public static final String CID_ROUTE = "vrp-route";
    public static final String CID_TRACKING = "vrp-tracking";
    public static final String CID_DONE = "vrp-done";

    private Point currentLocation = new Point(50, 50);
    private List<Point> remainingStops = new ArrayList<>();

    private int maxCapacity = 5;
    private int freeCapacity = 5;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        boolean showGui = false;

        if (args != null && args.length > 0) {
            String[] parts = args[0].toString().split(",");
            currentLocation = new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            int initCap = Integer.parseInt(parts[2]);

            // Check if MRA requested the Capacity GUI
            if (args.length > 1 && args[1].toString().equals("SHOW_GUI")) {
                showGui = true;
            } else {
                updateCapacity(initCap);
            }
        }

        if (showGui) {
            System.out.println(getLocalName() + " popping up GUI for capacity...");
            java.awt.EventQueue.invokeLater(() -> {
                DeliveryAgentGui gui = new DeliveryAgentGui(this);
                gui.setVisible(true);
            });
        }

        // Asynchronous Movement Simulator (Never stops)
        addBehaviour(new TickerBehaviour(this, 400) {
            @Override
            protected void onTick() {
                if (!remainingStops.isEmpty()) {
                    Point target = remainingStops.get(0);
                    double dist = currentLocation.distance(target);

                    if (dist <= 5.0) {
                        currentLocation.setLocation(target);
                        remainingStops.remove(0);
                        if (remainingStops.isEmpty()) {
                            sendGpsPing();
                            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                            msg.addReceiver(new jade.core.AID("MRA", jade.core.AID.ISLOCALNAME));
                            msg.setConversationId(CID_DONE);
                            send(msg);
                            return;
                        }
                    } else {
                        double dx = target.x - currentLocation.x;
                        double dy = target.y - currentLocation.y;
                        currentLocation.translate((int)(dx / dist * 5), (int)(dy / dist * 5));
                    }
                    sendGpsPing();
                }
            }
        });

        // Listen for Ghost Routes
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId(CID_ROUTE);
                ACLMessage msg = receive(mt);
                if (msg != null) {
                    String content = msg.getContent().substring(10);
                    String[] coords = content.split(",");
                    List<Point> newRoute = new ArrayList<>();
                    for (String c : coords) {
                        String[] xy = c.split(":");
                        newRoute.add(new Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
                    }
                    remainingStops = newRoute;
                } else { block(); }
            }
        });
    }

    public void updateCapacity(int cap) {
        this.maxCapacity = cap;
        this.freeCapacity = cap;
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new jade.core.AID("MRA", jade.core.AID.ISLOCALNAME));
        msg.setConversationId("vrp-capacity");
        msg.setContent("CAPACITY:" + cap);
        send(msg);
    }

    private void sendGpsPing() {
        ACLMessage ping = new ACLMessage(ACLMessage.INFORM);
        ping.addReceiver(new jade.core.AID("MRA", jade.core.AID.ISLOCALNAME));
        ping.setConversationId(CID_TRACKING);
        StringBuilder sb = new StringBuilder();
        sb.append(currentLocation.x).append(",").append(currentLocation.y).append("|");
        for (int i = 0; i < remainingStops.size(); i++) {
            sb.append(remainingStops.get(i).x).append(",").append(remainingStops.get(i).y);
            if (i < remainingStops.size() - 1) sb.append(";");
        }
        ping.setContent(sb.toString());
        send(ping);
    }
}