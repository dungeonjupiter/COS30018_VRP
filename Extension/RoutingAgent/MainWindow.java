package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.Map;

public class MainWindow extends JFrame {
    private final MasterRoutingAgent myAgent;
    private final TrackerPanel mapPanel;
    private final JTextArea logArea;

    private JTextField initCustomersField, initAgentsField;
    private JButton initBtn, loadMapBtn, clearMapBtn, plotRoutesBtn, dispatchBtn;
    private JLabel mapPathLabel;
    private String selectedMapPath = "RANDOM";

    private JTextField parcelXField, parcelYField, standbyNameField, standbyCapField;
    private JButton injectBtn, randomInjectBtn, standbyBtn, summaryBtn;
    private JPanel phase2Panel;

    public MainWindow(MasterRoutingAgent a) {
        super("VRP Hybrid Command Center");
        this.myAgent = a;
        setSize(1300, 900);
        setLayout(new BorderLayout());

        mapPanel = new TrackerPanel();
        add(mapPanel, BorderLayout.CENTER);

        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(320, 0));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ==========================================
        // PHASE 1: INITIAL SETUP
        // ==========================================
        JPanel phase1Panel = new JPanel(new GridLayout(9, 1, 5, 5));
        phase1Panel.setBorder(BorderFactory.createTitledBorder("Phase 1: Environment Setup"));

        JPanel mapBtns = new JPanel(new GridLayout(1, 2, 5, 5));
        loadMapBtn = new JButton("Load (.txt)");
        clearMapBtn = new JButton("Clear Map");
        clearMapBtn.setEnabled(false);
        mapBtns.add(loadMapBtn);
        mapBtns.add(clearMapBtn);

        mapPathLabel = new JLabel("Mode: RANDOM GENERATION");
        mapPathLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

        initCustomersField = new JTextField("20");
        initAgentsField = new JTextField("3");

        initBtn = new JButton("1. Prepare Environment");
        initBtn.setBackground(new Color(0, 123, 255));
        initBtn.setForeground(Color.WHITE);

        plotRoutesBtn = new JButton("2. Plot Routes (Math Only)");
        plotRoutesBtn.setBackground(new Color(253, 126, 20));
        plotRoutesBtn.setForeground(Color.WHITE);
        plotRoutesBtn.setEnabled(false);

        dispatchBtn = new JButton("3. Dispatch Fleet (Start Moving)");
        dispatchBtn.setBackground(new Color(40, 167, 69));
        dispatchBtn.setForeground(Color.WHITE);
        dispatchBtn.setEnabled(false);

        phase1Panel.add(mapBtns);
        phase1Panel.add(mapPathLabel);
        phase1Panel.add(new JLabel("Initial Customers:"));
        phase1Panel.add(initCustomersField);
        phase1Panel.add(new JLabel("Initial Agents:"));
        phase1Panel.add(initAgentsField);
        phase1Panel.add(initBtn);
        phase1Panel.add(plotRoutesBtn);
        phase1Panel.add(dispatchBtn);
        sidePanel.add(phase1Panel);

        sidePanel.add(Box.createVerticalStrut(20));

        // ==========================================
        // PHASE 2: DYNAMIC CONTROLS
        // ==========================================
        phase2Panel = new JPanel(new GridLayout(11, 1, 5, 5));
        phase2Panel.setBorder(BorderFactory.createTitledBorder("Phase 2: Dynamic Management"));

        parcelXField = new JTextField("80");
        parcelYField = new JTextField("80");
        injectBtn = new JButton("Inject Manual Parcel");
        randomInjectBtn = new JButton("Inject Random Parcel");

        standbyNameField = new JTextField("Standby-1");
        standbyCapField = new JTextField("5");
        standbyBtn = new JButton("Deploy Standby Agent");

        summaryBtn = new JButton("End Day & View Summary");
        summaryBtn.setBackground(new Color(220, 53, 69));
        summaryBtn.setForeground(Color.WHITE);

        phase2Panel.add(new JLabel("Manual Parcel Dest (X,Y):"));
        JPanel xyPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        xyPanel.add(parcelXField);
        xyPanel.add(parcelYField);
        phase2Panel.add(xyPanel);
        phase2Panel.add(injectBtn);
        phase2Panel.add(randomInjectBtn);
        phase2Panel.add(new JSeparator());

