package com.example.alanya;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ScrollPane;

import java.util.Map;
import java.util.HashMap;

public class ServerController {

	@FXML
	private HBox mainHBox;

	@FXML
	private AnchorPane leftPane;

	@FXML
	private ScrollPane clientsScrollPane;

	@FXML
	private VBox clientsVBox;

	@FXML
	private Button button1;

	@FXML
	private Button button2;

	@FXML
	private AnchorPane statusPane;

	@FXML
	private Text onlineCountText;

	@FXML
	private BorderPane rightPane;

	@FXML
	private Text serverStatusText;

	// Map pour stocker les clients connectés (nom d'utilisateur → handle)
	private final Map<String, ClientHandler> connectedClients = new HashMap<>();

	@FXML
	public void initialize() {
		clientsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		clientsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

		// Initialisation du contrôleur
		serverStatusText.setText("Serveur en écoute permanente...");
		updateOnlineCount();
	}

	public void setServerInstance(Server server) {
		// Référence au serveur principal
	}

	@FXML
	public void onUserClick(ActionEvent event) {
		Button clickedButton = (Button) event.getSource();
		String username = clickedButton.getText();

		// Mettre à jour le statut du serveur pour indiquer l'utilisateur sélectionné
		serverStatusText.setText("Utilisateur sélectionné : " + username);
	}

	public void addClient(String username, ClientHandler clientHandler) {
		// Ajouter le client à la map
		connectedClients.put(username, clientHandler);

		// Créer un nouveau bouton pour ce client
		Button clientButton = new Button(username);
		clientButton.setPrefHeight(40.0);
		clientButton.setPrefWidth(178.0);
		clientButton.setOnAction(this::onUserClick);

		// Ajouter le bouton à l'interface (dans le thread JavaFX)
		javafx.application.Platform.runLater(() -> {
			clientsVBox.getChildren().add(clientButton);
			updateOnlineCount();
		});
	}

	public void removeClient(String username) {
		// Supprimer le client de la map
		connectedClients.remove(username);

		// Supprimer le bouton correspondant (dans le thread JavaFX)
		javafx.application.Platform.runLater(() -> {
			clientsVBox.getChildren().removeIf(node -> {
				if (node instanceof Button) {
					return ((Button) node).getText().equals(username);
				}
				return false;
			});
			updateOnlineCount();
		});
	}

	private void updateOnlineCount() {
		int count = connectedClients.size();
		onlineCountText.setText(count + " en ligne.");
	}
}
