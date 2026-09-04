package forge.neo;

import forge.neo.ascent.AscentBattle;
import forge.neo.ascent.AscentEvent;
import forge.neo.ascent.AscentFeat;
import forge.neo.ascent.AscentNode;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRewards;
import forge.neo.ascent.AscentRun;
import forge.neo.ascent.AscentShop;
import forge.neo.ascent.AscentSummary;
import forge.neo.ascent.AscentUnlocks;
import forge.item.PaperCard;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.ui.AscentDeckScreen;
import forge.neo.ui.AscentEventScreen;
import forge.neo.ui.AscentMapScreen;
import forge.neo.ui.AscentOverScreen;
import forge.neo.ui.AscentPickScreen;
import forge.neo.ui.AscentRestScreen;
import forge.neo.ui.AscentRewardScreen;
import forge.neo.ui.AscentSetupScreen;
import forge.neo.ui.AscentShopScreen;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;

/**
 * El router de Ascenso: que pantalla del modo esta puesta.
 *
 * <p>Los campos de estado del modo viven <b>aqui y no en {@link NeoApp}</b>,
 * que es la leccion de la auditoría del motor, apartado E2: {@code NeoApp} llego a 4.896 lineas
 * porque cada modo nuevo le dejaba dentro sus tres campos y sus ocho metodos.
 * Es el mismo sitio que ocupan {@code NeoAppQuest} y {@code NeoAppTournament}.
 *
 * <h2>El bucle, que es lo que hace esta clase</h2>
 *
 * <pre>
 *   mapa  --eliges nodo-->  se juega / se resuelve  -->  premio  -->  mapa
 *                                    |
 *                                    +-- pierdes -->  se acaba la run
 * </pre>
 *
 * <p>Y el nodo <b>se marca resuelto al ganarlo, no al entrar</b>. Es lo que
 * impide la unica forma de hacer trampa que tendria el modo: entrar en un nodo,
 * ver que la partida va mal y cerrar el juego para que al volver el nodo siga
 * ahi pero con otra mano.
 */
final class NeoAppAscent {

    private final NeoApp app;

    NeoAppAscent(final NeoApp app) {
        this.app = app;
    }

    /** Cuanto se espera a una partida de Ascenso antes de darla por colgada. */
    private static final int TIMEOUT_SECS = 60 * 60;

    /**
     * Entrar al modo: a decidir con la run que haya, o a montar una.
     *
     * <p>Lo primero de todo es {@code AscentRelics.install()}, y va aqui por lo
     * mismo que el principio 8 (las notas de diseño): <b>en el punto por el que pasan
     * todos los caminos</b>. Una reliquia sin registrar no revienta — devuelve
     * {@code null} al pedir su carta y la run se juega sin ella, en silencio.
     *
     * <p>⚠️ La excepcion es {@code neo.ascent.enterAt} / {@code hoverAt}: esos
     * ganchos de {@code --ascent} comprueban de punta a punta que elegir un
     * nodo juega la partida, y sus temporizadores viven DENTRO de
     * {@code AscentMapScreen}. Con esas propiedades puestas se salta
     * {@code AscentPickScreen} y se entra al mapa igual que antes — si no, esas
     * pruebas dejarian de encontrar nunca el mapa que esperan capturar.
     */
    void showAscent() {
        AscentRelics.install();
        final AscentRun run = AscentRun.current();
        if (run == null) {
            showSetup();
            return;
        }
        if (Integer.getInteger("neo.ascent.enterAt", 0) > 0
                || Integer.getInteger("neo.ascent.hoverAt", 0) > 0) {
            showMap(run);
            return;
        }
        // Con una run a medias, primero se pregunta: continuar la lleva a SU
        // mapa; abandonar pasa por su propio confirm (principio 6b).
        showPick(run);
    }

