package holyflame.administration;

import holyflame.administration.model.Note;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests purs sur la normalisation des types d'evaluation : volontairement sans
 * contexte Spring, pour rester executables sans base de donnees.
 */
class NoteTypeTest {

	@Test
	void devoirTypesAreNormalizedAsEvaluationFamily() {
		assertTrue(Note.isDevoirLikeType("DEVOIR"));
		assertTrue(Note.isDevoirLikeType("DEVOIR2"));
		assertTrue(Note.isDevoirLikeType("DEVOIR3"));
		assertTrue(Note.isExamenType("EXAMEN"));
		assertTrue(Note.isControleContinuType("DEVOIR2"));
		assertTrue(Note.isControleContinuType("DEVOIR"));
		assertTrue(Note.isControleContinuType("DEVOIR3"));
		assertTrue(Note.isControleContinuType("PARTICIPATION"));
	}

	/**
	 * Les colonnes DEV 1 / DEV 2 / EXAMEN du bulletin trient les notes sur le type
	 * normalise : les libelles historiques saisis avant la normalisation doivent
	 * retomber sur la bonne colonne, sinon la note existe en base mais la case
	 * reste vide.
	 */
	@Test
	void legacyTypeLabelsMapToTheirBulletinColumn() {
		assertEquals(Note.TYPE_DEVOIR, Note.normalizeType("dev1"));
		assertEquals(Note.TYPE_DEVOIR, Note.normalizeType("Devoir1"));
		assertEquals(Note.TYPE_DEVOIR2, Note.normalizeType("dev2"));
		assertEquals(Note.TYPE_DEVOIR2, Note.normalizeType("devoir 2"));
		assertEquals(Note.TYPE_DEVOIR3, Note.normalizeType("dev3"));
		assertEquals(Note.TYPE_DEVOIR3, Note.normalizeType("devoir 3"));
		assertEquals(Note.TYPE_EXAMEN, Note.normalizeType(" examen "));
		assertNotEquals(Note.normalizeType("DEVOIR"), Note.normalizeType("DEVOIR2"));
		assertNotEquals(Note.normalizeType("DEVOIR2"), Note.normalizeType("DEVOIR3"));
	}
}
