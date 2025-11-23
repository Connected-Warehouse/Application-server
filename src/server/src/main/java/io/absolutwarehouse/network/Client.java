package io.absolutwarehouse.network;

import java.net.Socket;
import java.time.LocalDateTime;

public class Client {

    private final Socket socket;          // La socket associée au client
    private final LocalDateTime sessionStart; // Date d'ouverture de la session
    private LocalDateTime lastRequest;    // Date de la dernière requête
    private String currentAction = "CONNECTING";         // Action en cours pour ce client
    private boolean connected;            // État de la connexion

    // Constructeur
    public Client(Socket socket) {
        this.socket = socket;
        this.sessionStart = LocalDateTime.now();
        this.lastRequest = LocalDateTime.now();
        this.connected = true;
        this.currentAction = null;
    }

    // Marque la dernière requête reçue
    public void updateLastRequest() {
        this.lastRequest = LocalDateTime.now();
    }

    // Getters / Setters
    public Socket getSocket() {
        return socket;
    }

    public LocalDateTime getSessionStart() {
        return sessionStart;
    }

    public LocalDateTime getLastRequest() {
        return lastRequest;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(String currentAction) {
        this.currentAction = currentAction;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            updateLastRequest();
        }
    }

    @Override
    public String toString() {
        return "Client{" +
                "socket=" + socket.getInetAddress() +
                ", sessionStart=" + sessionStart +
                ", lastRequest=" + lastRequest +
                ", currentAction='" + currentAction + '\'' +
                ", connected=" + connected +
                '}';
    }
}
