package RoutingAgent;

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

class DeliveryAgentGui extends JFrame {

    private final DeliveryAgent myAgent;
    private JTextField capacityField;

    DeliveryAgentGui(DeliveryAgent a) {
        super(a.getLocalName());
        myAgent = a;

        JPanel p = new JPanel();
        p.setLayout(new GridLayout(1, 2));
        p.add(new JLabel("Capacity:"));
        capacityField = new JTextField(15);
        p.add(capacityField);
        getContentPane().add(p, BorderLayout.CENTER);

        JButton setButton = new JButton("Set Capacity");
        setButton.addActionListener(ev -> {
            try {
                String text = capacityField.getText().trim();
                int capacity = Integer.parseInt(text);

                myAgent.updateCapacity(capacity);
                dispose();   // close the GUI after submitting
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        DeliveryAgentGui.this,
                        "Invalid capacity. Please enter a valid number.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        p = new JPanel();
        p.add(setButton);
        getContentPane().add(p, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                myAgent.doDelete();
            }
        });

        setResizable(false);
    }

    public void show() {
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int centerX = (int) screenSize.getWidth() / 2;
        int centerY = (int) screenSize.getHeight() / 2;
        setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
        super.show();
    }
}