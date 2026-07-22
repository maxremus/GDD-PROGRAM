package org.example.gp.repository;

import org.example.gp.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByOfficeIdOrderByTransactionDateDesc(Long officeId);

    List<BankTransaction> findAllByOrderByTransactionDateDesc(); // за ADMIN
}
