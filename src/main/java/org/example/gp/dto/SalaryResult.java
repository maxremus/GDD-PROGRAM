package org.example.gp.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SalaryResult {

    private double grossSalary;
    private double employeeInsurance;
    private double employerInsurance;
    private double tax;
    private double netSalary;
    private double totalEmployerCost;
}
