package JPA_models;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class JPAUtil {

    private static final EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("SmartLibraryPU");

    private JPAUtil() {}

    public static EntityManager getEM() {
        return emf.createEntityManager();
    }
}
