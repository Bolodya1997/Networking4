package ru.nsu.fit.bolodya.chat;

import java.util.Map;
import java.util.UUID;

class Responser {

    private Map<UUID, Message> messages;

    Responser(Map<UUID, Message> messages) {
        this.messages = messages;
    }

    void handleResponse(UUID id, Connection connection) {
        Message message = messages.get(id);

        if (message == null || connection == null)
            return;

        message.removeConnection(connection);
        if (message.isDelivered())
            messages.remove(id);
    }
}
