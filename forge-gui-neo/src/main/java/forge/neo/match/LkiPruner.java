package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import forge.card.CardStateName;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardState;
import forge.game.cost.NeoCostRelease;
import forge.game.mana.Mana;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;

/**
 * <b>Soltar las copias de cartas que el motor guarda y no suelta nunca.</b>
 *
 * <p>Discord (12-10-2026, Android): una mesa de cuatro se paro en el turno 45
 * por falta de memoria, y ya iba la segunda vez; el techo de un movil son
 * 512 MB y ninguna app lo puede subir. Medido en el PC (mesa de cuatro, IA
 * contra IA): las copias de cartas vivas iban 214 → 1.394 → 6.124 → 10.151 en
 * los turnos 15/30/45/60, y la memoria tras un GC de 359 a 777 MB. Cada vez que
 * se usa algo el motor guarda una "foto" de la mesa y de los cementerios —una
 * copia de cada carta— y nadie la suelta. Donde se quedan:
 *
 * <ul>
 *   <li>en la habilidad ({@code SpellAbility.lastStateBattlefield} y
 *       {@code lastStateGraveyard}): la del hechizo con el que se lanzo cada
 *       carta ({@code Card.castSA}); la de cada efecto de reemplazo, que
 *       {@code ReplacementHandler} le pone cada vez que se aplica (un "entra
 *       girada", el del comandante...), con sus {@code replacingObjects}, que
 *       guardan los parametros enteros del movimiento; las que la IA mira antes
 *       de jugar ({@code AiController}); y las que las tablas de "activada este
 *       turno / esta partida" tienen de clave;</li>
 *   <li>en el coste ya pagado: sacrificar, exiliar o descartar copia la mesa
 *       entera ({@code CostPartWithList.table}), ver {@link NeoCostRelease};</li>
 *   <li>en las cartas que cuelgan de una en juego sin estarlo ellas: la que pago
 *       mana a un hechizo (un Tesoro ya sacrificado: el hechizo la guarda para
 *       "si se gasto mana de..."), la que la exilio, la copia de la que salio un
 *       efecto, la que hizo dano de comandante;</li>
 *   <li>y en las propias copias, que es lo que hace que crezca cada vez mas
 *       deprisa: cada copia lleva las habilidades de su original con SU foto,
 *       que es otra tanda de copias, que llevan la suya... La foto vigente de la
 *       partida ({@code Game.lastStateBattlefield}) es la punta de esa cadena.</li>
 * </ul>
 *
 * <p>Forge tiene con que soltarlas — {@code SpellAbility.clearLastState()},
 * publico — y no lo llama nunca. Aqui se llama, desde fuera y sin tocar el
 * motor (regla de oro), una vez por turno, recorriendo tambien las copias que
 * hay dentro de cada foto antes de cortarla: asi, si algo que no conocemos
 * retiene una foto, retiene esa y no la cadena entera de detras. A cada foto
 * se le deja un valor que el propio motor ya maneja:
 *
 * <ul>
 *   <li>{@code castSA} → vacia, como el de toda copia ({@code CardCopyService}).
 *       Los once hechizos que la leen ({@code ...WithFallback}: Reap, Volcanic
 *       Wind...) lo hacen mientras se lanzan o se resuelven, y con ella vacia
 *       miran la mesa. Menos el de lo que ha entrado este turno, por si acaso;</li>
 *   <li>la de un reemplazo → las cartas de AHORA (las vivas, como cuando aun no
 *       se ha aplicado nunca), nunca null: hay quien la lee sin mirar
 *       ({@code AbilityUtils}, {@code SpellAbilityCondition}, {@code CloneEffect}),
 *       y el motor se la vuelve a poner antes de cada uso;</li>
 *   <li>todo lo demas → null, que es como nace una habilidad y como estan
 *       siempre las del humano.</li>
 * </ul>
 *
 * <p><b>Solo con el motor parado</b>, y que no pueda arrancar a mitad: esperando
 * a que el jugador DE ESTA INTERFAZ pase prioridad, con la pila vacia
 * ({@link #quiet}), y llamado desde el hilo por el que pasan todas sus
 * respuestas — el de JavaFX en el escritorio ({@code NeoMatchUI.updateButtons}),
 * el de los gestos en Android ({@code NeoThreads.onInput}). Un jugador remoto no
 * cuenta: su respuesta llega por el hilo del servidor.
 *
 * <p>Resultado, en la misma mesa de cuatro: 838 → 1.890 → 3.179 copias en los
 * turnos 30/45/60 (un 70 % menos) y 399 → 461 → 545 MB, y ya no crecen cada vez
 * mas deprisa. Tarda 7-9 ms en el PC.
 * {@code -Dneo.lkiPrune.off=true} lo apaga; {@code -Dneo.mem.probe=true}
 * escribe en el registro, una vez por turno, la memoria tras un GC y lo que se
 * ha soltado. Sin JavaFX: lo comparten las dos interfaces.
 */
