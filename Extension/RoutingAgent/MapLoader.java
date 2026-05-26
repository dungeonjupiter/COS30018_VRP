package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapLoader {
    public static class ParsedData {
        public Point warehouse = new Point(50, 50);
        public List<Parcel> parcels = new ArrayList<>();
    }

    public static ParsedData load(String filePath) throws IOException {
        ParsedData data = new ParsedData();
        Map<Integer, Point> customerMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] t = line.split("\\s+");
                try {
                    switch (t[0].toUpperCase()) {
                        case "WAREHOUSE":
                            data.warehouse = new Point((int) Double.parseDouble(t[1]), (int) Double.parseDouble(t[2]));
                            break;
                        case "CUSTOMER":
                            customerMap.put(Integer.parseInt(t[1]), new Point((int) Double.parseDouble(t[2]), (int) Double.parseDouble(t[3])));
                            break;
                        case "PARCEL":
                            String pid = "P" + t[1];
                            int destId = Integer.parseInt(t[3]);
                            Point dest = customerMap.get(destId);
                            if (dest != null) {
                                data.parcels.add(new Parcel(pid, dest.x, dest.y, 1));
                            }
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("MapLoader Warning: Skipping malformed line -> " + line);
                }
            }
        }
        return data;
    }
}