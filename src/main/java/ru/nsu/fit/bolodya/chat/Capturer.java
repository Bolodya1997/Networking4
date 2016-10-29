package ru.nsu.fit.bolodya.chat;

import java.util.*;

import static ru.nsu.fit.bolodya.chat.Protocol.*;

class Capturer {    //  TODO:   ???

    private Map<UUID, Message> messages;
    private Set<Connection> captureSet;
    private Runnable shutdown;

    Capturer(Map<UUID, Message> messages, Set<Connection> captureSet, Runnable shutdown) {
        this.messages = messages;
        this.captureSet = captureSet;
        this.shutdown = shutdown;
    }

    void acceptCapture(byte[] data, Connection connection) {
        captureSet.add(connection);
        connection.send(accept(CAPTURE, getID(data), CAPTURE_ACCEPT));
    }

    void declineCapture(byte[] data, Connection connection) {
        connection.send(accept(CAPTURE, getID(data), CAPTURE_DECLINE));
    }

    void sendCaptureRequest(UUID id, Connection parent) {
        messages.put(id, new Message(capture(id), parent));
    }

    void receiveAccept(byte[] data) {
        shutdown.run();
    }
}
