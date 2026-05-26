package RoutingAgent.Extension.RoutingAgent;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class DeliveryAgentGui extends JFrame {
    private final DeliveryAgent myAgent;
    private JTextField capacityField;

    public DeliveryAgentGui(DeliveryAgent a) {
        super(a.getLocalName() + " Configuration");
        myAgent = a;

        JPanel p = new JPanel(new GridLayout(1, 2, 10, 10));
        p.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JLabel("Set Max Capacity:"));
        capacityField = new JTextField("5");
        p.add(capacityField);
        getContentPane().add(p, BorderLayout.CENTER);

        JButton setButton = new JButton("Confirm Capacity");
        setButton.addActionListener(ev -> {
            try {
                int capacity = Integer.parseInt(capacityField.getText().trim());
                if (capacity <= 0) throw new NumberFormatException();

                myAgent.updateCapacity(capacity);
                dispose();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid positive number.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel s = new JPanel();
        s.add(setButton);
        getContentPane().add(s, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { myAgent.doDelete(); }
        });

        setResizable(false);
        pack();

        // Center on screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((int)screenSize.getWidth()/2 - getWidth()/2, (int)screenSize.getHeight()/2 - getHeight()/2);
    }
}