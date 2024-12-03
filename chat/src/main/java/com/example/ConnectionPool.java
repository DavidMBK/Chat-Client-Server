package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;

import com.example.config.Config;

// Classe per gestire un pool di connessioni al database.
public class ConnectionPool {
    private static ConnectionPool instance; // Istanza unica del pool di connessioni.
    private Vector<Connection> connections; // Vettore per memorizzare le connessioni disponibili nel pool.

    /*
     * Vector è un oggetto Legacy di java safe-thread ovvero non causa problemi di
     * sincronizzazzione dei thread, ovvero possiamo
     * aggiungere e rimuovere connessioni in modo sicuro anche se molte parti del
     * codice cercano di farlo contemporaneamente.
     * 
     */

    private ConnectionPool() {
        connections = new Vector<>(); // Inizializza il vettore delle connessioni.
    }

    // Metodo per ottenere l'istanza unica del pool di connessioni.
    public synchronized static ConnectionPool getInstance() {
        if (instance == null) {
            instance = new ConnectionPool(); // Se l'istanza non è ancora stata creata, crea una nuova istanza.
        }
        return instance;
    }

    // Metodo per ottenere una connessione dal pool di connessioni.
    public synchronized Connection getConnection() throws SQLException {
        Connection conn = null;
        if (connections.size() > 0) {
            conn = connections.remove(0);
        } else {
            String url = Config.getInstance().getDbUrl(); // Ottiene l'URL del database dalla configurazione.
            String username = Config.getInstance().getDbUsername(); // Ottiene il nome del DB.
            String password = Config.getInstance().getDbPassword(); // Ottiene la password per connettersi al db
            // Crea una nuova connessione utilizzando DriverManager.
            conn = DriverManager.getConnection(url, username, password);
        }
        return conn; // Restituisce la connessione ottenuta.
    }

    // Metodo per rilasciare una connessione e rimetterla nel pool.
    public synchronized void releaseConnection(Connection conn) {
        if (conn != null) {
            connections.add(conn); // Aggiunge la connessione rilasciata al pool.
        }
    }

    // Metodo per chiudere tutte le connessioni nel pool.
    public synchronized void closeAllConnections() throws SQLException {
        for (Connection conn : connections) {
            conn.close(); // Chiude una per una tutte le connessioni nel pool.
        }
        connections.clear(); // Rimuove tutte le connessioni dal pool.
    }
}
