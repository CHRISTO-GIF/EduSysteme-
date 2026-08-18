package holyflame.administration.repository;

import holyflame.administration.model.TransactionMobile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionMobileRepository extends JpaRepository<TransactionMobile, Long> {
    Optional<TransactionMobile> findByTransactionId(String transactionId);
    List<TransactionMobile> findByEtablissementId(Long etablissementId);
}
