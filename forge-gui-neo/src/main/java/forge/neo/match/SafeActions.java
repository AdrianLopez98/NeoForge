package forge.neo.match;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import forge.LobbyPlayer;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.DeclineScope;
import forge.gamemodes.match.YieldController;
import forge.interfaces.IMacroSystem;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;

/**
 * Que el barrido de "que puedes hacer ahora" no se lleve la partida por delante.
 *
 * <p><b>De donde sale esto.</b> Reportado el 09-09-2026 por un jugador con
 * <i>Coram, the Undertaker</i> en un mazo de Commander. Ataca con Coram, se
 * resuelve la molida... y en cuanto el combate avanza, el juego <b>se cierra
 * entero</b>: sin ganador, sin perdedor y sin una palabra. Le paso las dos veces
 * que ataco con Coram, y mando el registro, que traia la excepcion entera.
 *
 * <p><b>Que pasa de verdad.</b> Un {@code NullPointerException} dentro del
 * motor, en el hilo de la partida, o sea que se lleva {@code mainGameLoop}
 * (igual que el caso de {@link SafeAi}, y por lo mismo: el hilo <i>es</i> la
 * partida). La traza, recortada:
 *
 * <pre>
 *   CardProperty.cardHasProperty:963        &lt;-- card.getZone() es null
 *   StaticAbilityContinuous.getAffectedCards
 *   GameAction.checkStaticAbilities
 *   GameActionUtil.getAlternativeCosts:113
 *   Card.getAllPossibleAbilities
 *   forge.ai.AvailableActions.walk          &lt;-- EL BARRIDO
 *   PlayerControllerHuman.chooseSpellAbilityToPlay:1662
 *   PhaseHandler.mainGameLoop
 * </pre>
 *
 * <p><b>Hacen falta las tres cosas a la vez</b>, y por eso no habia salido
 * antes. Las tres estan medidas una a una en {@link ActionsCheck}: quitando
 * cualquiera, la misma mesa juega treinta turnos sin un fallo.
 *
 * <ol>
 *   <li><b>Coram</b> pone en juego una estatica de permiso cuyo filtro es
 *       {@code Card.nonLand+ThisTurnEnteredFrom_Library}. Esa propiedad la
 *       resuelve {@code CardProperty} con <b>{@code card.getZone()} pelado</b>,
 *       sin comprobar el null — mientras que las ramas de al lado usan
 *       {@code getLastKnownZone()}. Ese es el fallo del motor, y es una linea.</li>
 *   <li><b>Una molida</b>: cartas cayendo de la biblioteca al cementerio este
 *       turno. La estatica de Coram mira el cementerio
 *       ({@code AffectedZone$ Graveyard}), asi que con los cementerios vacios
 *       no llega a filtrar nada. Y es justamente el <b>disparo de ataque de
 *       Coram</b>, de ahi que al jugador le pasara las dos veces que ataco
 *       con el.</li>
 *   <li><b>Una carta de dos caras</b> — modal, aventura, transformar o partida —
 *       entre las que recorre el barrido. Es la que trae el null: para saber
 *       que se puede hacer con la otra cara, {@code Spell.getAlternateHost} se
 *       hace una <b>copia LKI</b>, y una copia LKI tiene
 *       {@code savedLastKnownZone} puesto y <b>{@code currentZone} a null</b>.
 *       Asi pasa todos los guardias de {@code checkZoneRestrictions} (que
 *       preguntan por {@code getLastKnownZone()}) y revienta en el ultimo
 *       paso.</li>
 * </ol>
 *
 * <p><b>Henzie no hace falta</b>, aunque lo pareciera: el informe venia con el
 * de comandante y la primera reproduccion lo llevaba. Se probo sin el y falla
 * igual. Su {@code AffectedZone$ Stack} fabrica <i>otra</i> copia LKI por otro
 * camino de {@code GameActionUtil}, y por eso confundia.
 *
 * <p><b>Y quien pide ese barrido somos nosotros.</b> {@code AvailableActions}
 * solo corre si estan encendidos los resaltados de cartas jugables o el
 * auto-pass de "no tienes nada que hacer" — las dos cosas las enciende
 * {@link NeoGame#applyEnginePrefs()}, que es lo que hace que esta interfaz se
 * sienta como Arena. O sea que <b>el fallo es del motor pero el camino lo
 * abrimos nosotros</b>, y taparlo es nuestro problema, no del upstream.
 *
 * <p><b>Como se blinda sin tocar el motor.</b> Igual que {@link SafeAi} y que
 * {@link ManaColor}: relevando el controlador. {@code chooseSpellAbilityToPlay},
 * {@code getActionableCards}, {@code setYieldPref} y {@code applyYieldUpdate}
 * son publicos y sobrescribibles, y el barrido es <b>lo primero</b> que hacen —
 * todavia no se le ha pedido nada al jugador — asi que se puede reintentar
 * entero sin efectos a medias. Al reintentar se apaga el barrido con
 * {@code YieldController.setPref}, que son overrides <b>del controlador</b>
 * (no tocan las preferencias guardadas del jugador ni las de Forge).
 *
 * <p><b>Y se apaga solo para ese momento, no para la partida.</b> Es a
 * proposito: la combinacion que revienta se deshace sola — basta con que pase
 * el turno sin molida, o que Coram deje la mesa — y entonces los resaltados y
 * el auto-pass vuelven sin que nadie haga nada. Apagarlos "por si acaso"
 * durante una partida entera de Commander seria cobrar por un fallo que a lo
 * mejor dura un turno.
 *
 * <p><b>Lo que NO hace:</b> no arregla el fallo de Forge — eso seria tocar
 * {@code forge-game}, que es la regla de oro — ni tapa cualquier excepcion que
 * pase por ahi: solo las que traen un marco de {@code forge.ai.AvailableActions}
 * en la traza. Lo demas sube tal cual.
 *
 * <p><b>Card-Forge lo arreglo el 15-09-2026</b> (commit {@code aeedc51351},
 * <i>"Fix NPE in ThisTurnEnteredFrom on an LKI copy"</i>, #11917): esa rama de
 * {@code CardProperty} ya lee {@code getLastKnownZone()} y trata la zona nula
 * como "no coincide". Desde el rebase de ese dia esto <b>no salta nunca</b>, y
 * se queda a proposito <b>de guardia, dormido</b>: solo se traga lo que viene
 * de {@code AvailableActions}, asi que no cuesta nada, y el dia que otra
 * propiedad del motor tropiece con una copia LKI por el mismo barrido la
 * partida sigue viva en vez de cerrarse. {@code run.cmd actionscheck} da por
 * bueno cero rescates siempre que el barrido haya corrido de verdad
 * ({@link #scans()}).
 *
 * @see SafeAi el mismo problema con la IA, y el mismo remedio
 * @see ManaColor la otra cosa que se sienta en esta silla
 */
