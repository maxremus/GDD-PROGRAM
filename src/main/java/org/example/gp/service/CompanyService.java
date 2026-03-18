package org.example.gp.service;

import org.example.gp.dto.CompanyImportDto;
import org.example.gp.entity.Company;

import java.util.List;
import java.util.Optional;

public interface CompanyService {

    Company createCompany(Company name);

    void deleteCompany(Long id);

    Company updateCompany(Company name);

    List<Company> getAllCompanies();


    Optional<Company> getCompanyById(Long editId);

    Object searchCompanies(String keyword);

    boolean existsById(Long id);

    void copyCompaniesToNextYear(String currentYear, String nextYear);

    void importCompanies(List<CompanyImportDto> companies);
}
