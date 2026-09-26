package forge.neo.match;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.NeoPaymentPeek;
import forge.gui.interfaces.IGuiGame;
import forge.util.Localizer;

/**
 * "X manas en cualquier combinacion de colores" en UN dialogo, y ya repartido.
 *
 * <p><b>De donde sale esto.</b> Reportado en itch.io: con <i>Selvala, Heart of
 * the Wilds</i> y una criatura grande, habia que elegir un color y dar OK
 * <b>por cada punto</b> de mana. Con fuerza 9, nueve dialogos seguidos.
 *
 * <p><b>Por que pasaba.</b> {@code ManaEffect} tiene dos caminos para el mana
 * combinado: un reparto de una vez ({@code specifyManaCombo}) y un bucle que
 * llama a {@code chooseColor} una vez por mana. Usa el bucle en cuanto la
 * habilidad trae <i>express choice</i>, y lo trae casi siempre:
 * {@code InputPayMana} lo pone al pagar, y despues de resolverse
 * {@code ManaEffect} deja apuntada la combinacion y nadie la borra. En el Forge
 * de siempre molestaba menos porque los puntos con color "sugerido" salian
 * solos; aqui {@link ManaColor} los ensancha todos (para que una dual pregunte),
 * y entonces preguntaba cada uno.
 *
 * <p><b>Como se arregla sin tocar el motor.</b> A la primera pregunta del bucle
 * se abre el reparto entero, y las siguientes se contestan de una cola sin
 * ensenyar nada. Al motor le da igual: sigue recibiendo un color por llamada.
 *
 * <p><b>La cola sabe a que resolucion pertenece</b> por la <i>identidad</i> del
 * express choice: durante el bucle es el mismo objeto {@code String}, y al
 * acabar {@code ManaEffect} pone uno nuevo. Si algo deja la cola a medias, la
 * siguiente activacion no se la come.
 *
 * <p><b>El prerrelleno</b> es lo que lo hace comodo: pagando un coste, sale ya
 * repartido para pagarlo (lo que no hace falta va al color que mas pide); sin
 * coste delante, como la ultima vez con esa carta. Casi siempre es abrir y dar
 * Aceptar.
 */
final class ManaCombo {

    /** Los colores en el orden de siempre: el del dialogo y el de los desempates. */
    private static final MagicColor.Color[] ORDER = {
            MagicColor.Color.WHITE, MagicColor.Color.BLUE, MagicColor.Color.BLACK,
            MagicColor.Color.RED, MagicColor.Color.GREEN};

    /** Lo que queda por devolver de la resolucion en curso. */
    private final Deque<Byte> queue = new ArrayDeque<>();
    private SpellAbility queueSa;
    private String queueKey;

    /** El ultimo reparto con cada carta (por id), para ofrecerlo otra vez. */
    private final Map<Integer, Map<MagicColor.Color, Integer>> last = new HashMap<>();

    /**
     * Si esta pregunta es un punto de un reparto en curso, su respuesta.
     *
     * @return el color, o {@code null} si no hay nada en cola para ella
     */
    Byte next(final SpellAbility sa, final ColorSet options) {
        if (queue.isEmpty()) {
            return null;
        }
        if (sa != queueSa || keyOf(sa) != queueKey) {
            // De otra resolucion: no se usa, y no se deja por ahi.
            queue.clear();
            return null;
        }
        final byte c = queue.poll();
        if (options != null && !options.hasAnyColor(c)) {
            // No deberia pasar; si pasa, que pregunte como antes.
            queue.clear();
            return null;
        }
        return c;
    }

    /**
     * Si es la primera pregunta de un reparto de varios manas, lo pregunta
     * entero y devuelve el primer color.
     *
     * @return el primer color, o {@code null} si esta pregunta no es de esas
     */
    Byte start(final IGuiGame gui, final Input input, final String message,
               final SpellAbility sa, final ColorSet options) {
        if (!(gui instanceof NeoMatchUI ui) || options == null || options.countColors() < 2) {
            return null;
        }
        return start(gui, input, sa, options, batchable(sa, message));
    }