public final class SafeActions {

    private SafeActions() {
    }

    /**
     * Se puede apagar con {@code -Dneo.actions.guard=false}.
     *
     * <p>Apagarlo no quita el envoltorio: lo que hace es <b>volver a lanzar</b>.
     * Sirve para demostrar que sin el la partida SI se muere, que es la
     * contraprueba de {@code run.cmd actionscheck} — una prueba que no puede
     * fallar no prueba nada.
     */
    private static final boolean GUARD =
            !"false".equalsIgnoreCase(System.getProperty("neo.actions.guard", "true"));

    /** {@code -Dneo.actions.debug=true}: decir en cada prioridad si el barrido corre. */
    private static final boolean DEBUG = Boolean.getBoolean("neo.actions.debug");

    /** Cuantas veces se ha rescatado el barrido. Lo mira el comprobador. */
    private static final AtomicInteger RESCUES = new AtomicInteger();

    public static int rescues() {
        return RESCUES.get();
    }

    public static void resetRescues() {
        RESCUES.set(0);
        SCANS.set(0);
    }

    /**
     * Cuantas prioridades han pasado por aqui con el barrido encendido.
     *
     * <p>Existe para que cero rescates signifique algo: sin esto, "el motor ya
     * no falla" y "el barrido no llego a correr" dan el mismo cero, y la prueba
     * pasaria en verde sin haber ejecutado la linea que se vigila.
     */
    private static final AtomicInteger SCANS = new AtomicInteger();

    public static int scans() {
        return SCANS.get();
    }

    /** La clase del motor que hace el barrido. Es la firma que se reconoce. */
    private static final String SCAN_CLASS = "forge.ai.AvailableActions";

