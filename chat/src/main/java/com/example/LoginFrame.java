package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    @SuppressWarnings("unused")
    private Client client;

    public LoginFrame(Client client) {
        this.client = client;
        setTitle("Zuusmee");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Pannello per il form di login
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Pannello per il titolo centrato
        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Zuusmee", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(new Color(0, 122, 255));
        titlePanel.add(titleLabel, BorderLayout.CENTER);

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(titlePanel, gbc);

        // Impostazioni di base per le Label e TextField
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
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
                    showError("La password non può essere vuota!");
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

                // Controllo che la password non sia vuota
                if (password.isEmpty()) {
                    showError("La password non può essere vuota!");
                    return;
                }

                // Controllo sulla lunghezza della password
                if (password.length() < 8) {
                    showError("La password deve essere lunga almeno 8 caratteri.");
                    return;
                }

                // Controllo per verificare che la password contenga almeno una lettera
                // maiuscola, una minuscola, un numero e un carattere speciale
                String passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,}$";
                if (!password.matches(passwordPattern)) {
                    showError(
                            "La password deve contenere almeno una lettera maiuscola, una lettera minuscola, un numero e un carattere speciale.");
                    return;
                }

                // Se tutto è ok, invia il messaggio al server
                String hashedPassword = Client.hashPassword(password);
                client.sendMessage("/register " + nickname + " " + hashedPassword);
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

    public void clearPasswordField() {
        passwordField.setText(""); // Svuota il campo della password
    }
}
