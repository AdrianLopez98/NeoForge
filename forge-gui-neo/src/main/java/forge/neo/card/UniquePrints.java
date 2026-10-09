package forge.neo.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * <b>Que la politica de arte de Ajustes se cumpla de verdad</b> en la impresion
 * "por defecto" de cada carta: la de {@code getUniqueCards()}, que es de donde
 * sacan las cartas el catalogo del constructor, la enciclopedia, Ascenso y los
 * premios. Reportado en Discord (09-10-2026): <i>"No matter what you select for
 * art preferences, the program seems to pick a random card, including ones that
 * are full art or in different languages."</i>
 *
 * <p>Eran dos cosas del motor, y las dos se envuelven desde aqui (regla de oro):
 *
 * <ol>
 *   <li><b>La politica llegaba tarde.</b> {@code CardDb} calcula la impresion
 *       unica de cada carta <b>una vez</b>, al leer las cartas, con la politica
 *       de las preferencias de Forge ({@code UI_PREFERRED_ART}, de fabrica "la
 *       mas reciente de todas"). La nuestra se pone despues con
 *       {@code setCardArtPreference}, que la guarda pero <b>no recalcula</b>:
 *       servia para lo que se pide por nombre (importar, la IA), y para el
 *       catalogo no hacia nada. El unico recalculo publico es
 *       {@code setPreferredLanguageAvailability} (lo que llaman las GUIs de Forge
 *       al cambiar el idioma de las cartas), con el mismo criterio de idioma que
 *       le dio {@code FModel} al arrancar.</li>
 *   <li><b>Dentro de la expansion, el numero se comparaba como TEXTO.</b>
 *       {@code getBestUniquePrint} desempata por {@code getCollectorNumber()},
 *       que es un {@code String}: "116" va antes que "51" y "301" antes que
 *       "45". Asi ganaba la japonesa del Mystical Archive (SOA 116 frente a la
 *       51 inglesa) o la borderless de los 300 frente a la normal. Aqui se
 *       cambia por la impresion 1 de esa misma expansion — la normal, y la
 *       misma que da {@code getCard(nombre)} — con {@code setPreferredArt}, el
 *       setter publico.</li>
 * </ol>
 *
 * <p>Lo que <b>no</b> toca: el arte que el jugador ha elegido a mano (Forge lo
 * guarda en {@code CardPreferences} y lo carga al arrancar); a esos se les
 * devuelve su eleccion despues del recalculo, que la pisa en el indice. Y las
 * que dejamos nosotros se apuntan en {@link #ours} para no confundirlas con
 * las suyas la proxima vez (ni con los favoritos de Ascenso, ver
 * {@code AscentArt}).
 *
 * <p>⚠️ El recalculo vacia el indice antes de rehacerlo. Al arrancar no hay
 * partida; si se cambia en Ajustes a mitad de una, el motor solo lee ese indice
 * en {@code CopyPermanentEffect}, y el hueco dura una fraccion de segundo.
 * Sin {@code isBlank} ni nada de Java 11: este jar lo usa Android (API 26).
 */
public final class UniquePrints {

    private UniquePrints() {
    }

    /** Los nombres cuya impresion hemos fijado nosotros, no el jugador. */
    private static final Set<String> ours = Collections.synchronizedSet(new HashSet<String>());

    /** Si el arte "preferido" de esa carta lo ha puesto esta clase y no el jugador. */
    public static boolean isOurs(final String name) {
        return name != null && ours.contains(name);
    }

    /**
     * Recalcula la impresion por defecto de cada carta con la politica que ya
     * tenga puesta el motor ({@code setCardArtPreference}).
     *
     * @return cuantas cartas se han cambiado a la impresion 1 de su expansion
     */
    public static int rebuild() {
        final StaticData data = FModel.getMagicDb();
        if (data == null) {
            return 0;
        }
        final BiPredicate<String, String> lang = languageAvailabilityLikeFModel();
        data.setPreferredLanguageAvailability(lang);
        return preferFirstPrinting(data.getCommonCards(), lang);
    }

    private static int preferFirstPrinting(final CardDb db, final BiPredicate<String, String> lang) {
        final List<PaperCard> uniques = new ArrayList<>(db.getUniqueCards());
        int changed = 0;
        for (final PaperCard u : uniques) {
            if (u == null || u.getName() == null) {
                continue;
            }
            final String name = u.getName();
            final boolean mine = ours.contains(name);
            if (!mine && db.hasPreferredArt(name)) {
                // Elegida a mano por el jugador: el recalculo la ha pisado en el
                // indice, pero sigue apuntada. Se le devuelve.
                final PaperCard chosen = db.getCard(name);
                if (chosen != null && !chosen.equals(u)) {
                    db.setPreferredArt(name, chosen.getEdition(), chosen.getArtIndex());
                }
                continue;
            }
            PaperCard want = u;
            if (u.getArtIndex() > 1) {
                final PaperCard first = db.getCard(name, u.getEdition(), 1);
                if (first != null && first.getArtIndex() == 1 && u.getEdition().equals(first.getEdition())
                        && first.getRarity() != CardRarity.Special
                        && (lang == null || !inLanguage(u, lang) || inLanguage(first, lang))) {
                    want = first;
                }
            }
            if (want != u) {
                if (db.setPreferredArt(name, want.getEdition(), want.getArtIndex())) {
                    ours.add(name);
                    changed++;
                }
            } else if (mine) {
                // La apuntamos en una vuelta anterior, con otra politica: el
                // apunte sigue mandando en getCard(nombre), asi que se pone al
                // dia con la de ahora.
                db.setPreferredArt(name, u.getEdition(), u.getArtIndex());
            }
        }
        return changed;
    }

    private static boolean inLanguage(final PaperCard pc, final BiPredicate<String, String> lang) {
        final CardEdition ed = FModel.getMagicDb().getEditions().get(pc.getEdition());
        return ed != null && lang.test(ed.getScryfallCode(), pc.getCollectorNumber());
    }

    /** Lo mismo que {@code FModel.buildPreferredLanguageAvailability}, que es privado. */
    private static BiPredicate<String, String> languageAvailabilityLikeFModel() {
        final forge.localinstance.properties.ForgePreferences prefs = FModel.getPreferences();
        if (!prefs.getPrefBoolean(FPref.UI_PREFER_LANG_FOR_UNIQUE_CARDS)) {
            return null;
        }
        final String lang = prefs.getPref(FPref.UI_CARD_DOWNLOAD_LANG);
        if (lang == null || lang.isEmpty() || "en".equalsIgnoreCase(lang)) {
            return null;
        }
        return (set, number) -> forge.gui.download.CdnUuidCache.isAvailableInLanguage(set, number, lang);
    }
}
