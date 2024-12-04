package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private Client client;

    public LoginFrame(Client client) {
        this.client = client;
        setTitle("Login / Register");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Pannello per il form di login
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Impostazioni di base per le Label e TextField
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Zuusmee Login/Register");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(new Color(0, 122, 255));
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(titleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Nickname:"), gbc);

        usernameField = new JTextField();
        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Password:"), gbc);

        passwordField = new JPasswordField();
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        loginButton = new JButton("Login");
        loginButton.setBackground(new Color(76, 175, 80));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String nickname = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                if (password.isEmpty()) {
                    showError("Password cannot be empty!");
                } else {
                    String hashedPassword = Client.hashPassword(password);
                    client.sendMessage("/login " + nickname + " " + hashedPassword);
                }
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(loginButton, gbc);

        JButton registerButton = new JButton("Register");
        registerButton.setBackground(new Color(33, 150, 243));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFont(new Font("Arial", Font.BOLD, 14));
        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String nickname = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                if (password.isEmpty()) {
                    showError("Password cannot be empty!");
                } else {
                    String hashedPassword = Client.hashPassword(password);
                    client.sendMessage("/register " + nickname + " " + hashedPassword);
                }
            }
        });
        gbc.gridx = 1;
        panel.add(registerButton, gbc);

        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Spaziatura attorno ai bordi

        add(panel, BorderLayout.CENTER);
    }

    // Metodo per simulare il click del pulsante login
    public void simulateLogin() {
        loginButton.doClick();
    }

    // Metodo per mostrare un messaggio di errore
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
