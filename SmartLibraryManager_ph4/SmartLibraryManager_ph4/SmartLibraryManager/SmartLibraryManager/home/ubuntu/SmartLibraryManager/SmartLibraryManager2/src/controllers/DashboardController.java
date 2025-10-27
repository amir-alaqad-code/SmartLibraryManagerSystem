package controllers;

import JPA_models.JPAUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;

import javax.persistence.EntityManager;

/**
 * DashboardController
 * - تحديث الإحصاءات يتم في Task بالخلفية لمنع تجمّد الواجهة
 * - عند الضغط المتكرر على Refresh: نلغي المهمة السابقة ونشغّل الجديدة
 * - في حال الخطأ: نعرض Alert واضح للمستخدم (بدون تفاصيل حسّاسة)
 */
public class DashboardController {

    // إحصائيات معروضة على الداشبورد
    @FXML private Label lblBooksTotal;
    @FXML private Label lblBooksAvailable;
    @FXML private Label lblMembers;
    @FXML private Label lblBorrowingsActive;

    // عناصر اختيارية للحالة/التقدّم (أضِفها في FXML إن أردت)
    @FXML private ProgressIndicator piLoading;   // fx:id="piLoading" (اختياري)
    @FXML private Label lblStatus;               // fx:id="lblStatus"  (اختياري)

    // نحتفظ بآخر مهمة تشغيل لنتحكّم في إلغائها عند الحاجة
    private Task<Stats> currentTask;

    @FXML
    public void initialize() {
        refreshStats(); // أول تحميل عند فتح الداشبورد
    }

    @FXML
    private void handleRefresh() {
        refreshStats();
    }

    /**
     * يشغّل مهمة غير متزامنة لتحميل الإحصاءات من قاعدة البيانات.
     * يحافظ على استعمال JPA (بدون JDBC) مع إغلاق EntityManager داخل المهمة.
     */
    private void refreshStats() {
        // لو في مهمة سابقة شغّالة، نطلب إلغائها بلطف
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel(true);
        }

        currentTask = new Task<Stats>() {
            @Override
            protected Stats call() {
                updateMessage("Loading totals...");
                updateProgress(0, 4);

                EntityManager em = null;
                try {
                    em = JPAUtil.getEM(); // أو getEntityManager() لو أردت، الكلاس يدعم الطريقتين
                    // 1) عدد الكتب الكلي
                    long totalBooks = em.createQuery(
                            "SELECT COUNT(b) FROM Book b", Long.class)
                            .getSingleResult();
                    updateProgress(1, 4);
                    updateMessage("Counting available books...");

                    // 2) عدد الكتب المتاحة
                    long availableBooks = em.createQuery(
                            "SELECT COUNT(b) FROM Book b WHERE LOWER(b.status)='available'", Long.class)
                            .getSingleResult();
                    updateProgress(2, 4);
                    updateMessage("Counting members...");

                    // 3) عدد الأعضاء
                    long members = em.createQuery(
                            "SELECT COUNT(m) FROM Member m", Long.class)
                            .getSingleResult();
                    updateProgress(3, 4);
                    updateMessage("Counting active borrowings...");

                    // 4) عدد الاستعارات النشطة
                    long activeBorrows = em.createQuery(
                            "SELECT COUNT(bw) FROM Borrowing bw WHERE bw.returnDate IS NULL", Long.class)
                            .getSingleResult();
                    updateProgress(4, 4);
                    updateMessage("Done.");

                    return new Stats(totalBooks, availableBooks, members, activeBorrows);
                } catch (Exception ex) {
                    // نرمي الاستثناء ليُلتقط في onFailed (عشان نعرض Alert مرتب)
                    throw ex;
                } finally {
                    if (em != null && em.isOpen()) {
                        em.close();
                    }
                }
            }
        };

        // إن وُجدت عناصر حالة في الواجهة، نربطها بالمهمة
        if (piLoading != null) {
            piLoading.progressProperty().unbind();
            piLoading.progressProperty().bind(currentTask.progressProperty());
            piLoading.setVisible(true);
        }
        if (lblStatus != null) {
            lblStatus.textProperty().unbind();
            lblStatus.textProperty().bind(currentTask.messageProperty());
        }

        // عند النجاح: نحدّث اللابلز على خيط JavaFX تلقائيًا
        currentTask.setOnSucceeded(ev -> {
            Stats s = currentTask.getValue();
            lblBooksTotal.setText(String.valueOf(s.totalBooks));
            lblBooksAvailable.setText(String.valueOf(s.availableBooks));
            lblMembers.setText(String.valueOf(s.members));
            lblBorrowingsActive.setText(String.valueOf(s.activeBorrows));

            // فكّ الربط وإخفاء المؤشر إن وُجد
            if (piLoading != null) {
                piLoading.progressProperty().unbind();
                piLoading.setVisible(false);
            }
            if (lblStatus != null) {
                lblStatus.textProperty().unbind();
                lblStatus.setText("Ready");
            }
        });

        // عند الفشل: نعرض رسالة ودّية، ونحافظ على الواجهة شغّالة
        currentTask.setOnFailed(ev -> {
            if (piLoading != null) {
                piLoading.progressProperty().unbind();
                piLoading.setVisible(false);
            }
            if (lblStatus != null) {
                lblStatus.textProperty().unbind();
                lblStatus.setText("Error");
            }
            Throwable ex = currentTask.getException();
            showError("Database Error",
                    "Failed to connect or query the database.",
                    ex == null ? null : ex.getMessage());
        });

        // عند الإلغاء: نعيد ضبط الحالة ببساطة
        currentTask.setOnCancelled(ev -> {
            if (piLoading != null) {
                piLoading.progressProperty().unbind();
                piLoading.setVisible(false);
            }
            if (lblStatus != null) {
                lblStatus.textProperty().unbind();
                lblStatus.setText("Canceled");
            }
        });

        // تشغيل المهمة في خيط منفصل
        new Thread(currentTask, "dashboard-stats-loader").start();
    }

    // تنقّلات
    private void navigateTo(ActionEvent e, String fxmlPath, int w, int h) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, w, h));
        stage.setResizable(true);
    }

    @FXML private void handleBooks(ActionEvent e) throws Exception {
        navigateTo(e, "/fxml/Books.fxml", 900, 750);
    }
    @FXML private void handleMembers(ActionEvent e) throws Exception {
        navigateTo(e, "/fxml/Members.fxml", 900, 750);
    }
    @FXML private void handleBorrowing(ActionEvent e) throws Exception {
        navigateTo(e, "/fxml/Borrowing.fxml", 900, 750);
    }
    @FXML private void handleReports(ActionEvent e) throws Exception {
        navigateTo(e, "/fxml/Reports.fxml", 900, 900);
    }

    @FXML
    private void handleSignOut(ActionEvent e) throws Exception {
        // يفضّل إلغاء أي مهمة جارية عند الخروج
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel(true);
        }
        navigateTo(e, "/fxml/Login.fxml", 600, 420);
    }

    // ===== أدوات مساعدة بسيطة =====
    private void showError(String title, String header, String details) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(details == null ? "" : details);
            alert.showAndWait();
        });
    }

    /** حاوية صغيرة للأرقام لقراءة أسهل */
    private static class Stats {
        final long totalBooks, availableBooks, members, activeBorrows;
        Stats(long totalBooks, long availableBooks, long members, long activeBorrows) {
            this.totalBooks = totalBooks;
            this.availableBooks = availableBooks;
            this.members = members;
            this.activeBorrows = activeBorrows;
        }
    }
}
