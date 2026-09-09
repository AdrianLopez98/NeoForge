package forge.neo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.match.TableBinder;
import forge.neo.ui.CombatOverlay;
import forge.neo.ui.MultiBoardPreview;
import forge.neo.ui.PlayerBar;
import forge.neo.ui.TableScreen;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.util.Duration;

/**
 * Todo lo que solo sirve para comprobar la interfaz sin jugar: las
 * maquetas ({@code --mock-*}), los disparadores de captura, y los ayudantes
 * de arrastre/click sintetico ({@code --drag-test}, {@code --autopilot}...).
 *
 * <p>Nace de la auditoría del motor, apartado E2: {@code NeoApp.java} habia crecido a casi 4.900
 * lineas, y este bloque el solo eran mas de 1.300 -- ni una de ellas
 * alcanzable desde el juego de verdad, solo desde las banderas de linea de
 * comandos que usan las capturas y los comprobadores. Movido tal cual,
 * metodo por metodo, sin tocar la logica de ninguno: los diez comprobadores
 * y las capturas tienen que verse exactamente igual que antes.
 *
 * <p>Necesita la mesa, la escena y algun que otro campo de {@link NeoApp} —
 * de ahi el {@code app} que llevan todos los metodos no estaticos. Los
 * puramente estaticos (calculo de posiciones, sintetizar un click...) se
 * quedan sueltos porque no tocan nada de la aplicacion.
 */
final class NeoAppDebug {

    private final NeoApp app;

    NeoAppDebug(final NeoApp app) {
        this.app = app;
    }

    /**
     * El parpadeo del hover, que en una captura no se ve.
     *
     * <p>Reportado jugando en el draft: <i>"pongo el raton encima de una carta
     * y parpadea muchas veces"</i>. La causa esta explicada en
     * {@code CardNode.leaving()}: la carta se levanta y <b>se quita de debajo
     * del raton ella sola</b>, sale el {@code MOUSE_EXITED}, vuelve, y otra
     * vez. Una imagen no lo cuenta y el comprobador sin ventana tampoco: hay
     * que poner el puntero de VERDAD en el borde de abajo de una carta y contar
     * cuantas veces entra.
     *
     * <p>Con el raton quieto, lo correcto es <b>una</b> entrada. Dos o mas es
     * el fallo.
     *
     * <p>Mueve el puntero del sistema: es la unica forma de que JavaFX genere
     * un {@code MOUSE_ENTERED} de verdad. Por eso va detras de una bandera.
     */
    void hoverTest() {
        final PauseTransition t = new PauseTransition(Duration.millis(
                Long.getLong("neo.hover.testAt", 2500L)));
        t.setOnFinished(e -> {
            final List<CardNode> all = new ArrayList<>();
            collectCardNodes(app.scene.getRoot(), all);
            final int which = Integer.getInteger("neo.hover.index", 0);
            if (all.isEmpty() || which >= all.size()) {
                System.out.println("[hover] no hay carta " + which + " en pantalla");
                return;
            }
            final CardNode card = all.get(which);
            final int[] entradas = {0};
            card.hoverProperty().addListener((o, was, is) -> {
                if (is) {
                    entradas[0]++;
                }
            });

            // El borde de abajo, que es donde se cae: unos pocos pixeles por
            // encima del canto. Justo la franja que el hover descubre.
            final javafx.geometry.Bounds b = card.localToScreen(card.getBoundsInLocal());
            final double x = (b.getMinX() + b.getMaxX()) / 2;
            final double y = b.getMaxY() - 3;
            System.out.printf(java.util.Locale.ROOT,
                    "[hover] puntero en el borde de abajo de %s (%.0f, %.0f)%n",
                    card.getCard() == null ? "?" : card.getCard(), x, y);
            // La ventana, delante: el puntero lo recibe quien esta arriba, y
            // con la consola por encima la prueba salia con cero entradas — o
            // sea, "no parpadea" por no haber llegado a pasar nada.
            if (app.stage != null) {
                app.stage.toFront();
                app.stage.requestFocus();
            }
            new javafx.scene.robot.Robot().mouseMove(x, y);

            final PauseTransition cuenta = new PauseTransition(Duration.millis(1500));
            cuenta.setOnFinished(x2 -> {
                System.out.printf(java.util.Locale.ROOT,
                        "[hover] entradas con el raton quieto: %d -> %s%n",
                        entradas[0], entradas[0] <= 1 ? "OK" : "PARPADEA");
                if (app.animSnapshot != null) {
                    app.animSnapshot.run();
                }
            });
            cuenta.play();
        });
        t.play();
    }
    /**
     * Comprueba las animaciones de la mesa sin necesidad de una partida.
     *
     * <p>Una animacion dura 230 ms: no se puede verificar con una captura a
     * secas, hay que disparar el cambio y capturar EN MITAD. Esto quita un
     * permanente (fantasma que se desvanece), mete otro (entrada), gira una
     * carta y cambia las dos vidas; la captura sale a los 110 ms, o sea a
     * mitad de recorrido.
     */
    void animTest(final Deck deck) {
        final List<PaperCard> permanents = pickPermanents(deck, 14);
        // Abajo se va una carta (fantasma que se desvanece); arriba entra otra.
        final List<CardView> selfBefore = views(slice(permanents, 0, 7));
        final List<CardView> selfAfter = new java.util.ArrayList<>(selfBefore.subList(0, 6));
        final List<CardView> oppBefore = views(slice(permanents, 7, 11));
        final List<CardView> oppAfter = views(slice(permanents, 7, 12));

        app.table.setSelfBattlefield(selfBefore);
        app.table.setOpponentBattlefield(oppBefore);
        app.table.getSelfBar().setLife(40);
        app.table.getOpponentBar().setLife(38);

        final PauseTransition go = new PauseTransition(Duration.seconds(
                Double.parseDouble(System.getProperty("neo.anim.at", "2"))));
        go.setOnFinished(e -> {
            System.out.println("[anim] cambio disparado");
            app.table.setSelfBattlefield(selfAfter);       // una menos: se muere
            app.table.setOpponentBattlefield(oppAfter);    // una mas: entra
            app.table.getSelfBar().setLife(33);            // baja: rojo
            app.table.getOpponentBar().setLife(41);        // sube: verde
            for (final forge.neo.card.CardNode n : app.table.selfFieldNodes()) {
                n.setTapped(!n.isTapped(), true);
                break;
            }
            // Y el destello de dano y el golpe de contadores, que es lo que
            // cambia DENTRO de una carta que sigue donde estaba. Sin animar,
            // eso la mesa lo repinta en silencio.
            final List<forge.neo.card.CardNode> opp = app.table.opponentFieldNodes();
            for (int i = 0; i < opp.size() && i < 2; i++) {
                if (opp.get(i).getCard() != null) {
                    app.table.flashHit(opp.get(i).getCard());
                }
            }
            if (!opp.isEmpty() && opp.get(opp.size() - 1).getCard() != null) {
                app.table.bumpCard(opp.get(opp.size() - 1).getCard());
            }
            if (app.animSnapshot != null) {
                final PauseTransition shot = new PauseTransition(Duration.millis(110));
                shot.setOnFinished(x -> app.animSnapshot.run());
                shot.play();
            }
        });
        go.play();
    }

    /** Reparte cartas del mazo por las zonas. Solo para ver la mesa sin partida. */
    void fillMockTable(final Deck deck, final boolean wide) {
        final int oppCount = wide ? 14 : 6;
        final int selfCount = wide ? 20 : 7;
        final List<PaperCard> permanents = pickPermanents(deck, oppCount + selfCount);
        // --mock-hand=N: una mano sin limite. 30 cartas es el caso real que
        // hay que aguantar (Reliquary Tower y compania) y el que rompia el
        // abanico: no cabian, no encogian, y se salian por los dos lados.
        final List<PaperCard> anyCard = pickCards(deck, Integer.getInteger("neo.mock.hand", 7));
        int i = 0;
        app.table.setOpponentBattlefield(views(slice(permanents, i, i += oppCount)));
        app.table.setSelfBattlefield(views(slice(permanents, i, i + selfCount)));
        app.table.setHand(views(anyCard));

        app.table.setOpponentZonePiles(3, 0);
        app.table.setSelfZonePiles(2, 1);

        app.table.getOpponentBar().setPlayerName("IA-1");
        app.table.getOpponentBar().setLife(38);
        app.table.getOpponentBar().setZones(5, 84, 3, 0, 1);
        app.table.getOpponentBar().setMana(0, 0, 2, 0, 0, 1);

        app.table.getSelfBar().setPlayerName(deck.getName());
        app.table.getSelfBar().setLife(40);
        app.table.getSelfBar().setZones(7, 86, 2, 1, 1);
        app.table.getSelfBar().setMana(0, 0, 3, 0, 0, 2);
        app.table.getSelfBar().setActiveTurn(true);

        app.table.setPhase(PhaseType.MAIN1);
        // La pastilla de dano de comandante, para ver los tres colores sin
        // tener que jugar hasta que alguien conecte veinte veces.
        final int fakeDamage = Integer.getInteger("neo.cmddmg", 0);
        if (fakeDamage > 0) {
            app.table.getSelfBar().setCommanderDamage(fakeDamage);
            app.table.getOpponentBar().setCommanderDamage(Math.max(0, fakeDamage - 7));
        }
        app.table.setPrompt("Maqueta sin partida. Usa --live para jugar de verdad.");

        // Enseñar ya una carta en el panel: sirve para ver el detalle sin
        // tener que pasar el raton, util al comprobar capturas.
        if (!anyCard.isEmpty()) {
            app.table.showDetail(CardView.getCardForUi(anyCard.get(anyCard.size() - 1)));
        }
    }

