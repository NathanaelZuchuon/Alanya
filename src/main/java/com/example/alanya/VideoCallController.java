package com.example.alanya;

import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

public class VideoCallController {

	@FXML private BorderPane videoCallPane;
	@FXML private ImageView localVideoView;
	@FXML private ImageView remoteVideoView;
	@FXML private Label callStatusLabel;
	@FXML private Button endCallButton;
	@FXML private Label partnerNameLabel;

	private Client currentClient;

	@FXML
	public void initialize() {
		// Configuration de base
		callStatusLabel.setText("Connexion en cours...");

		// Action du bouton de fin d'appel
		endCallButton.setOnAction(event -> endCall());
	}

	public void setCurrentClient(Client client) {
		this.currentClient = client;
	}

	public void setPartnerName(String name) {
		partnerNameLabel.setText("Appel avec " + name);
		callStatusLabel.setText("Appel en cours avec " + name);
	}

	public ImageView getLocalVideoView() {
		return localVideoView;
	}

	public void updateLocalVideo(Image image) {
		localVideoView.setImage(image);
	}

	public void updateRemoteVideo(Image image) {
		remoteVideoView.setImage(image);
		callStatusLabel.setText("Connecté");
	}

	@FXML
	private void endCall() {
		if (currentClient != null) {
			currentClient.endVideoCall();
		}
	}
}
