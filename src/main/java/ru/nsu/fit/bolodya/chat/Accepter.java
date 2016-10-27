package ru.nsu.fit.bolodya.chat;

import java.util.Map;

class Accepter {

    private Map<Long, Message> messages;

    Accepter(Map<Long, Message> messages) {
        this.messages = messages;
    }

    void accept(long id, Connection connection) {
        Message message = messages.get(id);

        if (message == null || connection == null)
            return;

        message.removeConnection(connection);
        if (message.isDelivered())
            messages.remove(id);
    }
}
