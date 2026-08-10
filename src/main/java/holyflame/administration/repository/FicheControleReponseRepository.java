package holyflame.administration.repository;

import holyflame.administration.model.FicheControleReponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FicheControleReponseRepository extends JpaRepository<FicheControleReponse, Long> {
    List<FicheControleReponse> findByFicheControleIdOrderByIdAsc(Long ficheControleId);

    @Modifying @Transactional
    @Query("DELETE FROM FicheControleReponse r WHERE r.ficheControle.id = :id")
    void deleteByFicheControleId(@Param("id") Long id);
}
