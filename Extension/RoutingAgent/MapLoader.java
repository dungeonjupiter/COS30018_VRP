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
        public List<Point> warehouses = new ArrayList<>();
        public List<Parcel> parcels = new ArrayList<>();

        public Point getPrimaryWarehouse() {
            return warehouses.isEmpty() ? new Point(50, 50) : warehouses.get(0);
        }
    }

    public static ParsedData load(String filePath) throws IOException {
        ParsedData data = new ParsedData();
        Map<Integer, Point> customerMap = new HashMap<>();
        Map<Integer, Point> warehouseMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int warehouseCounter = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] t = line.split("\\s+");
                try {
                    switch (t[0].toUpperCase()) {
                        case "WAREHOUSE":
                            warehouseCounter++;
                            Point wh = new Point((int) Double.parseDouble(t[1]), (int) Double.parseDouble(t[2]));
                            data.warehouses.add(wh);
                            warehouseMap.put(warehouseCounter, wh);
                            break;
                        case "CUSTOMER":
                            customerMap.put(Integer.parseInt(t[1]), new Point((int) Double.parseDouble(t[2]), (int) Double.parseDouble(t[3])));
                            break;
                        case "PARCEL":
                            String pid = "P" + t[1];
                            int destId = Integer.parseInt(t[3]);
                            Point dest = customerMap.get(destId);
                            Point origin = data.getPrimaryWarehouse();
                            if (t.length >= 6 && t[4].equalsIgnoreCase("WH")) {
                                origin = warehouseMap.getOrDefault(Integer.parseInt(t[5]), origin);
                            }
                            if (dest != null) {
                                data.parcels.add(new Parcel(pid, dest.x, dest.y, 1, origin));
                            }
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("MapLoader Warning: Skipping malformed line -> " + line);
                }
            }
        }
        if (data.warehouses.isEmpty()) {
            data.warehouses.add(new Point(50, 50));
        }
        return data;
    }
}
