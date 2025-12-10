package organization.view;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
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
//@ViewScoped
@SessionScoped
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

    private UploadedFile uploadedFile;

    // === Методы DTO/Валидации ===

    public <T> Set<ConstraintViolation<T>> validateDto(T dto) {
        return validator.validate(dto);
    }

    // === Методы жизненного цикла ===

    @PostConstruct
    public void init() {
        loadAll();
        loadImportHistory();
    }

    public void loadAll() {
        organizationsDTO = organizationService.getAllOrganizations();
        if (allOrganizations == null) {
            allOrganizations = new ArrayList<>(organizationsDTO);
        } else {
            allOrganizations = new ArrayList<>(organizationsDTO);
        }
    }

    // === Методы для импорта CSV ===

    @SuppressWarnings("MagicConstant")
//    public void handleFileUpload(FileUploadEvent event) {
//        System.out.println("hello from handleFileUpload OrganizationView");
//        UploadedFile uploadedFile = event.getFile();
//        if (uploadedFile == null) {
//            addErrorMessage("Ошибка загрузки файла.", null);
//            return;
//        }
//        System.out.println("got file from handleFileUpload OrganizationView");
//        List<OrganizationRequestDTO> parsedOrganizations = new ArrayList<>();
//        List<String> errors = new ArrayList<>();
//        int headerLineNumber = 1;
//
//        try (
//                InputStream is = uploadedFile.getInputStream();
//                CSVParser parser = CSVFormat.DEFAULT
//                        .withHeader("name", "fullName", "type", "annualTurnover", "employeesCount", "rating",
//                                "coordinates.x", "coordinates.y", "officialAddress.street", "officialAddress.zipCode",
//                                "postalAddress.street", "postalAddress.zipCode")
//                        .withSkipHeaderRecord()
//                        .withIgnoreEmptyLines()
//                        .withTrim()
//                        .parse(new InputStreamReader(is, StandardCharsets.UTF_8))
//        ) {
//            for (CSVRecord record : parser) {
//                int dataLineNumber = (int) record.getRecordNumber() + headerLineNumber;
//                try {
//                    OrganizationRequestDTO dto = parseCsvRecord(record);
//
//                    Set<ConstraintViolation<OrganizationRequestDTO>> violations = validateDto(dto);
//                    if (!violations.isEmpty()) {
//                        String violationMsg = violations.stream()
//                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
//                                .collect(Collectors.joining("; "));
//                        throw new RuntimeException("Валидация DTO не пройдена: " + violationMsg);
//                    }
//                    parsedOrganizations.add(dto);
//                } catch (RuntimeException e) {
//                    errors.add("Строка " + dataLineNumber + ": " + e.getMessage());
//                }
//            }
//
//        } catch (IOException e) {
//            addErrorMessage("Ошибка чтения файла: " + e.getMessage(), e);
//            return;
//        } catch (Exception e) {
//            addErrorMessage("Непредвиденная ошибка парсинга: " + e.getMessage(), e);
//            return;
//        }
//
//        if (!errors.isEmpty()) {
//            errors.forEach(err -> addErrorMessage(err, null));
//            addErrorMessage("Импорт отменён из-за ошибок парсинга данных в файле.", null);
//            return;
//        }
//
//        if (parsedOrganizations.isEmpty()) {
//            addWarnMessage("Нет данных для импорта.", null);
//            return;
//        }
//
//        // 4. Вызов сервиса для транзакционного импорта
//        try {
//            int importedCount = organizationService.importFromCsv(parsedOrganizations);
//            addInfoMessage("Успешно импортировано " + importedCount + " организаций.", null);
//            loadAll();
//            loadImportHistory();
//        } catch (RuntimeException e) {
//            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
//            addErrorMessage("Импорт отменён (ошибка транзакции): " + msg, null);
//            loadImportHistory();
//        }
//    }

    public void handleFileUpload(FileUploadEvent event) {
        System.out.println("!!!!!!!!!! FILE UPLOAD START - RECEIVED EVENT !!!!!!!!!!!!");
        System.out.println("DEBUG: handleFileUpload - Start processing upload event."); // ENHANCED
        UploadedFile uploadedFile = event.getFile();

        if (uploadedFile == null || uploadedFile.getContent() == null || uploadedFile.getContent().length == 0) {
            addErrorMessage("Ошибка загрузки файла.", null);
            System.err.println("ERROR: handleFileUpload - uploadedFile is null or empty. File name: " + (uploadedFile != null ? uploadedFile.getFileName() : "N/A")); // ADDED
            return;
        }

        System.out.println("DEBUG: handleFileUpload - File received successfully. Name: " + uploadedFile.getFileName() + ", Size: " + uploadedFile.getSize()); // ENHANCED

        List<OrganizationRequestDTO> parsedOrganizations = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int headerLineNumber = 1;

        try (
                InputStream is = uploadedFile.getInputStream();
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8); // Explicit InputStreamReader
                CSVParser parser = CSVFormat.DEFAULT
                        .withHeader("name", "fullName", "type", "annualTurnover", "employeesCount", "rating",
                                "coordinates.x", "coordinates.y", "officialAddress.street", "officialAddress.zipCode",
                                "postalAddress.street", "postalAddress.zipCode")
                        .withSkipHeaderRecord()
                        .withIgnoreEmptyLines()
                        .withTrim()
                        .parse(isr) // Use isr
        ) {
            System.out.println("DEBUG: handleFileUpload - Starting CSV parsing loop. Header assumed to be skipped."); // ADDED

            for (CSVRecord record : parser) {
                int dataLineNumber = (int) record.getRecordNumber() + headerLineNumber;
                System.out.println("DEBUG: handleFileUpload - Processing record at line: " + dataLineNumber + ". Raw record: " + record); // ADDED
                try {
                    OrganizationRequestDTO dto = parseCsvRecord(record);

                    Set<ConstraintViolation<OrganizationRequestDTO>> violations = validateDto(dto);
                    if (!violations.isEmpty()) {
                        String violationMsg = violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining("; "));
                        System.err.println("ERROR: handleFileUpload - Validation failed on line " + dataLineNumber + ". Violations: " + violationMsg); // ADDED
                        throw new RuntimeException("Валидация DTO не пройдена: " + violationMsg);
                    }
                    parsedOrganizations.add(dto);
                    System.out.println("DEBUG: handleFileUpload - DTO successfully created from line " + dataLineNumber + "."); // ADDED
                } catch (RuntimeException e) {
                    errors.add("Строка " + dataLineNumber + ": " + e.getMessage());
                    System.err.println("ERROR: handleFileUpload - Parsing or validation error on line " + dataLineNumber + ": " + e.getMessage()); // ADDED
                    e.printStackTrace();
                }
            }
            System.out.println("DEBUG: handleFileUpload - Finished CSV parsing loop. Parsed count: " + parsedOrganizations.size() + ", errors: " + errors.size()); // ADDED

        } catch (IOException e) {
            addErrorMessage("Ошибка чтения файла: " + e.getMessage(), e);
            System.err.println("FATAL ERROR: handleFileUpload - IOException during file read: " + e.getMessage()); // ADDED
            return;
        } catch (Exception e) {
            addErrorMessage("Непредвиденная ошибка парсинга: " + e.getMessage(), e);
            System.err.println("FATAL ERROR: handleFileUpload - Unexpected exception during parsing: " + e.getMessage()); // ADDED
            return;
        }

        if (!errors.isEmpty()) {
            errors.forEach(err -> addErrorMessage(err, null));
            addErrorMessage("Импорт отменён из-за ошибок парсинга данных в файле.", null);
            System.out.println("DEBUG: handleFileUpload - Import cancelled due to " + errors.size() + " parsing errors."); // ADDED
            return;
        }

        if (parsedOrganizations.isEmpty()) {
            addWarnMessage("Нет данных для импорта.", null);
            System.out.println("DEBUG: handleFileUpload - No organizations found for import."); // ADDED
            return;
        }

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();
        // Используем "ADMIN", так как ранее мы установили это для обхода прав
        String userName = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "ADMIN";

        // 4. Вызов сервиса для транзакционного импорта
