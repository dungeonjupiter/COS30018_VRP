package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.*;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class DeliveryAgent extends Agent {
    private int capacity = 5; // Default capacity
    private Point currentLocation = new Point(50, 50); // Starts at warehouse
    private List<Point> currentRoute = new ArrayList<>();
    private MovementBehaviour moveSim;

    protected void setup() {
        System.out.println(getLocalName() + " logged in. Ready for dispatch.");

        // Listen for MRA commands
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.REQUEST && msg.getContent().equals("FREEZE")) {
                        handleFreezeCommand(msg);
                    } else if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        // Receiving a brand new route from the MRA
                        handleNewRoute(msg);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void handleFreezeCommand(ACLMessage freezeMsg) {
        System.out.println(getLocalName() + ": FREEZE received. Halting movement.");
        if (moveSim != null) {
            moveSim.stop(); // Stop driving!
        }

        // Reply to MRA with exact current status
        ACLMessage reply = freezeMsg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        // Format: X,Y,RemainingCapacity
        reply.setContent(currentLocation.x + "," + currentLocation.y + "," + capacity);
        send(reply);
    }

    private void handleNewRoute(ACLMessage routeMsg) {
        System.out.println(getLocalName() + ": New route received. Resuming delivery.");
        // In reality, you'd deserialize the route object here.
        // For testing, we just restart the movement engine.
        moveSim = new MovementBehaviour(this, 1000);
        addBehaviour(moveSim);
    }

    // Simulates driving step-by-step
    private class MovementBehaviour extends TickerBehaviour {
        public MovementBehaviour(Agent a, long period) { super(a, period); }
        protected void onTick() {
            // Logic to move 'currentLocation' towards the next Point in 'currentRoute'
            System.out.println(getLocalName() + " is driving... Current Loc: [" + currentLocation.x + "," + currentLocation.y + "]");
        }
    }
}