    /** Lo mismo con el total ya calculado. El comprobador entra por aqui. */
    Byte start(final IGuiGame gui, final Input input, final SpellAbility sa,
               final ColorSet options, final int amount) {
        if (!(gui instanceof NeoMatchUI ui) || options == null || options.countColors() < 2
                || amount < 2) {
            return null;
        }
        final Map<MagicColor.Color, Integer> split = ask(ui, input, sa, options, amount);
        if (split == null) {
            return null;
        }
        queue.clear();
        for (final MagicColor.Color c : ORDER) {
            for (int i = split.getOrDefault(c, 0); i > 0; i--) {
                queue.add(c.getColorMask());
            }
        }
        if (queue.size() != amount) {
            queue.clear();
            return null;
        }
        queueSa = sa;
        queueKey = keyOf(sa);
        return queue.poll();
    }

    /**
     * El otro camino del motor, el de {@code specifyManaCombo}: tambien con el
     * prerrelleno. Devuelve {@code null} si no toca (y entonces pregunta Forge).
     */
    Map<Byte, Integer> specify(final IGuiGame gui, final Input input, final SpellAbility sa,
                               final ColorSet options, final int amount, final boolean different) {
        if (different || !(gui instanceof NeoMatchUI ui) || options == null
                || options.countColors() < 2 || amount < 2) {
            return null;
        }
        final Map<MagicColor.Color, Integer> split = ask(ui, input, sa, options, amount);
        if (split == null) {
            return null;
        }
        final Map<Byte, Integer> out = new HashMap<>();
        for (final Map.Entry<MagicColor.Color, Integer> e : split.entrySet()) {
            if (e.getValue() > 0) {
                out.put(e.getKey().getColorMask(), e.getValue());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------

    private Map<MagicColor.Color, Integer> ask(final NeoMatchUI ui, final Input input,
                                               final SpellAbility sa, final ColorSet options,
                                               final int amount) {
        final Card host = sa.getHostCard();
        final ManaCostBeingPaid cost = NeoPaymentPeek.costOf(input);
        final SpellAbility paying = NeoPaymentPeek.paidFor(input);
        final Map<MagicColor.Color, Integer> previous = host == null ? null : last.get(host.getId());
        final Map<MagicColor.Color, Integer> suggested = suggest(amount, options, cost, previous,
                host == null ? null : host.getColor());

        final String costText = cost == null ? null : cost.toString();
        final CardView payingCard = paying == null || paying.getHostCard() == null
                ? null : CardView.get(paying.getHostCard());
        final Map<MagicColor.Color, Integer> split = ui.askManaCombo(
                host == null ? null : CardView.get(host), options, amount, suggested,
                costText, payingCard, cost == null && previous != null);
        if (split == null || total(split) != amount) {
            return null;
        }
        if (host != null) {
            last.put(host.getId(), new LinkedHashMap<>(split));
        }
        return split;
    }

    /**
     * Cuantos manas reparte esta pregunta, o 0 si no es un reparto que se
     * pueda juntar.
     *
     * <p>Fuera: los de colores <i>distintos</i> (el motor ya va quitando los
     * usados), los de {@code Each} (cada eleccion vale varios) y cualquier
     * {@code chooseColor} que no sea el del mana combinado.
     */
    static int batchable(final SpellAbility sa, final String message) {
        if (sa == null || sa.getHostCard() == null) {
            return 0;
        }
        final AbilityManaPart mp = sa.getManaPart();
        if (mp == null || !mp.isComboMana() || sa.hasParam("Each")) {
            return 0;
        }
        final String produced = mp.getOrigProduced();
        if (produced != null && produced.contains("Different")) {
            return 0;
        }
        if (message != null
                && !message.equals(Localizer.getInstance().getMessage("lblSelectManaProduce"))) {
            return 0;
        }
        return sa.hasParam("Amount")
                ? AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("Amount"), sa) : 1;
    }

    /** La identidad del express choice: cambia de objeto en cada resolucion. */
    private static String keyOf(final SpellAbility sa) {
        final AbilityManaPart mp = sa == null ? null : sa.getManaPart();
        return mp == null ? null : mp.getExpressChoice();
    }

    /**
     * El reparto que se ofrece hecho.
     *
     * <ol>
     *   <li>Pagando un coste: primero sus simbolos de un color, luego los
     *       hibridos (al color que ya mas se pide), y {2/W} con su color, que
     *       sale por uno en vez de por dos.</li>
     *   <li>Lo que sobra (genericos y lo que va a flotar) al color que mas pide
     *       el coste.</li>
     *   <li>Sin coste, o con uno sin colores: como la ultima vez con esa carta,
     *       y si no hay ultima vez, los colores de la propia carta.</li>
     * </ol>
     *
     * <p>No mira la reserva: el mana que ya flota lo usas tu clicandolo, y si
     * se contara aqui el pago se quedaria a medias esperando ese click.
     */
    static Map<MagicColor.Color, Integer> suggest(final int amount, final ColorSet options,
                                                  final ManaCostBeingPaid cost,
                                                  final Map<MagicColor.Color, Integer> previous,
                                                  final ColorSet hostColors) {
        final Map<MagicColor.Color, Integer> out = new LinkedHashMap<>();
        for (final MagicColor.Color c : ORDER) {
            if (options.hasAnyColor(c.getColorMask())) {
                out.put(c, 0);
            }
        }
        if (out.isEmpty() || amount <= 0) {
            return out;
        }
        int left = amount;

        if (cost != null) {
            final List<ManaCostShard> shards = cost.getUnpaidShards();
            // 1. Los de un color.
            for (final ManaCostShard s : shards) {
                if (left > 0 && s.isMonoColor() && !s.isSnow()) {
                    final MagicColor.Color c = colorOf(s, out, null);
                    if (c != null) {
                        out.merge(c, 1, Integer::sum);
                        left--;
                    }
                }
            }
            // 2. Los hibridos de dos colores: al que ya mas se pide.
            for (final ManaCostShard s : shards) {
                if (left > 0 && s.isMultiColor() && !s.isSnow()) {
                    final MagicColor.Color c = colorOf(s, out, out);
                    if (c != null) {
                        out.merge(c, 1, Integer::sum);
                        left--;
                    }
                }
            }
        }

        MagicColor.Color spare = dominant(out, hostColors);
        if (spare == null && previous != null) {
            // Como la ultima vez: cada color hasta lo que tuvo.
            for (final MagicColor.Color c : sortedDesc(previous)) {
                if (left <= 0) {
                    break;
                }
                if (out.containsKey(c)) {
                    final int n = Math.min(left, previous.get(c));
                    out.merge(c, n, Integer::sum);
                    left -= n;
                }
            }
            spare = dominant(out, hostColors);
        }
        if (spare == null && hostColors != null) {
            for (final MagicColor.Color c : ORDER) {
                if (out.containsKey(c) && hostColors.hasAnyColor(c.getColorMask())) {
                    spare = c;
                    break;
                }
            }
        }
        if (spare == null) {
            spare = out.keySet().iterator().next();
        }
        if (left > 0) {
            out.merge(spare, left, Integer::sum);
        }
        return out;
    }

    /**
     * El color con el que se paga este simbolo, de los que hay.
     *
     * @param prefer si no es null, entre varios se queda con el que mas lleva
     */
    private static MagicColor.Color colorOf(final ManaCostShard s,
                                            final Map<MagicColor.Color, Integer> available,
                                            final Map<MagicColor.Color, Integer> prefer) {
        MagicColor.Color best = null;
        for (final MagicColor.Color c : ORDER) {
            if (!available.containsKey(c) || !s.isColor(c.getColorMask())) {
                continue;
            }
            if (best == null || (prefer != null && prefer.get(c) > prefer.get(best))) {
                best = c;
            }
        }
        return best;
    }

    /**
     * El color que mas lleva. En un empate gana el de la propia carta (Selvala
     * pagando {G}{U} se queda el sobrante en verde), y si no, el orden WUBRG.
     */
    private static MagicColor.Color dominant(final Map<MagicColor.Color, Integer> counts,
                                             final ColorSet hostColors) {
        MagicColor.Color best = null;
        for (final Map.Entry<MagicColor.Color, Integer> e : counts.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            if (best == null || e.getValue() > counts.get(best)
                    || (e.getValue().equals(counts.get(best)) && hostColors != null
                        && hostColors.hasAnyColor(e.getKey().getColorMask())
                        && !hostColors.hasAnyColor(best.getColorMask()))) {
                best = e.getKey();
            }
        }
        return best;
    }

    private static List<MagicColor.Color> sortedDesc(final Map<MagicColor.Color, Integer> counts) {
        final List<MagicColor.Color> list = new java.util.ArrayList<>(counts.keySet());
        list.sort((a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        return list;
    }

    private static int total(final Map<MagicColor.Color, Integer> split) {
        int n = 0;
        for (final int v : split.values()) {
            n += v;
        }
        return n;
    }
}
