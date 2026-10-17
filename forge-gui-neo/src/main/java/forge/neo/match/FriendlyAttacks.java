package forge.neo.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.ai.ComputerUtil;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.util.collect.FCollection;

/**
 * <b>Que la IA te ataque menos</b>: "Agresividad de la IA contigo" (Discord,
 * 17-10-2026): <i>"I like testing Commander decks against three AI, but ...
 * they sometimes seem to prioritize attacking me instead of each other ... Just
 * a lower priority when the AI is deciding who to attack. If I'm clearly the
 * biggest threat, they should still come after me"</i>.
 *
 * <p><b>Como decide Forge</b> ({@code AiAttackController.choosePreferredDefenderPlayer},
 * estatico, en forge-ai: no se toca): puntua la amenaza de cada rival (su mesa,
 * su mano, sus tierras, y mucho mas si tiene poca vida; monarca e iniciativa),
 * se queda con los que estan a unos 10 puntos del mejor y elige uno AL AZAR. El
 * humano no cuenta ni mas ni menos que una IA.
 *
 * <p><b>Lo que se hace aqui</b>, desde fuera: antes de que la IA declare, se
 * repite esa misma cuenta con la amenaza del humano rebajada (al 65 % en "Baja",
 * al 35 % en "Muy baja": proporcional, porque esas puntuaciones van de decenas a
 * miles segun avanza la partida y un descuento fijo dejaria de notarse). Si aun
 * asi el humano esta entre los que Forge elegiria, no se toca nada. Si no, la IA
 * decide su ataque con el humano (y sus planeswalkers) fuera de la lista de
 * defensores del combate: el unico sitio donde la IA mira esa lista
 * ({@code AiAttackController.chooseDefender}) toma entonces al primero que
 * queda —el rival mas amenazante— y vuelve a calcular su ataque ENTERO contra
 * el. No es redirigir criaturas a ciegas: planea contra quien tiene delante.
 *
 * <p>La lista se repone al terminar, pase lo que pase. Lo que la IA no puede
 * dejar de hacer sigue valiendo: una criatura obligada a atacarte te ataca
 * ({@code Combat.addAttacker} no mira esa lista). Y si el ataque resultante no
 * pasa la validacion del motor, se descarta y la IA declara como siempre: lo
 * peor que puede pasar es que no haga nada.
 *
 * <p><b>Solo en una partida libre de Commander</b> (decidido por Ana): ni Quest,
 * ni Ascenso, ni draft, ni torneo, ni red. Quien la arranca la arma
 * ({@link #arm}) y la desarma al acabar; ademas se comprueba que el juego sea
 * {@code GameType.Commander}. De fabrica, "Normal": nadie tiene que cambiar nada.
 */
public final class FriendlyAttacks {

    /** El ajuste: 0 = normal (lo de Forge), 1 = baja, 2 = muy baja. */
    public static final String SETTING = "aiAggressionToYou";

    public static final int NORMAL = 0;
    public static final int LOW = 1;
    public static final int VERY_LOW = 2;

    /** El nivel de la partida en curso; 0 si no es una partida libre de Commander. */
    private static volatile int armed = NORMAL;

    /**
     * La partida a la que se ha enganchado, en su primer ataque (-1 = aun
     * ninguna). Si se abandona una partida y se empieza otra cosa antes de que
     * la vieja acabe del todo, lo nuevo no hereda el ajuste.
     */
    private static volatile int armedGame = -1;

    /** Para el comprobador: cuantas veces se ha apartado al humano. */
    private static volatile int spared;

    private FriendlyAttacks() {
    }

    /** Lo pone quien arranca una partida libre de Commander. */
    public static void arm(final int level) {
        armedGame = -1;
        armed = Math.max(NORMAL, Math.min(VERY_LOW, level));
    }

    /** Y lo quita al acabar (en un finally). */
    public static void disarm() {
        armed = NORMAL;
        armedGame = -1;
    }

    /** Si esta partida es la armada: la primera que ataca se queda con el ajuste. */
    private static synchronized boolean isArmedGame(final Game game) {
        if (armedGame < 0) {
            armedGame = game.getId();
        }
        return armedGame == game.getId();
    }

    public static int armedLevel() {
        return armed;
    }

    static int sparedCount() {
        return spared;
    }

    /** Cuanto cuenta tu amenaza en cada nivel. */
    static double factor(final int level) {
        switch (level) {
            case LOW:
                return 0.65;
            case VERY_LOW:
                return 0.35;
            default:
                return 1.0;
        }
    }

    /**
     * Declara los atacantes de {@code ai}: como siempre ({@code plain}), o con
     * los humanos apartados si con el ajuste no merecen el ataque.
     */
    public static void declare(final Player ai, final Combat combat, final Runnable plain) {
        decide(ai, combat, plain);
        if (combat != null && ai != null) {
            lastAttacked = ai.getName() + "->" + attackedPlayers(combat);
        }
    }

    /** Para el comprobador: a quien ataco la ultima IA que declaro ("IA-1->IA-2"). */
    private static volatile String lastAttacked = "";

    static String lastAttacked() {
        return lastAttacked;
    }

    private static String attackedPlayers(final Combat combat) {
        final java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (final Card c : combat.getAttackers()) {
            final Player p = combat.getDefenderPlayerByAttacker(c);
            if (p != null) {
                names.add(p.getName());
            }
        }
        return String.join(",", names);
    }

