package com.example.alanya;

import java.io.*;
import java.net.*;
import java.util.Objects;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.application.Platform;
import javafx.application.Application;

public class Client extends Application {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;

    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;

    private static boolean connected = false;

    private static ClientController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("client.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 600); stage.setResizable(false);

        // Récupération du contrôleur
        controller = fxmlLoader.getController();
        controller.setCurrentClient(this);

        // Configuration de la fenêtre
        stage.setTitle("Alanya.");
        stage.setScene(scene);

        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
        stage.getIcons().add(icon);

        stage.setOnCloseRequest(e -> closeConnection());

        // Affichage
        stage.show();
    }

    public static void main(String[] args) {
        connectToServer();
        launch();
    }

    private static void connectToServer() {
        Task<Void> connectionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    connected = true;

                    out.println("USER_CONNECTED:SERVER:me");
                    System.out.println("Connecté au serveur.");

                    // Thread pour recevoir les messages du serveur
                    Thread receiverThread = new Thread(new MessageReceiver(socket, in));
                    receiverThread.setDaemon(true);
                    receiverThread.start();

                } catch (IOException e) {
                    Platform.runLater(() -> showConnectionError(e.getMessage()));
                }
                return null;
            }
        };

        Thread connectionThread = new Thread(connectionTask);
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private static void showConnectionError(String message) {
        System.err.println("Impossible de se connecter au serveur: " + message);
        // Ajouter une boîte de dialogue d'erreur avec JavaFX si nécessaire
    }

    public void sendMessage(String type, String recipient, String message) {
        if (connected && out != null) {
            // Format: "TYPE:RECEVEUR:CONTENU"

            String formattedMessage = type + ":" + recipient + ":" + message;
            out.println(formattedMessage);

            System.out.println(formattedMessage);

        } else {
            System.err.println("Impossible d'envoyer le message : non connecté.");
        }
    }

    private void closeConnection() {
        if (connected && socket != null && !socket.isClosed()) {
            try {
                if (out != null) {
                    out.println("USER_DISCONNECTED:SERVER:me");
                    out.close();
                }

                if (in != null) {
                    in.close();
                }

                socket.close();
                System.out.println("Déconnecté du serveur.");

            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture de la connexion: " + e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        closeConnection();
        Platform.exit();
    }

    private record MessageReceiver(Socket socket, BufferedReader in) implements Runnable {

        @Override
            public void run() {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println("Message reçu: " + message);

                        // Traitement du message selon son format
                        processMessage(message);
                    }

                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        Platform.runLater(() -> showConnectionError("Connexion perdue: " + e.getMessage()));
                    }
                }
            }

            private void processMessage(String message) {
                // Format attendu: TYPE:EXPEDITEUR:CONTENU

                String[] parts = message.split(":", 3);
                if (parts.length < 2) {
                    System.out.println("processMessage error on Client.");

                    return;
                }

                String type = parts[0];

                String sender;
                String username;

                switch (type) {
                    case "MESSAGE":
                        sender = parts[1];
                        String content = parts[2];

                        controller.addMessage(content, false, sender);

                        break;

                    case "USER_CONNECTED":
                        sender = parts[1];

                        username = parts[2];
                        String finalUsername_c = username;

                        if (Objects.equals(sender, "SERVER")) {
                            out.println("USER_CONNECTED:" + username + ":me");
                        }

                        Platform.runLater(() -> {
                            controller.addUser(finalUsername_c);
                        });

                        break;

                    case "USER_DISCONNECTED":
                        username = parts[2];
                        String finalUsername_d = username;

                        Platform.runLater(() -> {
                            controller.removeUser(finalUsername_d);
                        });

                        break;

                    default:
                        System.out.println("Message de type inconnu reçu par le client: " + message);
                        break;
                }
            }
        }
}
