package forge.neo.card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordCollectionView;
import forge.game.keyword.KeywordView;

/**
 * Las palabras clave que una carta tiene <b>ahora</b>.
 *
 * <p>Esto vivia dentro de {@code forge.neo.ui.CardZoom}, que es JavaFX, y por
 * eso Android no podia usarlo: la logica es Java puro — no pinta nada — pero
 * estaba encerrada en una clase que arrastra media interfaz de escritorio.
 * Sacarla aqui no cambia nada en el PC y le da a Android <b>exactamente las
 * mismas palabras clave</b>, que es lo que pide la regla 2.2: el codigo
 * compartido se referencia, nunca se copia.
 *
 * <p>Salen del motor, no de leer el texto de la carta:
 * {@code CardStateView.getKeywords()} devuelve las de verdad — las impresas y
 * tambien las que le hayan <b>dado</b> un aura, un equipo o un efecto — y cada
 * una viene ya con su titulo montado ({@code "Ward {2}"},
 * {@code "Annihilator 2"}) y su explicacion con el numero o el coste puestos.
 * O sea que no hay nada que interpretar, y una criatura a la que le acaban de
 * regalar el volar lo explica igual que una que lo trae impreso.
 */
public final class CardKeywords {

    private CardKeywords() {
    }

    /**
     * Las palabras clave de una carta, sin repetir y con explicacion.
     *
     * <p>Se quitan las repetidas por titulo: una criatura puede llevar dos
     * instancias de la misma palabra clave y explicarla dos veces no anyade
     * nada.
     */
    public static List<KeywordView> of(final CardView card) {
        final List<KeywordView> out = new ArrayList<>();
        if (card == null) {
            return out;
        }
        try {
            final CardStateView st = card.getCurrentState();
            if (st == null) {
                return out;
            }
            final Set<String> seen = new HashSet<>();
            for (final KeywordView k : source(st)) {
                if (k == null) {
                    continue;
                }
                final String title = k.title();
                final String text = k.reminderText();
                // Sin explicacion no entra: la pastilla prometeria algo que al
                // pulsarla no esta. Pasa con las palabras clave que el motor
                // usa por dentro y no tienen texto de reglas.
                if (title == null || title.isBlank() || text == null || text.isBlank()) {
                    continue;
                }
                if (seen.add(title)) {
                    out.add(k);
                }
            }
        } catch (final RuntimeException e) {
            return out;
        }
        return out;
    }

    /**
     * De donde salen: del motor, y si no, de la carta en papel.
     *
     * <p>Dentro de una partida el motor las publica y son las de VERDAD. Fuera
     * de la partida no publica ninguna: un {@code CardView} de catalogo lo
     * monta {@code CardView.getCardForUi}, que crea la carta <b>sin juego
     * detras</b> y por tanto sin nada calculado (se ve facil: ahi
     * {@code getAbilityText()} tambien sale vacio). Y fuera de la partida es
     * justo donde mas falta hace, que es montando el mazo.
     *
     * <p>El respaldo saca los renglones {@code K:} de la carta en papel y les
     * pide al motor su instancia ({@code Keyword.getInstance}), que es lo mismo
     * que hace el propio Forge: asi el titulo sale montado igual y la
     * explicacion con su numero puesto.
     */
    private static Iterable<KeywordView> source(final CardStateView st) {
        final KeywordCollectionView live = st.getKeywords();
        if (live != null && !live.isEmpty()) {
            return live;
        }
        final List<KeywordView> out = new ArrayList<>();
        final String key = st.getImageKey();
        if (key == null || key.isEmpty()) {
            return out;
        }
        try {
            final forge.item.PaperCard pc = forge.util.ImageUtil.getPaperCardFromImageKey(key);
            if (pc == null || pc.getRules() == null) {
                return out;
            }
            // La cara que se esta mirando, no siempre la principal: una carta
            // de dos caras ampliada por la de atras tiene sus propias palabras.
            forge.card.ICardFace face = pc.getRules().getMainPart();
            final forge.card.ICardFace other = pc.getRules().getOtherPart();
            if (other != null && other.getName() != null
                    && other.getName().equals(st.getName())) {
                face = other;
            }
            if (face == null || face.getKeywords() == null) {
                return out;
            }
            for (final String raw : face.getKeywords()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                try {
                    out.add(Keyword.getInstance(raw).getView());
                } catch (final RuntimeException ignored) {
                    // Un renglon K: que no es una palabra clave al uso. Se
                    // salta: perder una no vale quedarse sin las demas.
                }
            }
        } catch (final RuntimeException ignored) {
            return out;
        }
        return out;
    }

    /**
     * LAS QUE CAMBIAN EL COMBATE, y solo esas.
     *
     * <p>Una criatura puede llevar quince palabras clave y en un cromo de la
     * mesa no caben ni tres. Pero <b>no todas valen lo mismo mientras juegas</b>:
     * lo que decide si bloqueas o no es si vuela, si mata al tocar, si arrolla o
     * si pega primero. Lo demas se lee ampliando la carta.
     *
     * <p>La lista es corta y esta escrita a mano <b>a proposito</b>: es una
     * decision de interfaz — que cabe en un cromo de 60 dp — y no una regla de
     * Magic. El motor no publica "cuales son importantes" porque esa pregunta no
     * es suya.
     *
     * @return los titulos, en el orden en que se han de pintar
     */
    public static List<String> combat(final CardView card) {
        final List<String> out = new ArrayList<>();
        for (final KeywordView k : of(card)) {
            final String t = k.title();
            if (t == null) {
                continue;
            }
            for (final String importante : COMBAT) {
                // Por prefijo: el titulo puede traer numero o coste detras
                // ("Protection from red", "Ward {2}", "Annihilator 2").
                if (t.equalsIgnoreCase(importante) || t.regionMatches(true, 0, importante, 0, importante.length())) {
                    if (!out.contains(importante)) {
                        out.add(importante);
                    }
                    break;
                }
            }
        }
        // En el orden de COMBAT y no en el que vinieran: asi la misma criatura
        // ensenya siempre sus simbolos en el mismo sitio.
        out.sort((a, b) -> Integer.compare(COMBAT.indexOf(a), COMBAT.indexOf(b)));
        return out;
    }

    /**
     * El orden es el de lo que mas cambia una decision de combate.
     *
     * <p>Volar y alcance primero porque deciden <b>si se puede bloquear</b>;
     * luego lo que decide <b>quien muere</b>; y al final lo que solo cambia
     * cuanto duele.
     */
    private static final List<String> COMBAT = Arrays.asList(
            "Flying", "Reach", "Menace", "Shadow", "Horsemanship", "Fear", "Intimidate", "Skulk",
            "Deathtouch", "First Strike", "Double Strike", "Indestructible", "Protection",
            "Trample", "Lifelink", "Vigilance", "Defender", "Hexproof", "Ward");
}
