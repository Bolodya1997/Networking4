package ru.nsu.fit.bolodya.chat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Parenter {

    private Map<UUID, Message> messages;
    private Map<InetSocketAddress, Connection> neighbours;

    private Connector connector;
    private Responser responser;
    private Messenger messenger;

    private InetSocketAddress parentAddress;

    Parenter(Map<UUID, Message> messages, Map<InetSocketAddress, Connection> neighbours,
             Connector connector, Responser responser, Messenger messenger,
             InetSocketAddress parentAddress) {
        this.messages = messages;
        this.neighbours = neighbours;
        this.connector = connector;
        this.responser = responser;
        this.messenger = messenger;
        this.parentAddress = parentAddress;
    }

    boolean isParent(InetSocketAddress address) {
        return parentAddress == address;
    }

    void handlePacket(byte[] data) {
    }
}
