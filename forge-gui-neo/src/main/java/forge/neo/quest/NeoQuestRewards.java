package forge.neo.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import forge.game.GameView;
import forge.gamemodes.quest.QuestWinLoseController;
import forge.gui.UiCommand;
import forge.gui.interfaces.IButton;
import forge.gui.interfaces.IWinLoseView;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;

/**
 * Las recompensas de un duelo de la aventura.
 *
 * <p>Esto es lo que convierte la aventura en una aventura: al ganar te llevas
 * creditos, cartas nuevas, subidas de nivel y a veces una edicion desbloqueada.
 * <b>Nada de eso lo calculamos nosotros</b>: lo hace
 * {@link QuestWinLoseController}, que es la misma clase que usa la GUI vieja, y
 * que ademas anota la victoria — que es lo que hace subir de nivel a los
 * rivales.
 *
 * <p>Esa clase habla con la interfaz por {@link IWinLoseView}: tres botones y
 * tres formas de ensenyar cosas. Esta clase es ese adaptador. Los botones no
 * pintan nada — solo existen porque el motor los toca — y lo que el motor
 * quiere ensenyar se guarda en una lista que despues lee nuestra pantalla de
 * fin de partida.
 *
 * <p><b>Ojo con el hilo.</b> {@code showRewards(Runnable)} recibe el trabajo de
 * verdad y la GUI vieja lo lanza en segundo plano. Aqui se ejecuta y se ESPERA,
 * porque quien nos llama ya esta fuera del hilo de interfaz y necesita las
 * recompensas aplicadas antes de repintar el hub.
 */
public final class NeoQuestRewards implements IWinLoseView<IButton> {

    private final List<String> messages = new ArrayList<>();
    private final List<PaperCard> cards = new ArrayList<>();

    /**
     * Cuales de esas cartas no tenias antes del duelo.
     *
     * <p>Hay que mirarlo <b>antes</b> de repartir: despues ya estan todas
     * dentro de la coleccion y no habria forma de saberlo. Es lo mismo que
     * hace la tienda al abrir un sobre ({@code NeoQuestShop.Opened}), solo que
     * aqui quien reparte es el motor y el sobre de premio llega despues, asi
     * que se calcula al final contra la foto de antes.
     */
    private final Set<String> fresh = new HashSet<>();
    private boolean won;

    private NeoQuestRewards() {
    }

    /**
     * Aplica las recompensas de la partida que acaba de terminar.
     *
     * <p>Llamar desde un hilo que NO sea el de interfaz.
     *
     * @return lo que el motor queria contarte, ya en orden
     */
    /** Si se venden solas las repetidas al terminar un duelo. */
    public static final String AUTO_SELL = "quest.autoSell";

    public static NeoQuestRewards apply(final GameView game) {
        final NeoQuestRewards view = new NeoQuestRewards();
        if (game == null || !NeoQuest.isActive()) {
            return view;
        }
        try {
            final QuestWinLoseController controller = new QuestWinLoseController(game, view);
            // Dos llamadas, y las dos hacen falta:
            //
            //   showRewards()  reparte lo que has ganado (creditos, cartas,
            //                  sobres, rachas, ediciones desbloqueadas).
            //   actionOnQuit() ANOTA el resultado — addWin() / addLost() — y
            //                  guarda.
            //
            // En la GUI vieja la segunda corre al cerrar la pantalla de
            // recompensas, asi que es facil no verla. Sin ella el duelo se juega,
            // se cobra... y el marcador se queda igual: sintoma exacto que salio
            // en questcheck, "15-0 -> 15-0, creditos 200 -> 200". Y como el nivel
            // de los rivales depende de las victorias, la aventura no avanzaria
            // nunca.
            // El sobre de premio lo damos NOSOTROS, no el motor: el suyo te
            // pregunta por un "formato" y da un revoltijo; el nuestro te deja
            // elegir expansion. Ver NeoQuestPrize.
            //
            // Se repone la preferencia ANTES de actionOnQuit(), que es quien
            // guarda el fichero de preferencias — compartido con la instalacion
            // normal de Forge del usuario.
            final boolean won = game.isMatchWonBy(
                    forge.player.GamePlayerUtil.getQuestPlayer());
            view.won = won;
            // La foto de lo que tenias, ANTES de que el motor reparta nada.
            final Set<String> before = NeoQuestShop.ownedNames();
            final boolean prizeDue = NeoQuestPrize.isDue(won);
            final String wasBooster = NeoQuestPrize.muteEngineBooster();
            try {
                controller.showRewards();
            } finally {
                NeoQuestPrize.unmuteEngineBooster(wasBooster);
            }
            controller.actionOnQuit();

            if (prizeDue) {
                final NeoQuestShop.Opened prize = NeoQuestPrize.award();
                if (prize != null) {
                    view.messages.add("SOBRE DE PREMIO: " + prize.getCards().size()
                            + " cartas, " + prize.getNewCount() + " nuevas");
                    view.cards.addAll(prize.getCards());
                }
            }
            // Vender lo que sobra, si esta puesto.
            //
            // Va AQUI y no en la tienda a proposito: el momento de "esto es lo
            // que ha cambiado en tu coleccion" es este, y una venta que ocurre
            // en silencio se nota solo porque el dinero sube sin motivo. La
            // linea dice cuantas y por cuanto, y las cartas vuelven al
            // mostrador por si te arrepientes.
            if (forge.neo.NeoSettings.getBool(AUTO_SELL, true)) {
                final NeoQuestSell.Sold sold = NeoQuestSell.sell();
                if (!sold.isEmpty()) {
                    view.messages.add(forge.neo.NeoText.get("quest.sold",
                            sold.getCopies(), sold.getCredits()));
                }
            }

            // Ya estan todas: cuales eran nuevas es lo que no estaba en la foto.
            for (final PaperCard card : view.cards) {
                if (!before.contains(NeoQuestShop.key(card))) {
                    view.fresh.add(NeoQuestShop.key(card));
                }
            }

            // Los logros los avisa el motor por IGuiBase, no por esta vista.
            view.messages.addAll(forge.neo.platform.NeoGuiBase.drainNotices());
            NeoQuest.forgetDuels();
            NeoQuest.save();
        } catch (final RuntimeException e) {
            System.err.println("[neo] las recompensas de la aventura han fallado: " + e);
            e.printStackTrace();
        }
        return view;
    }

