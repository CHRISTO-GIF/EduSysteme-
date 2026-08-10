package holyflame.administration.service;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Cycle de vie des annees scolaires : creation (avec duplication optionnelle des classes
 * et du budget previsionnel), activation (bascule l'annee "courante" utilisee par defaut
 * dans toute l'application), cloture (verrouille l'annee en lecture seule) et reouverture.
 */
@Service
public class AnneeScolaireService {

    @Autowired private AnneeScolaireRepository anneeScolaireRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private LigneBudgetRepository ligneBudgetRepository;

    public List<AnneeScolaire> lister(Long etabId) {
        return anneeScolaireRepository.findByEtablissementIdOrderByLibelleDesc(etabId);
    }

    public boolean estCloturee(String libelleAnnee, Long etabId) {
        if (libelleAnnee == null || etabId == null) return false;
        return anneeScolaireRepository.findByEtablissementIdAndLibelle(etabId, libelleAnnee)
            .map(a -> "CLOTUREE".equals(a.getStatut())).orElse(false);
    }

    /** A appeler en tete de toute action d'ecriture sur une entite rattachee a une annee scolaire. */
    public void verifierModifiable(String libelleAnnee, Long etabId) {
        if (estCloturee(libelleAnnee, etabId)) {
            throw new AnneeScolaireClotureeException(libelleAnnee);
        }
    }

    public AnneeScolaire creer(String libelle, Long etabId, String anneeSource, boolean dupliquerClasses, boolean dupliquerBudget) {
        if (anneeScolaireRepository.existsByEtablissementIdAndLibelle(etabId, libelle)) {
            throw new IllegalArgumentException("Cette annee scolaire existe deja.");
        }
        AnneeScolaire a = new AnneeScolaire();
        a.setLibelle(libelle);
        a.setEtablissementId(etabId);
        a.setStatut("INACTIVE");
        a.setDateCreation(LocalDate.now());
        a.setDupliqueeDepuis(anneeSource);
        anneeScolaireRepository.save(a);

        if (anneeSource != null && !anneeSource.isBlank()) {
            if (dupliquerClasses) dupliquerClasses(etabId, anneeSource, libelle);
            if (dupliquerBudget) dupliquerBudget(etabId, anneeSource, libelle);
        }
        return a;
    }

    private void dupliquerClasses(Long etabId, String source, String cible) {
        for (Classe c : classeRepository.findByAnneeScolaireAndEtablissementId(source, etabId)) {
            if (classeRepository.existsByNomAndAnneeScolaireAndEtablissementId(c.getNom(), cible, etabId)) continue;
            Classe nouvelle = new Classe();
            nouvelle.setNom(c.getNom());
            nouvelle.setNiveau(c.getNiveau());
            nouvelle.setAnneeScolaire(cible);
            nouvelle.setEtablissementId(etabId);
            nouvelle.setCapacite(c.getCapacite());
            nouvelle.setSalle(c.getSalle());
            nouvelle.setProfesseurTitulaireId(c.getProfesseurTitulaireId());
            classeRepository.save(nouvelle);
        }
    }

    private void dupliquerBudget(Long etabId, String source, String cible) {
        for (LigneBudget l : ligneBudgetRepository.findByEtablissementIdAndAnneeScolaireOrderByDesignationAsc(etabId, source)) {
            LigneBudget nouvelle = new LigneBudget();
            nouvelle.setDesignation(l.getDesignation());
            nouvelle.setCategorieComptable(l.getCategorieComptable());
            nouvelle.setMontantPrevu(l.getMontantPrevu());
            nouvelle.setMontantReel(0.0);
            nouvelle.setMois(l.getMois());
            nouvelle.setAnneeScolaire(cible);
            nouvelle.setNotes(l.getNotes());
            nouvelle.setDateCreation(LocalDate.now());
            nouvelle.setEtablissementId(etabId);
            ligneBudgetRepository.save(nouvelle);
        }
    }

    public void activer(Long anneeId, Long etabId) {
        AnneeScolaire cible = anneeScolaireRepository.findById(anneeId)
            .filter(a -> etabId != null && etabId.equals(a.getEtablissementId()))
            .orElseThrow(NoSuchElementException::new);
        if ("CLOTUREE".equals(cible.getStatut())) {
            throw new IllegalStateException("Impossible d'activer une annee cloturee : reouvrez-la d'abord.");
        }
        for (AnneeScolaire ancienne : anneeScolaireRepository.findByEtablissementIdAndStatut(etabId, "ACTIVE")) {
            ancienne.setStatut("INACTIVE");
            anneeScolaireRepository.save(ancienne);
        }
        cible.setStatut("ACTIVE");
        cible.setDateActivation(LocalDate.now());
        anneeScolaireRepository.save(cible);

        etablissementRepository.findById(etabId).ifPresent(etab -> {
            etab.setAnneeScolaire(cible.getLibelle());
            etablissementRepository.save(etab);
        });
        upsertParam(etabId, "ANNEE_SCOLAIRE", cible.getLibelle());
    }

    public void cloturer(Long anneeId, Long etabId, Long utilisateurId) {
        AnneeScolaire a = anneeScolaireRepository.findById(anneeId)
            .filter(x -> etabId != null && etabId.equals(x.getEtablissementId()))
            .orElseThrow(NoSuchElementException::new);
        if ("ACTIVE".equals(a.getStatut())) {
            throw new IllegalStateException("Activez une autre annee avant de cloturer celle-ci : l'annee active ne peut pas etre verrouillee.");
        }
        a.setStatut("CLOTUREE");
        a.setDateCloture(LocalDate.now());
        a.setClotureParId(utilisateurId);
        anneeScolaireRepository.save(a);
    }

    public void reouvrir(Long anneeId, Long etabId) {
        AnneeScolaire a = anneeScolaireRepository.findById(anneeId)
            .filter(x -> etabId != null && etabId.equals(x.getEtablissementId()))
            .orElseThrow(NoSuchElementException::new);
        a.setStatut("INACTIVE");
        a.setDateCloture(null);
        a.setClotureParId(null);
        anneeScolaireRepository.save(a);
    }

    private void upsertParam(Long etabId, String cle, String valeur) {
        Parametre p = parametreRepository.findByCleAndEtablissementId(cle, etabId)
            .orElseGet(() -> {
                Parametre np = new Parametre();
                np.setCle(cle);
                np.setDescription("Annee scolaire en cours");
                np.setCategorie("GENERAL");
                np.setEtablissementId(etabId);
                return np;
            });
        p.setValeur(valeur);
        parametreRepository.save(p);
    }
}
