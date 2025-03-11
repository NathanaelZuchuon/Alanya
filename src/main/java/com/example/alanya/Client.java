package com.example.alanya;

import java.io.*;
import java.net.*;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.application.Application;

/**
 * Client de messagerie pour se connecter au serveur de partage de messages
 */
public class Client extends Application {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("hello-view.fxml"));
        scene = new Scene(fxmlLoader.load());
        stage.setTitle("Alanya");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Connecté au serveur.");

            // Thread pour recevoir les messages du serveur
            Thread receiverThread = new Thread(new MessageReceiver(socket));
            receiverThread.start();

            // Envoyer des messages au serveur
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            launch();

            // Fermer les ressources
            // socket.close();

        } catch (IOException e) {
            System.out.println("Erreur de connexion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Classe pour recevoir les messages du serveur en continu
     */
    private static class MessageReceiver implements Runnable {
        private final Socket socket;
        private BufferedReader in;

        public MessageReceiver(Socket socket) {
            this.socket = socket;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                System.out.println("Erreur lors de la création du flux d'entrée: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.out.println("Connexion perdue avec le serveur: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}