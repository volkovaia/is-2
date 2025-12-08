package organization.view;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.primefaces.component.fileupload.FileUpload;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import organization.dto.*;
import organization.entity.Address;
import organization.entity.Coordinates;
import organization.entity.Organization;
import organization.entity.OrganizationType;
import organization.service.OrganizationService;
import lombok.Data;

import jakarta.faces.context.FacesContext;
import org.primefaces.component.fileupload.FileUpload;
import org.primefaces.model.file.UploadedFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Named
@ViewScoped
public class OrganizationView implements Serializable {

//    private final transient ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
//    private final transient Validator validator = factory.getValidator();

    @Inject
    private OrganizationService organizationService;

    private OrganizationRequestDTO newOrganizationDTO;

    private List<OrganizationResponseDTO> organizationsDTO;
    private String filterName;

    private List<OrganizationResponseDTO> allOrganizations; // Сохранённый полный список
    private String filterFullName; // Новое поле для фильтрации по fullName

    private boolean showMaxFullNameModal;
    private OrganizationResponseDTO selectedOrganization;
    private OrganizationResponseDTO editedOrganization;

    // === Поля для специальных операций ===
    private OrganizationResponseDTO maxFullNameOrg;
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

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    public <T> Set<ConstraintViolation<T>> validateDto(T dto) {
        return validator.validate(dto);
    }

