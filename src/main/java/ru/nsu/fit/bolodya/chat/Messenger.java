package ru.nsu.fit.bolodya.chat;

import java.util.*;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Messenger {

    private static final int LIMIT = 1000;

    private Collection<Connection> neighbours;
    private Printable printer;

    private Connector connector;

    private long lastSendTime;

    private Map<UUID, Message> messages = new HashMap<>();
    private int messageCount;

    private Map<UUID, Long> lastMessages = new HashMap<>();

    Messenger(Collection<Connection> neighbours, Printable printer) {
        this.neighbours = neighbours;
        this.printer = printer;
    }

    void setConnector(Connector connector) {
        this.connector = connector;
    }

    void send() {
        long curTime = System.currentTimeMillis();
        if (curTime - lastSendTime < TIMEOUT)
            return;

        lastSendTime = curTime;
        clearMessages();

        messages.values().forEach(Message::send);
    }

    private void clearMessages() {
        Iterator<Map.Entry<UUID, Message>> iterator = messages.entrySet().iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next().getValue();

            if (message.isDelivered())
                iterator.remove();

            if (message.outOfDate()) {
                iterator.remove();
                message.lost(connector);
            }
        }
    }

    void handleMessage(byte[] data, Connection connection) {
        clearLastMessages();
        if (lastMessages.containsKey(getID(data))) {
            connection.send(accept(MESSAGE, getID(data)));
            return;
        }

        if (messageCount >= LIMIT) {
            connection.send(decline(MESSAGE, getID(data)));
            return;
        }

        connection.send(accept(MESSAGE, getID(data)));

        printer.print(data);

        ++messageCount;
        messages.put(getID(data), new Message(data, neighbours).removeConnection(connection));
        lastMessages.put(getID(data), System.currentTimeMillis());
    }

    private void clearLastMessages() {
        long curTime = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> iterator = lastMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();

            if (curTime - entry.getValue() > MAX_MESSAGE_LIFE)
                iterator.remove();
        }
    }

    void sendMessage(byte[] data) {
        printer.print(data);

        messages.put(getID(data), new Message(data, neighbours).send());
    }

    void sendSystemMessage(UUID id, Message message) {
        messages.put(id, message.send());
    }

    void addConnection(Connection connection) {
        for (Message message: messages.values())
            message.addConnection(connection);
    }

    void removeConnection(Connection connection) {
        for (Message message : messages.values())
            message.removeConnection(connection);
    }
}
