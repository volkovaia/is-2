package organization.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional; // ВАЖНО: Нужен для транзакционности
import jakarta.validation.ValidationException;
import lombok.NoArgsConstructor;
import organization.dto.*;
import organization.entity.*;
import organization.mapper.OrganizationMapper;
import organization.repository.OrganizationRepository;

import jakarta.resource.spi.work.SecurityContext; // ОШИБКА: Это неправильный импорт! Должен быть другой.
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.InputStream; // Не используется, но оставлен
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@NoArgsConstructor
public class OrganizationService {

    @Inject
    private OrganizationRepository organizationRepository;

    @Inject
    private ImportHistoryService historyService;

    // ИСПРАВЛЕНИЕ: Этот импорт (jakarta.resource.spi.work.SecurityContext) неверен
    // Вы будете использовать другой способ получить пользователя, например, через CDI-Interceptor или FacesContext.
    // Пока оставим заглушку, но имейте в виду, что ее нужно будет доработать.
    private String getCurrentUsername() {
        try {
            InitialContext initialContext = new InitialContext();
            // ПРАВИЛЬНЫЙ JNDI LOOKUP ДЛЯ SecurityContext:
            // jakarta.ws.rs.core.SecurityContext или jakarta.faces.context.FacesContext
            // Object sc = initialContext.lookup("java:comp/EJBContext/SecurityContext");
            // Principal principal = ((jakarta.ejb.EJBContext)sc).getCallerPrincipal();

            // Заглушка, так как контекст безопасности сложен для получения из сервиса без EJB/REST
            return "ADMIN"; // Временно
        } catch (NamingException e) {
            return "UNKNOWN_USER";
        }
    }


    /**
     * Основной метод для импорта. Обеспечивает транзакцию "все или ничего" и логирование истории.
     */
//    @Transactional // <--- ЭТО ВАЖНО: Обеспечивает откат всей операции при ошибке
//    public int importFromCsv(List<OrganizationRequestDTO> organizationRequestDTOS) {
//
//        String username = getCurrentUsername();
//        System.out.println("userName: " + username);
//        ImportHistory history = new ImportHistory();
//        history.setUserName(username);
//        history.setStatus(ImportStatus.IN_PROGRESS);
//
//        // 1. Сохраняем начальный статус в отдельной транзакции (ImportHistoryService должен быть EJB)
//        history = historyService.save(history);
//        System.out.println("history got");
//        int successfulCount = 0;
//        try {
//            for (OrganizationRequestDTO dto : organizationRequestDTOS) {
//                Organization org = OrganizationMapper.toOrganization(dto);
//
//                // 2. ВАЛИДАЦИЯ ИЗ ЛР1
//                validateOrganization(org);
//
//                // 3. ПРОГРАММНАЯ ПРОВЕРКА УНИКАЛЬНОСТИ
//                checkBusinessUniqueConstraint(org);
//
//                org.setId(null);
//                org.setCreationDate(null);
//                organizationRepository.create(org);
//                successfulCount++;
//                System.out.println("successful count: " + successfulCount);
//            }
//            System.out.println("finished with organizations");
//            // Если дошли до конца цикла, фиксируем транзакцию и обновляем историю
//            historyService.updateStatus(history.getId(), ImportStatus.SUCCESS, successfulCount, null);
//            System.out.println("history status: success");
//            return successfulCount;
//
//        } catch (ValidationException | IllegalArgumentException e) {
//            // 4. ОШИБКА: Откат транзакции (автоматически из-за @Transactional)
//            String errorMsg = e.getMessage();
//            historyService.updateStatus(history.getId(), ImportStatus.FAILED, 0, errorMsg);
//
//            // Перебрасываем RuntimeException, чтобы контейнер выполнил Rollback и уведомил вызывающий код
//            throw new RuntimeException("Импорт не удался. Ничего не сохранено. Причина: " + errorMsg, e);
//        }
//    }