        phase2Panel.add(new JLabel("Deploy Standby (ID, Capacity):"));
        JPanel standbyPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        standbyPanel.add(standbyNameField);
        standbyPanel.add(standbyCapField);
        phase2Panel.add(standbyPanel);

        phase2Panel.add(standbyBtn);
        phase2Panel.add(Box.createVerticalStrut(10));
        phase2Panel.add(summaryBtn);

        setPhase2Enabled(false);
        sidePanel.add(phase2Panel);
        add(sidePanel, BorderLayout.EAST);

        logArea = new JTextArea(8, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // --- BUTTON ACTIONS ---
        loadMapBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                selectedMapPath = file.getAbsolutePath();
                mapPathLabel.setText("Map: " + file.getName());

                initCustomersField.setEnabled(false);
                initAgentsField.setEnabled(true);
                loadMapBtn.setEnabled(false);
                clearMapBtn.setEnabled(true);
                myAgent.previewMap(selectedMapPath);
            }
        });

        clearMapBtn.addActionListener(e -> {
            selectedMapPath = "RANDOM";
            mapPathLabel.setText("Mode: RANDOM GENERATION");
            initCustomersField.setEnabled(true);
            loadMapBtn.setEnabled(true);
            clearMapBtn.setEnabled(false);
            myAgent.clearPreview();
        });

        initBtn.addActionListener(e -> {
            try {
                int cust = Integer.parseInt(initCustomersField.getText().trim());
                int agents = Integer.parseInt(initAgentsField.getText().trim());

                initBtn.setEnabled(false);
                loadMapBtn.setEnabled(false);
                clearMapBtn.setEnabled(false);

                myAgent.prepareEnvironment(cust, agents, selectedMapPath);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numbers.");
            }
        });

        plotRoutesBtn.addActionListener(e -> {
            plotRoutesBtn.setEnabled(false);
            myAgent.plotRoutes();
        });

        dispatchBtn.addActionListener(e -> {
            dispatchBtn.setEnabled(false);
            myAgent.dispatchFleet();
        });

        injectBtn.addActionListener(e -> {
            try {
                int x = Integer.parseInt(parcelXField.getText().trim());
                int y = Integer.parseInt(parcelYField.getText().trim());
                myAgent.injectDynamicParcel(new Parcel("Dyn-" + System.currentTimeMillis(), x, y, 1));
            } catch (Exception ex) {}
        });

        randomInjectBtn.addActionListener(e -> {
            java.util.Random r = new java.util.Random();
            myAgent.injectDynamicParcel(new Parcel("Rnd-" + System.currentTimeMillis(), r.nextInt(100), r.nextInt(100), 1));
        });

        standbyBtn.addActionListener(e -> {
            try {
                String name = standbyNameField.getText().trim();
                int cap = Integer.parseInt(standbyCapField.getText().trim());
                if (name.isEmpty() || cap <= 0) throw new Exception("Invalid Input");
                myAgent.deployStandby(name, cap);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Please enter a valid Name and a positive Capacity.");
            }
        });

        summaryBtn.addActionListener(e -> {
            RouteSummaryGui summaryGui = new RouteSummaryGui(
                    myAgent.getInitialPlannedRoutes(),
                    myAgent.getActualDrivenRoutes(),
                    myAgent.parcelDirectory
            );
            summaryGui.setVisible(true);
        });

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setLocationRelativeTo(null);
    }

    public void setPhase2Enabled(boolean enabled) {
        for (Component c : phase2Panel.getComponents()) {
            if (c != summaryBtn) c.setEnabled(enabled);
        }
    }

    public void enablePlotting() { SwingUtilities.invokeLater(() -> plotRoutesBtn.setEnabled(true)); }
    public void enableDispatch() { SwingUtilities.invokeLater(() -> dispatchBtn.setEnabled(true)); }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateMap(Map<String, Point> locs, Map<String, List<Point>> actual, Map<String, List<Point>> remaining, Map<String, Integer> capacities, List<Point> unassigned) {
        SwingUtilities.invokeLater(() -> mapPanel.updateData(locs, actual, remaining, capacities, myAgent.getInitialPlannedRoutes(), unassigned, myAgent.parcelDirectory));
    }

    public void enableSummary() {
        SwingUtilities.invokeLater(() -> {
            summaryBtn.setEnabled(true);
            log("System Notice: All agents IDLE. Day summary available.");
        });
    }
}