package ru.nsu.fit.bolodya.chat;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Connector {

    private DatagramSocket socket;
    private Collection<Message> messages;
    private Map<InetSocketAddress, Connection> neighbours;
    private Set<Connection> captureSet;

    Connector(DatagramSocket socket, Collection<Message> messages, Map<InetSocketAddress,
              Connection> neighbours, Set<Connection> captureSet) {
        this.socket = socket;
        this.messages = messages;
        this.neighbours = neighbours;
        this.captureSet = captureSet;
    }

    void handleConnect(UUID id, InetSocketAddress address) {
        if (!neighbours.containsKey(address)) {
            Connection connection = new Connection(socket, address);

            neighbours.put(address, connection);
            for (Message message : messages)
                message.addConnection(connection);
        }

        neighbours.get(address).send(response(CONNECT, id));
    }

    void handleDisconnect(byte[] data, InetSocketAddress address) {
        Connection connection = neighbours.get(address);
        if (connection == null)
            connection = new Connection(socket, address);

        captureSet.remove(connection);

        connection.send(response(DISCONNECT, getID(data)));
        neighbours.remove(address);
    }

    void handleDisconnectResponse(Responser responser, UUID id, Connection connection) {
        responser.handleResponse(id, connection);
        neighbours.remove(connection.getAddress());
    }

    void lostConnection(Connection connection) {
        InetSocketAddress address = connection.getAddress();

        neighbours.remove(address);

        for (Message message : messages)
            message.removeConnection(connection);
    }
}
