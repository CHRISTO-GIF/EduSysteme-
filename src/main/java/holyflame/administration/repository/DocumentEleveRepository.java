package holyflame.administration.repository;

import holyflame.administration.model.DocumentEleve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentEleveRepository extends JpaRepository<DocumentEleve, Long> {
    List<DocumentEleve> findByEleveIdOrderByDateUploadDesc(Long eleveId);
}
