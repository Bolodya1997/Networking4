package ru.nsu.fit.bolodya.chat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Messenger {

    private Map<Long, Message> messages;
    private Collection<Connection> neighbours;
    private Printable printer;

    private Map<Long, Long> lastMessages = new HashMap<>();

    Messenger(Map<Long, Message> messages, Collection<Connection> neighbours, Printable printer) {
        this.messages = messages;
        this.neighbours = neighbours;
        this.printer = printer;
    }

    void message(byte[] data, Connection connection) {
        updateLastMessages();

        if (connection == null || !neighbours.contains(connection))
            return;

        connection.accept();
        if (lastMessages.containsKey(getID(data))) {
            return;
        }

        printer.print(data);

        messages.put(getID(data), new Message(data, neighbours).removeConnection(connection));
        lastMessages.put(getID(data), System.currentTimeMillis());
    }

    void message(byte[] data) {
        updateLastMessages();

        printer.print(data);

        messages.put(getID(data), new Message(data, neighbours));
        lastMessages.put(getID(data), System.currentTimeMillis());
    }

    private void updateLastMessages() {
        long curTime = System.currentTimeMillis();

        Iterator<Map.Entry<Long, Long>> iterator = lastMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (curTime - entry.getValue() > MAX_MESSAGE_LIFE)
                iterator.remove();
        }
    }
}
