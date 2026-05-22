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

    public static final String CID_ROUTE = "vrp-route";
    public static final String CID_TRACKING = "vrp-tracking";
    public static final String CID_DONE = "vrp-done";
    public static final String CID_CAPACITY = "vrp-capacity";
    private static final String PREFIX_ROUTE = "ROUTE:";

    private MainWindow myGui;
    private final TabuRoutingEngine tabuEngine = new TabuRoutingEngine();
    private final List<AID> fleet = new ArrayList<>();
    private final Point depot = new Point(50, 50);

    private final Map<String, Integer> fleetCapacities = new HashMap<>();
    public final Map<Point, Parcel> parcelDirectory = new HashMap<>();

    private final Map<String, Point> currentLocs = new HashMap<>();
    private final Map<String, List<Point>> actualDrivenRoutes = new HashMap<>();
    private final Map<String, List<Point>> remainingPaths = new HashMap<>();
    private final Set<String> activeDrivingAgents = new HashSet<>();
    private final Map<String, List<Point>> initialPlannedRoutes = new HashMap<>();
    private final Set<Point> dynamicDestinations = new HashSet<>();

    private List<Point> previewNodes = new ArrayList<>();
    private RouteState plannedBaseState = null;
    private boolean isPhase2Active = false;

    private List<Parcel> initParcels = new ArrayList<>();
    private int initTotalDemand = 0;
    private int initPendingAgents = 0;
    private int backupCounter = 0;

    @Override
    protected void setup() {
        myGui = new MainWindow(this);
        myGui.setVisible(true);
        myGui.log("System Booted. Select map source and Prepare Environment.");

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_TRACKING);
                ACLMessage msg = receive(tpl);
                if (msg != null) processGpsPing(msg.getSender().getLocalName(), msg.getContent());
                else block();
            }
        });

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_DONE);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    activeDrivingAgents.remove(msg.getSender().getLocalName());
                    if (activeDrivingAgents.isEmpty()) myGui.enableSummary();
                } else block();
            }
        });

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_CAPACITY);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    String name = msg.getSender().getLocalName();
                    try {
                        int cap = Integer.parseInt(msg.getContent().split(":")[1]);
                        fleetCapacities.put(name, cap);
                        myGui.log(name + " capacity registered: " + cap);

                        if (initPendingAgents > 0) {
                            initPendingAgents--;
                            if (initPendingAgents <= 0) checkCapacityLoop();
                        }
                    } catch (Exception e) {}
                } else block();
            }
        });
    }

    public void previewMap(String path) {
        try {
            MapLoader.ParsedData mapData = MapLoader.load(path);
            previewNodes.clear();
            for (Parcel p : mapData.parcels) previewNodes.add(p.getDestination());
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
            myGui.log("Map preview loaded successfully.");
        } catch (Exception e) {}
    }

    public void clearPreview() {
        previewNodes.clear();
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
        myGui.log("Map cleared. Reverted to random generation.");
    }

    public void prepareEnvironment(int numCustomers, int numAgents, String mapFilePath) {
        myGui.log("--- Preparing Agents & Environment ---");
        initParcels.clear();
        initTotalDemand = 0;
        previewNodes.clear();
        dynamicDestinations.clear();

        if (!mapFilePath.equals("RANDOM")) {
            try {
                MapLoader.ParsedData mapData = MapLoader.load(mapFilePath);
                backupCounter = numAgents;
                for (Parcel p : mapData.parcels) {
                    initParcels.add(p);
                    parcelDirectory.put(p.getDestination(), p);
                    initTotalDemand += p.getDemand();
                    previewNodes.add(p.getDestination());
                }

                initPendingAgents = numAgents;
                for (int i = 1; i <= numAgents; i++) {
                    String name = "DA" + i;
                    spawnDynamicAgent(name, mapData.warehouse.x, mapData.warehouse.y, 5, true);
                    activeDrivingAgents.add(name);
                    currentLocs.put(name, mapData.warehouse);
                }
                myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
            } catch (Exception e) {}
        } else {
            int cols = (int) Math.ceil(Math.sqrt(numCustomers));
            int rows = (int) Math.ceil((double) numCustomers / cols);
            int cellW = 100 / cols;
            int cellH = 100 / rows;

            Random rand = new Random();
            backupCounter = numAgents;
            int count = 1;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (count > numCustomers) break;
                    int x = (c * cellW) + rand.nextInt(Math.max(1, cellW - 5));
                    int y = (r * cellH) + rand.nextInt(Math.max(1, cellH - 5));

                    Parcel p = new Parcel("P" + count, x, y, 1);
                    initParcels.add(p);
                    parcelDirectory.put(p.getDestination(), p);
                    initTotalDemand += p.getDemand();
                    previewNodes.add(p.getDestination());
                    count++;
                }
            }

            initPendingAgents = numAgents;
            for (int i = 1; i <= numAgents; i++) {
                String name = "DA" + i;
                spawnDynamicAgent(name, 50, 50, 5, true);
                activeDrivingAgents.add(name);
                currentLocs.put(name, depot);
            }
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
        }
    }

    private void checkCapacityLoop() {
        int currentCapacity = fleetCapacities.values().stream().mapToInt(Integer::intValue).sum();
        if (currentCapacity < initTotalDemand) {
            backupCounter++;
            String backupName = "DA" + backupCounter;
            myGui.log("Alert: Insufficient capacity. Spawning Backup: " + backupName);
            spawnDynamicAgent(backupName, 50, 50, 5, true);
            activeDrivingAgents.add(backupName);
            currentLocs.put(backupName, depot);
            initPendingAgents = 1;
        } else {
            myGui.log("Capacity check passed. Click '2. Plot Routes'.");
            myGui.enablePlotting();
        }
    }

    public void plotRoutes() {
        myGui.log("--- Plotting Routes via Tabu Solver ---");
        RouteState baseState = new RouteState();
        for (AID aid : fleet) baseState.addAgent(aid.getLocalName(), depot);
        for (Parcel p : initParcels) {
            // Pass the logger callback to see the math engine working!
            baseState = tabuEngine.optimize(baseState, p, fleetCapacities, parcelDirectory, new HashMap<>(), new HashSet<>(), msg -> myGui.log(msg));
        }
        previewNodes.clear();
        for (Map.Entry<String, List<Point>> entry : baseState.getRoutes().entrySet()) {
            initialPlannedRoutes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            remainingPaths.put(entry.getKey(), entry.getValue());
        }
        plannedBaseState = baseState;
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
        myGui.log("Routes plotted successfully. Click '3. Dispatch Fleet'.");
        myGui.enableDispatch();
    }

    // ==========================================
    // DISPATCH & INJECTION UPDATES
    // ==========================================
    public void dispatchFleet() {
        myGui.log("--- Dispatching Fleet ---");
        // Pass empty maps because initial dispatch has no dynamic history or trunk contents
        dispatchRoutes(plannedBaseState, null, new HashMap<>());
        isPhase2Active = true;
        myGui.setPhase2Enabled(true);
        myGui.log("Fleet has been dispatched and is now moving.");
    }

    public void injectDynamicParcel(Parcel newParcel) {
        if (!isPhase2Active) return;
        parcelDirectory.put(newParcel.getDestination(), newParcel);
        dynamicDestinations.add(newParcel.getDestination());
        myGui.log("Dynamic Request: " + newParcel.getId());

        RouteState snapshot = new RouteState();
        Map<String, Integer> lockedPrefixes = new HashMap<>();
        Map<String, Set<Point>> trunkContents = new HashMap<>();

        for (AID aid : fleet) {
            String name = aid.getLocalName();
            Point loc = currentLocs.getOrDefault(name, depot);
            snapshot.addAgent(name, loc);
            List<Point> remaining = remainingPaths.getOrDefault(name, new ArrayList<>());
            int lockCount = 0;

            if (!remaining.isEmpty()) {
                int nextDepotIndex = remaining.indexOf(depot);
                if (nextDepotIndex == -1) lockCount = remaining.size();
                else lockCount = nextDepotIndex;

                lockCount = Math.max(lockCount, 1);
                if (loc.distance(remaining.get(0)) < 15.0 && remaining.size() > 1) {
                    lockCount = Math.max(lockCount, 2);
                }

                Set<Point> inTrunk = new HashSet<>();
                for (int i = 0; i < lockCount; i++) inTrunk.add(remaining.get(i));
                trunkContents.put(name, inTrunk);

                for (int i = 0; i < remaining.size(); i++) snapshot.insertNode(name, i + 1, remaining.get(i));
            } else {
                trunkContents.put(name, new HashSet<>());
            }
            lockedPrefixes.put(name, lockCount);
        }

        Set<Point> dynamicSnapshot = new HashSet<>(dynamicDestinations);

        // Pass the logger callback here as well
        CompletableFuture.supplyAsync(() -> tabuEngine.optimize(snapshot, newParcel, fleetCapacities, parcelDirectory, lockedPrefixes, dynamicSnapshot, msg -> myGui.log(msg)))
                .thenAccept(optimizedState -> addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
                    public void action() { dispatchRoutes(optimizedState, newParcel, trunkContents); }
                }));
    }

    private void dispatchRoutes(RouteState state, Parcel dynamicParcel, Map<String, Set<Point>> trunkContents) {
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            List<Point> mathNodes = state.getRoutes().get(name);
            if (mathNodes == null || mathNodes.size() < 2) continue;

            Set<Point> inTrunk = trunkContents.getOrDefault(name, new HashSet<>());
            List<Point> physicalStops = new ArrayList<>();
            boolean hasInventoryForDynamic = false;

            for (int i = 1; i < mathNodes.size(); i++) {
                Point p = mathNodes.get(i);

                if (p.equals(depot)) {
                    hasInventoryForDynamic = true;
                    if (physicalStops.isEmpty() || !physicalStops.get(physicalStops.size()-1).equals(depot)) physicalStops.add(p);
                    continue;
                }

                // THE FIX: Instead of relying on index math, we ask memory directly.
                // If it's already in the trunk, skip the warehouse detour entirely!
                if (inTrunk.contains(p)) {
                    physicalStops.add(p);
                    continue;
                }

                // If outside the trunk, apply normal dynamic depot rules
                if (dynamicDestinations.contains(p) && !hasInventoryForDynamic) {
                    if (physicalStops.isEmpty() || !physicalStops.get(physicalStops.size()-1).equals(depot)) physicalStops.add(new Point(depot));
                    hasInventoryForDynamic = true;
                }
                physicalStops.add(p);
            }

            if (!physicalStops.isEmpty() && !physicalStops.get(physicalStops.size()-1).equals(depot)) physicalStops.add(new Point(depot));

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

    // ==========================================
    // STANDBY AGENT DEPLOYMENT
    // ==========================================
    public void deployStandby(String name, int capacity) {
        myGui.log("Deploying Standby Agent: " + name + " (Capacity: " + capacity + ")");
        spawnDynamicAgent(name, depot.x, depot.y, capacity, false);

        // Immediately register all metadata so UI updates seamlessly
        fleetCapacities.put(name, capacity);
        activeDrivingAgents.add(name);
        currentLocs.put(name, depot);
        remainingPaths.put(name, new ArrayList<>());

        // Ensure it appears on the End of Day Summary even if it doesn't move
        initialPlannedRoutes.put(name, new ArrayList<>(List.of(depot)));
        actualDrivenRoutes.put(name, new ArrayList<>(List.of(depot)));

        // Force a map repaint to show the new agent in the Tracker Legend
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
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
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
        } catch (Exception e) {}
    }

    public void spawnDynamicAgent(String name, int startX, int startY, int capacity, boolean showGui) {
        try {
            Object[] args = showGui ? new Object[]{startX + "," + startY + "," + capacity, "SHOW_GUI"} : new Object[]{startX + "," + startY + "," + capacity};
            jade.wrapper.AgentController ac = getContainerController().createNewAgent(name, "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", args);
            ac.start();
            fleet.add(new AID(name, AID.ISLOCALNAME));
        } catch (Exception e) {}
    }

    public Map<String, List<Point>> getInitialPlannedRoutes() { return initialPlannedRoutes; }
    public Map<String, List<Point>> getActualDrivenRoutes() { return actualDrivenRoutes; }
}