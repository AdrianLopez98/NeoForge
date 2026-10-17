package forge.neo.match;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.card.CardView;
import forge.game.event.GameEvent;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventCardDamaged;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventPlayerLivesChanged;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.NeoText;

/**
 * <b>Las estadisticas de una partida</b>, para el boton del final (Discord,
 * 17-10-2026: <i>"a post game review of things like: Creature 1 did X damage
 * to Enemies, You lost 30 life to Enchantments, Opponent 3 did the most Player
 * damage, with 64. You countered 9 spells"</i>).
 *
 * <p>El motor no las lleva (solo cuenta hechizos lanzados), asi que se
 * escuchan sus eventos: se apunta al bus de la partida ({@link #attach}) y
 * suma danyo, vidas, hechizos, contrarrestados y criaturas muertas. No toca
 * nada de la partida: solo mira.
 *
 * <p><b>Contrarrestar</b> no tiene evento propio. Se deduce del orden en que
 * resuelve la pila ({@code MagicStack.resolveStack}): mientras algo resuelve,
 * cualquier OTRA carta que sale de la pila es que ese algo la ha quitado
 * (contrarrestar, exiliar un hechizo, devolverlo a la mano). La carta que
 * resuelve sale de la pila tambien, pero eso pasa en {@code finishResolving}
 * y es ella misma, asi que no cuenta.
 *
 * <p>Los eventos llegan en el hilo del motor y el resumen se pide desde la
 * interfaz: todo va sincronizado. Java puro: lo usa Android por el jar.
 */
public final class GameStats {

    /** Una por partida. Debil: la partida se va cuando se va, y sus cuentas con ella. */
    private static final Map<Game, GameStats> BY_GAME = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Empieza a contar en esta partida, si no se contaba ya. Se llama desde el
     * sitio por el que pasan todas las partidas (principio 8: {@code openView}).
     */
    public static void attach(final Game game) {
        if (game == null) {
            return;
        }
        synchronized (BY_GAME) {
            if (BY_GAME.containsKey(game)) {
                return;
            }
            final GameStats stats = new GameStats(game);
            BY_GAME.put(game, stats);
            game.subscribeToEvents(stats);
        }
    }

    /** Las de esa partida, o null si no se contaron (la del invitado en red, una repeticion). */
    public static GameStats of(final Game game) {
        return game == null ? null : BY_GAME.get(game);
    }

    private final WeakReference<Game> game;

    private final Map<Integer, String> playerNames = new HashMap<>();
    private final Map<Integer, Integer> damageToPlayersBy = new HashMap<>();
    private final Map<Integer, Integer> damageTaken = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> damageTakenByGroup = new HashMap<>();
    private final Map<Integer, String> cardNames = new HashMap<>();
    private final Map<Integer, String> cardOwners = new HashMap<>();
    private final Map<Integer, Integer> cardDamage = new HashMap<>();
    private final Map<Integer, Integer> spellsCast = new HashMap<>();
    private final Map<Integer, Integer> countered = new HashMap<>();
    private final Map<Integer, Integer> creaturesDied = new HashMap<>();
    private final Map<Integer, Integer> lifeGained = new HashMap<>();
    private int biggestHit;
    private String biggestHitSource;
    private String biggestHitTarget;

    private GameStats(final Game game) {
        this.game = new WeakReference<>(game);
    }

    @Subscribe
    public void receive(final GameEvent ev) {
        try {
            if (ev instanceof GameEventPlayerDamaged d) {
                onPlayerDamaged(d);
            } else if (ev instanceof GameEventCardDamaged d) {
                onCardDamaged(d);
            } else if (ev instanceof GameEventPlayerLivesChanged l) {
                onLife(l);
            } else if (ev instanceof GameEventSpellAbilityCast c) {
                onCast(c);
            } else if (ev instanceof GameEventCardChangeZone z) {
                onZone(z);
            } else if (ev instanceof forge.game.event.GameEventGameOutcome
                    && Boolean.getBoolean("neo.stats.print")) {
                // -Dneo.stats.print=true: el resumen por la consola al acabar,
                // en cualquier modo (para probarlo viendo a la IA).
                summary(null).forEach(l -> System.out.println("[stats] " + l));
            }
        } catch (final RuntimeException e) {
            // Es un resumen: nunca puede tumbar la partida.
        }
    }

