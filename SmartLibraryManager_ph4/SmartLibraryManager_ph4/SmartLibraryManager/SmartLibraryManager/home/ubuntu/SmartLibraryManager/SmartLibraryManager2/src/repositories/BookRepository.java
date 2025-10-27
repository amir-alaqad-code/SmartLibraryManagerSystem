package repositories;

import entities.Book;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import java.util.List;

public class BookRepository {
    private final EntityManagerFactory emf;
    public BookRepository(EntityManagerFactory emf) { this.emf = emf; }

    /** إرجاع كل الكتب (الأحدث أولًا) */
    public List<Book> findAll() {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<Book> q = em.createQuery("SELECT b FROM Book b ORDER BY b.id DESC", Book.class);
            return q.getResultList();
        } finally { em.close(); }
    }

    /** الكتب المتاحة فقط */
    public List<Book> findAvailable() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT b FROM Book b WHERE LOWER(b.status)='available' ORDER BY b.id DESC", Book.class)
                     .getResultList();
        } finally { em.close(); }
    }

    /** إضافة/تعديل كتاب (merge) */
    public Book save(Book b) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Book merged = em.merge(b);
            em.getTransaction().commit();
            return merged;
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }

    /** حذف كتاب بالمعرّف */
    public void delete(Book b) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Book ref = em.find(Book.class, b.getId());
            if (ref != null) em.remove(ref);
            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }

    /** تحديث حالة الكتاب */
    public void updateStatus(Integer id, String status) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Book ref = em.find(Book.class, id);
            if (ref != null) ref.setStatus(status);
            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }
}