    /** Continuar o abandonar la run que hay, sin cargar el mapa entero. */
    void showPick(final AscentRun run) {
        app.scene.setRoot(new AscentPickScreen(run, app.cardWidth, new AscentPickScreen.Actions() {
            @Override
            public void continueRun() {
                showMap(run);
            }

            @Override
            public void deck() {
                app.scene.setRoot(new AscentDeckScreen(run, app.cardWidth, () -> showPick(run)));
                app.applyScale();
            }

            @Override
            public void abandon() {
                run.discard();
                app.showMainMenu();
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        }));
        app.applyScale();
    }

    /**
     * Montar una run.
     *
     * <p>Nunca se empieza una sin pasar por aqui: de una run no se vuelve
     * atras, asi que una casilla del menu que la empezara al pulsarla se
     * llevaria por delante la que hubiera a medias y dejaria dentro a quien
     * solo entro a ver que era esto (principio 6).
     */
    void showSetup() {
        AscentRelics.install();
        app.scene.setRoot(new AscentSetupScreen(app.cardWidth, AscentRun.current() != null,
                new AscentSetupScreen.Actions() {
                    @Override
                    public void start(final AscentRun.Mode mode, final PaperCard commander,
                                      final int ascension) {
                        final AscentRun previous = AscentRun.current();
                        if (previous != null) {
                            // Su mazo se va con ella: si no, la carpeta de
                            // Ascenso acumula cadaveres.
                            previous.discard();
                        }
                        showMap(AscentRun.begin(mode, ascension,
                                mode == AscentRun.Mode.COMMANDER ? 40 : 20, commander));
                    }

                    @Override
                    public void back() {
                        app.showMainMenu();
                    }
                }));
        app.applyScale();
    }

    /** El mapa del acto en curso. */
    void showMap(final AscentRun run) {
        app.scene.setRoot(new AscentMapScreen(run, new AscentMapScreen.Actions() {
            @Override
            public void enter(final AscentNode node) {
                NeoAppAscent.this.enter(run, node);
            }

            @Override
            public void deck() {
                app.scene.setRoot(new AscentDeckScreen(run, app.cardWidth, () -> showMap(run)));
                app.applyScale();
            }

            @Override
            public void abandon() {
                run.discard();
                app.showMainMenu();
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        }));
        app.applyScale();
    }

    /**
     * Las pantallas del bucle, sin jugar la run.
     *
     * <p>Para verlas de verdad harian falta un combate ganado (el premio) y
     * llegar a la fila 10 (el descanso), o sea media hora de partida por
     * captura. Con esto se abren solas sobre una run de mentira, que es lo que
     * permite comprobarlas con {@code --snapshot}.
     */
    void showMock(final String which) {
        AscentRelics.install();
        AscentRun run = AscentRun.current();
        if (run == null) {
            run = AscentRun.begin(AscentRun.Mode.STANDARD, 0, 20, null);
        }
        final AscentRun demo = run;
        if ("shop".equals(which)) {
            // Con dinero: una tienda sin creditos ensenya lo mismo pero todo
            // apagado, que es justo lo que NO hay que mirar.
            if (demo.getCredits() < 400) {
                demo.addCredits(400 - demo.getCredits());
            }
            AscentNode node = demo.map().boss();
            outer:
            for (int r = 0; r < forge.neo.ascent.AscentMap.ROWS; r++) {
                for (final AscentNode n : demo.map().row(r)) {
                    if (n.getKind() == AscentNode.Kind.SHOP) {
                        node = n;
                        break outer;
                    }
                }
            }
            app.scene.setRoot(new AscentShopScreen(demo, AscentShop.stock(demo, node),
                    app.cardWidth, () -> showMap(demo)));
            app.applyScale();
            return;
        }
        if (which != null && which.startsWith("event")) {
            // -Dneo.ascent.event=<id> para uno concreto: cada uno ensenya una
            // cosa distinta (el bloqueado por creditos, el de quitar carta, la
            // apuesta...). Se busca por byId, que mira el catalogo ENTERO y no
            // el pozo desbloqueado: si no, los cuatro que hay detras de un hito
            // no se podrian capturar hasta habertelos ganado jugando.
            final String id = System.getProperty("neo.ascent.event");
            AscentEvent ev = id == null ? null : AscentEvent.byId(id);
            if (ev == null) {
                ev = AscentEvent.all().get(0);
            }
            // Con algo de dinero y a media vida: con la vida llena y sin
            // creditos, media pantalla sale apagada — que es justo lo que NO
            // hay que mirar.
            demo.recordLife(Math.max(1, demo.getMaxLife() * 2 / 3));
            if (demo.getCredits() < 200) {
                demo.addCredits(200 - demo.getCredits());
            }
            final AscentEventScreen screen = new AscentEventScreen(demo, ev,
                    new java.util.Random(7), app.cardWidth, () -> showMap(demo));
            app.scene.setRoot(screen);
            app.applyScale();
            // -Dneo.ascent.eventPick=N: pulsa esa opcion sola, para capturar lo
            // que viene DESPUES (la rejilla del mazo, el 1 de 3, el resultado).
            final int pick = Integer.getInteger("neo.ascent.eventPick", -1);
            if (pick >= 0) {
                screen.autoPickForTest(pick, Long.getLong("neo.ascent.eventPickAt", 1800L));
            }
            return;
        }
        if ("deck".equals(which)) {
            app.scene.setRoot(new AscentDeckScreen(demo, app.cardWidth, () -> showMap(demo)));
            app.applyScale();
            return;
        }
        if ("over".equals(which)) {
            // -Dneo.ascent.overWon=true para el final bueno: los dos textos y
            // los dos papeles son distintos, y el de ganar ademas dice si has
            // desbloqueado Ascension.
            final boolean won = Boolean.getBoolean("neo.ascent.overWon");
            // Una run recien empezada da 0 nodos y la vida llena, o sea la
            // pantalla que menos se parece a la de verdad. Se le pone algo de
            // historia encima para que la captura sirva de algo.
            demo.recordLife(Math.max(1, demo.getMaxLife() / 3));
            demo.addCredits(140);
            for (final AscentNode n : demo.available()) {
                demo.clear(n);
                break;
            }
            if (demo.relics().isEmpty() && !AscentRelics.all().isEmpty()) {
                demo.addRelic(AscentRelics.all().get(0));
            }
            // Y un hito conseguido, que es lo unico bueno que puede salir en
            // esta pantalla cuando se ha perdido. NO se apunta de verdad
            // (nada de AscentUnlocks.record): una maqueta que le regalara al
            // jugador un desbloqueo seria peor que no tener maqueta.
            showOver(AscentSummary.of(demo, won, won)
                    .withFeats(java.util.List.of(AscentFeat.REACH_ACT_2)));
            return;
        }
        if ("rest".equals(which)) {
            // A media vida: con la vida llena el boton de curarse sale
            // deshabilitado y no se ve lo que hay que ver.
            demo.recordLife(Math.max(1, demo.getMaxLife() / 2));
            app.scene.setRoot(new AscentRestScreen(demo, app.cardWidth, () -> showMap(demo)));
            app.applyScale();
            return;
        }
        // El premio de un JEFE, que es el unico que ofrece reliquias a elegir.
        reward(demo, demo.map().boss());
    }

    // ------------------------------------------------------------------
    //  Resolver un nodo
    // ------------------------------------------------------------------

    private void enter(final AscentRun run, final AscentNode node) {
        if (AscentBattle.isBattle(node.getKind())) {
            fight(run, node);
            return;
        }
        if (node.getKind() == AscentNode.Kind.REST) {
            app.scene.setRoot(new AscentRestScreen(run, app.cardWidth, () -> {
                run.clear(node);
                showMap(run);
            }));
            app.applyScale();
            return;
        }
        if (node.getKind() == AscentNode.Kind.SHOP) {
            // El nodo se marca resuelto AL ENTRAR, y aqui si es lo correcto:
            // no hay partida que rebobinar, y lo que evita es rerodar el
            // escaparate saliendo del juego y volviendo a entrar.
            run.clear(node);
            app.scene.setRoot(new AscentShopScreen(run, AscentShop.stock(run, node),
                    app.cardWidth, () -> showMap(run)));
            app.applyScale();
            return;
        }
        if (node.getKind() == AscentNode.Kind.EVENT) {
            // Igual que la tienda: resuelto AL ENTRAR. No hay partida que
            // rebobinar, y lo que evita es volver a tirar el evento (y sus
            // apuestas) saliendo del juego y entrando otra vez.
            run.clear(node);
            app.scene.setRoot(new AscentEventScreen(run, AscentEvent.of(run, node),
                    AscentEvent.rng(run, node), app.cardWidth, () -> showMap(run)));
            app.applyScale();
            return;
        }
        // Tesoro: se cobra el premio y al mapa.
        run.clear(node);
        reward(run, node);
    }

    /**
     * Jugar el duelo de un nodo.
     *
     * <p>Mesa <b>nueva</b>, como en cualquier otra partida: reaprovechar la del
     * nodo anterior la deja con sus nodos y sus resaltados puestos. Es lo mismo
     * que hacen {@code startFromHome}, el puzzle y el duelo de la aventura.
     */
    private void fight(final AscentRun run, final AscentNode node) {
        // Esta partida vale una run: el menu de Escape tiene que decirlo.
        app.runAtStake = true;
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        app.showTable();
        final TableBinder binder = new TableBinder(app.table);
        app.binder = binder;
        app.table.setPrompt(NeoText.get("app.preparingDuel"));

        final Thread engine = new Thread(() -> {
            AscentBattle.Outcome outcome = null;
            try {
                outcome = AscentBattle.fight(run, node, NeoMatchUI.Mode.HUMAN,
                        TIMEOUT_SECS, binder);
            } catch (final RuntimeException e) {
                System.err.println("[neo] el duelo de Ascenso ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final AscentBattle.Outcome got = outcome;
                Platform.runLater(() -> {
                    app.binder = null;
                    app.runAtStake = false;
                    if (got == null) {
                        // La partida ha reventado. NO se da el nodo por
                        // resuelto ni la run por perdida: se vuelve al mapa
                        // tal y como estaba. Perder una run por un fallo
                        // nuestro seria lo peor que puede pasar aqui.
                        showMap(run);
                        return;
                    }
                    // Reiniciar no se ofrece en Ascenso (seria rebarajar la
                    // mano), pero si llegara de algun sitio se trata como lo
                    // que es: dejar el duelo a medias, o sea perder la run.
                    if (!got.alive || got.exit == NeoMatchUI.Exit.RESTART) {
                        gameOver(run, got.won);
                        return;
                    }
                    // Se marca resuelto AL GANARLO, nunca al entrar.
                    run.clear(node);
                    reward(run, node);
                });
            }
        }, "Game-neo-ascent");
        engine.setDaemon(true);
        engine.start();
    }

    /** El premio del nodo, si da alguno. */
    private void reward(final AscentRun run, final AscentNode node) {
        final AscentRewards.Reward got = AscentRewards.of(run, node);
        if (got.credits > 0) {
            run.addCredits(got.credits);
        }
        if (got.cards.isEmpty() && got.relics.isEmpty()) {
            showMap(run);
            return;
        }
        app.scene.setRoot(new AscentRewardScreen(run, got, app.cardWidth, () -> showMap(run)));
        app.applyScale();
    }

    /**
     * Se acabo.
     *
     * <p>La run se borra <b>aqui</b> y no al volver al menu: si se dejara para
     * despues, cerrar el juego en esta pantalla la dejaria guardada y el menu
     * ofreceria continuar una partida que ya se perdio — que es justo lo que un
     * roguelike no puede permitir.
     */
    private void gameOver(final AscentRun run, final boolean won) {
        final boolean completed = won && run.isCompleted();
        final boolean unlocked = completed
                // Lo unico que sobrevive a la run. Va ANTES de discard(), que
                // borra todo lo que empieza por "ascent." — los desbloqueos
                // viven en sus propias claves justo para no irse con ella.
                && AscentUnlocks.recordWin(run.getAscension());
        // ⚠️ Y la FOTO tambien va antes: discard() se lleva el .dck y el bloque
        // entero, o sea todo lo que el resumen tiene que ensenyar. Ver
        // AscentSummary.
        final AscentSummary photo = AscentSummary.of(run, completed, unlocked);
        // Los hitos se calculan A PARTIR de la foto, asi que van despues de
        // hacerla y antes de borrar la run — igual que todo lo demas que tiene
        // que sobrevivir a discard(). Y se apuntan tambien cuando se ha
        // perdido: lo que la derrota se lleva es la run, no lo que ya te
        // habias ganado (el plan de Ascenso).
        final AscentSummary summary = photo.withFeats(AscentUnlocks.record(photo));
        run.discard();
        System.out.println("[ascenso] run terminada: " + summary);
        showOver(summary);
    }

    /**
     * El resumen de la run, que es donde se decide si hay otra.
     *
     * <p>La run ya no existe cuando esto se ve: lo que se pinta es la foto.
     */
    void showOver(final AscentSummary summary) {
        app.scene.setRoot(new AscentOverScreen(summary, app.cardWidth,
                new AscentOverScreen.Actions() {
                    @Override
                    public void again() {
                        // A la pantalla de montar, NO a empezar una run: de una
                        // run no se vuelve atras (principio 6).
                        showSetup();
                    }

                    @Override
                    public void menu() {
                        app.showMainMenu();
                    }
                }));
        app.applyScale();
    }
}
