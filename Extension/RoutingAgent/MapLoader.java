package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapLoader {

    public static class ParsedData {
        public List<Warehouse> warehouses = new ArrayList<>();
        public Point warehouse = new Point(50, 50);   // backward compat
        public List<Parcel> parcels = new ArrayList<>();
    }

    public static ParsedData load(String filePath) throws IOException {
        ParsedData data = new ParsedData();
        Map<Integer, Point>     customerMap  = new HashMap<>();
        Map<Integer, Warehouse> warehouseMap = new HashMap<>();
        List<int[]>             rawParcels   = new ArrayList<>();
        int parcelSeq = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] t = line.split("\\s+");
                try {
                    switch (t[0].toUpperCase()) {
                        case "WAREHOUSE":
                            if (t.length == 3) {
                                int x = (int) Double.parseDouble(t[1]);
                                int y = (int) Double.parseDouble(t[2]);
                                warehouseMap.put(0, new Warehouse(0, x, y));
                            } else if (t.length >= 4) {
                                int id = Integer.parseInt(t[1]);
                                int x  = (int) Double.parseDouble(t[2]);
                                int y  = (int) Double.parseDouble(t[3]);
                                warehouseMap.put(id, new Warehouse(id, x, y));
                            }
                            break;
                        case "CUSTOMER":
                            customerMap.put(Integer.parseInt(t[1]),
                                new Point((int)Double.parseDouble(t[2]), (int)Double.parseDouble(t[3])));
                            break;
                        case "PARCEL":
                            parcelSeq++;
                            int pid  = (t.length > 1) ? Integer.parseInt(t[1]) : parcelSeq;
                            int whId = (t.length > 2) ? Integer.parseInt(t[2]) : 0;
                            int dest = (t.length > 3) ? Integer.parseInt(t[3]) : -1;
                            rawParcels.add(new int[]{pid, whId, dest});
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("MapLoader: skipping malformed line -> " + line);
                }
            }
        }

        if (warehouseMap.isEmpty()) warehouseMap.put(0, new Warehouse(0, 50, 50));
        data.warehouses.addAll(warehouseMap.values());
        data.warehouses.sort(Comparator.comparingInt(Warehouse::getId));
        data.warehouse = data.warehouses.get(0).getPos();

        for (int[] raw : rawParcels) {
            Point destPt = customerMap.get(raw[2]);
            if (destPt == null) continue;
            int resolvedWhId = raw[1];
            if (!warehouseMap.containsKey(resolvedWhId)) {
                resolvedWhId = nearestWarehouseId(destPt, warehouseMap);
                System.err.println("MapLoader: parcel P" + raw[0]
                        + " unknown warehouse " + raw[1] + " -> nearest WH-" + resolvedWhId);
            }
            data.parcels.add(new Parcel("P" + raw[0], destPt.x, destPt.y, 1, resolvedWhId));
        }
        return data;
    }

    private static int nearestWarehouseId(Point dest, Map<Integer, Warehouse> map) {
        return map.values().stream()
                .min(Comparator.comparingDouble(w -> w.getPos().distance(dest)))
                .map(Warehouse::getId).orElse(0);
    }
}
