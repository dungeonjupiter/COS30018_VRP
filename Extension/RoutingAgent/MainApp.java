package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.*;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class MainApp {
    public static void main(String[] args) {
        // 1. Get the JADE runtime
        Runtime rt = Runtime.instance();

        // 2. Create a default profile
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "true"); // Shows the JADE RMA interface

        // 3. Create the Main Container
        ContainerController cc = rt.createMainContainer(p);

        try {
            // 4. Spawn the Master Routing Agent
            AgentController mra = cc.createNewAgent("MRA", "RoutingAgent.Extension.RoutingAgent.MasterRoutingAgent", null);
            mra.start();

            // 5. Spawn two test Delivery Agents
            AgentController da1 = cc.createNewAgent("DA1", "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", null);
            da1.start();

            AgentController da2 = cc.createNewAgent("DA2", "RoutingAgent.Extension.RoutingAgent.DeliveryAgent", null);
            da2.start();

            System.out.println("System Booted. Ready for dynamic testing.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}