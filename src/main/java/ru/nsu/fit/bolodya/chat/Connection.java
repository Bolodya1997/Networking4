package ru.nsu.fit.bolodya.chat;

import java.net.DatagramSocket;
import java.net.SocketAddress;

class Connection {

    private DatagramSocket socket;
    private SocketAddress address;

    Connection(DatagramSocket socket, SocketAddress address) {
        this.socket = socket;
        this.address = address;
    }

    void send(byte[] data) {

    }

    void accept() {

    }
}
