package io.absolutwarehouse.config;

import java.io.File;
import java.net.URISyntaxException;

public class ConfigBootstrap {

    public static String getJarFolder() {
        try {
            String jarPath = new File(
                    ConfigBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getAbsolutePath();

            return new File(jarPath).getParent();
        } catch (URISyntaxException e) {
            return ".";
        }
    }

    public static String resolveConfigPath(String[] args) {
        // 1) Argument --config=
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("--config=")) {
                    return arg.substring("--config=".length());
                }
            }
        }

        // 2) Par défaut -> fichier à côté du jar
        return getJarFolder() + File.separator + "server.config";
    }
}
