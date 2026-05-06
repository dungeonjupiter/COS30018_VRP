package RoutingAgent.vrp.util;

import RoutingAgent.vrp.model.Location;

public class DistanceCalculator {

    public static double euclidean(Location a, Location b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double euclidean(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double euclidean(double x1, double y1, Location b) {
        return euclidean(x1, y1, b.x, b.y);
    }
}
