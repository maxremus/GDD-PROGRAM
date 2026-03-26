package org.example.gp.repository;

import org.example.gp.entity.Company;
import org.example.gp.entity.CompanyWorked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Company findByName(String name);

    boolean existsByName(String name);

    Object findByNameContaining(String keyword);

    List<Company> findByYear(Integer year);

    boolean existsByNameAndYear(String name, Integer year);

    List<Company> findByMonthAndYear(Integer month, Integer years);

    boolean existsByNameAndMonthAndYear(String name, Integer month, Integer year);

    Optional<Company> findByNameAndMonthAndYear(String name, Integer month, Integer year);

    List<Company> findByNameContainingIgnoreCase(String name);

}
