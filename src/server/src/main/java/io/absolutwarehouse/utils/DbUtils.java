package io.absolutwarehouse.utils;

import io.absolutwarehouse.manager.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DbUtils {

    private DbUtils() {} // empêche l'instanciation

    /** Renvoie le code de permission d’un terminal */
    public static String getTerminalPermissions(String terminalName) {
        String sql = "SELECT permission_code FROM terminal WHERE terminal_name = ?";
        Connection conn = DatabaseManager.getInstance().getConnection(); // NE PAS fermer cette connection
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, terminalName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("permission_code"); // ex: "RW", "RX"
                }
            }
        } catch (SQLException e) {
            System.err.println("[DbUtils] ❌ Erreur getTerminalPermissions: " + e.getMessage());
        }
        return null; // terminal non trouvé
    }


    /** Convertit le code de permission en booléens lisibles */
    public static boolean canRead(String permissionCode) {
        if (permissionCode == null || permissionCode.length() < 1) return false;
        return permissionCode.charAt(0) != 'X';
    }

    public static boolean canWrite(String permissionCode) {
        if (permissionCode == null || permissionCode.length() < 2) return false;
        return permissionCode.charAt(1) != 'X';
    }


    // Récupérer un item libre dans un espace et mettre à jour ses champs optionnels
    public static long reserveItem(DatabaseManager db, String spaceCode, Double weight, String status,
                                   String estimatedDelivery, String exitTime) throws SQLException {

        ResultSet rs = db.from("item")
                .select("item_id")
                .where("space_code = ? AND item_status = 'in_storage'", spaceCode)
                .execute();

        if (!rs.next()) {
            return -1; // aucun item dispo
        }
        long itemId = rs.getLong("item_id");
        DatabaseManager.close(rs, null);

        // Mettre à jour champs si nécessaire
        if (weight != null) db.update("item").set("item_weight", weight).where("item_id = ?", itemId).execute();
        if (status != null) db.update("item").set("item_status", status).where("item_id = ?", itemId).execute();
        if (estimatedDelivery != null) db.update("item").set("item_estimated_delivery", estimatedDelivery).where("item_id = ?", itemId).execute();
        if (exitTime != null) db.update("item").set("item_exit_time", exitTime).where("item_id = ?", itemId).execute();

        db.update("item").set("space_code", spaceCode).where("item_id = ?", itemId).execute();

        return itemId;
    }


    // Parse une liste de paramètres style "-key value" en Map<key, value>
    public static Map<String, String> parseArgs(List<String> argsList) {
        Map<String, String> args = new HashMap<>();
        for (int i = 0; i < argsList.size(); i += 2) {
            if (i + 1 < argsList.size()) {
                String key = argsList.get(i).replaceFirst("^-+", "").toLowerCase();
                String value = argsList.get(i + 1);
                args.put(key, value);
            }
        }
        return args;
    }
}
