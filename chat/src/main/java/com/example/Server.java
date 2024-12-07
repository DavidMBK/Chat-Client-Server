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

    private final CopyOnWriteArrayList<ConnectionHandler> connections; // Lista delle connessioni attive
    private ServerSocket server; // Socket server
    private volatile boolean done; // Indica lo stato del server
    private final ExecutorService executor; // Pool di thread per gestire le connessioni

    public Server() {
        connections = new CopyOnWriteArrayList<>();
        done = false;
        executor = Executors.newCachedThreadPool(); // Pool dinamico
    }

    @Override
    public void run() {
        try {
            Config config = Config.getInstance();
            int serverPort = config.getServerPort(); // Ottieni la porta dal file di configurazione

            server = new ServerSocket(serverPort);
            System.out.println("Server started on port " + serverPort);

            while (!done) {
                Socket client = server.accept(); // Accetta nuove connessioni
                Client clientInfo = new Client();
                int inactivityTimeout = 20; // Timeout configurabile

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

    public void broadcast(String message, ConnectionHandler sender) {
        for (ConnectionHandler ch : connections) {
            if (ch.isAuthenticated() && ch != sender) {
                ch.sendMessage(message);
            }
        }
    }

    public void updateUsersList() {
        Set<String> uniqueUsernames = new HashSet<>();
        for (ConnectionHandler ch : connections) {
            if (ch.isAuthenticated()) {
                uniqueUsernames.add(ch.getClientInfo().getNickname());
            }
        }

        String usersList = "/users_list " + String.join(",", uniqueUsernames);
        broadcast(usersList, null); // broadcast a tutti gli utenti, quindi il sender è null
    }

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

    private class ConnectionHandler implements Runnable {

        private final Socket client;
        private final BufferedReader in;
        private final PrintWriter out;
        private final Client clientInfo;
        private final ScheduledExecutorService scheduler;
        private ScheduledFuture<?> timeoutFuture;
        private final Runnable timeoutTask;
        private final int inactivityTimeout;
        private boolean sessionExpired; // Flag che indica se la sessione è scaduta

        public ConnectionHandler(Socket client, Client clientInfo, int inactivityTimeout) throws IOException {
            this.client = client;
            this.clientInfo = clientInfo;
            this.inactivityTimeout = inactivityTimeout;
            this.scheduler = new ScheduledThreadPoolExecutor(1);
            this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.out = new PrintWriter(client.getOutputStream(), true);
            this.sessionExpired = false; // Inizialmente la sessione non è scaduta

            this.timeoutTask = () -> {
                try {
                    if (clientInfo.isAuthenticated() && !sessionExpired) {
                        out.println("Sessione scaduta. Riaccedere.");

                        sessionExpired = true; // Imposta il flag per evitare che il messaggio venga inviato più volte
                        shutdown(); // Chiude la connessione
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
        }

        @Override
        public void run() {
            try {
                timeoutFuture = scheduler.schedule(timeoutTask, inactivityTimeout, TimeUnit.SECONDS);

                String message;
                while ((message = in.readLine()) != null) {
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
                        // Dopo il login, accetta i comandi senza il prefisso /login
                        if (message.equals("/disconnect")) {
                            handleDisconnect();
                        } else if (message.startsWith("/change_password ")) {
                            handleChangePassword(message);
                        } else if (message.startsWith("/change_name ")) {
                            handleChangeName(message);
                        } else {
                            broadcast(clientInfo.getNickname() + ": " + message, this);
                        }
                    }

                }
            } catch (IOException e) {
                System.err.println("Connection error with client: " + e.getMessage());
            } finally {
                shutdown();
            }
        }

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

        private void handleChangePassword(String message) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length != 2) {
                out.println("/error Formato errato. Usa: /change_password <nuova_password>");
                return;
            }

            String newPassword = parts[1];
            String sql = "UPDATE Account SET Password = ? WHERE Nickname = ?";

            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newPassword);
                stmt.setString(2, clientInfo.getNickname());
                stmt.executeUpdate();
                out.println("Password aggiornata con successo.");
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante l'aggiornamento della password.");
            }
        }

        private void handleChangeName(String message) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length != 2) {
                out.println("/error Formato errato. Usa: /change_name <nuovo_nome>");
                return;
            }

            String newName = parts[1];
            String checkSql = "SELECT COUNT(*) FROM Account WHERE Nickname = ?";

            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, newName);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    out.println("/error Nome già in uso. Scegli un altro nome.");
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante il controllo del nome.");
                return;
            }

            String updateSql = "UPDATE Account SET Nickname = ? WHERE Nickname = ?";

            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, newName);
                updateStmt.setString(2, clientInfo.getNickname());
                updateStmt.executeUpdate();
                clientInfo.setNickname(newName);
                out.println("Nome aggiornato con successo.");
                updateUsersList();
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante l'aggiornamento del nome.");
            }
        }

        private boolean registerUser(String nickname, String password) {

            String checkSql = "SELECT COUNT(*) FROM Account WHERE Nickname = ?";
            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, nickname);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    out.println("/error Nickname già in uso. Scegli un altro nome.");
                    return false;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante la registrazione.");
                return false;
            }

            String sql = "INSERT INTO Account (Nickname, Password) VALUES (?, ?)";
            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nickname);
                stmt.setString(2, password);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("/error Errore durante la registrazione.");
                return false;
            }
        }

        private boolean validateLogin(String nickname, String password) {

            String sql = "SELECT Password FROM Account WHERE Nickname = ?";
            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nickname);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("Password").equals(password);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                if (!client.isClosed()) {
                    if (clientInfo.isAuthenticated()) {
                        out.println("Sessione scaduta. Riaccedere.");
                    }
                    client.close();
                }
                connections.remove(this);
                scheduler.shutdown();
                updateUsersList();
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
        new Thread(server).start();
    }
}