public final class LkiPruner {

    private static final boolean OFF = Boolean.getBoolean("neo.lkiPrune.off");
    private static final boolean PROBE = Boolean.getBoolean("neo.mem.probe");

    /** El ultimo turno podado de cada partida: una vez por turno basta. */
    private static final Map<Game, Integer> DONE = Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile int probedTurn = -1;

    /** Lo ya visto en esta poda (habilidades y cartas) y lo que hay por recorrer. */
    private final Set<SpellAbility> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Card> listed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Card> cards = new ArrayList<>();
    /**
     * La mesa y los cementerios de AHORA — las cartas vivas, no la foto de la
     * partida: es lo que mira el motor con un reemplazo que aun no se ha aplicado
     * nunca, y no retiene ninguna copia.
     */
    private final CardCollectionView nowBattlefield;
    private final CardCollectionView nowGraveyard;
    private int released;

    private LkiPruner(final Game game) {
        nowBattlefield = game.getCardsIn(ZoneType.Battlefield);
        nowGraveyard = game.getCardsIn(ZoneType.Graveyard);
    }

    /**
     * Poda si toca: una vez por turno, y solo con el motor esperando a que el
     * jugador de {@code gui} pase prioridad con la pila vacia. Llamar en cada
     * prompt, desde el hilo por el que pasan las respuestas de ese jugador.
     *
     * @return cuantas fotos se han soltado (0 si no tocaba)
     */
    public static int maybePrune(final Game game, final IGuiGame gui) {
        if (game == null) {
            return 0;
        }
        try {
            if (PROBE) {
                probe(game);
            }
            if (OFF || game.isGameOver()) {
                return 0;
            }
            final int turn = game.getPhaseHandler().getTurn();
            final Integer done = DONE.get(game);
            if ((done != null && done >= turn) || !quiet(game, gui)) {
                return 0;
            }
            DONE.put(game, turn);
            final long t0 = System.nanoTime();
            final LkiPruner pruner = new LkiPruner(game);
            final int n = pruner.prune(game, turn);
            if (PROBE) {
                System.out.printf(Locale.ROOT, "[memoria] soltadas %d fotos, %d cartas fuera de juego recorridas, %d ms%n",
                        n, pruner.cards.size() - game.getCardsInGame().size(), (System.nanoTime() - t0) / 1_000_000L);
            }
            return n;
        } catch (final RuntimeException e) {
            // Leer la partida desde fuera puede tropezar: se prueba el turno siguiente.
            return 0;
        }
    }

    /** El motor parado en un {@code InputPassPriority} del jugador de esta interfaz, con la pila vacia. */
    private static boolean quiet(final Game game, final IGuiGame gui) {
        if (!game.getStack().isEmpty() || game.getStack().hasSimultaneousStackEntries()) {
            return false;
        }
        final forge.game.player.Player p = game.getPhaseHandler().getPriorityPlayer();
        return p != null && p.getController() instanceof PlayerControllerHuman human
                && human.getGui() == gui
                && human.getInputQueue().getInput() instanceof InputPassPriority;
    }

    /** Una vez por turno: la memoria tras un GC. Solo con -Dneo.mem.probe. */
    private static void probe(final Game game) {
        final int turn = game.getPhaseHandler().getTurn();
        if (turn == probedTurn) {
            return;
        }
        probedTurn = turn;
        final Runtime rt = Runtime.getRuntime();
        System.gc();
        System.out.printf(Locale.ROOT, "[memoria] turno %d: %d MB tras un GC%n",
                turn, (rt.totalMemory() - rt.freeMemory()) >> 20);
    }

