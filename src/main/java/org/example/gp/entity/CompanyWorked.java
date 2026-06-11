package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyWorked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Company company;

    private Integer month;
    private Integer year;

    @Enumerated(EnumType.STRING)
    private FilingStatusMore status;


    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public Integer getMonth() {
        return month;
    }

    public Integer getYear() {
        return year;
    }

    public FilingStatusMore getStatus() {
        return status;
    }
}
