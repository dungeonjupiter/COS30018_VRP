package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.ALNSEngine;
import RoutingAgent.Extension.Solver.GreedyEngine;
import RoutingAgent.Extension.Solver.Parcel;
import RoutingAgent.Extension.Solver.RouteState;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MasterRoutingAgent extends Agent {

    public static final String CID_FREEZE = "vrp-freeze";
    public static final String CID_ROUTE = "vrp-route";
    public static final String FREEZE_CONTENT = "FREEZE";
    private static final String PREFIX_STATUS = "STATUS:";
    private static final String PREFIX_ROUTE = "ROUTE:";

    private MRAGui myGui;
    private MapTrackerGui trackerGui;
    private final GreedyEngine greedyEngine = new GreedyEngine();
    private final ALNSEngine alnsEngine = new ALNSEngine();
    private final List<AID> fleet = new ArrayList<>();
    private Point depot = new Point(50, 50);
    private long freezeTimeoutMs = 8000L;
    private long alnsTimeMs = 1500L;

    // Capacity & Tracking Variables
    private int expectedInitialAgents = 0;
    private int capacityReportedCount = 0;
    private int totalSystemDemand = 0;
    private List<Parcel> pendingInitialParcels = new ArrayList<>();
    private Map<String, Integer> fleetCapacities = new HashMap<>();
    public final Map<Point, Parcel> parcelDirectory = new HashMap<>();
    private int backupCounter = 1;

    // --- AUTONOMOUS & PHYSICAL TRACKING ---
    private final Set<String> activeDrivingAgents = new HashSet<>();
    private boolean summaryHasBeenShown = false;
    private final Set<Point> inVanParcels = new HashSet<>();
    private final Set<Point> dynamicTargetsThisRound = new HashSet<>();

    // Flight Recorder Memory
    private final Map<String, List<Point>> initialPlannedRoutes = new HashMap<>();
    private final Map<String, List<Point>> actualDrivenRoutes = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("MRA online. Depot (" + depot.x + "," + depot.y + ").");

        myGui = new MRAGui(this);
        myGui.display();

        // --- Listen for 'End of Shift' Pings ---
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId("vrp-done");
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    activeDrivingAgents.remove(msg.getSender().getLocalName());

                    if (activeDrivingAgents.isEmpty() && !summaryHasBeenShown) {
                        System.out.println("\n*** MRA: ALL AGENTS HAVE RETURNED TO DEPOT! Launching End-of-Day Summary! ***");
                        summaryHasBeenShown = true;
                        RouteSummaryGui summaryGui = new RouteSummaryGui(initialPlannedRoutes, actualDrivenRoutes);
                        summaryGui.display();
                    }
                } else { block(); }
            }
        });

        // Listen for Capacity inputs from DeliveryAgent GUIs
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId("vrp-capacity");
                ACLMessage msg = receive(mt);
                if (msg != null) {
                    String content = msg.getContent();
                    if (content != null && content.startsWith("CAPACITY:")) {
                        int cap = Integer.parseInt(content.substring(9).trim());
                        fleetCapacities.put(msg.getSender().getLocalName(), cap);
                        capacityReportedCount++;
                        if (capacityReportedCount == expectedInitialAgents) checkCapacitiesAndRunSolver();
                    }
                } else { block(); }
            }
        });

        // Listen for GPS Pings
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId("vrp-tracking");
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    processGpsPing(msg.getSender().getLocalName(), msg.getContent());
                } else { block(); }
            }
        });
    }

    public void spawnInitialAgent(String name, int startX, int startY) {
        try {
            Object[] args = new Object[]{startX + "," + startY};
            jade.wrapper.AgentController ac = getContainerController().createNewAgent(name, "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", args);
            ac.start();
            fleet.add(new AID(name, AID.ISLOCALNAME));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void initializeSystem(final int numCustomers, final int numAgents, final String filePath) {
        System.out.println("\n--- MRA: PHASE 1A - GENERATING MAP & SPAWNING AGENTS ---");
        pendingInitialParcels.clear();
        fleetCapacities.clear();
        parcelDirectory.clear();
        initialPlannedRoutes.clear();
        actualDrivenRoutes.clear();
        capacityReportedCount = 0;
        totalSystemDemand = 0;
        expectedInitialAgents = numAgents;

        if (filePath.equals("RANDOM")) {
            java.util.Random rand = new java.util.Random();
            for (int i = 1; i <= numCustomers; i++) {
                Parcel p = new Parcel("P" + i, rand.nextInt(100), rand.nextInt(100), 1);
                pendingInitialParcels.add(p);
                parcelDirectory.put(p.getDestination(), p);
                totalSystemDemand += p.getDemand();
            }
        } else {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(new java.io.File(filePath).toPath());
                int pCount = 1;
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        int demand = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : 1;
                        Parcel p = new Parcel("F" + pCount++, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), demand);
                        pendingInitialParcels.add(p);
                        parcelDirectory.put(p.getDestination(), p);
                        totalSystemDemand += p.getDemand();
                    }
                }
            } catch (Exception e) { System.err.println("MRA: Failed to parse file."); }
        }

        for (int i = 1; i <= numAgents; i++) spawnInitialAgent("DA" + i, depot.x, depot.y);
    }

    private void checkCapacitiesAndRunSolver() {
        int totalCap = 0;
        for (int cap : fleetCapacities.values()) totalCap += cap;

        if (totalCap < totalSystemDemand) {
            expectedInitialAgents++;
            spawnInitialAgent("DA_Backup_" + backupCounter++, depot.x, depot.y);
        } else {
            runInitialOptimizationPipeline();
        }
    }

    private void runInitialOptimizationPipeline() {
        addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
            @Override
            public void action() {
                RouteState baseState = new RouteState();
                for (AID aid : fleet) baseState.addAgent(aid.getLocalName(), depot);

                for (Parcel p : pendingInitialParcels) {
                    baseState = greedyEngine.insertNewParcel(p, baseState, fleetCapacities, parcelDirectory);
                }

                System.out.println("MRA: Running ALNS engine...");
                RouteState optimizedState = alnsEngine.optimize(baseState, 3000, fleetCapacities, parcelDirectory);

                // Record Initial Plan
                for (Map.Entry<String, List<Point>> entry : optimizedState.getRoutes().entrySet()) {
                    initialPlannedRoutes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }

                dispatch(optimizedState, new HashMap<>());

                myGui.unlockPhase2();
                if (trackerGui == null) trackerGui = new MapTrackerGui();
                trackerGui.display();
            }
        });
    }

    public void injectDynamicParcel(Parcel newParcel) {
        if (fleet.isEmpty()) return;
        parcelDirectory.put(newParcel.getDestination(), newParcel);

        dynamicTargetsThisRound.clear();
        dynamicTargetsThisRound.add(newParcel.getDestination()); // Explicitly flag this as a brand new box!

        addBehaviour(new FreezeOptimizeDispatchBehaviour(newParcel));
    }

    private final class FreezeOptimizeDispatchBehaviour extends Behaviour {
        private final Parcel parcel;
        private int phase = 0;
        private final Map<String, AgentStatus> pending = new LinkedHashMap<>();
        private long waitUntil = 0L;
        private RouteState optimized;

        FreezeOptimizeDispatchBehaviour(Parcel parcel) { this.parcel = parcel; }

        @Override
        public void action() {
            switch (phase) {
                case 0 -> sendFreeze();
                case 1 -> collectReplies();
                case 2 -> runSolversAndDispatch();
            }
        }

        private void sendFreeze() {
            pending.clear();
            for (AID aid : fleet) pending.put(aid.getLocalName(), null);
            ACLMessage m = new ACLMessage(ACLMessage.REQUEST);
            m.setContent(FREEZE_CONTENT);
            m.setConversationId(CID_FREEZE);
            for (AID aid : fleet) m.addReceiver(aid);
            send(m);
            waitUntil = System.currentTimeMillis() + freezeTimeoutMs;
            phase = 1;
        }

        private void collectReplies() {
            MessageTemplate tpl = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(CID_FREEZE));
            ACLMessage msg;
            while ((msg = receive(tpl)) != null) {
                String name = msg.getSender().getLocalName();
                if (pending.containsKey(name)) {
                    AgentStatus status = parseStatus(msg.getContent());
                    pending.put(name, status);
                    fleetCapacities.put(name, status.maxCapacity);
                }
            }
            if (pending.values().stream().allMatch(s -> s != null) || System.currentTimeMillis() > waitUntil) phase = 2;
            else block();
        }

        private void runSolversAndDispatch() {
            RouteState state = mergeFleetState(pending);
            RouteState greedy = greedyEngine.insertNewParcel(parcel, state, fleetCapacities, parcelDirectory);
            optimized = alnsEngine.optimize(greedy, alnsTimeMs, fleetCapacities, parcelDirectory);
            dispatch(optimized, pending);
            phase = 3;
        }
        @Override public boolean done() { return phase >= 3; }
    }

    private void dispatch(RouteState state, Map<String, AgentStatus> lastKnown) {
        summaryHasBeenShown = false;

        for (AID aid : fleet) {
            String name = aid.getLocalName();
            activeDrivingAgents.add(name);

            List<Point> pts = state.getRoutes().get(name);
            if (pts == null || pts.size() < 2) continue;

            List<Point> stops = new ArrayList<>();
            Point currentLoc = pts.get(0);
            stops.add(currentLoc);

            // INTELLIGENT PHYSICAL DETOUR LOGIC
            // If they are currently at the depot, they are already loaded!
            boolean hasVisitedDepotThisTrip = currentLoc.equals(depot);

            for (int i = 1; i < pts.size(); i++) {
                Point p = pts.get(i);

                if (p.equals(depot)) {
                    hasVisitedDepotThisTrip = true;
                    stops.add(p);
                    continue;
                }

                // If it is NOT in the van, OR it was just injected dynamically, it's a NEW box.
                boolean isNewParcel = !inVanParcels.contains(p) || dynamicTargetsThisRound.contains(p);

                // If it's a new box, and we haven't gone to the warehouse yet, FORCE a detour right now!
                if (isNewParcel && !hasVisitedDepotThisTrip) {
                    stops.add(new Point(depot));
                    hasVisitedDepotThisTrip = true; // Box is now loaded
                }

                stops.add(p);
            }

            // Force route to end at Warehouse
            if (!stops.isEmpty() && !stops.get(stops.size() - 1).equals(depot)) {
                stops.add(new Point(depot));
            }

            // Remove consecutive duplicates
            for (int i = stops.size() - 1; i > 0; i--) {
                if (stops.get(i).equals(stops.get(i - 1))) {
                    stops.remove(i);
                }
            }

            StringBuilder coords = new StringBuilder();
            StringBuilder routeLog = new StringBuilder();

            for (int i = 1; i < stops.size(); i++) {
                Point p = stops.get(i);
                if (i > 1) coords.append(',');
                coords.append(p.x).append(':').append(p.y);

                if (p.equals(depot)) {
                    routeLog.append("Warehouse(Pickup) ");
                } else {
                    Parcel parcel = parcelDirectory.get(p);
                    routeLog.append(parcel != null ? parcel.getId() : ("Unk(" + p.x + ")")).append(" ");
                }
            }

            AgentStatus prev = lastKnown.get(name);
            int free = prev != null ? prev.freeCapacity : fleetCapacities.getOrDefault(name, 5);
            int max = prev != null ? prev.maxCapacity : fleetCapacities.getOrDefault(name, 5);

            String payload = PREFIX_ROUTE + free + ":" + max + "|" + coords;

            ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
            m.addReceiver(aid);
            m.setConversationId(CID_ROUTE);
            m.setContent(payload);
            send(m);

            System.out.println("MRA -> " + name + " Route Assigned: [ " + routeLog.toString().trim() + " ]");
        }
    }

    private static class AgentStatus {
        final Point location; final int freeCapacity; final int maxCapacity; final List<Point> tailStops;
        AgentStatus(Point loc, int free, int max, List<Point> tail) { location = loc; freeCapacity = free; maxCapacity = max; tailStops = tail; }
    }

    private static AgentStatus parseStatus(String content) {
        String rest = content.substring(PREFIX_STATUS.length());
        String[] headTail = rest.split(":", 5);
        int x = Integer.parseInt(headTail[0].trim()), y = Integer.parseInt(headTail[1].trim());
        int free = Integer.parseInt(headTail[2].trim()), max = Integer.parseInt(headTail[3].trim());
        List<Point> stops = new ArrayList<>();
        if (headTail.length == 5 && !headTail[4].isBlank()) {
            for (String token : headTail[4].split(",")) {
                String[] xy = token.split(":");
                stops.add(new Point(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())));
            }
        }
        return new AgentStatus(new Point(x, y), free, max, stops);
    }

    private RouteState mergeFleetState(Map<String, AgentStatus> byName) {
        RouteState state = new RouteState();
        inVanParcels.clear();

        for (AID aid : fleet) {
            String name = aid.getLocalName();
            AgentStatus st = byName.get(name);
            state.addAgent(name, st != null ? st.location : new Point(depot));

            if (st != null) {
                // Determine what is ACTUALLY physically in the van right now
                boolean passedDepot = st.location.equals(depot);
                for (Point p : st.tailStops) {
                    if (p.equals(depot)) {
                        passedDepot = true;
                    } else if (!passedDepot) {
                        // Any customer before the depot detour is physically in the van!
                        inVanParcels.add(p);
                    }
                    state.insertNode(name, state.getRoutes().get(name).size(), new Point(p));
                }
            }
        }
        return state;
    }

    private void processGpsPing(String agentName, String content) {
        if (trackerGui == null || !trackerGui.isVisible()) return;
        try {
            String[] parts = content.split("\\|");
            String[] locCoords = parts[0].split(",");
            Point currentLoc = new Point(Integer.parseInt(locCoords[0]), Integer.parseInt(locCoords[1]));

            List<Point> history = actualDrivenRoutes.computeIfAbsent(agentName, k -> new ArrayList<>());
            if (history.isEmpty() || !history.get(history.size() - 1).equals(currentLoc)) {
                history.add(currentLoc);
            }

            List<Point> remainingStops = new ArrayList<>();
            if (parts.length > 1 && !parts[1].isBlank()) {
                for (String stop : parts[1].split(";")) {
                    String[] sCoords = stop.split(",");
                    remainingStops.add(new Point(Integer.parseInt(sCoords[0]), Integer.parseInt(sCoords[1])));
                }
            }
            trackerGui.updateAgent(agentName, currentLoc, remainingStops);
        } catch (Exception e) {}
    }

    public Map<String, List<Point>> getInitialPlannedRoutes() { return initialPlannedRoutes; }
    public Map<String, List<Point>> getActualDrivenRoutes() { return actualDrivenRoutes; }

    public void spawnDynamicAgent(String name, int startX, int startY, int capacity) {
        try {
            Object[] args = new Object[]{startX + "," + startY + "," + capacity};
            jade.wrapper.AgentController ac = getContainerController().createNewAgent(name, "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", args);
            ac.start();
            fleet.add(new AID(name, AID.ISLOCALNAME));
            fleetCapacities.put(name, capacity);
        } catch (Exception e) {}
    }
}