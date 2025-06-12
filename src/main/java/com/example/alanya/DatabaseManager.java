package com.example.alanya;

import java.sql.*;
import java.time.LocalDateTime;

public class DatabaseManager {
	private static final String URL = "jdbc:mysql://localhost:3306/alanya_infos";
	private static final String USER = "root";
	private static final String PASSWORD = ""; // À modifier

	private static Connection connection;

	static {
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			connection = DriverManager.getConnection(URL, USER, PASSWORD);
		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
	}

	public static int createUser(String userName, String phoneNumber, String profilePicture, String codeAccess) throws SQLException {
		String sql = "INSERT INTO User (userName, phoneNumber, profilPicture, codeAcess) VALUES (?, ?, ?, ?)";
		try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			stmt.setString(1, userName);
			stmt.setString(2, phoneNumber);
			stmt.setString(3, profilePicture);
			stmt.setString(4, codeAccess);
			stmt.executeUpdate();

			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				return rs.getInt(1);
			}
		}
		return -1;
	}

	public static void saveConnectedUser(int userID, String ipAddress) throws SQLException {
		String sql = "INSERT INTO ConnectedUser (UserID, Status, TimeLastConnection, IpAdress) VALUES (?, ?, ?, ?)";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, userID);
			stmt.setBoolean(2, true);
			stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
			stmt.setString(4, ipAddress);
			stmt.executeUpdate();
		}
	}

	public static void updateUserStatus(int userID, boolean status) throws SQLException {
		String sql = "UPDATE ConnectedUser SET Status = ?, TimeLastConnection = ? WHERE UserID = ?";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setBoolean(1, status);
			stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
			stmt.setInt(3, userID);
			stmt.executeUpdate();
		}
	}

	public static void saveMessage(int senderID, int receiverID, String content, String mediaURL) throws SQLException {
		String sql = "INSERT INTO Message (senderID, receiverID, content, mediaURL, Status, sendMessageTime) VALUES (?, ?, ?, ?, ?, ?)";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, senderID);
			stmt.setInt(2, receiverID);
			stmt.setString(3, content);
			stmt.setString(4, mediaURL);
			stmt.setString(5, "sent");
			stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
			stmt.executeUpdate();
		}
	}

	public static ResultSet getMessageHistory(int userID1, int userID2) throws SQLException {
		String sql = "SELECT * FROM Message WHERE (senderID = ? AND receiverID = ?) OR (senderID = ? AND receiverID = ?) ORDER BY sendMessageTime";
		PreparedStatement stmt = connection.prepareStatement(sql);
		stmt.setInt(1, userID1);
		stmt.setInt(2, userID2);
		stmt.setInt(3, userID2);
		stmt.setInt(4, userID1);
		return stmt.executeQuery();
	}

	public static int getUserIdByUsername(String username) throws SQLException {
		String sql = "SELECT userID FROM User WHERE userName = ?";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt("userID");
			}
		}
		return -1;
	}

	public static int authenticateUser(String username, String password) throws SQLException {
		String sql = "SELECT userID FROM User WHERE userName = ? AND codeAcess = ?";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setString(1, username);
			stmt.setString(2, password);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt("userID");
			}
		}
		return -1;
	}
}