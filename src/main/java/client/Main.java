package client;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Client client = new NonBlockingClient();
        client.start(9998, new Scanner(System.in));
    }
}
