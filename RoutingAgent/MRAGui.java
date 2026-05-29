package RoutingAgent.RoutingAgent;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class MRAGui extends JFrame {
    private final MasterRoutingAgent myAgent;
    private String selectedFilePath = "RANDOM";
    private int calculatedDemand = 20; // Default matches default customers

    public MRAGui(MasterRoutingAgent a) {
        super("MRA System Configuration");
        myAgent = a;

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Row 1: Customers
        panel.add(new JLabel("Number of Customers (Nodes):"));
        JTextField customersField = new JTextField("20");
        panel.add(customersField);

        // Row 2: Agents
        panel.add(new JLabel("Number of Delivery Agents:"));
        JTextField agentsField = new JTextField("3");
        panel.add(agentsField);

        // Row 3: Algorithm Selection
        panel.add(new JLabel("Optimization Algorithm:"));
        String[] algorithms = {"Genetic Algorithm (GA)", "Tabu Search"};
        JComboBox<String> algoDropdown = new JComboBox<>(algorithms);
        panel.add(algoDropdown);

        // Row 4: File Selection & Clear Button
        JPanel fileButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton browseButton = new JButton("Load File");
        JButton clearButton = new JButton("Clear File");
        clearButton.setEnabled(false); // Disabled by default

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
                    int nodeCount = 0;
                    int demandSum = 0;
                    List<String> lines = Files.readAllLines(selectedFile.toPath());

                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            nodeCount++;
                            String[] parts = line.split(",");
                            // SMART PARSING: Reads the demand column (x, y, demand)
                            if (parts.length >= 3) {
                                demandSum += Integer.parseInt(parts[2].trim());
                            } else {
                                demandSum += 1; // Fallback
                            }
                        }
                    }

                    selectedFilePath = selectedFile.getAbsolutePath();
                    calculatedDemand = demandSum;
                    fileLabel.setText("File: " + selectedFile.getName());

                    customersField.setText(String.valueOf(nodeCount));
                    customersField.setEnabled(false);
                    clearButton.setEnabled(true);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to read the file.", "File Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Cancel File Read
        clearButton.addActionListener(e -> {
            selectedFilePath = "RANDOM";
            fileLabel.setText("Mode: Random Generation");
            customersField.setEnabled(true);
            customersField.setText("20");
            calculatedDemand = 20; // Reset to default
            clearButton.setEnabled(false);
        });

        panel.add(fileButtonsPanel);
        panel.add(fileLabel);

        // Row 5: Start
        JButton startButton = new JButton("Initialize Fleet & Start");
        panel.add(new JLabel(""));
        panel.add(startButton);

        startButton.addActionListener(e -> {
            try {
                int numCustomers = Integer.parseInt(customersField.getText().trim());
                int numAgents = Integer.parseInt(agentsField.getText().trim());
                if (numCustomers <= 0 || numAgents <= 0) throw new NumberFormatException();

                // If random mode, assume 1 demand per customer
                if (selectedFilePath.equals("RANDOM")) {
                    calculatedDemand = numCustomers;
                }

                String selectedAlgo = (String) algoDropdown.getSelectedItem();

                // Pass the new selected algorithm to the MRA
                myAgent.startSystem(numCustomers, numAgents, selectedFilePath, calculatedDemand, selectedAlgo);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numbers.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        getContentPane().add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }
}