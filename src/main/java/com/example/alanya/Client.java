package com.example.alanya;

import java.io.*;
import java.awt.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;

import javax.sound.sampled.*;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.concurrent.Task;
import javafx.stage.StageStyle;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.application.Application;
import javafx.scene.control.ButtonType;

import java.util.Arrays;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import java.awt.image.BufferedImage;

import javafx.embed.swing.SwingFXUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_imgcodecs;

public class Client extends Application {
	private static final String SERVER_ADDRESS = "localhost";
	private static final int SERVER_PORT = 8080;

	private static Socket socket;
	private static PrintWriter out;
	private static BufferedReader in;

	private static int myUserID;
	public static int getMyUserID() {
		return myUserID;
	}

	public static String myUsername;
	private static boolean connected = false;

	// ---
	private static Thread audioThread;
	private static TargetDataLine audioLine;
	private static SourceDataLine audioOutput;
	private static AudioFormat audioFormat;
	private static boolean isAudioTransmitting = false;

	private static Thread videoThread;
	private static FrameGrabber grabber;
	private static boolean isInVideoCall = false;
	private static String videoCallPartner = null;
	private static OpenCVFrameConverter.ToMat converter;
	private static VideoCallController videoCallController;

	public void initiateVideoCall(String recipient) {
		if (connected && out != null) {
			sendMessage("VIDEO_CALL_REQUEST", recipient, "");
			System.out.println("Demande d'appel vidéo envoyée à " + recipient);

		} else {
			System.err.println("Impossible d'initier l'appel vidéo : non connecté.");
		}
	}

	public void acceptVideoCall(String caller) {
		if (connected && out != null) {
			sendMessage("VIDEO_CALL_ACCEPT", caller, "");

			// Ouvrir la fenêtre d'appel vidéo
			Platform.runLater(() -> {
				try {
					startVideoCall(caller);

				} catch (Exception e) {
					System.err.println("Erreur lors du démarrage de l'appel vidéo: " + e.getMessage());
					e.printStackTrace();
				}
			});
		}
	}

	public void rejectVideoCall(String caller) {
		if (connected && out != null) {
			sendMessage("VIDEO_CALL_REJECT", caller, "");
		}
	}

	public void endVideoCall() {
		if (videoCallPartner != null && connected && out != null) {
			sendMessage("VIDEO_CALL_END", videoCallPartner, "");
			stopVideoCall();
		}
	}

	private void startVideoCall(String partner) throws Exception {
		videoCallPartner = partner;
		isInVideoCall = true;

		// Ouvrir la fenêtre d'appel vidéo
		FXMLLoader loader = new FXMLLoader(getClass().getResource("videocall.fxml"));
		Parent root = loader.load();
		videoCallController = loader.getController();
		videoCallController.setCurrentClient(this);
		videoCallController.setPartnerName(partner);

		Stage videoStage = new Stage();
		videoStage.setTitle("Appel vidéo avec " + partner);
		videoStage.setScene(new Scene(root, 1000, 600));

		Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
		videoStage.getIcons().add(icon);

		videoStage.setOnCloseRequest(e -> {
			endVideoCall();
		});

		videoStage.show();

		// Démarrer la capture vidéo
		startVideoCapture();

		// Démarrer la capture audio
		startAudioCapture();
	}

