package entities;

// أعلى الملف
import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "borrowings")
public class Borrowing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "borrowing_date", nullable = false)
    private LocalDate borrowDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    // >>> جديد: تاريخ الاستحقاق
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    public Borrowing() {
    }

    public Borrowing(Book book, Member member, LocalDate borrowDate) {
        this.book = book;
        this.member = member;
        this.borrowDate = borrowDate;
    }

    // احسب/حدّث dueDate تلقائياً
    @PrePersist
    @PreUpdate
    private void computeDueDate() {
        if (borrowDate != null) {
            if (dueDate == null || !dueDate.equals(borrowDate.plusDays(14))) {
                dueDate = borrowDate.plusDays(14);
            }
        }
    }

    // getters/setters بما فيها dueDate
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public LocalDate getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(LocalDate borrowDate) {
        this.borrowDate = borrowDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
