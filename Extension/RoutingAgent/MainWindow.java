package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MainWindow — tabbed control panel for Multi-Warehouse VRP system.
 *
 * GUI layout: JTabbedPane replaces the old cramped BoxLayout stacking.
 *   Tab 1 "Setup"   — warehouse config, spawn mode, initial fleet
 *   Tab 2 "Dynamic" — inject parcels, deploy standby
 *
 * Standby warehouse is now automatic (MRA picks most-strained warehouse
 * internally), so no warehouse dropdown is shown in the standby section.
 *
 * Parcel source warehouse uses a simple dropdown: Auto or explicit WH-x.
 */
public class MainWindow extends JFrame {

    private final MasterRoutingAgent myAgent;
    private final TrackerPanel       mapPanel;
    private final JTextArea          logArea;

    // Setup tab
    private JTextField        initCustomersField, initAgentsField;
    private JButton           initBtn, loadMapBtn, clearMapBtn;
    private JButton           plotRoutesBtn, dispatchBtn;
    private JLabel            mapPathLabel;
    private String            selectedMapPath = "RANDOM";
    private JComboBox<String> numWarehousesBox;
    private JRadioButton      centralizedRb, distributedRb;

    // Dynamic tab
    private JTextField        parcelXField, parcelYField;
    private JComboBox<String> parcelWhBox;
    private JButton           injectManualBtn, injectRandomBtn;
    private JTextField        standbyNameField, standbyCapField;
    private JButton           standbyBtn;
    private JButton           summaryBtn;
    private JPanel            dynamicTab;

