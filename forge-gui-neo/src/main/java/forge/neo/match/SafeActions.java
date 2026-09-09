package forge.neo.match;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import forge.LobbyPlayer;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.DeclineScope;
import forge.gamemodes.match.YieldController;
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
 * {@code pushActionableCards}, {@code setYieldPref} y {@code applyYieldUpdate}
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
 * en la traza. Lo demas sube tal cual. Si algun dia Card-Forge pone el
 * {@code getLastKnownZone()} que falta, esto deja de saltar y
 * {@code run.cmd actionscheck} se pone en rojo diciendo que ya sobra.
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
     * <p>Los overrides viven en el {@link YieldController} de <b>este</b>
     * controlador, que nace con el relevo y muere con la partida: no se escribe
     * nada en {@code %APPDATA%} ni se toca el {@code FModel} compartido.
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
     * {@link ManaColor}, cuyo controlador hereda de este. Si algun dia hace
     * falta un tercer arreglo con asiento, se apila igual — no se puede sentar
     * a dos.
     */
    public static class Guarded extends PlayerControllerHuman {

        public Guarded(final Player player, final LobbyPlayer lobby,
                       final PlayerControllerHuman owner) {
            super(player, lobby, owner);
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
            if (DEBUG) {
                final YieldController y = getYieldController();
                System.out.printf("[acciones] prioridad: resaltados=%s autopass=%s%n",
                        y.getStringPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS),
                        y.getStringPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS));
            }
            try {
                return super.chooseSpellAbilityToPlay();
            } catch (final RuntimeException e) {
                return rescue(this, e, "chooseSpellAbilityToPlay",
                        super::chooseSpellAbilityToPlay);
            }
        }

        /** El mismo barrido, pedido desde el hilo de interfaz para resaltar. */
        @Override
        public void pushActionableCards(final boolean paymentMode,
                                        final Iterable<CardView> emphasized) {
            try {
                super.pushActionableCards(paymentMode, emphasized);
            } catch (final RuntimeException e) {
                rescue(this, e, "pushActionableCards",
                        () -> super.pushActionableCards(paymentMode, emphasized));
            }
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
