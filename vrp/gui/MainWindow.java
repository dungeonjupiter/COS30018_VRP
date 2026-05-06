package RoutingAgent.vrp.gui;

import RoutingAgent.vrp.agent.MasterRoutingAgent;
import RoutingAgent.vrp.algorithm.GreedyPlanner;
import RoutingAgent.vrp.io.MapLoader;
import RoutingAgent.vrp.model.*;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class MainWindow extends JFrame {

    private final MapPanel mapPanel;
    private final JTextArea logArea;
    private final JPanel infoPanel;
    private JButton planBtn;
    private JButton simBtn;
    private JButton addParcelBtn;
    private JTextField xField;
    private JTextField yField;
    private AgentContainer     jadeContainer;
    private WorldState         worldState;
    private MasterRoutingAgent mraInstance;

    public MainWindow() {
        super("VRP Dynamic Routing System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        mapPanel = new MapPanel();
        mapPanel.setPreferredSize(new Dimension(720, 660));
        add(mapPanel, BorderLayout.CENTER);

        infoPanel = new JPanel(new GridLayout(0, 2, 4, 3));
        infoPanel.setBorder(new TitledBorder("Map Info"));
        infoPanel.setBackground(new Color(245, 245, 248));
        add(buildDashboard(), BorderLayout.EAST);

        logArea = new JTextArea("System ready.\n");
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setRows(5);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        add(logScroll, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(980, 650));
        setVisible(true);
    }

    // ── dashboard ─────────────────────────────────────────────────────────────

    private JPanel buildDashboard() {
        JPanel dash = new JPanel();
        dash.setLayout(new BoxLayout(dash, BoxLayout.Y_AXIS));
        dash.setPreferredSize(new Dimension(250, 660));
        dash.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        dash.setBackground(new Color(245, 245, 248));

        // — Load map
        JPanel loadSection = new JPanel(new BorderLayout(4, 4));
        loadSection.setBorder(new TitledBorder("Map"));
        loadSection.setBackground(dash.getBackground());
        JButton loadBtn = new JButton("Load Map File");
        loadBtn.addActionListener(e -> loadMap());
        loadSection.add(loadBtn, BorderLayout.CENTER);
        dash.add(loadSection);
        dash.add(Box.createVerticalStrut(6));

        // — Map info (populated on load)
        dash.add(infoPanel);
        dash.add(Box.createVerticalStrut(6));

        // — Plan routes
        JPanel routeSection = new JPanel(new BorderLayout(4, 4));
        routeSection.setBorder(new TitledBorder("Routing"));
        routeSection.setBackground(dash.getBackground());
        planBtn = new JButton("Plan Routes (Greedy)");
        planBtn.setEnabled(false);
        planBtn.addActionListener(e -> planRoutes());
        routeSection.add(planBtn, BorderLayout.CENTER);
        dash.add(routeSection);
        dash.add(Box.createVerticalStrut(6));

        // — Simulation
        JPanel simSection = new JPanel(new BorderLayout(4, 4));
        simSection.setBorder(new TitledBorder("Simulation"));
        simSection.setBackground(dash.getBackground());
        simBtn = new JButton("Start Simulation");
        simBtn.setEnabled(false);
        simBtn.addActionListener(e -> startSimulation());
        simSection.add(simBtn, BorderLayout.CENTER);
        dash.add(simSection);
        dash.add(Box.createVerticalStrut(6));

        // — Add Parcel (Phase 5)
        dash.add(buildAddParcelPanel());
        dash.add(Box.createVerticalStrut(6));

        // — Phase 6 placeholder
        dash.add(buildPlaceholder("Add Agent", "Phase 6"));

        dash.add(Box.createVerticalGlue());
        return dash;
    }

    private JPanel buildAddParcelPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder("Add Parcel"));
        p.setBackground(new Color(245, 245, 248));

        // Coordinate row
        JPanel coords = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        coords.setBackground(p.getBackground());
        coords.add(new JLabel("X:"));
        xField = new JTextField(4);
        coords.add(xField);
        coords.add(new JLabel("Y:"));
        yField = new JTextField(4);
        coords.add(yField);
        p.add(coords);

        // Button row
        JPanel btns = new JPanel(new GridLayout(1, 2, 4, 2));
        btns.setBackground(p.getBackground());
        JButton randomBtn = new JButton("Random");
        randomBtn.addActionListener(e -> fillRandom());
        btns.add(randomBtn);
        addParcelBtn = new JButton("Add Parcel");
        addParcelBtn.setEnabled(false);
        addParcelBtn.addActionListener(e -> submitAddParcel());
        btns.add(addParcelBtn);
        p.add(btns);

        return p;
    }

    private void fillRandom() {
        if (worldState == null) return;
        double minX = worldState.customers.stream().mapToDouble(c -> c.x).min().orElse(5);
        double maxX = worldState.customers.stream().mapToDouble(c -> c.x).max().orElse(95);
        double minY = worldState.customers.stream().mapToDouble(c -> c.y).min().orElse(5);
        double maxY = worldState.customers.stream().mapToDouble(c -> c.y).max().orElse(95);
        Random rng = new Random();
        xField.setText(String.format("%.1f", minX + rng.nextDouble() * (maxX - minX)));
        yField.setText(String.format("%.1f", minY + rng.nextDouble() * (maxY - minY)));
    }

    private void submitAddParcel() {
        if (mraInstance == null) return;
        try {
            double x = Double.parseDouble(xField.getText().trim());
            double y = Double.parseDouble(yField.getText().trim());
            addParcelBtn.setEnabled(false);
            mraInstance.requestAddParcel(x, y);
            xField.setText("");
            yField.setText("");
        } catch (NumberFormatException ex) {
            log("Invalid coordinates — enter numbers or click Random first");
        }
    }

    private JPanel buildPlaceholder(String title, String phase) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder(title));
        p.setBackground(new Color(245, 245, 248));
        JLabel lbl = new JLabel("  Available in " + phase);
        lbl.setForeground(Color.GRAY);
        lbl.setFont(lbl.getFont().deriveFont(Font.ITALIC));
        p.add(lbl);
        return p;
    }

    // ── map loading ───────────────────────────────────────────────────────────

    private void loadMap() {
        JFileChooser fc = new JFileChooser(new File("preset_maps"));
        fc.setDialogTitle("Select VRP Map File");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            worldState = MapLoader.load(fc.getSelectedFile().getAbsolutePath());
            mapPanel.setWorldState(worldState);
            refreshInfoPanel();
            planBtn.setEnabled(true);
            log("Loaded: " + fc.getSelectedFile().getName()
                    + " — " + worldState.customers.size() + " customers, "
                    + worldState.parcels.size() + " parcels, "
                    + worldState.agents.size() + " agents");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load map:\n" + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
            log("ERROR: " + ex.getMessage());
        }
    }

    private void refreshInfoPanel() {
        infoPanel.removeAll();

        Warehouse wh = worldState.warehouse;
        addRow("Warehouse:", "(" + fmt(wh.x) + ", " + fmt(wh.y) + ")");
        addRow("Customers:", String.valueOf(worldState.customers.size()));
        addRow("Parcels:", String.valueOf(worldState.parcels.size()));
        addRow("Agents:", String.valueOf(worldState.agents.size()));
        int totalCap = worldState.agents.stream().mapToInt(a -> a.capacity).sum();
        addRow("Total Capacity:", String.valueOf(totalCap));

        if (!worldState.agents.isEmpty()) {
            infoPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
            infoPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
            for (AgentState a : worldState.agents) {
                addRow(a.name() + ":", a.status + "  (" + a.inVehicle.size() + "/" + a.capacity + ")");
            }
        }

        infoPanel.revalidate();
        infoPanel.repaint();
    }

    private void addRow(String key, String value) {
        JLabel k = new JLabel(key);
        k.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        JLabel v = new JLabel(value);
        v.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        infoPanel.add(k);
        infoPanel.add(v);
    }

    private String fmt(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ── planning ──────────────────────────────────────────────────────────────

    private void planRoutes() {
        if (worldState == null) return;
        GreedyPlanner.plan(worldState);
        mapPanel.setWorldState(worldState);
        refreshInfoPanel();

        StringBuilder sb = new StringBuilder("Greedy plan —");
        double total = 0;
        for (AgentState a : worldState.agents) {
            if (!a.remainingRoute.isEmpty()) {
                double dist = GreedyPlanner.routeDistance(a);
                total += dist;
                sb.append(String.format(" %s:%.1f", a.name(), dist));
            }
        }
        sb.append(String.format("  |  fleet total: %.1f", total));
        log(sb.toString());
        simBtn.setEnabled(true);
    }

    // ── simulation ────────────────────────────────────────────────────────────

    private void startSimulation() {
        if (worldState == null) return;
        simBtn.setEnabled(false);
        planBtn.setEnabled(false);

        if (jadeContainer == null) {
            // First run — boot JADE then spawn agents immediately
            bootJade();
            spawnAgents();
        } else {
            // Re-run — kill old agents, wait for JADE to deregister them, then respawn
            try { jadeContainer.getAgent("MRA").kill(); } catch (Exception ignored) {}
            for (AgentState a : worldState.agents) {
                try { jadeContainer.getAgent(a.name()).kill(); } catch (Exception ignored) {}
            }
            Timer respawn = new Timer(800, e -> spawnAgents());
            respawn.setRepeats(false);
            respawn.start();
        }
    }

    private void bootJade() {
        try {
            Runtime rt = Runtime.instance();
            Profile p  = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.GUI,       "false");
            jadeContainer = rt.createMainContainer(p);
        } catch (Exception e) {
            log("ERROR booting JADE: " + e.getMessage());
        }
    }

    private void spawnAgents() {
        try {
            AgentController mra = jadeContainer.createNewAgent(
                    "MRA", MasterRoutingAgent.class.getName(),
                    new Object[]{worldState, this});
            mra.start();
            mapPanel.startAnimation();
            addParcelBtn.setEnabled(true);
            log("JADE started — simulation running");
        } catch (Exception e) {
            log("ERROR starting simulation: " + e.getMessage());
            planBtn.setEnabled(true);
        }
    }

    // ── callbacks for MRA ─────────────────────────────────────────────────────

    public void setMRA(MasterRoutingAgent mra) {
        mraInstance = mra;
    }

    public void setAddParcelEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> addParcelBtn.setEnabled(enabled));
    }

    public void refreshMap() {
        SwingUtilities.invokeLater(() -> {
            if (worldState != null) refreshInfoPanel();
        });
    }

    public void onSimulationComplete() {
        SwingUtilities.invokeLater(() -> {
            mapPanel.stopAnimation();
            mapPanel.repaint();
            addParcelBtn.setEnabled(false);
            mraInstance = null;
            planBtn.setEnabled(true);
        });
    }

    // ── log ───────────────────────────────────────────────────────────────────

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