    /**
     * La zona de mando llena de efectos repetidos.
     *
     * <p>Es el caso que se reporto jugando y que NO se puede montar a voluntad
     * en una partida: hace falta un Potenciador veloz, muchas fichas y un turno
     * entero. El motor deja ahi un objeto de EFECTO por cada disparo, y once de
     * esos en fila se llevaban todo el ancho y empujaban <b>la mano fuera de la
     * pantalla</b>.
     *
     * <p>Se monta con {@code getCardForUi}, que ademas es la trampa de
     * las trampas conocidas: esas vistas comparten un id negativo, asi que agrupar por id
     * sacaria un "xN" hasta con cartas distintas. Aqui se agrupa por lo que se
     * ve — nombre y texto — y el comandante no se agrupa nunca.
     */
    void mockCommandZone(final Deck deck, final int count) {
        // -Dneo.mock.commandKinds=N anyade N efectos DISTINTOS, que no se
        // agrupan: es el otro camino, el del solapamiento.
        final int kinds = Integer.getInteger("neo.mock.commandKinds", 0);
        final List<PaperCard> some = pickCards(deck, 2 + kinds);
        if (some.isEmpty()) {
            return;
        }
        final List<CardView> command = new ArrayList<>();
        command.add(CardView.getCardForUi(some.get(0)));
        final PaperCard effect = some.get(1 % some.size());
        for (int i = 0; i < count; i++) {
            command.add(CardView.getCardForUi(effect));
        }
        for (int i = 0; i < kinds && 2 + i < some.size(); i++) {
            command.add(CardView.getCardForUi(some.get(2 + i)));
        }
        app.table.setCommandZone(command, null);
        app.table.setPrompt("Maqueta: comandante + " + count + " efectos identicos + "
                + kinds + " distintos en la zona de mando (--mock-command)");
    }

    /**
     * Un stack con varios objetos encima, para poder mirarlo.
     *
     * <p>Es el caso que el jugador reporto — <i>"cuando se concatenan muchos no
     * es bien legible"</i> — y el que NO se puede capturar de una partida real:
     * un stack de cinco dura lo que tarda en resolverse el primero, asi que
     * cazarlo con {@code --on-stack} es cuestion de suerte. Aqui se monta a
     * mano.
     *
     * <p>Se puede porque {@code StackItemView} tiene constructor publico
     * {@code (id, tracker)} y {@code set(TrackableProperty, ...)} es publico.
     * No se toca nada del motor: se rellenan las mismas propiedades que
     * rellenaria una partida.
     */
    void mockStack(final int count) {
        final List<forge.neo.card.CardNode> nodes = app.table.selfFieldNodes();
        if (nodes.isEmpty()) {
            return;
        }
        final forge.trackable.Tracker tracker = new forge.trackable.Tracker();
        final List<forge.game.spellability.StackItemView> items = new ArrayList<>();

        // Dos jugadores de mentira: sin ellos la columna del "quien" sale
        // vacia y no se comprueba justo lo que se ha anyadido.
        final forge.game.player.PlayerView me =
                new forge.game.player.PlayerView(8001, tracker);
        me.set(forge.trackable.TrackableProperty.LobbyPlayerName, "cloud");
        final forge.game.player.PlayerView rival =
                new forge.game.player.PlayerView(8002, tracker);
        rival.set(forge.trackable.TrackableProperty.LobbyPlayerName, "IA-1");
        for (int i = 0; i < count; i++) {
            final CardView card = nodes.get(i % nodes.size()).getCard();
            final forge.game.spellability.StackItemView item =
                    new forge.game.spellability.StackItemView(9000 + i, tracker);
            item.set(forge.trackable.TrackableProperty.SourceCard, card);
            item.set(forge.trackable.TrackableProperty.Description,
                    i % 2 == 0
                            ? "Destruye la criatura objetivo. Su controlador pierde 2 vidas."
                            : "Gana 3 vidas y roba una carta.");
            item.set(forge.trackable.TrackableProperty.ActivatingPlayer, i % 2 == 0 ? me : rival);
            // SourceTrigger > 0 es lo que hace que el motor lo considere disparo.
            final boolean trigger = i % 3 == 0;
            item.set(forge.trackable.TrackableProperty.SourceTrigger, trigger ? 1 : -1);
            // Uno de cada dos disparos, opcional: para ver el "(Opcional)" al
            // lado de uno normal en la misma captura (D6 de la auditoría del motor).
            item.set(forge.trackable.TrackableProperty.OptionalTrigger, trigger && i % 6 == 0);
            items.add(item);
        }
        app.table.setStack(items, me);
        app.table.requestLayout();

        // -Dneo.stack.open=true despliega las entradas POR EL CAMINO DE VERDAD
        // (un click en la cabecera, que es el que abre todas). Hace falta
        // porque el gesto que se viene a comprobar es justo un click, y en una
        // captura sin ventana no hay raton que lo de.
        if (Boolean.getBoolean("neo.stack.open")) {
            javafx.application.Platform.runLater(this::openWholeStack);
        }
    }

    /** Baja el visor de los Ajustes, que es mas largo que la ventana. */
    void scrollSettings(final double v) {
        app.table.applyCss();
        app.table.layout();
        final javafx.scene.Node n = app.table.lookup(".settings .dialog-scroll");
        if (n instanceof javafx.scene.control.ScrollPane sp) {
            sp.setVvalue(Math.max(0, Math.min(1, v)));
        } else {
            System.out.println("[maqueta] no se encuentra el visor de los ajustes");
        }
    }

    /** Click en la primera pastilla de palabra clave de la carta ampliada. */
    void clickFirstKeyword() {
        app.table.applyCss();
        app.table.layout();
        final javafx.scene.Node chip = app.table.lookup(".keyword-chip");
        if (chip == null) {
            System.out.println("[maqueta] esta carta no tiene palabras clave");
            return;
        }
        final javafx.geometry.Bounds b = chip.localToScene(chip.getBoundsInLocal());
        fire(chip, javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                new javafx.geometry.Point2D(b.getMinX() + b.getWidth() / 2,
                        b.getMinY() + b.getHeight() / 2));
    }

    /** Click en la cabecera del stack: abre o cierra todas las entradas. */
    private void openWholeStack() {
        app.table.applyCss();
        app.table.layout();
        final javafx.scene.Node header = app.table.lookup(".stack-header");
        if (header == null) {
            System.out.println("[maqueta] no hay cabecera de stack que clicar");
            return;
        }
        final javafx.geometry.Bounds b = header.localToScene(header.getBoundsInLocal());
        final javafx.geometry.Point2D at =
                new javafx.geometry.Point2D(b.getMinX() + 4, b.getMinY() + b.getHeight() / 2);
        fire(header, javafx.scene.input.MouseEvent.MOUSE_CLICKED, at);
    }

    /**
     * Mensajes del motor de mentira, tal y como los escribe de verdad.
     *
     * <p>Son las frases exactas que componen {@code PhaseHandler} y
     * {@code CombatUtil.validateBlocks}: la gracia de la prueba es que el
     * texto sea el mismo que llegara jugando, con su etiqueta de carta y su
     * numero incluidos.
     */
    static String cannedWhy(final String key) {
        switch (key) {
            case "alone":
                return "Osito (12) can't block alone.";
            case "two":
                return "Osito (12) can't block unless at least two other creatures block.";
            case "stronger":
                return "Osito (12) can't block unless a creature with greater power also blocks.";
            case "each":
                return "Osito (12) must block each combat but was not assigned to block any attacker now.";
            case "must":
                return "Osito (12) must block an attacker, but has not been assigned to block any.";
            case "still":
                return "Osito (12) must still block Bestia colosal (34).";
            case "count":
                return "Bestia colosal (34) cannot be blocked with 1 creatures you've assigned";
            case "raro":
                return "Osito (12) does something we do not know about";
            default:
                return "Attack declaration invalid";
        }
    }

    /**
     * La fila de atras con MUCHAS tierras y UN solo encantamiento.
     *
     * <p>Es el caso que salio jugando y el que rompia el reparto de ancho: el
     * grupo de los no-tierra se llevaba 1/11 de la fila — menos de lo que mide
     * una carta — y el encantamiento salia cortado por el recorte de la zona.
     * Con una maqueta cualquiera no se reproduce, porque depende de la
     * PROPORCION entre los dos grupos.
     */
    void fillCrowdedRow(final Deck deck) {
        final List<PaperCard> lands =
                pickByType(deck, Integer.getInteger("neo.crowded.lands", 10), true);
        final List<PaperCard> other =
                pickByType(deck, Integer.getInteger("neo.crowded.other", 1), false);
        final List<PaperCard> all = new ArrayList<>(lands);
        all.addAll(other);
        app.table.setSelfBattlefield(views(all));
        app.table.setPrompt(String.format(
                "Maqueta: %d tierras + %d permanente (--mock-crowded)",
                lands.size(), other.size()));
        System.out.printf("[crowded] %d tierras + %d no-tierra%n", lands.size(), other.size());
    }

