---
name: cv-professionnel
description: Rédiger, réécrire ou auditer un CV professionnel (et sa lettre de motivation) pensé pour passer les filtres ATS et convaincre un recruteur en 6 secondes. Utiliser dès que l'utilisateur parle de CV, curriculum vitae, résumé, profil LinkedIn, candidature, lettre de motivation, offre d'emploi à cibler, ou demande d'améliorer/corriger/noter un CV existant.
---

# CV professionnel « impossible à refuser »

Objectif : un CV **ciblé**, **chiffré**, **lisible par un logiciel ATS** et **scannable en 6 secondes** par un humain. Un CV n'est pas une biographie : c'est une publicité pour UN poste précis.

## 1. Collecter avant d'écrire

Ne rédige jamais un CV générique. Obtiens d'abord (pose les questions manquantes en une seule fois) :

1. **L'offre visée** (texte de l'annonce ou intitulé + secteur + pays). Sans offre, demande au moins le poste et le niveau.
2. **Le CV actuel** ou, à défaut : postes (intitulé, entreprise, ville, dates mois/année), formations, compétences, langues, certifications.
3. **Les résultats** : pour chaque poste, 2 à 5 réalisations avec un chiffre (%, €/FCFA, délais, volume, nombre de personnes, classement). Si l'utilisateur n'a pas de chiffre, aide-le à en estimer un honnête (« environ 30 élèves/jour », « 3 projets livrés »). **N'invente jamais** de chiffre, diplôme, employeur ou date.
4. Pays/culture cible (France, Afrique francophone, Canada, international) — cela change photo, longueur et données personnelles (voir `references/regles-pays.md`).

## 2. Analyser l'offre

