package holyflame.administration.repository;

import holyflame.administration.model.RappelParent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RappelParentRepository extends JpaRepository<RappelParent, Long> {
    List<RappelParent> findByParentEmailOrderByDateRappelAsc(String parentEmail);

    @Transactional
    void deleteByIdAndParentEmail(Long id, String parentEmail);
}