	private void startAudioCapture() {
		// Définir le format audio: 44.1KHz, 16bit, mono
		audioFormat = new AudioFormat(44100.0f, 16, 1, true, false);

		// Créer un thread pour la capture et l'envoi de l'audio
		audioThread = new Thread(() -> {
			try {
				// Récupérer la ligne d'entrée audio (microphone)
				DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
				if (!AudioSystem.isLineSupported(info)) {
					System.err.println("Le format audio n'est pas supporté");
					return;
				}

				audioLine = (TargetDataLine) AudioSystem.getLine(info);
				audioLine.open(audioFormat);
				audioLine.start();

				isAudioTransmitting = true;

				// Créer un buffer pour lire les données audio
				byte[] buffer = new byte[1024];
				int bytesRead;

				// Boucle de capture et d'envoi des données audio
				while (isInVideoCall && isAudioTransmitting) {
					bytesRead = audioLine.read(buffer, 0, buffer.length);
					if (bytesRead > 0) {
						// Encoder et envoyer les données audio
						String encodedAudio = Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, bytesRead));
						sendMessage("AUDIO_DATA", videoCallPartner, encodedAudio);
					}

					// Petite pause pour éviter de surcharger le réseau
					Thread.sleep(10);
				}

			} catch (LineUnavailableException e) {
				System.err.println("Impossible d'accéder au microphone: " + e.getMessage());
				e.printStackTrace();
			} catch (InterruptedException e) {
				// Le thread a été interrompu, c'est normal lors de l'arrêt
			} finally {
				stopAudioCapture();
			}
		});

		audioThread.setDaemon(true);
		audioThread.start();
	}

	private void stopAudioCapture() {
		isAudioTransmitting = false;

		if (audioLine != null) {
			audioLine.stop();
			audioLine.close();
			audioLine = null;
		}

		if (audioOutput != null) {
			audioOutput.stop();
			audioOutput.close();
			audioOutput = null;
		}

		if (audioThread != null && audioThread.isAlive()) {
			audioThread.interrupt();
		}
	}

	private void playAudio(byte[] audioData) {
		try {
			if (audioOutput == null || !audioOutput.isOpen()) {
				// Initialiser la ligne de sortie audio si nécessaire
				DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
				audioOutput = (SourceDataLine) AudioSystem.getLine(info);
				audioOutput.open(audioFormat);
				audioOutput.start();
			}

			// Jouer les données audio
			audioOutput.write(audioData, 0, audioData.length);

		} catch (LineUnavailableException e) {
			System.err.println("Erreur lors de la lecture audio: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void stopVideoCall() {
		isInVideoCall = false;
		videoCallPartner = null;

		// Arrêter la capture vidéo
		if (grabber != null) {
			try {
				grabber.stop();
				grabber.release();

			} catch (Exception e) {
				System.err.println("Erreur lors de l'arrêt de la capture vidéo: " + e.getMessage());
			}
		}

		// Arrêter le thread de capture vidéo
		if (videoThread != null && videoThread.isAlive()) {
			videoThread.interrupt();
		}

		// Arrêter la capture audio
		stopAudioCapture();

		// Fermer la fenêtre d'appel vidéo
		if (videoCallController != null) {
			Platform.runLater(() -> {
				Stage stage = (Stage) videoCallController.getLocalVideoView().getScene().getWindow();
				stage.close();
			});
		}
	}

	private void startVideoCapture() {
		videoThread = new Thread(() -> {
			try {
				// Initialiser la capture vidéo
				grabber = new OpenCVFrameGrabber(0); // 0 pour la webcam par défaut
				converter = new OpenCVFrameConverter.ToMat();
				grabber.start();

				// Boucle de capture et d'envoi des frames
				while (isInVideoCall) {
					try {
						Frame frame = grabber.grab();
						if (frame != null) {
							// Afficher la vidéo locale
							BufferedImage bufferedImage = Java2DFrameUtils.toBufferedImage(frame);
							Image image = SwingFXUtils.toFXImage(bufferedImage, null);

							Platform.runLater(() -> {
								videoCallController.updateLocalVideo(image);
							});

							// Envoyer la frame au partenaire
							sendVideoFrame(frame);

							// Petite pause pour éviter de surcharger le réseau
							Thread.sleep(30); // ~30 FPS
						}

					} catch (InterruptedException e) {
						break;
					}
				}

			} catch (Exception e) {
				System.err.println("Erreur dans la capture vidéo: " + e.getMessage());
				e.printStackTrace();

			} finally {
				isInVideoCall = false;
				try {
					if (grabber != null) {
						grabber.stop();
						grabber.release();
					}

				} catch (Exception e) {
					System.err.println("Erreur lors de la fermeture de la capture vidéo: " + e.getMessage());
				}
			}
		});

		videoThread.setDaemon(true);
		videoThread.start();
	}

	private void sendVideoFrame(Frame frame) {
		if (connected && out != null && videoCallPartner != null) {
			try {
				// Convertir la frame en Mat pour la traiter
				Mat mat = converter.convert(frame);

				// Redimensionner pour réduire la taille des données
				Mat resized = new Mat();
				opencv_imgproc.resize(mat, resized, new Size(320, 240));

				// Convertir en JPEG pour réduire la taille
				BytePointer buf = new BytePointer();
				opencv_imgcodecs.imencode(".jpg", resized, buf);
				byte[] byteArray = new byte[(int)buf.capacity()];
				buf.get(byteArray);

				// Encoder en Base64
				String encodedFrame = Base64.getEncoder().encodeToString(byteArray);

				// Envoyer au partenaire
				sendMessage("VIDEO_FRAME", videoCallPartner, encodedFrame);

				// Libérer la mémoire
				buf.close();

			} catch (Exception e) {
				System.err.println("Erreur lors de l'envoi de la frame vidéo: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	// ---

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

	public static Client th;

//	@Override
//	public void start(Stage stage) throws IOException {
//		FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("client.fxml"));
//		Scene scene = new Scene(fxmlLoader.load(), 900, 600); stage.setResizable(false);
//
//		// Récupération du contrôleur
//		controller = fxmlLoader.getController();
//		controller.setCurrentClient(this);
//
//		// --- IMPORTANT
//		th = this;
//		// ---
//
//		// Configuration de la fenêtre
//		stage.setTitle("Alanya.");
//		stage.setScene(scene);
//
//		Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
//		stage.getIcons().add(icon);
//
//		stage.setOnCloseRequest(e -> closeConnection());
//
//		// Affichage
//		stage.show();
//	}

	@Override
	public void start(Stage stage) throws IOException {
		FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("login.fxml"));
		Scene scene = new Scene(fxmlLoader.load(), 300, 400);

		LoginController loginController = fxmlLoader.getController();
		loginController.setClientApp(this);

		stage.setTitle("Alanya - Connexion");
		stage.setScene(scene);
		stage.setResizable(false);

		Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
		stage.getIcons().add(icon);

		stage.show();
	}

	public void setCredentials(String username, int userID) {
		myUsername = username;
		myUserID = userID;
	}

	public void showMainWindow(Stage loginStage) throws IOException {
		loginStage.close();

		Stage mainStage = new Stage();
		FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("client.fxml"));
		Scene scene = new Scene(fxmlLoader.load(), 900, 600);

		controller = fxmlLoader.getController();
		controller.setCurrentClient(this);

		mainStage.setTitle("Alanya.");
		mainStage.setScene(scene);
		mainStage.setResizable(false);

		Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
		mainStage.getIcons().add(icon);

		mainStage.setOnCloseRequest(e -> closeConnection());
		mainStage.show();

		connectToServer();
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

					// Envoyer le username authentifié au lieu de "me"
					out.println("USER_CONNECTED:SERVER:" + myUsername);
					System.out.println("Connecté au serveur en tant que: " + myUsername);

					// Mettre à jour le statut dans la BD
					DatabaseManager.saveConnectedUser(myUserID, socket.getInetAddress().getHostAddress());

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
				// --- FILE
				case "FILE_ERROR":
					Platform.runLater(() -> {
						Alert alert = new Alert(Alert.AlertType.ERROR);

						alert.setTitle("Erreur d'envoi");

						// ---
						Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
						Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
						stage.getIcons().add(icon);

						StageStyle stageStyle = new Stage().getStyle();
						alert.initStyle(stageStyle);
						// ---

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

							// ---
							Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
							Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
							stage.getIcons().add(icon);

							StageStyle stageStyle = stage.getStyle();
							alert.initStyle(stageStyle);
							// ---

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
				// ---

				// --- MESSAGE
				case "MESSAGE":
					sender = parts[1];
					String content = parts[2];

					controller.addMessage(content, false, sender);

					break;
				// ---

				// --- USER
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
				// ---

				// --- VIDEO-CALL
				case "VIDEO_CALL_REQUEST":
					sender = parts[1];

					Platform.runLater(() -> {
						// Afficher une notification d'appel entrant
						Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
						alert.setTitle("Appel vidéo entrant");

						// Configurer l'icône
						Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
						Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
						stage.getIcons().add(icon);

						alert.setHeaderText("Appel vidéo entrant de " + sender);
						alert.setContentText("Voulez-vous accepter cet appel vidéo ?");

						ButtonType acceptButton = new ButtonType("Accepter");
						ButtonType rejectButton = new ButtonType("Refuser", ButtonBar.ButtonData.CANCEL_CLOSE);
						alert.getButtonTypes().setAll(acceptButton, rejectButton);

						alert.showAndWait().ifPresent(response -> {
							if (response == acceptButton) {
								// Accepter l'appel
								th.acceptVideoCall(sender);
							} else {
								// Refuser l'appel
								th.rejectVideoCall(sender);
							}
						});
					});
					break;

				case "VIDEO_CALL_ACCEPT":
					sender = parts[1];

					Platform.runLater(() -> {
						try {
							// Démarrer l'appel vidéo
							th.startVideoCall(sender);

						} catch (Exception e) {
							System.err.println("Erreur lors du démarrage de l'appel vidéo: " + e.getMessage());
							e.printStackTrace();
						}
					});
					break;

				case "VIDEO_CALL_REJECT":
					sender = parts[1];

					Platform.runLater(() -> {
						Alert alert = new Alert(Alert.AlertType.INFORMATION);
						alert.setTitle("Appel refusé");

						// Configurer l'icône
						Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
						Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
						stage.getIcons().add(icon);

						alert.setHeaderText("Appel refusé");
						alert.setContentText(sender + " a refusé votre appel vidéo.");
						alert.showAndWait();
					});
					break;

				case "VIDEO_CALL_END":
					sender = parts[1];

					if (isInVideoCall && videoCallPartner != null && videoCallPartner.equals(sender)) {
						Platform.runLater(() -> {
							// Afficher un message que l'appel a été terminé
							Alert alert = new Alert(Alert.AlertType.INFORMATION);
							alert.setTitle("Appel terminé");

							// Configurer l'icône
							Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
							Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("client.png")));
							stage.getIcons().add(icon);

							alert.setHeaderText("Appel terminé");
							alert.setContentText("L'appel vidéo avec " + sender + " a été terminé.");
							alert.showAndWait();

							// Arrêter l'appel côté local
							th.stopVideoCall();
						});
					}
					break;

				case "VIDEO_FRAME":
					sender = parts[1];
					String frameData = parts[2];

					if (isInVideoCall && videoCallPartner != null && videoCallPartner.equals(sender)) {
						try {
							// Décoder la frame
							byte[] imageData = Base64.getDecoder().decode(frameData);

							// Créer un BytePointer à partir des données décodées
							BytePointer bytePointer = new BytePointer(imageData);

							// Convertir en Mat
							Mat mat = opencv_imgcodecs.imdecode(new Mat(bytePointer), opencv_imgcodecs.IMREAD_COLOR);

							// Convertir en Frame
							Frame frame = converter.convert(mat);

							// Convertir en BufferedImage
							BufferedImage bufferedImage = Java2DFrameUtils.toBufferedImage(frame);

							// Convertir en Image JavaFX
							Image image = SwingFXUtils.toFXImage(bufferedImage, null);

							// Afficher dans l'interface
							Platform.runLater(() -> {
								videoCallController.updateRemoteVideo(image);
							});

							// Libérer les ressources
							bytePointer.close();

						} catch (Exception e) {
							System.err.println("Erreur lors de la réception de la frame vidéo: " + e.getMessage());
							e.printStackTrace();
						}
					}
					break;

				case "AUDIO_DATA":
					sender = parts[1];
					String audioData = parts[2];

					if (isInVideoCall && videoCallPartner != null && videoCallPartner.equals(sender)) {
						try {
							// Décoder les données audio
							byte[] decodedAudio = Base64.getDecoder().decode(audioData);

							// Jouer l'audio
							th.playAudio(decodedAudio);

						} catch (Exception e) {
							System.err.println("Erreur lors de la réception des données audio: " + e.getMessage());
							e.printStackTrace();
						}
					}
					break;
				// ---

				default:
					System.out.println("Message de type inconnu reçu par le client: " + message);
					break;
			}
		}
	}
}