    private synchronized void onPlayerDamaged(final GameEventPlayerDamaged d) {
        if (d.amount() <= 0 || d.target() == null) {
            return;
        }
        final int target = player(d.target());
        damageTaken.merge(target, d.amount(), Integer::sum);
        damageTakenByGroup.computeIfAbsent(target, k -> new HashMap<>())
                .merge(groupOf(d.source()), d.amount(), Integer::sum);
        final PlayerView by = d.source() == null ? null : d.source().getController();
        if (by != null) {
            damageToPlayersBy.merge(player(by), d.amount(), Integer::sum);
        }
        if (d.source() != null) {
            card(d.source());
            cardDamage.merge(d.source().getId(), d.amount(), Integer::sum);
        }
        if (d.amount() > biggestHit) {
            biggestHit = d.amount();
            biggestHitSource = d.source() == null ? "?" : cardName(d.source());
            biggestHitTarget = d.target().getName();
        }
    }

    private synchronized void onCardDamaged(final GameEventCardDamaged d) {
        if (d.amount() <= 0 || d.source() == null) {
            return;
        }
        card(d.source());
        cardDamage.merge(d.source().getId(), d.amount(), Integer::sum);
    }

    private synchronized void onLife(final GameEventPlayerLivesChanged l) {
        if (l.player() != null && l.newLives() > l.oldLives()) {
            lifeGained.merge(player(l.player()), l.newLives() - l.oldLives(), Integer::sum);
        }
    }

    private synchronized void onCast(final GameEventSpellAbilityCast c) {
        if (c.sa() == null || !c.sa().isSpell() || c.sa().getHostCard() == null) {
            return;
        }
        final PlayerView by = c.sa().getHostCard().getController();
        if (by != null) {
            spellsCast.merge(player(by), 1, Integer::sum);
        }
    }

    private synchronized void onZone(final GameEventCardChangeZone z) {
        if (z.card() == null || z.from() == null) {
            return;
        }
        final ZoneType from = z.from().zoneType();
        final ZoneType to = z.to() == null ? null : z.to().zoneType();
        // Una criatura que se muere.
        if (from == ZoneType.Battlefield && to == ZoneType.Graveyard
                && z.card().getCurrentState() != null && z.card().getCurrentState().isCreature()) {
            final PlayerView owner = z.card().getOwner();
            if (owner != null) {
                creaturesDied.merge(player(owner), 1, Integer::sum);
            }
        }
        // Algo que sale de la pila mientras OTRA cosa resuelve: quitado por ella.
        if (from == ZoneType.Stack && to != ZoneType.Battlefield && to != ZoneType.Stack) {
            final Game g = game.get();
            if (g == null || !g.getStack().isResolving()) {
                return;
            }
            final SpellAbility resolving = g.getStack().peekAbility();
            if (resolving == null || resolving.getHostCard() == null
                    || resolving.getHostCard().getId() == z.card().getId()
                    || resolving.getActivatingPlayer() == null) {
                return;
            }
            countered.merge(player(PlayerView.get(resolving.getActivatingPlayer())), 1, Integer::sum);
        }
    }

    private int player(final PlayerView p) {
        playerNames.putIfAbsent(p.getId(), p.getName());
        return p.getId();
    }

    private void card(final CardView c) {
        cardNames.putIfAbsent(c.getId(), cardName(c));
        if (c.getController() != null) {
            cardOwners.putIfAbsent(c.getId(), c.getController().getName());
        }
    }

    private static String cardName(final CardView c) {
        try {
            return forge.neo.card.CardText.nameOf(c);
        } catch (final RuntimeException e) {
            return c.getName();
        }
    }