    @Transactional // Убедитесь, что этот метод транзакционный (jakarta.transaction.Transactional)
    public ImportHistory importFromCsv(List<OrganizationRequestDTO> organizations, String userName) { // Изменена сигнатура
        // 1. Создание записи IN_PROGRESS
        ImportHistory history = ImportHistory.builder()
                .status(ImportStatus.IN_PROGRESS)
                .userName(userName)
                .build();
        history = historyService.save(history); // Сохраняем начальный статус

        int successCount = 0;
        try {
            System.out.println("LOG: OrganizationService - Начинается сохранение " + organizations.size() + " организаций.");

            for (OrganizationRequestDTO dto : organizations) {

                Organization entity = OrganizationMapper.toOrganization(dto);
                organizationRepository.save(entity);

                successCount++;
            }
            // 2. Успешное завершение
            history.setStatus(ImportStatus.SUCCESS);
            history.setObjectCount(successCount);
            System.out.println("LOG: OrganizationService - Импорт успешно завершен. Объектов: " + successCount);

        } catch (Exception e) {
            // 3. Запись ошибки
            System.err.println("FATAL ERROR: OrganizationService - Ошибка импорта: " + e.getMessage());
            e.printStackTrace();

            history.setStatus(ImportStatus.FAILED);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
            history.setErrorMessage(errorMsg.length() > 4000 ? errorMsg.substring(0, 4000) : errorMsg);

            // Переброс исключения, чтобы откатить транзакцию (если @Transactional настроен правильно)
            throw new RuntimeException("Ошибка сохранения организаций в БД", e);

        } finally {
            // 4. Обновление истории
            history.setEndDate(LocalDateTime.now());
            // ВАЖНО: Если у вас есть @Transactional, сохранение должно работать. Если нет,
            // возможно, понадобится отдельный non-transactional метод для saveHistory.
            historyService.save(history);
        }
        return history;
    }

    /**
     * Проверяет уникальность поля fullName на программном уровне (для ЛР2).
     * @throws ValidationException при нарушении уникальности
     */
    private void checkBusinessUniqueConstraint(Organization organization) {
        // Запрос к репозиторию на наличие организации с таким же fullName
        List<Organization> existing = organizationRepository.findByFullName(organization.getFullName());

        if (existing != null && !existing.isEmpty()) {
            throw new ValidationException("Бизнес-ограничение нарушено: Организация с полным именем '" + organization.getFullName() + "' уже существует.");
        }
    }

    // --- ОСТАВЛЕННЫЕ МЕТОДЫ БИЗНЕС-ЛОГИКИ ---

    @Transactional
    public OrganizationResponseDTO getOrganizationWithMaxFullName() {
        return OrganizationMapper.toOrganizationResponseDTO(organizationRepository.getOrganizationWithMaxFullName());
    }

    @Transactional
    public long countByPostalAddress(AddressRequestDTO postalAddress) {
        return organizationRepository.countByPostalAddress(OrganizationMapper.toAddress(postalAddress));
    }

    @Transactional
    public long countByTypeLessThan(OrganizationTypeDTO type) {
        return organizationRepository.countByTypeLessThan(OrganizationMapper.toOrganizationType(type));
    }

//    @Transactional
//    public OrganizationResponseDTO mergeOrganizations(OrganizationMergeRequestDTO dto) {
//        Organization org1 = organizationRepository.findById(dto.getOrgId1());
//        Organization org2 = organizationRepository.findById(dto.getOrgId2());
//        if (org1 == null || org2 == null) {
//            throw new IllegalArgumentException("Одна из организаций не найдена");
//        }
//        org1.setName(dto.getNewName());
//        org1.setFullName(dto.getNewName());
//        org1.setPostalAddress(dto.getNewAddress());
//        org1.setAnnualTurnover(
//                (org1.getAnnualTurnover() != null ? org1.getAnnualTurnover() : 0)
//                        + (org2.getAnnualTurnover() != null ? org2.getAnnualTurnover() : 0)
//        );
//        org1.setEmployeesCount(org1.getEmployeesCount() + org2.getEmployeesCount());
//        organizationRepository.delete(org2);
//        return OrganizationMapper.toOrganizationResponseDTO(org1);
//    }

    @Transactional
    public OrganizationResponseDTO mergeOrganizations(OrganizationMergeRequestDTO dto) {
        Organization org1 = organizationRepository.findById(dto.getOrgId1());
        Organization org2 = organizationRepository.findById(dto.getOrgId2());
        if (org1 == null || org2 == null) {
            throw new IllegalArgumentException("Одна из организаций не найдена");
        }

        // 1. ПРЕОБРАЗОВАНИЕ DTO -> ENTITY
        Address newPostalAddress = OrganizationMapper.toAddress(dto.getNewAddress());


        org1.setName(dto.getNewName());
        org1.setFullName(dto.getNewName());

        // ИСПРАВЛЕНИЕ ТИПОВ: используем маппер
        org1.setPostalAddress(newPostalAddress);

        org1.setAnnualTurnover(
                (org1.getAnnualTurnover() != null ? org1.getAnnualTurnover() : 0)
                        + (org2.getAnnualTurnover() != null ? org2.getAnnualTurnover() : 0)
        );
        org1.setEmployeesCount(org1.getEmployeesCount() + org2.getEmployeesCount());
        organizationRepository.delete(org2);
        return OrganizationMapper.toOrganizationResponseDTO(org1);
    }


