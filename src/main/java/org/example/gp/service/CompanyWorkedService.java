package org.example.gp.service;

import org.example.gp.repository.CompanyWorkedRepository;
import org.springframework.stereotype.Service;

@Service
public class CompanyWorkedService {

    private final CompanyWorkedRepository companyWorkedRepository;

    public CompanyWorkedService(CompanyWorkedRepository companyWorkedRepository) {
        this.companyWorkedRepository = companyWorkedRepository;
    }

    public void deleteWorked(Long id) {
        if (!companyWorkedRepository.existsById(id)) {
            throw new RuntimeException("Worked record not found");
        }

        companyWorkedRepository.deleteById(id);
    }

    public void deleteById(Long id) {
        if (!companyWorkedRepository.existsById(id)) {
            throw new RuntimeException("Worked record not found");
        }

        companyWorkedRepository.deleteById(id);
    }
}
