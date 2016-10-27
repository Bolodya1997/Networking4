package ru.nsu.fit.bolodya.chat;

import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Map;

class Disconnector {

    private Map<SocketAddress, Connection> neighbours;
    private Connection[] parent;

    Disconnector(Map<SocketAddress, Connection> neighbours, Connection[] parent) {
        this.neighbours = neighbours;
        this.parent = parent;
    }

    void disconnect(byte[] data, Connection connection) {
        boolean badParent = false;

        SocketAddress address = null;
state:  if (connection == parent[0]) {
            try {
                address = Protocol.getParent(data);
            }
            catch (UnknownHostException e) {
                badParent = true;
            }

            if (badParent || address == null)
                break state;
        }

        connection.accept();
    }
}
