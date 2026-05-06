package RoutingAgent.Extension.RoutingAgent;

import RoutingAgent.Extension.Solver.Parcel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MRAGui extends JFrame {
    private final MasterRoutingAgent myAgent;
    private String selectedFilePath = "RANDOM";

    // Dynamic Panels (Disabled until system initializes)
    private JPanel dynamicParcelPanel;
    private JPanel dynamicAgentPanel;

    public MRAGui(MasterRoutingAgent a) {
        super("MRA Enterprise Control Panel - Extension 2");
        myAgent = a;
        setLayout(new BorderLayout(10, 10));

        // ==========================================
        // PHASE 1: INITIAL SETUP (From Basic Version)
        // ==========================================
        JPanel initPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        initPanel.setBorder(BorderFactory.createTitledBorder("Phase 1: Initial Fleet & Map Setup"));

        initPanel.add(new JLabel(" Initial Customers (Random):"));
        JTextField customersField = new JTextField("20");
        initPanel.add(customersField);

        initPanel.add(new JLabel(" Initial Delivery Agents:"));
        JTextField agentsField = new JTextField("3");
        initPanel.add(agentsField);

        // File Selection
        JPanel fileButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton browseButton = new JButton("Load File");
        JButton clearButton = new JButton("Clear File");
        clearButton.setEnabled(false);
        fileButtonsPanel.add(browseButton);
        fileButtonsPanel.add(Box.createHorizontalStrut(5));
        fileButtonsPanel.add(clearButton);

        JLabel fileLabel = new JLabel("Mode: Random Generation");

        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File("."));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    List<String> lines = Files.readAllLines(selectedFile.toPath());
                    int nodeCount = (int) lines.stream().filter(l -> !l.trim().isEmpty()).count();

                    selectedFilePath = selectedFile.getAbsolutePath();
                    fileLabel.setText("File: " + selectedFile.getName());
                    customersField.setText(String.valueOf(nodeCount));
                    customersField.setEnabled(false);
                    clearButton.setEnabled(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to read file.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        clearButton.addActionListener(e -> {
            selectedFilePath = "RANDOM";
            fileLabel.setText("Mode: Random Generation");
            customersField.setEnabled(true);
            customersField.setText("20");
            clearButton.setEnabled(false);
        });

        initPanel.add(fileButtonsPanel);
        initPanel.add(fileLabel);

        JButton startButton = new JButton("Initialize System & Calculate Initial Routes");

        // ==========================================
        // PHASE 2: DYNAMIC CONTROLS
        // ==========================================
        JPanel bottomContainer = new JPanel(new BorderLayout(5, 5));

        // Parcel Injection
        dynamicParcelPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        dynamicParcelPanel.setBorder(BorderFactory.createTitledBorder("Phase 2: Inject Dynamic Rush Order"));
        JTextField parcelIdField = new JTextField("RUSH-01");
        JTextField parcelXField = new JTextField("70");
        JTextField parcelYField = new JTextField("30");
        JButton injectParcelBtn = new JButton("Inject Parcel Now");
        dynamicParcelPanel.add(new JLabel(" ID:")); dynamicParcelPanel.add(parcelIdField);
        dynamicParcelPanel.add(new JLabel(" X, Y:"));
        JPanel xyPanel = new JPanel(new GridLayout(1,2)); xyPanel.add(parcelXField); xyPanel.add(parcelYField);
        dynamicParcelPanel.add(xyPanel);
        dynamicParcelPanel.add(new JLabel("")); dynamicParcelPanel.add(injectParcelBtn);

        // Standby Agent & Summary Spawning
        dynamicAgentPanel = new JPanel(new GridLayout(3, 2, 5, 5)); // Changed to 3 rows
        dynamicAgentPanel.setBorder(BorderFactory.createTitledBorder("Phase 2: Deploy Standby Agent"));
        JTextField standbyNameField = new JTextField("DA_Backup");
        JButton spawnAgentBtn = new JButton("Deploy Agent");
        JButton summaryBtn = new JButton("End of Day Summary"); // NEW BUTTON

        dynamicAgentPanel.add(new JLabel(" Agent Name:")); dynamicAgentPanel.add(standbyNameField);
        dynamicAgentPanel.add(new JLabel("")); dynamicAgentPanel.add(spawnAgentBtn);
        dynamicAgentPanel.add(new JLabel(" Route History:")); dynamicAgentPanel.add(summaryBtn); // NEW ROW

        bottomContainer.add(dynamicParcelPanel, BorderLayout.NORTH);
        bottomContainer.add(dynamicAgentPanel, BorderLayout.SOUTH);

        // --- Action Listeners ---
        startButton.addActionListener(e -> {
            try {
                int numCustomers = Integer.parseInt(customersField.getText().trim());
                int numAgents = Integer.parseInt(agentsField.getText().trim());

                myAgent.initializeSystem(numCustomers, numAgents, selectedFilePath);

                startButton.setEnabled(false);
                customersField.setEnabled(false);
                agentsField.setEnabled(false);
                browseButton.setEnabled(false);
                setDynamicControlsEnabled(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Use valid numbers.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        injectParcelBtn.addActionListener(e -> {
            try {
                int x = Integer.parseInt(parcelXField.getText().trim());
                int y = Integer.parseInt(parcelYField.getText().trim());
                myAgent.injectDynamicParcel(new Parcel(parcelIdField.getText().trim(), x, y, 1));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid Coordinates", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        spawnAgentBtn.addActionListener(e -> {
            myAgent.spawnDynamicAgent(standbyNameField.getText().trim(), 50, 50, 5);
        });

        summaryBtn.addActionListener(e -> {
            RouteSummaryGui summaryGui = new RouteSummaryGui(myAgent.getInitialPlannedRoutes(), myAgent.getActualDrivenRoutes());
            summaryGui.display();
        });

        add(initPanel, BorderLayout.NORTH);
        add(startButton, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void setDynamicControlsEnabled(boolean enabled) {
        for (Component c : dynamicParcelPanel.getComponents()) c.setEnabled(enabled);
        for (Component c : dynamicAgentPanel.getComponents()) c.setEnabled(enabled);
    }

    public void display() {
        setVisible(true);
    }

    public void unlockPhase2() {
        setDynamicControlsEnabled(true);
    }
}