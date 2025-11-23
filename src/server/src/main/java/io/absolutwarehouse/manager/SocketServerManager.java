package io.absolutwarehouse.manager;

import io.absolutwarehouse.config.ServerConfig;
import io.absolutwarehouse.network.SocketServer;
import io.absolutwarehouse.network.listener.MyListener;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class SocketServerManager {
    private static SocketServer instance;

    public static SocketServer getInstance() {
        if (instance == null) {
            try {
                InetAddress address = InetAddress.getByName(ServerConfig.IP);
                instance = new SocketServer(ServerConfig.PORT, address, new MyListener());
            } catch (UnknownHostException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        return instance;
    }

}