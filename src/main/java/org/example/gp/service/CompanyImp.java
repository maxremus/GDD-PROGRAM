package org.example.gp.service;

import org.example.gp.dto.CompanyImportDto;
import org.example.gp.entity.Company;
import org.example.gp.entity.FilingStatus;
import org.example.gp.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyImp implements CompanyService {

    public final CompanyRepository companyRepository;

    public CompanyImp(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }


    @Override
    public Company createCompany(Company company) {
        if (companyRepository.existsByName(company.getName())) {
            throw new RuntimeException("Company already exists");
        }
        companyRepository.save(company);
        return company;
    }

    @Override
    public void deleteCompany(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found with id: " + id));
        companyRepository.delete(company);
    }

    @Override
    public Company updateCompany(Company company) {

        Company existing = companyRepository.findById(company.getId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        existing.setName(company.getName());

        existing.setStatistics(company.getStatistics());
        existing.setCh73Al1(company.getCh73Al1());
        existing.setCh73Al6(company.getCh73Al6());
        existing.setAnnualDeclaration(company.getAnnualDeclaration());
        existing.setDeclaration6(company.getDeclaration6());

        return companyRepository.save(existing);
    }

    @Override
    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    @Override
    public Optional<Company> getCompanyById(Long editId) {
        return companyRepository.findById(editId);
    }

    @Override
    public List<Company> searchCompanies(String keyword) {
        return companyRepository.findByNameContainingIgnoreCase(keyword);
    }

    @Override
    public boolean existsById(Long id) {
        return companyRepository.existsById(id);
    }

    public void copyCompaniesToNextYear(String currentYear, String nextYear) {

        List<Company> currentCompanies = companyRepository.findByYear(Integer.valueOf(currentYear));

        for (Company company : currentCompanies) {

            // ако вече има такава фирма за новата година — пропускаме
            if (!companyRepository.existsByNameAndYear(company.getName(), Integer.valueOf(nextYear))) {

                Company newCompany = new Company();
                newCompany.setName(company.getName());
                newCompany.setYear(Integer.valueOf(nextYear));

                // НУЛИРАМЕ статусите
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

        for (CompanyImportDto dto : companies) {

            Company company = Company.builder()
                    .name(dto.getName())
                    .year(dto.getYear())
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
