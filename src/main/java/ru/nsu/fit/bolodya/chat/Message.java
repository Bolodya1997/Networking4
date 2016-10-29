package ru.nsu.fit.bolodya.chat;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class Message {

    private Set<Connection> connections = new HashSet<>();
    private byte[] data;

    Message(byte[] data, Connection connection) {
        this.data = Arrays.copyOf(data, data.length);
        connections.add(connection);
    }

    Message(byte[] data, Collection<Connection> connections) {
        this.data = Arrays.copyOf(data, data.length);
        this.connections.addAll(connections);
    }

    Message addConnection(Connection connection) {
        connections.add(connection);

        return this;
    }

    Message removeConnection(Connection connection) {
        connections.remove(connection);

        return this;
    }

    Message send() {
        for (Connection connection : connections)
            connection.send(data);

        return this;
    }

    void close(Connector connector) {
        connections.forEach(connector::lostConnection);
    }

    boolean isDelivered() {
        return connections.isEmpty();
    }
}