Extrais de l'annonce :
- les **10–15 mots-clés** (compétences dures, outils, certifications, intitulé exact du poste) ;
- les 3 **problèmes** que l'employeur veut résoudre ;
- le vocabulaire exact (reprends-le tel quel : l'ATS compare des chaînes).

Construis une table « exigence de l'offre → preuve dans le parcours ». Chaque exigence importante doit avoir au moins une preuve visible dans le CV.

## 3. Structure (ordre recommandé)

1. **En-tête** : Prénom NOM, **intitulé du poste visé** (identique à l'annonce), téléphone, e-mail professionnel, ville, LinkedIn/portfolio. Pas d'adresse complète.
2. **Accroche / profil** (3 lignes max) : `[Intitulé] + [années d'expérience] + [2 spécialités clés de l'offre] + [1 résultat phare chiffré] + [ce que j'apporte au poste]`.
3. **Compétences clés** : 8–12 mots-clés de l'offre, regroupés (Techniques / Outils / Langues). Pas de barres ni d'étoiles de niveau.
4. **Expérience professionnelle** (antichronologique) : Intitulé — Entreprise, Ville | mm/aaaa – mm/aaaa, puis 3–5 puces.
5. **Formation** (avant l'expérience seulement pour un junior/étudiant).
6. **Certifications, projets, bénévolat, langues** si pertinents pour le poste.

Longueur : **1 page** (< 10 ans d'expérience), **2 pages max** sinon.

## 4. Écrire des puces qui convainquent

Formule : **Verbe d'action au passé + quoi + comment + résultat chiffré**.

- ❌ « Responsable de la gestion des inscriptions. »
- ✅ « Digitalisé les inscriptions de 1 200 élèves via un logiciel de gestion, réduisant le délai de traitement de 5 jours à 24 h. »

Règles :
- Commence chaque puce par un verbe fort (liste dans `references/verbes-action.md`), jamais par « Responsable de », « Chargé de », « Participation à ».
- 1 à 2 lignes par puce, 60 % des puces au moins avec un chiffre.
- Résultats > tâches. Si c'est la description de poste, supprime.
- Pas de « je », pas de pronoms, pas de clichés (« dynamique », « motivé », « polyvalent », « force de proposition ») sans preuve.
- Les mots-clés de l'offre apparaissent **dans les puces**, pas seulement dans la section compétences.

## 5. Compatibilité ATS (non négociable)

- Une seule colonne (ou une colonne latérale simple pour les coordonnées uniquement).
- Pas de tableaux, zones de texte, en-têtes/pieds de page Word contenant des infos importantes, icônes à la place des mots, graphiques de compétences.
- Titres de sections standards : « Expérience professionnelle », « Formation », « Compétences ».
- Police standard (Calibri, Arial, Helvetica, Garamond), 10–12 pt corps, 14–18 pt nom.
- Dates au format homogène `mm/aaaa`.
- Fichier final : **PDF texte** (pas scanné) sauf si l'annonce exige du .docx. Nom : `CV_Prenom_Nom_Poste.pdf`.

## 6. Design humain (les 6 secondes)

- Marges 1,5–2 cm, espace blanc généreux, alignement à gauche.
- 1 couleur d'accent sobre maximum (bleu marine, vert foncé, bordeaux).
- Hiérarchie claire : nom > intitulé > titres de section > postes > puces.
- Le haut du premier tiers doit répondre seul à « pourquoi cette personne pour CE poste ? ».

## 7. Produire le livrable

- **Point de départ accepté** : texte collé, ancien CV (PDF, Word, photo/capture — lis-le entièrement, y compris par lecture d'image), profil LinkedIn. Extrais toutes les infos, puis applique les étapes 1 à 6.
- **Accords grammaticaux** : ne devine jamais le genre (ni d'après le prénom, ni d'après la photo). Reprends les accords du texte fourni ; s'il n'y en a pas, demande ou utilise des formulations neutres (« Sens de l'organisation » plutôt que « Organisé(e) »).
- **Style visuel** : propose 3 styles du catalogue `references/catalogue-modeles.md` adaptés au métier, ou laisse l'utilisateur choisir un numéro (1–24).
- **Canva disponible** (outils `mcp__Canva__*` ou connecteur Canva) → `create-design` avec format « Resume (A4) » et un brief qui contient le style choisi ET le texte final complet avec la consigne « utiliser exactement ce contenu, sans inventer ». Pour donner du choix, lance 2–3 générations en parallèle avec des styles différents. Photo : téléverse-la avec `create-upload-url` ; si l'envoi échoue, prévois un emplacement photo et explique comment l'ajouter dans Canva. Exporte en PDF et donne le lien d'édition.
- CV en Word demandé → utilise le skill `anthropic-skills:docx`.
- CV en PDF → `anthropic-skills:pdf` (ou docx puis export PDF).
- Sinon, document éditable/partageable → `anthropic-skills:docs`.
- Fournis toujours aussi une version texte brut (pour copier dans les formulaires en ligne / LinkedIn).

## 8. Lettre de motivation (si demandée)

250–350 mots, 4 blocs : (1) accroche spécifique à l'entreprise, (2) « vous avez besoin de X, j'ai fait X » avec 2 preuves chiffrées, (3) pourquoi cette entreprise précisément, (4) appel à un entretien. Jamais de copier-coller du CV.

## 9. Audit final — check-list avant de rendre

Note le CV sur 100 et liste les corrections restantes :

- [ ] Intitulé en en-tête = intitulé de l'offre (10)
- [ ] ≥ 80 % des mots-clés de l'offre présents (15)
- [ ] Accroche ≤ 3 lignes, chiffrée, ciblée (10)
- [ ] Chaque puce commence par un verbe d'action (10)
- [ ] ≥ 60 % des puces chiffrées (15)
- [ ] Mise en page ATS (une colonne, pas de tableaux/icônes) (10)
- [ ] 1–2 pages, aéré, cohérent (dates, polices, ponctuation) (10)
- [ ] Zéro faute d'orthographe/grammaire — relis mot à mot (10)
- [ ] Coordonnées complètes et e-mail professionnel (5)
- [ ] Aucune information inventée ni non vérifiable (5)

Si le score < 85, corrige avant de livrer. Termine en donnant à l'utilisateur les 3 améliorations qui dépendent de lui (ex. : chiffres à confirmer, certification à obtenir, lien LinkedIn à mettre à jour).
