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
    private int expectedReturns = 0;
    private Set<String> finishedAgents = new HashSet<>();

    private List<Warehouse>            warehouses       = new ArrayList<>();
    private SpawnMode                  spawnMode        = SpawnMode.DISTRIBUTED;
    /** DA local-name → warehouse id it belongs to (may change via fleet repositioning). */
    private Map<String, Integer>       agentWarehouseId = new HashMap<>();
    /** DA local-name → warehouse id at spawn (for legend / repositioning visuals). */
    private final Map<String, Integer> agentOriginWarehouseId = new HashMap<>();
    /** One-time drive-to-depot leg before first dispatch after repositioning. */
    private final Map<String, Point>   repositionLegFrom = new HashMap<>();
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

                    // Immediately remove from active tracking
                    activeDrivingAgents.remove(name);

                    // Wipe the map line so we don't rely on the final GPS ping
                    remainingPaths.put(name, new ArrayList<>());

                    // Check if the day is over
                    tryEnableSummary();
                } else {
                    block();
                }
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
        dynamicDestinations.clear(); agentWarehouseId.clear(); agentOriginWarehouseId.clear();
        repositionLegFrom.clear(); fleet.clear();
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

    /**
     * Round-robin warehouse assignment for distributed mode.
     * When agents &gt; warehouses, extra agents share a depot (e.g. 4 agents / 3 WH → two at WH-1).
     */
    private int warehouseIdForAgentIndex(int agentIndexOneBased) {
        if (spawnMode == SpawnMode.CENTRALIZED || warehouses.isEmpty()) {
            return warehouses.get(0).getId();
        }
        int slot = (agentIndexOneBased - 1) % warehouses.size();
        return warehouses.get(slot).getId();
    }

    /** Slight map offset so multiple agents at one warehouse do not stack invisibly. */
    private Point spawnLocationForAgent(Warehouse wh, int peerIndex, int peersAtWarehouse,
                                        Point fixedWarehousePos) {
        if (fixedWarehousePos != null) {
            return new Point(fixedWarehousePos.x, fixedWarehousePos.y);
        }
        if (peersAtWarehouse <= 1) {
            return new Point(wh.getX(), wh.getY());
        }
        double angle = (2.0 * Math.PI * peerIndex) / peersAtWarehouse;
        int r = 4;
        int x = Math.max(0, Math.min(100, wh.getX() + (int) Math.round(r * Math.cos(angle))));
        int y = Math.max(0, Math.min(100, wh.getY() + (int) Math.round(r * Math.sin(angle))));
        return new Point(x, y);
    }

    /** Spawn numAgents DAs, distributing across warehouses per spawnMode. */
    private void spawnAgentGroup(int numAgents, Point fixedWarehousePos) {
        initPendingAgents = numAgents;

        Map<Integer, Integer> peersPerWh = new HashMap<>();
        for (int i = 1; i <= numAgents; i++) {
            int whId = warehouseIdForAgentIndex(i);
            peersPerWh.put(whId, peersPerWh.getOrDefault(whId, 0) + 1);
        }
        Map<Integer, Integer> peerSlotUsed = new HashMap<>();

        for (int i = 1; i <= numAgents; i++) {
            String    name = "DA" + i;
            int       whId = warehouseIdForAgentIndex(i);
            Warehouse wh   = getWarehouseById(whId);
            int       slot   = peerSlotUsed.getOrDefault(whId, 0);
            peerSlotUsed.put(whId, slot + 1);
            Point loc = spawnLocationForAgent(wh, slot, peersPerWh.getOrDefault(whId, 1), fixedWarehousePos);

            agentWarehouseId.put(name, whId);
            agentOriginWarehouseId.put(name, whId);
            warehouseFleet.get(whId).add(new AID(name, AID.ISLOCALNAME));
            spawnDynamicAgent(name, loc.x, loc.y, 5, true);
            activeDrivingAgents.add(name);
            currentLocs.put(name, new Point(loc));
            if (peersPerWh.getOrDefault(whId, 0) > 1) {
                myGui.log("  Spawned " + name + " → " + wh.getName()
                        + " at (" + loc.x + "," + loc.y + ") [shared depot, slot " + (slot + 1) + "]");
            } else {
                myGui.log("  Spawned " + name + " → " + wh.getName() + " at (" + loc.x + "," + loc.y + ")");
            }
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
            agentOriginWarehouseId.put(backupName, targetWhId);
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
        // 1. If anyone is still driving, wait.
        if (!activeDrivingAgents.isEmpty()) return;

        // 2. If we are doing dynamic routing, wait.
        if (dynamicRerouteScheduled || !dynamicParcelQueue.isEmpty()) return;

        // 3. Otherwise, unlock! (DO NOT loop through remainingPaths here)
        myGui.enableSummary();
    }

    /** Parcels not present on any agent route after a routing pass. */
    private List<Parcel> collectUnassignedParcels(RouteState state) {
        Set<Point> assigned = new HashSet<>();
        for (List<Point> route : state.getRoutes().values()) {
            for (Point p : route) {
                if (parcelDirectory.containsKey(p)) assigned.add(p);
            }
        }
        List<Parcel> out = new ArrayList<>();
        for (Parcel p : initParcels) {
            if (!assigned.contains(p.getDestination())) out.add(p);
        }
        return out;
    }

    /**
     * Assign a parcel to any fleet agent with spare capacity, including agents at other
     * warehouses. {@link #dispatchRoutes} will insert a pickup stop at the parcel's source WH.
     *
     * @return assigned agent name, or null if no agent can serve it
     */
    private Set<Point> homeDepotFor(String agentName) {
        return Set.of(getAgentWarehouse(agentName).getPos());
    }

    /** Capacity resets at home depot and at any warehouse pickup stop on the route. */
    private Set<Point> depotResetPointsFor(String agentName) {
        Set<Point> depots = new HashSet<>(homeDepotFor(agentName));
        for (Warehouse wh : warehouses) {
            depots.add(wh.getPos());
        }
        return depots;
    }

    private int warehouseSurplus(int whId) {
        return -warehouseDeficit(whId);
    }

    /**
     * Before Tabu runs: permanently reassign trucks from surplus warehouses to strained ones
     * so each regional solve stays parcel-local (no virtual-depot zig-zags).
     */
    private void applyFleetRepositioning() {
        if (spawnMode == SpawnMode.CENTRALIZED || warehouses.size() <= 1) return;

        myGui.log("--- Fleet Repositioning (regional truck reassignment) ---");
        int moves = 0;
        int guard = 0;

        while (guard++ < fleet.size() * warehouses.size()) {
            int needyWh = findMostStrainedWarehouse();
            int deficit = warehouseDeficit(needyWh);
            if (deficit <= 0) break;

            int donorWh = findBestDonorWarehouse(needyWh);
            if (donorWh < 0) {
                myGui.log("  No surplus trucks left; " + deficit
                        + " parcel slot(s) still short at "
                        + getWarehouseById(needyWh).getName()
                        + " (cross-warehouse pickup may follow).");
                break;
            }

            String agent = pickAgentToReposition(donorWh);
            if (agent == null) {
                myGui.log("  Cannot peel another truck from "
                        + getWarehouseById(donorWh).getName() + " without creating a deficit.");
                break;
            }

            repositionAgent(agent, needyWh);
            moves++;
        }

        if (moves > 0) {
            myGui.log("Fleet repositioning complete: " + moves + " truck(s) reassigned for the day.");
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                    fleetCapacities, previewNodes, warehouses);
        } else {
            myGui.log("  Fleet balanced — no truck moves required.");
        }
    }

    /** Donor with largest spare capacity that is not the needy warehouse. */
    private int findBestDonorWarehouse(int needyWhId) {
        int bestWh     = -1;
        int bestSurplus = 0;
        for (Warehouse wh : warehouses) {
            if (wh.getId() == needyWhId) continue;
            int surplus = warehouseSurplus(wh.getId());
            if (surplus > bestSurplus) {
                bestSurplus = surplus;
                bestWh      = wh.getId();
            }
        }
        return bestSurplus > 0 ? bestWh : -1;
    }

    /** Pick a donor truck whose capacity does not exceed the donor warehouse's surplus. */
    private String pickAgentToReposition(int donorWhId) {
        int surplus = warehouseSurplus(donorWhId);
        List<AID> agents = new ArrayList<>(warehouseFleet.getOrDefault(donorWhId, List.of()));
        agents.sort((a, b) -> Integer.compare(
                fleetCapacities.getOrDefault(b.getLocalName(), 5),
                fleetCapacities.getOrDefault(a.getLocalName(), 5)));
        for (AID aid : agents) {
            String name = aid.getLocalName();
            int cap = fleetCapacities.getOrDefault(name, 5);
            if (cap <= surplus) return name;
        }
        return null;
    }

    private void repositionAgent(String agentName, int toWhId) {
        int fromWhId = agentWarehouseId.getOrDefault(agentName, 0);
        if (fromWhId == toWhId) return;

        Warehouse fromWh = getWarehouseById(fromWhId);
        Warehouse toWh   = getWarehouseById(toWhId);
        int cap = fleetCapacities.getOrDefault(agentName, 5);

        warehouseFleet.get(fromWhId).removeIf(a -> a.getLocalName().equals(agentName));
        warehouseFleet.get(toWhId).add(new AID(agentName, AID.ISLOCALNAME));

        Point fromPos = new Point(fromWh.getPos());
        repositionLegFrom.put(agentName, fromPos);

        agentWarehouseId.put(agentName, toWhId);
        Point toLoc = spawnLocationForAgent(toWh, peersAtWarehouse(toWhId), peersAtWarehouse(toWhId), null);
        currentLocs.put(agentName, toLoc);

        myGui.log("  " + agentName + ": " + fromWh.getName() + " → " + toWh.getName()
                + " (cap " + cap + "; will drive to " + toWh.getName() + " before deliveries)");
    }

    private int peersAtWarehouse(int whId) {
        return warehouseFleet.getOrDefault(whId, List.of()).size();
    }

    private List<Point> prependRepositioningLeg(String agentName, List<Point> physicalStops) {
        Point from = repositionLegFrom.remove(agentName);
        if (from == null || physicalStops == null) return physicalStops;

        Point home = getAgentWarehouse(agentName).getPos();
        List<Point> out = new ArrayList<>();
        out.add(from);
        if (!from.equals(home)) {
            out.add(new Point(home));
        }
        for (Point p : physicalStops) {
            appendStopIfNew(out, p);
        }
        return out;
    }

    private int spareCapacitySlots(String agentName, List<Point> route) {
        if (route == null) return fleetCapacities.getOrDefault(agentName, 5);
        int cap = fleetCapacities.getOrDefault(agentName, 5);
        int peak = tabuEngine.peakRouteLoad(route, parcelDirectory, cap, Set.of(), depotResetPointsFor(agentName));
        return Math.max(0, cap - peak);
    }

    private int indexOfWarehouseStop(List<Point> route, Point whPos) {
        if (route == null) return -1;
        for (int j = 1; j < route.size(); j++) {
            if (route.get(j).equals(whPos)) return j;
        }
        return -1;
    }

    private int warehouseIdAt(Point p) {
        for (Warehouse wh : warehouses) {
            if (wh.getPos().equals(p)) return wh.getId();
        }
        return -1;
    }

    /** Last delivery index for parcels sourced at {@code srcWhId} after a scheduled pickup. */
    private int lastDeliveryIndexForWarehouse(List<Point> route, int pickupIdx, int srcWhId) {
        int last = pickupIdx;
        for (int j = pickupIdx + 1; j < route.size(); j++) {
            Point p = route.get(j);
            if (isWarehousePosition(p)) break;
            Parcel info = parcelDirectory.get(p);
            if (info != null && info.getSourceWarehouseId() == srcWhId) last = j;
        }
        return last;
    }

    private boolean segmentOnlyParcelsFromWh(List<Point> route, int from, int to, int srcWhId) {
        if (from > to) return true;
        for (int j = from; j <= to; j++) {
            Parcel info = parcelDirectory.get(route.get(j));
            if (info == null || info.getSourceWarehouseId() != srcWhId) return false;
        }
        return true;
    }

    /**
     * Merge duplicate warehouse visits when the DA has not collected yet — one stop per WH
     * serves every pending parcel from that warehouse (e.g. two dynamic parcels from WH-1).
     */
    private void consolidateRoutePickups(List<Point> route) {
        if (route == null || route.size() < 3) return;

        for (int i = route.size() - 1; i >= 2; i--) {
            if (route.get(i).equals(route.get(i - 1)) && isWarehousePosition(route.get(i))) {
                route.remove(i);
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = route.size() - 1; i >= 2; i--) {
                if (!isWarehousePosition(route.get(i))) continue;
                Point whPos = route.get(i);
                int   whId  = warehouseIdAt(whPos);
                if (whId < 0) continue;

                for (int j = i - 1; j >= 1; j--) {
                    if (!route.get(j).equals(whPos)) continue;
                    if (segmentOnlyParcelsFromWh(route, j + 1, i - 1, whId)) {
                        route.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    /** Build test route for insertion (reuses an existing WH pickup when one is already scheduled). */
    private List<Point> routeAfterInsertingParcel(List<Point> route, int insertIndex,
                                                  String agentName, Parcel parcel) {
        List<Point> test = new ArrayList<>(route);
        Point srcPos = getWarehouseById(parcel.getSourceWarehouseId()).getPos();
        Point home   = getAgentWarehouse(agentName).getPos();
        int   srcWhId = parcel.getSourceWarehouseId();

        if (!srcPos.equals(home)) {
            int existingPickup = indexOfWarehouseStop(route, srcPos);
            if (existingPickup >= 0) {
                int after = lastDeliveryIndexForWarehouse(route, existingPickup, srcWhId);
                test.add(after + 1, parcel.getDestination());
                return test;
            }
            int at = insertIndex;
            test.add(at, new Point(srcPos));
            at++;
            test.add(at, parcel.getDestination());
            return test;
        }
        test.add(insertIndex, parcel.getDestination());
        return test;
    }

    private void insertParcelWithPickup(RouteState state, String agentName, int insertIndex, Parcel parcel) {
        List<Point> route = state.getRoutes().get(agentName);
        Point srcPos = getWarehouseById(parcel.getSourceWarehouseId()).getPos();
        Point home   = getAgentWarehouse(agentName).getPos();
        int   srcWhId = parcel.getSourceWarehouseId();

        if (!srcPos.equals(home)) {
            int existingPickup = indexOfWarehouseStop(route, srcPos);
            if (existingPickup >= 0) {
                int after = lastDeliveryIndexForWarehouse(route, existingPickup, srcWhId);
                state.insertNode(agentName, after + 1, parcel.getDestination());
                return;
            }
            int at = insertIndex;
            state.insertNode(agentName, at, new Point(srcPos));
            at++;
            state.insertNode(agentName, at, parcel.getDestination());
            return;
        }
        state.insertNode(agentName, insertIndex, parcel.getDestination());
    }

    /** Lower score = better. Idle DAs and proximity to the parcel's source WH are preferred. */
    private double agentAssignmentScore(String agentName, RouteState state, Parcel parcel) {
        if (spareCapacitySlots(agentName, state.getRoutes().get(agentName)) < parcel.getDemand()) {
            return Double.MAX_VALUE;
        }
        Point loc = currentLocs.getOrDefault(agentName, getAgentWarehouse(agentName).getPos());
        Point src = getWarehouseById(parcel.getSourceWarehouseId()).getPos();
        double dist = loc.distance(src);
        int parcelStops = countParcelStops(state.getRoutes().get(agentName));
        if (parcelStops == 0) return dist - 1000.0;
        return dist + parcelStops * 2.0;
    }

    private boolean tryAssignParcelToAgent(RouteState state, String agentName, Parcel parcel,
                                           double[] outBestCost) {
        List<Point> route = state.getRoutes().get(agentName);
        if (route == null) return false;

        int cap = fleetCapacities.getOrDefault(agentName, 5);
        Set<Point> depots = depotResetPointsFor(agentName);
        Set<Point> noDynamic = Set.of();

        double bestCost  = Double.MAX_VALUE;
        int    bestIndex = -1;

        for (int i = 1; i <= route.size(); i++) {
            List<Point> test = routeAfterInsertingParcel(route, i, agentName, parcel);
            if (tabuEngine.peakRouteLoad(test, parcelDirectory, cap, noDynamic, depots) > cap) continue;

            Point from = route.get(i - 1);
            double cost = estimateCrossWarehouseLeg(from, parcel);
            if (cost < bestCost) {
                bestCost  = cost;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) return false;
        insertParcelWithPickup(state, agentName, bestIndex, parcel);
        if (outBestCost != null && outBestCost.length > 0) outBestCost[0] = bestCost;
        return true;
    }

    /** Fleet-wide: idle / nearest DA with spare capacity (used for dynamic parcels). */
    private String assignParcelToBestFleetAgent(RouteState state, Parcel parcel) {
        List<String> names = fleet.stream().map(AID::getLocalName)
                .sorted(Comparator.comparingDouble(n -> agentAssignmentScore(n, state, parcel)))
                .collect(Collectors.toList());

        String bestAgent = null;
        double bestCost  = Double.MAX_VALUE;

        for (String name : names) {
            if (agentAssignmentScore(name, state, parcel) >= Double.MAX_VALUE) continue;
            RouteState trial = state.cloneState();
            double[] cost = new double[1];
            if (!tryAssignParcelToAgent(trial, name, parcel, cost)) continue;
            if (cost[0] < bestCost) {
                bestCost  = cost[0];
                bestAgent = name;
            }
        }

        if (bestAgent == null) return null;
        tryAssignParcelToAgent(state, bestAgent, parcel, null);
        return bestAgent;
    }

    private String assignParcelCrossWarehouse(RouteState state, Parcel parcel) {
        List<AID> candidates = new ArrayList<>(fleet);
        candidates.sort((a, b) -> Integer.compare(
                spareCapacitySlots(b.getLocalName(), state.getRoutes().get(b.getLocalName())),
                spareCapacitySlots(a.getLocalName(), state.getRoutes().get(a.getLocalName()))));

        for (AID aid : candidates) {
            String name = aid.getLocalName();
            if (tryAssignParcelToAgent(state, name, parcel, null)) return name;
        }
        return null;
    }

    /** Travel cost: from current point → source warehouse (if needed) → customer. */
    private double estimateCrossWarehouseLeg(Point from, Parcel parcel) {
        Point src  = getWarehouseById(parcel.getSourceWarehouseId()).getPos();
        Point dest = parcel.getDestination();
        if (from.equals(src)) return from.distance(dest);
        return from.distance(src) + src.distance(dest);
    }

    /**
     * Repeatedly assign every unassigned parcel to any DA with spare capacity (any warehouse),
     * enforce max onboard load, then expand routes with explicit origin-warehouse pickup legs.
     */
    private void balanceFleetAssignments(RouteState merged) {
        myGui.log("--- Fleet balance: free parcels → DAs with spare capacity ---");
        int totalPlaced = 0;
        for (int round = 0; round < 100; round++) {
            enforceStrictCapacity(merged);

            List<Parcel> free = collectUnassignedParcels(merged);
            if (free.isEmpty()) break;

            free.sort(Comparator
                    .comparingInt((Parcel p) -> warehouseDeficit(p.getSourceWarehouseId()))
                    .reversed());

            int placed = 0;
            for (Parcel p : free) {
                Warehouse srcWh = getWarehouseById(p.getSourceWarehouseId());
                String agent = assignParcelCrossWarehouse(merged, p);
                if (agent == null) continue;
                placed++;
                totalPlaced++;
                Warehouse agentWh = getAgentWarehouse(agent);
                if (agentWh.getId() != srcWh.getId()) {
                    myGui.log("  " + p.getId() + " @ " + srcWh.getName()
                            + " → " + agent + " [" + agentWh.getName()
                            + "] via pickup at (" + srcWh.getX() + "," + srcWh.getY() + ")");
                } else {
                    myGui.log("  " + p.getId() + " → " + agent + " (local spare capacity)");
                }
            }
            if (placed == 0) break;
        }

        List<Parcel> stillFree = collectUnassignedParcels(merged);
        if (!stillFree.isEmpty()) {
            myGui.log("WARNING: " + stillFree.size()
                    + " parcel(s) still unassigned — deploy standby or raise DA capacity.");
        } else if (totalPlaced > 0) {
            myGui.log("Fleet balance assigned " + totalPlaced + " cross-warehouse parcel(s).");
        }

        for (AID aid : fleet) {
            List<Point> route = merged.getRoutes().get(aid.getLocalName());
            if (route != null) consolidateRoutePickups(route);
        }
        applyPhysicalRoutes(merged);
    }

    /** Strip overloaded routes and push parcels to agents with spare capacity (max 5 onboard at once). */
    private void enforceStrictCapacity(RouteState merged) {
        Set<Point> noDynamic = Set.of();
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 500) {
            changed = false;
            List<Parcel> overflow = new ArrayList<>();
            for (AID aid : fleet) {
                String      name  = aid.getLocalName();
                List<Point> route = merged.getRoutes().get(name);
                if (route == null) continue;
                int cap = fleetCapacities.getOrDefault(name, 5);
                Set<Point> depots = depotResetPointsFor(name);
                while (route != null
                        && tabuEngine.peakRouteLoad(route, parcelDirectory, cap, noDynamic, depots) > cap) {
                    Point removed = removeLastParcelStop(route);
                    if (removed == null) break;
                    Parcel p = parcelDirectory.get(removed);
                    if (p != null) overflow.add(p);
                    changed = true;
                }
            }
            for (Parcel p : overflow) {
                if (assignParcelCrossWarehouse(merged, p) != null) {
                    changed = true;
                }
            }
        }
    }


    /** Removes the last customer/parcel stop from the route (not the start depot). */
    private Point removeLastParcelStop(List<Point> route) {
        for (int i = route.size() - 1; i >= 1; i--) {
            Point p = route.get(i);
            if (parcelDirectory.containsKey(p)) {
                route.remove(i);
                return p;
            }
        }
        return null;
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
            }

            balanceFleetAssignments(merged);

        } else {
            applyFleetRepositioning();

            // DISTRIBUTED: isolated per-warehouse Tabu (no cross-region parcel mixing)
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
                }
            }

            balanceFleetAssignments(merged);
        }

        // Any agent not yet in the merged state gets an empty route at their warehouse
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            if (!merged.getRoutes().containsKey(name)) {
                List<Point> solo = new ArrayList<>();
                solo.add(getAgentWarehouse(name).getPos());
                merged.getRoutes().put(name, solo);
                myGui.log("WARNING: " + name + " had no route after plotting — idle at "
                        + getAgentWarehouse(name).getName());
            } else if (countParcelStops(merged.getRoutes().get(name)) == 0) {
                myGui.log("NOTE: " + name + " has no parcels this run ("
                        + Warehouse.displayName(agentWarehouseId.getOrDefault(name, 0))
                        + " may be sharing load with a peer agent).");
            }
        }

        applyPhysicalRoutes(merged);
        previewNodes.clear();
        plannedBaseState = merged;
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
        myGui.log("Routes plotted (includes origin-warehouse pickups). Click '3. Dispatch Fleet'.");
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
                    List<Parcel> batch = new ArrayList<>();
                    while (!dynamicParcelQueue.isEmpty()) {
                        batch.add(dynamicParcelQueue.pollFirst());
                    }
                    if (!batch.isEmpty()) {
                        runDynamicRerouteBatch(batch);
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

    private RouteState buildFleetSnapshotFromRemaining() {
        RouteState snapshot = new RouteState();
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            Point  loc  = currentLocs.getOrDefault(name, getAgentWarehouse(name).getPos());
            snapshot.addAgent(name, loc);
            for (Point p : remainingPaths.getOrDefault(name, List.of())) {
                snapshot.insertNode(name, snapshot.getRoutes().get(name).size(), p);
            }
        }
        return snapshot;
    }

    /**
     * Assign a batch of dynamic parcels in one pass: pick idle/nearest DAs fleet-wide,
     * reuse one warehouse pickup per source WH, then dispatch once.
     */
    private void runDynamicRerouteBatch(List<Parcel> parcels) {
        RouteState snapshot = buildFleetSnapshotFromRemaining();
        Set<String> affected = new HashSet<>();

        myGui.log("--- Dynamic batch: " + parcels.size() + " parcel(s), fleet-wide assignment ---");

        for (Parcel p : parcels) {
            Warehouse srcWh = getWarehouseById(p.getSourceWarehouseId());
            String agent = assignParcelToBestFleetAgent(snapshot, p);
            if (agent == null) {
                myGui.log("WARNING: No DA with capacity for " + p.getId() + " from " + srcWh.getName());
                continue;
            }
            affected.add(agent);
            Warehouse agentWh = getAgentWarehouse(agent);
            if (agentWh.getId() != srcWh.getId()) {
                myGui.log("  " + p.getId() + " → " + agent + " (nearest/idle; pickup at "
                        + srcWh.getName() + ")");
            } else {
                myGui.log("  " + p.getId() + " → " + agent + " (local; shared WH pickup if pending)");
            }
        }

        for (AID aid : fleet) {
            String name = aid.getLocalName();
            List<Point> route = snapshot.getRoutes().get(name);
            if (route != null) consolidateRoutePickups(route);
        }

        for (String name : affected) {
            List<Point> route = snapshot.getRoutes().get(name);
            if (route != null && plannedBaseState != null) {
                plannedBaseState.getRoutes().put(name, new ArrayList<>(route));
            }
        }

        dispatchRoutesForDynamic(snapshot, affected);
        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
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
        agentOriginWarehouseId.put(name, warehouseId);
        warehouseFleet.get(warehouseId).add(new AID(name, AID.ISLOCALNAME));

        myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths,
                fleetCapacities, previewNodes, warehouses);
    }


    /**
     * Translate math-space routes into physical stop sequences and send to DAs.
     * Virtual-depot (50,50) nodes are translated to the agent's actual warehouse.
     * Dynamic parcels get a warehouse pickup inserted before their destination.
     */
    private int countParcelStops(List<Point> route) {
        if (route == null) return 0;
        int n = 0;
        for (Point p : route) {
            if (parcelDirectory.containsKey(p)) n++;
        }
        return n;
    }

    /**
     * Turn solver route (depot + optional embedded WH pickups + customer nodes) into
     * the stop sequence the DA actually drives, including origin-warehouse collection legs.
     */
    private List<Point> buildPhysicalRoute(String agentName, List<Point> mathNodes) {
        if (mathNodes == null || mathNodes.size() < 2) return new ArrayList<>();

        Warehouse agentWh    = getAgentWarehouse(agentName);
        Point     agentWhPos = agentWh.getPos();

        List<Point> physicalStops     = new ArrayList<>();
        Set<Point>  visitedWarehouses = new HashSet<>();

        for (int i = 1; i < mathNodes.size(); i++) {
            Point p = mathNodes.get(i);

            if (p.equals(virtualDepot) && !agentWhPos.equals(virtualDepot)) {
                p = new Point(agentWhPos);
            }

            if (p.equals(agentWhPos)) {
                visitedWarehouses.add(agentWhPos);
                appendStopIfNew(physicalStops, agentWhPos);
                continue;
            }

            if (isWarehousePosition(p)) {
                appendStopIfNew(physicalStops, p);
                visitedWarehouses.add(p);
                continue;
            }

            Parcel info = parcelDirectory.get(p);
            if (info != null) {
                Point srcWhPos = getWarehouseById(info.getSourceWarehouseId()).getPos();
                if (!visitedWarehouses.contains(srcWhPos)
                        && !pickupScheduledBefore(mathNodes, i, srcWhPos)) {
                    appendStopIfNew(physicalStops, srcWhPos);
                    visitedWarehouses.add(srcWhPos);
                }
            }
            physicalStops.add(p);
        }

        if (!physicalStops.isEmpty()
                && !physicalStops.get(physicalStops.size() - 1).equals(agentWhPos)) {
            physicalStops.add(new Point(agentWhPos));
        }
        return physicalStops;
    }

    private static void appendStopIfNew(List<Point> stops, Point p) {
        if (stops.isEmpty() || !stops.get(stops.size() - 1).equals(p)) {
            stops.add(new Point(p));
        }
    }

    private boolean pickupScheduledBefore(List<Point> route, int parcelIndex, Point srcWhPos) {
        for (int j = 1; j < parcelIndex && j < route.size(); j++) {
            if (route.get(j).equals(srcWhPos)) return true;
        }
        return false;
    }

    /** Stops from a live snapshot (index 0 = current GPS); already includes WH pickups from insertion. */
    private List<Point> physicalStopsFromSnapshot(String agentName, List<Point> snapshotRoute) {
        List<Point> stops = new ArrayList<>();
        if (snapshotRoute == null || snapshotRoute.size() < 2) return stops;
        for (int i = 1; i < snapshotRoute.size(); i++) {
            stops.add(new Point(snapshotRoute.get(i)));
        }
        return finalizePhysicalStops(agentName, stops);
    }

    private List<Point> finalizePhysicalStops(String agentName, List<Point> stops) {
        consolidateRoutePickups(stops);
        Point home = getAgentWarehouse(agentName).getPos();
        if (!stops.isEmpty() && !stops.get(stops.size() - 1).equals(home)) {
            stops.add(new Point(home));
        }
        return stops;
    }

    private void sendRouteToAgent(String agentName, List<Point> physicalStops) {
        // CRITICAL: Must be <= 1. An idle truck has a list size of 1 (just the warehouse).
        // If we don't block them here, they get added to activeDrivingAgents and freeze the day!
        if (physicalStops == null || physicalStops.size() <= 1) return;

        StringBuilder sb = new StringBuilder(PREFIX_ROUTE + "5:5|");
        for (int i = 0; i < physicalStops.size(); i++) {
            Point p = physicalStops.get(i);
            sb.append(p.x).append(":").append(p.y);
            if (i < physicalStops.size() - 1) sb.append(",");
        }
        ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
        m.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        m.setConversationId(CID_ROUTE);
        m.setContent(sb.toString());
        send(m);

        activeDrivingAgents.add(agentName);
        remainingPaths.put(agentName, new ArrayList<>(physicalStops));
        myGui.disableSummary();
    }

    /**
     * Dynamic reroute only — update DAs that received new parcels. Do not rebuild routes for
     * the rest of the fleet (avoids sending everyone back to warehouses for re-pickup).
     */
    private void dispatchRoutesForDynamic(RouteState snapshot, Set<String> affectedAgents) {
        if (affectedAgents.isEmpty()) return;
        for (String name : affectedAgents) {
            List<Point> snapRoute = snapshot.getRoutes().get(name);
            if (snapRoute == null || snapRoute.size() < 2) {
                myGui.log(name + " idle after dynamic assign — no route sent.");
                continue;
            }
            List<Point> physicalStops = physicalStopsFromSnapshot(name, snapRoute);
            sendRouteToAgent(name, physicalStops);
            myGui.log("Updated route for " + name + " only (" + physicalStops.size() + " stops).");
        }
    }

    private void applyPhysicalRoutes(RouteState merged) {
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            List<Point> math   = merged.getRoutes().get(name);
            List<Point> physical = buildPhysicalRoute(name, math);
            initialPlannedRoutes.put(name, new ArrayList<>(physical));
            remainingPaths.put(name, new ArrayList<>(physical));
        }
    }

    /** Initial fleet dispatch from math-space planned routes (Plot Routes → Dispatch Fleet). */
    private void dispatchRoutes(RouteState state, Parcel dynamicParcel) {
        for (AID aid : fleet) {
            String      name      = aid.getLocalName();
            List<Point> mathNodes = state.getRoutes().get(name);
            if (mathNodes == null) {
                myGui.log("WARNING: No route in plan for " + name + " — skipped dispatch.");
                continue;
            }
            if (mathNodes.size() < 2) {
                Point home = getAgentWarehouse(name).getPos();
                if (repositionLegFrom.containsKey(name)) {
                    List<Point> relocOnly = prependRepositioningLeg(name, List.of(home));
                    sendRouteToAgent(name, relocOnly);
                    myGui.log(name + " repositioning to " + getAgentWarehouse(name).getName()
                            + " (no parcels on this run).");
                } else {
                    remainingPaths.put(name, new ArrayList<>());
                    myGui.log(name + " idle (no deliveries assigned) — stays at "
                            + getAgentWarehouse(name).getName());
                }
                continue;
            }

            List<Point> physicalStops = buildPhysicalRoute(name, mathNodes);
            physicalStops = prependRepositioningLeg(name, physicalStops);
            sendRouteToAgent(name, physicalStops);
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
    public Map<String, Integer>      getAgentOriginWarehouseIds() { return agentOriginWarehouseId; }
}
