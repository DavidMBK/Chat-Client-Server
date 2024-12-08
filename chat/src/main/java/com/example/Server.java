package com.example;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.example.config.Config;

public class Server implements Runnable {

    private final CopyOnWriteArrayList<ConnectionHandler> connections; // Lista delle connessioni attive
    private ServerSocket server; // Socket server
    private volatile boolean done; // Indica lo stato del server
    private final ExecutorService executor; // Pool di thread per gestire le connessioni
    private final ConcurrentHashMap<String, Integer> ipConnections = new ConcurrentHashMap<>();

    public Server() {
        connections = new CopyOnWriteArrayList<>();
        done = false;
        executor = Executors.newCachedThreadPool(); // Pool dinamico
    }

    @Override
    public void run() {
        try {
            // Ottieni la configurazione
            Config config = Config.getInstance();
            int serverPort = config.getServerPort(); // Porta del server dal file di configurazione
            String sslpassword = config.getSSLPassword();
            // Carica il keystore per SSL
            char[] keystorePassword = sslpassword.toCharArray(); // Password del keystore
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            try (FileInputStream keystoreFile = new FileInputStream("SSL-TLS/server.keystore")) {
                keystore.load(keystoreFile, keystorePassword);
            }

            // Inizializza KeyManagerFactory con il keystore
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, keystorePassword);

            // Inizializza TrustManagerFactory con il keystore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keystore);

            // Crea un contesto SSL
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

            // Crea un server socket SSL
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            server = (SSLServerSocket) factory.createServerSocket(serverPort);
            System.out.println("SSL server started on port " + serverPort);

            // Ascolta e accetta connessioni in arrivo
            while (!done) {
                Socket client = server.accept(); // Accetta la connessione sicura
                String clientIp = client.getInetAddress().getHostAddress();

                // Controlla il numero di connessioni per l'indirizzo IP del client
                ipConnections.putIfAbsent(clientIp, 0);
                int currentConnections = ipConnections.get(clientIp);

                if (currentConnections >= 2) {
                    // Se ci sono già due connessioni da questo IP, invia un messaggio di errore e
                    // chiudi la connessione
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                    out.println("/error troppi account connessi da questo indirizzo IP.");
                    out.flush();
                    client.close();
                    System.out.println("Connessione rifiutata per l'indirizzo IP: " + clientIp);
                } else {
                    // Incrementa il conteggio delle connessioni per questo IP
                    ipConnections.put(clientIp, currentConnections + 1);

                    Client clientInfo = new Client();
                    int inactivityTimeout = config.getTimeout(); // Timeout configurabile

                    // Crea un gestore per il client
                    ConnectionHandler handler = new ConnectionHandler(client, clientInfo, inactivityTimeout);
                    connections.add(handler); // Aggiungi alla lista delle connessioni
                    executor.execute(handler); // Esegui il gestore in un thread separato
                }
            }
        } catch (Exception e) {
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
            } else {
                String newPassword = parts[1];

                // Controllo della password con il pattern
                String passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,}$";
                if (!newPassword.matches(passwordPattern)) {
                    out.println(
                            "/error La password deve contenere almeno una lettera maiuscola, una lettera minuscola, un numero e un carattere speciale.");
                } else {
                    // Hash della nuova password con SHA-256
                    String hashedPassword = hashPassword(newPassword);

                    String sql = "UPDATE Account SET Password = ? WHERE Nickname = ?";

                    try (Connection conn = Database.getInstance().getConnection();
                            PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, hashedPassword); // Salviamo la password hashed nel DB
                        stmt.setString(2, clientInfo.getNickname());
                        stmt.executeUpdate();
                        out.println("Password aggiornata con successo.");
                    } catch (SQLException e) {
                        e.printStackTrace();
                        out.println("Errore durante l'aggiornamento della password.");
                    }
                }
            }
        }

        private String hashPassword(String password) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashedBytes = digest.digest(password.getBytes());
                // Converti il byte array in una stringa esadecimale
                StringBuilder hexString = new StringBuilder();
                for (byte b : hashedBytes) {
                    hexString.append(String.format("%02x", b));
                }
                return hexString.toString();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return null;
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
                String clientIp = client.getInetAddress().getHostAddress();
                // Decrementa il conteggio delle connessioni per questo IP
                ipConnections.put(clientIp, ipConnections.get(clientIp) - 1);
        
                // Se la connessione è ancora aperta, invia il messaggio
                if (!client.isClosed()) {
                    if (clientInfo.isAuthenticated()) {
                        out.println("Sessione scaduta. Riaccedere.");
                        out.flush();  // Forza il flush per inviare il messaggio prima di chiudere la connessione
                    }
                    client.close();  // Chiudi la connessione dopo aver inviato il messaggio
                }
                
                // Rimuovi il gestore dalla lista delle connessioni
                connections.remove(this);
                
                // Ferma il task di timeout
                scheduler.shutdown();
                
                // Aggiorna la lista degli utenti online
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
