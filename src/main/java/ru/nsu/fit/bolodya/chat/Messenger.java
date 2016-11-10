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

    private Map<UUID, Long> lastMessages = new HashMap<>();

    Messenger(Collection<Connection> neighbours, Printable printer) {
        this.neighbours = neighbours;
        this.printer = printer;
    }

    void setConnector(Connector connector) {
        this.connector = connector;
    }

//  Send messages

    void send() {
        long curTime = System.currentTimeMillis();
        if (curTime - lastSendTime < TIMEOUT)
            return;

        lastSendTime = curTime;
        messages.values().forEach(Message::send);

        clearMessages();
    }

    private void clearMessages() {
        Iterator<Map.Entry<UUID, Message>> iterator = messages.entrySet().iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next().getValue();

            if (message.isDelivered()) {
                iterator.remove();
                continue;
            }

            if (message.outOfDate()) {
                iterator.remove();
                message.lost(connector);
            }
        }
    }

    void acceptMessage(byte[] data, Connection connection) {
        UUID id = getID(data);

        clearLastMessages();
        if (lastMessages.containsKey(id)) {
            connection.send(accept(MESSAGE, id));
            return;
        }

        if (messages.size() >= LIMIT) {
            connection.send(decline(MESSAGE, id));
            return;
        }

        connection.send(accept(MESSAGE, id));

        printer.print(data);

        messages.put(id, new Message(data, neighbours).removeConnection(connection));
        lastMessages.put(id, System.currentTimeMillis());
    }

    void declineMessage(byte[] data, Connection connection) {
        connection.send(decline(MESSAGE, getID(data)));
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
        message.setSystem(true);
        messages.put(id, message.send());
    }

//  Handle responses

    void handleAccept(UUID id, Connection connection) {
        Message message = messages.get(id);
        if (message != null)
            message.removeConnection(connection);
    }

    void handleDecline(UUID id) {
        Message message = messages.get(id);
        if (message != null)
            message.update();
    }

//  Other

    void addConnection(Connection connection) {
        for (Message message: messages.values())
            if (!message.isSystem() && !message.outOfDate())
                message.addConnection(connection);
    }

    void removeConnection(Connection connection) {
        for (Message message : messages.values())
            message.removeConnection(connection);
    }

    boolean isEmpty() {
        return messages.isEmpty();
    }
}
