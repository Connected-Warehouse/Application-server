package io.absolutwarehouse.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

public final class NetworkUtils {

    public static boolean isPortAvailable(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port invalide : " + port + ". Doit être entre 0 et 65535.");
        }

        try (ServerSocket ignored = new ServerSocket(port)) {
            return true; // port libre
        } catch (IOException e) {
            return false; // port déjà utilisé
        }
    }

    /**
     * Vérifie si une IP ou un hostname est valide.
     * @param ip l'adresse IP ou hostname
     * @return true si valide, false sinon
     */
    public static boolean isIpValid(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address != null;
        } catch (UnknownHostException e) {
            return false;
        }
    }

}
