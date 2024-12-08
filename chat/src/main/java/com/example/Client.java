package com.example;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import com.example.config.Config;

public class Client implements Runnable {

    private SSLSocket client; // SSLSocket per la connessione sicura
    private BufferedReader in; // Stream per leggere i dati dal server
    private PrintWriter out; // Stream per inviare i dati al server
    @SuppressWarnings("unused")
    private boolean done; // Flag per indicare se il client è in fase di chiusura
    private ClientGUI gui; // Interfaccia grafica del client
    private LoginFrame loginFrame; // Finestra di login
    private boolean isAuthenticated; // Flag per l'autenticazione
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

    // Metodo per connettersi al server tramite SSL
    private void connectToServer() {
        try {
            // Load configuration from config file
            Config config = Config.getInstance();
            String serverIp = config.getServerIp();
            int serverPort = config.getServerPort();

            // Load the client truststore containing trusted certificates
            char[] trustStorePassword = config.getSSLPassword().toCharArray();

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream trustStoreFile = new FileInputStream("SSL-TLS/client.truststore")) {
                trustStore.load(trustStoreFile, trustStorePassword);
            }

            // Create a TrustManagerFactory with the loaded truststore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            // Configure SSLContext with the TrustManager from the truststore
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());

            // Get the SSLSocketFactory and create a secure connection
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            client = (SSLSocket) socketFactory.createSocket(serverIp, serverPort);

            // Initialize streams
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // Show the login window
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    loginFrame.setVisible(true);
                }
            });

            // Read messages from the server
            String inMessage;
            while ((inMessage = in.readLine()) != null) {
                processMessage(inMessage);
            }

        } catch (Exception e) {
            showError("Error connecting to the server.");
            shutdown(); // Close resources
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

            // Aggiungi qui la gestione per chiudere la GUI e il processo
            if (errorMessage.contains("troppi account connessi da questo indirizzo IP")) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        gui.dispose(); // Chiude l'interfaccia grafica
                    }
                });
                System.exit(0); // Termina il processo
            }

        } else if (message.startsWith("/users_list")) {
            processUsersList(message); // Processa la lista degli utenti
        } else if (message.equals("Sessione scaduta. Riaccedere.")) {
            // Gestione sessione scaduta
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    loginFrame.clearPasswordField();
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
                new Object[]{"Chiudi"}, // Aggiungi il pulsante "Chiudi"
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
        } catch (Exception e) {
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
        clientThread.start(); // Avvia il client
    }
}
