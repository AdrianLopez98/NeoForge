package forge.neo;

import forge.deck.Deck;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import javafx.application.Platform;

/**
 * El torneo (la auditoría del motor, apartado C6): el cuadro de eliminacion directa, montarlo,
 * enseñarlo y arrancar cada partida (jugada, vista, o al azar).
 *
 * <p>Nace de la auditoría del motor, apartado E2, junto con las demas piezas de {@code NeoApp}
 * repartidas por familia. Movido tal cual desde {@code NeoApp}, sin tocar
 * la logica: ver el javadoc de {@link NeoAppDebug} para el porque.
 */
final class NeoAppTournament {

    private final NeoApp app;

    NeoAppTournament(final NeoApp app) {
        this.app = app;
    }

    /**
     * El torneo (la auditoría del motor, apartado C6): con un cuadro guardado se entra a él
     * directamente — también si ya ha terminado, para poder MIRARLO — y si
     * no hay ninguno, a montar uno nuevo.
     */
    void showTournament() {
        final forge.neo.tournament.NeoTournament t = forge.neo.tournament.NeoTournament.current();
        if (t != null) {
            showTournamentRun(t);
        } else {
            showTournamentSetup();
        }
    }

    void showTournamentSetup() {
        app.draftScreen = null;
        app.scene.setRoot(new forge.neo.ui.TournamentSetupScreen(app.cardWidth,
                new forge.neo.ui.TournamentSetupScreen.Actions() {
                    @Override
                    public void start(final Deck deck, final int size, final NeoFormat format) {
                        final forge.neo.tournament.NeoTournament t =
                                forge.neo.tournament.NeoTournament.start(deck, size, format);
                        if (t == null) {
                            app.showMainMenu();
                            return;
                        }
                        showTournamentRun(t);
                    }

                    @Override
                    public void back() {
                        app.showMainMenu();
                    }
                }));
        app.applyScale();
    }

    /**
     * El cuadro: qué ronda va, con qué juegas y contra quién, y cómo ha ido
     * el resto.
     *
     * <p>Se enseña SIEMPRE al instante — nunca hay una espera oculta antes
     * de esta pantalla. Los emparejamientos que aún no tienen ganador salen
     * marcados como pendientes, y es esta misma pantalla la que ofrece cómo
     * resolverlos (jugar, ver en la mesa, o al azar): la decisión es
     * siempre del jugador, nunca automática.
     */
    void showTournamentRun(final forge.neo.tournament.NeoTournament tournament) {
        app.draftScreen = null;
        app.scene.setRoot(new forge.neo.ui.TournamentRunScreen(tournament, app.cardWidth,
                new forge.neo.ui.TournamentRunScreen.Actions() {
                    @Override
                    public void play(final forge.neo.tournament.NeoTournament t) {
                        playTournamentMatch(t);
                    }

                    @Override
                    public void watchYourMatch(final forge.neo.tournament.NeoTournament t) {
                        final Deck rival = t.yourOpponentThisRound();
                        final int opponent = rival == null ? -1 : indexOf(t, rival);
                        if (opponent >= 0) {
                            watchPairing(t, 0, opponent);
                        }
                    }

                    @Override
                    public void watchOther(final forge.neo.tournament.NeoTournament t,
                                           final int a, final int b) {
                        watchPairing(t, a, b);
                    }

                    @Override
                    public void resolveRestRandomly(final forge.neo.tournament.NeoTournament t) {
                        // La tuya nunca se decide asi (principio 6 de
                        // las notas de diseño: lo que no se puede deshacer no se
                        // deja pasar por un boton que resuelve varias cosas
                        // a la vez) — solo las ajenas.
                        for (final int[] pair : t.pendingMatches()) {
                            if (pair[0] != 0 && pair[1] != 0) {
                                t.recordRandom(pair[0], pair[1]);
                            }
                        }
                        showTournamentRun(t);
                    }

                    @Override
                    public void abandon(final forge.neo.tournament.NeoTournament t) {
                        t.abandon();
                        showTournamentSetup();
                    }

                    @Override
                    public void newTournament() {
                        tournament.abandon();
                        showTournamentSetup();
                    }

                    @Override
                    public void back() {
                        app.showMainMenu();
                    }
                }));
        app.applyScale();
    }

