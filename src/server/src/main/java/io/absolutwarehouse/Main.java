package io.absolutwarehouse;

import io.absolutwarehouse.config.ActionConfig;
import io.absolutwarehouse.config.ServerConfig;
import io.absolutwarehouse.manager.DatabaseManager;
import io.absolutwarehouse.manager.SocketServerManager;
import io.absolutwarehouse.network.SocketServer;
import io.absolutwarehouse.network.listener.MyListener;

import java.net.InetAddress;

public class Main {


    public static void main(String[] args) {

        try {
            ActionConfig.loadConfig();
            DatabaseManager.getInstance();
            SocketServerManager.getInstance().start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}