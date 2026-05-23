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

    private final List<Point> warehouses = new ArrayList<>();
    private final Map<String, Point> agentHomeDepots = new HashMap<>();
    private final Map<String, Integer> fleetCapacities = new HashMap<>();
    public final Map<Point, Parcel> parcelDirectory = new HashMap<>();

    private final Map<String, Point> currentLocs = new HashMap<>();
    private final Map<String, List<Point>> actualDrivenRoutes = new HashMap<>();
    private final Map<String, List<Point>> remainingPaths = new HashMap<>();
    private final Set<String> activeDrivingAgents = new HashSet<>();
    private final Map<String, List<Point>> initialPlannedRoutes = new HashMap<>();
    private final Set<Point> dynamicDestinations = new HashSet<>();

    private final List<Parcel> scenarioParcels = new ArrayList<>();
    private boolean multiWarehouseMode = false;

    private List<Point> previewNodes = new ArrayList<>();
    private RouteState plannedBaseState = null;
    private boolean isPhase2Active = false;

    private List<Parcel> initParcels = new ArrayList<>();
    private int initTotalDemand = 0;
    private int initPendingAgents = 0;
    private int backupCounter = 0;
    private int scenarioParcelCounter = 0;

    @Override
    protected void setup() {
        warehouses.add(new Point(50, 50));
        myGui = new MainWindow(this);
        myGui.setVisible(true);
        myGui.log("System Booted. Choose warehouse mode, build scenario with parcels, then Prepare Environment.");

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

    // --- Scenario builder API ---

    public void setMultiWarehouseMode(boolean multi) {
        multiWarehouseMode = multi;
    }

    public void setSingleWarehouse(int x, int y) {
        warehouses.clear();
        warehouses.add(new Point(x, y));
        refreshScenarioPreview();
    }

    public boolean addScenarioWarehouse(int x, int y) {
        Point wh = new Point(x, y);
        for (Point existing : warehouses) {
            if (existing.x == wh.x && existing.y == wh.y) {
                return false;
            }
        }
        warehouses.add(wh);
        refreshScenarioPreview();
        if (myGui != null) {
            myGui.log("Warehouse added at (" + x + ", " + y + "). Total: " + warehouses.size());
        }
        return true;
    }

    public void resetMultiWarehouses() {
        warehouses.clear();
        warehouses.add(new Point(20, 20));
        warehouses.add(new Point(80, 80));
        refreshScenarioPreview();
    }

    public void addScenarioParcel(int x, int y, Point originWarehouse) {
        scenarioParcelCounter++;
        Parcel p = new Parcel("S" + scenarioParcelCounter, x, y, 1, originWarehouse);
        scenarioParcels.add(p);
        previewNodes.add(p.getDestination());
        parcelDirectory.put(p.getDestination(), p);
        refreshScenarioPreview();
        if (myGui != null) {
            myGui.log("Scenario parcel " + p.getId() + " -> (" + x + "," + y + ") from WH (" + originWarehouse.x + "," + originWarehouse.y + ")");
        }
    }

    public void clearScenario() {
        scenarioParcels.clear();
        scenarioParcelCounter = 0;
        parcelDirectory.clear();
        previewNodes.clear();
        if (!multiWarehouseMode) {
            warehouses.clear();
            warehouses.add(new Point(50, 50));
        }
        refreshScenarioPreview();
    }

    public int getScenarioParcelCount() { return scenarioParcels.size(); }

    public List<Point> getWarehouses() { return Collections.unmodifiableList(warehouses); }

    public Point nearestWarehouse(int x, int y) {
        if (warehouses.isEmpty()) return new Point(50, 50);
        Point target = new Point(x, y);
        Point best = warehouses.get(0);
        double bestDist = target.distance(best);
        for (Point wh : warehouses) {
            double d = target.distance(wh);
            if (d < bestDist) {
                bestDist = d;
                best = wh;
            }
        }
        return best;
    }

    public void refreshScenarioPreview() {
        previewNodes.clear();
        for (Parcel p : scenarioParcels) previewNodes.add(p.getDestination());
        if (myGui != null) {
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
        }
    }

    public void previewMap(String path) {
        try {
            MapLoader.ParsedData mapData = MapLoader.load(path);
            applyMapData(mapData);
            multiWarehouseMode = mapData.warehouses.size() > 1;
            myGui.log("Map preview: " + mapData.warehouses.size() + " warehouse(s), " + mapData.parcels.size() + " parcel(s).");
        } catch (Exception e) {
            myGui.log("Failed to load map preview: " + e.getMessage());
        }
    }

    private void applyMapData(MapLoader.ParsedData mapData) {
        warehouses.clear();
        warehouses.addAll(mapData.warehouses);
        scenarioParcels.clear();
        scenarioParcels.addAll(mapData.parcels);
        previewNodes.clear();
        parcelDirectory.clear();
        for (Parcel p : mapData.parcels) {
            parcelDirectory.put(p.getDestination(), p);
            previewNodes.add(p.getDestination());
        }
        myGui.refreshScenarioUi();
    }

    public void clearPreview() {
        previewNodes.clear();
        scenarioParcels.clear();
        parcelDirectory.clear();
        warehouses.clear();
        warehouses.add(new Point(50, 50));
        myGui.refreshScenarioUi();
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
        myGui.log("Map cleared. Reverted to scenario builder.");
    }

    public void prepareEnvironment(int numCustomers, int numAgents, String mapFilePath) {
        if (warehouses.isEmpty()) {
            warehouses.add(new Point(50, 50));
            myGui.log("Warning: No warehouses defined. Using default (50,50).");
        }
        myGui.log("--- Preparing Agents & Environment ---");
        initParcels.clear();
        initTotalDemand = 0;
        dynamicDestinations.clear();
        agentHomeDepots.clear();

        if ("SCENARIO".equals(mapFilePath) && !scenarioParcels.isEmpty()) {
            loadFromScenario(numAgents);
        } else if (!mapFilePath.equals("RANDOM") && !mapFilePath.equals("SCENARIO")) {
            try {
                MapLoader.ParsedData mapData = MapLoader.load(mapFilePath);
                applyMapData(mapData);
                loadFromScenario(numAgents);
            } catch (Exception e) {
                myGui.log("Map load failed: " + e.getMessage());
            }
        } else if (!scenarioParcels.isEmpty()) {
            loadFromScenario(numAgents);
        } else {
            generateRandomScenario(numCustomers, numAgents);
        }
    }

    private void loadFromScenario(int numAgents) {
        initParcels.addAll(scenarioParcels);
        for (Parcel p : scenarioParcels) {
            parcelDirectory.put(p.getDestination(), p);
            initTotalDemand += p.getDemand();
        }
        previewNodes.clear();
        for (Parcel p : scenarioParcels) previewNodes.add(p.getDestination());

        backupCounter = numAgents;
        initPendingAgents = numAgents;
        for (int i = 1; i <= numAgents; i++) {
            String name = "DA" + i;
            Point home = warehouses.get((i - 1) % warehouses.size());
            spawnDynamicAgent(name, home.x, home.y, 5, true);
            agentHomeDepots.put(name, home);
            activeDrivingAgents.add(name);
            currentLocs.put(name, home);
        }
        myGui.log("Loaded scenario: " + warehouses.size() + " warehouse(s), " + initParcels.size() + " parcel(s).");
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
    }

    private void generateRandomScenario(int numCustomers, int numAgents) {
        int cols = (int) Math.ceil(Math.sqrt(numCustomers));
        int rows = (int) Math.ceil((double) numCustomers / cols);
        int cellW = 100 / cols;
        int cellH = 100 / rows;

        Random rand = new Random();
        backupCounter = numAgents;
        int count = 1;
        Point primaryWh = warehouses.isEmpty() ? new Point(50, 50) : warehouses.get(0);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (count > numCustomers) break;
                int x = (c * cellW) + rand.nextInt(Math.max(1, cellW - 5));
                int y = (r * cellH) + rand.nextInt(Math.max(1, cellH - 5));

                Point origin = multiWarehouseMode ? nearestWarehouse(x, y) : primaryWh;
                Parcel p = new Parcel("P" + count, x, y, 1, origin);
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
            Point home = warehouses.get((i - 1) % warehouses.size());
            spawnDynamicAgent(name, home.x, home.y, 5, true);
            agentHomeDepots.put(name, home);
            activeDrivingAgents.add(name);
            currentLocs.put(name, home);
        }
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths, fleetCapacities, previewNodes);
    }

    private void checkCapacityLoop() {
        int currentCapacity = fleetCapacities.values().stream().mapToInt(Integer::intValue).sum();
        if (currentCapacity < initTotalDemand) {
            backupCounter++;
            String backupName = "DA" + backupCounter;
            Point home = warehouses.get((backupCounter - 1) % warehouses.size());
            myGui.log("Alert: Insufficient capacity. Spawning Backup: " + backupName);
            spawnDynamicAgent(backupName, home.x, home.y, 5, true);
            agentHomeDepots.put(backupName, home);
            activeDrivingAgents.add(backupName);
            currentLocs.put(backupName, home);
            initPendingAgents = 1;
        } else {
            myGui.log("Capacity check passed. Click '2. Plot Routes'.");
            myGui.enablePlotting();
        }
    }

    public void plotRoutes() {
        myGui.log("--- Plotting Routes via Tabu Solver ---");
        RouteState baseState = new RouteState();
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            baseState.addAgent(name, agentHomeDepots.getOrDefault(name, warehouses.get(0)));
        }
        for (Parcel p : initParcels) {
            baseState = tabuEngine.optimize(baseState, p, fleetCapacities, parcelDirectory,
                    new HashMap<>(), new HashSet<>(), agentHomeDepots, msg -> myGui.log(msg));
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

    public void dispatchFleet() {
        myGui.log("--- Dispatching Fleet ---");
        dispatchRoutes(plannedBaseState, null, new HashMap<>());
        isPhase2Active = true;
        myGui.setPhase2Enabled(true);
        myGui.log("Fleet has been dispatched and is now moving.");
    }

    public void injectDynamicParcel(Parcel newParcel) {
        if (!isPhase2Active) return;
        parcelDirectory.put(newParcel.getDestination(), newParcel);
        dynamicDestinations.add(newParcel.getDestination());
        myGui.log("Dynamic Request: " + newParcel.getId() + " from WH ("
                + newParcel.getOriginWarehouse().x + "," + newParcel.getOriginWarehouse().y + ")");

        RouteState snapshot = new RouteState();
        Map<String, Integer> lockedPrefixes = new HashMap<>();
        Map<String, Set<Point>> trunkContents = new HashMap<>();

        for (AID aid : fleet) {
            String name = aid.getLocalName();
            Point depot = agentHomeDepots.getOrDefault(name, warehouses.get(0));
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

        CompletableFuture.supplyAsync(() -> tabuEngine.optimize(snapshot, newParcel, fleetCapacities, parcelDirectory,
                        lockedPrefixes, dynamicSnapshot, agentHomeDepots, msg -> myGui.log(msg)))
                .thenAccept(optimizedState -> addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
                    public void action() { dispatchRoutes(optimizedState, newParcel, trunkContents); }
                }));
    }

    private void dispatchRoutes(RouteState state, Parcel dynamicParcel, Map<String, Set<Point>> trunkContents) {
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            Point depot = agentHomeDepots.getOrDefault(name, warehouses.get(0));
            List<Point> mathNodes = state.getRoutes().get(name);
            if (mathNodes == null || mathNodes.size() < 2) continue;

            Set<Point> inTrunk = trunkContents.getOrDefault(name, new HashSet<>());
            List<Point> physicalStops = new ArrayList<>();
            boolean hasInventoryForDynamic = false;

            for (int i = 1; i < mathNodes.size(); i++) {
                Point p = mathNodes.get(i);

                if (p.equals(depot)) {
                    hasInventoryForDynamic = true;
                    if (physicalStops.isEmpty() || !physicalStops.get(physicalStops.size() - 1).equals(depot)) {
                        physicalStops.add(p);
                    }
                    continue;
                }

                if (inTrunk.contains(p)) {
                    physicalStops.add(p);
                    continue;
                }

                Parcel parcel = parcelDirectory.get(p);
                Point requiredWh = parcel != null ? parcel.getOriginWarehouse() : depot;

                if (dynamicDestinations.contains(p) && !hasInventoryForDynamic) {
                    if (physicalStops.isEmpty() || !physicalStops.get(physicalStops.size() - 1).equals(requiredWh)) {
                        physicalStops.add(new Point(requiredWh));
                    }
                    hasInventoryForDynamic = true;
                }
                physicalStops.add(p);
            }

            if (!physicalStops.isEmpty() && !physicalStops.get(physicalStops.size() - 1).equals(depot)) {
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

    public void deployStandby(String name, int capacity) {
        Point home = warehouses.get(0);
        myGui.log("Deploying Standby Agent: " + name + " at WH (" + home.x + "," + home.y + ")");
        spawnDynamicAgent(name, home.x, home.y, capacity, false);

        fleetCapacities.put(name, capacity);
        agentHomeDepots.put(name, home);
        activeDrivingAgents.add(name);
        currentLocs.put(name, home);
        remainingPaths.put(name, new ArrayList<>());
        initialPlannedRoutes.put(name, new ArrayList<>(List.of(home)));
        actualDrivenRoutes.put(name, new ArrayList<>(List.of(home)));

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
