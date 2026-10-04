package forge.neo.match;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.gui.interfaces.IGuiGame;

/**
 * <b>La interfaz de UN asiento en una partida con varias personas en el mismo
 * aparato</b> (hot seat, itch.io 04-10-2026).
 *
 * <p>Con varias personas el motor usa UNA interfaz para todas (ver
 * {@code NeoGame.play}), y cuando pregunta algo por dialogo —elegir una carta
 * para descartar, un modo, ordenar disparos— no dice a quien: el controlador
 * llama a {@code getGui().getChoices(...)} y ya. Los clics si lo dicen
 * ({@code InputProxy} llama a {@code setCurrentPlayer}), los dialogos no.
 * Sin esto, la pregunta de B salia con la mano de A delante.
 *
 * <p>Asi que a cada asiento se le pone su propia interfaz: esta, que es la
 * misma {@link NeoMatchUI} vista desde ese asiento. Antes de cualquier
 * pregunta gira la mesa hacia el (con su cortina), y responde
 * {@code isUiSetToSkipPhase} sabiendo quien pregunta. Todo lo demas pasa tal
 * cual.
 *
 * <p>Se pone en el controlador SENTADO ({@code player.getController()}, el de
 * {@link ManaColor}), que es por donde salen los dialogos; las entradas siguen
 * yendo por la interfaz original del dueño ({@code InputProxy}), sin tocarla.
 * Por eso quien necesite la {@link NeoMatchUI} de verdad la saca con
 * {@link NeoMatchUI#of}.
 *
 * <p>Lo usan las dos interfaces: el escritorio ({@link NeoMatchUI}) y Android
 * ({@code AndroidMatchUI}, por el jar). Cada una pone su {@link Host}.
 * Java puro y sin API que Android no tenga en la 26.
 */
public final class HotSeatGui implements InvocationHandler {

    /** Lo que la interfaz de verdad sabe hacer con varias personas. */
    public interface Host {
        /** Gira la mesa hacia este asiento (con su cortina), si no lo esta ya. */
        void bringToFront(PlayerView seat);

        /** La parada de fase vista desde el asiento que pregunta. */
        boolean isUiSetToSkipPhaseFor(PlayerView asking, PlayerView turn, PhaseType phase);
    }

    /** Lo que pregunta algo a una persona: antes, la mesa se gira hacia ella. */
    private static final Set<String> ASKS = new java.util.HashSet<>(java.util.Arrays.asList(
            "getAbilityToPlay", "assignCombatDamage", "assignGenericAmount",
            "message", "showErrorDialog", "showConfirmDialog", "showOptionDialog",
            "showInputDialog", "confirm", "getChoices", "getInteger", "one", "oneOrNone",
            "many", "order", "insertInList", "sideboard", "chooseSingleEntityForEffect",
            "chooseEntitiesForEffect", "manipulateCardList", "chooseTargetsFor"));

    private final IGuiGame ui;
    private final Host host;
    private final PlayerView seat;

    private HotSeatGui(final IGuiGame ui, final Host host, final PlayerView seat) {
        this.ui = ui;
        this.host = host;
        this.seat = seat;
    }

    /** La interfaz {@code ui} vista desde {@code seat}. */
    public static IGuiGame wrap(final IGuiGame ui, final Host host, final PlayerView seat) {
        return (IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(),
                new Class<?>[] {IGuiGame.class}, new HotSeatGui(ui, host, seat));
    }

    /** La interfaz que hay detras, si esto es una de estas; si no, null. */
    public static IGuiGame unwrap(final Object gui) {
        if (gui != null && Proxy.isProxyClass(gui.getClass())
                && Proxy.getInvocationHandler(gui) instanceof HotSeatGui) {
            return ((HotSeatGui) Proxy.getInvocationHandler(gui)).ui;
        }
        return null;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final String name = method.getName();
        if ("isUiSetToSkipPhase".equals(name) && args != null && args.length == 2) {
            return host.isUiSetToSkipPhaseFor(seat, (PlayerView) args[0], (PhaseType) args[1]);
        }
        if ("equals".equals(name) && args != null && args.length == 1) {
            return proxy == args[0];
        }
        if ("hashCode".equals(name) && (args == null || args.length == 0)) {
            return System.identityHashCode(proxy);
        }
        if ("toString".equals(name) && (args == null || args.length == 0)) {
            return "HotSeatGui[" + (seat == null ? "?" : seat.getName()) + "]";
        }
        if (ASKS.contains(name)) {
            host.bringToFront(seat);
        }
        try {
            return method.invoke(ui, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }
}
