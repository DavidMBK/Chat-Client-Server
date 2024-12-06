package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.example.config.Config;

public class Client implements Runnable {

    private Socket client; // Socket utilizzato per la connessione con il server
    private BufferedReader in; // Stream per leggere i dati dal server
    private PrintWriter out; // Stream per inviare i dati al server
    private boolean done; // Flag per indicare se il client è in fase di chiusura
    private ClientGUI gui; // Riferimento all'interfaccia grafica del client
    private LoginFrame loginFrame; // Finestra di login
    private boolean isAuthenticated; // Flag per indicare se l'utente è autenticato
    private String nickname; // Nickname dell'utente

    public Client() {
        gui = new ClientGUI(this); // Inizializza l'interfaccia grafica del client
        loginFrame = new LoginFrame(this); // Inizializza la finestra di login
        isAuthenticated = false; // Imposta l'autenticazione a false
        nickname = ""; // Imposta il nickname vuoto
    }

    public void run() {
        connectToServer();
    }

    // Metodo per connettersi al server
    private void connectToServer() {
        try {
            // Carica la configurazione dal file di configurazione
            Config config = Config.getInstance();
            String serverIp = config.getServerIp();
            int serverPort = config.getServerPort();

            // Connessione al server
            client = new Socket(serverIp, serverPort);
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // Mostra la finestra di login
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    loginFrame.setVisible(true);
                }
            });

            // Legge i messaggi dal server
            String inMessage;
            while ((inMessage = in.readLine()) != null) {
                processMessage(inMessage);
            }

        } catch (IOException e) {
            showError("Error connecting to the server.");
            shutdown(); // Chiude le risorse
        }
    }

    // Metodo per processare i messaggi ricevuti dal server
    private void processMessage(String message) {
        if (message.startsWith("/login_success")) {
            isAuthenticated = true;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    loginFrame.setVisible(false); // Nasconde la finestra di login
                    gui.setVisible(true); // Mostra la finestra principale del client
                    gui.appendMessage("Benvenuto su Zuusmee, " + getNickname() + "!", false);
                }
            });
        } else if (message.startsWith("/register_success")) {
            // Simula il click del pulsante login dopo la registrazione
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    loginFrame.simulateLogin();
                }
            });
        } else if (message.startsWith("/error")) {
            // Gestisce i messaggi di errore per login o registrazione
            String errorMessage = message.replace("/error ", "");
            showError(errorMessage);
        } else if (message.startsWith("/users_list")) {
            processUsersList(message); // Processa la lista degli utenti
        } else if (message.equals("Sessione scaduta. Riaccedere.")) {
            // Gestione sessione scaduta
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    gui.setVisible(false); // Nasconde la finestra di chat
                    showError("Sessione scaduta. Riaccedere.");
                }
            });

            // Riconnessione: resetta e avvia di nuovo il ciclo
            resetConnection();
        } else {
            gui.appendMessage(message, false); // Aggiunge qualsiasi altro messaggio alla chat
        }
    }

    // Metodo per processare la lista degli utenti
    private void processUsersList(String message) {
        String[] users = message.replace("/users_list ", "").split(","); // Splitta il messaggio per trovare l'user
        Set<String> uniqueUsers = new HashSet<>(Arrays.asList(users)); // Usa un set per rimuovere duplicati
        gui.updateUsersList(uniqueUsers.toArray(new String[0])); // Aggiorna la lista degli utenti nella GUI
    }

    // Metodo per chiudere le risorse
    public void shutdown() {
        done = true;
        try {
            if (in != null) {
                in.close(); // Chiusura risorse lettura
            }
            if (out != null) {
                out.close(); // Chiusura risorse scrittura
            }
            if (client != null && !client.isClosed()) {
                client.close(); // Chiude il client
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Metodo per inviare un messaggio al server
    public void sendMessage(String messageText) {
        if (out != null) {
            out.println(messageText);
        }
    }

    // Metodo per mostrare un messaggio di errore tramite gui
    private void showError(String message) {
        // Crea una finestra di dialogo separata per gli errori di password
        JOptionPane optionPane = new JOptionPane(
                message,
                JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[] { "Chiudi" }, // Aggiungi il pulsante "Chiudi"
                null);

        // Crea un JDialog separato per l'errore di password
        JDialog dialog = optionPane.createDialog(gui, "Errore Cambio Password");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }

    // Getter per l'autenticazione
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    // Setter per l'autenticazione
    public void setAuthenticated(boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }

    // Getter per il nickname
    public String getNickname() {
        return nickname;
    }

    // Setter per il nickname
    public void setNickname(String nickname) {
        this.nickname = nickname.toLowerCase(); // Lowercase per chi accede
    }

    // Metodo per hashare una password con SHA-256
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // Metodo per resettare la connessione (riavvia il processo da capo)
    private void resetConnection() {
        shutdown(); // Chiude la connessione corrente
        isAuthenticated = false; // Reset dell'autenticazione
        run(); // Riconnette e riavvia tutto
    }

    public static void main(String[] args) {
        final Client client = new Client();
        // Avvia l'interfaccia grafica nel thread dell'Event Dispatch Thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                client.loginFrame.setVisible(true); // Mostra la finestra di login
            }
        });
        Thread clientThread = new Thread(client); // Crea un thread per eseguire il client
        clientThread.start(); // Avvia il thread del client
    }
}
