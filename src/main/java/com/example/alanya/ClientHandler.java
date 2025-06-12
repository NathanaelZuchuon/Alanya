package com.example.alanya;

import java.net.Socket;
import java.io.PrintWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ClientHandler implements Runnable {
    private PrintWriter out;
    private BufferedReader in;
    private final Socket socket;
    private final Server serverInstance;

    private int userID;
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
            // Attendre que le client envoie son username authentifié
            String firstMessage = in.readLine();
            if (firstMessage != null && firstMessage.startsWith("USER_CONNECTED:SERVER:")) {
                this.username = firstMessage.split(":")[2];

                // Récupérer l'ID depuis la BD
                this.userID = DatabaseManager.getUserIdByUsername(username);

                System.out.println("Client authentifié: " + username);
                serverInstance.addClientToUI(username, this);

                // Traiter le message de connexion
                serverInstance.broadcastMessage(firstMessage, this);
            }

            // Boucle normale
            String message;
            while ((message = in.readLine()) != null) {
                serverInstance.broadcastMessage(message, this);
            }

        } catch (IOException | SQLException e) {
            System.out.println("Erreur: " + e.getMessage());
        } finally {
            try {
                if (userID > 0) {
                    DatabaseManager.updateUserStatus(userID, false);
                }
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            serverInstance.broadcastMessage("USER_DISCONNECTED:SERVER:" + username, this);
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername() {
        return username;
    }
}
