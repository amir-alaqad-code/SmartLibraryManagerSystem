package repositories;

import entities.Member;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * MemberRepository — JPA فقط، مع دوال مساعدة لمرحلة 5
 * - findAll(): كما كانت
 * - save(): كما كانت
 * - delete(): كما كانت
 * - NEW: findByNameOrContact(String q)
 * - NEW: existsNameIgnoreCase(String name, Integer exceptId)
 * - NEW: hasActiveBorrowings(Integer memberId)
 * - NEW: deleteSafe(Member m): يمنع الحذف لو فيه إعارات نشطة
 */
public class MemberRepository {
    private final EntityManagerFactory emf;
    public MemberRepository(EntityManagerFactory emf) { this.emf = emf; }

    /** إرجاع جميع الأعضاء مرتّبين بالاسم */
    public List<Member> findAll() {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<Member> q = em.createQuery(
                "SELECT m FROM Member m ORDER BY m.name", Member.class);
            return q.getResultList();
        } finally { em.close(); }
    }

    /** بحث بسيط بالاسم أو وسيلة التواصل (غير حساس لحالة الأحرف) */
    public List<Member> findByNameOrContact(String qText) {
        String q = (qText == null ? "" : qText.trim().toLowerCase());
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<Member> qy = em.createQuery(
                "SELECT m FROM Member m " +
                "WHERE LOWER(m.name)    LIKE :q " +
                "   OR LOWER(m.contact) LIKE :q " +
                "ORDER BY m.id DESC", Member.class);
            qy.setParameter("q", "%" + q + "%");
            return qy.getResultList();
        } finally { em.close(); }
    }

    /** فحص تكرار الاسم (مع استثناء id معيّن عند التعديل) */
    public boolean existsNameIgnoreCase(String name, Integer exceptId) {
        String n = (name == null ? "" : name.trim().toLowerCase());
        EntityManager em = emf.createEntityManager();
        try {
            String jpql = (exceptId == null)
                ? "SELECT COUNT(m) FROM Member m WHERE LOWER(m.name) = :n"
                : "SELECT COUNT(m) FROM Member m WHERE LOWER(m.name) = :n AND m.id <> :id";
            TypedQuery<Long> q = em.createQuery(jpql, Long.class)
                                   .setParameter("n", n);
            if (exceptId != null) q.setParameter("id", exceptId);
            Long c = q.getSingleResult();
            return c != null && c > 0;
        } finally { em.close(); }
    }

    /** هل لدى العضو إعارات نشطة؟ (لازم قبل الحذف) */
    public boolean hasActiveBorrowings(Integer memberId) {
        EntityManager em = emf.createEntityManager();
        try {
            Long c = em.createQuery(
                "SELECT COUNT(bw) FROM Borrowing bw " +
                "WHERE bw.member.id = :mid AND bw.returnDate IS NULL",
                Long.class
            ).setParameter("mid", memberId).getSingleResult();
            return c != null && c > 0;
        } finally { em.close(); }
    }

    /** إضافة/تعديل عضو (merge) */
    public Member save(Member m) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Member merged = em.merge(m);
            em.getTransaction().commit();
            return merged;
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }

    /** حذف عضو (مثل السابق) */
    public void delete(Member m) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Member ref = em.find(Member.class, m.getId());
            if (ref != null) em.remove(ref);
            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        } finally { em.close(); }
    }

    /** حذف آمن: يمنع الحذف إذا عنده إعارات نشطة (اختياري للاستخدام) */
    public void deleteSafe(Member m) {
        if (m == null || m.getId() == null) return;
        if (hasActiveBorrowings(m.getId())) {
            throw new IllegalStateException("Cannot delete member with active borrowings.");
        }
        delete(m);
    }
}
