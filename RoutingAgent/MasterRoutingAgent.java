package RoutingAgent.RoutingAgent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import jade.core.behaviours.WakerBehaviour;

public class MasterRoutingAgent extends Agent {
    private static final String MASTER_DF_TYPE = "vrp-master-routing";
    private static final String MASTER_DF_NAME = "MRA";
    private static final String CAPACITY_CONVERSATION_ID = "vrp-capacity";
    private static final String CAPACITY_ACK_CONVERSATION_ID = "vrp-capacity-ack";
    private static final String ROUTE_CONVERSATION_ID = "vrp-route";
    private static final String CAPACITY_PREFIX = "CAPACITY:";
    private static final String ROUTE_PREFIX = "ROUTE:";
    private static final int DEFAULT_EXPECTED_AGENTS = 4;
    private boolean calculationTriggered = false;

    private final Map<String, Integer> capacities = new LinkedHashMap<>();
    private final List<String> agentNames = new ArrayList<>();

    private int expectedAgents = DEFAULT_EXPECTED_AGENTS;   // default
    private boolean routesSent = false;

    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                expectedAgents = Integer.parseInt(args[0].toString());
            } catch (Exception e) {
                System.out.println("MRA invalid expectedAgents arg, using default " + DEFAULT_EXPECTED_AGENTS);
            }
        }
        if (expectedAgents <= 0) {
            System.out.println("MRA expectedAgents must be > 0, using default " + DEFAULT_EXPECTED_AGENTS);
            expectedAgents = DEFAULT_EXPECTED_AGENTS;
        }

        System.out.println("MRA started. Expecting " + expectedAgents + " delivery agents.");

        registerService();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId(CAPACITY_CONVERSATION_ID);
                ACLMessage msg = myAgent.receive(mt);

                if (msg != null) {
                    handleCapacityMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void registerService() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType(MASTER_DF_TYPE);
            sd.setName(MASTER_DF_NAME);

            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private void handleCapacityMessage(ACLMessage msg) {
        String senderName = msg.getSender().getLocalName();
        String content = msg.getContent();

        if (content != null && content.startsWith(CAPACITY_PREFIX)) {
            Integer cap = parseCapacity(content);
            if (cap != null && cap > 0) {
                sendAck(msg);

                if (!capacities.containsKey(senderName)) {
                    capacities.put(senderName, cap);
                    agentNames.add(senderName);
                    System.out.println("MRA received capacity from " + senderName + ": " + cap);

                    // FIX: Add a small delay (1 second) after the last expected agent arrives
                    // to allow for JADE messaging overhead and prevent race conditions.
                    if (capacities.size() >= expectedAgents && !calculationTriggered) {
                        calculationTriggered = true;
                        System.out.println("MRA: Fleet complete. Starting GA in 1s...");
                        addBehaviour(new WakerBehaviour(this, 1000) {
                            protected void onWake() {
                                if (!routesSent) {
                                    routesSent = true;
                                    calculateOptimalRoutes(); //
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private Integer parseCapacity(String content) {
        if (content == null || !content.startsWith(CAPACITY_PREFIX)) return null;
        String payload = content.substring(CAPACITY_PREFIX.length()).trim();
        if (payload.isEmpty()) return null;
        try {
            return Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendAck(ACLMessage receivedMsg) {
        ACLMessage reply = receivedMsg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(CAPACITY_ACK_CONVERSATION_ID);
        reply.setContent("ACK:capacity received");
        send(reply);
    }

    private void assignDummyRoutes() {
        System.out.println("MRA has received all capacities. Assigning dummy routes...");

        for (int i = 0; i < agentNames.size(); i++) {
            String agentName = agentNames.get(i);

            // Dummy route for testing only.
            // Replace this later with solver output.
            String route;
            if (i == 0) {
                route = "0,1,2,0";
            } else if (i == 1) {
                route = "0,3,4,0";
            } else if (i == 2) {
                route = "0,5,0";
            } else {
                route = "0," + (i + 1) + ",0";
            }

            ACLMessage routeMsg = new ACLMessage(ACLMessage.INFORM);
            routeMsg.addReceiver(new jade.core.AID(agentName, jade.core.AID.ISLOCALNAME));
            routeMsg.setConversationId(ROUTE_CONVERSATION_ID);
            routeMsg.setContent(ROUTE_PREFIX + route);

            send(routeMsg);

            System.out.println("MRA sent route to " + agentName + ": " + route);
        }
    }

    // Inside MasterRoutingAgent.java
    private void calculateOptimalRoutes() {
        try {
            System.out.println("MRA: Invoking Genetic Algorithm solver...");

            // Prepare arguments for Python: "Agent1,Agent2" "5`,5"
            String namesArg = String.join(",", agentNames);
            String capsArg = capacities.values().toString().replaceAll("[\\[\\] ]", "");

            ProcessBuilder pb = new ProcessBuilder("python", "GA.py", namesArg, capsArg);
            pb.redirectErrorStream(true); // Merge error stream to see Python errors in Java console
            Process p = pb.start();

            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = "";
            String line;
            while ((line = in.readLine()) != null) {
                result = line; // The last line printed by Python is our route string
            }

            if (result != null && result.contains(":")) {
                parseAndSendRoutes(result);
            } else {
                System.out.println("MRA: GA returned invalid result, using dummy routes.");
                assignDummyRoutes();
            }

        } catch (IOException e) {
            System.err.println("MRA: Failed to run Python script. Ensure 'python' is in your PATH.");
            assignDummyRoutes();
        }
    }

    private void parseAndSendRoutes(String result) {
        // Expected format: Agent1:0,1,2,0|Agent2:0,3,4,0
        String[] individualRoutes = result.split("\\|");

        for (String routeEntry : individualRoutes) {
            String[] parts = routeEntry.split(":");
            if (parts.length == 2) {
                String targetAgent = parts[0];
                String routePoints = parts[1];

                ACLMessage routeMsg = new ACLMessage(ACLMessage.INFORM);
                routeMsg.addReceiver(new jade.core.AID(targetAgent, jade.core.AID.ISLOCALNAME));
                routeMsg.setConversationId(ROUTE_CONVERSATION_ID);
                routeMsg.setContent(ROUTE_PREFIX + routePoints);

                send(routeMsg);
                System.out.println("MRA: Sent optimized route to " + targetAgent + ": " + routePoints);
            }
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("MRA terminating.");
    }
}