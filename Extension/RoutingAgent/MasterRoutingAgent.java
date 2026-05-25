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
import java.util.stream.Collectors;

public class MasterRoutingAgent extends Agent {

    public static final String CID_ROUTE    = "vrp-route";
    public static final String CID_TRACKING = "vrp-tracking";
    public static final String CID_DONE     = "vrp-done";
    public static final String CID_CAPACITY = "vrp-capacity";
    private static final String PREFIX_ROUTE = "ROUTE:";

    public enum SpawnMode { CENTRALIZED, DISTRIBUTED }

    private static final int[][] WH_3_POSITIONS = {
            {20, 20},   // WH-0 top-left
            {80, 20},   // WH-1 top-right
            {50, 80}    // WH-2 bottom-centre
    };

    private MainWindow  myGui;
    private final TabuRoutingEngine tabuEngine = new TabuRoutingEngine();
    private final List<AID>         fleet      = new ArrayList<>();
    private final Point              virtualDepot = new Point(50, 50); // Tabu's hardcoded depot

    private final Map<String, Integer>    fleetCapacities     = new HashMap<>();
    public  final Map<Point,   Parcel>    parcelDirectory     = new HashMap<>();
    private final Map<String, Point>      currentLocs         = new HashMap<>();
    private final Map<String, List<Point>> actualDrivenRoutes  = new HashMap<>();
    private final Map<String, List<Point>> remainingPaths      = new HashMap<>();
    private final Set<String>              activeDrivingAgents = new HashSet<>();
    private final Map<String, List<Point>> initialPlannedRoutes = new HashMap<>();
    private final Set<Point>               dynamicDestinations  = new HashSet<>();

    private List<Point>  previewNodes     = new ArrayList<>();
    private RouteState   plannedBaseState = null;
    private boolean      isPhase2Active   = false;
    private List<Parcel> initParcels      = new ArrayList<>();
    private int          initTotalDemand  = 0;
    private int          initPendingAgents = 0;
    private int          backupCounter    = 0;

    private List<Warehouse>            warehouses       = new ArrayList<>();
    private SpawnMode                  spawnMode        = SpawnMode.DISTRIBUTED;
    /** DA local-name → warehouse id it belongs to. */
    private Map<String, Integer>       agentWarehouseId = new HashMap<>();
    /** warehouse id → list of AIDs assigned to it. */
    private Map<Integer, List<AID>>    warehouseFleet   = new HashMap<>();
    /** warehouse id → parcels assigned to it. */
    private Map<Integer, List<Parcel>> warehouseParcels = new HashMap<>();

