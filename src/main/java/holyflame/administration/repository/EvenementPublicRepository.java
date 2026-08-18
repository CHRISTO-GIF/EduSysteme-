package holyflame.administration.repository;

import holyflame.administration.model.EvenementPublic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EvenementPublicRepository extends JpaRepository<EvenementPublic, Long> {
    List<EvenementPublic> findByEtablissementIdOrderByDateEvenementAsc(Long etablissementId);
    List<EvenementPublic> findByEtablissementIdAndDateEvenementGreaterThanEqualOrderByDateEvenementAsc(
        Long etablissementId, LocalDate aPartirDe);
}