    public MainWindow(MasterRoutingAgent a) {
        super("VRP Hybrid Command Center — Multi-Warehouse");
        this.myAgent = a;
        setSize(1300, 950);
        setLayout(new BorderLayout(0, 0));

        mapPanel = new TrackerPanel();
        add(mapPanel, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setPreferredSize(new Dimension(300, 0));
        tabs.addTab("  Setup  ", buildSetupTab());
        tabs.addTab("  Dynamic  ", buildDynamicTab());
        add(tabs, BorderLayout.EAST);

        logArea = new JTextArea(7, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 130));
        add(logScroll, BorderLayout.SOUTH);

        wireActions();
        setDynamicEnabled(false);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });
        setLocationRelativeTo(null);
    }

    // ── Setup Tab ─────────────────────────────────────────────────────────────

    private JPanel buildSetupTab() {
        JPanel tab = new JPanel();
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tab.add(sectionLabel("Map Source"));
        mapPathLabel = new JLabel("Mode: RANDOM GENERATION");
        mapPathLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        mapPathLabel.setAlignmentX(LEFT_ALIGNMENT);
        JPanel mapRow = new JPanel(new GridLayout(1, 2, 6, 0));
        mapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        loadMapBtn  = new JButton("Load .txt");
        clearMapBtn = new JButton("Clear Map");
        clearMapBtn.setEnabled(false);
        mapRow.add(loadMapBtn); mapRow.add(clearMapBtn);
        tab.add(mapRow);
        tab.add(vgap(3)); tab.add(mapPathLabel); tab.add(vgap(14));

        tab.add(sectionLabel("Warehouse Settings"));
        numWarehousesBox = new JComboBox<>(new String[]{
                "1 Warehouse  (Base Mode)",
                "3 Warehouses  (Multi-Depot)"});
        numWarehousesBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        tab.add(numWarehousesBox); tab.add(vgap(6));
        centralizedRb = new JRadioButton("Centralized — all agents at WH-0");
        distributedRb = new JRadioButton("Distributed — spread agents evenly");
        distributedRb.setSelected(true);
        ButtonGroup bg = new ButtonGroup();
        bg.add(centralizedRb); bg.add(distributedRb);
        tab.add(centralizedRb); tab.add(distributedRb); tab.add(vgap(14));

        tab.add(sectionLabel("Initial Fleet"));
        tab.add(row("Customers:", initCustomersField = new JTextField("20")));
        tab.add(vgap(4));
        tab.add(row("Agents:", initAgentsField = new JTextField("5")));
        tab.add(vgap(14));

        initBtn       = colorBtn("1.  Prepare Environment",    new Color(0, 123, 255));
        plotRoutesBtn = colorBtn("2.  Plot Routes (Math Only)", new Color(253, 126, 20));
        dispatchBtn   = colorBtn("3.  Dispatch Fleet",          new Color(40, 167, 69));
        plotRoutesBtn.setEnabled(false);
        dispatchBtn.setEnabled(false);
        tab.add(initBtn);    tab.add(vgap(6));
        tab.add(plotRoutesBtn); tab.add(vgap(6));
        tab.add(dispatchBtn);
        return tab;
    }

    // ── Dynamic Tab ───────────────────────────────────────────────────────────

    private JPanel buildDynamicTab() {
        dynamicTab = new JPanel();
        dynamicTab.setLayout(new BoxLayout(dynamicTab, BoxLayout.Y_AXIS));
        dynamicTab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Parcel injection
        dynamicTab.add(sectionLabel("Inject Dynamic Parcel"));
        parcelWhBox = new JComboBox<>(new String[]{"Auto (Nearest)", "WH-0"});
        parcelWhBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        dynamicTab.add(row("Source Warehouse:", parcelWhBox));
        dynamicTab.add(vgap(4));
        parcelXField = new JTextField("80");
        parcelYField = new JTextField("80");
        JPanel xyRow = new JPanel(new GridLayout(1, 2, 6, 0));
        xyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        xyRow.add(parcelXField); xyRow.add(parcelYField);
        dynamicTab.add(row("Destination (X, Y):", xyRow));
        dynamicTab.add(vgap(6));
        injectManualBtn = plainBtn("Inject Manual Parcel");
        injectRandomBtn = plainBtn("Inject Random Parcel");
        dynamicTab.add(injectManualBtn); dynamicTab.add(vgap(4));
        dynamicTab.add(injectRandomBtn); dynamicTab.add(vgap(16));
        dynamicTab.add(divider()); dynamicTab.add(vgap(10));

        // Standby — no warehouse dropdown, MRA picks automatically
        dynamicTab.add(sectionLabel("Deploy Standby Agent"));
        JLabel autoLabel = new JLabel("Warehouse: auto (most strained)");
        autoLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        autoLabel.setForeground(new Color(120, 120, 120));
        autoLabel.setAlignmentX(LEFT_ALIGNMENT);
        dynamicTab.add(autoLabel); dynamicTab.add(vgap(4));
        standbyNameField = new JTextField("Standby-1");
        standbyCapField  = new JTextField("5");
        dynamicTab.add(row("Agent ID:", standbyNameField));   dynamicTab.add(vgap(4));
        dynamicTab.add(row("Capacity:", standbyCapField));    dynamicTab.add(vgap(6));
        standbyBtn = plainBtn("Deploy Standby Agent");
        dynamicTab.add(standbyBtn); dynamicTab.add(vgap(16));
        dynamicTab.add(divider()); dynamicTab.add(vgap(10));

        // Summary
        summaryBtn = colorBtn("End Day  &  View Summary", new Color(220, 53, 69));
        summaryBtn.setEnabled(false);
        dynamicTab.add(summaryBtn);
        return dynamicTab;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void wireActions() {
        loadMapBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(new File(System.getProperty("user.dir")));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedMapPath = fc.getSelectedFile().getAbsolutePath();
                mapPathLabel.setText("Map: " + fc.getSelectedFile().getName());
                initCustomersField.setEnabled(false);
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
                int cust   = Integer.parseInt(initCustomersField.getText().trim());
                int agents = Integer.parseInt(initAgentsField.getText().trim());
                int numWh  = numWarehousesBox.getSelectedIndex() == 0 ? 1 : 3;
                MasterRoutingAgent.SpawnMode mode = distributedRb.isSelected()
                        ? MasterRoutingAgent.SpawnMode.DISTRIBUTED
                        : MasterRoutingAgent.SpawnMode.CENTRALIZED;
                initBtn.setEnabled(false);
                loadMapBtn.setEnabled(false);
                clearMapBtn.setEnabled(false);
                rebuildWhBoxes(numWh);
                myAgent.prepareEnvironment(cust, agents, selectedMapPath, numWh, mode);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter valid numbers.");
            }
        });

        plotRoutesBtn.addActionListener(e -> { plotRoutesBtn.setEnabled(false); myAgent.plotRoutes(); });
        dispatchBtn.addActionListener(e   -> { dispatchBtn.setEnabled(false);   myAgent.dispatchFleet(); });

        injectManualBtn.addActionListener(e -> {
            try {
                int x = Integer.parseInt(parcelXField.getText().trim());
                int y = Integer.parseInt(parcelYField.getText().trim());
                if (x < 0 || x > 100 || y < 0 || y > 100) throw new NumberFormatException();
                myAgent.injectDynamicParcel(
                        new Parcel("Dyn-" + System.currentTimeMillis(), x, y, 1, resolveWh(x, y)));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "X and Y must be 0–100.");
            }
        });

        injectRandomBtn.addActionListener(e -> {
            int x = new Random().nextInt(100), y = new Random().nextInt(100);
            myAgent.injectDynamicParcel(
                    new Parcel("Rnd-" + System.currentTimeMillis(), x, y, 1, resolveWh(x, y)));
        });

        standbyBtn.addActionListener(e -> {
            try {
                String name = standbyNameField.getText().trim();
                int    cap  = Integer.parseInt(standbyCapField.getText().trim());
                if (name.isEmpty() || cap <= 0) throw new Exception();
                myAgent.deployStandby(name, cap);   // warehouse chosen automatically by MRA
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Enter a valid agent ID and positive capacity.");
            }
        });

        summaryBtn.addActionListener(e ->
                new RouteSummaryGui(myAgent.getInitialPlannedRoutes(),
                        myAgent.getActualDrivenRoutes(),
                        myAgent.parcelDirectory,
                        myAgent.getWarehouses()).setVisible(true));
    }

    // ── Public API for MRA ────────────────────────────────────────────────────

    public void enablePlotting()           { SwingUtilities.invokeLater(() -> plotRoutesBtn.setEnabled(true)); }
    public void enableDispatch()           { SwingUtilities.invokeLater(() -> dispatchBtn.setEnabled(true)); }
    public void enableSummary()            {
        SwingUtilities.invokeLater(() -> { summaryBtn.setEnabled(true); log("All agents IDLE. Summary ready."); });
    }
    public void setPhase2Enabled(boolean on) { SwingUtilities.invokeLater(() -> setDynamicEnabled(on)); }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateMap(Map<String, Point> locs,
                          Map<String, List<Point>> actual,
                          Map<String, List<Point>> remaining,
                          Map<String, Integer> caps,
                          List<Point> unassigned,
                          List<Warehouse> warehouses) {
        SwingUtilities.invokeLater(() ->
                mapPanel.updateData(locs, actual, remaining, caps,
                        myAgent.getInitialPlannedRoutes(), unassigned,
                        myAgent.parcelDirectory, warehouses,
                        myAgent.getAgentWarehouseIds()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveWh(int x, int y) {
        int sel = parcelWhBox.getSelectedIndex();
        if (sel == 0) {
            List<Warehouse> whs = myAgent.getWarehouses();
            if (whs.isEmpty()) return 0;
            Point dest = new Point(x, y);
            return whs.stream()
                    .min(Comparator.comparingDouble(w -> w.getPos().distance(dest)))
                    .map(Warehouse::getId).orElse(0);
        }
        return sel - 1;
    }

    private void rebuildWhBoxes(int numWh) {
        parcelWhBox.removeAllItems();
        parcelWhBox.addItem("Auto (Nearest)");
        for (int i = 0; i < numWh; i++) parcelWhBox.addItem("WH-" + i);
    }

    private void setDynamicEnabled(boolean on) {
        setTreeEnabled(dynamicTab, on);
        summaryBtn.setEnabled(false);
    }

    private static void setTreeEnabled(Container c, boolean on) {
        for (Component comp : c.getComponents()) {
            comp.setEnabled(on);
            if (comp instanceof Container) setTreeEnabled((Container) comp, on);
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private static JLabel sectionLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel row(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(125, 22));
        p.add(lbl, BorderLayout.WEST);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private static JPanel row(String label, JTextField field) { return row(label, (JComponent) field); }

    private static JButton colorBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        b.setAlignmentX(LEFT_ALIGNMENT);
        return b;
    }

    private static JButton plainBtn(String text) {
        JButton b = new JButton(text);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.setAlignmentX(LEFT_ALIGNMENT);
        return b;
    }

    private static Component vgap(int h) { return Box.createVerticalStrut(h); }

    private static JSeparator divider() {
        JSeparator s = new JSeparator();
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        return s;
    }
}
