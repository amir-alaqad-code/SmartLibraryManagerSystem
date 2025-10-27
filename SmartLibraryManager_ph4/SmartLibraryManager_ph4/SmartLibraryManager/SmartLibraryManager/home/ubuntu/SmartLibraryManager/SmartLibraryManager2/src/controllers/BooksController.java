package controllers;

import JPA_models.JPAUtil;
import entities.Book;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import repositories.BookRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BooksController {

    // ===== الجدول =====
    @FXML private TableView<Book> booksTable;
    @FXML private TableColumn<Book, Integer> colId;
    @FXML private TableColumn<Book, String> colTitle;
    @FXML private TableColumn<Book, String> colAuthor;
    @FXML private TableColumn<Book, String> colStatus;

    // ===== النموذج =====
    @FXML private TextField titleField;
    @FXML private TextField authorField;
    @FXML private ChoiceBox<String> statusBox;

    // ===== الفلترة / البحث =====
    @FXML private ComboBox<String> searchComboBox;   // Title / Author
    @FXML private TextField searchTextField;         // نص البحث
    @FXML private ComboBox<String> cbFilterStatus;   // All / Available / Borrowed
    @FXML private Button searchButton;

    // ===== شريط الأدوات / الحالة =====
    @FXML private Button btnRefresh;
    @FXML private Button btnCancel;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;
    @FXML private Label lblCount;

    private final ObservableList<Book> bookList = FXCollections.observableArrayList();
    private EntityManagerFactory emf;
    private BookRepository bookRepo;
    private Task<?> currentTask;

    @FXML
    public void initialize() {
        // إعداد الـ EntityManagerFactory والـ Repository
        EntityManager tmp = JPAUtil.getEM();
        this.emf = tmp.getEntityManagerFactory();
        tmp.close();
        bookRepo = new BookRepository(emf);

        // ربط الأعمدة بالكيان
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String dbStatus, boolean empty) {
                super.updateItem(dbStatus, empty);
                if (empty || dbStatus == null) {
                    setText(null);
                    return;
                }
                setText(dbStatus.equalsIgnoreCase("borrowed") ? "Borrowed" : "Available");
            }
        });

        // حالة الكتب في الفورم
        statusBox.setItems(FXCollections.observableArrayList("Available", "Borrowed"));
        statusBox.setValue("Available");

        // الفلاتر (المرحلة الخامسة)
        if (cbFilterStatus != null) {
            cbFilterStatus.getItems().setAll("All", "Available", "Borrowed");
            cbFilterStatus.getSelectionModel().selectFirst();
        }
        if (searchComboBox != null) {
            searchComboBox.getItems().setAll("Title", "Author");
        }

        // تحميل أولي
        doReload();

        // تعبئة الحقول عند اختيار صف
        booksTable.getSelectionModel().selectedItemProperty().addListener((obs, oldB, newB) -> {
            if (newB != null) {
                titleField.setText(newB.getTitle());
                authorField.setText(newB.getAuthor());
                statusBox.setValue(newB.getStatus() != null &&
                        newB.getStatus().equalsIgnoreCase("borrowed") ? "Borrowed" : "Available");
            } else {
                clearFields();
            }
        });
    }

    /* ===== أدوات مساعدة ===== */
    private <T> void runInBackground(String msg, Supplier<T> supplier, Consumer<T> onSuccess) {
        if (currentTask != null && currentTask.isRunning()) {
            alert(Alert.AlertType.WARNING, "Busy", "Please wait or cancel the current task first.");
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
            alert(Alert.AlertType.ERROR, "Error", String.valueOf(task.getException()));
        });
        task.setOnCancelled(e -> {
            if (statusLabel != null) {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Canceled");
            }
        });

        currentTask = task;
        new Thread(task, "books-bg").start();
    }

    @FXML private void handleCancelTask() {
        if (currentTask != null) currentTask.cancel();
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    private void info(String msg) { alert(Alert.AlertType.INFORMATION, "Info", msg); }
    private void warn(String msg) { alert(Alert.AlertType.WARNING, "Warning", msg); }
    private void error(String msg) { alert(Alert.AlertType.ERROR, "Error", msg); }

    private void clearFields() {
        titleField.clear();
        authorField.clear();
        statusBox.setValue("Available");
        booksTable.getSelectionModel().clearSelection();
    }

    private String toDbStatus(String display) {
        if (display == null) return "available";
        return display.toLowerCase().startsWith("b") ? "borrowed" : "available";
    }

    private void updateCounter(List<Book> list) {
        if (lblCount == null) return;
        long available = list.stream().filter(b -> "available".equalsIgnoreCase(String.valueOf(b.getStatus()))).count();
        lblCount.setText(list.size() + " books (" + available + " available)");
    }

    /* ===== تحميل / تحديث ===== */
    @FXML private void doReload() {
        runInBackground("Loading books...", () -> bookRepo.findAll(), list -> {
            bookList.setAll(list);
            booksTable.setItems(bookList);
            updateCounter(list);
        });
    }

    /* ===== CRUD ===== */
    @FXML private void handleAddBook() {
        String title = titleField.getText().trim();
        String author = authorField.getText().trim();
        String statusDb = toDbStatus(statusBox.getValue());
        if (title.isEmpty() || author.isEmpty()) { error("Please fill in all fields."); return; }
        if (!author.matches("^[\\p{L} .'-]+$")) { error("Author name is invalid."); return; }
        if (isTitleDuplicateInDb(title, null)) { error("Book title already exists."); return; }

        runInBackground("Adding book...", () -> {
            Book b = new Book();
            b.setTitle(title);
            b.setAuthor(author);
            b.setStatus(statusDb);
            bookRepo.save(b);
            return null;
        }, r -> {
            doReload();
            info("Book added successfully.");
            clearFields();
        });
    }

    @FXML private void handleEditBook() {
        Book selected = booksTable.getSelectionModel().getSelectedItem();
        if (selected == null) { warn("Please select a book to edit."); return; }

        String title = titleField.getText().trim();
        String author = authorField.getText().trim();
        String statusDb = toDbStatus(statusBox.getValue());

        if (title.isEmpty() || author.isEmpty()) { error("Please fill in all fields."); return; }
        if (!author.matches("^[\\p{L} .'-]+$")) { error("Invalid author name."); return; }
        if (isTitleDuplicateInDb(title, selected.getId())) { error("Another book with this title exists."); return; }

        runInBackground("Updating book...", () -> {
            selected.setTitle(title);
            selected.setAuthor(author);
            selected.setStatus(statusDb);
            bookRepo.save(selected);
            return null;
        }, r -> {
            doReload();
            info("Book updated successfully.");
            clearFields();
        });
    }

    @FXML private void handleDeleteBook() {
        Book selected = booksTable.getSelectionModel().getSelectedItem();
        if (selected == null) { warn("Please select a book to delete."); return; }

        if (isBookCurrentlyBorrowed(selected.getId())) {
            error("This book is currently borrowed and cannot be deleted.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this book?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;

        runInBackground("Deleting book...", () -> {
            bookRepo.delete(selected);
            return null;
        }, r -> {
            doReload();
            info("Book deleted successfully.");
            clearFields();
        });
    }

    /* ===== بحث / فرز ===== */
    @FXML
    void searchButtonHandler(ActionEvent event) {
        String q = (searchTextField.getText() == null) ? "" : searchTextField.getText().trim().toLowerCase();
        String statusFilter = (cbFilterStatus == null || cbFilterStatus.getValue() == null)
                ? "All" : cbFilterStatus.getValue();

        runInBackground("Searching...", () -> {
            EntityManager em = emf.createEntityManager();
            try {
                StringBuilder jpql = new StringBuilder("SELECT b FROM Book b WHERE 1=1");
                if (!q.isEmpty()) jpql.append(" AND (LOWER(b.title) LIKE :q OR LOWER(b.author) LIKE :q)");
                if ("Available".equals(statusFilter)) jpql.append(" AND LOWER(b.status) = 'available'");
                else if ("Borrowed".equals(statusFilter)) jpql.append(" AND LOWER(b.status) = 'borrowed'");
                jpql.append(" ORDER BY b.id DESC");

                TypedQuery<Book> query = em.createQuery(jpql.toString(), Book.class);
                if (!q.isEmpty()) query.setParameter("q", "%" + q + "%");
                List<Book> res = query.getResultList();
                return res;
            } finally { em.close(); }
        }, res -> {
            booksTable.getItems().setAll(res);
            updateCounter(res);
            if (res.isEmpty()) warn("No records found.");
        });
    }

    @FXML
    void searchComboBoxHandler(ActionEvent event) {
        String choice = searchComboBox.getValue();
        if (choice == null) return;
        if (choice.equalsIgnoreCase("Title"))
            booksTable.getItems().setAll(booksTable.getItems().stream()
                    .sorted(Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER)).toList());
        else
            booksTable.getItems().setAll(booksTable.getItems().stream()
                    .sorted(Comparator.comparing(Book::getAuthor, String.CASE_INSENSITIVE_ORDER)).toList());
    }

    /* ===== Back ===== */
    @FXML
    private void handleBack(ActionEvent e) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Dashboard.fxml"));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 750));
        stage.setResizable(false);
    }

    /* ===== Helpers ===== */
    private boolean isTitleDuplicateInDb(String title, Integer exceptId) {
        EntityManager em = emf.createEntityManager();
        try {
            String jpql = (exceptId == null)
                    ? "SELECT COUNT(b) FROM Book b WHERE LOWER(b.title) = :t"
                    : "SELECT COUNT(b) FROM Book b WHERE LOWER(b.title) = :t AND b.id <> :id";
            TypedQuery<Long> q = em.createQuery(jpql, Long.class)
                    .setParameter("t", title.trim().toLowerCase());
            if (exceptId != null) q.setParameter("id", exceptId);
            return q.getSingleResult() > 0;
        } finally {
            em.close();
        }
    }

    private boolean isBookCurrentlyBorrowed(Integer bookId) {
        EntityManager em = emf.createEntityManager();
        try {
            Long c = em.createQuery(
                    "SELECT COUNT(bw) FROM Borrowing bw WHERE bw.book.id = :bid AND bw.returnDate IS NULL",
                    Long.class).setParameter("bid", bookId).getSingleResult();
            return c != null && c > 0;
        } finally { em.close(); }
    }
}
