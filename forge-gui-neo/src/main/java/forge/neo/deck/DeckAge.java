package forge.neo.deck;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import forge.card.CardEdition;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * De que epoca es un mazo, y como se sortea un rival sin que salga siempre uno
 * de hace veinte anyos.
 *
 * <h2>El problema, medido</h2>
 *
 * <p>Reportado jugando el 03-09-2026: <i>"veo que pone bastantes cartas muy
 * antiguas... su legibilidad es limitada por la calidad de las imagenes"</i>.
 * No es una impresion: los mazos de fabrica que Forge trae para sortear rival
 * estan repartidos asi, contando que fraccion de su mazo principal se imprimio
 * en <b>2018 o despues</b>:
 *
 * <pre>
 * Commander (173 preconstruidos)   139 son >=80% modernos ·  33 son &lt;=20%
 * Estandar  (505 preconstruidos)   101 son >=80% modernos · 392 son &lt;=20%
 * </pre>
 *
 * <p>O sea que en Commander el rival ya sale moderno cuatro de cada cinco
 * veces, y en <b>Estandar sale antiguo casi ocho de cada diez</b>. Eso es lo
 * que se estaba viendo en la mesa: un mazo tematico del bloque Invasion, de
 * 2000, con escaneos de la epoca que a tamanyo de mesa no se leen.
 *
 * <p>Y el reparto es <b>bimodal</b>, que es lo que hace facil esto: casi no hay
 * mazos por el medio (7 de 505 caen entre el 20% y el 80%). Asi que un mazo o
 * es de una epoca o es de la otra, y cualquier umbral intermedio parte igual.
 *
 * <h2>Lo que NO se hace</h2>
 *
 * <p>No se quita ni una carta ni un mazo. Es lo que se pidio literalmente —
 * <i>"no quiero sacar las cartas antiguas... que sea todo azar, pero que haya
 * un poco mas de % de que salga alguna un pelin mas moderna"</i> — y ademas es
 * lo correcto: los preconstruidos viejos son mazos coherentes, hechos para
 * jugarse entre ellos, y sacarlos empobreceria el juego. Lo unico que cambia es
 * <b>en que orden salen de la bolsa</b>.
 *
 * <h2>Y por que no se toca el generador de mazos</h2>
 *
 * <p>Porque el rival de una partida normal <b>no se genera</b>: se sortea entre
 * los mazos que ya existen ({@code HomeScreen.resolvedOpponentDecks}). El
 * generador de Forge ({@code DeckgenUtil}) tira del pozo entero de cartas y no
 * admite ningun filtro por fecha, asi que sesgarlo obligaria a tocar
 * {@code forge-gui} — la regla de oro. No hace falta: el fallo no estaba ahi.
 */
public final class DeckAge {

    private DeckAge() {
    }

    /**
     * A partir de que anyo se considera "moderno".
     *
     * <p>2018 lo pidio Ana, y encaja con lo medido: es justo donde parte el
     * reparto bimodal de los preconstruidos.
     */
    public static final int MODERN_FROM = 2018;

    /**
     * Cuanta parte del mazo tiene que ser moderna para contarlo como moderno.
     *
     * <p>La mitad, y da igual el numero exacto: como el reparto es bimodal,
     * mover esto entre 0,2 y 0,8 reclasifica <b>siete</b> mazos de 505.
     */
    private static final double MODERN_DECK = 0.5;

    /** Cada cuantas veces sale un mazo moderno cuando hay de los dos. */
    private static final double SHARE = 0.70;

    /**
     * Cache por nombre de mazo.
     *
     * <p>Recorrer los 505 preconstruidos carta a carta cuesta poco, pero se
     * hace en cada arranque de partida y sobre la misma lista de siempre.
     */
    private static final Map<String, Boolean> CACHE = new HashMap<>();