    public void handleFileUpload(FileUploadEvent event) {
        this.uploadedFile = event.getFile();
        System.out.println("Файл получен и начинается обработка: " + uploadedFile.getFileName());

        // Переносим всю логику импорта из importCsv() сюда
        List<OrganizationRequestDTO> parsedOrganizations = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (InputStream is = uploadedFile.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            // Пропускаем заголовок
            String headerLine = reader.readLine();
            if (headerLine == null) {
                addErrorMessage("CSV пуст");
                return;
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    OrganizationRequestDTO dto = parseCsvLine(line); // Используем ИСПРАВЛЕННЫЙ метод
                    validateDto(dto);
                    parsedOrganizations.add(dto);
                } catch (Exception e) {
                    errors.add("Строка " + lineNumber + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            addErrorMessage("Ошибка чтения файла: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (!errors.isEmpty()) {
            errors.forEach(this::addErrorMessage);
            addErrorMessage("Импорт отменён из-за ошибок парсинга");
            return;
        }

        if (parsedOrganizations.isEmpty()) {
            addWarnMessage("Нет данных для импорта");
            return;
        }

        try {
            int importedCount = organizationService.importFromCsv(parsedOrganizations);
            addInfoMessage("Успешно импортировано " + importedCount + " организаций");
            loadAll();
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            addErrorMessage("Ошибка импорта (транзакция отменена): " + (msg != null ? msg : "неизвестная ошибка"));
            e.printStackTrace();
        }

        // Очистка
        this.uploadedFile = null;
    }


    public void setUploadedFile(UploadedFile uploadedFile) {
        this.uploadedFile = uploadedFile;
    }


    public void addInfoMessage(String message) {
        System.out.println("[INFO] " + message);
    }

    // Метод для вывода сообщений об ошибках
    public void addErrorMessage(String message) {
        System.err.println("[ERROR] " + message);
    }

    public void addWarnMessage(String message) {
        System.err.println("[WARN] " + message);
    }
//    public void importCsv() {
//        System.out.println("=== importCsv() STARTED ===");
//
//        if (uploadedFile == null) {
//            addErrorMessage("File not selected");
//            return;
//        }
//
//        List<OrganizationRequestDTO> parsedOrganizations = new ArrayList<>();
//        List<String> errors = new ArrayList<>();
//
//        try (InputStream is = uploadedFile.getInputStream();
//             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
//
//            // Пропускаем заголовок
//            String headerLine = reader.readLine();
//            if (headerLine == null) {
//                addErrorMessage("CSV пуст");
//                return;
//            }
//
//            String line;
//            int lineNumber = 1; // первая строка данных — №2 в файле, но будем нумеровать с 1
//            while ((line = reader.readLine()) != null) {
//                lineNumber++;
//                try {
//                    OrganizationRequestDTO dto = parseCsvLine(line);
//                    validateDto(dto); // ← ваша валидация по ЛР1
//                    parsedOrganizations.add(dto);
//                } catch (Exception e) {
//                    errors.add("Строка " + lineNumber + ": " + e.getMessage());
//                }
//            }
//
//        } catch (Exception e) {
//            addErrorMessage("Ошибка чтения файла: " + e.getMessage());
//            e.printStackTrace();
//            return;
//        }
//
//        // Если есть ошибки — не импортируем, только сообщаем
//        if (!errors.isEmpty()) {
//            errors.forEach(this::addErrorMessage);
//            addErrorMessage("Импорт отменён из-за ошибок");
//            return;
//        }
//
//        if (parsedOrganizations.isEmpty()) {
//            addWarnMessage("Нет данных для импорта");
//            return;
//        }
//
//        // ✅ Теперь — единая транзакция
//        try {
//            int importedCount = organizationService.importFromCsv(parsedOrganizations);
//            addInfoMessage("Успешно импортировано " + importedCount + " организаций");
//            loadAll();
//        } catch (Exception e) {
//            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
//            addErrorMessage("Ошибка импорта (транзакция отменена): " + (msg != null ? msg : "неизвестная ошибка"));
//            e.printStackTrace();
//        }
//    }
//
//    public OrganizationRequestDTO parseCsvLine(String line) throws Exception {
//        String[] parts = line.split(","); // Разделитель - запятая
//
//        // ОЖИДАЕТСЯ 12 ПОЛЕЙ!
//        if (parts.length < 12) {
//            throw new IllegalArgumentException("Неверное количество полей в строке CSV. Ожидается 12.");
//        }
//
//        try {
//            String name = parts[0].trim().replace("\"", "");
//            String fullName = parts[1].trim().replace("\"", "");
//            OrganizationType type = OrganizationType.valueOf(parts[2].trim().toUpperCase().replace("\"", ""));
//
//            // Обработка пустых строк для числовых полей
//            double annualTurnover = parts[3].trim().isEmpty() ? 0.0 : Double.parseDouble(parts[3].trim().replace("\"", ""));
//            int employeesCount = parts[4].trim().isEmpty() ? 0 : Integer.parseInt(parts[4].trim().replace("\"", ""));
//            float rating = parts[5].trim().isEmpty() ? 0.0f : Float.parseFloat(parts[5].trim().replace("\"", ""));
//            double coordX = parts[6].trim().isEmpty() ? 0.0 : Double.parseDouble(parts[6].trim().replace("\"", ""));
//            long coordY = parts[7].trim().isEmpty() ? 0L : Long.parseLong(parts[7].trim().replace("\"", ""));
//
//            String officialStreet = parts[8].trim().replace("\"", "");
//            String officialZipCode = parts[9].trim().replace("\"", ""); // Добавлено
//            String postalStreet = parts[10].trim().replace("\"", "");
//            String postalZipCode = parts[11].trim().replace("\"", ""); // Добавлено
//
//
//            Coordinates coordinates = Coordinates.builder()
//                    .x(coordX)
//                    .y((int) coordY)
//                    .build();
//
//            Address officialAddress = Address.builder()
//                    .street(officialStreet)
//                    .zipCode(officialZipCode.isEmpty() ? null : officialZipCode) // Учитываем пустое значение
//                    .build();
//
//            Address postalAddress = Address.builder()
//                    .street(postalStreet)
//                    .zipCode(postalZipCode.isEmpty() ? null : postalZipCode) // Учитываем пустое значение
//                    .build();
//
//
//            return OrganizationRequestDTO.builder()
//                    .name(name)
//                    .coordinates(coordinates)
//                    .officialAddress(officialAddress)
//                    .annualTurnover(annualTurnover)
//                    .employeesCount(employeesCount)
//                    .rating(rating)
//                    .fullName(fullName)
//                    .type(type)
//                    .postalAddress(postalAddress)
//                    .build();
//
//        } catch (Exception e) {
//            throw new Exception("Ошибка парсинга строки CSV. Проверьте форматирование и типы данных: " + e.getMessage());
//        }
//    }

    // МЕТОД importCsv() теперь не нужен, но его можно оставить пустым, чтобы не менять XHTML
    public void importCsv() {
        System.out.println("=== importCsv() STARTED (Ignored, logic moved to handleFileUpload) ===");
        addWarnMessage("Нажмите 'Выберите CSV-файл', чтобы начать импорт.");
    }

    // ИСПРАВЛЕННЫЙ МЕТОД ПАРСИНГА - ВАЖНО!
    public OrganizationRequestDTO parseCsvLine(String line) throws Exception {
        String[] parts = line.split(","); // Разделитель - запятая

        if (parts.length < 12) {
            throw new IllegalArgumentException("Неверное количество полей в строке CSV. Ожидается 12.");
        }

        try {
            // Убедитесь, что порядок полей в этом коде соответствует порядку в вашем CSV-файле
            String name = parts[0].trim().replace("\"", "");
            String fullName = parts[1].trim().replace("\"", "");
            OrganizationType type = OrganizationType.valueOf(parts[2].trim().toUpperCase().replace("\"", ""));

            // Обработка пустых строк для числовых полей
            double annualTurnover = parts[3].trim().isEmpty() ? 0.0 : Double.parseDouble(parts[3].trim().replace("\"", ""));
            int employeesCount = parts[4].trim().isEmpty() ? 0 : Integer.parseInt(parts[4].trim().replace("\"", ""));
            float rating = parts[5].trim().isEmpty() ? 0.0f : Float.parseFloat(parts[5].trim().replace("\"", ""));
            double coordX = parts[6].trim().isEmpty() ? 0.0 : Double.parseDouble(parts[6].trim().replace("\"", ""));
            long coordY = parts[7].trim().isEmpty() ? 0L : Long.parseLong(parts[7].trim().replace("\"", ""));

            String officialStreet = parts[8].trim().replace("\"", "");
            String officialZipCode = parts[9].trim().replace("\"", "");
            String postalStreet = parts[10].trim().replace("\"", "");
            String postalZipCode = parts[11].trim().replace("\"", "");


            Coordinates coordinates = Coordinates.builder()
                    .x(coordX)
                    .y((int) coordY)
                    .build();

            Address officialAddress = Address.builder()
                    .street(officialStreet)
                    .zipCode(officialZipCode.isEmpty() ? null : officialZipCode)
                    .build();

            Address postalAddress = Address.builder()
                    .street(postalStreet)
                    .zipCode(postalZipCode.isEmpty() ? null : postalZipCode)
                    .build();


            return OrganizationRequestDTO.builder()
                    .name(name)
                    .coordinates(coordinates)
                    .officialAddress(officialAddress)
                    .annualTurnover(annualTurnover)
                    .employeesCount(employeesCount)
                    .rating(rating)
                    .fullName(fullName)
                    .type(type)
                    .postalAddress(postalAddress)
                    .build();

        } catch (Exception e) {
            throw new Exception("Ошибка парсинга строки CSV. Проверьте форматирование, количество полей (должно быть 12) и типы данных: " + e.getMessage());
        }
    }




    @PostConstruct
    public void init() {
        loadAll();
    }

    public void loadAll() {

        organizationsDTO = organizationService.getAllOrganizations();

        if (allOrganizations == null) {
            allOrganizations = new ArrayList<>(organizationsDTO);
        }
    }

    public void loadFiltered() {
        if (filterName == null || filterName.trim().isEmpty()) {
            loadAll();
        } else {
            // Фильтрация по полному совпадению имени
            organizationsDTO = organizationService.getAllOrganizations().stream()
                    .filter(org -> filterName.equals(org.getName()))
                    .toList();
        }
    }


    public void init_filter() {
        loadAll_filter();
    }

    public void loadAll_filter() {
        allOrganizations = organizationService.getAllOrganizations();
        organizationsDTO = new ArrayList<>(allOrganizations); // начальное состояние — полный список
    }

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
        this.newOrganizationDTO.setCoordinates(new Coordinates());
        this.newOrganizationDTO.setOfficialAddress(new Address());
        this.newOrganizationDTO.setPostalAddress(new Address());
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
        newOrganizationDTO = new OrganizationRequestDTO();
        newOrganizationDTO.setCoordinates(new Coordinates());
        newOrganizationDTO.setOfficialAddress(new Address());
        newOrganizationDTO.setPostalAddress(new Address());
    }

    public void prepareMaxFullName() {
        resetAllParams();
        this.showMaxFullNameModal = true;
    }

    public void findMaxFullName() {
        maxFullNameOrg = organizationService.getOrganizationWithMaxFullName();
    }
//    public String addOrganization() {
//        try {
//            organizationService.createOrganization(newOrganizationDTO);
//            initNewOrganization();
//            loadAll();
//            System.out.println("Организация успешно добавлена");
//            return "list?faces-redirect=true";
//        } catch (Exception e) {
//            System.out.println("Ошибка при добавлении: " + e.getMessage());
//            e.printStackTrace();
//            return null;
//        }
//    }
    // === Методы специальных операций (добавьте эти) ===


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

        Address newAddress = new Address();
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