    /**
     * Viene este fallo del barrido del motor?
     *
     * <p>Se mira la traza y no el tipo de excepcion, y se mira <b>estrecho</b>:
     * un {@code catch} generoso aqui se tragaria fallos nuestros de la mesa o
     * del pago de mana, que pasan por los mismos metodos y que hay que ver.
     */
    static boolean fromScan(final Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            for (final StackTraceElement f : e.getStackTrace()) {
                if (SCAN_CLASS.equals(f.getClassName())) {
                    return true;
                }
            }
            if (e.getCause() == e) {
                break;
            }
        }
        return false;
    }

    /**
     * Las preferencias que encienden el barrido.
     *
     * <p>Son las cuatro que mira {@code PlayerControllerHuman}: la de los
     * resaltados y las tres de las que depende {@code needsAvailableActions()}.
     * Si falta una, el barrido se hace igual y el reintento revienta otra vez —
     * esta vez sin red, porque ya estamos dentro del rescate.
     */
    private static final Map<FPref, String> SCAN_OFF = new EnumMap<>(Map.of(
            FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, "false",
            FPref.YIELD_AUTO_PASS_NO_ACTIONS, "false",
            FPref.YIELD_DECLINE_SCOPE_NO_ACTIONS, DeclineScope.NEVER.name(),
            FPref.YIELD_DECLINE_SCOPE_STACK_YIELD, DeclineScope.NEVER.name()));

    /**
     * Corre {@code body} con el barrido apagado y lo deja como estaba.
     *
     * <p>Los overrides viven en el {@link YieldController} del asiento (desde
     * el 15-10-2026 uno solo, compartido por el relevo y el relevado: ver
     * {@link Guarded#getYieldController}), que muere con la partida: no se
     * escribe nada en {@code %APPDATA%} ni se toca el {@code FModel} compartido.
     */
    private static <T> T withoutScan(final PlayerControllerHuman controller,
                                     final Supplier<T> body) {
        final YieldController yields = controller.getYieldController();
        final Map<FPref, String> before = new EnumMap<>(FPref.class);
        for (final FPref pref : SCAN_OFF.keySet()) {
            before.put(pref, yields.getStringPref(pref));
        }
        SCAN_OFF.forEach(yields::setPref);
        try {
            return body.get();
        } finally {
            before.forEach(yields::setPref);
        }
    }

    /** El rescate, con su linea de registro. Devuelve lo que devuelva el reintento. */
    private static <T> T rescue(final PlayerControllerHuman controller,
                                final RuntimeException failure,
                                final String where,
                                final Supplier<T> retry) {
        if (!GUARD || !fromScan(failure)) {
            throw failure;
        }
        final int n = RESCUES.incrementAndGet();
        // Se dice SIEMPRE y con la causa: esto es un fallo del motor tapado, y
        // un fallo tapado en silencio es un fallo que nadie arregla nunca.
        System.out.println("[neo] el barrido de acciones ha fallado en " + where
                + " (rescate " + n + "): " + failure);
        return withoutScan(controller, retry);
    }

    /** Lo mismo para lo que no devuelve nada. */
    private static void rescue(final PlayerControllerHuman controller,
                               final RuntimeException failure,
                               final String where,
                               final Runnable retry) {
        rescue(controller, failure, where, () -> {
            retry.run();
            return null;
        });
    }

    /**
     * El controlador humano de siempre, con el barrido blindado.
     *
     * <p>No se instala solo: la silla del controlador es <b>una</b> y la ocupa
     * {@link ManaColor}, cuyo controlador hereda de este a traves de
     * {@link TriggerSubject.Telling}. Cada arreglo con asiento se apila igual —
     * no se puede sentar a dos.
     */
    /** Pone en {@code relay} el {@code YieldController} de {@code owner}. Ver {@link Guarded#getYieldController}. */
    static boolean shareYields(final PlayerControllerHuman relay, final PlayerControllerHuman owner) {
        try {
            final java.lang.reflect.Field f = PlayerControllerHuman.class.getDeclaredField("yieldController");
            f.setAccessible(true);
            f.set(relay, owner.getYieldController());
            return true;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[neo] el relevo no comparte el pase automatico: " + e);
            return false;
        }
    }

    public static class Guarded extends PlayerControllerHuman {

        /** El controlador al que se releva: el que tiene la interfaz en la mano. */
        private final PlayerControllerHuman owner;

        public Guarded(final Player player, final LobbyPlayer lobby,
                       final PlayerControllerHuman owner) {
            super(player, lobby, owner);
            this.owner = owner;
            sharedYields = shareYields(this, owner);
            try {
                player.getGame().subscribeToEvents(new EndTurnEars(this));
            } catch (final RuntimeException e) {
                System.err.println("[neo] sin memoria de 'Fin de turno': " + e);
            }
        }

        /** Si el campo ya es el del relevado. Ver {@link #getYieldController}. */
        private final boolean sharedYields;

        /**
         * El pase automatico tambien es UNO, el del relevado.
         *
         * <p>Discord, 15-10-2026: <i>"when you click End Turn ... you can not
         * cancel"</i>, y ni un Cyclonic Rift del rival lo paraba. Forge lo trae
         * todo hecho, pero cada pieza mira un controlador distinto: "pasar
         * turno" se arma en este (es el que el motor consulta, ver
         * {@code NeoMatchUI.seated}), y los cortes cuando un rival lanza algo
         * o te ataca ({@code FControlGameEventHandler}, cableado por
         * {@code HostedMatch} ANTES de sentarnos), el aviso "Cediendo hasta el
         * final del turno" con su Cancelar ({@code AbstractGuiGame} e
         * {@code InputLockUI}) miran el relevado. Cada {@code PlayerControllerHuman}
         * crea su propio {@code YieldController}, asi que el pase se quedaba en
         * uno y los cortes y el Cancelar iban al otro, que no tenia nada.
         *
         * <p>El campo es privado y final, y el motor lo usa por el campo (no por
         * el getter), asi que se le pone el del relevado. Su dueño sigue siendo
         * el relevado: comparten interfaz, jugador y asiento, que es todo lo que
         * le pregunta.
         *
         * <p>El getter devuelve el del relevado SOLO si el campo ya es ese. Si la
         * reflexion fallara, el motor seguiria leyendo el campo (el del relevo),
         * y un getter apuntando al otro partiria en dos lo que hoy va junto: el
         * control total y la pausa de la macro escriben por el getter.
         */
        @Override
        public forge.gamemodes.match.YieldController getYieldController() {
            return sharedYields ? owner.getYieldController() : super.getYieldController();
        }

        /**
         * Elegir X: antes de preguntar, cuanta X puedes pagar, para que la
         * lista de botones llegue hasta ahi y no siempre hasta 9. Ver
         * {@link AffordableX}. Con el control total de "empezar a pagar sin
         * recursos" no hay pista: ahi se elige a proposito por encima.
         */
        @Override
        public Integer announceRequirements(final SpellAbility ability, final int min, final int max,
                                            final String announce) {
            final boolean x = "X".equals(announce)
                    && !isFullControl(forge.game.player.PlayerController.FullControlFlag.AllowPaymentStartWithMissingResources);
            if (x) {
                AffordableX.prepare(ability, getPlayer());
            }
            try {
                return super.announceRequirements(ability, min, max, announce);
            } finally {
                if (x) {
                    AffordableX.clear();
                }
            }
        }

        /**
         * La macro es UNA, la del controlador relevado.
         *
         * <p>Reportado el 25-09-2026: la macro no grababa las confirmaciones del
         * stack. El constructor de relevo comparte interfaz, {@code InputQueue}
         * e {@code InputProxy}, pero NO la macro: cada {@code PlayerControllerHuman}
         * crea la suya. Y cada lado graba en la suya — el click de la mesa
         * entra por el relevado ({@code IGameController.selectCard}), y el OK de
         * pasar, confirmar, adivinar, ordenar el stack o elegir color lo graba
         * el input o la pregunta que crea <b>el motor</b>, o sea este. Asi que
         * se pulsaba grabar en una macro y la mitad de lo que se hacia iba a
         * otra que nadie estaba grabando, desde el dia en que se sento el
         * relevo (29-08, {@link ManaColor}) — antes de que existieran las
         * macros en NeoForge.
         */
        @Override
        public IMacroSystem macros() {
            return owner.macros();
        }

        /** Mulligan amistoso: la pregunta no avisa de que haya que devolver nada. Ver {@link FriendlyMulligan}. */
        @Override
        public boolean mulliganKeepHand(final Player firstPlayer, final int cardsToReturn) {
            final Boolean forced = FriendlyMulligan.noteKeepPrompt(getPlayer());
            if (forced != null) {
                return forced;
            }
            return super.mulliganKeepHand(firstPlayer, FriendlyMulligan.toReturn(cardsToReturn));
        }

        /** Mulligan amistoso: no se devuelve ninguna carta al fondo. */
        @Override
        public forge.game.card.CardCollectionView tuckCardsViaMulligan(
                final forge.game.card.CardCollectionView hand, final int cardsToReturn) {
            final forge.game.card.CardCollectionView none = FriendlyMulligan.tuck(cardsToReturn);
            return none != null ? none : super.tuckCardsViaMulligan(hand, cardsToReturn);
        }

        /**
         * Aqui es donde murio la partida del jugador.
         *
         * <p>El barrido es lo primero del metodo y todavia no se le ha pedido
         * nada a nadie, asi que reintentar entero es seguro: no hay ninguna
         * decision a medias que repetir.
         */
        @Override
        public List<SpellAbility> chooseSpellAbilityToPlay() {
            final YieldController y = getYieldController();
            final String highlights = y.getStringPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS);
            final String autoPass = y.getStringPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS);
            if ("true".equalsIgnoreCase(highlights) || "true".equalsIgnoreCase(autoPass)) {
                SCANS.incrementAndGet();
            }
            if (DEBUG) {
                System.out.printf("[acciones] prioridad: resaltados=%s autopass=%s%n",
                        highlights, autoPass);
            }
            // Antes del barrido y en este hilo: mayAutoPass solo lee el veredicto.
            // Dos huecos del motor de la misma familia — evalua antes de que
            // exista la eleccion de la que depende —: el mana de color elegido
            // (HiddenMana) y los objetivos que se comparan con X (XTargets).
            // Y el tercero, que ademas apaga la luz de la carta: improvisar y
            // convocar, que la IA no cuenta como forma de pagar (TapToPay).
            final TapToPay.Verdict tap = "true".equalsIgnoreCase(highlights)
                    || "true".equalsIgnoreCase(autoPass)
                    ? TapToPay.evaluate(getPlayer()) : null;
            overlooked = tap == null ? List.of() : tap.cards();
            hiddenMana = "true".equalsIgnoreCase(autoPass)
                    && (HiddenMana.evaluate(getPlayer()).holds()
                        || XTargets.evaluate(getPlayer()).holds()
                        || (tap != null && tap.holds()));
            try {
                return super.chooseSpellAbilityToPlay();
            } catch (final RuntimeException e) {
                return rescue(this, e, "chooseSpellAbilityToPlay",
                        super::chooseSpellAbilityToPlay);
            }
        }

        /**
         * Hay algo que hacer que el motor no ha visto: pagable con maná que no
         * cuenta ({@link HiddenMana}) o con objetivo en X ({@link XTargets}).
         */
        private volatile boolean hiddenMana;

        /** Lo que se puede lanzar girando y el motor no ha iluminado ({@link TapToPay}). */
        private volatile List<CardView> overlooked = List.of();

        /**
         * El auto-pass de "no tienes nada que hacer", menos cuando el motor
         * se ha equivocado contando tu maná. Un "pasar" que hayas pedido tú
         * ({@code shouldAutoYield}) manda siempre.
         */
        @Override
        public boolean mayAutoPass() {
            noteEndTurn();
            if (!super.mayAutoPass()) {
                return cannotRespond();
            }
            return !hiddenMana || getYieldController().shouldAutoYield();
        }

        // ---------------------------------------------------------------
        // Si no puedes responder, la parada no te para (Discord, 25-10-2026:
        // "the stack will be held up because someone's alt-tabbed even though
        // they have nothing to interact with")
        // ---------------------------------------------------------------

        /**
         * Las paradas de Forge ({@code YieldController.applyInterrupt}: un rival
         * lanza algo, te ataca, te apunta...) cortan tu "Fin de turno" y ademas
         * marcan que la siguiente prioridad NO se pase sola — aunque no tengas
         * nada que hacer. Asi que el que se ha quedado sin mana se quedaba con
         * la prioridad a cada hechizo de cada rival, y en red la mesa entera le
         * esperaba hasta el AFK (un minuto como poco).
         *
         * <p>La parada existe para que puedas responder. Si no puedes
         * ({@code hasAvailableActions}, lo mismo que mira Forge para pasar
         * solo, y sin {@link #hiddenMana}), se sigue pasando cuando: tienes
         * puesto "pasar sola cuando no puedo hacer nada" (con eso puesto, lo
         * unico que te para aqui es la parada), o pulsaste "Fin de turno" en
         * ESTE turno. Quien quiera quedarse la prioridad para un farol apaga
         * "pasar sola" y no pulsa "Fin de turno": eso no cambia.
         *
         * <p>En red vale igual: el anfitrion sienta este controlador tambien en
         * los asientos de los invitados ({@code NeoMatchUI.installForRemoteSeats}).
         */
        private boolean cannotRespond() {
            if (isMacroActive() || hiddenMana) {
                return false;
            }
            final PlayerView me = getLocalPlayerView();
            if (me == null || me.hasAvailableActions()) {
                return false;
            }
            final YieldController y = getYieldController();
            final boolean autoPass = y.getBoolPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS);
            // hasAvailableActions solo esta al dia si el motor ha barrido, y
            // barre con "pasar sola" o con los resaltados puestos. Si no, el
            // dato es viejo y no se puede fiar: se para como siempre.
            if (!autoPass && !y.getBoolPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS)) {
                return false;
            }
            return autoPass || endTurnArmedOn == turn();
        }

        /** El turno en el que tenias puesto "Fin de turno", o -1. */
        private volatile int endTurnArmedOn = -1;

        private int turn() {
            try {
                return getPlayer().getGame().getPhaseHandler().getTurn();
            } catch (final RuntimeException e) {
                return -2;
            }
        }

        /**
         * Apunta si "Fin de turno" esta puesto. Se mira en cada prioridad y
         * cambio de fase ({@link EndTurnEars}), no solo aqui: la parada lo
         * borra al LANZARSE el hechizo, antes de que te vuelva a llegar la
         * prioridad, y para entonces ya no se veria.
         */
        void noteEndTurn() {
            try {
                if (getYieldController().autoPassUntilEndOfTurn()) {
                    endTurnArmedOn = turn();
                }
            } catch (final RuntimeException e) {
                // Solo es memoria: si falla, se para como antes.
            }
        }

        /**
         * El mismo barrido, pedido para resaltar. Desde Forge #12414 (25-10-2026)
         * el motor ya no lo empuja a la interfaz: lo pide cada prompt y lo manda
         * dentro de su {@code setSelection}, justo despues de esta llamada.
         */
        @Override
        public List<CardView> getActionableCards(final boolean paymentMode,
                                                 final Iterable<CardView> emphasized) {
            List<CardView> out;
            try {
                out = super.getActionableCards(paymentMode, emphasized);
            } catch (final RuntimeException e) {
                out = rescue(this, e, "getActionableCards",
                        () -> super.getActionableCards(paymentMode, emphasized));
            }
            // Al lado de lo del motor: la luz de lo que se paga girando. Pagando
            // no — ahi lo que se ilumina es el mana.
            if (getGui() instanceof OverlookedSink ui) {
                ui.setOverlooked(!paymentMode && getYieldController()
                        .getBoolPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS) ? overlooked : List.of());
            }
            return out;
        }

        /** Y las dos puertas de {@code tryAutoPassNow}, que barre igual. */
        @Override
        public void setYieldPref(final FPref pref, final String value) {
            try {
                super.setYieldPref(pref, value);
            } catch (final RuntimeException e) {
                rescue(this, e, "setYieldPref", () -> super.setYieldPref(pref, value));
            }
        }

        @Override
        public void applyYieldUpdate(final forge.gamemodes.match.YieldUpdate update) {
            try {
                super.applyYieldUpdate(update);
            } catch (final RuntimeException e) {
                rescue(this, e, "applyYieldUpdate", () -> super.applyYieldUpdate(update));
            }
        }
    }

    /**
     * Oye cada prioridad y cada cambio de fase para que {@link Guarded} apunte
     * si "Fin de turno" estaba puesto antes de que una parada lo borre. En el
     * hilo del motor y sin hacer nada mas: leer un booleano.
     */
    static final class EndTurnEars {
        private final Guarded seat;

        EndTurnEars(final Guarded seat) {
            this.seat = seat;
        }

        @com.google.common.eventbus.Subscribe
        public void priority(final forge.game.event.GameEventPlayerPriority e) {
            seat.noteEndTurn();
        }

        @com.google.common.eventbus.Subscribe
        public void phase(final forge.game.event.GameEventTurnPhase e) {
            seat.noteEndTurn();
        }
    }

    /**
     * Enciende el barrido a mano. Solo lo usa el comprobador.
     *
     * <p>Sin ventana no pasa por {@code applyEnginePrefs} (que solo corre en
     * modo HUMAN), asi que el barrido no se haria y la prueba pasaria en verde
     * sin haber ejecutado ni una vez el codigo que revienta.
     */
    public static void forceScanOnForTest() {
        final var prefs = FModel.getPreferences();
        prefs.setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, true);
        prefs.setPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS, true);
    }
}
