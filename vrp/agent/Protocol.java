package RoutingAgent.vrp.agent;

public final class Protocol {

    // Content prefixes for ACLMessage routing
    public static final String NEW_ROUTE         = "NEW_ROUTE";
    public static final String FREEZE            = "FREEZE";
    public static final String RESUME            = "RESUME";
    public static final String STATE_REPORT      = "STATE_REPORT";
    public static final String DELIVERY_COMPLETE = "DELIVERY_COMPLETE";
    public static final String ALL_DELIVERED     = "ALL_DELIVERED";

    private Protocol() {}
}
