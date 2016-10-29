package ru.nsu.fit.bolodya;

import ru.nsu.fit.bolodya.chat.Node;

import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {

    public static void main(String[] args) {
        try {
            new Node(2048);
        }
        catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
