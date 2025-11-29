package io.absolutwarehouse.utils;

import java.io.IOException;
import java.util.logging.*;

public final class AppLogger {
    private static Logger logger = Logger.getLogger("AbsolutWarehouse");
    private static boolean initialized = false;

    private AppLogger() {}

    public static Logger getLogger() {
        if (!initialized) init();
        return logger;
    }

    private static void init() {
        try {
            // Désactiver les handlers par défaut (console)
            logger.setUseParentHandlers(false);

            // Formatter simple
            SimpleFormatter formatter = new SimpleFormatter();

            // File handler uniquement
            FileHandler fh = new FileHandler("server.log", true); // append = true
            fh.setLevel(Level.ALL);
            fh.setFormatter(formatter);
            logger.addHandler(fh);

            // Supprimer le console handler si présent
            Handler[] handlers = logger.getHandlers();
            for (Handler h : handlers) {
                if (h instanceof ConsoleHandler) {
                    logger.removeHandler(h);
                }
            }

            logger.setLevel(Level.ALL);
            initialized = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper pour niveaux custom
    public static void info(String msg) { getLogger().info(msg); }
    public static void warn(String msg) { getLogger().warning(msg); }
    public static void error(String msg) { getLogger().severe(msg); }
}
