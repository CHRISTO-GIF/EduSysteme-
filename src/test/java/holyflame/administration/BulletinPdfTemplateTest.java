package holyflame.administration;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Matiere;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie que le template bulletin-pdf.html est du XHTML bien forme et que openhtmltopdf
 * parvient a le convertir en PDF, sans dependre d'un contexte Spring ni d'une base de donnees
 * (le parsing XML strict d'openhtmltopdf est le point de rupture le plus probable d'un template
 * ecrit a la main).
 */
class BulletinPdfTemplateTest {

	@Test
	void bulletinPdfTemplateRendersToValidPdf() throws Exception {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");

		SpringTemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(resolver);

		Context ctx = new Context();
		ctx.setVariables(donneesBulletinDeTest());

		String html = templateEngine.process("bulletin-pdf", ctx);
		assertTrue(html.contains("DUPONT"), "le nom de l'élève doit apparaître dans le HTML généré");

		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
		builder.useFastMode();
		builder.withHtmlContent(html, null);
		builder.toStream(sortie);
		builder.run();

		byte[] pdf = sortie.toByteArray();
		assertTrue(pdf.length > 500, "le PDF genere doit contenir des donnees");
		assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF-"),
				"le fichier genere doit commencer par l'en-tête PDF standard");
	}

	private Map<String, Object> donneesBulletinDeTest() {
		Eleve eleve = new Eleve();
		eleve.setNom("DUPONT");
		eleve.setPrenom("Jean");
		eleve.setMatricule("HF-0001");
		Classe classe = new Classe();
		classe.setNom("6ème A");
		eleve.setClasse(classe);

		Matiere matiere = new Matiere();
		matiere.setNom("Mathématiques");
		matiere.setCoefficient(4.0);

		Map<String, Object> ligne = new LinkedHashMap<>();
		ligne.put("matiere", matiere);
		ligne.put("moyenne", 14.5);
		ligne.put("moyenneClasse", 12.3);
		ligne.put("appreciation", "Bon trimestre, continuez ainsi.");
		ligne.put("moyenneDevoir1", 13.0);
		ligne.put("moyenneDevoir2", 15.0);
		ligne.put("moyenneDevoir3", null);
		ligne.put("moyenneExamens", 15.5);
		ligne.put("mention", "BIEN");

		Map<String, List<Map<String, Object>>> poles = new LinkedHashMap<>();
		poles.put("Pôle Scientifique", List.of(ligne));

		Map<String, Object> donnees = new LinkedHashMap<>();
		donnees.put("eleve", eleve);
		donnees.put("poles", poles);
		donnees.put("moyenneGenerale", 14.2);
		donnees.put("moyenneClasseGenerale", 12.8);
		donnees.put("moyenneControleContinu", 13.5);
		donnees.put("moyenneExamens", 15.0);
		donnees.put("mention", "BIEN");
		donnees.put("appreciation", "Très bons résultats. Encouragements du conseil de classe.");
		donnees.put("rang", 3);
		donnees.put("effectif", 28);
		donnees.put("professeurTitulaire", null);
		donnees.put("photoDataUri", null);
		donnees.put("conduite", null);
		donnees.put("absencesJustifiees", 2L);
		donnees.put("absencesNonJustifiees", 0L);
		donnees.put("felicitations", false);
		donnees.put("tableauHonneur", true);
		donnees.put("codeVerification", "ETAB1-EL1-T1-20252026");
		donnees.put("anneeScolaire", "2025-2026");
		donnees.put("trimestre", 1);
		donnees.put("nomEtab", "HolyFlame");
		donnees.put("adresseEtab", "Abidjan, Côte d'Ivoire");
		donnees.put("emailEtab", "contact@holyflame.com");
		donnees.put("logoDataUri", null);
		donnees.put("devise", "Excellence & Discipline");
		donnees.put("chefEtablissement", "M. Le Directeur");
		return donnees;
	}
}
