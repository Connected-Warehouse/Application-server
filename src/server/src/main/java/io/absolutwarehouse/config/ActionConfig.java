package io.absolutwarehouse.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class ActionConfig {

    private static final Map<String, List<String>> actionPermissions = new HashMap<>();
    private static boolean loaded = false;

    public static void loadConfig() {
        try (InputStream is = ActionConfig.class.getClassLoader().getResourceAsStream("server_config.json")) {
            if (is == null) {
                throw new RuntimeException("Fichier server-config.json introuvable dans resources !");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);


            JsonNode actionsNode = root.get("actions");
            if (actionsNode == null || !actionsNode.isObject()) {
                throw new RuntimeException("Le champ 'actions' est manquant ou invalide dans server-config.json");
            }

            Iterator<String> fieldNames = actionsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String action = fieldNames.next();
                JsonNode permsNode = actionsNode.get(action);
                if (!permsNode.isArray()) {
                    throw new RuntimeException("Permissions de l'action '" + action + "' doivent être un tableau");
                }
                List<String> perms = new ArrayList<>();
                permsNode.forEach(node -> perms.add(node.asText()));
                actionPermissions.put(action, perms);
            }

            loaded = true;

        } catch (Exception e) {
            throw new RuntimeException("Erreur chargement config actions", e);
        }
    }

    public static boolean actionExists(String action) {
        return actionPermissions.containsKey(action);
    }

    public static List<String> getRequiredPermissions(String action) {
        return actionPermissions.get(action); // null si action inconnue
    }

    /**
     * Vérifie si un utilisateur avec "userPerm"
     * a le droit d'exécuter une action.
     *
     * @param userPerm  Permission de l’utilisateur ("R-", "-W", "RW", etc.)
     * @param action    Action que l’utilisateur veut exécuter
     * @return true si autorisé
     */
    public static boolean canExecute(String userPerm, String action) {
        if (userPerm==null || !actionExists(action)) return false;

        List<String> requiredPerms = getRequiredPermissions(action);

        for (String required : requiredPerms) {
            if (matches(userPerm, required)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vérifie si userPerm correspond à requiredPerm.
     * '-' dans required = caractère ignoré (regex)
     */
    private static boolean matches(String userPerm, String requiredPerm) {
        // remplace chaque '-' par '.' pour indiquer "n'importe quel caractère"
        String regex = requiredPerm.replace("-", ".");
        return Pattern.matches(regex, userPerm);
    }
}
