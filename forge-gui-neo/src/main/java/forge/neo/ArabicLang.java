package forge.neo;

import java.lang.reflect.Field;
import java.util.Map;

import forge.util.Lang;
import forge.util.Localizer;

/**
 * <b>Los trozos de frase que el motor monta solo, en arabe</b> (1.0.11).
 *
 * <p>Forge tiene una clase {@code Lang} por idioma para lo que no cabe en una
 * traduccion: el posesivo ("Ana's", "your") y los ordinales ("1st"). Para un
 * idioma que no conoce usa la inglesa, y en arabe salia
 * <i>"اختر بطاقة من المقبرة your"</i>. Las tres piezas tienen solucion
 * neutra en arabe, sin generos:
 * <ul>
 *   <li>posesivo: "lo que tiene X" — <i>المقبرة لديك</i> (tu cementerio),
 *       <i>المقبرة لدى Ana</i> (el del autor);</li>
 *   <li>ordinales: الأول، الثاني…</li>
 * </ul>
 *
 * <p><b>Como entra sin tocar Forge.</b> {@code Lang} guarda sus instancias en
 * un mapa privado y las crea con un {@code switch} cerrado; aqui se mete la
 * nuestra en ese mapa por reflexion ANTES de que el motor la pida (el gancho
 * de idioma corre antes que {@code Lang.createInstance}). Si algun dia Forge
 * cambia ese campo, la reflexion falla en silencio y se queda la inglesa, que
 * es lo que habia. Solo con el arabe: para cualquier otro idioma no hace nada.
 *
 * <p>Java de la 8: lo usa tambien Android.
 */
public final class ArabicLang extends Lang {

    private static final String[] ORDINALS = {
        "الأول", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    };

    @Override
    public String getOrdinal(final int position) {
        return position >= 1 && position <= ORDINALS.length ? ORDINALS[position - 1] : "رقم " + position;
    }

    @Override
    public String getPossesive(final String name) {
        return isYou(name) ? "لديك" : "لدى " + name;
    }

    @Override
    public String getPossessedObject(final String owner, final String object) {
        if (object == null || object.trim().isEmpty()) {
            return getPossesive(owner);
        }
        return object + " " + getPossesive(owner);
    }

    private static final java.util.regex.Pattern ENGLISH_PLURAL =
            java.util.regex.Pattern.compile("([\\u0600-\\u06FF])(?:es|s)(?![A-Za-z])");

    /**
     * Quita la "s" inglesa que el motor pega a una palabra arabe.
     *
     * <p>{@code Lang.getPlural} es estatico y comun a todos los idiomas: anyade
     * "s" al sustantivo, venga en el idioma que venga. En castellano cuela por
     * suerte (hechizo &rarr; hechizos); en arabe salia <i>تعويذةs</i> en los
     * logros. Solo actua con el arabe puesto (mirar el Lang activo es gratis) y
     * solo sobre una "s" pegada a una letra arabe: un nombre de carta ingles con
     * su plural no se toca.
     */
    public static String tidy(final String text) {
        if (text == null || !(Lang.getInstance() instanceof ArabicLang)) {
            return text;
        }
        return ENGLISH_PLURAL.matcher(text).replaceAll("$1");
    }

    /** "Tu": el motor lo escribe con lblYou (en arabe, انت) o en ingles a pelo. */
    private static boolean isYou(final String name) {
        if (name == null) {
            return false;
        }
        final String n = name.trim();
        if (n.equalsIgnoreCase("you") || n.equals("أنت")) {
            return true;
        }
        try {
            return n.equals(Localizer.getInstance().getMessage("lblYou"));
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * La deja puesta para ese idioma si es el arabe. Hay que llamarlo ANTES de
     * {@code Lang.createInstance}; llamarlo de mas no hace dano.
     */
    @SuppressWarnings("unchecked")
    public static void installIfArabic(final String languageId) {
        if (!NeoLanguage.ARABIC.equals(languageId)) {
            return;
        }
        try {
            final Field f = Lang.class.getDeclaredField("languages");
            f.setAccessible(true);
            final Map<String, Lang> languages = (Map<String, Lang>) f.get(null);
            if (!(languages.get(languageId) instanceof ArabicLang)) {
                languages.put(languageId, new ArabicLang());
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[neo] arabe: sin Lang propio, el motor usara el ingles para los posesivos: " + e);
        }
    }
}