    /**
     * Maqueta: un aura del RIVAL sobre una criatura MIA.
     *
     * <p>Reproduce el fallo que se reporto jugando — el aula enemiga se pintaba
     * en la fila de encantamientos del rival, como si no hubiera encantado nada
     * — y no se puede provocar a voluntad en una partida de verdad. Las vistas
     * se fabrican, que es lo mismo que hace {@code CombatCheck} con el combate:
     * {@code CardView} tiene constructor publico y {@code set(...)} tambien.
     *
     * <p>Bien pintado, el aura sale <b>debajo de mi criatura</b> y NO ocupa
     * hueco en la fila del rival.
     */
    void mockEnemyAura() {
        final forge.trackable.Tracker t = new forge.trackable.Tracker();
        // Con cartas de VERDAD: sin imagen no se ve donde cae la pastilla de
        // P/T ni si lo enganchado tapa algo, que es justo lo que hay que mirar.
        final CardView mine = mockCard(t, 9001, "E. Honda, Sumo Champion",
                "Legendary Creature Human Warrior", 0, 7);
        final CardView theirs = mockCard(t, 9002, "Colossal Dreadmaw",
                "Creature Dinosaur", 6, 6);
        final CardView aura = mockCard(t, 9003, "Darksteel Mutation",
                "Enchantment Aura", 0, 0);
        final CardView gear = mockCard(t, 9005, "Sword of Feast and Famine",
                "Artifact Equipment", 0, 0);
        final CardView theirEnchantment =
                mockCard(t, 9004, "Ghostly Prison", "Enchantment", 0, 0);

        // Las dos direcciones, que es como las publica el motor.
        aura.set(forge.trackable.TrackableProperty.EntityAttachedTo, mine);
        gear.set(forge.trackable.TrackableProperty.EntityAttachedTo, mine);
        mine.set(forge.trackable.TrackableProperty.AttachedCards,
                new forge.trackable.TrackableCollection<>(List.of(aura, gear)));

        // Y el caso entero del que sale todo esto: la P/T de la mesa no es la
        // impresa ni de lejos, porque encima hay contadores.
        mine.getCurrentState().set(forge.trackable.TrackableProperty.Power, 44);
        mine.getCurrentState().set(forge.trackable.TrackableProperty.Toughness, 46);
        final com.google.common.collect.Multiset<forge.game.card.CounterType> counters =
                com.google.common.collect.HashMultiset.create();
        counters.add(forge.game.card.CounterEnumType.P1P1, 44);
        mine.set(forge.trackable.TrackableProperty.Counters, counters);

        // Con tierras detras: es donde asoma lo enganchado, y es el reparto
        // que hay que mirar. Sin ellas la maqueta ensenya un caso mas facil que
        // el de la partida.
        final List<CardView> myBoard = new ArrayList<>(List.of(mine));
        int landId = 9200;
        for (final String land : List.of("Forest", "Island", "Mountain", "Plains")) {
            myBoard.add(mockCard(t, landId++, land, "Basic Land", 0, 0));
        }
        app.table.setSelfBattlefield(myBoard);
        app.table.setOpponentBattlefield(List.of(theirs, aura, theirEnchantment, gear));
        app.table.setPrompt("Maqueta: un aura del rival y un equipo tuyo, encima de tu criatura"
                + " (--mock-aura). Click derecho encima para ver su estado.");
        // El panel de detalle que deja puesto la maqueta general tapa justo lo
        // que hay que mirar.
        app.table.showDetail(null);
        System.out.println("[aura] mia=" + mine.getName()
                + " | enganchado a ella: " + mine.getAttachedCards()
                + " | el aura dice estar en: " + aura.getAttachedTo());
        for (final CardNode n : app.table.selfFieldNodes()) {
            System.out.println("[aura] nodo en la mesa: "
                    + (n.getCard() == null ? "?" : n.getCard().getName()));
        }
    }

    /**
     * Una carta de mentira que ademas SE VE: con zona, tipo y P/T.
     *
     * <p>La maqueta del aura se hacia con {@code new CardView(id, t, nombre)} a
     * secas, y eso deja una carta sin zona y sin tipos: ni sale la P/T, ni los
     * contadores (que solo se pintan en la mesa), ni la ficha de estado de la
     * carta ampliada. O sea que la maqueta no podia ensenyar el fallo que se
     * estaba arreglando.
     */
    static CardView mockCard(final forge.trackable.Tracker t, final int id,
                                     final String name, final String type,
                                     final int power, final int toughness) {
        final CardView cv = new CardView(id, t, name);
        cv.set(forge.trackable.TrackableProperty.Zone, forge.game.zone.ZoneType.Battlefield);
        final CardView.CardStateView st = cv.getCurrentState();
        st.set(forge.trackable.TrackableProperty.Type,
                forge.card.CardType.parse(type, false));
        st.set(forge.trackable.TrackableProperty.Power, power);
        st.set(forge.trackable.TrackableProperty.Toughness, toughness);
        // Si el nombre es el de una carta de verdad, se le pone su imagen y su
        // texto. No es cosmetico: la P/T solo se pinta cuando hay arte.
        final PaperCard pc = forge.model.FModel.getMagicDb().getCommonCards().getCard(name);
        if (pc != null) {
            st.set(forge.trackable.TrackableProperty.ImageKey, pc.getImageKey(false));
            st.set(forge.trackable.TrackableProperty.OracleText,
                    pc.getRules().getOracleText());
        }
        return cv;
    }

    /**
     * Maqueta: <b>elegir una de dos pilas</b> (Fortune's Favor y compania).
     *
     * <p>Se monta EXACTAMENTE como lo monta el motor en
     * {@code PlayerControllerHuman.chooseCardsPile}, que es lo que hace util
     * esta maqueta: dos {@code CardView} falsos que hacen de <b>etiqueta</b> de
     * cada pila ({@code new CardView(Integer.MIN_VALUE, null, "-- Pile 1 ...")},
     * sin tracker y sin imagen) y, detras, las cartas de la pila que va
     * <b>boca arriba</b>. La otra pila no se manda: esa parte el motor la hace
     * bien y la informacion oculta no se filtra.
     */
    void mockPiles(final List<String> args) {
        // Las tres formas que tiene el motor de preguntar esto:
        //   una      FaceDown$ One  -- 7 cartas (Fortune's Favor, Atris...)
        //   ambas    FaceDown$ True -- Phyrexian Portal: no ves ninguna
        //   ninguna  sin FaceDown   -- las otras 24: se ven las dos pilas
        final String kind = NeoApp.orDefault(NeoApp.optionOf(args, "--mock-piles"), "una");
        final boolean hide1 = !"ninguna".equals(kind);
        final boolean hide2 = "ambas".equals(kind);

        final List<CardView> cards = new ArrayList<>();
        final List<CardView> pool = new ArrayList<>();
        for (final forge.neo.card.CardNode n : app.table.selfFieldNodes()) {
            if (n.getCard() != null && pool.size() < 4) {
                pool.add(n.getCard());
            }
        }
        // Las etiquetas, tal cual las escribe el motor (sin traducir).
        cards.add(new CardView(Integer.MIN_VALUE, null, "-- Pile 1 (2 cards) --"));
        if (!hide1) {
            cards.addAll(pool.subList(0, Math.min(2, pool.size())));
        }
        cards.add(new CardView(Integer.MIN_VALUE + 1, null, "-- Pile 2 (2 cards) --"));
        if (!hide2) {
            cards.addAll(pool.subList(Math.min(2, pool.size()), Math.min(4, pool.size())));
        }
        final int shown = cards.size() - 2;
        System.out.printf("[maqueta] elegir pila: %d opciones (2 etiquetas + %d cartas)%n",
                cards.size(), shown);
        System.out.println("[maqueta] reconocida como eleccion de pilas: "
                + forge.neo.ui.PileDialog.looksLikePiles(cards));
        app.table.getOverlay().show(new forge.neo.ui.PileDialog(
                "Elige una pila", cards, 132,
                picked -> {
                    app.table.getOverlay().hide();
                    System.out.println("[maqueta] elegido: "
                            + (picked == null ? "(nada)" : picked.getName()));
                }));
    }

    /**
     * Maqueta: el brillo de una carta foil, al lado de la misma sin foil
     * (la auditoría del motor, apartado D5). Sin esto no hay forma de comprobar el brillo sin
     * esperar a que un sobre real saque una foil de verdad (~1 de cada 5).
     */
    void mockFoil(final List<String> args) {
        final String name = NeoApp.orDefault(NeoApp.optionOf(args, "--mock-foil"), "Atraxa, Grand Unifier");
        final forge.item.PaperCard base =
                forge.model.FModel.getMagicDb().getCommonCards().getCard(name);
        if (base == null) {
            System.out.println("[maqueta] no existe la carta de muestra");
            return;
        }
        final forge.neo.card.CardNode normal = new forge.neo.card.CardNode(app.table.zoomCardWidth());
        normal.setRotationEnabled(false);
        normal.setCard(forge.game.card.CardView.getCardForUi(base));
        final forge.neo.card.CardNode foil = new forge.neo.card.CardNode(app.table.zoomCardWidth());
        foil.setRotationEnabled(false);
        foil.setCard(forge.game.card.CardView.getCardForUi(base.getFoiled()));
        System.out.println("[maqueta] normal.hasPaperFoil=false, foil.hasPaperFoil="
                + forge.game.card.CardView.getCardForUi(base.getFoiled()).hasPaperFoil());
        final javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(24, normal, foil);
        row.setAlignment(javafx.geometry.Pos.CENTER);
        row.setPadding(new javafx.geometry.Insets(30));
        row.getStyleClass().add("dialog");
        app.table.getOverlay().show(row);
    }

    /**
     * Maqueta: las reliquias del RIVAL en su barra, como en un duelo de jefe.
     *
     * <p>Provocarlo jugando exige llegar a un jefe de Ascenso, o sea una run a
     * medias y diez minutos. Y es justo lo que hay que poder mirar: del rival
     * no se ve la zona de mando, solo su contador, asi que sin estas pastillas
     * sus reliquias son invisibles (reportado jugando contra el jefe del acto
     * 1: <i>"necesito poder ver sus reliquias, esa data es muy vital"</i>).
     *
     * <p>{@code -Dneo.mock.bossRelics=N} cuantas (2 es lo que lleva un jefe;
     * 3 con la Ascension 10, que anyade el segundo aliento).
     */
    void mockBossRelics() {
        forge.neo.ascent.AscentRelics.install();
        final int howMany = Math.max(1, Integer.getInteger("neo.mock.bossRelics", 2));
        final java.util.List<forge.game.card.CardView> cards = new java.util.ArrayList<>();
        for (final forge.neo.ascent.AscentRelic relic : forge.neo.ascent.AscentRelics.all()) {
            if (cards.size() >= howMany) {
                break;
            }
            final forge.item.PaperCard card = forge.neo.ascent.AscentRelics.cardOf(relic);
            if (card != null) {
                cards.add(forge.game.card.CardView.getCardForUi(card));
                System.out.println("[maqueta] reliquia del rival: " + relic.getCardName()
                        + " (" + relic.getRarity() + ")");
            }
        }
        app.table.getOpponentBar().setRelics(cards);
    }

