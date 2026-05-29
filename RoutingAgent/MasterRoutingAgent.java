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

/**
 * MasterRoutingAgent (MRA)
 * * The central coordinator for the Vehicle Routing Problem (VRP) Multi-Agent System.
 * Responsibilities:
 * 1. Initializes the environment via GUI inputs.
 * 2. Spawns Delivery Agents (DAs) and monitors their capacities.
 * 3. Dynamically spawns backup agents if the fleet's capacity is insufficient for the demand.
 * 4. Bridges Java with external Python optimization scripts (Tabu Search / GA) to calculate routes.
 * 5. Dispatches the optimized routes to the individual Delivery Agents.
 */
public class MasterRoutingAgent extends Agent {

    // --- Constants for Directory Facilitator (DF) & Communications ---
    private static final String MASTER_DF_TYPE = "vrp-master-routing";
    private static final String MASTER_DF_NAME = "MRA";
    private static final String CAPACITY_CONVERSATION_ID = "vrp-capacity";
    private static final String CAPACITY_ACK_CONVERSATION_ID = "vrp-capacity-ack";
    private static final String ROUTE_CONVERSATION_ID = "vrp-route";
    private static final String CAPACITY_PREFIX = "CAPACITY:";
    private static final String ROUTE_PREFIX = "ROUTE:";

    // --- State & Tracking Variables ---
    // LinkedHashMap preserves the order in which agents report their capacities
    private final Map<String, Integer> capacities = new LinkedHashMap<>();
    private final List<String> agentNames = new ArrayList<>();

    // --- Configuration Parameters (Set via GUI) ---
    private int expectedAgents = 0;
    private int targetCustomerCount = 20;
    private int totalSystemDemand = 20;
    private String dataFile = "RANDOM";
    private String selectedAlgorithm = "Genetic Algorithm (GA)";

    // --- Execution Flags ---
    private boolean calculationTriggered = false;
    private boolean routesSent = false;

    // Counter to generate unique names for dynamically spawned backup agents
    private int backupAgentCounter = 1;

    private MRAGui myGui;

    /**
     * Helper method to generate a short, readable label for log messages
     * based on the ACLMessage conversation ID or performative.
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
     * * @param direction "SEND" or "RECV" to indicate message flow.
     * @param msg The ACLMessage being logged.
     */
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

