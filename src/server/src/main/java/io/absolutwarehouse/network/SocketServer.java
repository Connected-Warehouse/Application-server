package io.absolutwarehouse.network;

import io.absolutwarehouse.config.ServerConfig;
import io.absolutwarehouse.manager.DatabaseManager;
import io.absolutwarehouse.network.listener.ClientListener;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;

/**
 * SocketServer : multi-client TCP server.
 * Listens for incoming connections, handles messages, and notifies a listener of client events.
 */
public class SocketServer implements Runnable {

    private final ArrayList<Client> clients;
    private final int port;
    private final InetAddress address;
    private final ClientListener listener;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public SocketServer(int port, InetAddress address, ClientListener listener) {
        this.port = port;
        this.address = address;
        this.listener = listener;
        this.clients = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            printServerInfo();
            serverSocket = new ServerSocket(port, ServerConfig.MAX_CONCURRENT_CONNECTIONS, address);
            serverSocket.setReceiveBufferSize(ServerConfig.MAX_BUFFER_SIZE);
            running = true;

            System.out.println("[INFO] Server started on " + address + ":" + port);

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handleNewConnection(socket);
                } catch (IOException e) {
                    if (running && listener != null) listener.onError(e);
                    System.out.println("[ERROR] Accepting new connection: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            if (listener != null) listener.onError(e);
            System.out.println("[ERROR] ServerSocket initialization failed: " + e.getMessage());
        } finally {
            shutdownServer();
        }
    }

    public void start() {
        new Thread(this, "SocketServer-MainListenerThread").start();
    }

    public void stop() {
        running = false;
        closeAllClients();
        shutdownServer();
        System.out.println("[INFO] Server stopped.");
    }

    private void handleNewConnection(Socket socket) throws IOException {
        synchronized (clients) {

            if (!running) {
                System.out.println("[WARN] Refused connection from " + socket.getInetAddress() + ": server is closing.");
                socket.close();
                return;
            }

            if (clients.size() >= ServerConfig.MAX_CONCURRENT_CONNECTIONS) {
                System.out.println("[WARN] Refused connection from " + socket.getInetAddress() + ": max connections reached.");
                socket.close();
                return;
            }

            Client client = new Client(socket);
            clients.add(client);

            if (listener != null) listener.onClientConnected(client);
            System.out.println("[INFO] New client connected: " + socket.getInetAddress());

            new Thread(() -> handleClient(client), "ClientThread-" + socket.getInetAddress()).start();
        }
    }

    private void handleClient(Client client) {
        Socket socket = client.getSocket();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            inetSocketConnection(socket);
            String msg;
            while ((msg = in.readLine()) != null) {
                client.updateLastRequest();
                System.out.println("[RECEIVED] From " + socket.getInetAddress() + ": " + msg);
                if (listener != null) listener.onReceived(client, msg);
            }
        } catch (IOException e) {
            handleClientException(client, e);
        } finally {
            removeClient(client);
            if (listener != null) listener.onClientDisconnected(client);
            try {
                socket.close();
            } catch (IOException ignored) {}
            System.out.println("[INFO] Client " + socket.getInetAddress() + " connection closed.");
        }
    }

    private void handleClientException(Client client, IOException e) {
        Socket socket = client.getSocket();

        if (e instanceof SocketException) {
            String msg = e.getMessage();
            switch (msg) {
                case "Connection reset" -> System.out.println("[WARN] Client " + socket.getInetAddress() + " closed the connection abruptly.");
                case "Socket closed" -> System.out.println("[WARN] Socket already closed for client " + socket.getInetAddress());
                case "Connection timed out" -> System.out.println("[WARN] Client " + socket.getInetAddress() + " timed out due to inactivity.");
                default -> {
                    System.out.println("[ERROR] SocketException for client " + socket.getInetAddress() + ": " + msg);
                    if (listener != null) listener.onError(e);
                }
            }
        } else if (e instanceof SocketTimeoutException) {
            System.out.println("[WARN] Client " + socket.getInetAddress() + " read timeout (" + ServerConfig.CLIENT_TIMEOUT_MS + " ms). Disconnecting.");
        } else if (e instanceof EOFException) {
            System.out.println("[INFO] Client " + socket.getInetAddress() + " closed the connection normally.");
        } else {
            System.out.println("[ERROR] IOException for client " + socket.getInetAddress() + ": " + e.getMessage());
            if (listener != null) listener.onError(e);
        }

        removeClient(client);
        if (listener != null) listener.onClientDisconnected(client);

        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    private void inetSocketConnection(Socket socket) throws SocketException {
        socket.setSoTimeout(ServerConfig.CLIENT_TIMEOUT_MS);
        socket.setSendBufferSize(ServerConfig.MAX_BUFFER_SIZE);
        socket.setReceiveBufferSize(ServerConfig.MAX_BUFFER_SIZE);
        System.out.println("[INFO] Socket settings applied for client " + socket.getInetAddress());
    }

    public void removeClient(Client client) {
        synchronized (clients) {
            clients.remove(client);
            System.out.println("[INFO] Removed client " + client.getSocket().getInetAddress() + " from client list.");
        }
    }

    private void closeAllClients() {
        synchronized (clients) {
            for (Client client : clients) {
                try {
                    client.getSocket().close();
                    System.out.println("[INFO] Closed socket for client " + client.getSocket().getInetAddress());
                } catch (IOException ignored) {}
            }
            clients.clear();
        }
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("[INFO] ServerSocket closed.");
            }
        } catch (IOException ignored) {}
    }

    private void shutdownServer() {
        closeAllClients();
        closeServerSocket();
    }

    private void printServerInfo() {
        System.out.println(String.format(
                """
               [CONFIG INFO] 
               ============
               ServerName: %s
               ============
               IP: %s
               Port: %d
               MaxConcurrentConnections: %d
               MaxBufferSize: %d KB
               ============
               """,
                ServerConfig.SERVER_NAME,
                ServerConfig.IP,
                ServerConfig.PORT,
                ServerConfig.MAX_CONCURRENT_CONNECTIONS,
                ServerConfig.MAX_BUFFER_SIZE
        ));
    }

    public ArrayList<Client> getClients() {
        return clients;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return address;
    }

    public ClientListener getListener() {
        return listener;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

}
