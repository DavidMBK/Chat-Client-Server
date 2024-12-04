package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientGUI extends JFrame {

    private JTextField input;
    private JTextArea chat;
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

        chat = new JTextArea();
        chat.setEditable(false);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        chat.setBackground(new Color(245, 245, 245));
        chat.setForeground(Color.BLACK);
        JScrollPane chatScrollPane = new JScrollPane(chat);

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
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Arial", Font.PLAIN, 14));
        userList.setBackground(new Color(245, 245, 245));
        userList.setForeground(Color.BLACK);
        userList.setSelectionBackground(new Color(0, 132, 255));
        userList.setSelectionForeground(Color.WHITE);

        // Pannello per gli utenti
        JPanel userListPanel = new JPanel(new BorderLayout());
        JLabel userListLabel = new JLabel("Utenti");
        userListLabel.setFont(new Font("Arial", Font.BOLD, 14));
        userListLabel.setHorizontalAlignment(JLabel.CENTER);
        userListLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        userListPanel.add(userListLabel, BorderLayout.NORTH);
        userListPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        userListPanel.setPreferredSize(new Dimension(200, 0));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(titleLabel, BorderLayout.NORTH); // Aggiunge il titolo grande in alto
        getContentPane().add(chatScrollPane, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
        getContentPane().add(userListPanel, BorderLayout.EAST); // Aggiunge il pannello degli utenti a destra

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
            client.sendMessage(message);
            input.setText("");
        }
    }

    public void appendMessage(String message) {
        chat.append(message + "\n");
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
        chat.setText("");
    }

    public static void main(String[] args) {
        Client client = new Client();
        ClientGUI gui = new ClientGUI(client);
        gui.setVisible(true);
    }
}
