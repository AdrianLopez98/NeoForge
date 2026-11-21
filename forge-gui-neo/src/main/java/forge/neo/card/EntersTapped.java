package forge.neo.card;

import java.util.Map;

import forge.game.Game;
import forge.game.GameEntityCounterTable;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPayLife;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * <b>Si una tierra entraria girada si la jugaras AHORA</b> (Discord,
 * 20-11-2026: <i>"a different highlight for lands that would enter tapped if
 * cast at the moment, either because they always enter tapped (ie surveil
 * lands) or because the conditions aren't met"</i>). Sin JavaFX: lo usan el PC
 * y Android.
 *
 * <p>Es la misma cuenta que hace la IA de Forge para elegir que tierra jugar
 * ({@code AiController.landEntersTapped}, privado): los reemplazos de "entra
 * en el campo" de la carta que la girarian, con su condicion evaluada en el
 * estado actual. Ademas, los que se evitan pagando ({@code UnlessCost}):
 * <ul>
 * <li>revelar una carta (Temple of the Dragon Queen, las de Gigante o Goblin,
 *     las de dos tipos basicos): entra girada solo si no tienes en la mano
 *     nada que revelar;</li>
 * <li>pagar vida (las "shocklands"): eso lo decides tu al jugarla, asi que no
 *     se marca.</li>
 * </ul>
 * Si algo no se puede evaluar, devuelve false: sin marca, como antes.
 */
public final class EntersTapped {

    private EntersTapped() {
    }

    /**
     * Por id de carta: lo que dejo {@link #scan} en el hilo de la partida.
     *
     * <p><b>No pregunta al motor</b>, y es a proposito. La cuenta de
     * {@link #now} pasa por {@code ReplacementHandler.getReplacementList}, que
     * llama a {@code checkStaticAbilities}: reescribe las estaticas y congela
     * el Tracker. Hecha desde el hilo de interfaz (PC) o el reloj de Android
     * mientras la partida sigue, mataba la partida con una
     * {@code ConcurrentModificationException} en {@code Tracker.unfreeze} o en
     * {@code StaticEffects.clearStaticEffects} (Discord e itch.io, 21-11-2026,
     * la 1.0.61 recien salida; las trampas conocidas, el fallo de la 1.0.43).
     */
    public static boolean ofId(final int cardId) {
        final Scan s = last;
        return s != null && s.game == AffectedBy.current() && s.ids.contains(cardId);
    }

    private record Scan(Game game, java.util.Set<Integer> ids) {
    }

    private static volatile Scan last;

    /**
     * La cuenta, para las tierras de {@code you} fuera del campo. <b>Solo
     * desde el hilo de la partida</b>: lo llama {@code SafeActions.Guarded}
     * al empezar cada prioridad, junto a improvisar y convocar.
     */
    public static void scan(final Player you) {
        if (you == null || you.getGame() == null) {
            return;
        }
        final java.util.Set<Integer> ids = new java.util.HashSet<>();
        try {
            for (final ZoneType z : new ZoneType[] {ZoneType.Hand, ZoneType.Graveyard, ZoneType.Exile,
                    ZoneType.Command, ZoneType.Library}) {
                for (final Card c : you.getCardsIn(z)) {
                    // De la biblioteca, solo la de arriba (Courser of Kruphix y compania).
                    if (z == ZoneType.Library && c != you.getCardsIn(ZoneType.Library).get(0)) {
                        break;
                    }
                    if (c.isLand() && now(c)) {
                        ids.add(c.getId());
                    }
                }
            }
        } catch (final RuntimeException e) {
            // Sin marca, como antes.
        }
        last = new Scan(you.getGame(), java.util.Set.copyOf(ids));
    }

    /** Si {@code land}, jugada ahora desde donde esta, entraria girada. */
    public static boolean now(final Card land) {
        if (land == null || !land.isLand() || land.getZone() == null) {
            return false;
        }
        // Solo desde el hilo de la partida: lo que sigue pregunta al motor.
        if (!forge.neo.match.EngineThread.check("EntersTapped")) {
            return false;
        }
        final Game game = land.getGame();
        final Player you = land.getController();
        if (game == null || you == null) {
            return false;
        }
        final Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(land);
        params.put(AbilityKey.Origin, land.getZone().getZoneType());
        params.put(AbilityKey.Destination, ZoneType.Battlefield);
        final GameEntityCounterTable table = new GameEntityCounterTable();
        params.put(AbilityKey.EffectOnly, true);
        params.put(AbilityKey.CounterTable, table);
        params.put(AbilityKey.CounterMap, table.column(land));

        for (final ReplacementEffect re : game.getReplacementHandler()
                .getReplacementList(ReplacementType.Moved, params, ReplacementLayer.Other)) {
            // El "girar" puede ir solo o detras de otra cosa en la cadena: el
            // Temple of the Dragon Queen REVELA primero (opcional) y luego gira
            // segun lo revelado. Se recorre la cadena y se recuerda el revelar.
            SpellAbility reveal = null;
            for (SpellAbility sa = re.ensureAbility(); sa != null; sa = sa.getSubAbility()) {
                if (sa.getActivatingPlayer() == null) {
                    sa.setActivatingPlayer(sa.getHostCard().getController());
                }
                if (ApiType.Reveal.equals(sa.getApi()) && sa.hasParam("RevealValid")) {
                    reveal = sa;
                    continue;
                }
                if (!ApiType.Tap.equals(sa.getApi()) || !sa.metConditions()) {
                    continue;
                }
                if (sa.hasParam("UnlessCost") && avoidable(sa, you)) {
                    continue;
                }
                if (reveal != null && canReveal(reveal, you, land)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /** Si tienes en la mano algo que revelar para que no entre girada (sin contar la propia tierra). */
    private static boolean canReveal(final SpellAbility reveal, final Player you, final Card land) {
        try {
            for (final Card c : forge.game.card.CardLists.getValidCards(you.getCardsIn(ZoneType.Hand),
                    reveal.getParam("RevealValid"), you, land, reveal)) {
                if (c != land) {
                    return true;
                }
            }
            return false;
        } catch (final RuntimeException e) {
            return true;
        }
    }

    /** Si el "salvo que pagues" se puede evitar: vida, siempre (lo eliges tu); revelar, si tienes que. */
    private static boolean avoidable(final SpellAbility sa, final Player you) {
        try {
            final Cost cost = new Cost(sa.getParam("UnlessCost"), true);
            for (final CostPart part : cost.getCostParts()) {
                if (part instanceof CostPayLife) {
                    return true;
                }
            }
            return cost.canPay(sa, you, true);
        } catch (final RuntimeException e) {
            return true;
        }
    }
}
