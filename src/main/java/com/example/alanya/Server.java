package com.example.alanya;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Serveur de partage de messages
 * Permet à plusieurs clients de se connecter et d'échanger des messages
 */
public class Server {
    private static final int PORT = 8080;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur démarré sur le port " + PORT);
            System.out.println("En attente de connexions... \n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouvelle connexion: " + clientSocket.getInetAddress().getHostAddress());

                // Créer un nouveau thread pour gérer le client
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur du serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Diffuse un message à tous les clients connectés
     */
    public static void broadcastMessage(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) { // Ne pas renvoyer le message à l'expéditeur
                client.sendMessage(message);
            }
        }
    }

    /**
     * Supprime un client de la liste des clients connectés
     */
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client déconnecté. Nombre de clients actifs: " + clients.size());
    }

    /**
     * Classe pour gérer les interactions avec un client
     */
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
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
                // Demander un nom d'utilisateur
                out.println("Entrez votre nom d'utilisateur:");
                username = in.readLine();
                System.out.println("Nouveau client: " + username);

                // Informer tous les clients de la nouvelle connexion
                broadcastMessage(username + " a rejoint le chat!", this);
                out.println("Bienvenue " + username + "! Vous pouvez commencer à envoyer des messages.");

                // Boucle de lecture des messages du client
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/quitter")) {
                        break;
                    }

                    System.out.println(username + ": " + message);
                    broadcastMessage(username + ": " + message, this);
                }
            } catch (IOException e) {
                System.out.println("Erreur de communication avec " + username + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                broadcastMessage(username + " a quitté le chat.", this);
                removeClient(this);
            }
        }

        /**
         * Envoie un message à ce client
         */
        public void sendMessage(String message) {
            out.println(message);
        }
    }
}