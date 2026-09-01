package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.card.CardStateName;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Las criaturas que <b>se preparan</b> (<i>prepare</i>, de Secretos de
 * Strixhaven): 54 cartas que llevan un hechizo dentro y que, al cumplirse su
 * condicion, dejan que lances una copia de ese hechizo.
 *
 * <h2>Por que hace falta comprobarlo</h2>
 *
 * Porque <b>el hechizo no se lanza desde la criatura</b>, y eso no se deduce de
 * la carta. Lo que hace el motor ({@code AlterAttributeEffect}, caso
 * {@code "Prepared"}) es:
 *
 * <ol>
 *   <li>fabricar una <b>copia</b> de la carta en su estado
 *       {@code PreparedSpell} y meterla en el <b>EXILIO</b>;</li>
 *   <li>crear un efecto en la zona de mando con
 *       {@code setRenderForUI(false)} — o sea, que <b>no</b> se pinta — cuyo
 *       unico trabajo es dar {@code MayPlay} sobre esa copia exiliada y
 *       "despreparar" la criatura en cuanto la lances.</li>
 * </ol>
 *
 * O sea que para el jugador es <b>lanzar una carta desde el exilio</b>, que es
 * justo el camino que ya abrimos para el flashback y las aventuras: el motor
 * publica esas cartas en {@code PlayerView.getFlashback()} (la zona de mentira
 * {@code ZoneType.Flashback}, que sale de
 * {@code Player.getCardsActivatableInExternalZones}) y {@code ZoneViewer} las
 * marca como accionables y manda el {@code selectCard} al clicarlas.
 *
 * <p><b>Lo que se comprueba, y en este orden</b>, porque cada punto solo tiene
 * sentido si el anterior es cierto:
 *
 * <ol>
 *   <li>La criatura se prepara de verdad ({@code Card.isPrepared()}).</li>
 *   <li>Aparece la copia en el exilio, en estado {@code PreparedSpell}.</li>
 *   <li>El motor la da por <b>jugable</b> desde ahi
 *       ({@code getCardsActivatableInExternalZones}) — esto es lo que decide
 *       si se puede clicar.</li>
 *   <li>Y lo mismo <b>visto desde la vista</b>, que es lo unico que lee la
 *       interfaz: {@code PlayerView.getFlashback()} la trae.</li>
 *   <li>El efecto de la zona de mando NO se pinta
 *       ({@code CardView.getRenderForUI()}), o la zona de mando se llenaria de
 *       fichas mudas.</li>
 *   <li>Y la criatura de la mesa dice que esta preparada
 *       ({@code CardView.getPreparedSpell()}), que es de donde puede salir un
 *       aviso en la carta.</li>
 * </ol>
 *
 * <p><b>Lo que NO prueba</b>: que el click concreto lance el hechizo. Eso es
 * una <i>secuencia</i>, y en modo automatico cada fase dura milisegundos — la
 * misma limitacion que ya documenta {@code FilterCheck}. Aqui se comprueba el
 * <i>estado</i>: que la carta esta donde la interfaz la busca y marcada como
 * jugable. El click en si es el mismo de siempre y se prueba jugando, con
 * {@code run.cmd ui --live "--rig=Scathing Shadelock"}.
 *
 * <p>Se ejecuta con {@code run.cmd preparecheck}.
 */
public final class PrepareCheck {

    private PrepareCheck() {
    }

    private static int passed;
    private static int failed;

    /**
     * La criatura de prueba, y por que esta.
     *
     * <p>De las 54, casi todas se preparan con una condicion que no se provoca
     * a voluntad (ganar 2 vidas, que un rival robe dos cartas, atacar con dos
     * criaturas). Scathing Shadelock se prepara <b>al principio de tu primera
     * fase principal y sin condicion ninguna</b>: es la unica forma de que la
     * comprobacion no dependa de que la partida haga algo concreto.
     */
    private static final String CREATURE = "Scathing Shadelock";

    /** El hechizo que lleva dentro, o sea lo que hay que poder lanzar. */
    private static final String SPELL = "Venomous Words";

