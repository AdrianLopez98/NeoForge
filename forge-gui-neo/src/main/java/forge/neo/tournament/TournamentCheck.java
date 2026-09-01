package forge.neo.tournament;

import java.util.Locale;

import forge.deck.Deck;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.player.GamePlayerUtil;

/**
 * Un cuadro de torneo entero sin ventana: genera los participantes, resuelve
 * los emparejamientos ajenos AL AZAR (aquí no hay mesa que enseñar, así que
 * ni "verla" ni el timeout de una simulación real tienen sentido — eso se
 * comprueba con capturas, no aquí), juega las tuyas con el modo automático
 * hasta quedar eliminado o coronarte campeón, y comprueba que el cuadro se
 * guarda, se recarga y sigue accesible una vez terminado (a diferencia del
 * draft, un torneo completo NO se borra solo: se queda para poder mirarlo).
 *
 * <p>Nada de esto se ve en una captura: el ciclo completo — generar N-1
 * rivales con mazo fijo, resolver emparejamientos, avanzar de ronda, cerrar
 * el cuadro — sólo se rompe jugando de verdad. Fue también lo que cazó el
 * fallo real de {@code TournamentIO} — ver el javadoc de {@link NeoTournament}.
 *
 * <p>{@code run.cmd tournamentcheck}
 */
public final class TournamentCheck {

    private TournamentCheck() {
    }

    public static void run() {
        final long t0 = System.currentTimeMillis();

        final Deck mine = NeoGame.firstCommanderDeck();
        if (mine == null) {
            System.out.println("  FALLO: no hay ningun mazo de Commander con el que probar.");
            return;
        }

        final NeoTournament tournament = NeoTournament.start(mine, 4, NeoFormat.COMMANDER);
        if (tournament == null) {
            System.out.println("  FALLO: el motor no ha podido generar el cuadro.");
            return;
        }
        final String name = tournament.getName();
        final int size = tournament.getSize();
        System.out.printf(Locale.ROOT, "  Torneo '%s': cuadro de %d (%d rondas)%n",
                name, size, tournament.totalRounds());

        final NeoTournament reloaded = NeoTournament.current();
        final boolean persistOk = reloaded != null && name.equals(reloaded.getName())
                && reloaded.getSize() == size && reloaded.getDeck() != null;
        System.out.println(persistOk
                ? "  OK - el cuadro se guarda y NeoTournament.current() lo recupera igual"
                : "  FALLO - la persistencia no cuadra al recargar");

        int rounds = 0;
        while (!tournament.isOver() && rounds < tournament.totalRounds() + 1) {
            // Las ajenas, al azar e instantaneas -- aqui no hay mesa que
            // enseñar, y "ver la partida" es justo lo que este comprobador
            // no puede probar (necesita ventana).
            for (final int[] pair : tournament.pendingMatches()) {
                if (pair[0] != 0 && pair[1] != 0) {
                    tournament.recordRandom(pair[0], pair[1]);
                }
            }
            if (tournament.isOver()) {
                break;
            }
            final Deck rival = tournament.yourOpponentThisRound();
            if (rival == null) {
                break;
            }
            final NeoGame.Result r = NeoGame.play(mine, 1, NeoMatchUI.Mode.AUTO_PLAY, 90, false,
                    null, null, true, NeoFormat.COMMANDER, java.util.List.of(rival), 1);
            final boolean won = r.winner != null
                    && r.winner.equals(GamePlayerUtil.getGuiPlayer().getName());
            tournament.record(won);
            rounds++;
            System.out.printf(Locale.ROOT, "  Tu partida %d: %s%s%n", rounds, won ? "ganada" : "perdida",
                    r.completed ? "" : " (TIMEOUT, no cuenta como fallo del torneo)");
        }
        // Si has quedado eliminado, el cuadro puede seguir SIN ti (rondas
        // restantes, todas ajenas) hasta salir un campeon.
        for (int i = 0; i < tournament.totalRounds() && !tournament.isOver(); i++) {
            for (final int[] pair : tournament.pendingMatches()) {
                tournament.recordRandom(pair[0], pair[1]);
            }
        }

        final boolean finished = tournament.isOver();
        System.out.println(finished
                ? "  OK - el cuadro se cierra de verdad (" + tournament.getStatus() + ")"
                : "  FALLO - el torneo se ha quedado a medias");

        final boolean stillThere = finished && NeoTournament.current() != null
                && name.equals(NeoTournament.current().getName());
        System.out.println(stillThere
                ? "  OK - un torneo terminado NO se borra solo: se puede seguir mirando el cuadro"
                : "  FALLO - el cuadro ha desaparecido nada mas terminar");

        tournament.abandon();
        final boolean cleaned = NeoTournament.current() == null;
        System.out.println(cleaned
                ? "  OK - abandonar limpia el cuadro y 'torneo actual'"
                : "  FALLO - abandonar no ha limpiado el estado");

        System.out.println();
        System.out.println("  -- Estandar --");
        final Deck standardMine = forge.deck.DeckgenUtil.buildCardGenDeck(
                forge.model.FModel.getFormats().getStandard(), true);
        if (standardMine == null) {
            System.out.println("  FALLO: no se ha podido generar un mazo de Estandar para probar.");
        } else {
            final NeoTournament est = NeoTournament.start(standardMine, 4, NeoFormat.ESTANDAR);
            if (est == null) {
                System.out.println("  FALLO: el cuadro de Estandar no se ha podido montar.");
            } else {
                for (final int[] pair : est.pendingMatches()) {
                    est.recordRandom(pair[0], pair[1]);
                }
                final boolean estOk = est.currentRoundIndex() > 0 || est.isOver();
                System.out.println(estOk
                        ? "  OK - Estandar genera rivales y las ajenas se resuelven al azar sin jugar a mano"
                        : "  FALLO - el cuadro de Estandar no avanza");
                est.abandon();
            }
        }

        System.out.printf(Locale.ROOT, "%n  %ds%n", (System.currentTimeMillis() - t0) / 1000);
    }
}
