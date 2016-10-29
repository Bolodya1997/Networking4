package ru.nsu.fit.bolodya.chat;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.getID;

class Connector {

    private DatagramSocket socket;
    private Collection<Message> messages;
    private Map<InetSocketAddress, Connection> neighbours;

    private Connection[] parent;

    Connector(DatagramSocket socket, Collection<Message> messages,
              Map<InetSocketAddress, Connection> neighbours, Connection[] parent) {
        this.socket = socket;
        this.messages = messages;
        this.neighbours = neighbours;
        this.parent = parent;
    }

    void connect(UUID id, InetSocketAddress address) {
        if (!neighbours.containsKey(address)) {
            Connection connection = new Connection(socket, address);
            neighbours.put(address, connection);
            for (Message message : messages)
                message.addConnection(connection);
        }
        neighbours.get(address).accept(id);
    }

    void disconnect(byte[] data, InetSocketAddress address) {
        if (neighbours.get(address) == parent[0]) {
            InetSocketAddress parentAddress = Protocol.getParent(data);

            parent[0] = new Connection(socket, parentAddress);
            neighbours.put(parentAddress, parent[0]);
        }

        neighbours.get(address).accept(getID(data));
        neighbours.remove(address);
    }

    void lostConnection(Connection connection) {
        InetSocketAddress address = connection.getAddress();

        neighbours.remove(address);
        if (connection == parent[0])
            parent[0] = null;

        for (Message message : messages)
            message.removeConnection(connection);
    }
}