    /** El anyo en el que se imprimio esa impresion, o 0 si no se sabe. */
    public static int yearOf(final PaperCard card) {
        if (card == null) {
            return 0;
        }
        try {
            final CardEdition ed = FModel.getMagicDb().getEditions().get(card.getEdition());
            final Date date = ed == null ? null : ed.getDate();
            if (date == null) {
                return 0;
            }
            // Ojo: Date.getYear() esta obsoleto Y devuelve el anyo menos 1900,
            // asi que comparado con 2018 da SIEMPRE falso. Costo una medicion
            // entera que salio con "0,0% de cartas modernas" en los 678 mazos.
            final Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal.get(Calendar.YEAR);
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /** Que fraccion del mazo principal es de {@link #MODERN_FROM} en adelante. */
    public static double modernFraction(final Deck deck) {
        if (deck == null || deck.getMain() == null) {
            return 0;
        }
        int modern = 0;
        int total = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final int n = e.getValue();
            total += n;
            if (yearOf(e.getKey()) >= MODERN_FROM) {
                modern += n;
            }
        }
        return total == 0 ? 0 : modern / (double) total;
    }

    /** Si ese mazo cuenta como moderno. */
    public static boolean isModern(final Deck deck) {
        if (deck == null) {
            return false;
        }
        final String key = deck.getName();
        final Boolean known = key == null ? null : CACHE.get(key);
        if (known != null) {
            return known;
        }
        final boolean modern = modernFraction(deck) >= MODERN_DECK;
        if (key != null) {
            CACHE.put(key, modern);
        }
        return modern;
    }

    /**
     * Baraja la bolsa de rivales dejando delante, la mayoria de las veces, uno
     * moderno.
     *
     * <p>Sigue siendo <b>todo azar</b>: los dos grupos se barajan enteros y lo
     * unico que se decide en cada hueco es de que bolsa se saca. Con
     * {@link #SHARE} a 0,70, siete de cada diez rivales seran modernos <i>en
     * media</i> — no siete de cada diez fijos, que seria una cuota y se notaria
     * como tal.
     *
     * <p>Cuando una de las dos bolsas se vacia se tira de la otra, asi que la
     * lista devuelta tiene <b>exactamente los mismos mazos</b> que la de
     * entrada: esto reordena, no filtra. Si todos los mazos fueran de la misma
     * epoca, es una baraja normal y no cambia nada.
     */
    public static List<Deck> shuffleFavouringModern(final List<Deck> decks, final Random rnd) {
        final List<Deck> modern = new ArrayList<>();
        final List<Deck> older = new ArrayList<>();
        if (decks != null) {
            for (final Deck d : decks) {
                (isModern(d) ? modern : older).add(d);
            }
        }
        // ⚠️ SUELO, no cuota. Si la bolsa ya es mas moderna que SHARE, esto se
        // aparta y baraja normal. Sin este control el "arreglo" EMPEORA los
        // sitios que ya estaban bien: medido sobre los 173 preconstruidos de
        // Commander, que son modernos el 80,8% de las veces, forzar el 70%
        // bajaba el rival moderno al 69,9%. Un ajuste que se llama "rivales
        // mas modernos" y hace que salgan menos es peor que no tenerlo.
        final int total = modern.size() + older.size();
        if (total == 0 || modern.size() / (double) total >= SHARE) {
            final List<Deck> plain = new ArrayList<>(modern);
            plain.addAll(older);
            java.util.Collections.shuffle(plain, rnd);
            return plain;
        }

        java.util.Collections.shuffle(modern, rnd);
        java.util.Collections.shuffle(older, rnd);

        final List<Deck> out = new ArrayList<>(modern.size() + older.size());
        int m = 0;
        int o = 0;
        while (m < modern.size() || o < older.size()) {
            final boolean wantModern = rnd.nextDouble() < SHARE;
            if (wantModern && m < modern.size()) {
                out.add(modern.get(m++));
            } else if (!wantModern && o < older.size()) {
                out.add(older.get(o++));
            } else if (m < modern.size()) {
                out.add(modern.get(m++));
            } else {
                out.add(older.get(o++));
            }
        }
        return out;
    }
}
