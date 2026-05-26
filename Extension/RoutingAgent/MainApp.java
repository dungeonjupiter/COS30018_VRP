package RoutingAgent.Extension.RoutingAgent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class MainApp {
    public static void main(String[] args) {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "true");

        ContainerController cc = rt.createMainContainer(p);

        try {
            System.out.println("--- Booting Multi-Agent System Engine ---");

            // Spawn ONLY the Master Routing Agent.
            // Arguments: [0] = Empty Fleet (""), [1] = Depot Location ("50,50")
            Object[] mraArgs = new Object[]{"", "50,50"};
            AgentController mra = cc.createNewAgent("MRA", "RoutingAgent.Extension.RoutingAgent.MasterRoutingAgent", mraArgs);
            mra.start();

            System.out.println("Engine Booted. Awaiting Manual Setup via GUI...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}