    /**
     * Maqueta: <b>las 37 reliquias de Ascenso a la vez</b>, con su cara
     * dibujada ({@code RelicArt}).
     *
     * <p>Hace falta bandera propia porque no hay ninguna forma razonable de
     * ver mas de tres juntas: los premios ofrecen una (tres si es un jefe) y
     * salen sorteadas, asi que comprobar los 27 emblemas jugando serian
     * decenas de runs. Aqui se ven todos de golpe y se captura.
     *
     * <p>Sale del <b>catalogo de verdad</b> ({@code AscentRelics.install()}),
     * no de una lista escrita aqui: una reliquia nueva aparece sola, y si
     * alguien anyade una sin darle emblema se ve en el acto — sale con el
     * generico.
     *
     * <p>{@code -Dneo.relics.small=true} las pinta pequenyas, que es como se
     * ven en la fila de premios: ahi la caja de texto se esconde a proposito
     * (ver {@code RelicArt}), y eso tambien hay que poder mirarlo.
     */
    void mockRelics() {
        final int n = forge.neo.ascent.AscentRelics.install();
        final boolean small = Boolean.getBoolean("neo.relics.small");
        final double w = app.table.zoomCardWidth() * (small ? 0.18 : 0.52);
        final javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane(10, 10);
        grid.setAlignment(javafx.geometry.Pos.CENTER);
        for (final forge.neo.ascent.AscentRelic relic : forge.neo.ascent.AscentRelics.all()) {
            final forge.item.PaperCard card = forge.neo.ascent.AscentRelics.cardOf(relic);
            if (card == null) {
                System.out.println("[maqueta] sin carta: " + relic.getId());
                continue;
            }
            final forge.neo.card.CardNode node = new forge.neo.card.CardNode(w);
            node.setRotationEnabled(false);
            node.setCard(forge.game.card.CardView.getCardForUi(card));
            grid.getChildren().add(node);
            System.out.println("[maqueta] " + relic.getId() + " -> "
                    + forge.neo.card.RelicEmblem.motifOf(relic.getId())
                    + " (" + relic.getRarity() + ")");
        }
        System.out.println("[maqueta] " + n + " reliquias registradas");
        final javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog");
        // Sin techo de alto el ScrollPane crece con su contenido y las de
        // abajo se salen de la ventana: es la trampa de siempre con las
        // rejillas grandes dentro de un overlay.
        scroll.setPrefViewportHeight(app.table.getHeight() * 0.86);
        app.table.getOverlay().show(scroll);
    }

    /**
     * Maqueta: el banquillo entre partida y partida de un Bo3 (draft/sellado
     * con banquillo — la auditoría del motor, apartado C5).
     *
     * <p>A proposito con <b>copias repetidas</b> (dos Bosque, dos Piedra
     * del Sol): es el caso que rompia el {@code ChoiceDialog} generico antes
     * de esta sesion — un {@code Set} por VALOR confundia dos cartas iguales
     * y marcar una desmarcaba las dos. Verlo aqui sin tener que jugar un Bo3
     * entero hasta la segunda partida.
     */
    void mockSideboard() {
        final forge.item.PaperCard forest =
                forge.model.FModel.getMagicDb().getCommonCards().getCard("Forest");
        final forge.item.PaperCard solRing =
                forge.model.FModel.getMagicDb().getCommonCards().getCard("Sol Ring");
        final forge.item.PaperCard giant =
                forge.model.FModel.getMagicDb().getCommonCards().getCard("Runeclaw Bear");
        final forge.item.PaperCard bolt =
                forge.model.FModel.getMagicDb().getCommonCards().getCard("Lightning Bolt");
        final List<forge.item.PaperCard> current = new ArrayList<>();
        for (final forge.item.PaperCard c : List.of(forest, forest, solRing, giant)) {
            if (c != null) {
                current.add(c);
            }
        }
        final List<forge.item.PaperCard> pool = new ArrayList<>(current);
        for (final forge.item.PaperCard c : List.of(forest, solRing, bolt)) {
            if (c != null) {
                pool.add(c);
            }
        }
        if (current.isEmpty() || pool.size() <= current.size()) {
            System.out.println("[maqueta] faltan cartas de catalogo para el banquillo");
            return;
        }
        final int size = current.size();
        app.table.getOverlay().show(new forge.neo.ui.ChoiceDialog<>(
                forge.neo.NeoText.get("sideboard.title"), pool, size, size,
                forge.item.PaperCard::getName, app.table.zoomCardWidth() * 0.62, current,
                picked -> {
                    app.table.getOverlay().hide();
                    System.out.println("[maqueta] mazo elegido: " + picked);
                }));
    }

    /**
     * Maqueta: una carta de DOS caras, ampliada.
     *
     * <p>Para comprobar que la cara de atras se ve. Por defecto una de dos
     * caras modal, que es donde mas duele no verla: decidir si la lanzas por
     * la cara de tierra sin poder mirar la cara de tierra.
     */
    void mockFace(final List<String> args) {
        final String name = NeoApp.orDefault(NeoApp.optionOf(args, "--mock-face"), "Malakir Rebirth");
        final PaperCard pc = forge.model.FModel.getMagicDb().getCommonCards().getCard(name);
        if (pc == null) {
            System.out.println("[maqueta] no existe la carta: " + name);
            return;
        }
        final CardView cv = CardView.getCardForUi(pc);
        System.out.printf("[maqueta] %s | otra cara: %s | partida: %s%n",
                name, cv.hasAlternateState()
                        ? cv.getAlternateState().getName() : "(ninguna)",
                cv.isSplitCard());
        forge.neo.ui.CardZoom.show(app.table, cv);
    }

    /**
     * Maqueta: elegir una carta DENTRO del cementerio.
     *
     * <p>Es el camino que no existia: {@code InputSelectTargets} marca cartas
     * que estan en el cementerio y espera un click sobre ellas. Aqui se abre el
     * visor con una marcada para comprobar que se ve elegible y que el click
     * llega.
     */
    void mockZonePick() {
        final forge.trackable.Tracker t = new forge.trackable.Tracker();
        final CardView pickable = new CardView(9101, t, "Criatura elegible");
        final CardView other = new CardView(9102, t, "Otra carta");
        final forge.game.player.PlayerView me = new forge.game.player.PlayerView(9100, t);
        me.set(forge.trackable.TrackableProperty.LobbyPlayerName, "Ana");
        me.set(forge.trackable.TrackableProperty.Graveyard,
                new forge.trackable.TrackableCollection<>(List.of(pickable, other)));

        app.table.setSelectable(c -> c != null && c.getId() == 9101);
        app.table.setOnCardClicked(c ->
                System.out.println("[zone-pick] clicada: " + (c == null ? "?" : c.getName())));
        app.table.setPrompt("Maqueta: elige una criatura de tu cementerio (--mock-zone-pick)");
        app.table.showZone(me, forge.game.zone.ZoneType.Graveyard);
    }