    /** El indice del participante cuyo mazo es {@code deck} (comparacion por objeto). */
    static int indexOf(final forge.neo.tournament.NeoTournament t, final Deck deck) {
        for (int i = 0; i < t.getSize(); i++) {
            if (t.deckOf(i) == deck) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Juega tu partida de esta ronda.
     *
     * <p>Mismo patrón que {@link #playDraftMatch}: una sola partida
     * (a muerte súbita no hay banquillo ni Bo3 que pedir) en Commander.
     */
    void playTournamentMatch(final forge.neo.tournament.NeoTournament tournament) {
        final Deck mine = tournament.getDeck();
        final Deck rival = tournament.yourOpponentThisRound();
        if (mine == null || rival == null) {
            showTournamentRun(tournament);
            return;
        }
        final NeoFormat format = tournament.getFormat();
        app.lastFormat = format;
        app.lastOpponentDecks = java.util.List.of(rival);
        app.showTable();

        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        final boolean autoMana = NeoSettings.autoPayMana();
        app.table.setPrompt(forge.neo.NeoText.get("app.preparing"));

        final Thread engine = new Thread(() -> {
            boolean won = false;
            try {
                final NeoGame.Result r = NeoGame.play(mine, 1, NeoMatchUI.Mode.HUMAN, 3600,
                        false, liveBinder, NeoSettings.get(NeoSettings.AI_PROFILE, null),
                        autoMana, format, java.util.List.of(rival), 1);
                won = r.winner != null && r.winner.equals(
                        forge.player.GamePlayerUtil.getGuiPlayer().getName());
            } catch (final Exception e) {
                System.err.println("[neo] la partida del torneo ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final boolean result = won;
                Platform.runLater(() -> {
                    app.binder = null;
                    tournament.record(result);
                    showTournamentRun(tournament);
                });
            }
        }, "Game-neo-tournament");
        engine.setDaemon(true);
        engine.start();
    }

    /**
     * Ver EN LA MESA el emparejamiento {@code (a, b)} — las dos IA jugando
     * de verdad, tú mirando. Vale tanto para tu propia partida ("que juegue
     * la IA") como para una ajena ("ver una partida"): es exactamente el
     * mismo camino que ya usa {@code --watch}, con el app.binder puesto.
     *
     * <p>Antes esto se hacia OCULTO, sin mesa, en un hilo de fondo — y por
     * eso heredaba el tiempo de pensar de la ULTIMA partida jugada por un
     * humano (hasta 20s por decision) sin que nadie lo viera, dejando al
     * jugador mirando una pantalla en negro varios minutos. Aqui, al
     * enseñarse en la mesa de verdad, la espera se ve — que es justo lo que
     * se pidio.
     */
    void watchPairing(final forge.neo.tournament.NeoTournament tournament,
                              final int a, final int b) {
        final Deck deckA = tournament.deckOf(a);
        final Deck deckB = tournament.deckOf(b);
        if (deckA == null || deckB == null) {
            showTournamentRun(tournament);
            return;
        }
        final NeoFormat format = tournament.getFormat();
        app.lastFormat = format;
        app.lastOpponentDecks = java.util.List.of(deckB);
        app.showTable();

        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        app.table.setPrompt(forge.neo.NeoText.get("app.preparing"));

        final Thread engine = new Thread(() -> {
            int winner = a;
            try {
                final NeoGame.Result r = NeoGame.play(deckA, 1, NeoMatchUI.Mode.OBSERVE, 3600,
                        false, liveBinder, null, true, format, java.util.List.of(deckA, deckB), 1);
                final String seat0 = forge.neo.look.NeoPlayers.aiName(0);
                winner = r.winner != null && r.winner.equals(seat0) ? a : b;
            } catch (final Exception e) {
                System.err.println("[neo] la partida observada del torneo ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final int result = winner;
                Platform.runLater(() -> {
                    app.binder = null;
                    tournament.recordMatch(a, b, result);
                    showTournamentRun(tournament);
                });
            }
        }, "Game-neo-tournament-watch");
        engine.setDaemon(true);
        engine.start();
    }

}
