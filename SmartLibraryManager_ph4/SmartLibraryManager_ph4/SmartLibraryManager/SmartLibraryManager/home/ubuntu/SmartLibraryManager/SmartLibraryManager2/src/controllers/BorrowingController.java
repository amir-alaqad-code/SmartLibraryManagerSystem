package controllers;

import JPA_models.JPAUtil;
import entities.Book;
import entities.Borrowing;
import entities.Member;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
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
import repositories.BorrowingRepository;
import repositories.MemberRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BorrowingController {

    // حدود صالحة لـ MySQL
    private static final LocalDate MIN_SQL = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX_SQL = LocalDate.of(9999, 12, 31);

    // مدة الإعارة الافتراضية (لأغراض التظليل البصري فقط)
    private static final int DEFAULT_LOAN_DAYS = 14;

    // ===== عناصر الفورم =====
    @FXML private ComboBox<Book>   bookBox;
    @FXML private ComboBox<Member> memberBox;
    @FXML private DatePicker       borrowDate;

    // ===== البحث/الفرز =====
    @FXML private TextField        searchTextField;
    @FXML private ComboBox<String> searchComboBox;
    @FXML private DatePicker       searchDatePicker;
    @FXML private Button           searchButton;

    // ===== الجدول =====
    @FXML private TableView<Borrowing>              borrowsTable;
    @FXML private TableColumn<Borrowing, Integer>   colId;
    @FXML private TableColumn<Borrowing, String>    colBook;
    @FXML private TableColumn<Borrowing, String>    colMember;
    @FXML private TableColumn<Borrowing, LocalDate> colBorrowDate;
    @FXML private TableColumn<Borrowing, LocalDate> colReturnDate;

    // ===== شريط الحالة/العداد =====
    @FXML private Button            btnCancel;     // اختياري
    @FXML private ProgressIndicator progress;      // اختياري
    @FXML private Label             statusLabel;   // اختياري
    @FXML private Label             lblCount;      // عدّاد نتائج

    // ===== البيانات =====
    private final ObservableList<Borrowing> data = FXCollections.observableArrayList();
    private final ObservableList<Book>   availableBooks = FXCollections.observableArrayList();
    private final ObservableList<Member> allMembers     = FXCollections.observableArrayList();

    private EntityManagerFactory emf;
    private BookRepository bookRepo;
    private MemberRepository memberRepo;
    private BorrowingRepository borrowingRepo;

    private Task<?> currentTask;

    @FXML
    public void initialize() {
        // تهيئة الـ EMF والـ Repositories
        EntityManager tmp = JPAUtil.getEM();
        this.emf = tmp.getEntityManagerFactory();
        tmp.close();

        bookRepo      = new BookRepository(emf);
        memberRepo    = new MemberRepository(emf);
        borrowingRepo = new BorrowingRepository(emf);

        // إعداد أعمدة الجدول
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colReturnDate.setCellValueFactory(new PropertyValueFactory<>("returnDate"));
        colBook.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBook() == null ? "" : c.getValue().getBook().getTitle()));
        colMember.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getMember() == null ? "" : c.getValue().getMember().getName()));
        borrowsTable.setItems(data);

        // تظليل بصري للسجلات المتأخرة
        borrowsTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Borrowing br, boolean empty) {
                super.updateItem(br, empty);
                setStyle("");
                if (empty || br == null) return;
                boolean overdue = (br.getReturnDate() == null)
                        && br.getBorrowDate() != null
                        && br.getBorrowDate().plusDays(DEFAULT_LOAN_DAYS).isBefore(LocalDate.now());
                if (overdue) setStyle("-fx-background-color: rgba(255,0,0,0.10);");
            }
        });

        if (borrowDate.getValue() == null) borrowDate.setValue(LocalDate.now());
        searchComboBox.getItems().setAll("Borrowing Date");

        doReload();
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

    // ربط/فك ربط عناصر الحالة إن وُجدت
    private void bindTaskUI(Task<?> task, String msg) {
        if (progress != null)      progress.visibleProperty().bind(task.runningProperty());
        if (statusLabel != null)   statusLabel.textProperty().bind(task.messageProperty());
        if (btnCancel != null)     btnCancel.disableProperty().bind(task.runningProperty().not());
    }
    private void unbindTaskUI() {
        if (progress != null)      progress.visibleProperty().unbind();
        if (statusLabel != null)   statusLabel.textProperty().unbind();
        if (btnCancel != null)     btnCancel.disableProperty().unbind();
    }

    private <T> void runInBackground(String msg, Supplier<T> supplier, Consumer<T> onSuccess) {
        if (currentTask != null && currentTask.isRunning()) {
            alert(Alert.AlertType.WARNING, "Busy", "Please wait for the current operation to finish or cancel it.");
            return;
        }
        Task<T> task = new Task<>() {
            { updateMessage(msg); }
            @Override protected T call() { return supplier.get(); }
        };

        bindTaskUI(task, msg);

        task.setOnSucceeded(e -> {
            unbindTaskUI();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            unbindTaskUI();
            String m = (task.getException() == null) ? "Unknown error" : task.getException().getMessage();
            alert(Alert.AlertType.ERROR, "Error", m);
        });

        currentTask = task;
        new Thread(task, "borrowing-bg").start();
    }

    @FXML private void handleCancelTask() { if (currentTask != null) currentTask.cancel(); }

    @FXML private void handleRefresh() { doReload(); }

    // إعادة تحميل القوائم والجدول
    @FXML
    public void doReload() {
        runInBackground("Loading...", () -> {
            availableBooks.setAll(bookRepo.findAvailable());
            allMembers.setAll(memberRepo.findAll());
            return borrowingRepo.findByRangeWithRefs(MIN_SQL, MAX_SQL);
        }, list -> {
            bookBox.setItems(availableBooks);
            memberBox.setItems(allMembers);
            data.setAll(list);
            updateCounter(list);
        });
    }

    @FXML
    private void handleBorrowBook() {
        Book b = bookBox.getSelectionModel().getSelectedItem();
        Member m = memberBox.getSelectionModel().getSelectedItem();
        LocalDate d = borrowDate.getValue();

        if (b == null || m == null || d == null) {
            alert(Alert.AlertType.ERROR, "Input Error", "Please select a book, a member, and a borrow date.");
            return;
        }
        if (d.isAfter(LocalDate.now())) {
            alert(Alert.AlertType.ERROR, "Input Error", "Borrow date cannot be in the future.");
            return;
        }

        // سياسات المرحلة الخامسة
        if (borrowingRepo.existsActiveByBook(b)) {
            alert(Alert.AlertType.ERROR, "Unavailable", "This book is already borrowed.");
            return;
        }
        if (borrowingRepo.countActiveByMember(m) >= 5) {
            alert(Alert.AlertType.ERROR, "Limit Reached", "This member has reached the maximum active borrowings.");
            return;
        }
        if (borrowingRepo.hasOverdue(m, LocalDate.now())) {
            alert(Alert.AlertType.ERROR, "Overdue", "This member has overdue borrowings.");
            return;
        }

        runInBackground("Borrowing book...", () -> {
            Borrowing br = new Borrowing();
            br.setBook(b);
            br.setMember(m);
            br.setBorrowDate(d);
            br.setReturnDate(null);
            borrowingRepo.save(br);
            bookRepo.updateStatus(b.getId(), "Available".equalsIgnoreCase(b.getStatus()) ? "Borrowed" : "Borrowed");
            return null;
        }, r -> {
            doReload();
            alert(Alert.AlertType.INFORMATION, "Success", "Book borrowed successfully!");
            clearFields();
        });
    }

    @FXML
    private void handleReturnBook() {
        Borrowing selected = borrowsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert(Alert.AlertType.WARNING, "No Selection", "Please select a borrowing to return.");
            return;
        }
        if (selected.getReturnDate() != null) {
            alert(Alert.AlertType.INFORMATION, "Already Returned", "This borrowing has already been returned.");
            return;
        }

        runInBackground("Returning book...", () -> {
            borrowingRepo.closeBorrowing(selected.getId(), LocalDate.now());
            bookRepo.updateStatus(selected.getBook().getId(), "Available");
            return null;
        }, r -> {
            doReload();
            alert(Alert.AlertType.INFORMATION, "Success", "Book returned successfully!");
        });
    }

    @FXML
    void searchButtonHandler(ActionEvent event) {
        String q = (searchTextField.getText() == null) ? "" : searchTextField.getText().trim().toLowerCase();
        LocalDate d = searchDatePicker.getValue();

        runInBackground("Searching...", () ->
                borrowingRepo.findByRangeWithRefs(MIN_SQL, MAX_SQL).stream()
                        .filter(br -> (q.isEmpty()
                                || (br.getBook() != null && br.getBook().getTitle().toLowerCase().contains(q))
                                || (br.getMember() != null && br.getMember().getName().toLowerCase().contains(q))))
                        .filter(br -> (d == null || d.equals(br.getBorrowDate())))
                        .toList(),
                list -> {
                    data.setAll(list);
                    updateCounter(list);
                    if (list.isEmpty()) alert(Alert.AlertType.INFORMATION, "No Results", "No borrowing records match your search.");
                }
        );
    }

    @FXML
    void searchComboBoxHandler(ActionEvent event) {
        // فرز بسيط بإعادة التحميل من المصدر (حفاظًا على البساطة)
        runInBackground("Sorting...", () -> borrowingRepo.findByRangeWithRefs(MIN_SQL, MAX_SQL),
                list -> { data.setAll(list); updateCounter(list); });
    }

    @FXML
    private void handleDeleteBorrowing() {
        Borrowing selected = borrowsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert(Alert.AlertType.WARNING, "No Selection", "Please select a borrowing to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this borrowing record?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;

        runInBackground("Deleting...", () -> {
            if (selected.getReturnDate() == null) {
                bookRepo.updateStatus(selected.getBook().getId(), "Available");
            }
            borrowingRepo.deleteById(selected.getId());
            return null;
        }, r -> {
            doReload();
            alert(Alert.AlertType.INFORMATION, "Deleted", "Borrowing deleted successfully!");
        });
    }

    private void updateCounter(List<Borrowing> list) {
        if (lblCount == null) return;
        long active = list.stream().filter(br -> br.getReturnDate() == null).count();
        lblCount.setText(list.size() + " records (" + active + " active)");
    }

    private void clearFields() {
        bookBox.getSelectionModel().clearSelection();
        memberBox.getSelectionModel().clearSelection();
        borrowDate.setValue(LocalDate.now());
    }

    @FXML
    private void handleBack(ActionEvent e) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Dashboard.fxml"));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 750));
        stage.setResizable(false);
    }
}