    private final Deque<Parcel> dynamicParcelQueue = new ArrayDeque<>();
    private boolean dynamicRerouteScheduled = false;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void setup() {
        myGui = new MainWindow(this);
        myGui.setVisible(true);
        myGui.log("System Booted. Select map source and Prepare Environment.");

        // GPS tracking behaviour
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_TRACKING);
                ACLMessage msg = receive(tpl);
                if (msg != null) processGpsPing(msg.getSender().getLocalName(), msg.getContent());
                else block();
            }
        });

        // Done signal
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_DONE);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    String name = msg.getSender().getLocalName();
                    List<Point> rem = remainingPaths.getOrDefault(name, List.of());
                    if (rem.isEmpty()) {
                        activeDrivingAgents.remove(name);
                    }
                    tryEnableSummary();
                } else block();
            }
        });

        // Capacity registration
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_CAPACITY);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    String name = msg.getSender().getLocalName();
                    try {
                        String[] parts = msg.getContent().split(":", 2);
                        if (parts.length < 2) throw new NumberFormatException("bad capacity message");
                        int cap = Integer.parseInt(parts[1].trim());
                        fleetCapacities.put(name, cap);
                        myGui.log(name + " capacity registered: " + cap
                                + " (" + Warehouse.displayName(agentWarehouseId.getOrDefault(name, 0)) + ")");
                        if (initPendingAgents > 0) {
                            initPendingAgents--;
                            if (initPendingAgents <= 0) checkCapacityLoop();
                        }
                    } catch (Exception e) {
                        myGui.log("WARNING: invalid capacity from " + name + ": " + msg.getContent());
                    }
                } else block();
            }
        });
    }


    public void previewMap(String path) {
        try {
            MapLoader.ParsedData mapData = MapLoader.load(path);
            previewNodes.clear();
            for (Parcel p : mapData.parcels) previewNodes.add(p.getDestination());
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                    fleetCapacities, previewNodes, warehouses);
            myGui.log("Map preview loaded.");
        } catch (Exception e) { myGui.log("Preview failed: " + e.getMessage()); }
    }

    public void clearPreview() {
        previewNodes.clear();
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
        myGui.log("Map cleared. Reverted to random generation.");
    }


    public void prepareEnvironment(int numCustomers, int numAgents,
                                   String mapFilePath,
                                   int numWarehouses, SpawnMode mode) {
        prepareEnvironment(numCustomers, numAgents, mapFilePath, numWarehouses, mode, null);
    }

    /**
     * @param customWarehousePositions optional [whIndex][x,y] for multi-warehouse random mode;
     *                                 ignored when a map file supplies warehouses
     */
    public void prepareEnvironment(int numCustomers, int numAgents,
                                   String mapFilePath,
                                   int numWarehouses, SpawnMode mode,
                                   int[][] customWarehousePositions) {
        this.spawnMode = mode;
        myGui.log("--- Preparing: " + numWarehouses + " warehouse(s), mode=" + mode + " ---");

        // Reset state
        initParcels.clear(); initTotalDemand = 0; previewNodes.clear();
        dynamicDestinations.clear(); agentWarehouseId.clear(); fleet.clear();
        warehouseFleet.clear(); warehouseParcels.clear();
        fleetCapacities.clear(); currentLocs.clear(); actualDrivenRoutes.clear();
        remainingPaths.clear(); activeDrivingAgents.clear(); initialPlannedRoutes.clear();
        parcelDirectory.clear(); plannedBaseState = null; isPhase2Active = false;
        backupCounter = 0; initPendingAgents = 0;
        dynamicParcelQueue.clear(); dynamicRerouteScheduled = false;
        myGui.disableSummary();

        // Build warehouses
        warehouses.clear();
        if (numWarehouses == 1) {
            warehouses.add(new Warehouse(0, 50, 50, "Main Depot"));
        } else {
            int count = (customWarehousePositions != null && customWarehousePositions.length > 0)
                    ? customWarehousePositions.length
                    : numWarehouses;
            for (int i = 0; i < count; i++) {
                int x = (i < WH_3_POSITIONS.length) ? WH_3_POSITIONS[i][0] : 50;
                int y = (i < WH_3_POSITIONS.length) ? WH_3_POSITIONS[i][1] : 50;
                if (customWarehousePositions != null
                        && i < customWarehousePositions.length
                        && customWarehousePositions[i] != null
                        && customWarehousePositions[i].length >= 2) {
                    x = customWarehousePositions[i][0];
                    y = customWarehousePositions[i][1];
                }
                warehouses.add(new Warehouse(i, x, y));
            }
        }
        for (Warehouse wh : warehouses) {
            warehouseFleet.put(wh.getId(), new ArrayList<>());
            warehouseParcels.put(wh.getId(), new ArrayList<>());
            myGui.log("  " + wh);
        }
        if (numWarehouses > 1 && customWarehousePositions != null && mapFilePath.equals("RANDOM")) {
            myGui.log("Using custom warehouse coordinates from Setup.");
        }

        if (!mapFilePath.equals("RANDOM")) {
            myGui.log("Note: file mode uses single-warehouse (WH-1 only).");
            loadFromFile(mapFilePath, numAgents);
        } else {
            generateRandom(numCustomers, numAgents);
        }

        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
    }

    private void loadFromFile(String path, int numAgents) {
        try {
            MapLoader.ParsedData mapData = MapLoader.load(path);
            backupCounter = numAgents;

            // Merge warehouses from file into our warehouse list (if multi-warehouse file)
            if (mapData.warehouses.size() > 1) {
                warehouses.clear();
                warehouseFleet.clear();
                warehouseParcels.clear();
                for (Warehouse wh : mapData.warehouses) {
                    warehouses.add(wh);
                    warehouseFleet.put(wh.getId(), new ArrayList<>());
                    warehouseParcels.put(wh.getId(), new ArrayList<>());
                }
                myGui.log("File loaded " + warehouses.size() + " warehouses.");
            }

            for (Parcel p : mapData.parcels) {
                // Parcel already has sourceWarehouseId set by MapLoader
                int whId = p.getSourceWarehouseId();
                if (!warehouseParcels.containsKey(whId)) {
                    myGui.log("WARNING: parcel " + p.getId()
                            + " references unknown warehouse " + whId + " — assigning to WH-1.");
                    whId = 0;
                }
                initParcels.add(p);
                registerParcel(p);
                initTotalDemand += p.getDemand();
                previewNodes.add(p.getDestination());
                warehouseParcels.get(whId).add(p);
            }
            // Pass null so each agent spawns at their assigned warehouse position.
            // mapData.warehouse (first WH position) was the old single-warehouse
            // shortcut — it incorrectly placed all agents at WH-0 in multi-WH files.
            spawnAgentGroup(numAgents, null);
        } catch (Exception e) { myGui.log("Error loading map: " + e.getMessage()); }
    }

    private void generateRandom(int numCustomers, int numAgents) {
        Random rand = new Random();
        backupCounter = numAgents;
        int count    = 1;
        int attempts = 0;
        int maxAttempts = numCustomers * 50;

        while (count <= numCustomers && attempts < maxAttempts) {
            attempts++;
            int   x  = rand.nextInt(98) + 1;
            int   y  = rand.nextInt(98) + 1;
            Point pt = new Point(x, y);
            // a warehouse square. TrackerPanel.isWarehousePoint() skips drawing
            // dots at warehouse coords, making those customers invisible.
            if (isWarehousePosition(pt)) continue;

            Warehouse nearest = nearestWarehouse(pt);
            Parcel p = new Parcel("P" + count, x, y, 1, nearest.getId());
            initParcels.add(p);
            registerParcel(p);
            initTotalDemand += p.getDemand();
            previewNodes.add(p.getDestination());
            warehouseParcels.get(nearest.getId()).add(p);
            count++;
        }
        if (count <= numCustomers)
            myGui.log("WARNING: generated only " + (count-1) + " of "
                    + numCustomers + " customers (warehouse position collisions).");

        spawnAgentGroup(numAgents, null);
    }

    /** True if p exactly matches any warehouse position. */
    private boolean isWarehousePosition(Point p) {
        for (Warehouse wh : warehouses)
            if (wh.getPos().equals(p)) return true;
        return false;
    }

    /** Spawn numAgents DAs, distributing across warehouses per spawnMode. */
    private void spawnAgentGroup(int numAgents, Point fileWarehousePos) {
        initPendingAgents = numAgents;
        for (int i = 1; i <= numAgents; i++) {
            String    name = "DA" + i;
            int       whId = (spawnMode == SpawnMode.CENTRALIZED || warehouses.size() == 1)
                             ? 0 : (i - 1) % warehouses.size();
            Warehouse wh   = warehouses.get(whId);
            int sx = (fileWarehousePos != null) ? fileWarehousePos.x : wh.getX();
            int sy = (fileWarehousePos != null) ? fileWarehousePos.y : wh.getY();

            agentWarehouseId.put(name, whId);
            warehouseFleet.get(whId).add(new AID(name, AID.ISLOCALNAME));
            spawnDynamicAgent(name, sx, sy, 5, true);
            activeDrivingAgents.add(name);
            currentLocs.put(name, new Point(sx, sy));
            myGui.log("  Spawned " + name + " → " + Warehouse.displayName(whId) + " at (" + sx + "," + sy + ")");
        }
    }


    private void checkCapacityLoop() {
        int currentCapacity = fleetCapacities.values().stream().mapToInt(Integer::intValue).sum();
        if (currentCapacity < initTotalDemand) {
            backupCounter++;
            String backupName = "DA" + backupCounter;

            int targetWhId = findMostStrainedWarehouse();
            if (warehouseDeficit(targetWhId) <= 0) {
                targetWhId = 0;
            }
            Warehouse targetWh = getWarehouseById(targetWhId);

            myGui.log("Alert: Insufficient capacity (" + currentCapacity + " < "
                    + initTotalDemand + "). Spawning backup " + backupName
                    + " at " + targetWh.getName() + ".");

            spawnDynamicAgent(backupName, targetWh.getX(), targetWh.getY(), 5, false);
            fleetCapacities.put(backupName, 5);
            activeDrivingAgents.add(backupName);
            currentLocs.put(backupName, targetWh.getPos());
            agentWarehouseId.put(backupName, targetWhId);
            warehouseFleet.get(targetWhId).add(new AID(backupName, AID.ISLOCALNAME));
            checkCapacityLoop();
        } else {
            myGui.log("Capacity check passed (" + currentCapacity
                    + " ≥ " + initTotalDemand + "). Click '2. Plot Routes'.");
            myGui.enablePlotting();
        }
    }

    /** Returns the warehouse ID whose parcel demand most exceeds its agents' capacity. */
    private int warehouseDeficit(int whId) {
        int demand   = warehouseParcels.getOrDefault(whId, List.of())
                                       .stream().mapToInt(Parcel::getDemand).sum();
        int capacity = warehouseFleet.getOrDefault(whId, List.of())
                                     .stream()
                                     .mapToInt(aid -> fleetCapacities
                                             .getOrDefault(aid.getLocalName(), 0))
                                     .sum();
        return demand - capacity;
    }

    private int findMostStrainedWarehouse() {
        int worstWhId    = 0;
        int worstDeficit = 0;

        for (Warehouse wh : warehouses) {
            int demand   = warehouseParcels.getOrDefault(wh.getId(), List.of())
                                           .stream().mapToInt(Parcel::getDemand).sum();
            int capacity = warehouseFleet.getOrDefault(wh.getId(), List.of())
                                         .stream()
                                         .mapToInt(aid -> fleetCapacities
                                                 .getOrDefault(aid.getLocalName(), 0))
                                         .sum();
            int deficit  = demand - capacity;
            if (deficit > worstDeficit) {
                worstDeficit = deficit;
                worstWhId    = wh.getId();
            }
        }
        return worstWhId;
    }

    private void logSolver(String msg) {
        myGui.log(msg);
    }

    private void registerParcel(Parcel p) {
        Point dest = p.getDestination();
        Parcel existing = parcelDirectory.get(dest);
        if (existing != null && !existing.getId().equals(p.getId())) {
            myGui.log("WARNING: " + p.getId() + " overlaps coords with " + existing.getId()
                    + " at (" + dest.x + "," + dest.y + ")");
        }
        parcelDirectory.put(dest, p);
    }

    private void tryEnableSummary() {
        if (!activeDrivingAgents.isEmpty()) return;
        if (dynamicRerouteScheduled || !dynamicParcelQueue.isEmpty()) return;
        for (List<Point> rem : remainingPaths.values()) {
            if (rem != null && !rem.isEmpty()) return;
        }
        myGui.enableSummary();
    }


    /**
     * Run Tabu SEPARATELY per warehouse, using coordinate translation.
     * Results are merged into plannedBaseState.
     */
    public void plotRoutes() {
        myGui.log("--- Plotting Routes (mode=" + spawnMode + ") ---");
        RouteState merged = new RouteState();

        if (spawnMode == SpawnMode.CENTRALIZED) {
            // The old per-warehouse loop skipped WH-1 and WH-2 because they
            // had no agents, silently dropping all their parcels.
            // Now we collect ALL parcels from ALL warehouses into one routing run.
            Warehouse mainWh = warehouses.get(0);
            int dx = 50 - mainWh.getX();
            int dy = 50 - mainWh.getY();

            // All agents start at the main warehouse (shifted to virtual depot)
            RouteState state = new RouteState();
            for (AID aid : fleet) {
                state.addAgent(aid.getLocalName(), new Point(50, 50));
            }

            // Collect every parcel from every warehouse
            List<Parcel> allParcels = new ArrayList<>();
            for (Warehouse wh : warehouses) {
                List<Parcel> wp = warehouseParcels.getOrDefault(wh.getId(), List.of());
                allParcels.addAll(wp);
            }
            myGui.log("  CENTRALIZED: " + fleet.size() + " agents, "
                    + allParcels.size() + " total parcels.");

            Map<Point, Parcel> shiftedDir = buildShiftedDirectory(dx, dy);

            for (Parcel p : allParcels) {
                Parcel shifted = shiftParcel(p, dx, dy);
                state = tabuEngine.optimize(state, shifted,
                        fleetCapacities, shiftedDir,
                        new HashMap<>(), new HashSet<>(), this::logSolver);
            }

            // Shift back and merge
            for (Map.Entry<String, List<Point>> e : state.getRoutes().entrySet()) {
                List<Point> real = shiftPoints(e.getValue(), -dx, -dy, mainWh.getPos());
                merged.getRoutes().put(e.getKey(), real);
                initialPlannedRoutes.put(e.getKey(), new ArrayList<>(real));
                remainingPaths.put(e.getKey(), new ArrayList<>(real));
            }

        } else {
            // DISTRIBUTED: per-warehouse routing (unchanged)
            for (Warehouse wh : warehouses) {
                List<AID>    agents  = warehouseFleet.getOrDefault(wh.getId(), List.of());
                List<Parcel> parcels = warehouseParcels.getOrDefault(wh.getId(), List.of());

                if (agents.isEmpty()) {
                    myGui.log("  " + wh.getName() + ": no agents, skipping.");
                    continue;
                }
                myGui.log("  " + wh.getName() + ": " + agents.size()
                        + " agents, " + parcels.size() + " parcels.");

                int dx = 50 - wh.getX();
                int dy = 50 - wh.getY();

                RouteState whState = new RouteState();
                for (AID aid : agents) {
                    whState.addAgent(aid.getLocalName(), new Point(50, 50));
                }

                Map<Point, Parcel> shiftedDir = new HashMap<>();
                for (Parcel p : parcels) {
                    shiftedDir.put(new Point(p.getDestination().x + dx,
                                             p.getDestination().y + dy), p);
                }

                for (Parcel p : parcels) {
                    Parcel shifted = shiftParcel(p, dx, dy);
                    whState = tabuEngine.optimize(whState, shifted,
                            filterCapacities(agents), shiftedDir,
                            new HashMap<>(), new HashSet<>(), this::logSolver);
                }

                for (Map.Entry<String, List<Point>> e : whState.getRoutes().entrySet()) {
                    List<Point> real = shiftPoints(e.getValue(), -dx, -dy, wh.getPos());
                    merged.getRoutes().put(e.getKey(), real);
                    initialPlannedRoutes.put(e.getKey(), new ArrayList<>(real));
                    remainingPaths.put(e.getKey(), new ArrayList<>(real));
                }
            }
        }

        // Any agent not yet in the merged state gets an empty route at their warehouse
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            if (!merged.getRoutes().containsKey(name)) {
                List<Point> solo = new ArrayList<>();
                solo.add(getAgentWarehouse(name).getPos());
                merged.getRoutes().put(name, solo);
                initialPlannedRoutes.put(name, new ArrayList<>(solo));
                remainingPaths.put(name, new ArrayList<>(solo));
            }
        }

        previewNodes.clear();
        plannedBaseState = merged;
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
        myGui.log("Routes plotted. Click '3. Dispatch Fleet'.");
        myGui.enableDispatch();
    }


    public void dispatchFleet() {
        myGui.log("--- Dispatching Fleet ---");
        dispatchRoutes(plannedBaseState, null);
        isPhase2Active = true;
        myGui.setPhase2Enabled(true);
        myGui.log("Fleet dispatched and moving.");
    }


    /**
     * Inject a new parcel mid-operation.
     *
     * The key fix: agents from OTHER warehouses have their FULL remaining route
     * locked before Tabu runs, so Tabu cannot reassign their parcels.
     * Only agents belonging to the source warehouse are freely reroutable.
     */
    public void injectDynamicParcel(Parcel newParcel) {
        if (!isPhase2Active) return;

        Warehouse srcWh;
        try {
            srcWh = getWarehouseById(newParcel.getSourceWarehouseId());
        } catch (IllegalArgumentException e) {
            myGui.log("ERROR: " + e.getMessage() + " — parcel dropped.");
            return;
        }

        registerParcel(newParcel);
        dynamicDestinations.add(newParcel.getDestination());
        warehouseParcels.get(srcWh.getId()).add(newParcel);
        myGui.disableSummary();

        myGui.log("Dynamic Request: " + newParcel.getId()
                + " dest=(" + newParcel.getDestination().x + ","
                + newParcel.getDestination().y + ") from " + srcWh.getName());

        dynamicParcelQueue.addLast(newParcel);
        scheduleDynamicReroute();
    }

    private void scheduleDynamicReroute() {
        if (dynamicRerouteScheduled) return;
        dynamicRerouteScheduled = true;
        addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
            public void action() {
                try {
                    while (!dynamicParcelQueue.isEmpty()) {
                        runDynamicReroute(dynamicParcelQueue.pollFirst());
                    }
                } catch (Exception e) {
                    myGui.log("ERROR during dynamic reroute: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    dynamicRerouteScheduled = false;
                    tryEnableSummary();
                }
            }
        });
    }

    private void runDynamicReroute(Parcel newParcel) {
        Warehouse srcWh = getWarehouseById(newParcel.getSourceWarehouseId());

        List<String> candidateAgents = warehouseFleet
                .getOrDefault(srcWh.getId(), List.of())
                .stream()
                .map(aid -> aid.getLocalName())
                .collect(Collectors.toList());

        RouteState           snapshot       = new RouteState();
        Map<String, Integer> lockedPrefixes = new HashMap<>();

        for (AID aid : fleet) {
            String      name      = aid.getLocalName();
            Point       loc       = currentLocs.getOrDefault(name, getAgentWarehouse(name).getPos());
            List<Point> remaining = remainingPaths.getOrDefault(name, new ArrayList<>());

            snapshot.addAgent(name, loc);
            for (int i = 0; i < remaining.size(); i++) {
                snapshot.insertNode(name, i + 1, remaining.get(i));
            }
            if (!candidateAgents.contains(name)) {
                lockedPrefixes.put(name, remaining.size());
                continue;
            }

            int lockCount = 0;
            if (!remaining.isEmpty()) {
                Point agentWhPos   = getAgentWarehouse(name).getPos();
                int   nextDepotIdx = remaining.indexOf(agentWhPos);
                lockCount = (nextDepotIdx == -1) ? remaining.size() : nextDepotIdx;
                lockCount = Math.max(lockCount, 1);
                if (loc.distance(remaining.get(0)) < 15.0 && remaining.size() > 1) {
                    lockCount = Math.max(lockCount, 2);
                }
            }
            lockedPrefixes.put(name, lockCount);
        }

        Set<Point> dynamicSnapshot = new HashSet<>(dynamicDestinations);
        int dx = 50 - srcWh.getX();
        int dy = 50 - srcWh.getY();

        RouteState         shiftedSnapshot = shiftStateIn(snapshot, dx, dy);
        Parcel             shiftedParcel   = shiftParcel(newParcel, dx, dy);
        Map<Point, Parcel> shiftedDir      = buildShiftedDirectory(dx, dy);
        Set<Point>         shiftedDynamic  = dynamicSnapshot.stream()
                .map(p -> new Point(p.x + dx, p.y + dy))
                .collect(Collectors.toSet());

        RouteState optimized = tabuEngine.optimize(
                shiftedSnapshot, shiftedParcel,
                fleetCapacities, shiftedDir,
                lockedPrefixes, shiftedDynamic, this::logSolver);

        Point shiftedDest = shiftedParcel.getDestination();
        if (!tabuEngine.routeContainsDestination(optimized, shiftedDest)) {
            myGui.log("WARNING: Tabu could not place " + newParcel.getId()
                    + " — no feasible route with current capacity.");
            return;
        }

        RouteState real = shiftStateBack(optimized, -dx, -dy, srcWh.getPos());
        dispatchRoutes(real, newParcel);
    }


    /**
     * Deploy a standby (backup) agent at the specified warehouse.
     *
     * What does "Standby Warehouse" mean?
     *   A standby agent is a reserve vehicle that is idle until deployed.
     *   The "Standby Warehouse" is WHERE it starts — which physical depot
     *   it will leave from when given a route.
     *   Choosing the right warehouse matters:
     *     - Closest to the new parcel's source = faster pickup
     *     - Same as the overloaded warehouse = immediately helps that group
     *   The agent registers itself with that warehouse's fleet so future
     *   rerouting correctly restricts Tabu to that warehouse group.
     *
     * @param name         agent name (must be unique)
     * @param capacity     how many parcels it can carry
     * @param warehouseId  which warehouse it starts from (0-based index)
     */
    /**
     * Deploy a standby agent. Warehouse is chosen automatically
     * (the most strained warehouse), so the GUI doesn't need a selector.
     */
    public void deployStandby(String name, int capacity) {
        deployStandby(name, capacity, findMostStrainedWarehouse());
    }

    private void deployStandby(String name, int capacity, int warehouseId) {
        Warehouse wh = getWarehouseById(warehouseId);
        myGui.log("Deploying Standby: " + name + " cap=" + capacity
                + " at " + wh.getName());

        spawnDynamicAgent(name, wh.getX(), wh.getY(), capacity, false);

        fleetCapacities.put(name, capacity);
        activeDrivingAgents.add(name);
        currentLocs.put(name, wh.getPos());
        remainingPaths.put(name, new ArrayList<>());
        initialPlannedRoutes.put(name, new ArrayList<>(List.of(wh.getPos())));
        actualDrivenRoutes.put(name, new ArrayList<>(List.of(wh.getPos())));
        agentWarehouseId.put(name, warehouseId);
        warehouseFleet.get(warehouseId).add(new AID(name, AID.ISLOCALNAME));

        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
    }


    /**
     * Translate math-space routes into physical stop sequences and send to DAs.
     * Virtual-depot (50,50) nodes are translated to the agent's actual warehouse.
     * Dynamic parcels get a warehouse pickup inserted before their destination.
     */
    private void dispatchRoutes(RouteState state, Parcel dynamicParcel) {
        for (AID aid : fleet) {
            String      name      = aid.getLocalName();
            List<Point> mathNodes = state.getRoutes().get(name);
            if (mathNodes == null || mathNodes.size() < 2) continue;

            Warehouse agentWh    = getAgentWarehouse(name);
            Point     agentWhPos = agentWh.getPos();

            List<Point>  physicalStops    = new ArrayList<>();
            Set<Point>   visitedWarehouses = new HashSet<>();

            for (int i = 1; i < mathNodes.size(); i++) {
                Point p = mathNodes.get(i);

                if (p.equals(virtualDepot) && !agentWhPos.equals(virtualDepot)) {
                    p = new Point(agentWhPos);
                }

                if (p.equals(agentWhPos)) {
                    visitedWarehouses.add(agentWhPos);
                    if (physicalStops.isEmpty()
                            || !physicalStops.get(physicalStops.size()-1).equals(agentWhPos)) {
                        physicalStops.add(new Point(agentWhPos));
                    }
                    continue;
                }

                Parcel info = parcelDirectory.get(p);
                if (info != null) {
                    Point srcWhPos = getWarehouseById(info.getSourceWarehouseId()).getPos();
                    if (!visitedWarehouses.contains(srcWhPos)) {
                        if (physicalStops.isEmpty()
                                || !physicalStops.get(physicalStops.size() - 1).equals(srcWhPos)) {
                            physicalStops.add(new Point(srcWhPos));
                        }
                        visitedWarehouses.add(srcWhPos);
                    }
                }
                physicalStops.add(p);
            }

            if (!physicalStops.isEmpty()
                    && !physicalStops.get(physicalStops.size()-1).equals(agentWhPos)) {
                physicalStops.add(new Point(agentWhPos));
            }

            StringBuilder sb = new StringBuilder(PREFIX_ROUTE + "5:5|");
            for (int i = 0; i < physicalStops.size(); i++) {
                Point p = physicalStops.get(i);
                sb.append(p.x).append(":").append(p.y);
                if (i < physicalStops.size()-1) sb.append(",");
            }
            ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
            m.addReceiver(aid);
            m.setConversationId(CID_ROUTE);
            m.setContent(sb.toString());
            send(m);

            if (!physicalStops.isEmpty()) {
                activeDrivingAgents.add(name);
                remainingPaths.put(name, new ArrayList<>(physicalStops));
                myGui.disableSummary();
            }
        }
    }


    private void processGpsPing(String agentName, String content) {
        try {
            String[] parts  = content.split("\\|");
            String[] coords = parts[0].split(",");
            Point loc = new Point(Integer.parseInt(coords[0]),
                                  Integer.parseInt(coords[1]));
            currentLocs.put(agentName, loc);
            List<Point> history = actualDrivenRoutes.computeIfAbsent(agentName,
                    k -> new ArrayList<>());
            if (history.isEmpty() || !history.get(history.size()-1).equals(loc))
                history.add(loc);

            List<Point> rem = new ArrayList<>();
            if (parts.length > 1 && !parts[1].isBlank()) {
                for (String s : parts[1].split(";")) {
                    String[] sc = s.split(",");
                    rem.add(new Point(Integer.parseInt(sc[0]),
                                      Integer.parseInt(sc[1])));
                }
            }
            remainingPaths.put(agentName, rem);
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                    fleetCapacities, previewNodes, warehouses);
        } catch (Exception e) {
            myGui.log("WARNING: bad GPS from " + agentName + ": " + content);
        }
    }


    public void spawnDynamicAgent(String name, int startX, int startY,
                                  int capacity, boolean showGui) {
        try {
            Object[] args = showGui
                    ? new Object[]{startX + "," + startY + "," + capacity, "SHOW_GUI"}
                    : new Object[]{startX + "," + startY + "," + capacity};
            jade.wrapper.AgentController ac = getContainerController()
                    .createNewAgent(name,
                            "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", args);
            ac.start();
            fleet.add(new AID(name, AID.ISLOCALNAME));
        } catch (Exception e) { myGui.log("Failed to spawn " + name + ": " + e.getMessage()); }
    }


    private RouteState shiftStateIn(RouteState src, int dx, int dy) {
        RouteState r = new RouteState();
        for (Map.Entry<String, List<Point>> e : src.getRoutes().entrySet()) {
            List<Point> s = e.getValue();
            if (s.isEmpty()) continue;
            r.addAgent(e.getKey(), new Point(s.get(0).x + dx, s.get(0).y + dy));
            List<Point> d = r.getRoutes().get(e.getKey());
            for (int i = 1; i < s.size(); i++) d.add(new Point(s.get(i).x + dx, s.get(i).y + dy));
        }
        return r;
    }

    private RouteState shiftStateBack(RouteState src, int dx, int dy, Point realWhPos) {
        RouteState r = new RouteState();
        for (Map.Entry<String, List<Point>> e : src.getRoutes().entrySet()) {
            List<Point> s = e.getValue();
            if (s.isEmpty()) continue;
            r.addAgent(e.getKey(), new Point(s.get(0).x + dx, s.get(0).y + dy));
            List<Point> d = r.getRoutes().get(e.getKey());
            for (int i = 1; i < s.size(); i++) d.add(new Point(s.get(i).x + dx, s.get(i).y + dy));
        }
        return r;
    }

    private List<Point> shiftPoints(List<Point> src, int dx, int dy, Point realWhPos) {
        return src.stream()
                .map(p -> new Point(p.x + dx, p.y + dy))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<Point, Parcel> buildShiftedDirectory(int dx, int dy) {
        Map<Point, Parcel> m = new HashMap<>();
        parcelDirectory.forEach((k, v) ->
                m.put(new Point(k.x + dx, k.y + dy), v));
        return m;
    }

    private Parcel shiftParcel(Parcel p, int dx, int dy) {
        return new Parcel(p.getId(),
                p.getDestination().x + dx,
                p.getDestination().y + dy,
                p.getDemand(),
                p.getSourceWarehouseId());
    }

    private Map<String, Integer> filterCapacities(List<AID> agents) {
        Map<String, Integer> m = new HashMap<>();
        for (AID aid : agents) m.put(aid.getLocalName(),
                fleetCapacities.getOrDefault(aid.getLocalName(), 5));
        return m;
    }


    public Warehouse getWarehouseById(int id) throws IllegalArgumentException {
        for (Warehouse wh : warehouses) {
            if (wh.getId() == id) return wh;
        }
        throw new IllegalArgumentException(
                "Warehouse id=" + id + " does not exist. Valid ids: "
                + warehouses.stream().map(w -> String.valueOf(w.getId()))
                            .collect(java.util.stream.Collectors.joining(",")));
    }

    public Warehouse getAgentWarehouse(String agentName) {
        return getWarehouseById(agentWarehouseId.getOrDefault(agentName, 0));
    }

    private Warehouse nearestWarehouse(Point p) {
        return warehouses.stream()
                .min(Comparator.comparingDouble(w -> w.getPos().distance(p)))
                .orElse(new Warehouse(0, 50, 50));
    }

    public List<Warehouse>           getWarehouses()          { return warehouses; }
    public Map<String, List<Point>>  getInitialPlannedRoutes(){ return initialPlannedRoutes; }
    public Map<String, List<Point>>  getActualDrivenRoutes()  { return actualDrivenRoutes; }
    public Map<String, Integer>      getAgentWarehouseIds()   { return agentWarehouseId; }
}
