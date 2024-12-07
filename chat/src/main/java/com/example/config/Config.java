package com.example.config;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

// Questa classe gestisce la configurazione del sistema basata su un file XML.
public class Config {

    private static Config instance;
    // Documento XML (DOM) che contiene la configurazione.
    private Document doc;

    // Costruttore privato per impedire la creazione di istanze esterne.
    private Config() {
        try {
            // Carica il file di configurazione "config.xml".
            File file = new File("config.xml");
            // Crea un parser (DOM) per il documento XML.
            DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            // Parsa il file XML e lo converte in un oggetto Document (DOM).
            doc = dBuilder.parse(file);
            // Normalizza la struttura del documento, assicurando che sia coerente per fare
            // un analisi dei dati.
            doc.getDocumentElement().normalize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Metodo per ottenere l'istanza unica della classe Config.
    public static Config getInstance() {
        // Se l'istanza non è stata creata, crea una nuova istanza.
        if (instance == null) {
            instance = new Config();
        }
        // Restituisce l'istanza esistente o appena creata.
        return instance;
    }

    // Metodo per ottenere l'indirizzo IP del server dalla configurazione.
    public String getServerIp() {
        // Ottiene il valore dell'elemento "serverIp" dal documento XML.
        return doc.getElementsByTagName("serverIp").item(0).getTextContent();
    }

    // Metodo per ottenere la porta del server dalla configurazione.
    public int getServerPort() {
        // Ottiene il valore dell'elemento "serverPort" dal documento XML e lo converte
        // in intero per il serverSocket.
        return Integer.parseInt(doc.getElementsByTagName("serverPort").item(0).getTextContent());
    }

    // Metodo per ottenere l'URL del database dalla configurazione.
    public String getDbUrl() {
        // Ottiene il valore dell'elemento "dbUrl" dal documento XML.
        return doc.getElementsByTagName("dbUrl").item(0).getTextContent();
    }

    // Metodo per ottenere il nome utente del database dalla configurazione.
    public String getDbUsername() {
        // Ottiene il valore dell'elemento "dbUsername" dal documento XML.
        return doc.getElementsByTagName("dbUsername").item(0).getTextContent();
    }

    // Metodo per ottenere la password del database dalla configurazione.
    public String getDbPassword() {
        // Ottiene il valore dell'elemento "dbPassword" dal documento XML.
        return doc.getElementsByTagName("dbPassword").item(0).getTextContent();
    }

    public String getSSLPassword() {
        return doc.getElementsByTagName("sslPassword").item(0).getTextContent();
    }

    public int getTimeout() {
        return Integer.parseInt(doc.getElementsByTagName("timeout").item(0).getTextContent());
    }
}
