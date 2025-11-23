package io.absolutwarehouse.manager;

import io.absolutwarehouse.config.ActionConfig;
import io.absolutwarehouse.network.Client;
import io.absolutwarehouse.utils.DbUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.postgresql.util.PGobject;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;


/**
 * ClientManager
 *
 * Singleton responsible for managing clients and handling received messages.
 * Each client maintains its current state.
 */
public class ClientManager {

    private static ClientManager instance;

    private ClientManager() {}

    public static ClientManager getInstance() {
        if (instance == null) instance = new ClientManager();
        return instance;
    }

    public void handleMessage(Client client, String message) {
        if (message == null || !client.isConnected()) return;

        // Log du message entier en une seule ligne
        String compactMessage = message.replace("\n", "\\n");
        System.out.println("[RECEIVED] " + compactMessage);

        List<String> parsedMessage = parseMessage(message);
        if (parsedMessage.isEmpty()) return;

        String action = parsedMessage.get(0).toUpperCase();
        System.out.println("[RECEIVED] Action: " + action);

        String currentAction = client.getCurrentAction();
        if (currentAction == null) currentAction = "CONNECTING";

        switch (action) {
            case "DISCONNECT":
                System.out.println("[INFO] Client " + client.getSocket().getInetAddress() + " requested disconnect.");
                disconnectClient(client, "You have been disconnected.");
                break;

            case "INFO":
                System.out.println("[INFO] Client " + client.getSocket().getInetAddress() + " requested connection info.");
                basicAnswer(client, generateClientInfo(client));
                break;

            default:
                switch (currentAction) {
                    case "CONNECTING":
                        initMessageAction(parsedMessage, client);
                        break;
                    case "READ":
                        readPackageAction(parsedMessage, client);
                        break;
                    case "ADD":
                        addPackageAction(parsedMessage, client);
                        break;
                    case "MODIFY":
                        modifyPackageAction(parsedMessage, client);
                        break;
                    case "DELETE":
                        deletePackageAction(parsedMessage, client);
                        break;
                    default:
                        System.out.println("[WARN] Unknown stage for client: " + currentAction);
                }
                break;
        }
    }



