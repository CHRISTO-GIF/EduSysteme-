package holyflame.administration.repository;

import holyflame.administration.model.PhotoGalerie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoGalerieRepository extends JpaRepository<PhotoGalerie, Long> {
    List<PhotoGalerie> findByEtablissementIdOrderByDateAjoutDesc(Long etablissementId);
}
