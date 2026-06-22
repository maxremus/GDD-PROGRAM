package org.example.gp.dto;

import org.example.gp.entity.FilingStatus;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyImportDto {

    private String name;
    private Integer year;

    private FilingStatus statistics;
    private FilingStatus ch73Al1;
    private FilingStatus ch73Al6;
    private FilingStatus annualDeclaration;
    private FilingStatus declaration6;

    public String getName() {
        return name;
    }

    public Integer getYear() {
        return year;
    }

    public FilingStatus getStatistics() {
        return statistics;
    }

    public FilingStatus getCh73Al1() {
        return ch73Al1;
    }

    public FilingStatus getCh73Al6() {
        return ch73Al6;
    }

    public FilingStatus getAnnualDeclaration() {
        return annualDeclaration;
    }

    public FilingStatus getDeclaration6() {
        return declaration6;
    }
}