package com.example.alanya;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.text.Text;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;
import javafx.stage.FileChooser;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextFlow;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ScrollPane;

public class ClientController {

	@FXML private HBox mainHBox;
	@FXML private AnchorPane leftPane;
	@FXML private ScrollPane usersScrollPane;
	@FXML private VBox usersVBox;
	@FXML private Button user1Button;
	@FXML private Button user2Button;
	@FXML private StackPane rightPaneContainer;
	@FXML private BorderPane initialRightPane;
	@FXML private Text welcomeText;
	@FXML private VBox chatInterface;
	@FXML private HBox chatHeader;
	@FXML private ImageView profilePicture;
	@FXML private Text userNameText;
	@FXML private Button videoCallButton;
	@FXML private ScrollPane messagesScrollPane;
	@FXML private VBox messagesVBox;
	@FXML private HBox messageInputArea;
	@FXML private Button sendFileButton;
	@FXML private TextField messageInput;
	@FXML private Button sendMessageButton;

	private Client currentClient;
	public void setCurrentClient(Client client) {
		this.currentClient = client;
	}

	private static ClientController instance;
	public static ClientController getInstance() {
		if (instance == null) {
			instance = new ClientController();
		}
		return instance;
	}

	private String currentUser = null;
	private final Map<String, VBox> userChatHistories = new HashMap<>();

	@FXML
	public void initialize() {
		usersScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		usersScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

		// Supprimer les utilisateurs de test
		usersVBox.getChildren().clear();

		// Configurer le champ de saisie de message pour envoyer avec la touche Entrée
		messageInput.setOnAction(this::onSendMessageClick);

		// Rendre l'image de profil ronde
		Circle clip = new Circle(20, 20, 21); // x, y, rayon (moitié de la taille)
		profilePicture.setClip(clip);
	}

	@FXML
	public void onUserClick(ActionEvent event) {
		Button clickedButton = (Button) event.getSource();
		String username = clickedButton.getText();

		// Si on clique sur l'utilisateur actuel, ne rien faire
		if (currentUser != null && currentUser.equals(username)) {
			return;
		}

		// Afficher l'interface de chat
		chatInterface.setVisible(true);
		initialRightPane.setVisible(false);

		// Mettre à jour le nom d'utilisateur dans l'en-tête
		userNameText.setText(username);

		// Charger l'historique des messages pour cet utilisateur
		loadChatHistory(username);

		// Changer l'utilisateur actuel
		currentUser = username;

		System.out.println("Conversation ouverte avec : " + username);
	}

	private void loadChatHistory(String username) {
		// Si l'historique n'existe pas encore, créer un nouveau VBox
		if (!userChatHistories.containsKey(username)) {
			userChatHistories.put(username, new VBox(10));
			userChatHistories.get(username).setPadding(new Insets(10));
		}

		// Remplacer le contenu du VBox de messages par une copie de l'historique de cet utilisateur
		messagesVBox.getChildren().clear();

		// Créer une copie visuelle de chaque message dans l'historique
		VBox userHistory = userChatHistories.get(username);
		for (var node : userHistory.getChildren()) {
			if (node instanceof HBox messageContainer) {
				// Créer une copie de l'élément visuel pour l'affichage
				HBox displayMessageContainer = new HBox();
				displayMessageContainer.setPadding(new Insets(5));
				displayMessageContainer.setMaxWidth(500);
				displayMessageContainer.setAlignment(messageContainer.getAlignment());

				// Copier le contenu du message
				for (var child : messageContainer.getChildren()) {
					if (child instanceof TextFlow originalTextFlow) {
						TextFlow newTextFlow = new TextFlow();
						newTextFlow.setPadding(originalTextFlow.getPadding());
						newTextFlow.setStyle(originalTextFlow.getStyle());

						// Copier le texte
						for (var textNode : originalTextFlow.getChildren()) {
							if (textNode instanceof Text originalText) {
								Text newText = new Text(originalText.getText());
								newText.setFill(originalText.getFill());
								newTextFlow.getChildren().add(newText);
							}
						}

						displayMessageContainer.getChildren().add(newTextFlow);
					}
				}

				messagesVBox.getChildren().add(displayMessageContainer);
			}
		}

		// Faire défiler automatiquement vers le bas pour afficher les derniers messages
		Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
	}

