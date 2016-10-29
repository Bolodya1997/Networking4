package ru.nsu.fit.bolodya.chat;

import java.net.InetSocketAddress;
import java.util.UUID;

interface PacketSpecial {

    boolean run(byte[] data, UUID id, InetSocketAddress address);
}
