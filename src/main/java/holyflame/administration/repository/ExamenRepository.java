package holyflame.administration.repository;

import holyflame.administration.model.Examen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExamenRepository extends JpaRepository<Examen, Long> {
    List<Examen> findByEtablissementIdOrderByDateExamenAscHeureDebutAsc(Long etablissementId);
    List<Examen> findByEtablissementIdAndClasseIdOrderByDateExamenAscHeureDebutAsc(Long etablissementId, Long classeId);
    long countByEtablissementIdAndDateExamenGreaterThanEqual(Long etablissementId, LocalDate date);
    long countByMatiereIdAndClasseIdAndTrimestre(Long matiereId, Long classeId, Integer trimestre);
    void deleteByMatiereId(Long matiereId);
    void deleteByClasseId(Long classeId);
}