    @Transactional
    public OrganizationResponseDTO absorbOrganization(OrganizationAbsorbRequestDTO dto) {
        Organization absorber = organizationRepository.findById(dto.getOrgId1());
        Organization absorbed = organizationRepository.findById(dto.getOrgId2());
        if (absorber == null || absorbed == null) {
            throw new IllegalArgumentException("Одна из организаций не найдена");
        }

        absorber.setEmployeesCount(absorber.getEmployeesCount() + absorbed.getEmployeesCount());
        absorber.setAnnualTurnover((absorber.getAnnualTurnover() != null ? absorber.getAnnualTurnover() : 0)
                + (absorbed.getAnnualTurnover() != null ? absorbed.getAnnualTurnover() : 0));

        organizationRepository.update(absorber);
        organizationRepository.delete(absorbed);

        Organization updatedAbsorber = organizationRepository.findById(dto.getOrgId1());

        return OrganizationMapper.toOrganizationResponseDTO(updatedAbsorber);
    }

    @Transactional
    public OrganizationResponseDTO getOrganizationById(Long id) {
        return OrganizationMapper.toOrganizationResponseDTO(organizationRepository.findById(id));
    }
    @Transactional
    public List<OrganizationResponseDTO> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(OrganizationMapper::toOrganizationResponseDTO)
                .collect(Collectors.toList());
    }
    @Transactional
    public void deleteOrganization(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID не может быть null");
        }
        organizationRepository.deleteById(id);
    }
    @Transactional
    public OrganizationResponseDTO createOrganization(OrganizationRequestDTO organization) {
        Organization org = OrganizationMapper.toOrganization(organization);
        validateOrganization(org);
        organizationRepository.create(org);
        return OrganizationMapper.toOrganizationResponseDTO(org);
    }

    @Transactional
    public void updateOrganization(Long organizationId, OrganizationResponseDTO organizationDTO) {
        Organization organization = OrganizationMapper.toOrganization(organizationDTO);
        validateOrganization(organization);

        organization.setId(organizationId);
        organizationRepository.update(organization);

    }

    // --- МЕТОДЫ ВАЛИДАЦИИ/ПАРСИНГА (Оставлены для полноты) ---

    // Удален дубликат importFromCsv.
    // Удален parseCsvRecord, так как его логика должна быть в OrganizationView,
    // если вы передаете список DTOs (как в новом методе).
    // Если DTOs создаются в другом месте, то все в порядке.
    // Оставлены только методы валидации.

    private void validateOrganization(Organization organization) {
        if (organization.getName() == null || organization.getName().trim().isEmpty()) {
            throw new ValidationException("Organization name не может быть пустым");
        }
        if (organization.getOfficialAddress() == null) {
            throw new ValidationException("Official address не может быть null");
        }
        if (organization.getAnnualTurnover() == null || organization.getAnnualTurnover() <= 0) { // Проверка на null добавлена
            throw new ValidationException("Annual turnover должен быть положительным");
        }
//        if (organization.getEmployeesCount() == null || organization.getEmployeesCount() <= 0) { // Проверка на null добавлена
//            throw new ValidationException("Employees count должно быть положительным");
//        }
        if (organization.getEmployeesCount() <= 0) { // Проверка на null удалена
            throw new ValidationException("Employees count должно быть положительным");
        }
        if (organization.getRating() == null || organization.getRating() <= 0) { // Проверка на null добавлена
            throw new ValidationException("Rating должен быть положительным");
        }
        if (organization.getType() == null) {
            throw new ValidationException("Organization type не может быть null");
        }
        if (organization.getPostalAddress() == null) {
            throw new ValidationException("Postal address не может быть null");
        }

        validateAddress(organization.getOfficialAddress(), "Official address");
        validateAddress(organization.getPostalAddress(), "Postal address");
        validateCoordinates(organization.getCoordinates());
    }

    private void validateCoordinates(Coordinates coordinates) {
        if (coordinates == null) {
            throw new ValidationException("Coordinates не может быть null");
        }
        if (coordinates.getY() <= -461) {
            throw new ValidationException("Y должен быть больше -461");
        }
    }
    private void validateAddress(Address address, String addressType) {
        if (address == null) {
            throw new ValidationException(addressType + " не может быть null");
        }
        if (address.getStreet() == null || address.getStreet().trim().isEmpty()) {
            throw new ValidationException(addressType + " street не может быть пустым");
        }
    }
}