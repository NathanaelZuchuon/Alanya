package com.example.alanya;

import java.util.UUID;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ClientHandler implements Runnable {
    private PrintWriter out;
    private BufferedReader in;
    private final Socket socket;
    private final Server serverInstance;

    private String username;

    public ClientHandler(Socket socket, Server serverInstance) {
        this.socket = socket;
        this.serverInstance = serverInstance;

        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);

        } catch (IOException e) {
            System.out.println("Erreur lors de la création des flux: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Donner un pseudo à l'utilisateur
            this.username = UUID.randomUUID().toString().substring(0, 8);
            System.out.println(username);

            // Ajouter le client à l'interface
            serverInstance.addClientToUI(username, this);

            // Boucle de lecture des messages du client
            String message;
            while ((message = in.readLine()) != null) {
                serverInstance.broadcastMessage(message, this);
            }

        } catch (IOException e) {
            System.out.println("Erreur de communication avec " + username + ": " + e.getMessage());

        } finally {
            try {
                socket.close();

            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                // Informer tous les clients de la nouvelle déconnexion
                serverInstance.broadcastMessage("USER_DISCONNECTED:SERVER:me", this);
            }
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername() {
        return username;
    }
}