    /** Las lineas que el motor queria ensenyar ("Has ganado 150 creditos"...). */
    public List<String> getMessages() {
        return messages;
    }

    /** Las cartas que te llevas. */
    public List<PaperCard> getCards() {
        return cards;
    }

    /** true si esa carta no la tenias antes del duelo. */
    public boolean isNew(final PaperCard card) {
        return card != null && fresh.contains(NeoQuestShop.key(card));
    }

    /** Cuantas cartas no tenias. */
    public int getNewCount() {
        return fresh.size();
    }

    /**
     * Recompensas de mentira, solo para poder capturar la pantalla del botin.
     *
     * <p>Se marcan como nuevas una de cada dos: con todas nuevas o con ninguna
     * no se ve si la pastilla distingue algo.
     */
    public static NeoQuestRewards mock(final boolean won, final List<String> messages,
                                       final List<PaperCard> cards) {
        final NeoQuestRewards view = new NeoQuestRewards();
        view.won = won;
        view.messages.addAll(messages);
        view.cards.addAll(cards);
        for (int i = 0; i < cards.size(); i += 2) {
            view.fresh.add(NeoQuestShop.key(cards.get(i)));
        }
        return view;
    }

    /** Si has ganado el duelo. Lo dice el motor, no lo deducimos. */
    public boolean isWon() {
        return won;
    }

    /** Si hay algo que ensenyar: cartas o algo que contar. */
    public boolean hasAnything() {
        return !cards.isEmpty() || !messages.isEmpty();
    }

    // ---------------------------------------------------------------
    // IWinLoseView
    // ---------------------------------------------------------------

    @Override
    public void showRewards(final Runnable runnable) {
        // El motor da por hecho que esto se va a otro hilo y que la pantalla
        // sigue viva mientras tanto. Nosotros ya venimos de un hilo de fondo:
        // se ejecuta aqui y se espera, que es lo que hace falta para que el hub
        // se repinte con los creditos YA sumados.
        final CountDownLatch done = new CountDownLatch(1);
        final Thread t = new Thread(() -> {
            try {
                runnable.run();
            } finally {
                done.countDown();
            }
        }, "neo-quest-rewards");
        t.setDaemon(true);
        t.start();
        try {
            // SIN PLAZO, y esto costo un premio de verdad.
            //
            // Antes se esperaba 30 segundos. Lo ultimo que hace el motor al
            // repartir es el SOBRE DE PREMIO, y para darlo te PREGUNTA de que
            // formato lo quieres. Un humano leyendo un dialogo que no esperaba
            // tarda mas de 30 segundos: se cumplia el plazo, esta llamada
            // volvia, se guardaba la aventura, la app se iba al cuartel general
            // — y al cambiar de pantalla el dialogo desaparecia, asi que la
            // respuesta no llegaba nunca y las cartas no se anyadian jamas.
            //
            // Sintoma exacto: "dinero si me dieron por ganar, pero la carta
            // no". Los creditos se reparten ANTES de la pregunta; el sobre,
            // despues.
            //
            // Esperar sin plazo es seguro: sin ventana no hay dialogo que
            // esperar (NeoGuiBase contesta solo), y con ventana el dialogo
            // siempre se puede contestar.
            done.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void showCards(final String title, final List<PaperCard> shown) {
        if (title != null && !title.isBlank()) {
            messages.add(title);
        }
        if (shown != null) {
            cards.addAll(shown);
        }
    }

    @Override
    public void showMessage(final String message, final String title, final FSkinProp icon) {
        if (title != null && !title.isBlank()) {
            messages.add(title);
        }
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }

    @Override
    public void hide() {
    }

    @Override
    public IButton getBtnContinue() {
        return dummy;
    }

    @Override
    public IButton getBtnRestart() {
        return dummy;
    }

    @Override
    public IButton getBtnQuit() {
        return dummy;
    }

    /**
     * Un boton que no existe.
     *
     * <p>El motor les cambia el texto y los esconde para montar SU pantalla de
     * fin de partida. La nuestra ya existe ({@code GameOverScreen}), asi que
     * aqui solo hay que tragarse las llamadas.
     */
    private final IButton dummy = new IButton() {
        private String text = "";
        private boolean visible = true;
        private boolean enabled = true;
        private String tooltip = "";

        @Override public boolean isSelected() { return false; }
        @Override public void setSelected(final boolean b) { }
        @Override public boolean requestFocusInWindow() { return false; }
        @Override public void setCommand(final UiCommand command) { }
        @Override public void setImage(final FSkinProp color) { }
        @Override public void setTextColor(final int r, final int g, final int b) { }
        @Override public String getText() { return text; }
        @Override public void setText(final String t) { this.text = t; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public void setEnabled(final boolean b) { this.enabled = b; }
        @Override public boolean isVisible() { return visible; }
        @Override public void setVisible(final boolean b) { this.visible = b; }
        @Override public String getToolTipText() { return tooltip; }
        @Override public void setToolTipText(final String s) { this.tooltip = s; }
    };
}
