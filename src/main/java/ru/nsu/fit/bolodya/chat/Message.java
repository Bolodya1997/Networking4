package ru.nsu.fit.bolodya.chat;

import java.util.*;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Message {

    private Set<Connection> connections = new HashSet<>();
    private byte[] data;

    private long createTime = System.currentTimeMillis();
    private long lastUpdateTime = createTime;

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

    void lost(Connector connector) {
        for (Connection connection : connections)
            connector.lostConnection(connection);
    }

    boolean isDelivered() {
        return connections.isEmpty();
    }

    void update() {
        lastUpdateTime = System.currentTimeMillis();
    }

    boolean outOfDate() {
        long curTime = System.currentTimeMillis();
        return curTime - lastUpdateTime > MESSAGE_LIFE || curTime - createTime > MAX_MESSAGE_LIFE;
    }

    static UUID nextID() {
        return UUID.randomUUID();
    }
}
