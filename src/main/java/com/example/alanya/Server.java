package com.example.alanya;

import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.SQLException;
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

    public int getClientCount() {
        return clients.size();
    }

    private String extractAfterSecondColon(String str) {
        // Trouver l'index de la première occurrence de " : "
        int firstColonIndex = str.indexOf(":");

        // Trouver l'index de la deuxième occurrence de " : " en commençant la recherche après la première
        int secondColonIndex = str.indexOf(":", firstColonIndex + 1);

        // Si la deuxième occurrence existe, extraire la sous-chaîne à partir de cet index jusqu'à la fin
        if (secondColonIndex != -1) {
            return str.substring(secondColonIndex + 1);
        }

        // Retourner une chaîne vide ou un message d'erreur si la deuxième occurrence n'existe pas
        return "";
    }

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
        // FILE_DATA:RECIPIENT:TRANSFER_ID:CHUNK
        // [FILE_END|FILE_ERROR]:RECIPIENT:TRANSFER_ID
        // FILE_INFO:RECIPIENT:TRANSFER_ID:FILE_NAME:FILE_LENGTH

        String[] parts = message.split(":", -1);

        if (parts.length < 2) {
            System.out.println("broadcastMessage error on Server.");

            return;
        }

        String type = parts[0];

        String sender;
        String content;
        String username;
        String receiver;

        for (ClientHandler clientHandler : clients) {
            switch (type) {
                // --- FILE
				case "FILE_END":
					receiver = parts[1];

					if (Objects.equals(clientHandler.getUsername(), receiver)) {
						clientHandler.sendMessage("FILE_END:" + clientHandlerSender.getUsername() + ":" + extractAfterSecondColon(message));
					}

					break;

				case "FILE_DATA", "FILE_ERROR":
					receiver = parts[1];

					if (Objects.equals(clientHandler.getUsername(), receiver)) {
						clientHandler.sendMessage(message);
						return;
					}

					break;

                case "FILE_INFO":
                    receiver = parts[1];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("FILE_INFO:" + clientHandlerSender.getUsername() + ":" + extractAfterSecondColon(message));
                        return;
                    }

                    break;
                // ---

                // --- MESSAGE
                case "MESSAGE":
                    receiver = parts[1];
                    content = parts[2];

                    try {
                        int senderID = DatabaseManager.getUserIdByUsername(clientHandlerSender.getUsername());
                        int receiverID = DatabaseManager.getUserIdByUsername(receiver);
                        if (senderID != -1 && receiverID != -1) {
                            DatabaseManager.saveMessage(senderID, receiverID, content, null);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("MESSAGE:" + clientHandlerSender.getUsername() + ":" + content);
                        return;
                    }

                    break;
                // ---

                // --- USER
                case "USER_CONNECTED":
                    sender = parts[1];
                    username = parts[2];

                    if (Objects.equals(sender, "SERVER")) {
                        // Nouveau client qui vient de se connecter
                        // 1. Envoyer tous les clients existants au nouveau client
                        for (ClientHandler existingClient : clients) {
                            if (!existingClient.equals(clientHandlerSender) && existingClient.getUsername() != null) {
                                clientHandlerSender.sendMessage("USER_CONNECTED:SERVER:" + existingClient.getUsername());
                            }
                        }

                        // 2. Envoyer le nouveau client à tous les autres
                        for (ClientHandler otherClient : clients) {
                            if (!otherClient.equals(clientHandlerSender)) {
                                otherClient.sendMessage("USER_CONNECTED:SERVER:" + clientHandlerSender.getUsername());
                            }
                        }
                    }
                    break;

                case "USER_DISCONNECTED":
                    this.removeClient(clientHandlerSender);
                    clientHandler.sendMessage("USER_DISCONNECTED:SERVER:" + clientHandlerSender.getUsername());

                    break;
                // ---

                // --- VIDEO-CALL
                case "VIDEO_CALL_REQUEST":
                    receiver = parts[1];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("VIDEO_CALL_REQUEST:" + clientHandlerSender.getUsername() + ":");
                        return;
                    }
                    break;

                case "VIDEO_CALL_ACCEPT":
                    receiver = parts[1];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("VIDEO_CALL_ACCEPT:" + clientHandlerSender.getUsername() + ":");
                        return;
                    }
                    break;

                case "VIDEO_CALL_REJECT":
                    receiver = parts[1];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("VIDEO_CALL_REJECT:" + clientHandlerSender.getUsername() + ":");
                        return;
                    }
                    break;

                case "VIDEO_CALL_END":
                    receiver = parts[1];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("VIDEO_CALL_END:" + clientHandlerSender.getUsername() + ":");
                        return;
                    }
                    break;

                case "VIDEO_FRAME":
                    receiver = parts[1];
                    String frameData = parts[2];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("VIDEO_FRAME:" + clientHandlerSender.getUsername() + ":" + frameData);
                        return;
                    }
                    break;

                case "AUDIO_DATA":
                    receiver = parts[1];
                    String audioData = parts[2];

                    if (Objects.equals(clientHandler.getUsername(), receiver)) {
                        clientHandler.sendMessage("AUDIO_DATA:" + clientHandlerSender.getUsername() + ":" + audioData);
                        return;
                    }
                    break;
                // ---

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
