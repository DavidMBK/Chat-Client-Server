package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.example.config.Config;

public class Server implements Runnable {

    private CopyOnWriteArrayList<ConnectionHandler> connections; // Lista delle connessioni attive
    private ServerSocket server; // Socket server
    private boolean done; // Indica lo stato del server
    private ExecutorService executor; // Pool di thread per gestire le connessioni

    public Server() {
        connections = new CopyOnWriteArrayList<>();
        done = false;
        executor = Executors.newCachedThreadPool(); // Pool dinamico
    }

    public void run() {
        try {
            Config config = Config.getInstance();
            int serverPort = config.getServerPort(); // Ottieni la porta dal file di configurazione

            server = new ServerSocket(serverPort);
            System.out.println("Server started on port " + serverPort);

            while (!done) {
                Socket client = server.accept(); // Accetta nuove connessioni

                Client clientInfo = new Client();
                int inactivityTimeout = 15; // Timeout configurabile

                // Crea un gestore per il client
                ConnectionHandler handler = new ConnectionHandler(client, clientInfo, inactivityTimeout);
                connections.add(handler); // Aggiungi alla lista delle connessioni
                executor.execute(handler); // Esegui il gestore in un thread separato
            }
        } catch (IOException e) {
            if (!done) {
                System.err.println("Error starting server: " + e.getMessage());
            }
            shutdown();
        }
    }

    // Invia un messaggio a tutti gli utenti autenticati
    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch.isAuthenticated()) {
                ch.sendMessage(message);
            }
        }
    }

    // Aggiorna la lista degli utenti connessi
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

    // Arresta il server
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
            System.out.println("Server shut down.");
        } catch (IOException e) {
            System.err.println("Error shutting down server: " + e.getMessage());
        }
    }

    // Classe interna per gestire la connessione di un singolo client
    class ConnectionHandler implements Runnable {

        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private Client clientInfo;
        private ScheduledExecutorService scheduler;
        private ScheduledFuture<?> timeoutFuture;
        private Runnable timeoutTask;
        private int inactivityTimeout;

        public ConnectionHandler(Socket client, Client clientInfo, int inactivityTimeout) {
            this.client = client;
            this.clientInfo = clientInfo;
            this.inactivityTimeout = inactivityTimeout;
            this.scheduler = new ScheduledThreadPoolExecutor(1);

            // Task per disconnettere l'utente inattivo
            this.timeoutTask = () -> {
                try {
                    if (clientInfo.isAuthenticated()) {
                        out.println("Sessione scaduta. Riaccedere.");
                    }
                    System.out.println("User " + clientInfo.getNickname() + " inactive for " + inactivityTimeout + " seconds. Disconnecting...");
                    shutdown(); // Disconnette solo questo client
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

        }

        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                // Pianifica il primo timeout
                timeoutFuture = scheduler.schedule(timeoutTask, inactivityTimeout, TimeUnit.SECONDS);

                String message;
                while ((message = in.readLine()) != null) {
                    // Reset del timer di inattività
                    timeoutFuture.cancel(false);
                    timeoutFuture = scheduler.schedule(timeoutTask, inactivityTimeout, TimeUnit.SECONDS);

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
                System.err.println("Connection error with client: " + e.getMessage());
            } finally {
                shutdown(); // Assicura la chiusura della connessione
            }
        }

        // Gestione del login
        private synchronized void handleLogin(String message) {
            String[] parts = message.split("\\s+", 3);
            if (parts.length != 3) {
                out.println("Invalid login format. Use: /login <Nickname> <Password>");
                return;
            }
            String nickname = parts[1];
            String password = parts[2];

            if (validateLogin(nickname, password)) {
                clientInfo.setAuthenticated(true);
                clientInfo.setNickname(nickname);
                out.println("/login_success");
                updateUsersList();
            } else {
                out.println("/error Nome utente o password errati.");
            }
        }

        // Gestione della registrazione
        private synchronized void handleRegister(String message) {
            String[] parts = message.split("\\s+", 3);
            if (parts.length != 3) {
                out.println("Invalid register format. Use: /register <Nickname> <Password>");
                return;
            }
            String nickname = parts[1];
            String password = parts[2];

            if (registerUser(nickname, password)) {
                out.println("/register_success");
            } else {
                out.println("Registration failed.");
            }
        }

        // Registra un nuovo utente nel database
        private boolean registerUser(String nickname, String password) {
            // Prima controlliamo se il nickname esiste già nel database
            String checkSql = "SELECT COUNT(*) FROM Account WHERE Nickname = ?";
            try (Connection conn = Database.getInstance().getConnection(); PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, nickname);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    // Se il nickname esiste già, invia un errore al client
                    out.println("/error Nickname già in uso. Scegli un altro nome.");
                    return false;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante la registrazione.");
                return false;
            }

            // Se il nickname è disponibile, procediamo con la registrazione
            String sql = "INSERT INTO Account (Nickname, Password) VALUES (?, ?)";
            try (Connection conn = Database.getInstance().getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nickname);
                stmt.setString(2, password);
                stmt.executeUpdate();
                out.println("/register_success");
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante la registrazione.");
                return false;
            }
        }

        // Valida il login
        private boolean validateLogin(String nickname, String password) {
            String sql = "SELECT Password FROM Account WHERE Nickname = ?";
            try (Connection conn = Database.getInstance().getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nickname);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String storedPassword = rs.getString("Password");
                    return storedPassword.equals(password);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        // Invia un messaggio al client
        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                if (!client.isClosed()) {
                    if (clientInfo.isAuthenticated()) {
                        out.println("Sessione scaduta. Riaccedere.");
                    }
                    client.close();  // Chiudi la connessione del client
                }
                connections.remove(this); // Rimuovi dalla lista globale
                scheduler.shutdown();
                updateUsersList(); // Aggiorna la lista degli utenti connessi
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean isAuthenticated() {
            return clientInfo.isAuthenticated();
        }

        public Client getClientInfo() {
            return clientInfo;
        }

        private void handleDisconnect() {
            shutdown();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        Thread serverThread = new Thread(server);
        serverThread.start();
    }
}
