package com.example;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.config.Config;

public class Server implements Runnable {

    private CopyOnWriteArrayList<ConnectionHandler> connections; // Lista per contenere le connessioni attive dei client
    private ServerSocket server; // Socket server
    private boolean done; // Variabile booleana che indica lo stato del server
    private ExecutorService executor; // Gestisce l'esecuzione dei thread dei client

    public Server() {
        connections = new CopyOnWriteArrayList<>();
        done = false;
        // Creazione di un pool di thread per gestire le connessioni dei client
        executor = Executors.newCachedThreadPool();
    }

    public void run() {
        try {
            // Ottenimento la configurazione del server
            Config config = Config.getInstance();
            int serverPort = config.getServerPort();

            server = new ServerSocket(serverPort); // Creazione del socket del server in ascolto sulla porta specificata

            while (!done) {
                Socket client = server.accept();

                // Creazione di un oggetto Client per memorizzare le informazioni del client
                Client clientInfo = new Client();

                // Creazione di un gestore di connessione per il nuovo client
                ConnectionHandler handler = new ConnectionHandler(client, clientInfo);

                connections.add(handler); // Aggiunta del gestore di connessione alla lista delle connessioni attive
                executor.execute(handler); // Esecuzione del gestore di connessione in un thread separato
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    // Metodo per inviare un messaggio a tutti i client connessi
    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch.isAuthenticated()) {
                ch.sendMessage(message);
            }
        }
    }

    // Metodo per aggiornare la lista degli utenti connessi
    public void updateUsersList() {
        Set<String> uniqueUsernames = new HashSet<>();
        for (ConnectionHandler ch : connections) {
            if (ch.isAuthenticated()) {
                uniqueUsernames.add(ch.getClientInfo().getNickname());
            }
        }

        String usersList = "/users_list " + String.join(",", uniqueUsernames);
        broadcast(usersList);
    }

    // Metodo per terminare il server
    public void shutdown() {
        done = true;
        try {
            if (server != null && !server.isClosed()) {
                server.close();
            }
            executor.shutdown();
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Classe interna per gestire le connessioni dei client
    class ConnectionHandler implements Runnable {

        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private Client clientInfo;

        // Costruttore che inizializza i componenti per la gestione della connessione
        public ConnectionHandler(Socket client, Client clientInfo) {
            this.client = client;
            this.clientInfo = clientInfo;
        }

        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                String message;
                while ((message = in.readLine()) != null) {
                    if (!clientInfo.isAuthenticated()) {
                        if (message.startsWith("/login ")) {
                            handleLogin(message);
                        } else if (message.startsWith("/register ")) {
                            handleRegister(message);
                        } else {
                            out.println("You must login/register first.");
                        }
                    } else {
                        if (message.equals("/disconnect")) {
                            handleDisconnect();
                        } else {
                            broadcast(clientInfo.getNickname() + ": " + message);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                shutdown();
            }
        }

        // Metodo per gestire il login
        private synchronized void handleLogin(String message) {
            String[] parts = message.split("\\s+", 3); // \\s+ serve per usare lo spazio per delimitare.
            if (parts.length != 3) {
                out.println("Formato di login non valido. Utilizzo: /login <Nickname> <Password>");
                return;
            }
            String nickname = parts[1]; // [0] = /login
            String password = parts[2];

            if (validateLogin(nickname, password)) {
                clientInfo.setAuthenticated(true);
                clientInfo.setNickname(nickname);
                out.println("/login_success");
                updateUsersList();
            }
        }

        // Metodo per gestire la registrazione
        private synchronized void handleRegister(String message) {
            String[] parts = message.split("\\s+", 3);
            if (parts.length != 3) {
                out.println("Formato di registrazione non valido. Utilizzo: /register <Nickname> <Password>");
                return;
            }
            String nickname = parts[1];
            String password = parts[2];

            if (registerUser(nickname, password)) {
                out.println("/register_success");
            }
        }

        // Metodo per registrare un nuovo utente nel database
        private boolean registerUser(String nickname, String password) {
            String sql = "INSERT INTO Account (Nickname, Password) VALUES (?, ?)";
            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                // SQL ANTI-INJECTION
                stmt.setString(1, nickname);
                stmt.setString(2, password);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        // Metodo per validare il login, se è giusto
        private boolean validateLogin(String nickname, String password) {
            String sql = "SELECT Password FROM Account WHERE Nickname = ?";
            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nickname);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    // Guarda la password di quell'utente
                    String storedPassword = rs.getString("Password");
                    return storedPassword.equals(password); // ritorna True
                }
            } catch (SQLException e) {
                e.printStackTrace();
                shutdown();
            }
            return false;
        }

        // Metodo per inviare un messaggio al client
        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Metodo per verificare se il client è autenticato
        public boolean isAuthenticated() {
            return clientInfo.isAuthenticated();
        }

        // Metodo per ottenere le informazioni sul client
        public Client getClientInfo() {
            return clientInfo;
        }

        private synchronized void handleDisconnect() {
            connections.remove(this);
            updateUsersList();
            shutdown();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        Thread serverThread = new Thread(server);
        serverThread.start();
    }
}
