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

    public SalaryResult calculate(double grossSalary) {

        // Осигуровки служител
        double employeeInsurance =
                grossSalary * EMPLOYEE_INSURANCE;

        // Облагаем доход
        double taxableIncome =
                grossSalary - employeeInsurance;

        // Данък
        double tax =
                taxableIncome * TAX;

        // Нетна заплата
        double netSalary =
                taxableIncome - tax;

        // Осигуровки работодател
        double employerInsurance =
                grossSalary * EMPLOYER_INSURANCE;

        // Общ разход
        double totalEmployerCost =
                grossSalary + employerInsurance;

        return new SalaryResult(

                round(grossSalary),

                round(employeeInsurance),

                round(employerInsurance),

                round(tax),

                round(netSalary),

                round(totalEmployerCost)
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
