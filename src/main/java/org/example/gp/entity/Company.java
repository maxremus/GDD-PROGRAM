package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String name;

    @Enumerated(EnumType.STRING)
    private FilingStatus statistics;

    @Column(name = "reporting_year")
    private Integer year; // Добавете това поле!

    @Enumerated(EnumType.STRING)
    private FilingStatus ch73Al1;

    @Enumerated(EnumType.STRING)
    private FilingStatus ch73Al6;

    @Enumerated(EnumType.STRING)
    private FilingStatus annualDeclaration;

    @Enumerated(EnumType.STRING)
    private FilingStatus declaration6;

    public FilingStatusMore getStatistics2() {
        return statistics2;
    }

    public void setStatistics2(FilingStatusMore statistics2) {
        this.statistics2 = statistics2;
    }

    @Enumerated(EnumType.STRING)
    private FilingStatusMore statistics2;

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    private Integer month;


    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }


    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatistics(FilingStatus statistics) {
        this.statistics = statistics;
    }


    public void setCh73Al1(FilingStatus ch73Al1) {
        this.ch73Al1 = ch73Al1;
    }

    public void setCh73Al6(FilingStatus ch73Al6) {
        this.ch73Al6 = ch73Al6;
    }

    public void setAnnualDeclaration(FilingStatus annualDeclaration) {
        this.annualDeclaration = annualDeclaration;
    }

    public void setDeclaration6(FilingStatus declaration6) {
        this.declaration6 = declaration6;
    }
}
