package ru.nsu.fit.bolodya.chat;

import java.net.DatagramPacket;

interface PacketReceived {

    boolean run(DatagramPacket receivePacket);
}
