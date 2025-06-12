package com.example.alanya;

import javafx.fxml.FXML;
import javafx.stage.Stage;
import java.sql.SQLException;
import javafx.scene.control.*;

public class LoginController {
	@FXML private Button loginButton;
	@FXML private Button registerButton;
	@FXML private TextField usernameField;
	@FXML private PasswordField passwordField;

	private Client clientApp;

	public void setClientApp(Client app) {
		this.clientApp = app;
	}

	@FXML
	private void onLogin() {
		String username = usernameField.getText().trim();
		String password = passwordField.getText().trim();

		if (username.isEmpty() || password.isEmpty()) {
			showAlert("Veuillez remplir tous les champs");
			return;
		}

		try {
			int userID = DatabaseManager.authenticateUser(username, password);
			if (userID != -1) {
				clientApp.setCredentials(username, userID);
				clientApp.showMainWindow((Stage) loginButton.getScene().getWindow());
			} else {
				showAlert("Nom d'utilisateur ou mot de passe incorrect");
			}
		} catch (Exception e) {
			showAlert("Erreur de connexion");
		}
	}

	@FXML
	private void onRegister() {
		String username = usernameField.getText().trim();
		String password = passwordField.getText().trim();

		if (username.isEmpty() || password.isEmpty()) {
			showAlert("Veuillez remplir tous les champs");
			return;
		}

		try {
			int userID = DatabaseManager.createUser(username, "", "", password);
			if (userID != -1) {
				showAlert("Compte créé avec succès ! Vous pouvez maintenant vous connecter.");
				usernameField.clear();
				passwordField.clear();
			}
		} catch (SQLException e) {
			showAlert("Ce nom d'utilisateur existe déjà");
		}
	}

	private void showAlert(String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setContentText(message);
		alert.showAndWait();
	}
}