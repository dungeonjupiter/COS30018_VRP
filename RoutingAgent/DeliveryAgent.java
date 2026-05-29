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

/**
 * DeliveryAgent (DA)
 * * Represents an individual physical vehicle in the Vehicle Routing Problem (VRP).
 * Responsibilities:
 * 1. Wait for user input (via GUI) or programmatic arguments to set its maximum capacity.
 * 2. Transmit its capacity to the Master Routing Agent (MRA) with robust retry logic.
 * 3. Receive an optimized route string from the MRA.
 * 4. Simulate the physical driving process by stepping through the route with a time delay.
 */
public class DeliveryAgent extends Agent {

    // --- Communication Target ---
    private static final String MRA_LOCAL_NAME = "MRA";

    // --- Directory Facilitator (DF) & Conversation IDs ---
    private static final String DELIVERY_AGENT_DF_TYPE = "delivery-agent";
    private static final String CAPACITY_CONVERSATION_ID = "vrp-capacity";
    private static final String CAPACITY_ACK_CONVERSATION_ID = "vrp-capacity-ack";
    private static final String ROUTE_CONVERSATION_ID = "vrp-route";
    private static final String ROUTE_PREFIX = "ROUTE:";

    // --- Robustness & Simulation Parameters ---
    private static final int MAX_CAPACITY_ATTEMPTS = 5;
    private static final long CAPACITY_RETRY_INTERVAL_MS = 1000;
    private static final long MOVE_SIMULATED_STEP_MS = 700; // Simulated driving time per stop

    private DeliveryAgentGui myGui;

    // --- State Tracking Variables ---
    private Integer capacity = null;
    private boolean capacityAckReceived = false;
    private int capacityAttempts = 0;
    private long lastCapacitySentAtMs = 0;

    private boolean executingRoute = false;
    private List<String> currentStops = new ArrayList<>();
    private int currentStopIndex = 0;

    /**
     * Helper to generate a short string label for log messages based on the Conversation ID.
     */
    private String shortLabel(ACLMessage msg) {
        String convId = msg.getConversationId();

        if (CAPACITY_CONVERSATION_ID.equals(convId)) return "CAPACITY";
        if (CAPACITY_ACK_CONVERSATION_ID.equals(convId)) return "ACK";
        if (ROUTE_CONVERSATION_ID.equals(convId)) return "ROUTE";
        return ACLMessage.getPerformative(msg.getPerformative());
    }

    /**
     * Standardized console logger for agent-to-agent communication.
     */
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

    /**
     * Agent setup routine. Called automatically by JADE when the agent starts.
     */
    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        // Initialize the GUI so the user can input this agent's specific capacity
        myGui = new DeliveryAgentGui(this);
        myGui.show();

        registerService();

        /*
         * BEHAVIOUR 1: Capacity Management & Retry Loop
         * Listens for an ACK from the MRA confirming capacity was received.
         * If no ACK is received (e.g., MRA hasn't spawned yet), it retries automatically.
         */
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                // Look for an ACK specifically from the MRA
                MessageTemplate ackTemplate = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(CAPACITY_ACK_CONVERSATION_ID),
                        MessageTemplate.MatchSender(new AID(MRA_LOCAL_NAME, AID.ISLOCALNAME))
                );

                ACLMessage ack = myAgent.receive(ackTemplate);
                if (ack != null) {
                    capacityAckReceived = true;
                    logMessage("RECV", ack);
                    System.out.println(getLocalName() + " received capacity ACK from MRA.");
                    return; // ACK received, nothing more to do this cycle
                }

                // If capacity hasn't been set by the GUI yet, pause and wait
                if (capacity == null) {
                    block(200);
                    return;
                }

                // Robustness: If no ACK received, send/resend capacity at intervals
                if (!capacityAckReceived && capacityAttempts < MAX_CAPACITY_ATTEMPTS) {
                    long now = System.currentTimeMillis();
                    boolean shouldSendNow = capacityAttempts == 0
                            || (now - lastCapacitySentAtMs) >= CAPACITY_RETRY_INTERVAL_MS;

                    if (shouldSendNow) {
                        sendCapacityToMRA();
                    }
                } else if (!capacityAckReceived) {
                    // Give up retrying after max attempts to prevent infinite loops
                    System.out.println(getLocalName() + " capacity ACK not received after "
                            + MAX_CAPACITY_ATTEMPTS + " attempts; continuing anyway.");
                    capacityAckReceived = true;
                }

                block(200); // Prevent high CPU usage while waiting
            }
        });

        /*
         * BEHAVIOUR 2: Route Execution Listener
         * Listens for the optimized route dispatch from the MRA and triggers the driving simulation.
         */
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

                        // Safety check: Don't accept a new route if currently driving
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
                    block(); // Yield CPU until a new message arrives
                }
            }
        });
    }

    /**
     * Triggered by the GUI when the user inputs a capacity and clicks "Set".
     */
    public void updateCapacity(final int newCapacity) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                if (newCapacity <= 0) {
                    System.out.println(getLocalName() + " capacity must be > 0.");
                    return;
                }

                // Reset state to begin a fresh transmission to the MRA
                capacity = newCapacity;
                capacityAckReceived = false;
                capacityAttempts = 0;
                lastCapacitySentAtMs = 0;

                System.out.println(getLocalName() + " capacity set to " + capacity);
                sendCapacityToMRA();
            }
        });
    }

    /**
     * Registers the DA in the JADE Directory Facilitator (Yellow Pages).
     */
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

    /**
     * Constructs and fires the capacity information message to the MRA.
     */
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

    /**
     * Helper to split the MRA's comma-separated route string into an iterable list.
     */
    private List<String> parseStops(String route) {
        List<String> stops = new ArrayList<>();
        if (route == null || route.isBlank()) return stops;

        for (String stop : route.split(",")) {
            String cleaned = stop.trim();
            if (!cleaned.isEmpty()) stops.add(cleaned);
        }
        return stops;
    }

    /**
     * Simulates the physical driving process.
     * Uses a TickerBehaviour to delay between node visits, mimicking actual travel time.
     */
    private void startRouteExecution(List<String> stops) {
        executingRoute = true;
        currentStops = stops;
        currentStopIndex = 0;

        System.out.println(getLocalName() + " executing route...");

        addBehaviour(new TickerBehaviour(this, MOVE_SIMULATED_STEP_MS) {
            @Override
            protected void onTick() {
                // Step to the next node in the route
                if (currentStopIndex < currentStops.size()) {
                    String stop = currentStops.get(currentStopIndex++);
                    System.out.println(getLocalName() + " moving to stop " + stop);
                }

                // Route completed
                if (currentStopIndex >= currentStops.size()) {
                    System.out.println(getLocalName() + " finished delivery.");
                    executingRoute = false;
                    currentStops = new ArrayList<>();
                    currentStopIndex = 0;
                    stop(); // Terminate this TickerBehaviour
                }
            }
        });
    }

    /**
     * Agent termination routine. Deregisters from the DF and closes the GUI.
     */
    @Override
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