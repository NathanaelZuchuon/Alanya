package com.example.alanya;

import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ToggleButton;

public class VideoCallController {

	@FXML private BorderPane videoCallPane;
	@FXML private ImageView localVideoView;
	@FXML private ImageView remoteVideoView;
	@FXML private Label callStatusLabel;
	@FXML private Button endCallButton;
	@FXML private Label partnerNameLabel;
	@FXML private ToggleButton muteAudioButton; // Nouveau bouton pour couper/activer le micro

	private Client currentClient;
	private boolean audioMuted = false;

	@FXML
	public void initialize() {
		// Configuration de base
		callStatusLabel.setText("Connexion en cours...");

		// Action du bouton de fin d'appel
		endCallButton.setOnAction(event -> endCall());

		// Ajout de l'action pour le bouton de mute
		if (muteAudioButton != null) {
			muteAudioButton.setOnAction(event -> toggleMuteAudio());
		}
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

	@FXML
	private void toggleMuteAudio() {
		if (currentClient != null) {
			audioMuted = muteAudioButton.isSelected();

			// Accéder à la variable d'instance dans Client
			try {
				java.lang.reflect.Field field = Client.class.getDeclaredField("isAudioTransmitting");
				field.setAccessible(true);
				field.set(currentClient, !audioMuted);
			} catch (Exception e) {
				System.err.println("Erreur lors de la modification de l'état audio: " + e.getMessage());
				e.printStackTrace();
			}

			// Mettre à jour le texte du bouton
			if (audioMuted) {
				muteAudioButton.setText("Activer le micro");
			} else {
				muteAudioButton.setText("Couper le micro");
			}
		}
	}
}
