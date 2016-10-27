package ru.nsu.fit.bolodya.chat;

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;

class Connector {

    private DatagramSocket socket;
    private Collection<Message> messages;
    private Map<SocketAddress, Connection> neighbours;

    Connector(DatagramSocket socket, Collection<Message> messages, Map<SocketAddress, Connection> neighbours) {
        this.socket = socket;
        this.messages = messages;
        this.neighbours = neighbours;
    }

    void connect(SocketAddress address) {
        if (!neighbours.containsKey(address)) {
            Connection connection = new Connection(socket, address);
            neighbours.put(address, connection);
            for (Message message : messages)
                message.addConnection(connection);
        }
        neighbours.get(address).accept();
    }
}
