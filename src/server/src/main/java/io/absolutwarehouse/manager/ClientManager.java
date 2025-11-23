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



    private void modifyPackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] modifyPackageAction received: " + parsedMessage);

        Map<String, String> args = DbUtils.parseArgs(parsedMessage);

        String packageCode = args.get("code");
        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            resetEtape(client);
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();

        try {
            // 1) Récupérer package et item associé
            ResultSet rs = db.from("package")
                    .select("package_id, item_id")
                    .where("package_code = ?", packageCode)
                    .execute();

            if (!rs.next()) {
                basicAnswer(client, "ERROR: package introuvable");
                return;
            }

            long packageId = rs.getLong("package_id");
            long itemId = rs.getLong("item_id");
            DatabaseManager.close(rs, null);

            // 2) Modifier package
            DatabaseManager.UpdateQuery pkgQuery = db.update("package").where("package_id = ?", packageId);
            if (args.containsKey("fragile")) pkgQuery.set("package_fragile", Boolean.parseBoolean(args.get("fragile")));
            if (args.containsKey("refrigerated")) pkgQuery.set("package_refrigerated", Boolean.parseBoolean(args.get("refrigerated")));
            pkgQuery.execute();

            // 3) Modifier item
            DatabaseManager.UpdateQuery itemQuery = db.update("item").where("item_id = ?", itemId);

            if (args.containsKey("weight")) {
                itemQuery.set("item_weight", Double.parseDouble(args.get("weight")));
            }

            if (args.containsKey("status")) {
                String statusStr = args.get("status");
                PGobject statusEnum = new PGobject();
                statusEnum.setType("item_status");
                statusEnum.setValue(statusStr.equalsIgnoreCase("null") ? null : statusStr);
                itemQuery.set("item_status", statusEnum);
            }

            if (args.containsKey("estimated_delivery")) {
                String dateStr = args.get("estimated_delivery");
                if (dateStr == null || dateStr.equalsIgnoreCase("null") || dateStr.isEmpty()) {
                    itemQuery.set("item_estimated_delivery", null);
                } else {
                    Date sqlDate = Date.valueOf(dateStr); // yyyy-MM-dd
                    itemQuery.set("item_estimated_delivery", sqlDate);
                }
            }

            if (args.containsKey("exit_time")) {
                String tsStr = args.get("exit_time").trim();
                if (tsStr.isEmpty() || tsStr.equalsIgnoreCase("null")) {
                    itemQuery.set("item_exit_time", null);
                } else if (tsStr.equalsIgnoreCase("now")) {
                    itemQuery.set("item_exit_time", new Timestamp(System.currentTimeMillis()));
                } else {
                    try {
                        Timestamp sqlTs = Timestamp.valueOf(tsStr); // yyyy-MM-dd HH:mm:ss
                        itemQuery.set("item_exit_time", sqlTs);
                    } catch (IllegalArgumentException ex) {
                        basicAnswer(client, "ERROR: format exit_time invalide, attendu yyyy-MM-dd HH:mm:ss");
                        resetEtape(client);
                        return;
                    }
                }
            }

            if (args.containsKey("spacecode")) {
                itemQuery.set("space_code", args.get("spacecode"));
            }

            itemQuery.execute();

            basicAnswer(client, "ALLOWED MODIFY (package_id=" + packageId + ")");
            System.out.println("[INFO] Package modifié : " + packageCode + " (ID=" + packageId + ")");

        } catch (SQLException e) {
            basicAnswer(client, "ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            resetEtape(client);
        }
    }




    private void addPackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] addPackageAction received: " + parsedMessage);

        Map<String, String> args = DbUtils.parseArgs(parsedMessage);

        String packageCode = args.get("code");
        String spaceCode = args.get("spacecode");

        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            resetEtape(client);
            return;
        }
        if (spaceCode == null) {
            basicAnswer(client, "ERROR: space_code manquant");
            resetEtape(client);
            return;
        }

        boolean fragile = Boolean.parseBoolean(args.getOrDefault("fragile", "false"));
        boolean refrigerated = Boolean.parseBoolean(args.getOrDefault("refrigerated", "false"));
        Double weight = args.containsKey("weight") ? Double.parseDouble(args.get("weight")) : 0.0;
        String statusStr = args.getOrDefault("status", "in_storage");
        String estimatedDeliveryStr = args.get("estimated_delivery");
        String exitTimeStr = args.get("exit_time");

        DatabaseManager db = DatabaseManager.getInstance();

        try {
            // -------------------
            // 1) Créer l'item
            // -------------------
            Map<String, Object> itemValues = new HashMap<>();
            itemValues.put("item_weight", weight);

            PGobject pgStatus = new PGobject();
            pgStatus.setType("item_status");
            pgStatus.setValue(statusStr);
            itemValues.put("item_status", pgStatus);

            // Traiter estimated_delivery (DATE)
            if (estimatedDeliveryStr == null || estimatedDeliveryStr.equalsIgnoreCase("null") || estimatedDeliveryStr.isEmpty()) {
                itemValues.put("item_estimated_delivery", null);
            } else {
                itemValues.put("item_estimated_delivery", Date.valueOf(estimatedDeliveryStr));
            }

            // Traiter exit_time (TIMESTAMP)
            if (exitTimeStr == null || exitTimeStr.equalsIgnoreCase("null") || exitTimeStr.isEmpty()) {
                itemValues.put("item_exit_time", null);
            } else if (exitTimeStr.equalsIgnoreCase("now")) {
                itemValues.put("item_exit_time", new Timestamp(System.currentTimeMillis()));
            } else {
                itemValues.put("item_exit_time", Timestamp.valueOf(exitTimeStr));
            }

            itemValues.put("space_code", spaceCode);

            int itemRows = db.insert("item", itemValues);
            if (itemRows == 0) {
                basicAnswer(client, "ERROR: échec création item");
                resetEtape(client);
                return;
            }

            // Récupérer l'ID de l'item
            ResultSet rsItem = db.from("item")
                    .select("item_id")
                    .where("space_code = ? ORDER BY item_entry_time DESC LIMIT 1", spaceCode)
                    .execute();
            long itemId = rsItem.next() ? rsItem.getLong("item_id") : 0;
            DatabaseManager.close(rsItem, null);

            // -------------------
            // 2) Créer le package
            // -------------------
            Map<String, Object> packageValues = new HashMap<>();
            packageValues.put("package_code", packageCode);
            packageValues.put("package_refrigerated", refrigerated);
            packageValues.put("package_fragile", fragile);
            packageValues.put("item_id", itemId);

            int pkgRows = db.insert("package", packageValues);
            if (pkgRows == 0) {
                basicAnswer(client, "ERROR: échec ajout package");
                resetEtape(client);
                return;
            }

            ResultSet rsPkg = db.from("package")
                    .select("package_id")
                    .where("package_code = ?", packageCode)
                    .execute();
            long packageId = rsPkg.next() ? rsPkg.getLong("package_id") : -1;
            DatabaseManager.close(rsPkg, null);

            basicAnswer(client, "ALLOWED ADD (package_id=" + packageId + ")");
            System.out.println("[INFO] Package ajouté : " + packageCode + " (ID=" + packageId + ")");

        } catch (SQLException e) {
            basicAnswer(client, "ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            resetEtape(client);
        }
    }



    private void deletePackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] deletePackageAction received: " + parsedMessage);

        // Parse les arguments en Map clé/valeur
        Map<String, String> args = DbUtils.parseArgs(parsedMessage);

        String packageCode = args.get("code");
        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            resetEtape(client);
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        ResultSet rs = null;

        try {
            // Vérifier que le package existe
            rs = db.from("package")
                    .select("package_id, item_id")
                    .where("package_code = ?", packageCode)
                    .execute();

            if (!rs.next()) {
                basicAnswer(client, "ERROR: package introuvable");
                return;
            }

            long packageId = rs.getLong("package_id");
            long itemId = rs.getLong("item_id");
            DatabaseManager.close(rs, null);

            // Supprimer le package
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
            resetEtape(client);
        }
    }



    private void readPackageAction(List<String> parsedMessage, Client client) {
        System.out.println("[DEBUG] readPackageAction received: " + parsedMessage);

        // Parse les arguments en Map clé/valeur
        Map<String, String> args = DbUtils.parseArgs(parsedMessage);

        String packageCode = args.get("code");
        if (packageCode == null) {
            basicAnswer(client, "ERROR: package_code manquant");
            resetEtape(client);
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        ResultSet rs = null;

        try {
            // Requête avec jointure package ↔ item
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
        } finally {
            DatabaseManager.close(rs, null);
            resetEtape(client);
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
