package org.example.gp.service;

import org.example.gp.dto.SalaryResult;
import org.springframework.stereotype.Service;

@Service
public class SalaryService {

    public SalaryResult calculate(double netSalary) {

        double gross = netSalary / 0.779;

        double employeeInsurance = gross * 0.1378;

        double taxBase = gross - employeeInsurance;

        double tax = taxBase * 0.10;

        double employerInsurance = gross * 0.1892;

        double totalEmployerCost =
                gross + employerInsurance;

        return new SalaryResult(
                round(gross),
                round(employeeInsurance),
                round(employerInsurance),
                round(tax),
                round(netSalary),
                round(totalEmployerCost)
        );
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