    private void readPackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] readPackageAction received: " + parsedMessage);

        Map<String, String> args = DbUtils.parseArgs(parsedMessage);
        String packageCode = args.get("code");
        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            disconnectClient(client, "FINISHED");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        ResultSet rs = null;

        try {
            rs = db.from("package p")
                    .select(
                            "p.package_id",
                            "p.package_code",
                            "p.package_refrigerated",
                            "p.package_fragile",
                            "i.item_id",
                            "i.item_weight",
                            "i.item_status",
                            "i.item_estimated_delivery",
                            "i.item_entry_time",
                            "i.item_exit_time",
                            "i.space_code"
                    )
                    .join("INNER", "item i", "p.item_id = i.item_id")
                    .where("p.package_code = ?", packageCode)
                    .execute();

            if (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("PACKAGE DATA: ");
                sb.append("package_id=").append(rs.getLong("package_id")).append(", ");
                sb.append("package_code=").append(rs.getString("package_code")).append(", ");
                sb.append("refrigerated=").append(rs.getBoolean("package_refrigerated")).append(", ");
                sb.append("fragile=").append(rs.getBoolean("package_fragile")).append(", ");
                sb.append("item_id=").append(rs.getLong("item_id")).append(", ");
                sb.append("weight=").append(rs.getBigDecimal("item_weight")).append(", ");
                sb.append("status=").append(rs.getString("item_status")).append(", ");
                sb.append("estimated_delivery=").append(rs.getDate("item_estimated_delivery")).append(", ");
                sb.append("entry_time=").append(rs.getTimestamp("item_entry_time")).append(", ");
                sb.append("exit_time=").append(rs.getTimestamp("item_exit_time")).append(", ");
                sb.append("space_code=").append(rs.getString("space_code"));

                basicAnswer(client, sb.toString());
            } else {
                basicAnswer(client, "ERROR: package introuvable");
            }
        } catch (SQLException e) {
            basicAnswer(client, "ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DatabaseManager.close(rs, null);
            disconnectClient(client, "FINISHED");
        }
    }

    private void addPackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] addPackageAction received: " + parsedMessage);

        Map<String, String> args = DbUtils.parseArgs(parsedMessage);

        System.out.println("[DEBUG] Parsed args:");
        for (Map.Entry<String, String> entry : args.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }

        String packageCode = args.get("code");
        String spaceCode = args.get("spacecode");

        // Vérification existence des champs obligatoires
        if (packageCode == null || spaceCode == null) {
            basicAnswer(client, "ERROR: package_code ou space_code manquant");
            disconnectClient(client, "FINISHED");
            return;
        }

        // -------------------------------
        //  VALIDATION DU POIDS OBLIGATOIRE
        // -------------------------------
        Double weight = null;

        if (!args.containsKey("weight")) {
            basicAnswer(client, "ERROR: weight manquant (doit être > 0)");
            disconnectClient(client, "FINISHED");
            return;
        }

        try {
            weight = Double.parseDouble(args.get("weight"));
            if (weight <= 0) {
                basicAnswer(client, "ERROR: weight invalide (doit être > 0)");
                disconnectClient(client, "FINISHED");
                return;
            }
        } catch (NumberFormatException e) {
            basicAnswer(client, "ERROR: weight non numérique");
            disconnectClient(client, "FINISHED");
            return;
        }

        boolean fragile = Boolean.parseBoolean(args.getOrDefault("fragile", "false"));
        boolean refrigerated = Boolean.parseBoolean(args.getOrDefault("refrigerated", "false"));
        String statusStr = args.getOrDefault("status", "in_storage");
        String estimatedDeliveryStr = args.get("estimated_delivery");
        String exitTimeStr = args.get("exit_time");

        DatabaseManager db = DatabaseManager.getInstance();

        try {
            Map<String, Object> itemValues = new HashMap<>();
            itemValues.put("item_weight", weight);

            PGobject pgStatus = new PGobject();
            pgStatus.setType("item_status");
            pgStatus.setValue(statusStr);
            itemValues.put("item_status", pgStatus);

            // estimated_delivery
            if (estimatedDeliveryStr == null || estimatedDeliveryStr.equalsIgnoreCase("null") || estimatedDeliveryStr.isEmpty()) {
                itemValues.put("item_estimated_delivery", null);
            } else {
                itemValues.put("item_estimated_delivery", Date.valueOf(estimatedDeliveryStr));
            }

            // exit_time
            if (exitTimeStr == null || exitTimeStr.equalsIgnoreCase("null") || exitTimeStr.isEmpty()) {
                itemValues.put("item_exit_time", null);
            } else if (exitTimeStr.equalsIgnoreCase("now")) {
                itemValues.put("item_exit_time", new Timestamp(System.currentTimeMillis()));
            } else {
                itemValues.put("item_exit_time", Timestamp.valueOf(exitTimeStr));
            }

            itemValues.put("space_code", spaceCode);

            // Insert item
            int itemRows = db.insert("item", itemValues);
            if (itemRows == 0) {
                basicAnswer(client, "ERROR: échec création item");
                disconnectClient(client, "FINISHED");
                return;
            }

            ResultSet rsItem = db.from("item")
                    .select("item_id")
                    .where("space_code = ? ORDER BY item_entry_time DESC LIMIT 1", spaceCode)
                    .execute();
            long itemId = rsItem.next() ? rsItem.getLong("item_id") : 0;
            DatabaseManager.close(rsItem, null);

            // Insert package
            Map<String, Object> packageValues = new HashMap<>();
            packageValues.put("package_code", packageCode);
            packageValues.put("package_refrigerated", refrigerated);
            packageValues.put("package_fragile", fragile);
            packageValues.put("item_id", itemId);

            int pkgRows = db.insert("package", packageValues);
            if (pkgRows == 0) {
                basicAnswer(client, "ERROR: échec ajout package");
                disconnectClient(client, "FINISHED");
                return;
            }

            ResultSet rsPkg = db.from("package")
                    .select("package_id")
                    .where("package_code = ?", packageCode)
                    .execute();
            long packageId = rsPkg.next() ? rsPkg.getLong("package_id") : -1;
            DatabaseManager.close(rsPkg, null);

            basicAnswer(client, "ALLOWED ADD (package_id=" + packageId + ")");
        } catch (SQLException e) {
            basicAnswer(client, "ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnectClient(client, "FINISHED");
        }
    }


    private void modifyPackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] modifyPackageAction received: " + parsedMessage);

        Map<String, String> args = DbUtils.parseArgs(parsedMessage);
        String packageCode = args.get("code");
        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            disconnectClient(client, "FINISHED");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        try {
            ResultSet rs = db.from("package")
                    .select("package_id, item_id")
                    .where("package_code = ?", packageCode)
                    .execute();

            if (!rs.next()) {
                basicAnswer(client, "ERROR: package introuvable");
                disconnectClient(client, "FINISHED");
                return;
            }

            long packageId = rs.getLong("package_id");
            long itemId = rs.getLong("item_id");
            DatabaseManager.close(rs, null);

            DatabaseManager.UpdateQuery pkgQuery = db.update("package").where("package_id = ?", packageId);
            if (args.containsKey("fragile")) pkgQuery.set("package_fragile", Boolean.parseBoolean(args.get("fragile")));
            if (args.containsKey("refrigerated")) pkgQuery.set("package_refrigerated", Boolean.parseBoolean(args.get("refrigerated")));
            pkgQuery.execute();

            DatabaseManager.UpdateQuery itemQuery = db.update("item").where("item_id = ?", itemId);
            if (args.containsKey("weight")) itemQuery.set("item_weight", Double.parseDouble(args.get("weight")));
            if (args.containsKey("status")) {
                PGobject statusEnum = new PGobject();
                statusEnum.setType("item_status");
                statusEnum.setValue(args.get("status").equalsIgnoreCase("null") ? null : args.get("status"));
                itemQuery.set("item_status", statusEnum);
            }
            if (args.containsKey("estimated_delivery")) {
                String dateStr = args.get("estimated_delivery");
                itemQuery.set("item_estimated_delivery",
                        (dateStr == null || dateStr.equalsIgnoreCase("null") || dateStr.isEmpty()) ? null : Date.valueOf(dateStr));
            }
            if (args.containsKey("exit_time")) {
                String tsStr = args.get("exit_time").trim();
                if (tsStr.isEmpty() || tsStr.equalsIgnoreCase("null")) itemQuery.set("item_exit_time", null);
                else if (tsStr.equalsIgnoreCase("now")) itemQuery.set("item_exit_time", new Timestamp(System.currentTimeMillis()));
                else itemQuery.set("item_exit_time", Timestamp.valueOf(tsStr));
            }
            if (args.containsKey("spacecode")) itemQuery.set("space_code", args.get("spacecode"));

            itemQuery.execute();

            basicAnswer(client, "ALLOWED MODIFY (package_id=" + packageId + ")");
            System.out.println("[INFO] Package modifié : " + packageCode + " (ID=" + packageId + ")");
        } catch (SQLException e) {
            basicAnswer(client, "ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnectClient(client, "FINISHED");
        }
    }

    private void deletePackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] deletePackageAction received: " + parsedMessage);

        Map<String, String> args = DbUtils.parseArgs(parsedMessage);
        String packageCode = args.get("code");
        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            disconnectClient(client, "FINISHED");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        ResultSet rs = null;

        try {
            rs = db.from("package")
                    .select("package_id, item_id")
                    .where("package_code = ?", packageCode)
                    .execute();

            if (!rs.next()) {
                basicAnswer(client, "ERROR: package introuvable");
                disconnectClient(client, "FINISHED");
                return;
            }

            long packageId = rs.getLong("package_id");
            long itemId = rs.getLong("item_id");
            DatabaseManager.close(rs, null);

            int rows = db.deleteFrom("package")
                    .where("package_id = ?", packageId)
                    .execute();

            if (rows > 0) {
                basicAnswer(client, "CONFIRMED DELETE (package_id=" + packageId + ", item_id=" + itemId + ")");
                System.out.println("[INFO] Package supprimé : " + packageCode + " (ID=" + packageId + ")");
            } else {
                basicAnswer(client, "ERROR: échec suppression package");
            }
        } catch (SQLException e) {
            basicAnswer(client, "ERROR: " + e.getMessage());
        } finally {
            disconnectClient(client, "FINISHED");
        }
    }






    private void printList(List<String> parsedMessage) {
        parsedMessage.forEach(s -> System.out.println("[DATA] " + s));
    }



    private void initMessageAction(List<String> parsedMessage, Client client) {

        if (parsedMessage.size() != 2) {
            disconnectClient(client, "BAD FORMAT.\nEXPECTED: 'INSTRUCTION' TERMINAL_ID \nRECEIVED: " + String.join(" ", parsedMessage));
            return;
        }

        String action = parsedMessage.get(0);
        String terminal = parsedMessage.get(1);

        if (!checkPerms(terminal, action)) {
            disconnectClient(client, "ACTION DENIED");
            return;
        }

        switch (action) {
            case "ADD":
                client.setCurrentAction("ADD");
                break;
            case "MODIFY":
                client.setCurrentAction("MODIFY");
                break;
            case "READ":
                client.setCurrentAction("READ");
                break;
            case "DELETE":
                client.setCurrentAction("DELETE");
                break;
            default:
                client.setCurrentAction("CONNECTING");
                basicAnswer(client, "UNKNOWN ACTION!");
                System.out.println("[WARN] Unknown action received: " + action);
                return;
        }
        basicAnswer(client, "ALLOWED");
        System.out.println("[INFO] Action allowed for client " + terminal + ": " + action);
    }





    public void basicAnswer(Client client, String message) {
        if (client == null || !client.isConnected()) return;

        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getSocket().getOutputStream()));
            out.write(message);
            out.newLine();
            out.flush();
            System.out.println("[INFO] Sent message to client " + client.getSocket().getInetAddress() + ": " + message);
        } catch (IOException e) {
            System.err.println("[ERROR] Sending message to client " + client.getSocket().getInetAddress() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<String> parseMessage(String message) {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(message);

        while (m.find()) {
            if (m.group(1) != null) tokens.add(m.group(1));
            else tokens.add(m.group(2));
        }
        return tokens;
    }

    private static boolean checkPerms(String terminalID, String action) {


        String code = DbUtils.getTerminalPermissions(terminalID);;


        if (ActionConfig.canExecute(code, action)) {
            System.out.println("[INFO] Action allowed");
            return true;
        } else {
            System.out.println("[WARN] Permission denied");
            return false;
        }
    }

    private String generateClientInfo(Client client) {
        if (client == null || !client.isConnected()) return "Client not connected.";

        StringBuilder sb = new StringBuilder();
        sb.append("=== Client Info ===\n");
        sb.append("IP: ").append(client.getSocket().getInetAddress().getHostAddress()).append("\n");
        sb.append("Port: ").append(client.getSocket().getPort()).append("\n");
        sb.append("Current Action: ").append(client.getCurrentAction()).append("\n");
        sb.append("Connected: ").append(client.isConnected()).append("\n");

        if (client.getLastRequest() != null) {
            sb.append("Last Request: ").append(client.getLastRequest()).append("\n");
        }

        sb.append("==================");
        return sb.toString();
    }

    public void resetEtape(Client client) {
        client.setCurrentAction("CONNECTING");
        System.out.println("[INFO] Reset client stage to CONNECTING: " + client.getSocket().getInetAddress());
    }

    public void disconnectClient(Client client, String message) {
        if (client == null) return;

        basicAnswer(client, message);

        try {
            if (!client.getSocket().isClosed()) {
                client.getSocket().close();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Closing socket for client " + client.getSocket().getInetAddress() + ": " + e.getMessage());
            e.printStackTrace();
        }

        client.setConnected(false);
        resetEtape(client);
        SocketServerManager.getInstance().removeClient(client);

        System.out.println("[INFO] Client " + client.getSocket().getInetAddress() + " disconnected cleanly.");
    }
}
