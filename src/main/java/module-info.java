module com.example.alanya {
	requires javafx.web;
    requires javafx.fxml;
	requires javafx.swing;
	requires javafx.controls;
	requires org.bytedeco.opencv;
	requires org.bytedeco.javacv;
	requires java.sql;

	opens com.example.alanya to javafx.fxml;
    exports com.example.alanya;
}