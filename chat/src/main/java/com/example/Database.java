package com.example;

import java.sql.Connection;
import java.sql.SQLException;

// Questa classe gestisce l'accesso al database attraverso un pool di connessioni.
public class Database {
    // Pool di connessioni per gestire le connessioni al database.
    private ConnectionPool connectionPool;

    /*
     * Perché ConnectionPool?
     * 
     * Invece di aprire e chiudere una nuova connessione ogni volta che è necessario
     * interagire con il database, un connection pool mantiene un insieme di
     * connessioni già aperte e pronte per essere utilizzate.
     */

    // Rendere il costruttore privato poiché in questo modo possiamo dichiarare che
    // il database ha solo un'istanza, non può essere creato da altre classi.
    // (Singleton)

    private Database() {
        // Ottiene l'istanza del pool di connessioni esistente.
        connectionPool = ConnectionPool.getInstance();
    }

    // Metodo per ottenere un'istanza della classe Database nuova.
    public static Database getInstance() {
        return new Database();
    }

    // Metodo per ottenere una connessione dal pool di connessioni.
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }
}
