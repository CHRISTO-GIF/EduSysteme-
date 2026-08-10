package holyflame.administration.service;

import holyflame.administration.model.PeriodeCalendrier;
import holyflame.administration.repository.PeriodeCalendrierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Calcule un nombre de jours d'ecole reels sur une periode : exclut les week-ends et les
 * periodes de vacances/jours feries declares dans le calendrier scolaire de l'etablissement.
 * Sert de base a tout calcul d'assiduite qui se veut plus fiable qu'un simple compte de
 * jours calendaires entre deux dates.
 */
@Service
public class CalendrierScolaireService {

    @Autowired private PeriodeCalendrierRepository periodeCalendrierRepository;

    public long joursEcoleOuvres(LocalDate debut, LocalDate fin, Long etabId) {
        if (debut == null || fin == null || fin.isBefore(debut) || etabId == null) return 0;
        List<PeriodeCalendrier> periodes = periodeCalendrierRepository.findByEtablissementIdOrderByDateDebutAsc(etabId).stream()
            .filter(PeriodeCalendrier::isExclusion)
            .toList();
        long total = 0;
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            DayOfWeek jour = d.getDayOfWeek();
            if (jour == DayOfWeek.SATURDAY || jour == DayOfWeek.SUNDAY) continue;
            final LocalDate jourCourant = d;
            boolean enPeriodeNonEnseignee = periodes.stream()
                .anyMatch(p -> !jourCourant.isBefore(p.getDateDebut()) && !jourCourant.isAfter(p.getDateFin()));
            if (enPeriodeNonEnseignee) continue;
            total++;
        }
        return total;
    }

    /** Jours ouvrables = tous les jours sauf le dimanche (definition legale usuelle, hors vacances/feries). */
    public long joursOuvrables(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) return 0;
        long total = 0;
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) total++;
        }
        return total;
    }

    public long joursCalendaires(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(debut, fin) + 1;
    }
}
