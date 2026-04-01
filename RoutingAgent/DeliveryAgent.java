package RoutingAgent.RoutingAgent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.List;

public class DeliveryAgent extends Agent {

    private static final String MRA_LOCAL_NAME = "MRA";

    private static final String DELIVERY_AGENT_DF_TYPE = "delivery-agent";
    private static final String CAPACITY_CONVERSATION_ID = "vrp-capacity";
    private static final String CAPACITY_ACK_CONVERSATION_ID = "vrp-capacity-ack";
    private static final String ROUTE_CONVERSATION_ID = "vrp-route";
    private static final String ROUTE_PREFIX = "ROUTE:";

    private static final int MAX_CAPACITY_ATTEMPTS = 5;
    private static final long CAPACITY_RETRY_INTERVAL_MS = 1000;
    private static final long MOVE_SIMULATED_STEP_MS = 700;

    private DeliveryAgentGui myGui;

    private Integer capacity = null;
    private boolean capacityAckReceived = false;
    private int capacityAttempts = 0;
    private long lastCapacitySentAtMs = 0;

    private boolean executingRoute = false;
    private List<String> currentStops = new ArrayList<>();
    private int currentStopIndex = 0;

    private String shortLabel(ACLMessage msg) {
        String convId = msg.getConversationId();
        String content = msg.getContent();

        if (CAPACITY_CONVERSATION_ID.equals(convId)) return "CAPACITY";
        if (CAPACITY_ACK_CONVERSATION_ID.equals(convId)) return "ACK";
        if (ROUTE_CONVERSATION_ID.equals(convId)) return "ROUTE";
        return ACLMessage.getPerformative(msg.getPerformative());
    }

    private void logMessage(String direction, ACLMessage msg) {
        if (msg == null) return;

        String sender = msg.getSender() != null ? msg.getSender().getLocalName() : "null";
        String receiver = msg.getAllReceiver().hasNext()
                ? ((AID) msg.getAllReceiver().next()).getLocalName()
                : "none";

        String arrow = "SEND".equals(direction)
                ? sender + " -> " + receiver
                : sender + " -> " + getLocalName();

        System.out.println("[MSG][" + shortLabel(msg) + "] "
                + arrow
                + " | " + (msg.getContent() != null ? msg.getContent() : "<no-content>"));
    }

    protected void setup() {
        System.out.println(getLocalName() + " started.");

        myGui = new DeliveryAgentGui(this);
        myGui.show();

        registerService();

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate ackTemplate = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(CAPACITY_ACK_CONVERSATION_ID),
                        MessageTemplate.MatchSender(new AID(MRA_LOCAL_NAME, AID.ISLOCALNAME))
                );

                ACLMessage ack = myAgent.receive(ackTemplate);
                if (ack != null) {
                    capacityAckReceived = true;
                    logMessage("RECV", ack);
                    System.out.println(getLocalName() + " received capacity ACK from MRA.");
                    return;
                }

                if (capacity == null) {
                    block(200);
                    return;
                }

                if (!capacityAckReceived && capacityAttempts < MAX_CAPACITY_ATTEMPTS) {
                    long now = System.currentTimeMillis();
                    boolean shouldSendNow = capacityAttempts == 0
                            || (now - lastCapacitySentAtMs) >= CAPACITY_RETRY_INTERVAL_MS;

                    if (shouldSendNow) {
                        sendCapacityToMRA();
                    }
                } else if (!capacityAckReceived) {
                    System.out.println(getLocalName() + " capacity ACK not received after "
                            + MAX_CAPACITY_ATTEMPTS + " attempts; continuing anyway.");
                    capacityAckReceived = true;
                }

                block(200);
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId(ROUTE_CONVERSATION_ID);
                ACLMessage msg = myAgent.receive(mt);

                if (msg != null) {
                    String content = msg.getContent();
                    logMessage("RECV", msg);
                    System.out.println(getLocalName() + " received route: " + content);

                    if (content != null && content.startsWith(ROUTE_PREFIX)) {
                        String route = content.substring(ROUTE_PREFIX.length());
                        List<String> stops = parseStops(route);

                        if (executingRoute) {
                            System.out.println(getLocalName() + " is already executing a route; ignoring new one.");
                            return;
                        }

                        if (!stops.isEmpty()) {
                            startRouteExecution(stops);
                        } else {
                            System.out.println(getLocalName() + " received an empty route; nothing to do.");
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }

    public void updateCapacity(final int newCapacity) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                if (newCapacity <= 0) {
                    System.out.println(getLocalName() + " capacity must be > 0.");
                    return;
                }

                capacity = newCapacity;
                capacityAckReceived = false;
                capacityAttempts = 0;
                lastCapacitySentAtMs = 0;

                System.out.println(getLocalName() + " capacity set to " + capacity);
                sendCapacityToMRA();
            }
        });
    }

    private void registerService() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType(DELIVERY_AGENT_DF_TYPE);
            sd.setName(getLocalName());

            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private void sendCapacityToMRA() {
        if (capacity == null) return;

        capacityAttempts++;
        lastCapacitySentAtMs = System.currentTimeMillis();

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(MRA_LOCAL_NAME, AID.ISLOCALNAME));
        msg.setConversationId(CAPACITY_CONVERSATION_ID);
        msg.setContent("CAPACITY:" + capacity);

        send(msg);
        logMessage("SEND", msg);
        System.out.println(getLocalName() + " sent capacity to MRA (attempt "
                + capacityAttempts + "/" + MAX_CAPACITY_ATTEMPTS + "): " + capacity);
    }

    private List<String> parseStops(String route) {
        List<String> stops = new ArrayList<>();
        if (route == null || route.isBlank()) return stops;

        for (String stop : route.split(",")) {
            String cleaned = stop.trim();
            if (!cleaned.isEmpty()) stops.add(cleaned);
        }
        return stops;
    }

    private void startRouteExecution(List<String> stops) {
        executingRoute = true;
        currentStops = stops;
        currentStopIndex = 0;

        System.out.println(getLocalName() + " executing route...");

        addBehaviour(new TickerBehaviour(this, MOVE_SIMULATED_STEP_MS) {
            @Override
            protected void onTick() {
                if (currentStopIndex < currentStops.size()) {
                    String stop = currentStops.get(currentStopIndex++);
                    System.out.println(getLocalName() + " moving to stop " + stop);
                }

                if (currentStopIndex >= currentStops.size()) {
                    System.out.println(getLocalName() + " finished delivery.");
                    executingRoute = false;
                    currentStops = new ArrayList<>();
                    currentStopIndex = 0;
                    stop();
                }
            }
        });
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        if (myGui != null) {
            myGui.dispose();
        }

        System.out.println(getLocalName() + " terminating.");
    }
}