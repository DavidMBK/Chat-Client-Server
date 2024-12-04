package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientGUI extends JFrame {

    private JTextField input; // Campo di testo per inserire i messaggi
    private JTextArea chat; // Area di testo per visualizzare la chat
    private JList<String> userList; // Lista per visualizzare gli utenti
    private DefaultListModel<String> userListModel; // Modello per la lista degli utenti
    private Client client; // Istanza del client

    // Costruttore della classe ClientGUI
    public ClientGUI(final Client client) {
        this.client = client; // Assegna il client passato al costruttore
        setTitle("Zuusmee"); // Imposta il titolo della finestra
        setSize(500, 400); // Imposta la dimensione della finestra
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Imposta l'operazione di chiusura
        setLocationRelativeTo(null); // Posiziona la finestra al centro dello schermo

        // Inizializza l'area di testo per la chat e la rende non editabile
        chat = new JTextArea();
        chat.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chat); // Aggiunge una barra di scorrimento alla chat

        // Inizializza il campo di testo per l'input dei messaggi
        input = new JTextField();
        input.addActionListener(new ActionListener() { // Aggiunge un'azione quando si preme l'invio
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Crea il pulsante invia
        JButton send = new JButton("Invia");
        send.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Pannello con Campo di input
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(input, BorderLayout.CENTER); // Aggiunge il testo input al centro
        bottom.add(send, BorderLayout.EAST); // Aggiunge il pulsante a destra

        // Inizializza il modello della lista degli utenti
        // Per vedere online/offline
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel); // Inizializza la lista degli utenti con il modello
        JScrollPane userScrollPane = new JScrollPane(userList); // barra per scrollare
        userScrollPane.setPreferredSize(new Dimension(150, 0)); // Default barra size

        // Imposta il layout del contenitore principale
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(chatScrollPane, BorderLayout.CENTER); // Aggiunge la chat al centro
        getContentPane().add(bottom, BorderLayout.SOUTH); // Aggiunge il pannello inferiore in basso
        getContentPane().add(userScrollPane, BorderLayout.EAST); // Aggiunge la lista degli utenti a destra

        // Chiude le finestre
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                final Client finalClient = client; // Dichiarazione final per la variabile client
                finalClient.sendMessage("/disconnect"); // Invia il messaggio di disconnessione
                super.windowClosing(e); // Chiama il metodo della superclasse
            }
        });
    }

    // Metodo per inviare un messaggio
    private void sendMessage() {
        String message = input.getText().trim(); // Ottiene il testo dal campo di input e rimuove gli spazi
        if (!message.isEmpty()) { // Controlla se il messaggio non è vuoto

            // Invia il messaggio al client
            client.sendMessage(message);
            // Resetta il campo di input
            input.setText("");
        }
    }

    // Metodo per aggiungere un messaggio alla chat
    public void appendMessage(String message) {
        chat.append(message + "\n"); // Aggiunge il messaggio alla chat
    }

    // Metodo per aggiornare la lista degli utenti
    public void updateUsersList(final String[] users) {
        SwingUtilities.invokeLater(new Runnable() // aggiorna la interfaccia grafica ad ogni modifica
        {
            @Override
            public void run() {
                userListModel.clear(); // Pulisce il modello della lista
                for (String user : users) {
                    userListModel.addElement(user); // Aggiunge ogni utente al modello della lista
                }
            }
        });
    }

    // Metodo per rimuovere le istruzioni dalla chat
    public void removeInstructions() {
        chat.setText("");
    }

    public static void main(String[] args) {
        Client client = new Client(); // Crea una nuova istanza del client
        ClientGUI gui = new ClientGUI(client); // Crea una nuova istanza della GUI
        gui.setVisible(true); // Rende visibile la GUI
    }
}