    /** El grupo de la carta que hizo el danyo, con las claves del constructor. */
    private static String groupOf(final CardView source) {
        final CardView.CardStateView s = source == null ? null : source.getCurrentState();
        if (s == null) {
            return "group.other";
        }
        if (s.isCreature()) {
            return "group.creatures";
        }
        if (s.isPlaneswalker()) {
            return "group.planeswalkers";
        }
        if (s.isEnchantment()) {
            return "group.enchantments";
        }
        if (s.isArtifact()) {
            return "group.artifacts";
        }
        if (s.isLand()) {
            return "group.lands";
        }
        if (s.isInstant() || s.isSorcery()) {
            return "group.spells";
        }
        return "group.other";
    }

    // ------------------------------------------------------------------

    /**
     * Las frases del resumen, ya traducidas, de la mas general a la mas tuya.
     * Solo salen las que tienen algo que contar.
     *
     * @param you el jugador que mira (su "tu"), o null
     */
    public synchronized List<String> summary(final PlayerView you) {
        final List<String> out = new ArrayList<>();
        final Game g = game.get();
        if (g != null) {
            out.add(NeoText.get("stats.turns", g.getPhaseHandler().getTurn()));
        }
        final Integer me = you == null ? null : you.getId();

        final Map.Entry<Integer, Integer> topDealer = max(damageToPlayersBy);
        if (topDealer != null) {
            out.add(NeoText.get("stats.mostPlayerDamage", who(topDealer.getKey(), me), topDealer.getValue()));
        }
        final Map.Entry<Integer, Integer> topCard = max(cardDamage);
        if (topCard != null) {
            out.add(NeoText.get("stats.topCard", cardNames.get(topCard.getKey()),
                    cardOwners.getOrDefault(topCard.getKey(), "?"), topCard.getValue()));
        }
        if (biggestHit > 0) {
            out.add(NeoText.get("stats.biggestHit", biggestHitSource, biggestHit, biggestHitTarget));
        }
        if (me != null && damageTaken.getOrDefault(me, 0) > 0) {
            final Map.Entry<String, Integer> group = max(damageTakenByGroup.getOrDefault(me, Map.of()));
            out.add(NeoText.get("stats.youTook", damageTaken.get(me),
                    group == null ? "" : NeoText.get(group.getKey()).toLowerCase(java.util.Locale.ROOT),
                    group == null ? 0 : group.getValue()));
        }
        final Map.Entry<Integer, Integer> topCaster = max(spellsCast);
        if (topCaster != null) {
            out.add(NeoText.get("stats.mostSpells", who(topCaster.getKey(), me), topCaster.getValue()));
        }
        for (final Map.Entry<Integer, Integer> e : sorted(countered)) {
            out.add(NeoText.get("stats.countered", who(e.getKey(), me), e.getValue()));
        }
        final Map.Entry<Integer, Integer> topHealer = max(lifeGained);
        if (topHealer != null) {
            out.add(NeoText.get("stats.lifeGained", who(topHealer.getKey(), me), topHealer.getValue()));
        }
        final Map.Entry<Integer, Integer> mostLost = max(creaturesDied);
        if (mostLost != null) {
            out.add(NeoText.get("stats.creaturesDied", who(mostLost.getKey(), me), mostLost.getValue()));
        }
        return out;
    }

    private String who(final int id, final Integer me) {
        return me != null && me == id ? NeoText.get("stats.you") : playerNames.getOrDefault(id, "?");
    }

    private static <K> Map.Entry<K, Integer> max(final Map<K, Integer> m) {
        Map.Entry<K, Integer> best = null;
        for (final Map.Entry<K, Integer> e : m.entrySet()) {
            if (e.getValue() > 0 && (best == null || e.getValue() > best.getValue())) {
                best = e;
            }
        }
        return best;
    }

    private static List<Map.Entry<Integer, Integer>> sorted(final Map<Integer, Integer> m) {
        final List<Map.Entry<Integer, Integer>> l = new ArrayList<>(m.entrySet());
        l.removeIf(e -> e.getValue() <= 0);
        l.sort((a, b) -> b.getValue() - a.getValue());
        return l;
    }
}
