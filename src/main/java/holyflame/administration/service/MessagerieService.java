package holyflame.administration.service;

import holyflame.administration.model.MessagePrive;
import holyflame.administration.repository.MessagePriveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MessagerieService {

    @Autowired private MessagePriveRepository messagePriveRepository;
    @Autowired private FileStorageService fileStorageService;

    public Map<String, Object> creerContact(String email, String nom, String role) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("email", email);
        c.put("nom", nom);
        c.put("role", role);
        c.put("matieres", new ArrayList<String>());
        return c;
    }

    public List<Map<String, Object>> construireConversations(String monEmail, Map<String, Map<String, Object>> contactsParEmail) {
        List<MessagePrive> mesMessages = messagePriveRepository.findAllConcernant(monEmail);
        Map<String, List<MessagePrive>> parCorrespondant = new LinkedHashMap<>();
        for (MessagePrive m : mesMessages) {
            String correspondant = monEmail.equalsIgnoreCase(m.getExpediteurEmail()) ? m.getDestinataireEmail() : m.getExpediteurEmail();
            parCorrespondant.computeIfAbsent(correspondant, k -> new ArrayList<>()).add(m);
        }

        List<Map<String, Object>> conversations = new ArrayList<>();
        for (Map.Entry<String, List<MessagePrive>> entry : parCorrespondant.entrySet()) {
            String correspondant = entry.getKey();
            List<MessagePrive> msgs = entry.getValue();
            MessagePrive dernier = msgs.get(msgs.size() - 1);
            long nonLus = msgs.stream().filter(m -> !m.isLu() && correspondant.equalsIgnoreCase(m.getExpediteurEmail())).count();

            Map<String, Object> contactInfo = contactsParEmail.get(correspondant);
            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("email", correspondant);
            conv.put("nom", contactInfo != null ? contactInfo.get("nom") : correspondant);
            conv.put("role", contactInfo != null ? contactInfo.get("role") : "");
            conv.put("dernierMessage", dernier.getContenu());
            conv.put("dernierDate", dernier.getDateEnvoi());
            conv.put("nonLus", nonLus);
            conversations.add(conv);
        }
        conversations.sort(Comparator.comparing((Map<String, Object> c) -> (LocalDateTime) c.get("dernierDate")).reversed());

        for (Map<String, Object> contact : contactsParEmail.values()) {
            String contactEmail = (String) contact.get("email");
            boolean dejaPresent = conversations.stream().anyMatch(c -> contactEmail.equalsIgnoreCase((String) c.get("email")));
            if (!dejaPresent) {
                Map<String, Object> conv = new LinkedHashMap<>();
                conv.put("email", contactEmail);
                conv.put("nom", contact.get("nom"));
                conv.put("role", contact.get("role"));
                conv.put("dernierMessage", null);
                conv.put("dernierDate", null);
                conv.put("nonLus", 0L);
                conversations.add(conv);
            }
        }
        return conversations;
    }

    public List<MessagePrive> chargerFilEtMarquerLu(String monEmail, String correspondant) {
        List<MessagePrive> fil = messagePriveRepository.findConversation(monEmail, correspondant);
        for (MessagePrive m : fil) {
            if (!m.isLu() && correspondant.equalsIgnoreCase(m.getExpediteurEmail())) {
                m.setLu(true);
                messagePriveRepository.save(m);
            }
        }
        return fil;
    }

    public List<MessagePrive> mediasPartages(List<MessagePrive> fil) {
        return fil.stream().filter(m -> m.getPieceJointeChemin() != null).toList();
    }

    public List<Map<String, Object>> avecSeparateursDate(List<MessagePrive> fil) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate dernierJour = null;
        LocalDate aujourdHui = LocalDate.now();
        for (MessagePrive m : fil) {
            LocalDate jour = m.getDateEnvoi().toLocalDate();
            Map<String, Object> row = new LinkedHashMap<>();
            if (dernierJour == null || !jour.equals(dernierJour)) {
                String label;
                if (jour.equals(aujourdHui)) label = "AUJOURD'HUI";
                else if (jour.equals(aujourdHui.minusDays(1))) label = "HIER";
                else label = jour.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
                row.put("separateur", label);
            } else {
                row.put("separateur", null);
            }
            row.put("message", m);
            result.add(row);
            dernierJour = jour;
        }
        return result;
    }

    public void envoyerMessage(String expediteur, String destinataire, String contenu, MultipartFile pieceJointe) throws IOException {
        boolean aTexte = contenu != null && !contenu.isBlank();
        boolean aPieceJointe = pieceJointe != null && !pieceJointe.isEmpty();
        if (!aTexte && !aPieceJointe) return;

        MessagePrive m = new MessagePrive();
        m.setExpediteurEmail(expediteur);
        m.setDestinataireEmail(destinataire);
        m.setContenu(aTexte && contenu != null ? contenu.trim() : "");
        m.setDateEnvoi(LocalDateTime.now());
        m.setLu(false);
        if (aPieceJointe && pieceJointe != null) {
            String path = fileStorageService.store(pieceJointe, "messagerie");
            m.setPieceJointeNom(pieceJointe.getOriginalFilename());
            m.setPieceJointeChemin(path);
            m.setPieceJointeType(pieceJointe.getContentType());
            m.setPieceJointeTaille(pieceJointe.getSize());
        }
        messagePriveRepository.save(m);
    }
}
