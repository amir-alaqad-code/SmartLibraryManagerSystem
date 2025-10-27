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
import javax.persistence.NoResultException;
import java.util.regex.Pattern;

public class SignUpController {
    @FXML private TextField firstName;
    @FXML private TextField lastName;
    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private PasswordField confirm;

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void navigateTo(ActionEvent e, String fxml) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 750));
        stage.setResizable(false);
    }

    @FXML
    private void handleSignUp(ActionEvent e) throws Exception {
        String fName = (firstName.getText() == null) ? "" : firstName.getText().trim();
        String lName = (lastName.getText() == null)  ? "" : lastName.getText().trim();
        String userEmail = (email.getText() == null) ? "" : email.getText().trim();
        String userPassword = password.getText();
        String confirmPassword = confirm.getText();

        // فحوصات إدخال
        if (fName.isEmpty() || lName.isEmpty() || userEmail.isEmpty() ||
                userPassword.isEmpty() || confirmPassword.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Sign Up Error", "Please fill in all required fields.");
            return;
        }
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        if (!Pattern.compile(emailRegex).matcher(userEmail).matches()) {
            showAlert(Alert.AlertType.ERROR, "Sign Up Error", "Please enter a valid email address.");
            return;
        }
        if (!userPassword.equals(confirmPassword)) {
            showAlert(Alert.AlertType.ERROR, "Sign Up Error", "Passwords do not match.");
            return;
        }

        String hashedPassword = PasswordUtil.md5(userPassword);

        EntityManager em = JPAUtil.getEM();
        try {
            // فحص تكرار البريد
            boolean exists;
            try {
                em.createQuery("SELECT u.id FROM User u WHERE u.email = :email", Integer.class)
                  .setParameter("email", userEmail)
                  .getSingleResult();
                exists = true;
            } catch (NoResultException nre) {
                exists = false;
            }

            if (exists) {
                showAlert(Alert.AlertType.ERROR, "Sign Up Error", "This email is already registered.");
                return;
            }

            // إدراج المستخدم الجديد
            em.getTransaction().begin();
            User u = new User(fName, lName, userEmail, hashedPassword);
            em.persist(u);
            em.getTransaction().commit();

            showAlert(Alert.AlertType.INFORMATION, "Sign Up Success",
                    "Account created successfully! You can now log in.");
            navigateTo(e, "/fxml/Login.fxml");

        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to write to the database.");
        } finally {
            em.close();
        }
    }

    @FXML
    private void backToLogin(ActionEvent e) throws Exception {
        navigateTo(e, "/fxml/Login.fxml");
    }
}
