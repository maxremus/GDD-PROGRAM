package org.example.gp.repository;

import org.example.gp.entity.Company;
import org.example.gp.entity.CompanyWorked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyWorkedRepository extends JpaRepository<CompanyWorked, Long> {

    Optional<CompanyWorked> findByCompanyAndMonthAndYear(
            Company company, Integer month, Integer year);
}
