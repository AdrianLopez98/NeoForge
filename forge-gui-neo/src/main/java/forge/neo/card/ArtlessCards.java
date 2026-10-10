package forge.neo.card;

import java.util.ArrayList;
import java.util.List;

import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.NeoSettings;

/**
 * <b>Las cartas sin arte, fuera del juego si el jugador lo pide.</b>
 *
 * <p>Pedido en Discord el 10-10-2026, con capturas de la Enciclopedia: <i>"there
 * are playable cards that don't have artwork yet ... I don't intend to play with
 * them, and seeing them in the game is a little off-putting"</i>. Son las cartas
 * que Forge no asigna a ninguna expansion: el motor las mete en la edicion
 * desconocida ({@link CardEdition#UNKNOWN_CODE}, "???") y no hay imagen que
 * pedir. El 10-10-2026 eran 19: 14 Alchemy {@code A-...} de
 * {@code cardsfolder/rebalanced} y 5 viejas (Goblin Bruiser, Ogre Painbringer...).
 * Las otras ~210 A- si estan en la seccion {@code [rebalanced]} de su expansion
 * y tienen foto (ver {@code RebalancedArt}): esas se quedan.
 *
 * <p><b>No se filtra nada pantalla a pantalla.</b> El motor ya tiene el
 * interruptor: {@code FPref.UI_LOAD_UNKNOWN_CARDS}. Apagado, las cartas sin
 * expansion no llegan a la base de cartas ({@code CardDb.addUnassignedCardPrints}),
 * asi que desaparecen de todos los modos a la vez — constructor, Enciclopedia,
 * comandantes, sobres, mazos de la IA, Ascenso, Quest y la Aventura (que lo recibe
 * en sus preferencias, ver {@code AdventureSettings}). Y si un dia Forge les da
 * expansion, y con ella imagen, vuelven solas aunque el ajuste siga puesto.
 *
 * <p>Tres cosas que no se ven:
 * <ul>
 *   <li><b>Se aplica al abrir el juego</b>, en el mismo gancho que el idioma
 *       ({@code NeoLanguage.hook}): la base de cartas se monta una vez.</li>
 *   <li><b>Con el ajuste apagado no se toca la preferencia de Forge</b>: quien la
 *       tuviera apagada en su Forge de siempre sigue igual que antes.</li>
 *   <li><b>Un mazo TUYO que lleve una la conserva</b>: al leerlo, el motor la
 *       carga a peticion ({@code StaticData.attemptToLoadCard}, que no mira el
 *       interruptor), y desde ahi esa carta existe en esa sesion. Es lo que se
 *       quiere: el ajuste quita lo que se ofrece, no cambia tus mazos sin
 *       preguntar. En una Quest, en cambio, el motor la quita de la coleccion al
 *       cargarla (QuestDataIO: "It will be removed from the quest save").</li>
 * </ul>
 *
 * <p>Pura y compartida con Android por el jar.
 */
public final class ArtlessCards {

    /** El ajuste. */
    public static final String KEY = "cards.hideArtless";

    /** {@code -Dneo.cards.hideArtless=true}: encenderlo sin escribir el ajuste (lo usa el comprobador). */
    public static final String FORCE_PROPERTY = "neo.cards.hideArtless";

    private ArtlessCards() {
    }

    /** Si se ocultan (lo que se pidio para ESTA sesion o la siguiente). */
    public static boolean hidden() {
        return Boolean.getBoolean(FORCE_PROPERTY) || NeoSettings.getBool(KEY, false);
    }

    public static void setHidden(final boolean on) {
        NeoSettings.setBool(KEY, on);
        NeoSettings.save();
    }

    /** Lo que habia en el motor antes de tocarlo, para reponerlo despues. */
    private static volatile String engineValueBefore;

    /** Si en esta sesion se arranco con ellas fuera. */
    private static volatile boolean hiddenThisSession;

    /**
     * En el gancho de {@code FModel.initialize}, antes de que se lea una carta.
     * Solo hace algo con el ajuste puesto.
     */
    public static void applyTo(final ForgePreferences prefs) {
        hiddenThisSession = hidden();
        if (!hiddenThisSession) {
            engineValueBefore = null;
            return;
        }
        engineValueBefore = prefs.getPref(FPref.UI_LOAD_UNKNOWN_CARDS);
        prefs.setPref(FPref.UI_LOAD_UNKNOWN_CARDS, false);
        System.out.println("        cartas sin arte: fuera (" + KEY + ")");
    }

    /**
     * Repone el valor del motor en cuanto la base de cartas esta montada: ya no
     * sirve de nada, y un {@code save()} del motor lo escribiria en el fichero
     * que comparte con el Forge de siempre. Lo mismo que hace el idioma.
     */
    public static void restoreEngineValue() {
        final String before = engineValueBefore;
        if (before == null) {
            return;
        }
        try {
            FModel.getPreferences().setPref(FPref.UI_LOAD_UNKNOWN_CARDS, before);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido reponer UI_LOAD_UNKNOWN_CARDS: " + e);
        }
        engineValueBefore = null;
    }

    /** Si las de esta sesion estan fuera (se arranco con el ajuste puesto). */
    public static boolean hiddenThisSession() {
        return hiddenThisSession;
    }

    /**
     * Las cartas sin expansion que hay ahora en el juego: las que el ajuste
     * quitaria. Sin las nuestras que tambien van a "???" pero no salen de un
     * fichero de carta (las reliquias de Ascenso).
     */
    public static List<PaperCard> present() {
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards()) {
            if (CardEdition.UNKNOWN_CODE.equals(p.getEdition())
                    && p.getRules() != null && p.getRules().getPath() != null) {
                out.add(p);
            }
        }
        return out;
    }
}
