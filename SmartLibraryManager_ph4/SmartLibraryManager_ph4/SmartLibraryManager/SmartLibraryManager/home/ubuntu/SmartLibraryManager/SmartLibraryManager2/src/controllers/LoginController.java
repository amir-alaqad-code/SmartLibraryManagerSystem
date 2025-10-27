package controllers;

import JPA_models.JPAUtil;
import entities.User;
import utils.PasswordUtil;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import javax.persistence.EntityManager;
import java.util.List;

public class LoginController {
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    public static User currentUser; // المستخدم الحالي

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void navigateTo(ActionEvent e, String fxml) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 750));
        stage.setResizable(false);
    }

    @FXML
    private void handleLogin(ActionEvent e) throws Exception {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText();

        // فحوصات بسيطة
        if (email.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Login Error", "Please fill in all required fields.");
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            showAlert(Alert.AlertType.ERROR, "Login Error", "Please enter a valid email address.");
            return;
        }

        String hashed = PasswordUtil.md5(password);

        EntityManager em = JPAUtil.getEM();
        try {
            // نبحث عن المستخدم بالبريد
            List<User> users = em.createQuery(
                    "SELECT u FROM User u WHERE u.email = :e", User.class)
                    .setParameter("e", email)
                    .getResultList();

            if (users.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Login Error", "This email is not registered.");
                return;
            }

            User u = users.get(0);
            if (!hashed.equals(u.getPasswordHash())) {
                showAlert(Alert.AlertType.ERROR, "Login Error", "Incorrect password.");
                return;
            }

            currentUser = u;
            showAlert(Alert.AlertType.INFORMATION, "Login Successful", "Welcome, " + u.getFirstName() + "!");
            navigateTo(e, "/fxml/Dashboard.fxml");

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to connect or query the database.");
        } finally {
            em.close();
        }
    }

    @FXML
    private void handleGoToSignUp(ActionEvent e) throws Exception {
        navigateTo(e, "/fxml/SignUp.fxml");
    }
}
