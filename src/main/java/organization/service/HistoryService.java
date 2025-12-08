package organization.service;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;

@Stateless
public class ImportHistoryService {

    @PersistenceContext
    private EntityManager em;

    // Транзакция REQUIRES_NEW гарантирует, что статус будет записан
    // даже если родительская транзакция импорта откатится.
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ImportHistory save(ImportHistory history) {
        if (history.getStartDate() == null) {
            history.setStartDate(LocalDateTime.now());
        }
        em.persist(history);
        return history;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateStatus(Long historyId, ImportStatus status, Integer count, String error) {
        ImportHistory history = em.find(ImportHistory.class, historyId);
        if (history != null) {
            history.setStatus(status);
            history.setObjectCount(count);
            history.setErrorMessage(error != null ? error.substring(0, Math.min(error.length(), 4000)) : null);
            history.setEndDate(LocalDateTime.now());
            em.merge(history);
        }
    }

    // Метод для получения истории (логика фильтрации по ролям/пользователю)
    // Здесь требуется доступ к текущему пользователю и его ролям
    public List<ImportHistory> findHistoryByUser(String username, boolean isAdmin) {
        if (isAdmin) {
            // Администратор видит всю историю
            TypedQuery<ImportHistory> query = em.createQuery(
                    "SELECT h FROM ImportHistory h ORDER BY h.startDate DESC", ImportHistory.class);
            return query.getResultList();
        } else {
            // Обычный пользователь видит только свою историю
            TypedQuery<ImportHistory> query = em.createQuery(
                    "SELECT h FROM ImportHistory h WHERE h.userName = :username ORDER BY h.startDate DESC", ImportHistory.class);
            query.setParameter("username", username);
            return query.getResultList();
        }
    }
}
