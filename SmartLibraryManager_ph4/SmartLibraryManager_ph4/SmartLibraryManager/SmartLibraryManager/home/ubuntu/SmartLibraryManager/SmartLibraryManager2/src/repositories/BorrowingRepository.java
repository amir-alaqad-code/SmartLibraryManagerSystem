package repositories;

import entities.Borrowing;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;

public class BorrowingRepository {
    private final EntityManagerFactory emf;
    public BorrowingRepository(EntityManagerFactory emf) { this.emf = emf; }

    /** هل يوجد استعارة نشطة لهذا الكتاب؟ (بالـ id لتجنب detached) */
    public boolean existsActiveByBook(entities.Book book) {
        EntityManager em = emf.createEntityManager();
        try {
            Long c = em.createQuery(
                    "SELECT COUNT(bw) FROM Borrowing bw " +
                    "WHERE bw.book.id = :bid AND bw.returnDate IS NULL", Long.class)
                .setParameter("bid", book.getId())
                .getSingleResult();
            return c != 0;
        } finally { em.close(); }
    }

    /** عدد الإعارات النشطة لعضو معين */
    public long countActiveByMember(entities.Member member) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(bw) FROM Borrowing bw " +
                    "WHERE bw.member.id = :mid AND bw.returnDate IS NULL", Long.class)
                .setParameter("mid", member.getId())
                .getSingleResult();
        } finally { em.close(); }
    }

    /** هل لدى العضو إعارات متأخرة حتى تاريخ معين؟ يعتمد على dueDate */
    public boolean hasOverdue(entities.Member member, LocalDate today) {
        EntityManager em = emf.createEntityManager();
        try {
            Long c = em.createQuery(
                    "SELECT COUNT(bw) FROM Borrowing bw " +
                    "WHERE bw.member.id = :mid AND bw.returnDate IS NULL AND bw.dueDate < :t", Long.class)
                .setParameter("mid", member.getId())
                .setParameter("t", today)
                .getSingleResult();
            return c != 0;
        } finally { em.close(); }
    }

    // ------------------------------------------------------------------
    // ⬇️⬇️ التعديل المهم ⬇️⬇️
    // كنا نرجّع "نشطة فقط" وبالتالي السجل يختفي بعد Return.
    // الآن نرجّع "جميع السجلات" ضمن النطاق مع book/member،
    // فيبقى السجل ظاهر لكن بحقل ReturnDate ممتلئ.
    // ------------------------------------------------------------------
    /** جميع الإعارات (نشطة وغير نشطة) ضمن نطاق التاريخ (مع book/member) للعرض في الجدول */
    public List<Borrowing> findByRangeWithRefs(LocalDate from, LocalDate to) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT bw FROM Borrowing bw " +
                    "JOIN FETCH bw.book " +
                    "JOIN FETCH bw.member " +
                    "WHERE bw.borrowDate BETWEEN :f AND :t " +   // ✅ أزلنا شرط returnDate IS NULL
                    "ORDER BY bw.borrowDate DESC", Borrowing.class)
                .setParameter("f", from)
                .setParameter("t", to)
                .getResultList();
        } finally { em.close(); }
    }

    // (اختياري) نسخة مرنة لو حبيت لاحقًا تعرض النشطة فقط بدون لمس الكنترولر كثيرًا
    public List<Borrowing> findWithRefs(Boolean activeOnly, LocalDate from, LocalDate to) {
        EntityManager em = emf.createEntityManager();
        try {
            String jpql =
                "SELECT bw FROM Borrowing bw " +
                "JOIN FETCH bw.book " +
                "JOIN FETCH bw.member " +
                "WHERE bw.borrowDate BETWEEN :f AND :t " +
                (Boolean.TRUE.equals(activeOnly) ? "AND bw.returnDate IS NULL " : "") +
                "ORDER BY bw.borrowDate DESC";
            return em.createQuery(jpql, Borrowing.class)
                .setParameter("f", from)
                .setParameter("t", to)
                .getResultList();
        } finally { em.close(); }
    }

    /** إعارات متأخرة (لتقارير/تذكيرات) */
    public List<Borrowing> findOverdue(LocalDate asOf) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT bw FROM Borrowing bw " +
                    "JOIN FETCH bw.book " +
                    "JOIN FETCH bw.member " +
                    "WHERE bw.returnDate IS NULL AND bw.dueDate < :d " +
                    "ORDER BY bw.dueDate ASC", Borrowing.class)
                .setParameter("d", asOf)
                .getResultList();
        } finally { em.close(); }
    }

    /** إضافة/تعديل استعارة */
    public Borrowing save(Borrowing bw) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Borrowing merged = em.merge(bw);
            em.getTransaction().commit();
            return merged;
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }

    /** إغلاق استعارة (إرجاع كتاب) */
    public void closeBorrowing(Integer id, LocalDate returnDate) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Borrowing ref = em.find(Borrowing.class, id);
            if (ref != null) ref.setReturnDate(returnDate);
            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }

    /** حذف فعلي لسجل استعارة */
    public void deleteById(Integer id) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Borrowing ref = em.find(Borrowing.class, id);
            if (ref != null) em.remove(ref);
            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }
}