    /** Tierras (o permanentes que no son ni tierra ni criatura) del mazo. */
    static List<PaperCard> pickByType(final Deck deck, final int max,
                                              final boolean wantLands) {
        final List<PaperCard> out = new ArrayList<>();
        for (final var e : deck.getMain()) {
            final var type = e.getKey().getRules().getType();
            final boolean ok = wantLands ? type.isLand()
                    : !type.isLand() && !type.isCreature()
                            && (type.isEnchantment() || type.isArtifact()
                                    || type.isPlaneswalker());
            if (ok) {
                out.add(e.getKey());
            }
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    /**
     * Flechas de combate de mentira, sobre la maqueta.
     *
     * <p>Sirve para comprobar el dibujo de las flechas sin tener que esperar a
     * que en una partida real se declare un ataque. No hay combate detras: se
     * le pasan a la mesa las mismas tripletas que le pasaria el motor.
     */
    void mockCombat() {
        final List<CardView> mine = app.table.selfCreatureViews();
        final List<CardView> theirs = app.table.opponentCreatureViews();
        final List<Object[]> links = new ArrayList<>();

        // Dos de mis criaturas atacan al rival; una de las suyas bloquea a la
        // primera. Es el caso que hay que ver bien: flechas que salen del mismo
        // sitio y una flecha de bloqueo cruzando en sentido contrario.
        final var opponent = app.table.getOpponentBar().getPlayer();
        for (int i = 0; i < Math.min(3, mine.size()); i++) {
            links.add(new Object[] {mine.get(i), opponent, CombatOverlay.Kind.ATTACK});
        }
        if (!theirs.isEmpty() && !mine.isEmpty()) {
            links.add(new Object[] {theirs.get(0), mine.get(0), CombatOverlay.Kind.BLOCK});
        }
        if (theirs.size() > 1 && mine.size() > 1) {
            links.add(new Object[] {mine.get(1), theirs.get(1), CombatOverlay.Kind.TARGET});
        }
        app.table.setCombatLinks(links);
        app.table.setPrompt("Maqueta con flechas de combate (--mock-combat).");
    }

    /**
     * Comprueba que se puede preguntar al jugador DESDE el hilo de interfaz.
     *
     * <p>Es el caso que rompia: un click se atiende en el hilo de JavaFX y
     * desde ahi el motor puede devolvernos la llamada pidiendo que elijamos
     * una habilidad. Aqui se reproduce a proposito — se llama al dialogo
     * estando ya en ese hilo — y se contesta sola a los 2 s pulsando la SEGUNDA
     * opcion. Si vuelve un 1, el bucle de eventos anidado funciona; si vuelve
     * un 0 sin esperar, la decision del jugador se esta perdiendo.
     */
    void nestedDialogTest() {
        final forge.neo.match.NeoMatchUI ui = new forge.neo.match.NeoMatchUI(
                forge.neo.match.NeoMatchUI.Mode.HUMAN, false);
        ui.setTable(app.table);

        final PauseTransition start = new PauseTransition(Duration.seconds(2));
        start.setOnFinished(e -> {
            // A los 2 s de abrirse el dialogo, pulsar "Segunda".
            final PauseTransition answer = new PauseTransition(Duration.seconds(2));
            answer.setOnFinished(e2 -> {
                for (final javafx.scene.Node n : app.scene.getRoot().lookupAll(".btn-secondary")) {
                    if (n instanceof javafx.scene.control.Button b && "Segunda".equals(b.getText())) {
                        System.out.println("[nested-test] pulsando 'Segunda'");
                        b.fire();
                        return;
                    }
                }
                System.out.println("[nested-test] FALLO: el dialogo no se ha pintado");
            });
            answer.play();

            // OJO: hay que preguntar desde la COLA DE EVENTOS, no desde dentro
            // de esta animacion. JavaFX prohibe abrir un bucle anidado durante
            // el pulso de animacion o de layout, y preguntar ahi falsearia la
            // prueba. En la partida real la llamada llega igual: por
            // Platform.runLater, que es lo que usa respondLater.
            Platform.runLater(() -> {
                final long t0 = System.currentTimeMillis();
                final int result = ui.selfTestAskOption();
                final long ms = System.currentTimeMillis() - t0;
                System.out.printf("[nested-test] resultado=%d en %d ms -> %s%n", result, ms,
                        result == 1 ? "OK, el dialogo anidado funciona"
                                : "FALLO, la respuesta se ha perdido");
                Platform.exit();
            });
        });
        start.play();
    }

    /**
     * Comprueba el gesto de arrastrar sin tocar el raton.
     *
     * <p>Arrastrar y soltar es lo unico de la interfaz que no se puede validar
     * con una captura: no se ve en una imagen. Esto sintetiza los eventos de
     * raton que produciria una persona (pulsar sobre una carta de la mano,
     * mover hasta una criatura del rival y soltar) y escribe por consola lo que
     * ha recibido el manejador de soltar.
     *
     * <p>No prueba el motor: prueba el CABLEADO de la mesa, que es justo lo que
     * no se puede mirar.
     */
    void dragTest() {
        // En una partida real el manejador lo pone NeoMatchUI y hay que
        // respetarlo: lo que interesa entonces es que la orden llegue al motor
        // de verdad. La traza se enciende con -Dneo.drag.debug=true.
        if (!app.table.hasDropHandler()) {
            app.table.setOnCardDropped((source, fromHand, target) ->
                    System.out.printf("[drag-test] soltada %s (mano=%s) sobre %s%n",
                            nameOf(source), fromHand, nameOf(target)));
        }

        final PauseTransition wait = new PauseTransition(
                Duration.seconds(Integer.getInteger("neo.dragTest.delay", 2)));
        wait.setOnFinished(e -> {
            final List<javafx.scene.Node> from = new ArrayList<>(app.table.handNodes());
            final List<javafx.scene.Node> to = new ArrayList<>(app.table.opponentFieldNodes());
            if (from.isEmpty() || to.isEmpty()) {
                System.out.println("[drag-test] no hay cartas con las que probar");
                Platform.exit();
                return;
            }
            System.out.println("[drag-test] mano -> campo del rival");
            simulateDrag(from.get(0), centreOf(to.get(0)));

            final List<javafx.scene.Node> mine = new ArrayList<>(app.table.selfFieldNodes());
            if (!mine.isEmpty()) {
                System.out.println("[drag-test] campo propio -> campo del rival");
                simulateDrag(mine.get(0), centreOf(to.get(0)));
                System.out.println("[drag-test] campo propio -> retrato del rival");
                simulateDrag(mine.get(0), centreOf(app.table.getOpponentBar()));
            }

            // Soltar sobre un retrato es el gesto de "ataco a este jugador", y
            // hay que probarlo con una partida viva: en la maqueta las barras no
            // tienen PlayerView y no hay a quien apuntar.
            System.out.println("[drag-test] carta -> retrato propio");
            simulateDrag(to.get(0), centreOf(app.table.getSelfBar()));

            // Arrepentirse: coger una carta de la mano, moverla y devolverla.
            // NO debe jugarse. Antes se jugaba igual, y jugar una tierra no se
            // puede deshacer, asi que el error era definitivo.
            System.out.println("[drag-test] mano -> mano (NO debe soltar nada)");
            simulateDrag(from.get(0), centreOf(from.get(from.size() - 1)));

            // El caso que se colaba: un arrastre "hacia atras" que NO cae
            // exactamente sobre la mano (el rail de fases, la columna de la
            // derecha) tampoco tiene que jugar nada. Antes solo se cancelaba
            // soltando DENTRO del rectangulo de la mano; cualquier otro sitio
            // fuera de la mesa jugaba la carta igual. Reportado jugando.
            System.out.println("[drag-test] mano -> rail de fases (NO debe soltar nada)");
            simulateDrag(from.get(0), centreOf(app.table.getPhaseRail()));

            System.out.println("[drag-test] click corto (no debe contar como arrastre)");
            final javafx.geometry.Point2D p = centreOf(from.get(0));
            fire(from.get(0), javafx.scene.input.MouseEvent.MOUSE_PRESSED, p);
            fire(from.get(0), javafx.scene.input.MouseEvent.MOUSE_RELEASED, p);
            if (!Boolean.getBoolean("neo.dragTest.stay")) {
                Platform.exit();
            }
        });
        wait.play();
    }

    static String nameOf(final Object entity) {
        if (entity instanceof CardView cv && cv.getCurrentState() != null) {
            return cv.getCurrentState().getName();
        }
        if (entity instanceof forge.game.player.PlayerView pv) {
            return "jugador " + forge.neo.match.PlayerName.of(pv);
        }
        return String.valueOf(entity);
    }

    /** La primera pila de zona que responda al click (o sea, que tenga cartas). */
    /** El visor de zona que este abierto, si lo hay. */
    static javafx.scene.Node firstZoneViewer(final javafx.scene.Node root) {
        if (root instanceof forge.neo.ui.ZoneViewer) {
            return root;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (final javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                final javafx.scene.Node found = firstZoneViewer(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** La primera carta con el resaltado de "esto lo puedes usar". */
    static CardNode firstActionableCard(final javafx.scene.Node root) {
        if (root instanceof CardNode card) {
            return card.isActionable() ? card : null;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (final javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                final CardNode found = firstActionableCard(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static javafx.scene.Node firstClickablePile(final javafx.scene.Node root) {
        if (root instanceof forge.neo.ui.ZonePile pile) {
            return pile.getCount() > 0 ? pile : null;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (final javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                final javafx.scene.Node found = firstClickablePile(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** La primera carta del arbol, en orden de pintado. */
    /** Todas las cartas de la pantalla, en orden de arbol. */
    static void collectCardNodes(final javafx.scene.Node root,
                                         final List<CardNode> out) {
        if (root instanceof CardNode cn) {
            if (cn.getCard() != null) {
                out.add(cn);
            }
            return;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (final javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectCardNodes(child, out);
            }
        }
    }

    static CardNode firstCardNode(final javafx.scene.Node root) {
        if (root instanceof CardNode cn) {
            return cn.getCard() == null ? null : cn;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (final javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                final CardNode found = firstCardNode(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static javafx.geometry.Point2D centreOf(final javafx.scene.Node n) {
        final javafx.geometry.Bounds b = n.localToScene(n.getBoundsInLocal());
        return new javafx.geometry.Point2D(
                b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
    }

    /** Pulsar, mover en varios pasos y soltar, como haria una persona. */
    static void simulateDrag(final javafx.scene.Node source,
                                     final javafx.geometry.Point2D end) {
        final javafx.geometry.Point2D start = centreOf(source);
        fire(source, javafx.scene.input.MouseEvent.MOUSE_PRESSED, start);
        for (int i = 1; i <= 4; i++) {
            final double t = i / 4.0;
            fire(source, javafx.scene.input.MouseEvent.MOUSE_DRAGGED,
                    start.add(end.subtract(start).multiply(t)));
        }
        fire(source, javafx.scene.input.MouseEvent.MOUSE_RELEASED, end);
    }

    /** Una vuelta de rueda con Ctrl, como la del raton de verdad. */
    static void scroll(final javafx.scene.Node target,
                               final javafx.geometry.Point2D scenePoint,
                               final double deltaY) {
        javafx.event.Event.fireEvent(target, new javafx.scene.input.ScrollEvent(
                javafx.scene.input.ScrollEvent.SCROLL,
                scenePoint.getX(), scenePoint.getY(),
                scenePoint.getX(), scenePoint.getY(),
                false, true, false, false, true, false,
                0, deltaY, 0, deltaY,
                javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null));
    }

    static void fire(final javafx.scene.Node target,
                             final javafx.event.EventType<javafx.scene.input.MouseEvent> type,
                             final javafx.geometry.Point2D scenePoint) {
        fire(target, type, scenePoint, javafx.scene.input.MouseButton.PRIMARY);
    }

    static void fire(final javafx.scene.Node target,
                             final javafx.event.EventType<javafx.scene.input.MouseEvent> type,
                             final javafx.geometry.Point2D scenePoint,
                             final javafx.scene.input.MouseButton button) {
        final boolean primary = button == javafx.scene.input.MouseButton.PRIMARY;
        final boolean middle = button == javafx.scene.input.MouseButton.MIDDLE;
        javafx.event.Event.fireEvent(target, new javafx.scene.input.MouseEvent(
                type, scenePoint.getX(), scenePoint.getY(),
                scenePoint.getX(), scenePoint.getY(),
                button, 1,
                false, false, false, false,
                primary, middle, !primary && !middle,
                false, false, false, null));
    }

    /**
     * Maqueta del reparto de dano de combate.
     *
     * <p>El dialogo real solo sale cuando una criatura tuya es bloqueada por
     * varias a la vez, que puede tardar muchas partidas en pasar. Esto lo
     * levanta con cartas cualesquiera para poder verlo.
     */
    void mockDamage() {
        final List<CardView> creatures = app.table.selfCreatureViews();
        if (creatures.size() < 3) {
            System.out.println("[maqueta] no hay criaturas suficientes para el reparto de dano");
            return;
        }
        final CardView attacker = creatures.get(0);
        final List<CardView> blockers = creatures.subList(1, Math.min(4, creatures.size()));
        app.table.getOverlay().show(new forge.neo.ui.DamageDialog(
                attacker, blockers, 7, app.table.getOpponentBar().getPlayer(),
                false, true, false, 10, 132,
                map -> app.table.getOverlay().hide()));
    }

    /**
     * El reparto generico: "de que color es el mana que produce esta tierra".
     *
     * <p>Por defecto sale el caso que lo destapo — una tierra de filtro
     * roja-blanca ({@code Combo R W | Amount$ 2}), que antes producia {R}{R}
     * sola. Con {@code --mock-amount=cartas} sale el otro uso del mismo
     * dialogo: repartir contadores entre varias criaturas.
     */
    void mockAmount(final List<String> args) {
        final boolean entities = "cartas".equals(NeoApp.optionOf(args, "--mock-amount"));
        final java.util.Map<Object, Integer> targets = new java.util.LinkedHashMap<>();
        final int amount = entities ? 4 : 2;
        if (entities) {
            // Las cartas hay que FABRICARLAS, no cogerlas de la mesa: las
            // vistas "para interfaz" comparten id (-1), y la igualdad de un
            // TrackableObject es por id, asi que tres criaturas de la maqueta
            // se colapsan en una sola entrada del mapa. En partida real los
            // ids son distintos y el mapa lo monta el motor.
            final forge.trackable.Tracker t = new forge.trackable.Tracker();
            int id = 1;
            for (final CardView c : app.table.selfCreatureViews()) {
                final String name = c.getCurrentState() == null
                        ? "Criatura" : c.getCurrentState().getName();
                final CardView fake = new CardView(id++, t, name);
                fake.getCurrentState().set(
                        forge.trackable.TrackableProperty.ImageKey,
                        c.getCurrentState() == null ? "" : c.getCurrentState().getImageKey());
                targets.put(fake, amount);
                if (targets.size() >= 3) {
                    break;
                }
            }
        } else {
            // El orden es el de ColorSet, que es justo el que ponia el rojo
            // primero y hacia que el juego eligiera por ti.
            targets.put(forge.card.MagicColor.Color.RED, amount);
            targets.put(forge.card.MagicColor.Color.WHITE, amount);
        }
        if (targets.isEmpty()) {
            System.out.println("[maqueta] no hay objetivos para el reparto");
            return;
        }
        final CardView source = app.table.selfFieldNodes().isEmpty()
                ? null : app.table.selfFieldNodes().get(0).getCard();
        app.table.getOverlay().show(new forge.neo.ui.AmountDialog(
                source, targets, amount, entities, entities ? "contadores" : "maná",
                132, map -> app.table.getOverlay().hide()));
    }

    /**
     * Lo que el motor publica y antes no se veia en ninguna parte.
     *
     * <p>Todo junto a proposito: son cosas que en partida salen sueltas y muy
     * de vez en cuando, asi que sin una maqueta no hay forma de mirarlas.
     *
     * <ul>
     *   <li><b>Contadores del jugador</b> — veneno (ya letal), energia y
     *       experiencia. Estaban solo en el detalle del raton.</li>
     *   <li><b>Marcadores sobre la carta</b> — la velocidad de Aetherdrift, el
     *       nivel de una Clase, cuanto te ha tentado el Anillo.</li>
     *   <li><b>Resaltado debil</b> — lo que puedes usar (fuerza 1) y lo que
     *       taparia el boton "Auto" (fuerza 2).</li>
     *   <li><b>Desplazamiento de fase</b> — esta en la mesa pero es como si no
     *       estuviera.</li>
     * </ul>
     */
    /**
     * Maqueta: "sacrifica tres criaturas", con dos ya elegidas.
     *
     * <p>Sin esto no hay forma de mirar el caso: provocar una eleccion de
     * varias cartas en una partida de verdad exige que salga la carta que la
     * pide y que la mesa tenga con que. Aqui las cuatro criaturas quedan
     * elegibles y las dos primeras marcadas, que es exactamente lo que se ve a
     * mitad de la eleccion.
     */
    void mockPicked() {
        final List<forge.neo.card.CardNode> field = app.table.selfFieldNodes();
        for (int i = 0; i < field.size(); i++) {
            field.get(i).setSelectable(true);
            field.get(i).setHighlighted(i < 2);
        }
        app.table.getActionBar().setPrompt("Sacrifica tres criaturas.  Elegidas 2 de 3");
        System.out.println("[maqueta] eleccion de varias cartas: "
                + field.size() + " elegibles, 2 ya elegidas");
    }

    void mockMechanics() {
        // --- contadores del jugador ---
        final com.google.common.collect.Multiset<forge.game.card.CounterType> mine =
                com.google.common.collect.HashMultiset.create();
        mine.add(forge.game.card.CounterEnumType.POISON, 10);
        mine.add(forge.game.card.CounterEnumType.ENERGY, 4);
        app.table.getSelfBar().setCounters(mine, 2, 10, null,
                forge.neo.match.PlayerSpeed.MAX, null);

        final com.google.common.collect.Multiset<forge.game.card.CounterType> theirs =
                com.google.common.collect.HashMultiset.create();
        theirs.add(forge.game.card.CounterEnumType.POISON, 3);
        theirs.add(forge.game.card.CounterEnumType.EXPERIENCE, 2);
        theirs.add(forge.game.card.CounterEnumType.RAD, 1);
        app.table.getOpponentBar().setCounters(theirs, 0, 10, "Paige", 2, null);

        // --- marcadores, resaltado y desplazamiento, sobre cartas reales ---
        final List<forge.neo.card.CardNode> field = app.table.selfFieldNodes();
        final String[][] marks = {
            {"Speed 3"}, {"CL:2"}, {"RL:4"}, {"In Room:", "Ruinous Cellar"},
        };
        for (int i = 0; i < field.size(); i++) {
            final forge.neo.card.CardNode node = field.get(i);
            final CardView cv = node.getCard();
            if (cv == null) {
                continue;
            }
            if (i < marks.length) {
                cv.set(forge.trackable.TrackableProperty.MarkerText, List.of(marks[i]));
            }
            if (i == 4) {
                cv.set(forge.trackable.TrackableProperty.PhasedOut, true);
            }
            // 1 = la puedes usar, 2 = ademas la taparia el "Auto"
            node.setActionable(i == 5 ? 1 : i == 6 ? 2 : 0);
            node.refresh();
        }
        // Emparejadas (almas gemelas) y clones: dos relaciones que el motor
        // publica y que solo se ven al ampliar la carta.
        if (field.size() >= 3) {
            final CardView a = field.get(0).getCard();
            final CardView b = field.get(1).getCard();
            final CardView c = field.get(2).getCard();
            if (a != null && b != null && c != null) {
                a.set(forge.trackable.TrackableProperty.PairedWith, b);
                c.set(forge.trackable.TrackableProperty.CloneOrigin, b);
                // Y el cuadro de texto intercambiado, a lo Deadpool: la carta
                // sigue con su arte y su texto impreso, pero el motor dice que
                // hace otra cosa. En una maqueta hay que ponerlo a mano porque
                // las vistas de catalogo no traen texto vivo.
                a.set(forge.trackable.TrackableProperty.Zone,
                        forge.game.zone.ZoneType.Battlefield);
                a.getCurrentState().set(forge.trackable.TrackableProperty.AbilityText,
                        "Flying\r\n\r\n"
                        + "{W}: Another target creature you control gains "
                        + "protection from colorless or from the color of "
                        + "your choice until end of turn.\r\n\r\n"
                        + "(Texto intercambiado por Deadpool, Trading Card)");
                System.out.printf("[maqueta] %s emparejada con %s; %s es copia de %s%n",
                        a.getName(), b.getName(), c.getName(), b.getName());
                // -Dneo.mech.zoom=paired|clone amplia la que interesa mirar.
                final String which = System.getProperty("neo.mech.zoom", "");
                final CardView target = "paired".equals(which) ? a
                        : "clone".equals(which) ? c : null;
                if (target != null) {
                    final javafx.animation.PauseTransition wait =
                            new javafx.animation.PauseTransition(
                                    javafx.util.Duration.seconds(3));
                    wait.setOnFinished(e -> {
                        forge.neo.ui.CardZoom.show(app.table, target);
                        // -Dneo.mech.expand=true despliega ademas el texto
                        // actual, que es lo unico que no se ve en una captura.
                        if (Boolean.getBoolean("neo.mech.expand")) {
                            final javafx.animation.PauseTransition after =
                                    new javafx.animation.PauseTransition(
                                            javafx.util.Duration.millis(700));
                            after.setOnFinished(x -> {
                                final javafx.scene.Node n = app.table.getScene() == null ? null
                                        : app.table.getScene().getRoot()
                                                .lookup(".zoom-live-toggle");
                                if (n instanceof javafx.scene.control.Button btn) {
                                    System.out.println("[maqueta] despliego el texto actual");
                                    btn.fire();
                                } else {
                                    System.out.println("[maqueta] no encuentro el boton");
                                }
                            });
                            after.play();
                        }
                    });
                    wait.play();
                }
            }
        }
        System.out.println("[maqueta] mecanicas: contadores de jugador, marcadores, "
                + "resaltado debil, desplazamiento de fase, emparejadas y clones");
    }

    /**
     * MAQUETA: las cuatro mesas a la vez, para ver si el Commander a 4 cabe
     * sin pestanyas ({@code --mock-multiboard}).
     *
     * <p>Reemplaza la raiz de la escena por un {@link MultiBoardPreview}. No
     * toca {@code app.table} ni el binder: la mesa de verdad se queda como
     * estaba y esto es una pantalla aparte que solo se pinta.
     *
     * <p>Lo que se viene a medir es <b>el ancho de carta que sale</b>, asi que
     * se imprime al final. Se mide sobre el nodo ya repartido y no con una
     * cuenta nuestra, que es lo unico que hace fiable la maqueta.
     *
     * <p>Banderas:
     * <ul>
     *   <li>{@code -Dneo.multiboard.perms=15} permanentes por rival. 15 es una
     *       mesa normal a mitad de partida (8 tierras, 4 criaturas, 3 mas).</li>
     *   <li>{@code -Dneo.multiboard.piles=false} quita el cementerio y el
     *       exilio de la mesa del rival. Es LA palanca: esa tira cuesta 173 px
     *       de los ~500 que le tocan a cada rival.</li>
     *   <li>{@code -Dneo.multiboard.oppShare=0.52} cuanto del alto libre se
     *       lleva la fila de rivales.</li>
     * </ul>
     */
    void mockMultiBoard(final Deck deck) {
        final int perms = Math.max(1, Integer.getInteger("neo.multiboard.perms", 15));
        final int opponents = Math.max(1, Integer.getInteger("neo.multiboard.opponents", 3));
        final boolean piles = !"false".equals(System.getProperty("neo.multiboard.piles"));

        final MultiBoardPreview view = new MultiBoardPreview(
                app.cardWidth, app.sideWidth, opponents, piles);

        // Cartas de verdad: el reparto depende de cuantas son tierras y
        // cuantas no, asi que con cartas inventadas la medida no valdria.
        final List<PaperCard> pool = pickPermanents(deck, perms * (opponents + 1) + 8);
        final List<CardView> everywhere = new ArrayList<>();
        final List<List<CardView>> boards = new ArrayList<>();
        for (int i = 0; i <= opponents; i++) {
            // El pozo de un mazo se agota: se recicla desde el principio en vez
            // de dejar mesas vacias, que falsearian el reparto a la baja.
            final List<CardView> board = new ArrayList<>();
            for (int k = 0; k < perms; k++) {
                board.add(CardView.getCardForUi(pool.get((i * perms + k) % pool.size())));
            }
            boards.add(board);
            everywhere.addAll(board);
        }

        for (int i = 0; i < opponents; i++) {
            view.setOpponentBoard(i, boards.get(i), everywhere);
            final PlayerBar bar = view.opponentBar(i);
            bar.setPlayerName("IA-" + (i + 1));
            bar.setLife(40 - i * 6);
            bar.setZones(5 + i, 84 - i * 3, 3 + i, i, 1);
            bar.setMana(0, i, 2, 0, 1, 1);
            bar.setActiveTurn(i == 1);
            view.opponentField(i).setZoneCounts(3 + i, i);
        }

        view.setSelfBoard(boards.get(opponents), everywhere);
        view.getSelfBar().setPlayerName(deck.getName());
        // El desglose de dano de comandante, que en una partida de verdad no se
        // puede provocar a voluntad: hacen falta tres comandantes distintos
        // conectandote. Es LO que hay que poder mirar — que 10+7+4 no se suman
        // y que ninguno de los tres esta cerca de los 21.
        view.getSelfBar().setCommanderDamage(List.of(
                new PlayerBar.CommanderHit("Sephiroth", 10),
                new PlayerBar.CommanderHit("Atraxa", 7),
                new PlayerBar.CommanderHit("Kenrith", 18)));
        view.getSelfBar().setLife(40);
        view.getSelfBar().setZones(7, 86, 2, 1, 1);
        view.getSelfBar().setMana(0, 0, 3, 0, 0, 2);
        view.setHand(views(pickCards(deck, Integer.getInteger("neo.mock.hand", 7))),
                app.cardWidth);

        app.scene.setRoot(view);

        // El tamanyo con el que se EVALUA, que no tiene por que ser el de esta
        // ventana: lo normal es mirar esto en una sesion sin monitor de verdad.
        // Ver setSnapshotNode.
        final String[] wh = System.getProperty("neo.multiboard.size", "1920x1080")
                .split("x");
        final double renderW = Double.parseDouble(wh[0]);
        final double renderH = Double.parseDouble(wh[1]);

        // Se repite antes de capturar porque entre medias la escena hace su
        // propio layout con el tamanyo de la ventana y pisa este.
        final Runnable sizeAndMeasure = () -> {
            view.resize(renderW, renderH);
            view.setStyle(forge.neo.ui.UiScale.rootStyle(renderH));
            // Medir DESPUES de un layout de verdad: antes de eso los anchos
            // valen 0 y la maqueta diria que no cabe nada. Es la trampa de
            // las trampas conocidas de las alturas medidas antes del primer layout.
            view.applyCss();
            view.layout();

            final String hud = String.format(
                    "MAQUETA · %d rivales en fila · %d permanentes cada uno · "
                    + "cementerio/exilio en la mesa: %s · render %.0fx%.0f",
                    opponents, perms, piles ? "SI" : "NO", renderW, renderH);
            view.setHudText(hud + "\n" + view.report()
                    + "     (suelo legible del propio codigo: 65 px  ·  "
                    + "contadores completos a partir de 108 px  ·  "
                    + "hoy con pestanyas: ~110 px)");
            // El rotulo cambia de alto al cambiar de texto.
            view.layout();

            System.out.println("[multiboard] " + hud);
            System.out.println("[multiboard] " + view.report());
        };
        sizeAndMeasure.run();
        debugSnapshotOf(view, sizeAndMeasure);
    }

    /** Atajo para no exponer los campos de la captura. */
    private void debugSnapshotOf(final javafx.scene.Node node, final Runnable before) {
        setSnapshotNode(node, before);
    }

    // ---------------------------------------------------------------

    static List<CardView> views(final List<PaperCard> cards) {
        final List<CardView> out = new ArrayList<>();
        for (final PaperCard pc : cards) {
            out.add(CardView.getCardForUi(pc));
        }
        return out;
    }

    static List<PaperCard> slice(final List<PaperCard> src, final int from, final int to) {
        return src.subList(Math.min(from, src.size()), Math.min(to, src.size()));
    }

    /** Solo permanentes: lo unico que puede estar en el campo de batalla. */
    static List<PaperCard> pickPermanents(final Deck deck, final int max) {
        final List<PaperCard> out = new ArrayList<>();
        for (final var e : deck.getMain()) {
            final var rules = e.getKey().getRules();
            final var type = rules.getType();
            if (type.isCreature() || type.isLand() || type.isArtifact()
                    || type.isEnchantment() || type.isPlaneswalker()) {
                out.add(e.getKey());
            }
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    static List<PaperCard> pickCards(final Deck deck, final int max) {
        final List<PaperCard> out = new ArrayList<>();
        for (final var e : deck.getMain()) {
            out.add(e.getKey());
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    /**
     * Pulsa por ti el boton principal que haya activo, cada pocos segundos.
     *
     * <p>Sirve para VALIDAR el camino humano sin jugar: acciona los botones de
     * verdad de la interfaz (barra de accion y dialogos), no el motor por
     * detras. Si una partida avanza con esto, el cableado de la fase 4 es
     * correcto. No es una ayuda de juego: es una herramienta de prueba.
     */
    void startAutopilot(final javafx.scene.Scene scene) {
        final javafx.animation.Timeline t = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(1.2), e -> {
                    if (scene == null || scene.getRoot() == null) {
                        return;
                    }
                    // En la sala de espera NO. El piloto pulsa cualquier
                    // ".btn-primary" que encuentre, y ahi uno de ellos es el de
                    // "Listo": lo marcaba y al segundo siguiente lo volvia a
                    // pulsar, desmarcandolo. La partida no empezaba nunca y
                    // parecia un fallo de la sala. La sala tiene su propio
                    // piloto (--lobby-auto), que sabe en que orden van las
                    // cosas.
                    final forge.neo.ui.LobbyScreen sala = app.net.lobbyScreen;
                    if (sala != null && scene.getRoot() == sala.getRoot()) {
                        return;
                    }
                    // ANTES que el boton: puede que lo que el motor espere sea
                    // un RETRATO, no un OK. Pasa al empezar una partida de tres
                    // o mas si ganas el sorteo inicial ("Haz clic en el
                    // retrato"). Ahi el OK esta encendido pero no contesta
                    // nada, asi que el piloto se quedaba pulsandolo para
                    // siempre y la partida no arrancaba — la mitad de las
                    // veces, segun cayera la moneda.
                    if (clickAPortrait()) {
                        return;
                    }
                    // ANTES que nada de la mesa: si hay una pregunta de
                    // "seguro que dejas tu fase principal?", se contesta que
                    // si. El piloto pulsa el primer .btn-primary que ve, y ahi
                    // el marcado es "me quedo": se quedaria dando OK y diciendo
                    // que no para siempre, sin llegar nunca al combate. Asi los
                    // comprobadores pasan por la pregunta de verdad en vez de
                    // esquivarla.
                    for (final javafx.scene.Node n : scene.getRoot().lookupAll(".phase-ask-go")) {
                        if (n instanceof javafx.scene.control.Button b && !b.isDisabled()) {
                            System.out.println("[autopilot] dejo la fase principal");
                            b.fire();
                            return;
                        }
                    }
                    // ANTES que el OK de la partida: en el tutorial lo que hay
                    // que pulsar es el "Siguiente" de la banda. lookupAll
                    // devuelve un conjunto sin orden, asi que sin esto el
                    // piloto se ponia a jugar la partida y la leccion se
                    // quedaba en el mismo paso hasta que se acababa el tiempo.
                    for (final javafx.scene.Node n : scene.getRoot().lookupAll(".coach-next")) {
                        if (n instanceof javafx.scene.control.Button b
                                && !b.isDisabled() && b.isVisible()) {
                            System.out.println("[autopilot] siguiente paso del tutorial");
                            b.fire();
                            return;
                        }
                    }
                    for (final javafx.scene.Node n : scene.getRoot().lookupAll(".btn-primary")) {
                        if (n instanceof javafx.scene.control.Button b
                                && !b.isDisabled() && b.isVisible()) {
                            // Se dice CUAL se pulsa: sin esto, cuando el piloto
                            // recorre varias pantallas seguidas no hay forma de
                            // saber por donde ha pasado ni donde acabo.
                            System.out.println("[autopilot] pulso " + b.getText());
                            b.fire();
                            return;
                        }
                    }
                    // El reparto generico (que mana da una tierra de filtro,
                    // contadores entre varios objetivos) deja el Aceptar
                    // apagado hasta que cuadre, asi que el piloto se plantaria
                    // ahi. "Repartir solo" cuadra y deja seguir.
                    for (final javafx.scene.Node n : scene.getRoot().lookupAll(".amount-auto")) {
                        if (n instanceof javafx.scene.control.Button b && !b.isDisabled()) {
                            System.out.println("[autopilot] reparto solo");
                            b.fire();
                            return;
                        }
                    }
                    // Si no hay boton que pulsar, el motor esta esperando que
                    // se CLIQUEN CARTAS: descartar al final del turno, elegir
                    // que sacrificar, declarar atacantes. Sin esto el piloto se
                    // queda plantado en la fase de limpieza con el OK apagado y
                    // la partida no avanza nunca — que es justo donde se quedo
                    // al intentar recorrer un duelo entero de la aventura.
                    if (clickSomethingSelectable(scene)) {
                        return;
                    }
                    // Y si tampoco, puede haber un dialogo de elegir de una
                    // lista (el sobre de premio al ganar un duelo). Se coge la
                    // primera opcion: lo que se comprueba es que el camino
                    // llega hasta el final, no cual se elige.
                    for (final javafx.scene.Node n : scene.getRoot().lookupAll(".choice-item")) {
                        if (n instanceof javafx.scene.control.Button b && !b.isDisabled()) {
                            System.out.println("[autopilot] elijo " + b.getText());
                            b.fire();
                            return;
                        }
                    }
                }));
        t.setCycleCount(javafx.animation.Animation.INDEFINITE);
        t.play();
    }

    /**
     * Clica el retrato propio si el motor esta esperando un jugador.
     *
     * <p>Se clica la barra de verdad, no se llama al motor por detras: lo que
     * se quiere validar es que ese gesto funciona.
     */
    boolean clickAPortrait() {
        final TableBinder b = app.binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui == null || app.table == null || !ui.isWaitingForPlayerPick()) {
            return false;
        }
        final forge.neo.ui.PlayerBar bar = app.table.getSelfBar();
        if (bar == null || !bar.isVisible()) {
            return false;
        }
        System.out.println("[autopilot] clico mi retrato");
        fire(bar, javafx.scene.input.MouseEvent.MOUSE_CLICKED, centreOf(bar));
        return true;
    }

    /** La primera carta que el motor este esperando que se cliquee. */
    boolean clickSomethingSelectable(final javafx.scene.Scene scene) {
        final forge.neo.card.CardNode target = firstSelectable(scene.getRoot());
        if (target == null || app.table == null) {
            return false;
        }
        System.out.println("[autopilot] clico "
                + (target.getCard() == null ? "?" : target.getCard()));
        fire(target, javafx.scene.input.MouseEvent.MOUSE_CLICKED, centreOf(target));
        return true;
    }

    static forge.neo.card.CardNode firstSelectable(final javafx.scene.Node root) {
        if (root instanceof forge.neo.card.CardNode cn) {
            return cn.isSelectable() ? cn : null;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (final javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                final forge.neo.card.CardNode found = firstSelectable(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Captura en cuanto haya combate en la mesa.
     *
     * <p>Verificar el combate con una captura a tiempo fijo es una loteria: no
     * se sabe cuando va a atacar la IA. Esto espera a que aparezca la primera
     * flecha y dispara entonces. Con {@code --wait} se pone el limite de
     * paciencia, en segundos.
     */
    /** Si la captura de combate debe ampliar antes una carta girada. */
    boolean zoomOnCombat;

    /** Esperar a que haya algo en el stack en vez de a que haya combate. */
    boolean stackNotEmpty;

    /**
     * Esperar a que se levante un dialogo.
     *
     * <p>Sirve para comprobar preguntas que solo salen en un momento concreto
     * de una partida de verdad y que ademas duran poco si hay piloto: se
     * captura en el mismo fotograma en que aparecen.
     */
    boolean waitForModal;

    /**
     * Esperar a que la mesa este puesta y sin nada delante.
     *
     * <p>Lo contrario de {@code --on-modal}: captura el primer fotograma en que
     * ya hay cartas y ningun dialogo tapando. Con {@code --autopilot} sale justo
     * despues de que se cierre el cartel de turno, sin darle tiempo a la partida
     * a irse sola.
     */
    boolean waitForBoard;

    /**
     * Esperar a que haya una criatura PREPARADA en la mesa, y nada delante.
     *
     * <p>Ese estado no se pide: llega cuando la partida entra en la fase que lo
     * dispara, y con el piloto automatico eso cae donde cae. Sin esperarlo, la
     * captura sale con lo que hubiera puesto en ese segundo — un dialogo, otro
     * turno — y no prueba nada.
     */
    boolean waitForPrepared;

    void snapshotWhenCombat(final Scene scene, final String path, final int maxSecs) {
        final long deadline = System.currentTimeMillis() + maxSecs * 1000L;
        final javafx.animation.AnimationTimer watcher = new javafx.animation.AnimationTimer() {
            @Override
            public void handle(final long now) {
                final boolean combat = app.table != null
                        && (waitForModal ? app.table.isModalShowing()
                            : waitForPrepared ? app.table.hasPreparedCard() && !app.table.isModalShowing()
                            : waitForBoard ? app.table.nodeCount() > 0 && !app.table.isModalShowing()
                            : stackNotEmpty ? app.table.stackSize() > 0 : app.table.fightLinkCount() > 0);
                if (!combat && System.currentTimeMillis() < deadline) {
                    return;
                }
                stop();
                System.out.println(combat
                        ? "[snapshot] combate detectado, capturando"
                        : "[snapshot] se agoto la espera sin combate");
                // Sin esperas: en modo automatico el combate dura milisegundos
                // y cualquier pausa lo pierde. Se fuerza el repintado de las
                // flechas y se captura en este mismo pulso.
                if (app.table != null) {
                    app.table.redrawCombat();
                    // Comprobar la carta ampliada con una carta REALMENTE
                    // girada: en la maqueta no hay ninguna, y el atacante
                    // siempre lo esta.
                    if (zoomOnCombat) {
                        for (final javafx.scene.Node n : app.table.opponentFieldNodes()) {
                            final forge.neo.card.CardNode cn = (forge.neo.card.CardNode) n;
                            if (cn.getCard() != null && cn.getCard().isTapped()) {
                                app.table.showZoom(cn.getCard());
                                break;
                            }
                        }
                    }
                }
                writeSnapshot(scene, path);
            }
        };
        watcher.start();
    }

    /**
     * Capturar un NODO a un tamanyo dado, en vez de la ventana.
     *
     * <p>Hace falta porque el tamanyo de la ventana lo decide el monitor
     * ({@code Screen.getPrimary}), y una maqueta que se evalua por si las
     * cartas se leen no puede depender de en que pantalla se ejecute: capturada
     * en un 1024x768 saldria mucho peor de lo que va a salir de verdad, y
     * decidiriamos sobre una medida falsa.
     *
     * <p>Nulo por defecto: sin llamar a esto, {@link #writeSnapshot} captura la
     * escena como toda la vida.
     */
    void setSnapshotNode(final javafx.scene.Node node, final Runnable before) {
        this.snapshotNode = node;
        this.beforeSnapshot = before;
    }

    private javafx.scene.Node snapshotNode;
    private boolean snapResized;
    private Runnable beforeSnapshot;

    void writeSnapshot(final Scene scene, final String path) {
        // Puede no haber mesa: la pantalla de inicio tambien se captura.
        final TableScreen t = app.table != null && app.table.getScene() != null ? app.table : null;
        if (t != null && Boolean.getBoolean("neo.layout.debug")) {
            t.dumpLayout();
        }
        if (beforeSnapshot != null) {
            beforeSnapshot.run();
        }
        // -Dneo.snapSize=1920x1080: capturar como se veria en OTRA pantalla.
        //
        // El tamanyo de la ventana lo decide el monitor, y una sesion de
        // verificacion puede no tener uno de verdad (aqui son 1024x768). Sin
        // esto, cualquier cosa que dependa del ancho — y el reparto de la mesa
        // depende entero — se comprueba sobre una resolucion que el jugador no
        // usa, y se decide sobre una medida falsa.
        final String snapSize = System.getProperty("neo.snapSize");
        if (snapSize != null && snapshotNode == null
                && scene.getRoot() instanceof javafx.scene.layout.Region root) {
            try {
                final String[] wh = snapSize.split("x");
                final double sw = Double.parseDouble(wh[0]);
                final double sh = Double.parseDouble(wh[1]);
                root.resize(sw, sh);
                root.setStyle(forge.neo.ui.UiScale.rootStyle(sh));
                root.applyCss();
                root.layout();

                // Y una segunda pasada, porque hay decisiones que dependen del
                // ancho y NO las toma el layout: cuantas mesas de rival caben
                // lo decide el binder (TableBinder.apply), que corre en su
                // propio turno. Sin dejarle correr con el tamanyo nuevo, la
                // captura sale con el reparto que se decidio para la ventana
                // pequenya — o sea justo con lo que no se queria medir.
                if (!snapResized) {
                    snapResized = true;
                    if (app.binder != null) {
                        app.binder.requestRefresh();
                    }
                    final PauseTransition again = new PauseTransition(Duration.millis(300));
                    again.setOnFinished(e -> writeSnapshot(scene, path));
                    again.play();
                    return;
                }

                if (t != null && Boolean.getBoolean("neo.layout.debug")) {
                    t.dumpLayout();
                }
                Snapshots.writePng(root.snapshot(null, null), new File(path));
                System.out.printf("Captura en %s | forzada a %.0fx%.0f%n", path, sw, sh);
                Platform.exit();
                return;
            } catch (final Exception | Error ex) {
                System.err.println("neo.snapSize no valido, se captura normal: " + ex);
            }
        }
        try {
            Snapshots.writePng(snapshotNode != null
                    ? snapshotNode.snapshot(null, null) : scene.snapshot(null), new File(path));
            if (t == null) {
                System.out.printf("Captura en %s | pantalla de inicio%n", path);
            } else {
                System.out.printf("Captura en %s | cartas: %d | con imagen: %d | flechas: %d%n",
                        path, t.nodeCount(), t.nodesWithArt(), t.combatLinkCount());
            }
        } catch (final Exception ex) {
            System.err.println("No se pudo guardar la captura: " + ex);
        }
        Platform.exit();
    }

    /** Espera a que bajen las imagenes y guarda una captura de la escena. */
    void scheduleSnapshot(final Scene scene, final String path, final int waitSecs) {
        final PauseTransition wait = new PauseTransition(Duration.seconds(waitSecs));
        wait.setOnFinished(e -> {
            if (app.table != null && app.table.getScene() != null) {
                app.table.refreshAll();
            }
            if (app.home != null && app.home.getScene() != null) {
                app.home.refreshArt();
            }
            final PauseTransition settle = new PauseTransition(Duration.millis(500));
            settle.setOnFinished(e2 -> writeSnapshot(scene, path));
            settle.play();
        });
        wait.play();
    }

}