    private int prune(final Game game, final int turn) {
        // Lo que NO se toca: el hechizo con el que ha entrado este turno un permanente.
        for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getCastSA() != null && c.getTurnInZone() >= turn) {
                seen.add(c.getCastSA());
            }
        }
        for (final Card c : game.getCardsInGame()) {
            add(c);
        }
        // La foto vigente de la partida se queda (hace falta), pero sus copias
        // llevan las habilidades de su original con la foto que tenian: es la
        // punta de la cadena. En el turno 58 colgaban de aqui la mitad de las
        // copias que quedaban.
        addAll(game.getLastStateBattlefield());
        addAll(game.getLastStateGraveyard());
        // El dano de comandante va por la carta que lo hizo, y la clave se queda
        // con el objeto de entonces aunque el comandante haya cambiado de zona.
        for (final forge.game.player.Player p : game.getPlayers()) {
            for (final Map.Entry<Card, Integer> e : p.getCommanderDamage()) {
                add(e.getKey());
            }
        }
        // La lista crece mientras se recorre: cada foto que se suelta apunta
        // antes sus copias, y se recorren tambien.
        for (int i = 0; i < cards.size(); i++) {
            visit(cards.get(i));
        }
        return released;
    }

    /** Una carta: las que cuelgan de ella, el hechizo con el que se lanzo y todas sus habilidades. */
    private void visit(final Card c) {
        final SpellAbility source = c.getEffectSourceAbility();
        if (source != null && source.getCardState() != null) {
            add(source.getCardState().getCard());
        }
        add(c.getEffectSource());
        add(c.getExiledWith());
        final SpellAbility cast = c.getCastSA();
        if (cast != null && seen.add(cast)) {
            if (hasSnapshot(cast)) {
                collect(cast);
                cast.setLastStateBattlefield(CardCollection.EMPTY);
                cast.setLastStateGraveyard(CardCollection.EMPTY);
                released++;
            }
            released += NeoCostRelease.release(cast, this::addAll);
            for (final Mana m : cast.getPayingMana()) {
                add(m.getSourceCard());
            }
        }
        final List<SpellAbility> all = new ArrayList<>(c.getAllSpellAbilities());
        all.add(c.getEffectSourceAbility());
        all.add(c.getExiledSA());
        all.add(c.getTokenSpawningAbility());
        if (cast != null) {
            all.addAll(cast.getPayingManaAbilities());
        }
        // Los de ahora (con lo ganado) y los de cada cara: una carta transformada
        // conserva los de la otra cara con la foto de cuando eran suyos.
        final List<Trigger> triggers = new ArrayList<>(c.getTriggers());
        final List<ReplacementEffect> replacements = new ArrayList<>(c.getReplacementEffects());
        for (final CardStateName name : c.getStates()) {
            final CardState st = c.getState(name);
            if (st != null) {
                triggers.addAll(st.getTriggers());
                replacements.addAll(st.getReplacementEffects());
            }
        }
        for (final Trigger t : triggers) {
            all.add(t.getOverridingAbility());
        }
        for (final ReplacementEffect re : replacements) {
            all.add(re.getOverridingAbility());
        }
        all.addAll(c.getAbilityActivatedThisTurn().rowKeySet());
        all.addAll(c.getAbilityActivatedThisGame().rowKeySet());
        all.addAll(c.getAbilityResolvedThisTurn().rowKeySet());
        for (final SpellAbility sa : all) {
            release(sa);
        }
    }

    /** Una habilidad y las que cuelgan de ella. */
    private void release(final SpellAbility sa) {
        if (sa == null || !seen.add(sa)) {
            return;
        }
        if (sa.getParent() == null && sa.isReplacementAbility()) {
            // La de un reemplazo: las cartas de ahora (nunca null), y sin los
            // parametros del ultimo movimiento, en ella y en sus subhabilidades.
            if (hasSnapshot(sa)) {
                collect(sa);
                sa.setLastStateBattlefield(nowBattlefield);
                sa.setLastStateGraveyard(nowGraveyard);
                released++;
            }
            for (SpellAbility sub = sa; sub != null; sub = sub.getSubAbility()) {
                collectObjects(sub.getReplacingObjects().values(), 2);
                sub.getReplacingObjects().clear();
            }
        } else if (hasSnapshot(sa)) {
            collect(sa);
            sa.clearLastState();
            released++;
        }
        released += NeoCostRelease.release(sa, this::addAll);
        release(sa.getSubAbility());
        for (final List<AbilitySub> list : sa.getAdditionalAbilityLists().values()) {
            for (final AbilitySub sub : list) {
                release(sub);
            }
        }
    }

    /** Apunta las copias de la foto de una habilidad, para recorrerlas antes de soltarla. */
    private void collect(final SpellAbility sa) {
        addAll(sa.getLastStateBattlefield());
        addAll(sa.getLastStateGraveyard());
    }

    /** Lo mismo con los parametros de un reemplazo: cartas, listas y el mapa del movimiento. */
    private void collectObjects(final Iterable<?> values, final int depth) {
        for (final Object v : values) {
            if (v instanceof Card card) {
                add(card);
            } else if (v instanceof CardCollectionView some) {
                addAll(some);
            } else if (v instanceof Map<?, ?> m && depth > 0) {
                collectObjects(m.values(), depth - 1);
            }
        }
    }

    private void add(final Card c) {
        if (c != null && listed.add(c)) {
            cards.add(c);
        }
    }

    private void addAll(final CardCollectionView some) {
        if (some != null) {
            for (final Card c : some) {
                add(c);
            }
        }
    }

    /** Si guarda alguna foto CON cartas (una vacia no pesa: se deja). */
    private static boolean hasSnapshot(final SpellAbility sa) {
        return notEmpty(sa.getLastStateBattlefield()) || notEmpty(sa.getLastStateGraveyard());
    }

    private static boolean notEmpty(final CardCollectionView cards) {
        return cards != null && !cards.isEmpty();
    }
}
