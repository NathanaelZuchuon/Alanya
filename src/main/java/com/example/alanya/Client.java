package com.example.alanya;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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

	private static class FileTransfer {
		public final String sender;
		public final File file;
		public final long totalSize;
		public long bytesReceived;

		public FileTransfer(String sender, File file, long totalSize) {
			this.sender = sender;
			this.file = file;
			this.totalSize = totalSize;
			this.bytesReceived = 0;
		}
	}

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

	public void sendFileInfo(String info) {
		if (connected && out != null) {
			// Format: "FILE_INFO:RECIPIENT:TRANSFER_ID:FILE_NAME:FILE_LENGTH"

			out.println(info);
			System.out.println(info);

		} else {
			System.err.println("Impossible d'envoyer les infos du fichier : non connecté.");
		}
	}

	public void sendFileChunk(String chunk) {
		if (connected && out != null) {
			// Format: "FILE_DATA:RECIPIENT:TRANSFER_ID:CHUNK

			out.println(chunk);
			System.out.println(chunk);

		} else {
			System.err.println("Impossible d'envoyer ce morceau du fichier : non connecté.");
		}
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

		private static final Map<String, FileTransfer> fileTransfers = new HashMap<>();

		private String formatFileSize(long size) {
			final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
			int unitIndex = 0;
			double fileSize = size;

			while (fileSize > 1024 && unitIndex < units.length - 1) {
				fileSize /= 1024;
				unitIndex++;
			}

			return String.format("%.2f %s", fileSize, units[unitIndex]);
		}

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
			// Format attendu: TYPE:RECEVEUR:CONTENU
			// FILE_DATA:RECIPIENT:TRANSFER_ID:CHUNK
			// [FILE_END|FILE_ERROR]:RECIPIENT:TRANSFER_ID
			// FILE_INFO:RECIPIENT:TRANSFER_ID:FILE_NAME:FILE_LENGTH

			String[] parts = message.split(":", -1);
			if (parts.length < 2) {
				System.out.println("processMessage error on Client.");

				return;
			}

			String type = parts[0];

			String chunk;
			String sender;
			String username;
			String fileName;
			String fileSize;
			String transferId;
			FileTransfer transfer;

			switch (type) {
				case "FILE_ERROR":
					Platform.runLater(() -> {
						Alert alert = new Alert(Alert.AlertType.ERROR);
						alert.setTitle("Erreur d'envoi");
						alert.setHeaderText("Il a été impossible de vous envoyer un fichier.");
						alert.setContentText("Erreur d'envoi !");
						alert.showAndWait();
					});

					break;

				case "FILE_END":
					transferId = parts[2];
					transfer = fileTransfers.remove(transferId);

					sender = parts[1];

					if (transfer != null) {
						// Informer l'utilisateur que le fichier a été reçu avec succès
						controller.addMessage("Fichier reçu: " + transfer.file.getName() + " (enregistré dans " + transfer.file.getParent() + ")", false, sender);

						Platform.runLater(() -> {
							// Proposer d'ouvrir le fichier
							Alert alert = new Alert(Alert.AlertType.INFORMATION);
							alert.setTitle("Fichier reçu");
							alert.setHeaderText("Fichier reçu de " + sender);
							alert.setContentText("Le fichier " + transfer.file.getName() + " a été enregistré dans " + transfer.file.getParent());

							ButtonType openButton = new ButtonType("Ouvrir");
							ButtonType openFolderButton = new ButtonType("Ouvrir le dossier");
							ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
							alert.getButtonTypes().setAll(openButton, openFolderButton, closeButton);

							alert.showAndWait().ifPresent(response -> {
								try {
									if (response == openButton) {
										// Ouvrir le fichier
										Desktop.getDesktop().open(transfer.file);

									} else if (response == openFolderButton) {
										// Ouvrir le dossier contenant le fichier
										Desktop.getDesktop().open(transfer.file.getParentFile());
									}

								} catch (IOException e) {
									System.err.println("Erreur lors de l'ouverture du fichier: " + e.getMessage());
									e.printStackTrace();
								}
							});
						});
					}

					break;

				case "FILE_DATA":
					chunk = parts[3];
					transferId = parts[2];

					transfer = fileTransfers.get(transferId);
					if (transfer != null) {
						try {
							// Décoder les données Base64
							byte[] data = Base64.getDecoder().decode(chunk);

							// Écrire les données dans le fichier
							try (FileOutputStream fos = new FileOutputStream(transfer.file, true)) {
								fos.write(data);
							}

							// Mettre à jour la progression du transfert
							transfer.bytesReceived += data.length;

						} catch (IOException e) {
							System.err.println("Erreur lors de la réception des données du fichier: " + e.getMessage());
							e.printStackTrace();
						}
					}

					break;

				case "FILE_INFO":
					sender = parts[1];
					fileName = parts[3];
					fileSize = parts[4];
					transferId = parts[2];

					// Informer l'utilisateur de la réception d'un fichier
					controller.addMessage("Réception du fichier: " + fileName + " (" + this.formatFileSize(Long.parseLong(fileSize)) + ")", false, sender);

					// Créer un répertoire pour les téléchargements s'il n'existe pas
					File downloadsDir = new File("Alanya_Downloads");
					if (!downloadsDir.exists()) {
						if (!downloadsDir.mkdirs()) {
							System.err.println("Erreur lors de la création du dossier de réception.");
							return;
						}
					}

					// Créer un fichier pour recevoir les données
					File receivedFile = new File(downloadsDir, fileName);

					// Stocker les informations sur le transfert en cours
					fileTransfers.put(transferId, new FileTransfer(sender, receivedFile, Long.parseLong(fileSize)));

					break;

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