    private static void decide(final Player ai, final Combat combat, final Runnable plain) {
        final int level = armed;
        final Game game = ai == null ? null : ai.getGame();
        if (level <= NORMAL || combat == null || game == null
                || game.getRules().getGameType() != GameType.Commander
                || !(combat.getDefenders() instanceof FCollection)
                || !isArmedGame(game)) {
            plain.run();
            return;
        }
        final List<Player> sparedPlayers = playersToSpare(ai, level);
        if (sparedPlayers.isEmpty()) {
            plain.run();
            return;
        }

        @SuppressWarnings("unchecked")
        final FCollection<GameEntity> defenders = (FCollection<GameEntity>) combat.getDefenders();
        final List<GameEntity> saved = new ArrayList<>(defenders);
        final List<GameEntity> kept = new ArrayList<>();
        for (final GameEntity e : saved) {
            if (!belongsTo(e, sparedPlayers)) {
                kept.add(e);
            }
        }
        if (kept.isEmpty() || kept.stream().noneMatch(e -> e instanceof Player)) {
            plain.run();
            return;
        }
        // El rival mas amenazante, el primero: es el que la IA tomara.
        final Player target = mostThreatening(ai, kept);
        if (target != null) {
            kept.remove(target);
            kept.add(0, target);
        }

        try {
            defenders.clear();
            defenders.addAll(kept);
            plain.run();
        } finally {
            defenders.clear();
            defenders.addAll(saved);
        }

        boolean valid;
        try {
            valid = CombatUtil.validateAttackers(combat);
        } catch (final RuntimeException e) {
            valid = false;
        }
        if (!valid) {
            // Lo que la IA decidio sin el humano no vale para el motor: como
            // siempre, y aqui no ha pasado nada.
            System.out.println("[neo] friendly AI: " + ai.getName()
                    + "'s attack was not valid without the human; declaring as usual");
            combat.clearAttackers();
            plain.run();
            return;
        }
        spared++;
        System.out.println("[neo] friendly AI: " + ai.getName() + " looks past "
                + names(sparedPlayers) + " and attacks " + (target == null ? "someone else" : target.getName()));
    }

    /**
     * Los humanos que, con su amenaza rebajada, Forge ya no elegiria: los que se
     * quedan por debajo del umbral de {@code choosePreferredDefenderPlayer}.
     * Vacio si no hay al menos un rival que no sea humano.
     */
    static List<Player> playersToSpare(final Player ai, final int level) {
        final List<Player> opponents = new ArrayList<>(ai.getOpponents());
        final List<Player> out = new ArrayList<>();
        if (opponents.size() < 2) {
            return out;
        }
        boolean anyAi = false;
        for (final Player p : opponents) {
            if (p.getController() != null && p.getController().isAI()) {
                anyAi = true;
            }
        }
        if (!anyAi) {
            return out;
        }
        final Map<Player, Integer> scores = threatScores(ai, opponents);
        final double factor = factor(level);
        for (final Player p : opponents) {
            if (isHuman(p)) {
                scores.put(p, (int) Math.round(scores.get(p) * factor));
            }
        }
        int max = Integer.MIN_VALUE;
        for (final int s : scores.values()) {
            max = Math.max(max, s);
        }
        final int limit = max - 10 - ai.getGame().getPhaseHandler().getTurn();
        for (final Player p : opponents) {
            if (isHuman(p) && scores.get(p) < limit) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * La misma cuenta que {@code AiAttackController.choosePreferredDefenderPlayer}
     * con {@code forCombatDmg}: mesa, poca vida, monarca, iniciativa. Copiada y
     * no llamada porque alli es privada por dentro; si Forge la cambia, esto
     * sigue siendo una aproximacion razonable y lo peor es apartar menos.
     */
    static Map<Player, Integer> threatScores(final Player ai, final List<Player> opponents) {
        final Map<Player, Integer> scores = new HashMap<>();
        for (final Player opp : opponents) {
            final int life = opp.getLife();
            int score = ComputerUtil.evaluateBoardPosition(ai, opp);
            final int lowLifeThreshold = Math.min(20, opp.getStartingLife());
            if (life > 0 && life < lowLifeThreshold) {
                final int lifeDeficit = lowLifeThreshold - life;
                score += lifeDeficit * lifeDeficit;
            }
            if (opp.isMonarch() && ai.canBecomeMonarch()) {
                score += 80;
            }
            if (opp.hasInitiative()) {
                score += 80;
            }
            if (!opp.canLoseLife()) {
                score -= 100;
            }
            if (opp.cantLoseForZeroOrLessLife()) {
                score -= 50;
            }
            scores.put(opp, score);
        }
        return scores;
    }

    private static Player mostThreatening(final Player ai, final List<GameEntity> kept) {
        final List<Player> players = new ArrayList<>();
        for (final GameEntity e : kept) {
            if (e instanceof Player p) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        final Map<Player, Integer> scores = threatScores(ai, players);
        Player best = null;
        for (final Player p : players) {
            if (best == null || scores.get(p) > scores.get(best)) {
                best = p;
            }
        }
        return best;
    }

    /** El jugador, o un planeswalker suyo, o una batalla que el protege. */
    private static boolean belongsTo(final GameEntity e, final List<Player> players) {
        if (e instanceof Player p) {
            return players.contains(p);
        }
        if (e instanceof Card c) {
            final Player who = c.isBattle() ? c.getProtectingPlayer() : c.getController();
            return who != null && players.contains(who);
        }
        return false;
    }

    private static boolean isHuman(final Player p) {
        return p.getController() != null && !p.getController().isAI();
    }

    private static String names(final List<Player> players) {
        final StringBuilder sb = new StringBuilder();
        for (final Player p : players) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getName());
        }
        return sb.toString();
    }
}
