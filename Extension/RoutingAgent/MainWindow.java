package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
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
    private JPanel            customWhPanel;
    private JPanel            warehouseRowsPanel;
    private final List<WarehouseRow> warehouseRows = new ArrayList<>();
    private JButton           addWarehouseBtn, presetTriangleBtn;

    private static final int MIN_MULTI_WAREHOUSES = 2;
    private static final int MAX_WAREHOUSES       = 20;

    private static final int[][] PRESET_TRIANGLE = {
            {20, 20}, {80, 20}, {50, 80}
    };

    private static class WarehouseRow {
        final JLabel     label;
        final JTextField xField, yField;
        final JPanel     panel;
        WarehouseRow(JLabel label, JTextField xField, JTextField yField, JPanel panel) {
            this.label = label; this.xField = xField; this.yField = yField; this.panel = panel;
        }
    }

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
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
        innerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        innerPanel.add(sectionLabel("Map Source"));
        mapPathLabel = new JLabel("Mode: RANDOM GENERATION");
        mapPathLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        mapPathLabel.setAlignmentX(LEFT_ALIGNMENT);
        JPanel mapRow = new JPanel(new GridLayout(1, 2, 6, 0));
        mapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        mapRow.setAlignmentX(LEFT_ALIGNMENT);
        loadMapBtn  = new JButton("Load .txt");
        clearMapBtn = new JButton("Clear Map");
        clearMapBtn.setEnabled(false);
        mapRow.add(loadMapBtn); mapRow.add(clearMapBtn);
        innerPanel.add(mapRow);
        innerPanel.add(vgap(3)); innerPanel.add(mapPathLabel); innerPanel.add(vgap(14));

        innerPanel.add(sectionLabel("Warehouse Settings"));
        numWarehousesBox = new JComboBox<>(new String[]{
                "1 Warehouse  (Base Mode)",
                "Multiple Warehouses  (add X,Y below)"});
        numWarehousesBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        numWarehousesBox.setAlignmentX(LEFT_ALIGNMENT);
        innerPanel.add(numWarehousesBox); innerPanel.add(vgap(6));

        centralizedRb = new JRadioButton("Centralized — All Agents Starts At WH-1");
        distributedRb = new JRadioButton("Distributed — Spread Agents Evenly");
        distributedRb.setSelected(true);
        ButtonGroup bg = new ButtonGroup();
        bg.add(centralizedRb); bg.add(distributedRb);

        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        radioPanel.setAlignmentX(LEFT_ALIGNMENT);
        radioPanel.add(centralizedRb);
        radioPanel.add(distributedRb);

        innerPanel.add(radioPanel);
        innerPanel.add(vgap(8));

        JPanel customWhPanel = buildCustomWarehousePanel();
        customWhPanel.setAlignmentX(LEFT_ALIGNMENT);
        innerPanel.add(customWhPanel);
        innerPanel.add(vgap(14));

        innerPanel.add(sectionLabel("Initial Fleet"));
        innerPanel.add(row("Customers:", initCustomersField = new JTextField("20")));
        innerPanel.add(vgap(4));
        innerPanel.add(row("Agents:", initAgentsField = new JTextField("4")));
        innerPanel.add(vgap(14));

        initBtn       = colorBtn("1.  Prepare Environment",    new Color(0, 123, 255));
        plotRoutesBtn = colorBtn("2.  Plot Routes (Math Only)", new Color(253, 126, 20));
        dispatchBtn   = colorBtn("3.  Dispatch Fleet",          new Color(40, 167, 69));
        plotRoutesBtn.setEnabled(false);
        dispatchBtn.setEnabled(false);
        innerPanel.add(initBtn);    innerPanel.add(vgap(6));
        innerPanel.add(plotRoutesBtn); innerPanel.add(vgap(6));
        innerPanel.add(dispatchBtn);

        // Listens to the dropdown and turns the radio buttons on/off automatically
        numWarehousesBox.addActionListener(e -> {
            boolean isMulti = numWarehousesBox.getSelectedIndex() == 1;
            radioPanel.setVisible(isMulti);
            updateCustomWhPanelVisibility();
        });

        // Trigger it once to set the correct initial state when the app launches
        boolean initialMulti = numWarehousesBox.getSelectedIndex() == 1;
        radioPanel.setVisible(initialMulti);
        updateCustomWhPanelVisibility();

        JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tab.add(innerPanel);

        return tab;
    }

    private JPanel buildCustomWarehousePanel() {
        customWhPanel = new JPanel();
        customWhPanel.setLayout(new BoxLayout(customWhPanel, BoxLayout.Y_AXIS));
        customWhPanel.setAlignmentX(LEFT_ALIGNMENT);
        customWhPanel.setBorder(BorderFactory.createTitledBorder("Warehouses (X, Y)"));

        warehouseRowsPanel = new JPanel();
        warehouseRowsPanel.setLayout(new BoxLayout(warehouseRowsPanel, BoxLayout.Y_AXIS));
        warehouseRowsPanel.setAlignmentX(LEFT_ALIGNMENT);

        JPanel header = new JPanel(new GridBagLayout());
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        addWhGridCell(header, new JLabel(""), 0, 0, 0, GridBagConstraints.WEST);
        JLabel xHdr = new JLabel("X");
        xHdr.setHorizontalAlignment(SwingConstants.CENTER);
        addWhGridCell(header, xHdr, 1, 0, 0.5, GridBagConstraints.CENTER);
        JLabel yHdr = new JLabel("Y");
        yHdr.setHorizontalAlignment(SwingConstants.CENTER);
        addWhGridCell(header, yHdr, 2, 0, 0.5, GridBagConstraints.CENTER);
        addWhGridCell(header, new JLabel(""), 3, 0, 0, GridBagConstraints.EAST);
        customWhPanel.add(header);

        customWhPanel.add(warehouseRowsPanel);
        customWhPanel.add(vgap(4));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 4, 0));
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        addWarehouseBtn = new JButton("+ Add");
        addWarehouseBtn.setToolTipText("Add another warehouse");
        presetTriangleBtn = new JButton("Preset");
        presetTriangleBtn.setToolTipText("Load default 3-warehouse triangle");
        btnRow.add(addWarehouseBtn);
        btnRow.add(presetTriangleBtn);
        customWhPanel.add(btnRow);

        addWarehouseBtn.addActionListener(e -> {
            if (warehouseRows.size() >= MAX_WAREHOUSES) {
                JOptionPane.showMessageDialog(this, "Maximum " + MAX_WAREHOUSES + " warehouses.");
                return;
            }
            addWarehouseRow(50, 50);
        });
        presetTriangleBtn.addActionListener(e -> loadPresetTriangle());

        loadPresetTriangle();

        JLabel hint = new JLabel("<html><i>Coords 0–100. Min " + MIN_MULTI_WAREHOUSES
                + " WH. File maps override.</i></html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hint.setForeground(new Color(100, 100, 100));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        customWhPanel.add(vgap(4));
        customWhPanel.add(hint);

        return customWhPanel;
    }

    private void loadPresetTriangle() {
        clearWarehouseRows();
        for (int[] p : PRESET_TRIANGLE) addWarehouseRow(p[0], p[1]);
    }

    private void clearWarehouseRows() {
        warehouseRows.clear();
        warehouseRowsPanel.removeAll();
    }

    private static void addWhGridCell(JPanel panel, Component c, int col, int row,
                                      double weightx, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = col;
        gbc.gridy = row;
        gbc.weightx = weightx;
        gbc.weighty = 0;
        gbc.insets = new Insets(1, 2, 1, 2);
        gbc.fill = (weightx > 0) ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        gbc.anchor = anchor;
        panel.add(c, gbc);
    }

    private void addWarehouseRow(int x, int y) {
        int index = warehouseRows.size();
        JLabel lbl = new JLabel(Warehouse.displayName(index));
        lbl.setPreferredSize(new Dimension(36, 22));

        JTextField xField = compactCoordField(x);
        JTextField yField = compactCoordField(y);

        JButton removeBtn = new JButton("×");
        removeBtn.setMargin(new Insets(0, 2, 0, 2));
        removeBtn.setPreferredSize(new Dimension(26, 22));
        removeBtn.setToolTipText("Remove warehouse");

        JPanel row = new JPanel(new GridBagLayout());
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        addWhGridCell(row, lbl, 0, 0, 0, GridBagConstraints.WEST);
        addWhGridCell(row, xField, 1, 0, 1, GridBagConstraints.CENTER);
        addWhGridCell(row, yField, 2, 0, 1, GridBagConstraints.CENTER);
        addWhGridCell(row, removeBtn, 3, 0, 0, GridBagConstraints.EAST);

        WarehouseRow entry = new WarehouseRow(lbl, xField, yField, row);
        warehouseRows.add(entry);
        warehouseRowsPanel.add(row);

        removeBtn.addActionListener(e -> removeWarehouseRow(entry));
        renumberWarehouseLabels();

        customWhPanel.revalidate();
        customWhPanel.repaint();
    }

    private void removeWarehouseRow(WarehouseRow entry) {
        if (warehouseRows.size() <= MIN_MULTI_WAREHOUSES) {
            JOptionPane.showMessageDialog(this,
                    "Need at least " + MIN_MULTI_WAREHOUSES + " warehouses in multi-depot mode.");
            return;
        }
        warehouseRows.remove(entry);
        warehouseRowsPanel.remove(entry.panel);
        renumberWarehouseLabels();

        customWhPanel.revalidate();
        customWhPanel.repaint();
    }

    private static JTextField compactCoordField(int value) {
        JTextField f = new JTextField(String.valueOf(value), 3);
        f.setHorizontalAlignment(SwingConstants.CENTER);
        f.setPreferredSize(new Dimension(42, 22));
        f.setMaximumSize(new Dimension(52, 22));
        return f;
    }

    private void renumberWarehouseLabels() {
        for (int i = 0; i < warehouseRows.size(); i++)
            warehouseRows.get(i).label.setText(Warehouse.displayName(i));
    }

    private void updateCustomWhPanelVisibility() {
        boolean multi = numWarehousesBox.getSelectedIndex() == 1;
        customWhPanel.setVisible(multi);
        boolean mapLoaded = !"RANDOM".equals(selectedMapPath);
        boolean editable = multi && !mapLoaded;
        addWarehouseBtn.setEnabled(editable);
        presetTriangleBtn.setEnabled(editable);
        for (WarehouseRow wr : warehouseRows) {
            wr.xField.setEnabled(editable);
            wr.yField.setEnabled(editable);
            for (Component c : wr.panel.getComponents()) {
                if (c instanceof JButton) c.setEnabled(editable);
            }
        }
        if (multi && warehouseRows.isEmpty()) loadPresetTriangle();
        if (customWhPanel.getParent() != null) {
            customWhPanel.getParent().revalidate();
            customWhPanel.getParent().repaint();
        }
    }

    private int[][] readCustomWarehouseCoords() throws NumberFormatException {
        int[][] coords = new int[warehouseRows.size()][2];
        for (int i = 0; i < warehouseRows.size(); i++) {
            WarehouseRow wr = warehouseRows.get(i);
            int x = Integer.parseInt(wr.xField.getText().trim());
            int y = Integer.parseInt(wr.yField.getText().trim());
            if (x < 0 || x > 100 || y < 0 || y > 100) throw new NumberFormatException();
            coords[i][0] = x;
            coords[i][1] = y;
        }
        return coords;
    }

    // ── Dynamic Tab ───────────────────────────────────────────────────────────

    private JPanel buildDynamicTab() {
        dynamicTab = new JPanel();
        dynamicTab.setLayout(new BoxLayout(dynamicTab, BoxLayout.Y_AXIS));
        dynamicTab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Parcel injection
        dynamicTab.add(sectionLabel("Inject Dynamic Parcel"));
        parcelWhBox = new JComboBox<>(new String[]{"Auto (Nearest)", "WH-1"});
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
                updateCustomWhPanelVisibility();
            }
        });

        clearMapBtn.addActionListener(e -> {
            selectedMapPath = "RANDOM";
            mapPathLabel.setText("Mode: RANDOM GENERATION");
            initCustomersField.setEnabled(true);
            loadMapBtn.setEnabled(true);
            clearMapBtn.setEnabled(false);
            myAgent.clearPreview();
            updateCustomWhPanelVisibility();
        });

        initBtn.addActionListener(e -> {
            try {
                int cust   = Integer.parseInt(initCustomersField.getText().trim());
                int agents = Integer.parseInt(initAgentsField.getText().trim());
                boolean singleWh = numWarehousesBox.getSelectedIndex() == 0;
                int numWh = 1;
                int[][] customWh = null;
                if (!singleWh) {
                    if ("RANDOM".equals(selectedMapPath)) {
                        customWh = readCustomWarehouseCoords();
                        numWh = customWh.length;
                        if (numWh < MIN_MULTI_WAREHOUSES) {
                            JOptionPane.showMessageDialog(this,
                                    "Add at least " + MIN_MULTI_WAREHOUSES + " warehouses (X, Y).");
                            return;
                        }
                        if (hasDuplicateWarehouseCoords(customWh)) {
                            JOptionPane.showMessageDialog(this,
                                    "Each warehouse needs a unique (X, Y) position.");
                            return;
                        }
                    } else {
                        numWh = MIN_MULTI_WAREHOUSES;
                    }
                }
                MasterRoutingAgent.SpawnMode mode = distributedRb.isSelected()
                        ? MasterRoutingAgent.SpawnMode.DISTRIBUTED
                        : MasterRoutingAgent.SpawnMode.CENTRALIZED;
                initBtn.setEnabled(false);
                loadMapBtn.setEnabled(false);
                clearMapBtn.setEnabled(false);
                myAgent.prepareEnvironment(cust, agents, selectedMapPath, numWh, mode, customWh);
                rebuildWhBoxes(myAgent.getWarehouses().size());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Enter valid numbers. Warehouse X/Y must be integers from 0 to 100.");
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
    public void disableSummary()           {
        SwingUtilities.invokeLater(() -> summaryBtn.setEnabled(false));
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
                        myAgent.getAgentWarehouseIds(),
                        myAgent.getAgentOriginWarehouseIds()));
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

    private static boolean hasDuplicateWarehouseCoords(int[][] coords) {
        for (int i = 0; i < coords.length; i++) {
            for (int j = i + 1; j < coords.length; j++) {
                if (coords[i][0] == coords[j][0] && coords[i][1] == coords[j][1]) return true;
            }
        }
        return false;
    }

    private void rebuildWhBoxes(int numWh) {
        parcelWhBox.removeAllItems();
        parcelWhBox.addItem("Auto (Nearest)");
        for (int i = 0; i < numWh; i++) parcelWhBox.addItem(Warehouse.displayName(i));
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
