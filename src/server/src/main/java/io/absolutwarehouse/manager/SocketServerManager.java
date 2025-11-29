package io.absolutwarehouse.manager;

import io.absolutwarehouse.config.ServerConfig;
import io.absolutwarehouse.network.SocketServer;
import io.absolutwarehouse.network.listener.MyListener;
import io.absolutwarehouse.utils.NetworkUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class SocketServerManager {
    private static SocketServer instance;

    public static SocketServer getInstance() {
        if (instance == null) {
            try {
                // Vérifier l'IP
                if (!NetworkUtils.isIpValid(ServerConfig.IP)) {
                    System.err.println("[FATAL] IP invalide : " + ServerConfig.IP);
                    System.exit(1);
                }

                // Vérifier le port (format valide + libre)
                if (!NetworkUtils.isPortAvailable(ServerConfig.PORT)) {
                    System.err.println("[FATAL] Port " + ServerConfig.PORT + " déjà utilisé ou invalide !");
                    System.exit(1);
                }

                InetAddress address = InetAddress.getByName(ServerConfig.IP);
                instance = new SocketServer(ServerConfig.PORT, address, new MyListener());

            } catch (UnknownHostException e) {
                System.err.println("[FATAL] Impossible de résoudre l'IP : " + ServerConfig.IP);
                e.printStackTrace();
                System.exit(1);
            }
        }
        return instance;
    }
}
