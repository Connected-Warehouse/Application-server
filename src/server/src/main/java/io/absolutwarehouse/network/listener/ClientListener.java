package io.absolutwarehouse.network.listener;

import io.absolutwarehouse.network.Client;

public interface ClientListener {
    void onClientDisconnected(Client client);
    void onClientConnected(Client client);
    void onReceived(Client client, String message);
    void onError(Exception e);
}