//        try {
//            System.out.println("DEBUG: handleFileUpload - Calling service to import " + parsedOrganizations.size() + " organizations."); // ADDED
//            int importedCount = organizationService.importFromCsv(parsedOrganizations);
//            addInfoMessage("Успешно импортировано " + importedCount + " организаций.", null);
//            loadAll();
//            loadImportHistory();
//            System.out.println("DEBUG: handleFileUpload - Import successful. Count: " + importedCount); // ADDED
//        } catch (RuntimeException e) {
//            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
//            addErrorMessage("Импорт отменён (ошибка транзакции): " + msg, null);
//            System.err.println("FATAL ERROR: handleFileUpload - Transactional import failed: " + msg); // ADDED
//            e.printStackTrace();
//            loadImportHistory();
//        }
        try {
            System.out.println("DEBUG: handleFileUpload - Calling service to import " + parsedOrganizations.size() + " organizations for user: " + userName);

            // НОВЫЙ ВЫЗОВ: передаем имя пользователя, и сервис сам записывает историю
            ImportHistory resultHistory = organizationService.importFromCsv(parsedOrganizations, userName);

            if (ImportStatus.SUCCESS.equals(resultHistory.getStatus())) {
                addInfoMessage("Успешно импортировано " + resultHistory.getObjectCount() + " организаций.", null);
            } else {
                addErrorMessage("Импорт завершен с ошибкой: " + resultHistory.getErrorMessage(), null);
            }

            loadAll();
            loadImportHistory();
            System.out.println("DEBUG: handleFileUpload - Import processing complete.");

        } catch (RuntimeException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            addErrorMessage("Импорт отменён (Критическая ошибка): " + msg, null);
            System.err.println("FATAL ERROR: handleFileUpload - Transactional import failed: " + msg);
            e.printStackTrace();
            loadImportHistory();
        }
        System.out.println("DEBUG: handleFileUpload - Method finished."); // ADDED
    }

