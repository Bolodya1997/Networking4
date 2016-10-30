package ru.nsu.fit.bolodya.chat;

import java.util.*;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Messenger {

    private Map<UUID, Message> messages;
    private Collection<Connection> neighbours;
    private Printable printer;

    private Map<UUID, Long> lastMessages = new HashMap<>();

    Messenger(Map<UUID, Message> messages, Collection<Connection> neighbours, Printable printer) {
        this.messages = messages;
        this.neighbours = neighbours;
        this.printer = printer;
    }

    void handleMessage(byte[] data, Connection connection) {
        connection.send(response(MESSAGE, getID(data)));
        if (lastMessages.containsKey(getID(data))) {
            return;
        }

        printer.print(data);

        messages.put(getID(data), new Message(data, neighbours).removeConnection(connection));
        lastMessages.put(getID(data), System.currentTimeMillis());
    }

    void sendMessage(byte[] data) {
        printer.print(data);

        messages.put(getID(data), new Message(data, neighbours).send());
        lastMessages.put(getID(data), System.currentTimeMillis());
    }

    void addSystemMessage(UUID id, Message message) {
        messages.put(id, message.send());
        lastMessages.put(id, System.currentTimeMillis());
    }

    void addConnection(Connection connection) {
        for (UUID id : messages.keySet()) {
            messages.get(id).addConnection(connection);
            lastMessages.put(id, System.currentTimeMillis());
        }
    }

    void removeConnection(Connection connection) {
        for (UUID id : messages.keySet())
            messages.get(id).removeConnection(connection);
    }

    void updateLastMessages(Connector connector) {
        long curTime = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> iterator = lastMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();

            if (curTime - entry.getValue() > MAX_MESSAGE_LIFE) {
                Message message = messages.get(entry.getKey());
                if (message != null) {
                    message.close(connector);
                    messages.remove(entry.getKey());
                }

                iterator.remove();
            }
        }
    }
}
