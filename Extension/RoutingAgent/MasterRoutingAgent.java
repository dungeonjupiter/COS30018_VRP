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
    private static final String PREFIX_ROUTE = "ROUTE:";

    private MainWindow myGui;
    private final TabuRoutingEngine tabuEngine = new TabuRoutingEngine();
    private final List<AID> fleet = new ArrayList<>();
    private final Point depot = new Point(50, 50);

    private final Map<String, Integer> fleetCapacities = new HashMap<>();
    public final Map<Point, Parcel> parcelDirectory = new HashMap<>();

    // Live State
    private final Map<String, Point> currentLocs = new HashMap<>();
    private final Map<String, List<Point>> actualDrivenRoutes = new HashMap<>();
    private final Map<String, List<Point>> remainingPaths = new HashMap<>();
    private final Set<Point> globalInVanParcels = new HashSet<>();
    private final Set<String> activeDrivingAgents = new HashSet<>();

    private final Map<String, List<Point>> initialPlannedRoutes = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("MRA online.");
        myGui = new MainWindow(this);
        myGui.setVisible(true);

        // Dummy Setup for testing
        fleet.add(new AID("DA1", AID.ISLOCALNAME));
        fleetCapacities.put("DA1", 5);
        activeDrivingAgents.add("DA1");
        spawnInitialAgent("DA1");

        // Listen for GPS Pings
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_TRACKING);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    processGpsPing(msg.getSender().getLocalName(), msg.getContent());
                } else { block(); }
            }
        });

        // Listen for Route Completion
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate tpl = MessageTemplate.MatchConversationId(CID_DONE);
                ACLMessage msg = receive(tpl);
                if (msg != null) {
                    activeDrivingAgents.remove(msg.getSender().getLocalName());
                    if (activeDrivingAgents.isEmpty()) {
                        System.out.println("MRA: All agents returned.");
                        myGui.setAllAgentsIdle();
                    }
                } else { block(); }
            }
        });
    }

    public void injectDynamicParcel(Parcel newParcel) {
        parcelDirectory.put(newParcel.getDestination(), newParcel);
        System.out.println("MRA: Dynamic Parcel Injected at " + newParcel.getDestination() + ". Launching Ghost Rerouting.");

        // 1. Build Snapshot & N+1 Buffer Lock
        RouteState snapshot = new RouteState();
        Map<String, Integer> lockedPrefixes = new HashMap<>();

        for (AID aid : fleet) {
            String name = aid.getLocalName();
            Point loc = currentLocs.getOrDefault(name, depot);
            snapshot.addAgent(name, loc);

            List<Point> remaining = remainingPaths.getOrDefault(name, new ArrayList<>());
            int lockedCount = 0;

            if (!remaining.isEmpty()) {
                snapshot.insertNode(name, 1, remaining.get(0));
                lockedCount = 1; // Always lock the immediate next node

                // The N+1 Velocity Buffer: If DA is very close to node 0, lock node 1 as well!
                if (loc.distance(remaining.get(0)) < 15.0 && remaining.size() > 1) {
                    snapshot.insertNode(name, 2, remaining.get(1));
                    lockedCount = 2;
                }

                // Add the rest of the nodes to be optimized
                for (int i = lockedCount; i < remaining.size(); i++) {
                    snapshot.insertNode(name, i + 1, remaining.get(i));
                }
            }
            lockedPrefixes.put(name, lockedCount);
        }

        // 2. Asynchronous Optimization (NO FREEZE)
        CompletableFuture.supplyAsync(() -> tabuEngine.optimize(snapshot, newParcel, fleetCapacities, parcelDirectory, lockedPrefixes))
                .thenAccept(optimizedState -> {
                    // Return to main JADE context to dispatch
                    addBehaviour(new jade.core.behaviours.OneShotBehaviour() {
                        @Override
                        public void action() {
                            dispatchGhostRoute(optimizedState, newParcel);
                        }
                    });
                });
    }

    private void dispatchGhostRoute(RouteState state, Parcel newParcel) {
        for (AID aid : fleet) {
            String name = aid.getLocalName();
            List<Point> mathRoute = state.getRoutes().get(name);
            if (mathRoute == null || mathRoute.size() < 2) continue;

            List<Point> physicalRoute = new ArrayList<>();
            boolean hasVisitedDepotThisTrip = false;

            // Loop through the optimized mathematical route
            for (int i = 1; i < mathRoute.size(); i++) {
                Point p = mathRoute.get(i);

                if (p.equals(depot)) {
                    hasVisitedDepotThisTrip = true;
                    physicalRoute.add(p);
                    continue;
                }

                // The Depot Batching Check
                boolean isBrandNewParcel = p.equals(newParcel.getDestination());
                if (isBrandNewParcel && !hasVisitedDepotThisTrip && !globalInVanParcels.contains(p)) {
                    physicalRoute.add(new Point(depot));
                    hasVisitedDepotThisTrip = true; // Flips the flag for batched pickups!
                }

                physicalRoute.add(p);
                if (hasVisitedDepotThisTrip) globalInVanParcels.add(p); // Mark as picked up
            }

            // Cleanup redundancies
            if (!physicalRoute.isEmpty() && !physicalRoute.get(physicalRoute.size() - 1).equals(depot)) {
                physicalRoute.add(new Point(depot));
            }

            StringBuilder coords = new StringBuilder();
            for (int i = 0; i < physicalRoute.size(); i++) {
                if (i > 0) coords.append(',');
                coords.append(physicalRoute.get(i).x).append(':').append(physicalRoute.get(i).y);
            }

            ACLMessage m = new ACLMessage(ACLMessage.PROPOSE);
            m.addReceiver(aid);
            m.setConversationId(CID_ROUTE);
            m.setContent(PREFIX_ROUTE + "5:5|" + coords);
            send(m);
        }
    }

    private void processGpsPing(String agentName, String content) {
        try {
            String[] parts = content.split("\\|");
            String[] locCoords = parts[0].split(",");
            Point currentLoc = new Point(Integer.parseInt(locCoords[0]), Integer.parseInt(locCoords[1]));

            currentLocs.put(agentName, currentLoc);
            actualDrivenRoutes.computeIfAbsent(agentName, k -> new ArrayList<>()).add(currentLoc);

            List<Point> remStops = new ArrayList<>();
            if (parts.length > 1 && !parts[1].isBlank()) {
                for (String stop : parts[1].split(";")) {
                    String[] sCoords = stop.split(",");
                    remStops.add(new Point(Integer.parseInt(sCoords[0]), Integer.parseInt(sCoords[1])));
                }
            }
            remainingPaths.put(agentName, remStops);
            myGui.updateMap(currentLocs, actualDrivenRoutes, remainingPaths);
        } catch (Exception e) {}
    }

    private void spawnInitialAgent(String name) {
        try {
            Object[] args = new Object[]{"50,50,5"};
            getContainerController().createNewAgent(name, "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", args).start();
        } catch (Exception e) {}
    }
    public Map<String, List<Point>> getInitialPlannedRoutes() { return initialPlannedRoutes; }
    public Map<String, List<Point>> getActualDrivenRoutes() { return actualDrivenRoutes; }
}