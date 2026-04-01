package RoutingAgent.RoutingAgent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
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

    private final Map<String, Integer> capacities = new LinkedHashMap<>();
    private final List<String> agentNames = new ArrayList<>();

    private int expectedAgents = 0;
    private int targetCustomerCount = 20;
    private int totalSystemDemand = 20;
    private String dataFile = "RANDOM";

    private boolean calculationTriggered = false;
    private boolean routesSent = false;

    // Counter to give unique names to backup agents if multiple are needed
    private int backupAgentCounter = 1;

    private MRAGui myGui;

    private String shortLabel(ACLMessage msg) {
        String convId = msg.getConversationId();

        if (CAPACITY_CONVERSATION_ID.equals(convId)) return "CAPACITY";
        if (CAPACITY_ACK_CONVERSATION_ID.equals(convId)) return "ACK";
        if (ROUTE_CONVERSATION_ID.equals(convId)) return "ROUTE";
        return ACLMessage.getPerformative(msg.getPerformative());
    }

    private void logMessage(String direction, ACLMessage msg) {
        if (msg == null) return;

        String sender = msg.getSender() != null ? msg.getSender().getLocalName() : "null";
        String receiver = msg.getAllReceiver().hasNext()
                ? ((jade.core.AID) msg.getAllReceiver().next()).getLocalName()
                : "none";

        String arrow = "SEND".equals(direction)
                ? sender + " -> " + receiver
                : sender + " -> " + getLocalName();

        System.out.println("[MSG][" + shortLabel(msg) + "] "
                + arrow
                + " | " + (msg.getContent() != null ? msg.getContent() : "<no-content>"));
    }

    protected void setup() {
        System.out.println("MRA started. Waiting for GUI configuration...");
        registerService();

        myGui = new MRAGui(this);
        myGui.setVisible(true);

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId(CAPACITY_CONVERSATION_ID);
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    logMessage("RECV", msg);
                    handleCapacityMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    // Called by the MRAGui when "Start" is clicked
    public void startSystem(int numCustomers, int numAgents, String filePath, int demand) {
        this.targetCustomerCount = numCustomers;
        this.expectedAgents = numAgents;
        this.dataFile = filePath;
        this.totalSystemDemand = demand;

        System.out.println("MRA: Configuration set. " + numCustomers + " customers, " + numAgents + " agents. Required Fleet Capacity: " + demand);

        ContainerController container = getContainerController();
        for (int i = 1; i <= numAgents; i++) {
            String agentName = "DA" + i;
            try {
                AgentController ac = container.createNewAgent(agentName, "RoutingAgent.RoutingAgent.DeliveryAgent", null);
                ac.start();
            } catch (Exception e) {
                System.err.println("Failed to spawn " + agentName);
                e.printStackTrace();
            }
        }
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
        } catch (FIPAException e) { e.printStackTrace(); }
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

                    if (capacities.size() >= expectedAgents && !calculationTriggered) {

                        int totalCapacity = capacities.values().stream().mapToInt(Integer::intValue).sum();

                        // SMART CHECK: Compare against true demand, not just node count
                        if (totalCapacity < totalSystemDemand) {
                            System.out.println("\n*** MRA INTELLIGENCE TRIGGERED ***");
                            System.out.println("WARNING: Insufficient fleet capacity (" + totalCapacity + " units available vs " + totalSystemDemand + " units demanded).");
                            System.out.println("ACTION: Spawning Emergency Backup Agent...");

                            expectedAgents++; // Tell the MRA to wait for one more agent
                            String backupName = "DA_Backup_" + backupAgentCounter++;

                            try {
                                ContainerController container = getContainerController();
                                AgentController backup = container.createNewAgent(backupName, "RoutingAgent.RoutingAgent.DeliveryAgent", null);
                                backup.start();
                                // The MRA will now naturally wait for this new agent to report its capacity
                            } catch (Exception e) {
                                System.err.println("Failed to spawn backup agent.");
                                e.printStackTrace();
                            }
                        } else {
                            // Capacity is mathematically sufficient, proceed to Algorithm
                            calculationTriggered = true;
                            System.out.println("MRA: Fleet capacity sufficient (" + totalCapacity + "/" + totalSystemDemand + "). Starting GA in 1s...");
                            addBehaviour(new WakerBehaviour(this, 1000) {
                                protected void onWake() {
                                    if (!routesSent) {
                                        routesSent = true;
                                        calculateOptimalRoutes();
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private Integer parseCapacity(String content) {
        if (content == null || !content.startsWith(CAPACITY_PREFIX)) return null;
        try {
            return Integer.parseInt(content.substring(CAPACITY_PREFIX.length()).trim());
        } catch (NumberFormatException e) { return null; }
    }

    private void sendAck(ACLMessage receivedMsg) {
        ACLMessage reply = receivedMsg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(CAPACITY_ACK_CONVERSATION_ID);
        reply.setContent("ACK:capacity received");
        send(reply);
        logMessage("SEND", reply);
    }

    private void calculateOptimalRoutes() {
        try {
            System.out.println("MRA: Invoking Genetic Algorithm solver...");

            String namesArg = String.join(",", agentNames);
            String capsArg = capacities.values().toString().replaceAll("[\\[\\] ]", "");

            // Pass the targetCustomerCount and dataFile path as the 3rd and 4th arguments to Python
            ProcessBuilder pb = new ProcessBuilder("python", "GA.py", namesArg, capsArg, String.valueOf(targetCustomerCount), dataFile);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = "";
            String line;

            while ((line = in.readLine()) != null) {
                // Java looks for the secret prefix
                if (line.startsWith("FINAL_ROUTES:")) {
                    result = line.substring(13); // Cuts off the prefix, leaving just the route data
                    break; // Stops reading and moves on to sending the messages
                }

                // If it's not the route, print it normally (this handles your Generation logs)
                System.out.println("Python: " + line);
            }

            if (result != null && !result.isEmpty()) {
                parseAndSendRoutes(result); // This is what triggers your DA movement logs!
            } else {
                System.out.println("MRA: GA returned invalid result.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseAndSendRoutes(String result) {
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
                logMessage("SEND", routeMsg);
                System.out.println("MRA: Sent optimized route to " + targetAgent + ": " + routePoints);
            }
        }
    }

    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) {}
        if (myGui != null) myGui.dispose();
        System.out.println("MRA terminating.");
    }
}