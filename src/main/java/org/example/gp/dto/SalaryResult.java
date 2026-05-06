package org.example.gp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SalaryResult {

    private double grossSalary;
    private double employeeInsurance;
    private double employerInsurance;
    private double tax;
    private double netSalary;
    private double totalEmployerCost;
}
