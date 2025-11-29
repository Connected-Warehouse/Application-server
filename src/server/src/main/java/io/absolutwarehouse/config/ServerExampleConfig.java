package io.absolutwarehouse.config;

public final class ServerExampleConfig {

    public static String SERVER_NAME = "SERVER_NAME";
    public static String IP = "127.0.0.1";
    public static int PORT = 8080;

    public static int MAX_CONCURRENT_CONNECTIONS = 1;

    public static int MAX_BUFFER_SIZE = 1024; // in Bytes
    public static int CLIENT_TIMEOUT_MS = 90000; // 90s sans réponse avant déconnection

    public static String DB_HOSTNAME = "postgre-...";
    public static String DB_NAME = "my_db_name";
    public static String DB_USERNAME = "myUsername";
    public static String DB_PASSWORD = "myPassword";
    public static int DB_PORT = 5432;

    public static void loadFromFile(String path) throws Exception {
        ConfigLoader.load(path);

        SERVER_NAME = ConfigLoader.getString("SERVER_NAME", SERVER_NAME);
        IP = ConfigLoader.getString("IP", IP);
        PORT = ConfigLoader.getInt("PORT", PORT);

        MAX_CONCURRENT_CONNECTIONS = ConfigLoader.getInt("MAX_CONCURRENT_CONNECTIONS", MAX_CONCURRENT_CONNECTIONS);
        MAX_BUFFER_SIZE = ConfigLoader.getInt("MAX_BUFFER_SIZE", MAX_BUFFER_SIZE);
        CLIENT_TIMEOUT_MS = ConfigLoader.getInt("CLIENT_TIMEOUT_MS", CLIENT_TIMEOUT_MS);

        DB_HOSTNAME = ConfigLoader.getString("DB_HOSTNAME", DB_HOSTNAME);
        DB_NAME = ConfigLoader.getString("DB_NAME", DB_NAME);
        DB_USERNAME = ConfigLoader.getString("DB_USERNAME", DB_USERNAME);
        DB_PASSWORD = ConfigLoader.getString("DB_PASSWORD", DB_PASSWORD);
        DB_PORT = ConfigLoader.getInt("DB_PORT", DB_PORT);

        printConfig();
    }

    private static void printConfig() {
        System.out.println("==== Server Config Loaded ====");
        System.out.println("SERVER_NAME = " + SERVER_NAME);
        System.out.println("IP = " + IP);
        System.out.println("PORT = " + PORT);
        System.out.println("MAX_CONCURRENT_CONNECTIONS = " + MAX_CONCURRENT_CONNECTIONS);
        System.out.println("MAX_BUFFER_SIZE = " + MAX_BUFFER_SIZE);
        System.out.println("CLIENT_TIMEOUT_MS = " + CLIENT_TIMEOUT_MS);
        System.out.println("DB_HOSTNAME = " + DB_HOSTNAME);
        System.out.println("DB_NAME = " + DB_NAME);
        System.out.println("DB_USERNAME = " + DB_USERNAME);
        System.out.println("DB_PASSWORD = " + DB_PASSWORD);
        System.out.println("DB_PORT = " + DB_PORT);
        System.out.println("==============================");
    }


}
