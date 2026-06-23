package org.example.gp.service;

import org.example.gp.dto.SalaryResult;
import org.springframework.stereotype.Service;

@Service
public class SalaryService {

    // Служител
    private static final double EMPLOYEE_INSURANCE = 0.1378;

    // Работодател + ТЗПБ 0.6%
    private static final double EMPLOYER_INSURANCE = 0.1952;

    // Данък
    private static final double TAX = 0.10;

    /**
     * Брутна → Нетна
     */
    public SalaryResult calculate(double grossSalary) {

        double employeeInsurance  = grossSalary * EMPLOYEE_INSURANCE;
        double taxableIncome      = grossSalary - employeeInsurance;
        double tax                = taxableIncome * TAX;
        double netSalary          = taxableIncome - tax;
        double employerInsurance  = grossSalary * EMPLOYER_INSURANCE;
        double totalEmployerCost  = grossSalary + employerInsurance;

        return new SalaryResult(
                round(grossSalary),
                round(employeeInsurance),
                round(employerInsurance),
                round(tax),
                round(netSalary),
                round(totalEmployerCost)
        );
    }

    /**
     * Нетна → Брутна (обратен калкулатор)
     * нето = брuto × (1 - EMPLOYEE_INSURANCE) × (1 - TAX)
     *      = брuto × 0.8622 × 0.90
     *      = брuto × 0.77598
     * ∴ брuto = нето / 0.77598
     */
    public SalaryResult calculateReverse(double netSalary) {

        double factor     = (1.0 - EMPLOYEE_INSURANCE) * (1.0 - TAX);
        double gross      = netSalary / factor;

        return calculate(round(gross));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
