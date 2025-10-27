package controllers;

import JPA_models.JPAUtil;
import entities.Borrowing;
import repositories.BorrowingRepository;
import repositories.MemberRepository;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;

import javafx.stage.FileChooser;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ReportsController {

    // حدود صالحة لـ SQL بدلاً من LocalDate.MIN/MAX
    private static final LocalDate MIN_SQL = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX_SQL = LocalDate.of(9999, 12, 31);

    // UI
    @FXML
    private Button btnBack;
    @FXML
    private Button btnGenerateReminders;
    @FXML
    private Button btnGenerateFines;
    @FXML
    private Button btnCancel;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private Label statusLabel;
    @FXML
    private TextArea resultArea;

    // فلاتر Phase5
    @FXML
    private DatePicker dpFrom;
    @FXML
    private DatePicker dpTo;
    @FXML
    private CheckBox chkActiveOnly;
    @FXML
    private TextField tfSearch;        // عضو/كتاب
    @FXML
    private TextField tfFinePerDay;    // معدل الغرامة
    @FXML
    private Label lblCount;        // عدّاد النتائج
    @FXML
    private Label lblTotalFine;    // إجمالي الغرامات

    // تصدير
    @FXML
    private Button btnCopy;
    @FXML
    private Button btnSaveCsv;

    private Task<?> currentTask;

    private EntityManagerFactory emf;
    private BorrowingRepository borrowingRepo;
    private MemberRepository memberRepo;

    public ReportsController() {
    }

    @FXML
    public void initialize() {
        EntityManager tmp = JPAUtil.getEM();
        this.emf = tmp.getEntityManagerFactory();
        tmp.close();

        borrowingRepo = new BorrowingRepository(emf);
        memberRepo = new MemberRepository(emf);

        resultArea.setEditable(false);
        statusLabel.setText("Ready");

        // قيم افتراضية للفلترة
        if (dpFrom != null && dpFrom.getValue() == null) {
            dpFrom.setValue(LocalDate.now().minusMonths(6));
        }
        if (dpTo != null && dpTo.getValue() == null) {
            dpTo.setValue(LocalDate.now());
        }
        if (tfFinePerDay != null && (tfFinePerDay.getText() == null || tfFinePerDay.getText().isBlank())) {
            tfFinePerDay.setText("1.0");
        }
    }

    /* ===================== مشغّل عام للخلفية ===================== */
    private <T> void runInBackground(String msg, Supplier<T> supplier, Consumer<T> onSuccess) {
        if (currentTask != null && currentTask.isRunning()) {
            alertInfo("Busy", "Please wait for the current operation or cancel it.");
            return;
        }

        Task<T> task = new Task<>() {
            {
                updateMessage(msg);
            }

            @Override
            protected T call() {
                return supplier.get();
            }
        };

        // ربط عناصر الحالة
        if (progress != null) {
            progress.visibleProperty().bind(task.runningProperty());
        }
        if (statusLabel != null) {
            statusLabel.textProperty().bind(task.messageProperty());
        }
        setButtonsDisabled(true, task);

        task.setOnSucceeded(e -> {
            unbindState();
            if (statusLabel != null) {
                statusLabel.setText("Done");
            }
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            unbindState();
            if (statusLabel != null) {
                statusLabel.setText("Failed");
            }
            String m = (task.getException() == null) ? "Unknown error" : task.getException().getMessage();
            alertError("Error", m);
        });

        currentTask = task;
        new Thread(task, "reports-bg").start();
    }

    private void setButtonsDisabled(boolean running, Task<?> task) {
        if (btnCancel != null) {
            btnCancel.disableProperty().bind(task.runningProperty().not());
        }
        if (btnGenerateReminders != null) {
            btnGenerateReminders.disableProperty().bind(task.runningProperty());
        }
        if (btnGenerateFines != null) {
            btnGenerateFines.disableProperty().bind(task.runningProperty());
        }
        if (btnBack != null) {
            btnBack.disableProperty().bind(task.runningProperty());
        }
        if (btnCopy != null) {
            btnCopy.disableProperty().bind(task.runningProperty());
        }
        if (btnSaveCsv != null) {
            btnSaveCsv.disableProperty().bind(task.runningProperty());
        }
    }

    private void unbindState() {
        if (progress != null) {
            progress.visibleProperty().unbind();
        }
        if (statusLabel != null) {
            statusLabel.textProperty().unbind();
        }
        if (btnCancel != null) {
            btnCancel.disableProperty().unbind();
        }
        if (btnGenerateReminders != null) {
            btnGenerateReminders.disableProperty().unbind();
        }
        if (btnGenerateFines != null) {
            btnGenerateFines.disableProperty().unbind();
        }
        if (btnBack != null) {
            btnBack.disableProperty().unbind();
        }
        if (btnCopy != null) {
            btnCopy.disableProperty().unbind();
        }
        if (btnSaveCsv != null) {
            btnSaveCsv.disableProperty().unbind();
        }
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null) {
            currentTask.cancel();
        }
    }

    /* ===================== تنقّل ===================== */
    @FXML
    private void handleBack(ActionEvent e) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Dashboard.fxml"));
            ((Node) e.getSource()).getScene().setRoot(root);
        } catch (Exception ex) {
            alertError("Navigation", "Cannot open Dashboard:\n" + ex.getMessage());
        }
    }

    /* ===================== توليد التذكيرات ===================== */
    @FXML
    private void handleGenerateReminders() {
        runInBackground("Generating reminders...", () -> {
            var ctx = readFilters();
            List<Borrowing> list = borrowingRepo.findWithRefs(
                    ctx.activeOnly, ctx.fromDate, ctx.toDate
            );

            String q = ctx.query;
            LocalDate today = LocalDate.now();

            String text = list.stream()
                    .filter(b -> b.getDueDate() != null)
                    // بالنسبة للتذكيرات: نهتم بما هو اقترب أو وصل أو تجاوز الاستحقاق
                    .filter(b -> !today.isBefore(b.getDueDate().minusDays(2)))
                    .filter(b -> q.isEmpty()
                    || (safeMember(b).toLowerCase().contains(q))
                    || (safeTitle(b).toLowerCase().contains(q)))
                    .sorted(Comparator.comparing(Borrowing::getDueDate))
                    .map(b -> String.format(
                    "Reminder — Member: %s, Book: '%s', Borrowed: %s, Due: %s",
                    safeMember(b), safeTitle(b), safeDate(b.getBorrowDate()), safeDate(b.getDueDate())))
                    .collect(Collectors.joining("\n"));

            return new ResultBundle(text, list.size(), 0.0);
        }, res -> {
            resultArea.setText(res.text.isEmpty() ? "No reminders." : res.text);
            updateCounters(res.count, 0.0);
        });
    }

    /* ===================== حساب الغرامات ===================== */
    @FXML
    private void handleGenerateFines() {
        runInBackground("Calculating fines...", () -> {
            var ctx = readFilters();
            List<Borrowing> list = borrowingRepo.findWithRefs(
                    ctx.activeOnly, ctx.fromDate, ctx.toDate
            );

            double finePerDay = parseFine(tfFinePerDay == null ? "1.0" : tfFinePerDay.getText());
            LocalDate today = LocalDate.now();
            double[] total = new double[]{0.0};

            String text = list.stream()
                    .filter(b -> b.getDueDate() != null && today.isAfter(b.getDueDate()))
                    .filter(b -> !ctx.activeOnly || b.getReturnDate() == null) // لو Active only مفعّل
                    .filter(b -> ctx.query.isEmpty()
                    || (safeMember(b).toLowerCase().contains(ctx.query))
                    || (safeTitle(b).toLowerCase().contains(ctx.query)))
                    .sorted(Comparator.comparing(Borrowing::getDueDate))
                    .map(b -> {
                        long daysLate;
                        if (b.getReturnDate() == null) {
                            daysLate = ChronoUnit.DAYS.between(b.getDueDate(), today);
                        } else {
                            // في حال عرضنا غير النشطة: احتسب التأخير حتى يوم الإرجاع
                            daysLate = ChronoUnit.DAYS.between(b.getDueDate(), b.getReturnDate());
                        }
                        daysLate = Math.max(0, daysLate);
                        double fine = daysLate * finePerDay;
                        total[0] += fine;
                        return String.format(
                                "Fine — Member: %s, Book: '%s', Days late: %d, Fine: $%.2f (Borrowed: %s, Due: %s%s)",
                                safeMember(b), safeTitle(b), daysLate, fine,
                                safeDate(b.getBorrowDate()), safeDate(b.getDueDate()),
                                b.getReturnDate() == null ? "" : ", Returned: " + safeDate(b.getReturnDate()));
                    })
                    .collect(Collectors.joining("\n"));

            return new ResultBundle(text, list.size(), total[0]);
        }, res -> {
            resultArea.setText(res.text.isEmpty() ? "No fines." : res.text + "\n\nTOTAL FINES: $" + String.format("%.2f", res.totalFine));
            updateCounters(res.count, res.totalFine);
        });
    }

    /* ===================== تصدير/نسخ ===================== */
    @FXML
    private void handleCopy() {
        String txt = resultArea.getText();
        if (txt == null) {
            txt = "";
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(txt);
        Clipboard.getSystemClipboard().setContent(content);
        alertInfo("Copied", "Results copied to clipboard.");
    }

    @FXML
    private void handleSaveCsv() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Report as CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            fc.setInitialFileName("report.csv");
            File file = fc.showSaveDialog(resultArea.getScene().getWindow());
            if (file == null) {
                return;
            }

            // نحول النص الحالي لـ CSV بسيط (سطر لكل نتيجة)
            String txt = resultArea.getText() == null ? "" : resultArea.getText();
            String csv = "Row\n" + txt.lines()
                    .map(line -> "\"" + line.replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining("\n"));

            try (FileWriter fw = new FileWriter(file)) {
                fw.write(csv);
            }
            alertInfo("Saved", "CSV file saved successfully.");
        } catch (Exception ex) {
            alertError("Save CSV", ex.getMessage());
        }
    }

    /* ===================== Helpers ===================== */
   private static class Filters {
    final LocalDate fromDate;
    final LocalDate toDate;
    final boolean activeOnly;
    final String query;

    Filters(LocalDate fromDate, LocalDate toDate, boolean activeOnly, String query) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.activeOnly = activeOnly;
        this.query = (query == null) ? "" : query;
    }
}

    private Filters readFilters() {
        LocalDate from = (dpFrom == null || dpFrom.getValue() == null) ? MIN_SQL : dpFrom.getValue();
        LocalDate to = (dpTo == null || dpTo.getValue() == null) ? MAX_SQL : dpTo.getValue();
        if (to.isBefore(from)) { // تصحيح بسيط
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        boolean activeOnly = chkActiveOnly != null && chkActiveOnly.isSelected();
        String q = (tfSearch == null || tfSearch.getText() == null) ? "" : tfSearch.getText().trim().toLowerCase();
        return new Filters(from, to, activeOnly, q);
    }

    private double parseFine(String s) {
        try {
            return Math.max(0, Double.parseDouble(s.trim()));
        } catch (Exception ignore) {
            return 1.0;
        }
    }

    private static class ResultBundle {

        final String text;
        final int count;
        final double totalFine;

        ResultBundle(String t, int c, double f) {
            this.text = t;
            this.count = c;
            this.totalFine = f;
        }
    }

    private void updateCounters(int count, double totalFine) {
        if (lblCount != null) {
            lblCount.setText(count + " records");
        }
        if (lblTotalFine != null) {
            lblTotalFine.setText("$" + String.format("%.2f", totalFine));
        }
    }

    private String safeTitle(Borrowing b) {
        return (b.getBook() == null) ? "N/A" : b.getBook().getTitle();
    }

    private String safeMember(Borrowing b) {
        return (b.getMember() == null) ? "N/A" : b.getMember().getName();
    }

    private String safeDate(LocalDate d) {
        return (d == null) ? "N/A" : d.toString();
    }

    private void alertInfo(String title, String msg) {
        showAlert(Alert.AlertType.INFORMATION, title, msg);
    }

    private void alertError(String title, String msg) {
        showAlert(Alert.AlertType.ERROR, title, msg);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }
}
