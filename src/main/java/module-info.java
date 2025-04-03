module com.example.alanya {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
	requires java.desktop;

	opens com.example.alanya to javafx.fxml;
    exports com.example.alanya;
}