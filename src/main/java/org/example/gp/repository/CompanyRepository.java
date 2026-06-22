package org.example.gp.repository;

import org.example.gp.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // --- филтриране по кантора (officeId) ---
    List<Company> findByOfficeId(Long officeId);
    List<Company> findByOfficeIdAndYear(Long officeId, Integer year);
    List<Company> findByOfficeIdAndNameContainingIgnoreCase(Long officeId, String name);
    boolean existsByNameAndOfficeId(String name, Long officeId);
    boolean existsByNameAndYearAndOfficeId(String name, Integer year, Long officeId);

    // --- запазени за ROLE_ADMIN (системен) ---
    List<Company> findByYear(Integer year);
    boolean existsByName(String name);
    boolean existsByNameAndYear(String name, Integer year);
    List<Company> findByNameContainingIgnoreCase(String name);
    Optional<Company> findByNameAndMonthAndYear(String name, Integer month, Integer year);
    List<Company> findByMonthAndYear(Integer month, Integer year);
    boolean existsByNameAndMonthAndYear(String name, Integer month, Integer year);
    Object findByNameContaining(String keyword);
    Company findByName(String name);
}
