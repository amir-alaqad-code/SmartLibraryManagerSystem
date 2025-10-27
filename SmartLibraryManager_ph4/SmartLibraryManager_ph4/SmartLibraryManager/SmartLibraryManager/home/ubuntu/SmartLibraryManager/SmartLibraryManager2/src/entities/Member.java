package entities;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    // الاتصال/الهاتف ممكن يكون null
    private String contact;

    // علاقة عكسية اختيارية
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private List<Borrowing> borrowings;

    public Member() {
    }

    public Member(String name) {
        this.name = name;
    }

    public Member(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Member(Integer id, String name, String contact) {
        this.id = id;
        this.name = name;
        this.contact = contact;
    }

    // === getters/setters ===
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return name;
    }
}
