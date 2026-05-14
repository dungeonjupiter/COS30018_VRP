package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import RoutingAgent.Extension.Solver.RouteState;
import RoutingAgent.Extension.Solver.TabuRoutingEngine;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.awt.Point;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MasterRoutingAgent extends Agent {

    // JADE Communication IDs
    public static final String CID_ROUTE = "vrp-route";
    public static final String CID_TRACKING = "vrp-tracking";
    public static final String CID_DONE = "vrp-done";
    public static final String CID_CAPACITY = "vrp-capacity";
    private static final String PREFIX_ROUTE = "ROUTE:";

    // Architecture Components
    private MainWindow myGui;
    private final TabuRoutingEngine tabuEngine = new TabuRoutingEngine();
    private final List<AID> fleet = new ArrayList<>();
    private final Point depot = new Point(50, 50);

    // Fleet & Map Metadata
    private final Map<String, Integer> fleetCapacities = new HashMap<>();
    public final Map<Point, Parcel> parcelDirectory = new HashMap<>();

    // Live Tracking Data
    private final Map<String, Point> currentLocs = new HashMap<>();
    private final Map<String, List<Point>> actualDrivenRoutes = new HashMap<>();
    private final Map<String, List<Point>> remainingPaths = new HashMap<>();
    private final Set<String> activeDrivingAgents = new HashSet<>();

    // Summary & Visuals Data
    private final Map<String, List<Point>> initialPlannedRoutes = new HashMap<>();

    // State Flags
    private boolean isPhase2Active = false;

    // Async Initialization Variables
    private List<Parcel> initParcels = new ArrayList<>();
    private int initTotalDemand = 0;
    private int initPendingAgents = 0;
    private boolean isPhase1RandomInitializing = false;

    @Override
    protected void setup() {
        myGui = new MainWindow(this);
        myGui.setVisible(true);
        myGui.log("System Status: Booted. Awaiting Phase 1 Initialization...");

        // GPS Listener
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_TRACKING);
                ACLMessage msg = receive(tpl);
                if (msg != null) processGpsPing(msg.getSender().getLocalName(), msg.getContent());
                else block();
            }
        });

        // Agent Done Listener
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_DONE);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    activeDrivingAgents.remove(msg.getSender().getLocalName());
                    if (activeDrivingAgents.isEmpty()) myGui.enableSummary();
                } else block();
            }
        });

        // Async Capacity Configuration Listener
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_CAPACITY);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    String name = msg.getSender().getLocalName();
                    int cap = Integer.parseInt(msg.getContent().split(":")[1]);
                    fleetCapacities.put(name, cap);
                    myGui.log(name + " registered with capacity: " + cap);

                    // Check if we are waiting for user inputs during random initialization
                    if (isPhase1RandomInitializing) {
                        initPendingAgents--;
                        if (initPendingAgents <= 0) {
                            isPhase1RandomInitializing = false;
                            finalizePhase1Initialization(); // Continue once all GUIs are done
                        }
                    }
                } else { block(); }
            }
        });
    }

    // ==========================================
    // PHASE 1: ASYNCHRONOUS INITIALIZATION
    // ==========================================
    public void initializeSystem(int numCustomers, int numAgents, String mapFilePath) {
        myGui.log("--- Initializing Phase 1 ---");
        initParcels.clear();
        initTotalDemand = 0;

        if (!mapFilePath.equals("RANDOM")) {
            // MAP LOADER LOGIC
            try {
                MapLoader.ParsedData mapData = MapLoader.load(mapFilePath);

                for (Parcel p : mapData.parcels) {
                    initParcels.add(p);
                    parcelDirectory.put(p.getDestination(), p);
                    initTotalDemand += p.getDemand();
                }

                for (Map.Entry<String, Integer> entry : mapData.agents.entrySet()) {
                    String name = entry.getKey();
                    int cap = entry.getValue();
                    spawnDynamicAgent(name, mapData.warehouse.x, mapData.warehouse.y, cap, false);
                    fleetCapacities.put(name, cap);
                    activeDrivingAgents.add(name);
                }

                myGui.log("Map Loaded: " + mapData.parcels.size() + " customers, " + mapData.agents.size() + " agents.");
                finalizePhase1Initialization(); // Proceed to Math Optimization instantly

            } catch (Exception e) {
                myGui.log("Failed to parse map file: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            // RANDOM GENERATOR LOGIC
            isPhase1RandomInitializing = true;
            Random rand = new Random();
            for (int i = 1; i <= numCustomers; i++) {
                Parcel p = new Parcel("P" + i, rand.nextInt(100), rand.nextInt(100), 1);
                initParcels.add(p);
                parcelDirectory.put(p.getDestination(), p);
                initTotalDemand += p.getDemand();
            }
            myGui.log("Generated " + numCustomers + " random customers. Total Demand: " + initTotalDemand);

            // Spawn agents and wait for user to configure capacity via GUI
            initPendingAgents = numAgents;
            myGui.log("Awaiting capacity configuration for " + numAgents + " agents via GUI...");
            for (int i = 1; i <= numAgents; i++) {
                String name = "DA" + i;
                spawnDynamicAgent(name, 50, 50, 5, true); // True triggers the DeliveryAgentGui
                activeDrivingAgents.add(name);
            }
        }
    }

    private void finalizePhase1Initialization() {
        int currentCapacity = fleetCapacities.values().stream().mapToInt(Integer::intValue).sum();

        // Backup Agent Logic
        int backupCount = fleetCapacities.size();
        while (currentCapacity < initTotalDemand) {
            backupCount++;
            String backupName = "DA" + backupCount;
            myGui.log("Alert: Insufficient capacity. Deploying Backup Agent " + backupName + " (Cap: 5)");
            spawnDynamicAgent(backupName, 50, 50, 5, false); // No GUI for backups
            fleetCapacities.put(backupName, 5);
            activeDrivingAgents.add(backupName);
            currentCapacity += 5;
        }

        // Run Initial Optimization
        RouteState baseState = new RouteState();
        for (AID aid : fleet) baseState.addAgent(aid.getLocalName(), depot);
        for (Parcel p : initParcels) {
            baseState = tabuEngine.optimize(baseState, p, fleetCapacities, parcelDirectory, new HashMap<>());
        }

        // Save initial routes for Bold Line drawing in GUI
        for (Map.Entry<String, List<Point>> entry : baseState.getRoutes().entrySet()) {
            initialPlannedRoutes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        dispatchRoutes(baseState, null);
        isPhase2Active = true;
        myGui.setPhase2Enabled(true);
        myGui.log("Phase 1 Complete. Fleet dispatched.");
    }

    // ==========================================
    // PHASE 2: DYNAMIC REROUTING
    // ==========================================
    public void injectDynamicParcel(Parcel newParcel) {
        if (!isPhase2Active) return;
        parcelDirectory.put(newParcel.getDestination(), newParcel);
        myGui.log("Dynamic Request: " + newParcel.getId() + " at " + newParcel.getDestination());

        RouteState snapshot = new RouteState();
        Map<String, Integer> lockedPrefixes = new HashMap<>();

        // N+1 Buffer Lock Logic
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            Point loc = currentLocs.getOrDefault(name, depot);
            snapshot.addAgent(name, loc);
            List<Point> remaining = remainingPaths.getOrDefault(name, new ArrayList<>());
            int lockCount = 0;

            if (!remaining.isEmpty()) {
                snapshot.insertNode(name, 1, remaining.get(0));
                lockCount = 1;

                // If truck is very close to node 0, lock node 1 as well
                if (loc.distance(remaining.get(0)) < 15.0 && remaining.size() > 1) {
                    snapshot.insertNode(name, 2, remaining.get(1));
                    lockCount = 2;
                }

                for (int i = lockCount; i < remaining.size(); i++) {
                    snapshot.insertNode(name, i + 1, remaining.get(i));
                }
            }
            lockedPrefixes.put(name, lockCount);
        }

        // Asynchronous Ghost Math
        CompletableFuture.supplyAsync(() -> tabuEngine.optimize(snapshot, newParcel, fleetCapacities, parcelDirectory, lockedPrefixes))
                .thenAccept(optimizedState -> addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
                    public void action() {
                        dispatchRoutes(optimizedState, newParcel);
                        myGui.log("Optimization Complete: Route updated for " + newParcel.getId());
                    }
                }));
    }

    private void dispatchRoutes(RouteState state, Parcel dynamicParcel) {
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            List<Point> mathNodes = state.getRoutes().get(name);
            if (mathNodes == null || mathNodes.size() < 2) continue;

            List<Point> physicalStops = new ArrayList<>();
            boolean pickedUpAtDepot = false;

            for (int i = 1; i < mathNodes.size(); i++) {
                Point p = mathNodes.get(i);
                if (p.equals(depot)) { pickedUpAtDepot = true; physicalStops.add(p); continue; }

                // Batch Pickup Logic: Go to warehouse if this parcel hasn't been picked up
                if (dynamicParcel != null && p.equals(dynamicParcel.getDestination()) && !pickedUpAtDepot) {
                    physicalStops.add(new Point(depot));
                    pickedUpAtDepot = true;
                }
                physicalStops.add(p);
            }

            if (!physicalStops.isEmpty() && !physicalStops.get(physicalStops.size()-1).equals(depot)) {
                physicalStops.add(new Point(depot));
            }

            StringBuilder sb = new StringBuilder(PREFIX_ROUTE + "5:5|");
            for (int i = 0; i < physicalStops.size(); i++) {
                Point p = physicalStops.get(i);
                sb.append(p.x).append(":").append(p.y);
                if (i < physicalStops.size() - 1) sb.append(",");
            }

            ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
            m.addReceiver(aid);
            m.setConversationId(CID_ROUTE);
            m.setContent(sb.toString());
            send(m);
        }
    }

    private void processGpsPing(String agentName, String content) {
        try {
            String[] parts = content.split("\\|");
            String[] coords = parts[0].split(",");
            Point loc = new Point(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));

            currentLocs.put(agentName, loc);
            List<Point> history = actualDrivenRoutes.computeIfAbsent(agentName, k -> new ArrayList<>());
            if (history.isEmpty() || !history.get(history.size() - 1).equals(loc)) history.add(loc);

            List<Point> rem = new ArrayList<>();
            if (parts.length > 1 && !parts[1].isBlank()) {
                for (String s : parts[1].split(";")) {
                    String[] sc = s.split(",");
                    rem.add(new Point(Integer.parseInt(sc[0]), Integer.parseInt(sc[1])));
                }
            }
            remainingPaths.put(agentName, rem);
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities);
        } catch (Exception e) {}
    }

    // Default spawn (No GUI)
    public void spawnDynamicAgent(String name, int startX, int startY, int capacity) {
        spawnDynamicAgent(name, startX, startY, capacity, false);
    }

    // Overloaded spawn (Optional GUI trigger)
    public void spawnDynamicAgent(String name, int startX, int startY, int capacity, boolean showGui) {
        try {
            Object[] args = showGui
                    ? new Object[]{startX + "," + startY + "," + capacity, "SHOW_GUI"}
                    : new Object[]{startX + "," + startY + "," + capacity};

            jade.wrapper.AgentController ac = getContainerController().createNewAgent(name, "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", args);
            ac.start();
            fleet.add(new AID(name, AID.ISLOCALNAME));
        } catch (Exception e) {
            e.printStackTrace();
            myGui.log("Error spawning agent: " + name);
        }
    }

    public Map<String, List<Point>> getInitialPlannedRoutes() { return initialPlannedRoutes; }
    public Map<String, List<Point>> getActualDrivenRoutes() { return actualDrivenRoutes; }
}