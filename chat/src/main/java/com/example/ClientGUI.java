package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientGUI extends JFrame {

    private JTextField input;
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private Client client;

    public ClientGUI(final Client client) {
        this.client = client;
        setTitle("Zuusmee - Telegram Style");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Titolo grande dell'app
        JLabel titleLabel = new JLabel("Zuusmee", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(new Color(0, 132, 255));
        titleLabel.setForeground(Color.WHITE);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(245, 245, 245));

        chatScrollPane = new JScrollPane(chatPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        input = new JTextField();
        input.setFont(new Font("Arial", Font.PLAIN, 14));
        input.setBackground(new Color(255, 255, 255));
        input.setForeground(Color.BLACK);
        input.setBorder(BorderFactory.createCompoundBorder(
            input.getBorder(),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        input.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        JButton send = new JButton("Invia");
        send.setFont(new Font("Arial", Font.BOLD, 14));
        send.setBackground(new Color(0, 132, 255));
        send.setForeground(Color.WHITE);
        send.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        send.setFocusPainted(false);
        send.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Arial", Font.PLAIN, 14));
        userList.setBackground(new Color(245, 245, 245));
        userList.setForeground(Color.BLACK);
        userList.setSelectionBackground(new Color(0, 132, 255));
        userList.setSelectionForeground(Color.WHITE);

        JPanel userListPanel = new JPanel(new BorderLayout());
        userListPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel userListLabel = new JLabel("Utenti");
        userListLabel.setFont(new Font("Arial", Font.BOLD, 14));
        userListLabel.setHorizontalAlignment(JLabel.CENTER);
        userListLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        userListPanel.add(userListLabel, BorderLayout.NORTH);
        userListPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        userListPanel.setPreferredSize(new Dimension(200, 0));

        // Aggiunta dei pulsanti "Modifica Password" e "Modifica Nome"
        JButton changePasswordButton = new JButton("Modifica Password");
        changePasswordButton.setFont(new Font("Arial", Font.BOLD, 14));
        changePasswordButton.setBackground(new Color(0, 132, 255));
        changePasswordButton.setForeground(Color.WHITE);
        changePasswordButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        changePasswordButton.setFocusPainted(false);
        changePasswordButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changePassword();
            }
        });

        JButton changeNameButton = new JButton("Modifica Nome");
        changeNameButton.setFont(new Font("Arial", Font.BOLD, 14));
        changeNameButton.setBackground(new Color(0, 132, 255));
        changeNameButton.setForeground(Color.WHITE);
        changeNameButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        changeNameButton.setFocusPainted(false);
        changeNameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeName();
            }
        });

        JPanel userOptionsPanel = new JPanel();
        userOptionsPanel.setLayout(new BoxLayout(userOptionsPanel, BoxLayout.Y_AXIS));
        userOptionsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        userOptionsPanel.add(changePasswordButton);
        userOptionsPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spaziatura
        userOptionsPanel.add(changeNameButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(titleLabel, BorderLayout.NORTH);
        getContentPane().add(chatScrollPane, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
        getContentPane().add(userListPanel, BorderLayout.EAST);
        getContentPane().add(userOptionsPanel, BorderLayout.WEST);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.sendMessage("/disconnect");
                super.windowClosing(e);
            }
        });
    }

    private void sendMessage() {
        String message = input.getText().trim();
        if (!message.isEmpty()) {
            appendMessage(message, true);
            client.sendMessage(message);
            input.setText("");
        }
    }

    private void changePassword() {
        String newPassword = JOptionPane.showInputDialog(
            this,
            "Inserisci la nuova password:",
            "Modifica Password",
            JOptionPane.PLAIN_MESSAGE
        );

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            client.sendMessage("/change_password " + newPassword.trim());
        } else {
            JOptionPane.showMessageDialog(
                this,
                "La password non può essere vuota.",
                "Errore",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void changeName() {
        String newName = JOptionPane.showInputDialog(
            this,
            "Inserisci il nuovo nome:",
            "Modifica Nome",
            JOptionPane.PLAIN_MESSAGE
        );

        if (newName != null && !newName.trim().isEmpty()) {
            client.sendMessage("/change_name " + newName.trim());
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Il nome non può essere vuoto.",
                "Errore",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public void appendMessage(String message, boolean sentByUser) {
        MessageBubble bubble = new MessageBubble(message, sentByUser);
        chatPanel.add(bubble);
        chatPanel.revalidate();
        chatScrollPane.getVerticalScrollBar().setValue(chatScrollPane.getVerticalScrollBar().getMaximum());
    }

    public void updateUsersList(final String[] users) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                userListModel.clear();
                for (String user : users) {
                    userListModel.addElement(user);
                }
            }
        });
    }

    public void removeInstructions() {
        chatPanel.removeAll();
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    public static void main(String[] args) {
        Client client = new Client();
        ClientGUI gui = new ClientGUI(client);
        gui.setVisible(true);
    }
}
