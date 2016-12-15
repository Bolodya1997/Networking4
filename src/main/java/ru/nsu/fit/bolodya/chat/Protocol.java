package ru.nsu.fit.bolodya.chat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.UUID;

class Protocol {

    private final static int TYPE_LENGTH  = 1;
    private final static int ID_LENGTH    = 2 * Long.BYTES;  //  sizeof UUID
    private final static int META_LENGTH  = TYPE_LENGTH + ID_LENGTH;

    static final int MAX_DATA = 1024 * 1024;
    static final int MAX_PACKET = MAX_DATA + META_LENGTH;

    static final long MESSAGE_LIFE = 1000;  //  in milliseconds
    static final long MAX_MESSAGE_LIFE = 3 * MESSAGE_LIFE;
    static final int TIMEOUT = (int) (MESSAGE_LIFE / 100);

//  TYPE = 0b0000RRTT

    private static final byte TYPE_MASK     = 0b0011;
    static final byte CAPTURE               = 0b0000;
    static final byte CONNECT               = 0b0001;
    static final byte MESSAGE               = 0b0010;
    static final byte DISCONNECT            = 0b0011;

    private static final byte RESPONSE_MASK = 0b1100;
    static final byte NO_RESPONSE           = 0b0000;
    static final byte ACCEPT                = 0b0100;
    static final byte DECLINE               = 0b1000;

//  Different message types

    private static byte[] baseMessage(byte type, UUID id, byte[] data) {
        int dataLength = (data == null) ? 0 : data.length;
        if (dataLength > MAX_DATA)  //  cuts all data over MAX_DATA
            dataLength = MAX_DATA;

        ByteBuffer buffer = ByteBuffer.allocate(META_LENGTH + dataLength)
                .put(type)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits());

        if (dataLength > 0)
            buffer.put(data, 0, dataLength);

        return buffer.array();
    }

    static byte[] capture(UUID id) {
        return baseMessage(CAPTURE, id, null);
    }

    static byte[] connect(UUID id) {
        return baseMessage(CONNECT, id, null);
    }

    static byte[] message(UUID id, byte[] data) {
        return baseMessage(MESSAGE, id, data);
    }

    static byte[] disconnect(UUID id, InetSocketAddress socket) {
        if (socket == null)
            return baseMessage(DISCONNECT, id, null);

        byte[] address = socket.getAddress().getAddress();
        byte[] data = ByteBuffer.allocate(Integer.BYTES + address.length + Integer.BYTES)
                .putInt(address.length)
                .put(address)
                .putInt(socket.getPort())
                .array();

        return baseMessage(DISCONNECT, id, data);
    }

//  Different response types

    private static byte[] baseResponse(byte responseType, byte type, UUID id) {
        return baseMessage((byte) (type | responseType), id, null);
    }

    static byte[] accept(byte type, UUID id) {
        return baseResponse(type, ACCEPT, id);
    }

    static byte[] decline(byte type, UUID id) {
        return baseResponse(type, DECLINE, id);
    }

//  Parse message

    static byte getType(byte[] data) {
        return (byte) (data[0] & TYPE_MASK);
    }

    static byte getResponse(byte[] data) {
        return (byte) (data[0] & RESPONSE_MASK);
    }

    static UUID getID(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data, TYPE_LENGTH, ID_LENGTH);
        long most = buffer.getLong();
        long least = buffer.getLong();

        return new UUID(most, least);
    }

    static byte[] getData(byte[] data) {
        int length = data.length - META_LENGTH;

        byte[] tmp = new byte[length];
        ((ByteBuffer) ByteBuffer.wrap(data).position(META_LENGTH)).get(tmp);

        return tmp;
    }

    static InetSocketAddress getParent(byte[] data) {
        if (data.length == META_LENGTH)
            return null;

        ByteBuffer buffer = (ByteBuffer) ByteBuffer.wrap(data).position(META_LENGTH);
        int addressLength = buffer.getInt();
        byte[] address = new byte[addressLength];
        buffer.get(address);
        int port = buffer.getInt();

        try {
            return new InetSocketAddress(InetAddress.getByAddress(address), port);
        }
        catch (UnknownHostException e) {
            return null;
        }
    }

//  Work with metadata

    static boolean filter(int length) {
        return length < META_LENGTH;
    }
}