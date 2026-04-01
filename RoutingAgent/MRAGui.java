package RoutingAgent.RoutingAgent;

import javax.swing.*;
import java.awt.*;

public class MRAGui extends JFrame {
    private final MasterRoutingAgent myAgent;

    public MRAGui(MasterRoutingAgent a) {
        super("MRA System Configuration");
        myAgent = a;

        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(new JLabel("Number of Customers (Nodes):"));
        JTextField customersField = new JTextField("20");
        panel.add(customersField);

        panel.add(new JLabel("Number of Delivery Agents:"));
        JTextField agentsField = new JTextField("4");
        panel.add(agentsField);

        JButton startButton = new JButton("Initialize Fleet & Start");
        panel.add(new JLabel("")); // Spacer
        panel.add(startButton);

        startButton.addActionListener(e -> {
            try {
                int numCustomers = Integer.parseInt(customersField.getText().trim());
                int numAgents = Integer.parseInt(agentsField.getText().trim());

                if (numCustomers <= 0 || numAgents <= 0) {
                    throw new NumberFormatException();
                }

                // Send data to MRA and close GUI
                myAgent.startSystem(numCustomers, numAgents);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid positive numbers.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        getContentPane().add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null); // Center on screen
        setResizable(false);
    }
}