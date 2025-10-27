package entities;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "books") // اسم الجدول في الـ DB
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    // نحزن الحالة كما هي في الجدول: Available / Borrowed
    @Column(nullable = false)
    private String status;

    // علاقة عكسية اختيارية (نخليها Lazy لتقليل التحميل)
    @OneToMany(mappedBy = "book", fetch = FetchType.LAZY)
    private List<Borrowing> borrowings;

    public Book() {
    }

    public Book(String title, String author, String status) {
        this.title = title;
        this.author = author;
        this.status = status;
    }

    public Book(Integer id, String title, String author, String status) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.status = status;
    }

    // === getters/setters ===
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return title + " (" + author + ")";
    }
}
