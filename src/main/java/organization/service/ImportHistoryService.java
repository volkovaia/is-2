package organization.service;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import organization.entity.ImportHistory;
import organization.entity.ImportStatus;

import java.time.LocalDateTime;
import java.util.List;

@Stateless
public class ImportHistoryService {

    @PersistenceContext
    private EntityManager em;

    // Максимальная длина сообщения об ошибке для обрезки
    private static final int MAX_ERROR_LENGTH = 3999;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ImportHistory save(ImportHistory history) {
        if (history.getStartDate() == null) {
            history.setStartDate(LocalDateTime.now());
        }
        em.persist(history);
        return history;
    }

    /**
     * Обновляет статус и результаты завершенной операции импорта.
     * Использует REQUIRES_NEW для гарантии, что запись будет сохранена
     * независимо от отката основной транзакции импорта.
     *
     * @param historyId ID записи истории для обновления.
     * @param status Новый статус (SUCCESS или FAILED).
     * @param objectCount Число обработанных/добавленных объектов (может быть null/0 для FAILED).
     * @param error Сообщение об ошибке (только для FAILED).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateStatus(Long historyId, ImportStatus status, Integer objectCount, String error) {
        ImportHistory history = em.find(ImportHistory.class, historyId);

        if (history != null) {
            history.setStatus(status);
            history.setEndDate(LocalDateTime.now());

            if (status == ImportStatus.SUCCESS) {
                history.setObjectCount(objectCount);
                history.setErrorMessage(null); // Очищаем ошибку для успешной операции
            } else if (status == ImportStatus.FAILED) {
                // Для FAILED сбрасываем счетчик и устанавливаем ошибку
                history.setObjectCount(0);

                // Обрезаем сообщение об ошибке, если оно слишком длинное
                String finalError = error != null ? error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH)) : null;
                history.setErrorMessage(finalError);
            }

            em.merge(history);
        }
    }

    // ... (Метод findHistoryByUser остается без изменений) ...
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