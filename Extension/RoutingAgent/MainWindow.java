package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainWindow extends JFrame {
    private final MasterRoutingAgent myAgent;
    private final TrackerPanel mapPanel;

    // Controls
    private JButton injectBtn, summaryBtn;
    private JTextField parcelXField, parcelYField;
    private JLabel statusLabel;

    public MainWindow(MasterRoutingAgent a) {
        super("VRP Asynchronous Command Center");
        this.myAgent = a;
        setSize(1000, 750);
        setLayout(new BorderLayout());

        // --- MAP PANEL (CENTER) ---
        mapPanel = new TrackerPanel();
        mapPanel.setBackground(new Color(30, 30, 35));
        add(mapPanel, BorderLayout.CENTER);

        // --- CONTROL PANEL (RIGHT) ---
        JPanel controlPanel = new JPanel(new GridLayout(10, 1, 10, 10));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Live Dispatch Controls"));
        controlPanel.setPreferredSize(new Dimension(250, 0));

        controlPanel.add(new JLabel("Dynamic Parcel X:"));
        parcelXField = new JTextField("80");
        controlPanel.add(parcelXField);

        controlPanel.add(new JLabel("Dynamic Parcel Y:"));
        parcelYField = new JTextField("80");
        controlPanel.add(parcelYField);

        injectBtn = new JButton("Inject Dynamic Parcel");
        injectBtn.setBackground(new Color(40, 167, 69));
        injectBtn.setForeground(Color.WHITE);
        controlPanel.add(injectBtn);

        controlPanel.add(new JSeparator());

        statusLabel = new JLabel("Status: Agents Active");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        controlPanel.add(statusLabel);

        summaryBtn = new JButton("View Route Summary");
        summaryBtn.setEnabled(false); // Disabled until agents finish
        controlPanel.add(summaryBtn);

        add(controlPanel, BorderLayout.EAST);

        // --- LISTENERS ---
        injectBtn.addActionListener(e -> {
            try {
                int x = Integer.parseInt(parcelXField.getText().trim());
                int y = Integer.parseInt(parcelYField.getText().trim());
                myAgent.injectDynamicParcel(new Parcel("Dyn-" + System.currentTimeMillis(), x, y, 1));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid Coordinates");
            }
        });

        summaryBtn.addActionListener(e -> {
            RouteSummaryGui summaryGui = new RouteSummaryGui(myAgent.getInitialPlannedRoutes(), myAgent.getActualDrivenRoutes());
            summaryGui.display();
        });

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setLocationRelativeTo(null);
    }

    public void updateMap(Map<String, Point> locs, Map<String, List<Point>> actual, Map<String, List<Point>> remaining) {
        mapPanel.updateData(locs, actual, remaining);
    }

    public void setAllAgentsIdle() {
        statusLabel.setText("Status: All Agents Idle");
        statusLabel.setForeground(Color.RED);
        summaryBtn.setEnabled(true);
    }
}