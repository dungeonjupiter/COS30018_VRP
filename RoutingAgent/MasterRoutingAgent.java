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

public class MasterRoutingAgent extends Agent {
    private static final String MASTER_DF_TYPE = "vrp-master-routing";
    private static final String MASTER_DF_NAME = "MRA";
    private static final String CAPACITY_CONVERSATION_ID = "vrp-capacity";
    private static final String CAPACITY_ACK_CONVERSATION_ID = "vrp-capacity-ack";
    private static final String ROUTE_CONVERSATION_ID = "vrp-route";
    private static final String CAPACITY_PREFIX = "CAPACITY:";
    private static final String ROUTE_PREFIX = "ROUTE:";
    private static final int DEFAULT_EXPECTED_AGENTS = 3;

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
            if (cap == null) {
                System.out.println("MRA ignored malformed capacity message from " + senderName + ": " + content);
                return;
            }
            if (cap <= 0) {
                System.out.println("MRA ignored non-positive capacity from " + senderName + ": " + cap);
                return;
            }

            // Always ACK valid messages so retried DAs can stop retrying.
            sendAck(msg);

            if (!capacities.containsKey(senderName)) {
                capacities.put(senderName, cap);
                agentNames.add(senderName);

                System.out.println("MRA received capacity from " + senderName + ": " + cap);

                if (capacities.size() >= expectedAgents && !routesSent) {
                    assignDummyRoutes();
                    routesSent = true;
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

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("MRA terminating.");
    }
}