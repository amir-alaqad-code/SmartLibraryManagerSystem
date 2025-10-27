package controllers;

import JPA_models.JPAUtil;
import entities.Member;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import repositories.MemberRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MembersController {

    // ===== الجدول =====
    @FXML private TableView<Member> membersTable;
    @FXML private TableColumn<Member, Integer> colId;
    @FXML private TableColumn<Member, String>  colName;
    @FXML private TableColumn<Member, String>  colContact;

    // ===== النموذج =====
    @FXML private TextField nameField;
    @FXML private TextField contactField;

    // ===== البحث/الفرز =====
    @FXML private TextField        searchTextField;
    @FXML private ComboBox<String> searchComboBox; // Name / Contact
    @FXML private Button           searchButton;

    // ===== شريط الأدوات/الحالة =====
    @FXML private Button            btnRefresh;
    @FXML private Button            btnCancel;
    @FXML private ProgressIndicator progress;
    @FXML private Label             statusLabel;
    @FXML private Label             lblCount;   // عدّاد النتائج

    private final ObservableList<Member> memberList = FXCollections.observableArrayList();

    // بيانات (Repository + EMF)
    private EntityManagerFactory emf;
    private MemberRepository memberRepo;

    // مرجع آخر مهمة قيد التشغيل (للإلغاء)
    private Task<?> currentTask;

    @FXML
    public void initialize() {
        // الحصول على EMF مرة واحدة وربط الـRepository (بدون تغييرات جذرية)
        EntityManager tmp = JPAUtil.getEM();
        this.emf = tmp.getEntityManagerFactory();
        tmp.close();
        memberRepo = new MemberRepository(emf);

        // ربط الجدول بخصائص الكيان
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colContact != null) {
            // لو كيان Member عندك فيه حقل "contact" كما هو — ممتاز.
            // لو عندك Email/Phone منفصلين، خبرني أضبط CellValueFactory مخصّص.
            colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));
        }

        // تحميل أولي (بالخلفية)
        doReload();

        // عند اختيار صف: تعبئة حقول التعديل
        membersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, m) -> {
            if (m != null) {
                nameField.setText(m.getName());
                if (contactField != null) contactField.setText(m.getContact());
            } else {
                clearFields();
            }
        });

        // خيارات الفرز
        if (searchComboBox != null) {
            searchComboBox.getItems().setAll("Name", "Contact");
            searchComboBox.getSelectionModel().selectFirst();
        }
    }

    /* ====== أدوات مساعدة ====== */

    // مُشغّل عام لأي عملية بالخلفية + ربط حالة الواجهة
    private <T> void runInBackground(String msg, Supplier<T> supplier, Consumer<T> onSuccess) {
        if (currentTask != null && currentTask.isRunning()) {
            alert(Alert.AlertType.WARNING, "Busy", "Please wait for the current operation or cancel it.");
            return;
        }
        Task<T> task = new Task<>() {
            { updateMessage(msg); }
            @Override protected T call() { return supplier.get(); }
        };

        if (progress != null) progress.visibleProperty().bind(task.runningProperty());
        if (statusLabel != null) statusLabel.textProperty().bind(task.messageProperty());
        if (btnCancel != null) btnCancel.disableProperty().bind(task.runningProperty().not());

        task.setOnSucceeded(e -> {
            if (statusLabel != null) {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Done");
            }
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            if (statusLabel != null) {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Failed");
            }
            alert(Alert.AlertType.ERROR, "Error",
                  String.valueOf(task.getException() == null ? "Unknown error" : task.getException().getMessage()));
        });

        currentTask = task;
        new Thread(task, "members-bg").start();
    }

    @FXML private void handleCancelTask() { if (currentTask != null) currentTask.cancel(); }

    private void alert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(message);
            a.showAndWait();
        });
    }

    private void clearFields() {
        if (nameField != null) nameField.clear();
        if (contactField != null) contactField.clear();
        if (membersTable != null) membersTable.getSelectionModel().clearSelection();
    }

    private void updateCounter(List<Member> list) {
        if (lblCount != null) {
            lblCount.setText(list.size() + " members");
        }
    }

    /* ====== تحميل / تحديث الجدول ====== */

    @FXML
    private void doReload() {
        runInBackground("Loading members...", () -> memberRepo.findAll(), list -> {
            memberList.setAll(list);
            membersTable.setItems(memberList);
            updateCounter(list);
        });
    }

    /* ====== CRUD عبر Repository مع أقل تغييرات ====== */

    // فحص تكرار الاسم (بسيط)
    private boolean isNameDuplicate(String name, Integer exceptId) {
        EntityManager em = emf.createEntityManager();
        try {
            String jpql = (exceptId == null)
                    ? "SELECT COUNT(m) FROM Member m WHERE LOWER(m.name) = :n"
                    : "SELECT COUNT(m) FROM Member m WHERE LOWER(m.name) = :n AND m.id <> :id";
            TypedQuery<Long> q = em.createQuery(jpql, Long.class)
                                   .setParameter("n", name.trim().toLowerCase());
            if (exceptId != null) q.setParameter("id", exceptId);
            return q.getSingleResult() > 0;
        } finally {
            em.close();
        }
    }

    // لا نحذف عضو لديه إعارات نشطة
    private boolean hasActiveBorrowings(Integer memberId) {
        EntityManager em = emf.createEntityManager();
        try {
            Long c = em.createQuery(
                    "SELECT COUNT(bw) FROM Borrowing bw WHERE bw.member.id = :mid AND bw.returnDate IS NULL",
                    Long.class
            ).setParameter("mid", memberId).getSingleResult();
            return c != null && c > 0;
        } finally {
            em.close();
        }
    }

    @FXML
    private void handleAddMember() {
        String name    = nameField.getText() == null ? "" : nameField.getText().trim();
        String contact = contactField == null ? "" :
                (contactField.getText() == null ? "" : contactField.getText().trim());

        if (name.isEmpty()) { alert(Alert.AlertType.ERROR, "Input Error", "Please fill in the name field."); return; }
        if (!name.matches("^[\\p{L} .'-]+$")) { alert(Alert.AlertType.ERROR, "Invalid Name", "Name must contain letters and spaces only."); return; }
        if (isNameDuplicate(name, null)) {
            alert(Alert.AlertType.ERROR, "Duplicate Entry", "A member with this name already exists."); return;
        }

        runInBackground("Adding member...", () -> {
            Member m = new Member();
            m.setName(name);
            m.setContact(contact.isEmpty() ? null : contact);
            memberRepo.save(m);
            return null;
        }, r -> {
            doReload();
            alert(Alert.AlertType.INFORMATION, "Success", "Member added successfully!");
            clearFields();
        });
    }

    @FXML
    private void handleEditMember() {
        Member selectedMember = membersTable.getSelectionModel().getSelectedItem();
        if (selectedMember == null) {
            alert(Alert.AlertType.WARNING, "No Selection", "Please select a member to edit."); return;
        }

        String name    = nameField.getText() == null ? "" : nameField.getText().trim();
        String contact = contactField == null ? "" :
                (contactField.getText() == null ? "" : contactField.getText().trim());

        if (name.isEmpty()) { alert(Alert.AlertType.ERROR, "Input Error", "Please fill in the name field."); return; }
        if (!name.matches("^[\\p{L} .'-]+$")) { alert(Alert.AlertType.ERROR, "Invalid Name", "Name must contain letters and spaces only."); return; }
        if (isNameDuplicate(name, selectedMember.getId())) {
            alert(Alert.AlertType.ERROR, "Duplicate Entry", "A member with this name already exists."); return;
        }

        runInBackground("Updating member...", () -> {
            // نستخدم merge عبر الريبو
            Member m = new Member();
            m.setId(selectedMember.getId());
            m.setName(name);
            m.setContact(contact.isEmpty() ? null : contact);
            memberRepo.save(m);
            return null;
        }, r -> {
            doReload();
            alert(Alert.AlertType.INFORMATION, "Success", "Member updated successfully!");
            clearFields();
        });
    }

    @FXML
    private void handleDeleteMember() {
        Member selected = membersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert(Alert.AlertType.WARNING, "No Selection", "Please select a member to delete.");
            return;
        }

        // سياسة: لا يمكن حذف عضو لديه إعارات نشطة
        if (hasActiveBorrowings(selected.getId())) {
            alert(Alert.AlertType.ERROR, "Cannot Delete", "This member has active borrowings. Please return all borrowed books first.");
            return;
        }

        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this member?", ButtonType.YES, ButtonType.NO);
        c.setHeaderText(null);
        c.showAndWait();
        if (c.getResult() != ButtonType.YES) return;

        runInBackground("Deleting member...", () -> {
            memberRepo.delete(selected);  // قد يفشل لو فيه FK أخرى
            return null;
        }, r -> {
            doReload();
            alert(Alert.AlertType.INFORMATION, "Deleted", "Member deleted successfully.");
            clearFields();
        });
    }

    /* ====== بحث وفرز ====== */

    @FXML
    void searchComboBoxHandler(ActionEvent event) {
        String choice = searchComboBox.getValue();
        if (choice == null) return;

        if (choice.equalsIgnoreCase("Contact") && colContact != null) {
            membersTable.getItems().setAll(
                membersTable.getItems().sorted(Comparator.comparing(m -> {
                    String c = ((Member)m).getContact();
                    return c == null ? "" : c;
                })).stream().toList()
            );
        } else {
            membersTable.getItems().setAll(
                membersTable.getItems().sorted(Comparator.comparing(Member::getName)).stream().toList()
            );
        }
    }

    @FXML
    void searchButtonHandler(ActionEvent event) {
        String query = (searchTextField.getText() == null) ? "" : searchTextField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Empty Search", "Please enter a search value (name/contact)."); return;
        }

        runInBackground("Searching...", () -> {
            EntityManager em = emf.createEntityManager();
            try {
                // بحث بالاسم أو وسيلة الاتصال (بسيط وغير حساس لحالة الأحرف)
                TypedQuery<Member> q = em.createQuery(
                    "SELECT m FROM Member m " +
                    "WHERE LOWER(m.name) LIKE :q OR LOWER(m.contact) LIKE :q " +
                    "ORDER BY m.id DESC", Member.class);
                q.setParameter("q", "%" + query + "%");
                return q.getResultList();
            } finally {
                em.close();
            }
        }, res -> {
            if (res.isEmpty()) {
                doReload();
                alert(Alert.AlertType.WARNING, "No Results", "No results found");
            } else {
                membersTable.getItems().setAll(res);
                updateCounter(res);
            }
        });
    }

    /* ====== رجوع ====== */
    @FXML
    private void handleBack(ActionEvent e) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Dashboard.fxml"));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 750));
        stage.setResizable(false);
    }
}
