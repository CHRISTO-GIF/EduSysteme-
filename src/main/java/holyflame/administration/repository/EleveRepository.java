package holyflame.administration.repository;

import holyflame.administration.model.Eleve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface EleveRepository extends JpaRepository<Eleve, Long> {
    List<Eleve> findByClasseIdOrderByNomAsc(Long classeId);
    List<Eleve> findAllByOrderByNomAscPrenomAsc();
    List<Eleve> findByEtablissementIdOrderByNomAscPrenomAsc(Long etablissementId);
    Optional<Eleve> findByCompteEmail(String compteEmail);
    Optional<Eleve> findByMatriculeAndEtablissementId(String matricule, Long etablissementId);
    Optional<Eleve> findByEmailParent(String emailParent);
    List<Eleve> findAllByEmailParentOrderByNomAsc(String emailParent);
    long countByEtablissementId(Long etablissementId);
    long countByClasseId(Long classeId);
    boolean existsByMatricule(String matricule);

    @Query("SELECT COUNT(e) > 0 FROM Eleve e WHERE e.pereCodeAcces = :code OR e.mereCodeAcces = :code")
    boolean existsByCodeAccesParent(@Param("code") String code);

    /**
     * Matricule sequentiel HF-annee-NNN garanti libre : "count() + 1" seul redonnait un
     * matricule deja attribue des qu'un eleve avait ete supprime (doublons, puis erreurs
     * sur l'import de notes qui retrouve les eleves par matricule).
     */
    default String genererMatriculeUnique(int annee) {
        long n = count() + 1;
        String matricule;
        do {
            matricule = "HF-" + annee + "-" + String.format("%03d", n++);
        } while (existsByMatricule(matricule));
        return matricule;
    }

    @Query("SELECT e FROM Eleve e WHERE :email IN (e.emailParent, e.pereEmail, e.mereEmail) ORDER BY e.nom ASC")
    List<Eleve> findAllByParentEmailAnyOrderByNomAsc(@Param("email") String email);

    @Query("SELECT e FROM Eleve e WHERE (e.pereCodeAcces IS NOT NULL AND e.pereCodeAcces = :code) " +
           "OR (e.mereCodeAcces IS NOT NULL AND e.mereCodeAcces = :code)")
    Optional<Eleve> findByPereCodeAccesOrMereCodeAcces(@Param("code") String code);

    @Query("SELECT e FROM Eleve e WHERE e.etablissementId = :etabId AND " +
           "(LOWER(e.nom) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(e.prenom) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(e.matricule) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<Eleve> searchByEtablissement(@Param("q") String q, @Param("etabId") Long etabId);

    @Modifying @Transactional
    @Query("UPDATE Eleve e SET e.etablissementId = :etabId WHERE e.etablissementId IS NULL")
    int migrateNullEtablissementId(@Param("etabId") Long etabId);
}
