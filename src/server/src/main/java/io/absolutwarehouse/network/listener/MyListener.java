package io.absolutwarehouse.network.listener;

import io.absolutwarehouse.manager.ClientManager;
import io.absolutwarehouse.network.Client;

public class MyListener implements ClientListener {

    @Override
    public void onClientConnected(Client client) {
        System.out.println("Client connected : " + client.getSocket().getInetAddress());
    }

    @Override
    public void onReceived(Client client, String message) {
        System.out.println("Message received from " + client.getSocket().getInetAddress() + " : " + message);
        ClientManager.getInstance().handleMessage(client, message);
    }

    @Override
    public void onClientDisconnected(Client client) {
        System.out.println("Client disconnected : " + client.getSocket().getInetAddress());
        client.setConnected(false);  // met à jour l’état du client
        ClientManager.getInstance().resetEtape(client);
    }

    @Override
    public void onError(Exception e) {
        System.out.println("[Error] " + e.getMessage());
    }
}