	@FXML
	public void onVideoCallClick(ActionEvent event) {
		System.out.println("Appel vidéo avec : " + currentUser);
	}

	@FXML
	public void onSendFileClick(ActionEvent event) {
		if (currentUser == null) {
			System.out.println("Aucun utilisateur sélectionné pour l'envoi de fichier");
			return;
		}

		// Ouvrir une boîte de dialogue pour sélectionner un fichier
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Sélectionner un fichier à envoyer à " + currentUser);

		// Configurer les filtres de fichiers (optionnel)
		fileChooser.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"),
				new FileChooser.ExtensionFilter("Images", "*.jpg", "*.png", "*.gif"),
				new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt")
		);

		// Afficher la boîte de dialogue et récupérer le fichier sélectionné
		File selectedFile = fileChooser.showOpenDialog(sendFileButton.getScene().getWindow());

		if (selectedFile != null) {
			// Vérifier la taille du fichier (limite à 10 Mo par exemple)
			long fileSize = selectedFile.length();
			if (fileSize > 10 * 1024 * 1024) { // 10 Mo en octets
				Alert alert = new Alert(Alert.AlertType.ERROR);
				alert.setTitle("Fichier trop volumineux");
				alert.setHeaderText("Impossible d'envoyer le fichier");
				alert.setContentText("Le fichier sélectionné est trop volumineux. La taille maximum est de 10 Mo.");
				alert.showAndWait();
				return;
			}

			// Créer un identifiant unique pour le transfert
			String transferId = UUID.randomUUID().toString();

			// Informer l'utilisateur que le fichier est en cours d'envoi
			addMessage("Envoi du fichier: " + selectedFile.getName(), true, currentUser);

			// Envoyer le fichier dans un thread séparé pour ne pas bloquer l'interface
			new Thread(() -> sendFile(selectedFile, transferId, currentUser)).start();

			System.out.println("Envoi du fichier à " + currentUser + ": " + selectedFile.getName());
		}
	}

	private void sendFile(File file, String transferId, String recipient) {
		try {
			// Envoyer d'abord les informations sur le fichier
			String fileInfo = "FILE_INFO:" + recipient + ":" + transferId + ":" + file.getName() + ":" + file.length();
			this.currentClient.sendFileInfo(fileInfo);

			// Attendre une seconde pour s'assurer que le serveur est prêt
			Thread.sleep(1000);

			// Lire le fichier et l'envoyer par morceaux
			try (FileInputStream fileInputStream = new FileInputStream(file)) {
				byte[] buffer = new byte[8192]; // 8 Ko par morceau
				int bytesRead;

				while ((bytesRead = fileInputStream.read(buffer)) != -1) {
					// Préparer le message avec les données binaires
					String chunk = "FILE_DATA:" + recipient + ":" + transferId + ":" + Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, bytesRead));
					this.currentClient.sendFileChunk(chunk);

					// Petite pause pour ne pas surcharger le réseau
					Thread.sleep(50);
				}

				// Signaler la fin du transfert
				this.currentClient.sendMessage("FILE_END", recipient, transferId);

				// Mettre à jour l'interface pour indiquer que le fichier a été envoyé
				addMessage("Fichier envoyé avec succès: " + file.getName(), true, recipient);

			} catch (IOException | InterruptedException e) {
				addMessage("Erreur lors de l'envoi du fichier: " + e.getMessage(), true, recipient);
				this.currentClient.sendMessage("FILE_ERROR", recipient, transferId);

				e.printStackTrace();
			}

		} catch (Exception e) {
			addMessage("Erreur lors de l'envoi du fichier: " + e.getMessage(), true, recipient);

			e.printStackTrace();
		}
	}

	@FXML
	public void onSendMessageClick(ActionEvent event) {
		String messageText = messageInput.getText().trim();

		if (!messageText.isEmpty() && currentUser  != null) {
			// Ajouter le message à l'interface
			addMessage(messageText, true, currentUser );

			// Envoyer le message au serveur
			this.currentClient.sendMessage("MESSAGE", currentUser , messageText);

			// Vider le champ de texte
			messageInput.clear();
		}
	}

	public void addMessage(String messageContent, boolean isSent, String username) {
		// Exécuter sur le thread JavaFX pour éviter les problèmes d'UI
		Platform.runLater(() -> {
			// Créer le container du message
			HBox messageContainer = new HBox();
			messageContainer.setPadding(new Insets(5));
			messageContainer.setMaxWidth(500);

			// Créer le contenu du message
			TextFlow textFlow = new TextFlow();
			Text text = new Text(messageContent);
			text.setFill(isSent ? Color.WHITE : Color.BLACK);
			textFlow.getChildren().add(text);
			textFlow.setPadding(new Insets(8));
			textFlow.setStyle(isSent ?
					"-fx-background-color: #0091EA; -fx-background-radius: 10px;" :
					"-fx-background-color: #B3E5FC; -fx-background-radius: 10px;");

			messageContainer.getChildren().add(textFlow);
			messageContainer.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

			// Ajouter le message à l'historique de l'utilisateur
			if (!userChatHistories.containsKey(username)) {
				userChatHistories.put(username, new VBox(10));
				userChatHistories.get(username).setPadding(new Insets(10));
			}
			userChatHistories.get(username).getChildren().add(messageContainer);

			// Si c'est l'utilisateur actuellement affiché, créer une copie pour l'affichage
			if (username.equals(currentUser)) {
				// Créer une copie pour l'affichage
				HBox displayMessageContainer = new HBox();
				displayMessageContainer.setPadding(new Insets(5));
				displayMessageContainer.setMaxWidth(500);
				displayMessageContainer.setAlignment(messageContainer.getAlignment());

				TextFlow displayTextFlow = new TextFlow();
				Text displayText = new Text(messageContent);
				displayText.setFill(isSent ? Color.WHITE : Color.BLACK);
				displayTextFlow.getChildren().add(displayText);
				displayTextFlow.setPadding(new Insets(8));
				displayTextFlow.setStyle(isSent ?
						"-fx-background-color: #0091EA; -fx-background-radius: 10px;" :
						"-fx-background-color: #B3E5FC; -fx-background-radius: 10px;");

				displayMessageContainer.getChildren().add(displayTextFlow);

				// Ajouter à l'interface visuelle
				messagesVBox.getChildren().add(displayMessageContainer);

				// Faire défiler automatiquement vers le bas après avoir ajouté le message
				messagesScrollPane.setVvalue(1.0);
			}
		});
	}

	public void addUser(String username) {
		// Vérifier si l'utilisateur existe déjà
		for (var node : usersVBox.getChildren()) {
			if (node instanceof Button && ((Button) node).getText().equals(username)) {
				return; // L'utilisateur existe déjà
			}
		}

		Button userButton = new Button(username);
		userButton.setPrefHeight(40.0);
		userButton.setPrefWidth(178.0);
		userButton.setOnAction(this::onUserClick);
		userButton.setStyle("-fx-background-color: #B3E5FC; -fx-text-fill: #01579B;");

		usersVBox.getChildren().add(userButton);
	}

	public void removeUser(String username) {
		usersVBox.getChildren().removeIf(node -> {
			if (node instanceof Button) {
				return ((Button) node).getText().equals(username);
			}
			return false;
		});

		// Si l'utilisateur actuel a été supprimé, revenir à l'écran d'accueil
		if (username.equals(currentUser)) {
			chatInterface.setVisible(false);
			initialRightPane.setVisible(true);
			currentUser = null;
		}
	}
}
