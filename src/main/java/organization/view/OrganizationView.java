package organization.view;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.Data;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import organization.dto.*;
import organization.entity.*;
import organization.service.ImportHistoryService;
import organization.service.OrganizationService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Named
@ViewScoped
public class OrganizationView implements Serializable {

    // === Зависимости ===
    @Inject
    private OrganizationService organizationService;

    @Inject
    private ImportHistoryService historyService;

    // Валидатор для DTO
    private final transient ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final transient Validator validator = factory.getValidator();

    // === Поля для UI/Данных ===
    private OrganizationRequestDTO newOrganizationDTO;
    private List<OrganizationResponseDTO> organizationsDTO;
    private List<OrganizationResponseDTO> allOrganizations;
    private String filterFullName;
    private OrganizationResponseDTO selectedOrganization;
    private OrganizationResponseDTO editedOrganization;
    private OrganizationResponseDTO maxFullNameOrg;
    private List<ImportHistory> importHistoryList; // Для истории импорта

    // === Поля для специальных операций ===
    private Long countByPostalAddressResult;
    private Long countByTypeResult;
    private OrganizationResponseDTO mergeResult;
    private OrganizationResponseDTO absorbResult;

    private String postalStreet;
    private String postalZipCode;
    private OrganizationType selectedType;

    private Long mergeOrgId1;
    private Long mergeOrgId2;
    private String mergeNewName;
    private String mergeNewStreet;
    private String mergeNewZipCode;

    private Long absorbOrgId1;
    private Long absorbOrgId2;

    // Поле для загрузки файла (оставлено, хотя не используется напрямую, т.к. PrimeFaces использует event)
    private UploadedFile uploadedFile;

    // === Методы DTO/Валидации ===

    public <T> Set<ConstraintViolation<T>> validateDto(T dto) {
        return validator.validate(dto);
    }

    // === Методы жизненного цикла ===

    @PostConstruct
    public void init() {
        loadAll();
        loadImportHistory(); // Загружаем историю при инициализации
    }

    public void loadAll() {
        organizationsDTO = organizationService.getAllOrganizations();
        if (allOrganizations == null) {
            allOrganizations = new ArrayList<>(organizationsDTO);
        } else {
            // Обновление allOrganizations для фильтрации
            allOrganizations = new ArrayList<>(organizationsDTO);
        }
    }

    // === Методы для импорта CSV ===

    /**
     * Обработчик события загрузки файла. Парсит CSV и вызывает сервис.
     */
    @SuppressWarnings("MagicConstant")
    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile uploadedFile = event.getFile();
        if (uploadedFile == null) {
            addErrorMessage("Ошибка загрузки файла.", null);
            return;
        }

        List<OrganizationRequestDTO> parsedOrganizations = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        // Номер строки, начиная с 1 для заголовка
        int headerLineNumber = 1;