//    private OrganizationRequestDTO parseCsvRecord(CSVRecord record) {
//        try {
//            // Вспомогательная функция для получения Trimmed/Cleaned String
//            java.util.function.Function<String, String> getCleaned = (fieldName) ->
//                    record.get(fieldName).trim().replace("\"", "");
//
//            String annualTurnoverStr = getCleaned.apply("annualTurnover");
//            double annualTurnover = annualTurnoverStr.isEmpty() ? 0.0 : Double.parseDouble(annualTurnoverStr);
//
//            String employeesCountStr = getCleaned.apply("employeesCount");
//            int employeesCount = employeesCountStr.isEmpty() ? 0 : Integer.parseInt(employeesCountStr);
//
//            String ratingStr = getCleaned.apply("rating");
//            float rating = ratingStr.isEmpty() ? 0.0f : Float.parseFloat(ratingStr);
//
//            String coordXStr = getCleaned.apply("coordinates.x");
//            double coordX = coordXStr.isEmpty() ? 0.0 : Double.parseDouble(coordXStr);
//
//            String coordYStr = getCleaned.apply("coordinates.y");
//            long coordY = coordYStr.isEmpty() ? 0L : Long.parseLong(coordYStr);
//
//            // 2. Координаты (ИСПРАВЛЕНО: Используем CoordinatesRequestDTO)
//            CoordinatesRequestDTO coordinates = CoordinatesRequestDTO.builder()
//                    .x(coordX)
//                    .y((int) coordY)
//                    .build();
//
//            // 3. Адреса (ИСПРАВЛЕНО: Используем AddressRequestDTO)
//            String officialZipCode = getCleaned.apply("officialAddress.zipCode");
//            AddressRequestDTO officialAddress = AddressRequestDTO.builder()
//                    .street(getCleaned.apply("officialAddress.street"))
//                    .zipCode(officialZipCode.isEmpty() ? null : officialZipCode)
//                    .build();
//
//            String postalZipCode = getCleaned.apply("postalAddress.zipCode");
//            AddressRequestDTO postalAddress = AddressRequestDTO.builder()
//                    .street(getCleaned.apply("postalAddress.street"))
//                    .zipCode(postalZipCode.isEmpty() ? null : postalZipCode)
//                    .build();
//
//            // 4. Сборка DTO
//            return OrganizationRequestDTO.builder()
//                    .name(getCleaned.apply("name"))
//                    .fullName(getCleaned.apply("fullName"))
//                    .type(OrganizationType.valueOf(getCleaned.apply("type").toUpperCase()))
//                    .annualTurnover(annualTurnover)
//                    .employeesCount(employeesCount)
//                    .rating(rating)
//                    .coordinates(coordinates)
//                    .officialAddress(officialAddress)
//                    .postalAddress(postalAddress)
//                    .build();
//
//        } catch (IllegalArgumentException e) {
//            throw new RuntimeException("Ошибка преобразования типов данных (числа/ENUM): " + e.getMessage(), e);
//        } catch (Exception e) {
//            throw new RuntimeException("Ошибка парсинга поля: " + e.getMessage(), e);
//        }
//    }

    private OrganizationRequestDTO parseCsvRecord(CSVRecord record) {
        try {
            // Вспомогательная функция для получения Trimmed/Cleaned String
            java.util.function.Function<String, String> getCleaned = (fieldName) ->
                    record.get(fieldName).trim().replace("\"", "");

            // 1. Парсинг числовых полей
            String annualTurnoverStr = getCleaned.apply("annualTurnover");
            double annualTurnover = annualTurnoverStr.isEmpty() ? 0.0 : Double.parseDouble(annualTurnoverStr);
            System.out.println("DEBUG: parseCsvRecord - annualTurnover: " + annualTurnover); // ADDED

            String employeesCountStr = getCleaned.apply("employeesCount");
            int employeesCount = employeesCountStr.isEmpty() ? 0 : Integer.parseInt(employeesCountStr);
            System.out.println("DEBUG: parseCsvRecord - employeesCount: " + employeesCount); // ADDED

            String ratingStr = getCleaned.apply("rating");
            float rating = ratingStr.isEmpty() ? 0.0f : Float.parseFloat(ratingStr);
            System.out.println("DEBUG: parseCsvRecord - rating: " + rating); // ADDED

            String coordXStr = getCleaned.apply("coordinates.x");
            double coordX = coordXStr.isEmpty() ? 0.0 : Double.parseDouble(coordXStr);
            System.out.println("DEBUG: parseCsvRecord - coordX: " + coordX); // ADDED

            String coordYStr = getCleaned.apply("coordinates.y");
            int coordY = coordYStr.isEmpty() ? 0 : Integer.parseInt(coordYStr);
            System.out.println("DEBUG: parseCsvRecord - coordY: " + coordY); // ADDED

            // 2. Координаты (ИСПРАВЛЕНО: Используем CoordinatesRequestDTO)
            CoordinatesRequestDTO coordinates = CoordinatesRequestDTO.builder()
                    .x(coordX)
                    .y(coordY)
                    .build();
            System.out.println("DEBUG: parseCsvRecord - Coordinates: " + coordinates); // ADDED

            // 3. Адреса (ИСПРАВЛЕНО: Используем AddressRequestDTO)
            String officialZipCode = getCleaned.apply("officialAddress.zipCode");
            AddressRequestDTO officialAddress = AddressRequestDTO.builder()
                    .street(getCleaned.apply("officialAddress.street"))
                    .zipCode(officialZipCode.isEmpty() ? null : officialZipCode)
                    .build();
            System.out.println("DEBUG: parseCsvRecord - OfficialAddress: " + officialAddress); // ADDED

            String postalZipCode = getCleaned.apply("postalAddress.zipCode");
            AddressRequestDTO postalAddress = AddressRequestDTO.builder()
                    .street(getCleaned.apply("postalAddress.street"))
                    .zipCode(postalZipCode.isEmpty() ? null : postalZipCode)
                    .build();
            System.out.println("DEBUG: parseCsvRecord - PostalAddress: " + postalAddress); // ADDED

            // 4. Сборка DTO
            String name = getCleaned.apply("name");
            String fullName = getCleaned.apply("fullName");
            String typeStr = getCleaned.apply("type").toUpperCase();
            OrganizationType type = OrganizationType.valueOf(typeStr);
            System.out.println("DEBUG: parseCsvRecord - Name: " + name + ", FullName: " + fullName + ", Type: " + type); // ADDED

            return OrganizationRequestDTO.builder()
                    .name(name)
                    .fullName(fullName)
                    .type(type)
                    .annualTurnover(annualTurnover)
                    .employeesCount(employeesCount)
                    .rating(rating)
                    .coordinates(coordinates)
                    .officialAddress(officialAddress)
                    .postalAddress(postalAddress)
                    .build();

        } catch (IllegalArgumentException e) {
            // NumberFormatException или Enum.valueOf() failure
            System.err.println("FATAL ERROR: parseCsvRecord - Conversion error for record: " + record); // ADDED
            throw new RuntimeException("Ошибка преобразования типов данных (числа/ENUM): " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("FATAL ERROR: parseCsvRecord - Generic parsing error for record: " + record); // ADDED
            throw new RuntimeException("Ошибка парсинга поля: " + e.getMessage(), e);
        }
    }


    // === Методы истории импорта ===

    public void loadImportHistory() {
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();

        String username = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "ADMIN";
        boolean isAdmin = request.isUserInRole("ADMIN");

        try {
            this.importHistoryList = historyService.findHistoryByUser(username, isAdmin);
        } catch (Exception e) {
            addErrorMessage("Ошибка загрузки истории импорта: " + e.getMessage(), e);
            this.importHistoryList = new ArrayList<>();
        }
    }

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
}