    public static void run() {
        passed = 0;
        failed = 0;

        final Probe p = probe();

        check(p.seen, "la posicion arranco y la criatura esta en la mesa");
        check(p.prepared, "la criatura SE PREPARA de verdad (Card.isPrepared)");
        check(p.copyInExile,
                "la copia del hechizo aparece en el EXILIO, no en la criatura"
                + " (exilio: " + p.exile + ")");
        check(p.copyIsPreparedState,
                "y esta en su estado PreparedSpell, o sea con la cara de hechizo");
        check(p.engineSaysPlayable,
                "el motor la da por jugable desde el exilio"
                + " (getCardsActivatableInExternalZones)");
        check(p.viewSaysPlayable,
                "y la VISTA tambien: PlayerView.getFlashback() la trae, que es"
                + " lo unico que lee ZoneViewer para dejar clicarla"
                + " (flashback: " + p.flashback + ")");
        check(p.effectHidden,
                "el efecto de la zona de mando no se pinta (setRenderForUI false)");
        check(p.viewSaysPrepared,
                "la criatura de la mesa publica su hechizo preparado"
                + " (CardView.getPreparedSpell)");
        check(p.sameCard,
                "y ES la misma carta que el motor da por jugable, por id"
                + " — es lo que hace que clicar la criatura la lance");
        check(p.structural,
                "hasPreparedSpell() es estructural: sirve para rotular la carta"
                + " tambien antes de prepararse");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(
                    failed + " comprobacion(es) de criaturas preparadas han fallado");
        }
    }

    /** Lo que se saca de la posicion. */
    private static final class Probe {
        private boolean seen;
        private boolean prepared;
        private boolean copyInExile;
        private boolean copyIsPreparedState;
        private boolean engineSaysPlayable;
        private boolean viewSaysPlayable;
        private boolean effectHidden = true;
        private boolean viewSaysPrepared;
        private boolean sameCard;
        private boolean structural;
        private final List<String> exile = new ArrayList<>();
        private final List<String> flashback = new ArrayList<>();
    }

    private static Probe probe() {
        final Probe out = new Probe();
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean alive = new AtomicBoolean(true);
        final NeoMatchUI[] gui = new NeoMatchUI[1];

        final Thread poller = new Thread(() -> {
            while (alive.get()) {
                try {
                    if (!done.get()) {
                        inspect(gui[0], done, out);
                    }
                    Thread.sleep(120L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // Todavia no hay partida, o esta a medias: se reintenta.
                }
            }
        }, "preparecheck");
        poller.setDaemon(true);

        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 40, null, false, ui -> {
                    gui[0] = ui;
                    poller.start();
                });
        alive.set(false);
        return out;
    }

    /**
     * La mesa: la criatura puesta y el turno empezando.
     *
     * <p>Se arranca en <b>mantenimiento</b> a proposito, no en la fase
     * principal: el disparo es "al comienzo de tu primera fase principal", asi
     * que hay que dejar que la partida <i>entre</i> en ella. Empezando ya
     * dentro, el disparo se habria perdido y esto diria que la mecanica no
     * funciona cuando el fallo seria de la posicion.
     */
    private static TutorialLesson position() {
        return new TutorialLesson("prepare", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=UPKEEP",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + CREATURE + ";Swamp;Swamp",
                "humanhand=",
                "humanlibrary=Swamp;Swamp;Swamp",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Plains;Plains;Plains",
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
    }

    private static Player human(final NeoMatchUI ui) {
        if (ui == null || ui.getGameView() == null) {
            return null;
        }
        final Game game = ui.getGameView().getGame();
        if (game == null) {
            return null;
        }
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static void inspect(final NeoMatchUI ui, final AtomicBoolean done, final Probe out) {
        final Player me = human(ui);
        if (me == null) {
            return;
        }
        Card creature = null;
        for (final Card c : me.getCardsIn(ZoneType.Battlefield).threadSafeIterable()) {
            if (CREATURE.equals(c.getName())) {
                creature = c;
            }
        }
        if (creature == null) {
            return;
        }
        out.seen = true;
        if (!creature.isPrepared()) {
            // Todavia no ha llegado a la fase principal: se sigue mirando.
            return;
        }
        out.prepared = true;

        // 1. La copia, en el exilio.
        //
        // Las listas se guardan solo cuando traen algo: ahora se mira varias
        // veces (ver el final del metodo) y una pasada con el exilio vacio
        // borraria lo que ya se habia visto, dejando el mensaje del rojo sin
        // la unica pista que tiene.
        final List<String> exileNow = new ArrayList<>();
        for (final Card c : me.getCardsIn(ZoneType.Exile).threadSafeIterable()) {
            exileNow.add(c.getName());
            if (SPELL.equals(c.getName()) || CREATURE.equals(c.getName())) {
                out.copyInExile = true;
                out.copyIsPreparedState |=
                        c.getCurrentStateName() == CardStateName.PreparedSpell;
            }
        }
        if (!exileNow.isEmpty()) {
            out.exile.clear();
            out.exile.addAll(exileNow);
        }

        // 2. Y jugable desde ahi. Esto es lo que decide si se puede clicar.
        for (final Card c : me.getCardsActivatableInExternalZones(true)) {
            if (c.isInZone(ZoneType.Exile)) {
                out.engineSaysPlayable = true;
            }
        }

        // 3. Lo mismo, pero por donde lo lee la interfaz.
        final PlayerView view = me.getView();
        final List<String> flashNow = new ArrayList<>();
        if (view != null && view.getFlashback() != null) {
            for (final CardView cv : view.getFlashback()) {
                flashNow.add(String.valueOf(cv));
                out.viewSaysPlayable = true;
            }
        }
        if (!flashNow.isEmpty()) {
            out.flashback.clear();
            out.flashback.addAll(flashNow);
        }

        // 4. El efecto de la zona de mando no llega a la vista.
        //
        // No hay que preguntarle nada: el motor lo marca con
        // setRenderForUI(false) y TrackableCollection lo FILTRA al construir
        // la vista de la zona (CardView linea 66), asi que la prueba es que
        // NO este. Si algun dia dejara de filtrarse, la zona de mando se
        // llenaria de fichas mudas que no se pueden usar.
        if (view != null && view.getCards(ZoneType.Command) != null) {
            for (final CardView cv : view.getCards(ZoneType.Command)) {
                if (cv != null && String.valueOf(cv).contains("Prepared Spell")) {
                    out.effectHidden = false;
                }
            }
        }

        // 5. Y la criatura publica su hechizo preparado.
        final CardView me2 = creature.getView();
        final boolean nowPrepared = me2 != null && me2.getPreparedSpell() != null;
        out.viewSaysPrepared |= nowPrepared;

        // 6. La pieza que sostiene el click: la carta que publica la criatura
        //    tiene que ser LA MISMA que el motor da por jugable. Se compara
        //    por id y no por identidad de objeto -- en red los CardView son
        //    copias (ver las trampas conocidas), que es justo como lo compara
        //    NeoMatchUI.isPlayableOutside.
        if (nowPrepared && view != null && view.getFlashback() != null) {
            for (final CardView cv : view.getFlashback()) {
                if (cv != null && cv.getId() == me2.getPreparedSpell().getId()) {
                    out.sameCard = true;
                }
            }
        }

        // 7. Y lo ESTRUCTURAL, que es otra pregunta: hasPreparedSpell() dice
        //    que la carta TIENE cara de hechizo, este preparada o no. Es lo
        //    que rotula el zoom ("Su hechizo preparado" en vez de "La otra
        //    cara") tambien en la mano y en el catalogo.
        out.structural = me2 != null && me2.hasPreparedSpell();

        // Se sigue mirando hasta haberlo visto TODO.
        //
        // Cerrar en el primer fotograma en que la criatura esta preparada era
        // una carrera perdida: el disparo que la prepara se resuelve con el
        // stack todavia ocupado, y un CONJURO no es lanzable con el stack
        // ocupado — asi que getCardsActivatableInExternalZones decia que no y
        // esta comprobacion salia en rojo una vez de cada tres, sin que nada
        // estuviera mal. La pregunta correcta no es "se puede lanzar en este
        // instante", es "llega a poderse lanzar".
        if (out.engineSaysPlayable && out.viewSaysPlayable && out.sameCard
                && out.copyInExile && out.copyIsPreparedState) {
            done.set(true);
        }
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
