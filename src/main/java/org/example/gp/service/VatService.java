package org.example.gp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VatService {

    public double calculateVat(double amount, double rate) {

        double vat = amount * rate / 100;
        log.info("Изчислено ДДС: {}", vat);

        return vat;

    }

    public double calculateTotal(double amount, double rate) {
        return amount + calculateVat(amount, rate);
    }
}