    /**
     * Agent setup routine. Called automatically by JADE when the agent starts.
     */
    @Override
    protected void setup() {
        System.out.println("MRA started. Waiting for GUI configuration...");
        registerService();

        // Launch the graphical user interface for user configuration
        myGui = new MRAGui(this);
        myGui.setVisible(true);

        // Continuous listener for incoming capacity reports from DAs
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId(CAPACITY_CONVERSATION_ID);
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    logMessage("RECV", msg);
                    handleCapacityMessage(msg);
                } else {
                    block(); // Yield CPU until a new message arrives
                }
            }
        });
    }

    /**
     * Triggered by the MRAGui when the "Start" button is clicked.
     * Configures the system parameters and spawns the initial fleet.
     */
    public void startSystem(int numCustomers, int numAgents, String filePath, int demand, String algorithm) {
        this.targetCustomerCount = numCustomers;
        this.expectedAgents = numAgents;
        this.dataFile = filePath;
        this.totalSystemDemand = demand;
        this.selectedAlgorithm = algorithm;

        System.out.println("MRA: Configuration set. Spawning Delivery Agents... (Algorithm: " + algorithm + ")");

        // Spawn the requested number of Delivery Agents into the container
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

        System.out.println("\n=======================================================");
        System.out.println(">>> DAs SPAWNED! You may now enter DA capacities. <<<");
        System.out.println("=======================================================\n");
    }

    /**
     * Registers the MRA in the JADE Directory Facilitator (Yellow Pages),
     * allowing other agents to easily find it.
     */
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

    /**
     * Processes capacity reports from DAs. If fleet capacity is too low,
     * it intelligently spawns backup agents. Once capacity is sufficient,
     * it triggers the Python routing engine.
     */
    private void handleCapacityMessage(ACLMessage msg) {
        String senderName = msg.getSender().getLocalName();
        String content = msg.getContent();

        if (content != null && content.startsWith(CAPACITY_PREFIX)) {
            Integer cap = parseCapacity(content);
            if (cap != null && cap > 0) {
                // Acknowledge receipt to the sender
                sendAck(msg);

                // Register the agent if we haven't seen it yet
                if (!capacities.containsKey(senderName)) {
                    capacities.put(senderName, cap);
                    agentNames.add(senderName);
                    System.out.println("MRA received capacity from " + senderName + ": " + cap);

                    // Check if all expected agents have checked in
                    if (capacities.size() >= expectedAgents && !calculationTriggered) {

                        int totalCapacity = capacities.values().stream().mapToInt(Integer::intValue).sum();

                        // --- MRA INTELLIGENCE: CAPACITY ADAPTATION ---
                        if (totalCapacity < totalSystemDemand) {
                            System.out.println("\n*** MRA INTELLIGENCE TRIGGERED ***");
                            System.out.println("WARNING: Insufficient fleet capacity (" + totalCapacity + " units available vs " + totalSystemDemand + " units demanded).");

                            // Calculate deficit and spawn a backup agent explicitly to handle it
                            int capacityDeficit = totalSystemDemand - totalCapacity;
                            System.out.println("ACTION: Spawning Automated Emergency Backup Agent to handle " + capacityDeficit + " units...");

                            expectedAgents++; // Increment target so the system waits for the backup to report in
                            String backupName = "DA_Backup_" + backupAgentCounter++;

                            try {
                                ContainerController container = getContainerController();
                                // Pass the exact deficit as an argument to the new agent
                                Object[] backupArgs = new Object[] { capacityDeficit };
                                AgentController backup = container.createNewAgent(backupName, "RoutingAgent.RoutingAgent.DeliveryAgent", backupArgs);
                                backup.start();
                            } catch (Exception e) {
                                System.err.println("Failed to spawn backup agent.");
                                e.printStackTrace();
                            }
                        } else {
                            // Capacity is sufficient. Proceed to routing.
                            calculationTriggered = true;
                            System.out.println("MRA: Fleet capacity sufficient (" + totalCapacity + "/" + totalSystemDemand + "). Starting solver in 1s...");

                            // 1-second delay ensures all GUI/console events settle before freezing thread for Python
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

    /** Helper to extract integer value from capacity message string. */
    private Integer parseCapacity(String content) {
        if (content == null || !content.startsWith(CAPACITY_PREFIX)) return null;
        try {
            return Integer.parseInt(content.substring(CAPACITY_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sends an ACK message back to the DA confirming capacity was recorded. */
    private void sendAck(ACLMessage receivedMsg) {
        ACLMessage reply = receivedMsg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(CAPACITY_ACK_CONVERSATION_ID);
        reply.setContent("ACK:capacity received");
        send(reply);
        logMessage("SEND", reply);
    }

    /**
     * Bridges Java and Python. Spawns a subprocess to execute the chosen
     * optimization algorithm, capturing its console output to retrieve routes.
     */
    private void calculateOptimalRoutes() {
        try {
            System.out.println("MRA: Invoking " + selectedAlgorithm + " solver...");

            // Dynamically select the target script based on the GUI string
            String scriptName = selectedAlgorithm.equals("Tabu Search") ? "Tabu.py" : "GA.py";

            // Format arguments for CLI: "DA1,DA2" and "5,5"
            String namesArg = String.join(",", agentNames);
            String capsArg = capacities.values().toString().replaceAll("[\\[\\] ]", "");

            // Build process execution command
            ProcessBuilder pb = new ProcessBuilder("python", scriptName, namesArg, capsArg, String.valueOf(targetCustomerCount), dataFile);
            pb.redirectErrorStream(true); // Merge standard error with standard out
            Process p = pb.start();

            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = "";
            String line;

            // Read the live stdout from the Python script
            while ((line = in.readLine()) != null) {
                // Look for the specific payload keyword defined in the Python script
                if (line.startsWith("FINAL_ROUTES:")) {
                    result = line.substring(13);
                    break;
                }
                // Print Python debug/progress logs directly to the Java console
                System.out.println("Python: " + line);
            }

            if (result != null && !result.isEmpty()) {
                parseAndSendRoutes(result);
            } else {
                System.out.println("MRA: " + selectedAlgorithm + " returned invalid result.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the payload returned from Python and dispatches individual routes
     * to the corresponding Delivery Agents via JADE ACLMessages.
     * * @param result String format expected: "DA1:1,2,3|DA2:4,5|DA3:0"
     */
    private void parseAndSendRoutes(String result) {
        // Split by the pipe character to isolate each agent's assignment
        String[] individualRoutes = result.split("\\|");

        for (String routeEntry : individualRoutes) {
            String[] parts = routeEntry.split(":");
            if (parts.length == 2) {
                String targetAgent = parts[0];
                String routePoints = parts[1];

                // Construct and dispatch the specific route to the specific agent
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

    /**
     * Agent termination routine. Deregisters from the DF and cleans up the GUI.
     */
    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // Ignore exception during shutdown
        }

        if (myGui != null) {
            myGui.dispose();
        }
        System.out.println("MRA terminating.");
    }
}