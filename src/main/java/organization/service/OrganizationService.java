package organization.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import lombok.NoArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import organization.dto.*;
import organization.entity.*;
import organization.mapper.OrganizationMapper;
import organization.repository.OrganizationRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@NoArgsConstructor
public class OrganizationService {

    @Inject
    private OrganizationRepository organizationRepository;

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

    @Transactional
    public OrganizationResponseDTO mergeOrganizations(OrganizationMergeRequestDTO dto) {
        Organization org1 = organizationRepository.findById(dto.getOrgId1());
        Organization org2 = organizationRepository.findById(dto.getOrgId2());
        if (org1 == null || org2 == null) {
            throw new IllegalArgumentException("Одна из организаций не найдена");
        }
        org1.setName(dto.getNewName());
        org1.setFullName(dto.getNewName());
        org1.setPostalAddress(dto.getNewAddress());
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

    @Transactional
    public int importFromCsv(InputStream csvStream) throws IOException {
        List<Organization> organizations = new ArrayList<>();

        try (CSVParser parser = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreEmptyLines()
                .withTrim()
                .parse(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            int lineNumber = 1; // первая строка — заголовки, данные с 2-й
            for (CSVRecord record : parser) {
                lineNumber++;

                try {
                    Organization org = parseCsvRecord(record);
                    validateOrganization(org); // ← ваша же валидация!
                    // ID и creationDate проставятся автоматически при persist
                    org.setId(null); // гарантируем генерацию нового ID
                    org.setCreationDate(null); // будет проставлено триггером / listeners / в репозитории
                    organizations.add(org);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Ошибка в строке " + lineNumber + ": " + e.getMessage(), e);
                }
            }

            // Сохраняем все — если где-то ошибка → откат всей транзакции
            for (Organization org : organizations) {
                organizationRepository.create(org); // ваш метод create()
            }

            return organizations.size();
        }
    }

    private Organization parseCsvRecord(CSVRecord record) {
        Organization org = new Organization();

        // === Обязательные поля ===
        org.setName(record.get("name"));
        org.setFullName(record.get("fullName"));
        org.setType(OrganizationType.valueOf(record.get("type").trim().toUpperCase()));

        // === Опциональные числовые поля (могут быть пустыми) ===
        if (!record.get("annualTurnover").isEmpty()) {
            org.setAnnualTurnover(Double.valueOf(record.get("annualTurnover")));
        }
        if (!record.get("employeesCount").isEmpty()) {
            org.setEmployeesCount(Integer.valueOf(record.get("employeesCount")));
        }
        if (!record.get("rating").isEmpty()) {
            org.setRating(Float.valueOf(record.get("rating")));
        }

        // === Координаты (вложенный объект) ===
        Coordinates coords = new Coordinates();
        coords.setX(Integer.valueOf(record.get("coordinates.x")));
        coords.setY(Integer.valueOf(record.get("coordinates.y")));
        org.setCoordinates(coords);

        // === Официальный адрес ===
        Address official = new Address();
        official.setStreet(record.get("officialAddress.street"));
        String officialZip = record.get("officialAddress.zipCode");
        official.setZipCode(officialZip.isEmpty() ? null : officialZip);
        org.setOfficialAddress(official);

        // === Почтовый адрес ===
        Address postal = new Address();
        postal.setStreet(record.get("postalAddress.street"));
        String postalZip = record.get("postalAddress.zipCode");
        postal.setZipCode(postalZip.isEmpty() ? null : postalZip);
        org.setPostalAddress(postal);

        return org;
    }

    private void validateOrganization(Organization organization) {
        if (organization.getName() == null || organization.getName().trim().isEmpty()) {
            throw new ValidationException("Organization name не может быть пустым");
        }
        if (organization.getOfficialAddress() == null) {
            throw new ValidationException("Official address не может быть null");
        }
        if (organization.getAnnualTurnover() <= 0) {
            throw new ValidationException("Annual turnover должен быть положительным");
        }
        if (organization.getEmployeesCount() <= 0) {
            throw new ValidationException("Employees count должно быть положительным");
        }
        if (organization.getRating() <= 0) {
            throw new ValidationException("Rating должке быть положительным");
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
        if (coordinates.getY() <= -461) {
            throw new ValidationException("Y должен быть больше -461");
        }
    }
    private void validateAddress(Address address, String addressType) {
        if (address.getStreet() == null || address.getStreet().trim().isEmpty()) {
            throw new ValidationException(addressType + " street не может быть пустым");
        }
    }


    //для дебага
//    @Transactional
//    public TestEntity createEntity(TestEntity test) {
//        System.out.println("starting service addEntity");
//        return organizationRepository.createTestEntity(test);
//    }

}
