package RoutingAgent.vrp.io;

import RoutingAgent.vrp.model.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class MapLoader {

    public static WorldState load(String filePath) throws IOException {
        WorldState state = new WorldState();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNum = 0;

            while ((line = br.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] t = line.split("\\s+");
                try {
                    switch (t[0].toUpperCase()) {
                        case "WAREHOUSE" -> {
                            double x = Double.parseDouble(t[1]);
                            double y = Double.parseDouble(t[2]);
                            state.warehouse = new Warehouse(x, y);
                        }
                        case "CUSTOMER" -> {
                            int id = Integer.parseInt(t[1]);
                            double x = Double.parseDouble(t[2]);
                            double y = Double.parseDouble(t[3]);
                            state.customers.add(new CustomerNode(id, x, y));
                        }
                        case "PARCEL" -> {
                            // Format: PARCEL <id> DEST <customerId>
                            int id = Integer.parseInt(t[1]);
                            int destId = Integer.parseInt(t[3]);
                            CustomerNode dest = state.findCustomer(destId);
                            if (dest == null)
                                throw new IOException("Line " + lineNum + ": Parcel " + id + " references unknown customer " + destId);
                            state.parcels.add(new Parcel(id, dest));
                        }
                        case "AGENT" -> {
                            // Format: AGENT <id> CAPACITY <cap>
                            int id = Integer.parseInt(t[1]);
                            int cap = Integer.parseInt(t[3]);
                            if (state.warehouse == null)
                                throw new IOException("WAREHOUSE must be defined before AGENT entries");
                            state.agents.add(new AgentState(id, cap, state.warehouse));
                        }
                        default -> System.err.println("MapLoader: unknown keyword '" + t[0] + "' on line " + lineNum + " — skipped");
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Line " + lineNum + ": invalid number format — " + line);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new IOException("Line " + lineNum + ": missing fields — " + line);
                }
            }
        }

        if (state.warehouse == null)
            throw new IOException("Map file is missing a WAREHOUSE entry");

        return state;
    }
}
