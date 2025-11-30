package io.absolutwarehouse;

import io.absolutwarehouse.config.ActionConfig;
import io.absolutwarehouse.config.ServerConfig;
import io.absolutwarehouse.config.ConfigBootstrap;
import io.absolutwarehouse.manager.DatabaseManager;
import io.absolutwarehouse.manager.SocketServerManager;

public class Main {

    public static void main(String[] args) {

        try {
            // ➤ Trouve le chemin du fichier config
            String configPath = ConfigBootstrap.resolveConfigPath(args);

            System.out.println("[INFO] Loading config file: " + configPath);

            try {
                // ➤ Charge les valeurs dans ServerConfig
                ServerConfig.loadFromFile(configPath);
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to load config file: " + configPath);
                System.out.println("Loading base config...");
            }

            // ➤ Charge config des actions
            ActionConfig.loadConfig();

            // ➤ Init DB + Serveur
            DatabaseManager.getInstance();
            SocketServerManager.getInstance().start();

        } catch (Exception e) {
            System.err.println("[FATAL] Server startup failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
