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
        String port = System.getProperty("jade.port", "1099");
        p.setParameter(Profile.MAIN_PORT, port);

        System.out.println("--- Booting Multi-Agent System Engine ---");

        ContainerController cc = rt.createMainContainer(p);
        if (cc == null) {
            System.err.println("JADE failed to start (container is null).");
            System.err.println("Port " + port + " is likely already in use by another JADE/Java process.");
            System.err.println("Fix: stop the old run (red Stop in IntelliJ), or end java.exe on that port, then run again.");
            System.err.println("Optional: Run with VM option -Djade.port=1100 to use a different port.");
            return;
        }

        try {

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