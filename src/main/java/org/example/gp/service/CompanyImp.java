package org.example.gp.service;

import org.example.gp.dto.CompanyImportDto;
import org.example.gp.entity.Company;
import org.example.gp.entity.FilingStatus;
import org.example.gp.entity.User;
import org.example.gp.repository.CompanyRepository;
import org.example.gp.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyImp implements CompanyService {

    public final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public CompanyImp(CompanyRepository companyRepository, UserRepository userRepository,
                      SubscriptionService subscriptionService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
    }

    // -------------------------------------------------------------------------
    // Взима текущо логнатия потребител от БД
    // -------------------------------------------------------------------------
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    // -------------------------------------------------------------------------
    // Връща officeId на текущия потребител.
    // ROLE_ADMIN → null (системен, вижда всичко)
    // ROLE_OFFICE / ROLE_USER → своя officeId
    // -------------------------------------------------------------------------
    private Long getCurrentOfficeId() {
        User user = getCurrentUser();
        if (user == null) return null;
        if ("ROLE_ADMIN".equals(user.getRole())) return null; // системен admin
        return user.getOfficeId();
    }

    // -------------------------------------------------------------------------
    // getAllCompanies:
    //   ROLE_ADMIN  → всички фирми от всички кантори
    //   ROLE_OFFICE / ROLE_USER → само фирмите на тяхната кантора
    // -------------------------------------------------------------------------
    @Override
    public List<Company> getAllCompanies() {
        Long officeId = getCurrentOfficeId();
        if (officeId == null) {
            return companyRepository.findAll(); // само системен ADMIN
        }
        return companyRepository.findByOfficeId(officeId);
    }

    @Override
    public List<Company> searchCompanies(String keyword) {
        Long officeId = getCurrentOfficeId();
        if (officeId == null) {
            return companyRepository.findByNameContainingIgnoreCase(keyword);
        }
        return companyRepository.findByOfficeIdAndNameContainingIgnoreCase(officeId, keyword);
    }

    @Override
    public Company createCompany(Company company) {
        Long officeId = getCurrentOfficeId();
        if (officeId == null) {
            throw new RuntimeException("Системният администратор не може да добавя фирми директно.");
        }
        if (!subscriptionService.hasAccess(officeId)) {
            throw new RuntimeException("Абонаментът ви е изтекъл. Моля изберете план за продължение.");
        }
        if (!subscriptionService.canAddMoreCompanies(officeId)) {
            throw new RuntimeException("Достигнат е лимита на фирми за вашия план. Преминете към по-голям план.");
        }
        if (companyRepository.existsByNameAndOfficeId(company.getName(), officeId)) {
            throw new RuntimeException("Фирма с това име вече съществува в тази кантора.");
        }
        company.setOfficeId(officeId);
        companyRepository.save(company);
        return company;
    }

    @Override
    public void deleteCompany(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Фирмата не е намерена: " + id));
        // Сигурност: може да трие само от собствената си кантора
        Long officeId = getCurrentOfficeId();
        if (officeId != null && !officeId.equals(company.getOfficeId())) {
            throw new RuntimeException("Нямате права да изтриете тази фирма.");
        }
        companyRepository.delete(company);
    }

    @Override
    public Company updateCompany(Company company) {
        Company existing = companyRepository.findById(company.getId())
                .orElseThrow(() -> new RuntimeException("Фирмата не е намерена."));

        // Сигурност: може да редактира само от собствената си кантора
        Long officeId = getCurrentOfficeId();
        if (officeId != null && !officeId.equals(existing.getOfficeId())) {
            throw new RuntimeException("Нямате права да редактирате тази фирма.");
        }

        existing.setName(company.getName());
        existing.setStatistics(company.getStatistics());
        existing.setCh73Al1(company.getCh73Al1());
        existing.setCh73Al6(company.getCh73Al6());
        existing.setAnnualDeclaration(company.getAnnualDeclaration());
        existing.setDeclaration6(company.getDeclaration6());

        return companyRepository.save(existing);
    }

    @Override
    public Optional<Company> getCompanyById(Long editId) {
        return companyRepository.findById(editId);
    }

    @Override
    public boolean existsById(Long id) {
        return companyRepository.existsById(id);
    }

    @Override
    public void copyCompaniesToNextYear(String currentYear, String nextYear) {
        Long officeId = getCurrentOfficeId();
        List<Company> currentCompanies = (officeId != null)
                ? companyRepository.findByOfficeIdAndYear(officeId, Integer.valueOf(currentYear))
                : companyRepository.findByYear(Integer.valueOf(currentYear));

        for (Company company : currentCompanies) {
            boolean exists = (officeId != null)
                    ? companyRepository.existsByNameAndYearAndOfficeId(company.getName(), Integer.valueOf(nextYear), officeId)
                    : companyRepository.existsByNameAndYear(company.getName(), Integer.valueOf(nextYear));

            if (!exists) {
                Company newCompany = new Company();
                newCompany.setName(company.getName());
                newCompany.setYear(Integer.valueOf(nextYear));
                newCompany.setOfficeId(company.getOfficeId());
                newCompany.setStatistics(FilingStatus.NOT_REQUIRED);
                newCompany.setCh73Al1(FilingStatus.NOT_REQUIRED);
                newCompany.setCh73Al6(FilingStatus.NOT_REQUIRED);
                newCompany.setAnnualDeclaration(FilingStatus.NOT_REQUIRED);
                newCompany.setDeclaration6(FilingStatus.NOT_REQUIRED);
                companyRepository.save(newCompany);
            }
        }
    }

    @Override
    public void importCompanies(List<CompanyImportDto> companies) {
        Long officeId = getCurrentOfficeId();
        if (officeId == null) {
            throw new RuntimeException("Системният администратор не може да импортира фирми.");
        }
        for (CompanyImportDto dto : companies) {
            Company company = Company.builder()
                    .name(dto.getName())
                    .year(dto.getYear())
                    .officeId(officeId)
                    .statistics(dto.getStatistics())
                    .ch73Al1(dto.getCh73Al1())
                    .ch73Al6(dto.getCh73Al6())
                    .annualDeclaration(dto.getAnnualDeclaration())
                    .declaration6(dto.getDeclaration6())
                    .build();
            companyRepository.save(company);
        }
    }
}