        try (
                InputStream is = uploadedFile.getInputStream();
                CSVParser parser = CSVFormat.DEFAULT
                        .withHeader("name", "fullName", "type", "annualTurnover", "employeesCount", "rating",
                                "coordinates.x", "coordinates.y", "officialAddress.street", "officialAddress.zipCode",
                                "postalAddress.street", "postalAddress.zipCode") // Заголовки
                        .withSkipHeaderRecord() // Пропускаем строку с заголовками
                        .withIgnoreEmptyLines()
                        .withTrim()
                        .parse(new InputStreamReader(is, StandardCharsets.UTF_8))
        ) {
            for (CSVRecord record : parser) {
                // Номер строки данных (2, 3, 4...)
                int dataLineNumber = (int) record.getRecordNumber() + headerLineNumber;
                try {
                    OrganizationRequestDTO dto = parseCsvRecord(record);

                    // 1. Дополнительная валидация DTO перед добавлением в список
                    Set<ConstraintViolation<OrganizationRequestDTO>> violations = validateDto(dto);
                    if (!violations.isEmpty()) {
                        String violationMsg = violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining("; "));
                        throw new RuntimeException("Валидация DTO не пройдена: " + violationMsg);
                    }
                    parsedOrganizations.add(dto);
                } catch (RuntimeException e) {
                    errors.add("Строка " + dataLineNumber + ": " + e.getMessage());
                }
            }

        } catch (IOException e) {
            addErrorMessage("Ошибка чтения файла: " + e.getMessage(), e);
            return;
        } catch (Exception e) {
            addErrorMessage("Непредвиденная ошибка парсинга: " + e.getMessage(), e);
            return;
        }

        if (!errors.isEmpty()) {
            errors.forEach(err -> addErrorMessage(err, null));
            addErrorMessage("Импорт отменён из-за ошибок парсинга данных в файле.", null);
            return;
        }

        if (parsedOrganizations.isEmpty()) {
            addWarnMessage("Нет данных для импорта.", null);
            return;
        }

        // 4. Вызов сервиса для транзакционного импорта
        try {
            int importedCount = organizationService.importFromCsv(parsedOrganizations);
            addInfoMessage("Успешно импортировано " + importedCount + " организаций.", null);
            loadAll();
            loadImportHistory();
        } catch (RuntimeException e) {
            // Перехватываем RuntimeException, выброшенный сервисом (с откатом транзакции)
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            addErrorMessage("Импорт отменён (ошибка транзакции): " + msg, null);
            loadImportHistory(); // Обновляем историю, чтобы показать FAILED статус
        }
    }

    /**
     * Парсит CSVRecord (из Apache Commons CSV) в OrganizationRequestDTO.
     * @throws RuntimeException при ошибке парсинга
     */
    private OrganizationRequestDTO parseCsvRecord(CSVRecord record) {
        try {
            // Вспомогательная функция для получения Trimmed/Cleaned String
            java.util.function.Function<String, String> getCleaned = (fieldName) ->
                    record.get(fieldName).trim().replace("\"", "");

            // 1. Обработка числовых полей с учетом пустых строк (0.0 для double/float, 0 для int)
            String annualTurnoverStr = getCleaned.apply("annualTurnover");
            double annualTurnover = annualTurnoverStr.isEmpty() ? 0.0 : Double.parseDouble(annualTurnoverStr);

            String employeesCountStr = getCleaned.apply("employeesCount");
            int employeesCount = employeesCountStr.isEmpty() ? 0 : Integer.parseInt(employeesCountStr);

            String ratingStr = getCleaned.apply("rating");
            float rating = ratingStr.isEmpty() ? 0.0f : Float.parseFloat(ratingStr);

            String coordXStr = getCleaned.apply("coordinates.x");
            double coordX = coordXStr.isEmpty() ? 0.0 : Double.parseDouble(coordXStr);

            String coordYStr = getCleaned.apply("coordinates.y");
            long coordY = coordYStr.isEmpty() ? 0L : Long.parseLong(coordYStr);

            // 2. Координаты
            CoordinatesRequestDTO coordinates = CoordinatesRequestDTO.builder()
                    .x(coordX)
                    .y((int) coordY)
                    .build();

            // 3. Адреса
            String officialZipCode = getCleaned.apply("officialAddress.zipCode");
            AddressRequestDTO officialAddress = AddressRequestDTO.builder()
                    .street(getCleaned.apply("officialAddress.street"))
                    .zipCode(officialZipCode.isEmpty() ? null : officialZipCode)
                    .build();

            String postalZipCode = getCleaned.apply("postalAddress.zipCode");
            AddressRequestDTO postalAddress = AddressRequestDTO.builder()
                    .street(getCleaned.apply("postalAddress.street"))
                    .zipCode(postalZipCode.isEmpty() ? null : postalZipCode)
                    .build();

            // 4. Сборка DTO
            return OrganizationRequestDTO.builder()
                    .name(getCleaned.apply("name"))
                    .fullName(getCleaned.apply("fullName"))
                    .type(OrganizationType.valueOf(getCleaned.apply("type").toUpperCase()))
                    .annualTurnover(annualTurnover)
                    .employeesCount(employeesCount)
                    .rating(rating)
                    .coordinates(coordinates)
                    .officialAddress(officialAddress)
                    .postalAddress(postalAddress)
                    .build();

        } catch (IllegalArgumentException e) {
            // Бросаем RuntimeException, чтобы не объявлять throws Exception
            throw new RuntimeException("Ошибка преобразования типов данных (числа/ENUM): " + e.getMessage(), e);
        } catch (Exception e) {
            // Бросаем RuntimeException
            throw new RuntimeException("Ошибка парсинга поля: " + e.getMessage(), e);
        }
    }


    // === Методы истории импорта ===

    /**
     * Загружает список истории импорта, фильтруя по пользователю и роли.
     */
    public void loadImportHistory() {
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();

        // Получение имени пользователя и роли
        String username = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "ANONYMOUS";
        boolean isAdmin = request.isUserInRole("ADMIN"); // Замените "ADMIN" на вашу роль администратора

        try {
            this.importHistoryList = historyService.findHistoryByUser(username, isAdmin);
        } catch (Exception e) {
            addErrorMessage("Ошибка загрузки истории импорта: " + e.getMessage(), e);
            this.importHistoryList = new ArrayList<>();
        }
    }

    // --- Методы, оставленные без изменений ---

    public void applyFullNameFilter() {
        if (filterFullName == null || filterFullName.trim().isEmpty()) {
            organizationsDTO = new ArrayList<>(allOrganizations);
        } else {
            organizationsDTO = allOrganizations.stream()
                    .filter(org -> filterFullName.equals(org.getFullName()))
                    .collect(Collectors.toList());
        }
    }

    public void clearFilter() {
        filterFullName = null;
        organizationsDTO = new ArrayList<>(allOrganizations);
    }

    public void view(Long id) {
        selectedOrganization = organizationService.getOrganizationById(id);
        System.out.println("Выбрана организация: " + selectedOrganization);
    }

    public void resetSelectedOrganization() {
        this.selectedOrganization = null;
    }

    public void resetAllParams() {
        this.selectedOrganization = null;
        this.editedOrganization = null;
        this.newOrganizationDTO = null;
        this.maxFullNameOrg = null;
    }

    public void edit(Long id) {
        this.editedOrganization = organizationService.getOrganizationById(id);
    }

    public void updateOrganization() {
        resetSelectedOrganization();
        System.out.println("организация с правками:" + editedOrganization);
        if (editedOrganization != null) {
            organizationService.updateOrganization(editedOrganization.getId(), editedOrganization);
            this.organizationsDTO = organizationService.getAllOrganizations();
            this.editedOrganization = null;
        }
    }

    public void delete(Long id) {
        resetSelectedOrganization();
        organizationService.deleteOrganization(id);
        loadAll(); // обновить список
    }

    public void prepareNewOrganization() {
        resetSelectedOrganization();
        this.newOrganizationDTO = new OrganizationRequestDTO();
        // ИСПРАВЛЕНО: Используем RequestDTO классы
        this.newOrganizationDTO.setCoordinates(new CoordinatesRequestDTO());
        this.newOrganizationDTO.setOfficialAddress(new AddressRequestDTO());
        this.newOrganizationDTO.setPostalAddress(new AddressRequestDTO());
    }

    public void addOrganization() {
        resetSelectedOrganization();
        if (newOrganizationDTO != null) {
            organizationService.createOrganization(newOrganizationDTO);
            this.organizationsDTO = organizationService.getAllOrganizations();
            this.newOrganizationDTO = null;
        }
    }


    public void initNewOrganization() {
        resetSelectedOrganization();
        this.newOrganizationDTO = new OrganizationRequestDTO();
        this.newOrganizationDTO.setCoordinates(new CoordinatesRequestDTO());
        this.newOrganizationDTO.setOfficialAddress(new AddressRequestDTO());
        this.newOrganizationDTO.setPostalAddress(new AddressRequestDTO());
    }

    public void findMaxFullName() {
        maxFullNameOrg = organizationService.getOrganizationWithMaxFullName();
    }

    public void executeCountByPostalAddress() {
        AddressRequestDTO addr = new AddressRequestDTO();
        addr.setStreet(postalStreet);
        addr.setZipCode(postalZipCode);
        countByPostalAddressResult = organizationService.countByPostalAddress(addr);
    }

    public void executeCountByTypeLessThan() {
        OrganizationTypeDTO dto = new OrganizationTypeDTO();
        dto.setType(selectedType);
        countByTypeResult = organizationService.countByTypeLessThan(dto);
    }

    public void executeMerge() {
        OrganizationMergeRequestDTO req = new OrganizationMergeRequestDTO();
        req.setOrgId1(mergeOrgId1);
        req.setOrgId2(mergeOrgId2);
        req.setNewName(mergeNewName);

        AddressRequestDTO newAddress = new AddressRequestDTO();
        newAddress.setStreet(mergeNewStreet);
        newAddress.setZipCode(mergeNewZipCode);
        req.setNewAddress(newAddress);

        mergeResult = organizationService.mergeOrganizations(req);
        loadAll(); // обновить список
    }

    public void executeAbsorb() {
        OrganizationAbsorbRequestDTO req = new OrganizationAbsorbRequestDTO();
        req.setOrgId1(absorbOrgId1);
        req.setOrgId2(absorbOrgId2);
        absorbResult = organizationService.absorbOrganization(req);
        loadAll(); // обновить список
    }

    // --- Геттеры и Сеттеры, где не Data Lombok ---
    public List<ImportHistory> getImportHistoryList() {
        if (this.importHistoryList == null) {
            loadImportHistory();
        }
        return importHistoryList;
    }

    // === Вспомогательные методы сообщений ===

    private void addInfoMessage(String message, Throwable cause) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Успех", message));
        if (cause != null) cause.printStackTrace();
    }
    private void addErrorMessage(String message, Throwable cause) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Ошибка", message));
        if (cause != null) cause.printStackTrace();
    }
    private void addWarnMessage(String message, Throwable cause) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, "Предупреждение", message));
        if (cause != null) cause.printStackTrace();
    }
}