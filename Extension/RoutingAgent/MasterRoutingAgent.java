package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.*;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class MasterRoutingAgent extends Agent {

    // Algorithm Engines (You will build the logic inside these classes next)
    private GreedyEngine greedyEngine = new GreedyEngine();
    private ALNSEngine alnsEngine = new ALNSEngine();

    protected void setup() {
        System.out.println("MRA Online. Awaiting dynamic injections.");
        // Setup GUI here...
    }

    // --- TRIGGERED BY GUI WHEN USER CLICKS "ADD PARCEL" ---
    public void injectDynamicParcel(Parcel newParcel) {
        System.out.println("\n*** MRA: DYNAMIC PARCEL INJECTED (" + newParcel.getId() + ") ***");

        // Step 1: Freeze all DAs
        ACLMessage freezeMsg = new ACLMessage(ACLMessage.REQUEST);
        freezeMsg.setContent("FREEZE");
        freezeMsg.addReceiver(new AID("DA1", AID.ISLOCALNAME));
        freezeMsg.addReceiver(new AID("DA2", AID.ISLOCALNAME));
        // Add all active agents...
        send(freezeMsg);

        System.out.println("MRA: Waiting for fleet status reports...");

        // In a full implementation, use a ParallelBehaviour or WakerBehaviour to wait
        // for the INFORM replies from the DAs before executing Step 2.

        // --- SIMULATED OPTIMIZATION PIPELINE ---
        runOptimizationPipeline(newParcel);
    }

    private void runOptimizationPipeline(Parcel newParcel) {
        System.out.println("MRA: Running Greedy Insertion (Phase 1)...");
        // RouteState greedyState = greedyEngine.insertNewParcel(newParcel, currentFleetState);

        System.out.println("MRA: Running ALNS Deep Clean (Phase 2) for 4.5 seconds...");
        // RouteState finalState = alnsEngine.optimize(greedyState, 4500);

        System.out.println("MRA: Optimization Complete. Dispatching new routes.");
        dispatchRoutes();
    }

    private void dispatchRoutes() {
        // Send the new optimized routes back to the specific DAs
        ACLMessage dispatchMsg = new ACLMessage(ACLMessage.PROPOSE);
        dispatchMsg.setContent("NEW_ROUTE_DATA");
        dispatchMsg.addReceiver(new AID("DA1", AID.ISLOCALNAME));
        dispatchMsg.addReceiver(new AID("DA2", AID.ISLOCALNAME));
        send(dispatchMsg);
    }
}
