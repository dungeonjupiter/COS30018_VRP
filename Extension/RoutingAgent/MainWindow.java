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
    private JTextField scenarioWhXField, scenarioWhYField;
    private JButton injectBtn, randomInjectBtn, standbyBtn, summaryBtn;
    private JPanel phase2Panel;

    private JRadioButton singleWarehouseRadio, multiWarehouseRadio;
    private JTextField singleWhXField, singleWhYField;
    private JButton setWarehouseBtn, addWarehouseBtn, addScenarioParcelBtn, clearScenarioBtn;
    private JComboBox<String> parcelWarehouseCombo;
    private JLabel scenarioStatusLabel;
    private JPanel singleWarehousePanel, multiWarehousePanel;
    private DefaultListModel<String> warehouseListModel;

    private JPanel warehouseCardPanel;

    public MainWindow(MasterRoutingAgent a) {
        super("VRP Hybrid Command Center");
        this.myAgent = a;
        setSize(1300, 950);
        setLayout(new BorderLayout());

        mapPanel = new TrackerPanel();
        add(mapPanel, BorderLayout.CENTER);

        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(360, 0));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel phase1Panel = new JPanel();
        phase1Panel.setLayout(new BoxLayout(phase1Panel, BoxLayout.Y_AXIS));
        phase1Panel.setBorder(BorderFactory.createTitledBorder("Phase 1: Environment Setup"));

        JPanel mapBtns = new JPanel(new GridLayout(1, 2, 5, 5));
        loadMapBtn = new JButton("Load (.txt)");
        clearMapBtn = new JButton("Clear Map");
        clearMapBtn.setEnabled(false);
        mapBtns.add(loadMapBtn);
        mapBtns.add(clearMapBtn);

        mapPathLabel = new JLabel("Mode: RANDOM / SCENARIO BUILDER");
        mapPathLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

        JPanel warehouseModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        warehouseModePanel.setBorder(BorderFactory.createTitledBorder("Warehouse Mode"));
        singleWarehouseRadio = new JRadioButton("Single", true);
        multiWarehouseRadio = new JRadioButton("Multiple");
        ButtonGroup warehouseGroup = new ButtonGroup();
        warehouseGroup.add(singleWarehouseRadio);
        warehouseGroup.add(multiWarehouseRadio);
        warehouseModePanel.add(singleWarehouseRadio);
        warehouseModePanel.add(multiWarehouseRadio);

        JPanel scenarioPanel = new JPanel();
        scenarioPanel.setLayout(new BoxLayout(scenarioPanel, BoxLayout.Y_AXIS));
        scenarioPanel.setBorder(BorderFactory.createTitledBorder("Scenario Builder (add while designing)"));
        scenarioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        singleWhXField = new JTextField("50");
        singleWhYField = new JTextField("50");
        setPreferredFieldSize(singleWhXField);
        setPreferredFieldSize(singleWhYField);

        singleWarehousePanel = new JPanel(new GridLayout(3, 1, 4, 4));
        singleWarehousePanel.add(new JLabel("Warehouse (X, Y):"));
        JPanel singleWhFields = new JPanel(new GridLayout(1, 2, 6, 0));
        singleWhFields.add(singleWhXField);
        singleWhFields.add(singleWhYField);
        setWarehouseBtn = new JButton("Set Warehouse");
        singleWarehousePanel.add(singleWhFields);
        singleWarehousePanel.add(setWarehouseBtn);

        scenarioWhXField = new JTextField("20");
        scenarioWhYField = new JTextField("80");
        setPreferredFieldSize(scenarioWhXField);
        setPreferredFieldSize(scenarioWhYField);
        warehouseListModel = new DefaultListModel<>();
        JList<String> warehouseList = new JList<>(warehouseListModel);
        warehouseList.setVisibleRowCount(4);
        warehouseList.setFixedCellHeight(18);
        addWarehouseBtn = new JButton("Add Warehouse");

        multiWarehousePanel = new JPanel();
        multiWarehousePanel.setLayout(new BoxLayout(multiWarehousePanel, BoxLayout.Y_AXIS));
        multiWarehousePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel multiCoordsPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        multiCoordsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        multiCoordsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        multiCoordsPanel.add(new JLabel("New WH (X, Y):"));
        JPanel multiWhFields = new JPanel(new GridLayout(1, 2, 6, 0));
        multiWhFields.add(scenarioWhXField);
        multiWhFields.add(scenarioWhYField);
        multiCoordsPanel.add(multiWhFields);

        addWarehouseBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addWarehouseBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, addWarehouseBtn.getPreferredSize().height));

        JScrollPane warehouseListScroll = new JScrollPane(warehouseList);
        warehouseListScroll.setPreferredSize(new Dimension(0, 90));
        warehouseListScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        warehouseListScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        multiWarehousePanel.add(multiCoordsPanel);
        multiWarehousePanel.add(Box.createVerticalStrut(4));
        multiWarehousePanel.add(addWarehouseBtn);
        multiWarehousePanel.add(Box.createVerticalStrut(6));
        multiWarehousePanel.add(warehouseListScroll);

        warehouseCardPanel = new JPanel(new CardLayout());
        warehouseCardPanel.add(singleWarehousePanel, "single");
        warehouseCardPanel.add(multiWarehousePanel, "multi");
        warehouseCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        warehouseCardPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        parcelXField = new JTextField("80");
        parcelYField = new JTextField("80");
        setPreferredFieldSize(parcelXField);
        setPreferredFieldSize(parcelYField);
        parcelWarehouseCombo = new JComboBox<>();
        parcelWarehouseCombo.setEnabled(false);
        addScenarioParcelBtn = new JButton("Add Parcel to Scenario");
        clearScenarioBtn = new JButton("Clear Scenario");
        scenarioStatusLabel = new JLabel("1 warehouse, 0 parcels");
        scenarioStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

        JPanel scenarioParcelPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        scenarioParcelPanel.add(new JLabel("Parcel (X, Y):"));
        JPanel parcelFields = new JPanel(new GridLayout(1, 2, 6, 0));
        parcelFields.add(parcelXField);
        parcelFields.add(parcelYField);
        scenarioParcelPanel.add(parcelFields);

        JPanel parcelWhPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        parcelWhPanel.add(new JLabel("Origin WH:"));
        parcelWhPanel.add(parcelWarehouseCombo);

        scenarioPanel.add(warehouseCardPanel);
        scenarioPanel.add(scenarioParcelPanel);
        scenarioPanel.add(parcelWhPanel);
        scenarioPanel.add(addScenarioParcelBtn);
        scenarioPanel.add(clearScenarioBtn);
        scenarioPanel.add(scenarioStatusLabel);

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
        phase1Panel.add(Box.createVerticalStrut(5));
        phase1Panel.add(warehouseModePanel);
        phase1Panel.add(scenarioPanel);
        phase1Panel.add(Box.createVerticalStrut(5));
        phase1Panel.add(new JLabel("Initial Customers (random mode only):"));
        phase1Panel.add(initCustomersField);
        phase1Panel.add(new JLabel("Initial Agents:"));
        phase1Panel.add(initAgentsField);
        phase1Panel.add(initBtn);
        phase1Panel.add(plotRoutesBtn);
        phase1Panel.add(dispatchBtn);
        sidePanel.add(phase1Panel);

        singleWarehouseRadio.addActionListener(e -> updateWarehouseModeUi());
        multiWarehouseRadio.addActionListener(e -> updateWarehouseModeUi());

        setWarehouseBtn.addActionListener(e -> {
            try {
                int x = Integer.parseInt(singleWhXField.getText().trim());
                int y = Integer.parseInt(singleWhYField.getText().trim());
                myAgent.setSingleWarehouse(x, y);
                refreshScenarioUi();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Enter valid warehouse coordinates.");
            }
        });

        addWarehouseBtn.addActionListener(e -> {
            try {
                int x = Integer.parseInt(scenarioWhXField.getText().trim());
                int y = Integer.parseInt(scenarioWhYField.getText().trim());
                if (myAgent.addScenarioWarehouse(x, y)) {
                    refreshScenarioUi();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "A warehouse already exists at (" + x + ", " + y + "). Use different coordinates.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Enter valid warehouse coordinates.");
            }
        });

        addScenarioParcelBtn.addActionListener(e -> addParcelToScenario());

        clearScenarioBtn.addActionListener(e -> {
            myAgent.clearScenario();
            selectedMapPath = "RANDOM";
            mapPathLabel.setText("Mode: RANDOM / SCENARIO BUILDER");
            initCustomersField.setEnabled(true);
            loadMapBtn.setEnabled(true);
            clearMapBtn.setEnabled(false);
            refreshScenarioUi();
        });

        myAgent.setSingleWarehouse(50, 50);
        updateWarehouseModeUi();
        mapPanel.setWarehouses(myAgent.getWarehouses());

        sidePanel.add(Box.createVerticalStrut(20));

        phase2Panel = new JPanel(new GridLayout(11, 1, 5, 5));
        phase2Panel.setBorder(BorderFactory.createTitledBorder("Phase 2: Dynamic Management"));

        JTextField phase2ParcelX = new JTextField("80");
        JTextField phase2ParcelY = new JTextField("80");
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
        xyPanel.add(phase2ParcelX);
        xyPanel.add(phase2ParcelY);
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

        JScrollPane sideScroll = new JScrollPane(sidePanel);
        sideScroll.setBorder(null);
        sideScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sideScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(sideScroll, BorderLayout.EAST);

        logArea = new JTextArea(8, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        loadMapBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                selectedMapPath = file.getAbsolutePath();
                mapPathLabel.setText("Map: " + file.getName());
                initCustomersField.setEnabled(false);
                loadMapBtn.setEnabled(false);
                clearMapBtn.setEnabled(true);
                myAgent.previewMap(selectedMapPath);
            }
        });

        clearMapBtn.addActionListener(e -> {
            selectedMapPath = "RANDOM";
            mapPathLabel.setText("Mode: RANDOM / SCENARIO BUILDER");
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
                int x = Integer.parseInt(phase2ParcelX.getText().trim());
                int y = Integer.parseInt(phase2ParcelY.getText().trim());
                myAgent.injectDynamicParcel(new Parcel("Dyn-" + System.currentTimeMillis(), x, y, 1,
                        myAgent.nearestWarehouse(x, y)));
            } catch (Exception ignored) {}
        });

        randomInjectBtn.addActionListener(e -> {
            java.util.Random r = new java.util.Random();
            int x = r.nextInt(100);
            int y = r.nextInt(100);
            myAgent.injectDynamicParcel(new Parcel("Rnd-" + System.currentTimeMillis(), x, y, 1,
                    myAgent.nearestWarehouse(x, y)));
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
                    myAgent.parcelDirectory,
                    myAgent.getWarehouses()
            );
            summaryGui.setVisible(true);
        });

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setLocationRelativeTo(null);
    }

    private static void setPreferredFieldSize(JTextField field) {
        Dimension size = new Dimension(90, 26);
        field.setMinimumSize(size);
        field.setPreferredSize(size);
    }

    private void updateWarehouseModeUi() {
        boolean multi = multiWarehouseRadio.isSelected();
        myAgent.setMultiWarehouseMode(multi);
        CardLayout cards = (CardLayout) warehouseCardPanel.getLayout();
        cards.show(warehouseCardPanel, multi ? "multi" : "single");
        parcelWarehouseCombo.setEnabled(multi);
        addWarehouseBtn.setEnabled(multi);
        warehouseCardPanel.revalidate();
        warehouseCardPanel.repaint();

        if (multi) {
            if (myAgent.getWarehouses().size() <= 1) {
                myAgent.clearScenario();
                myAgent.resetMultiWarehouses();
            }
        } else {
            try {
                int x = Integer.parseInt(singleWhXField.getText().trim());
                int y = Integer.parseInt(singleWhYField.getText().trim());
                myAgent.setSingleWarehouse(x, y);
            } catch (Exception ignored) {}
        }
        refreshScenarioUi();
    }

    private void addParcelToScenario() {
        try {
            int x = Integer.parseInt(parcelXField.getText().trim());
            int y = Integer.parseInt(parcelYField.getText().trim());
            java.awt.Point origin;
            if (multiWarehouseRadio.isSelected()) {
                int idx = parcelWarehouseCombo.getSelectedIndex();
                if (idx < 0) {
                    JOptionPane.showMessageDialog(this, "Add at least one warehouse first.");
                    return;
                }
                origin = myAgent.getWarehouses().get(idx);
            } else {
                origin = myAgent.getWarehouses().get(0);
            }
            myAgent.addScenarioParcel(x, y, origin);
            selectedMapPath = "SCENARIO";
            mapPathLabel.setText("Mode: CUSTOM SCENARIO");
            initCustomersField.setEnabled(false);
            refreshScenarioUi();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Enter valid parcel coordinates.");
        }
    }

    public void refreshScenarioUi() {
        SwingUtilities.invokeLater(() -> {
            warehouseListModel.clear();
            int i = 1;
            for (java.awt.Point wh : myAgent.getWarehouses()) {
                warehouseListModel.addElement("WH" + i + " (" + wh.x + "," + wh.y + ")");
                i++;
            }
            parcelWarehouseCombo.removeAllItems();
            i = 1;
            for (java.awt.Point wh : myAgent.getWarehouses()) {
                parcelWarehouseCombo.addItem("WH" + i + " (" + wh.x + "," + wh.y + ")");
                i++;
            }
            scenarioStatusLabel.setText(myAgent.getWarehouses().size() + " warehouses, "
                    + myAgent.getScenarioParcelCount() + " parcels");
            mapPanel.setWarehouses(myAgent.getWarehouses());
            myAgent.refreshScenarioPreview();
        });
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
