package com.example.alanya;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.application.Application;

public class Server extends Application {
    private static final int PORT = 8080;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    private ServerController controller;
    private ServerSocket serverSocket = null;

    @Override
    public void start(Stage stage) {
        try {
            // Charger le fichier FXML
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("server.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 600); stage.setResizable(false);
            stage.setTitle("Alanya.");
            stage.setScene(scene);

            // Ajouter l'icône de la fenêtre
            Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("server.png")));
            stage.getIcons().add(icon);

            // Affichage
            stage.show();

            // Récupérer le contrôleur du serveur
            controller = fxmlLoader.getController();
            controller.setServerInstance(this);

            // Démarrer le serveur dans un thread séparé
            new Thread(this::startServer).start();

        } catch (IOException e) {
            System.out.println("Erreur lors du chargement de l'interface serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws IOException {
        this.serverSocket.close();
    }

    private void startServer() {
        try {
            this.serverSocket = new ServerSocket(PORT);

            System.out.println("Serveur démarré sur le port " + PORT);
            System.out.println("En attente de connexions...\n");

            while (this.serverSocket != null) {
                Socket clientSocket = this.serverSocket.accept(); // ---
                System.out.println("Nouvelle connexion: " + clientSocket.getInetAddress().getHostAddress());

                // Créer un nouveau thread pour gérer le client
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);

                new Thread(clientHandler).start();
            }

        } catch (IOException e) {
            System.out.println("\nErreur du serveur: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public void broadcastMessage(String message, ClientHandler clientHandlerSender) {
        // Format attendu: TYPE:RECEVEUR:CONTENU

        String[] parts = message.split(":", 3);

        if (parts.length < 2) {
            System.out.println("broadcastMessage error on Server.");

            return;
        }

        String type = parts[0];

        String sender;
        String content;
        String username;
        String receiver;

        for (ClientHandler client : clients) {
            switch (type) {
                case "MESSAGE":
                    receiver = parts[1];
                    content = parts[2];

                    if (Objects.equals(client.getUsername(), receiver)) {
                        client.sendMessage("MESSAGE:" + clientHandlerSender.getUsername() + ":" + content);
                        return;
                    }

                    break;

                case "USER_CONNECTED":
                    sender = parts[1];
                    username = parts[2];

                    if (Objects.equals(sender, "SERVER") && Objects.equals(username, "me")) {
                        client.sendMessage("USER_CONNECTED:SERVER:" + clientHandlerSender.getUsername());
                    }

                    if (!Objects.equals(sender, "SERVER") && Objects.equals(username, "me")) {
                        if (Objects.equals(client.getUsername(), sender)) {
                            client.sendMessage("USER_CONNECTED:" + sender + ":" + clientHandlerSender.getUsername());
                            return;
                        }
                    }

                    break;

                case "USER_DISCONNECTED":
                    this.removeClient(clientHandlerSender);
                    client.sendMessage("USER_DISCONNECTED:SERVER:" + clientHandlerSender.getUsername());

                    break;

                default:
                    System.err.println("Message de type inconnu reçu par le serveur: " + message);
                    break;
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client " + client.getUsername() + " déconnecté. Nombre de clients actifs: " + clients.size());

        // Mettre à jour l'interface
        if (controller != null && client.getUsername() != null) {
            controller.removeClient(client.getUsername());
        }
    }

    public void addClientToUI(String username, ClientHandler clientHandler) {
        if (controller != null) {
            controller.addClient(username, clientHandler);
        }
    }
}
