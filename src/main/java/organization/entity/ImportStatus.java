package organization.entity;

public enum ImportStatus {
    IN_PROGRESS, // Операция запущена и выполняется
    SUCCESS,     // Операция успешно завершена
    FAILED       // Операция завершилась с ошибкой (откат транзакции)
}