package forge.neo.match;

import forge.game.GameView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.gamemodes.match.YieldController;
import forge.gamemodes.match.YieldUpdate;
import forge.interfaces.IGameController;

/**
 * <b>"Resolverlo todo"</b> (Discord, 08-10-2026, Munkster: <i>"a creature is
 * getting six +1/+1 counters from an enchantment happening six times.
 * Currently it does those one at a time until the stack empties"</i>).
 *
 * <p>Es el "Resolve entire stack" del Forge de escritorio ({@code VStack}):
 * pasar la prioridad hasta que el stack se vacie. <b>No junta nada</b> —las
 * reglas no dejan: cada disparo se resuelve aparte y los rivales siguen
 * pudiendo responder a cada uno—, solo te ahorra el OK de cada vez. Se corta
 * solo si un rival lanza algo ({@code respectsInterrupts}, con los avisos de
 * {@code NeoGame.applySmartPass}) o con el Cancelar del aviso. Las preguntas
 * que traigan los disparos (objetivos, "puedes...") llegan igual: esto solo
 * toca la prioridad.
 *
 * <p>Lo usan las dos interfaces ({@code NeoMatchUI} y el {@code AndroidMatchUI}
 * de Android, por el jar): lo delicado son los dos controladores, y eso no se
 * puede escribir dos veces. Nada de API de Java que Android no tenga en la 26.
 */
public final class ResolveAll {

    private ResolveAll() {
    }

    /**
     * Lo enciende.
     *
     * <p>Se le pone a LOS DOS controladores, el de la interfaz y el sentado
     * ({@link NeoMatchUI#seated}, el relevo de {@code ManaColor}, que tiene SU
     * PROPIO {@code YieldController}). Quien decide si la prioridad se pasa
     * sola es el sentado; pero quien escucha si un rival lanza algo
     * ({@code FControlGameEventHandler}) y quien atiende el Cancelar del aviso
     * ({@code InputLockUI}) es el de la interfaz. Con uno solo se queda a
     * medias: o no se pasa nada, o no se corta nunca. Lo que lo apaga en el de
     * la interfaz se copia al sentado con {@link #mirrorOff}.
     *
     * <p>El orden importa: primero el sentado, porque el de la interfaz pasa
     * la prioridad en el acto ({@code tryAutoPassNow}) y la siguiente vez ya
     * pregunta el sentado. En un invitado de red no hay relevo: va al
     * anfitrion por {@code sendYieldUpdate}, que es lo que hace Forge.
     */
    public static void arm(final IGameController gc, final PlayerView me) {
        if (gc == null || me == null) {
            return;
        }
        final IGameController engine = NeoMatchUI.seated(gc);
        if (engine != gc && engine.getYieldController() != null) {
            engine.getYieldController().setAutoPassUntilStackEmpty(true, true);
        }
        gc.sendYieldUpdate(new YieldUpdate.StackYield(me, true, true));
    }

    /**
     * Lo que apaga el "resolverlo todo" en el controlador de la interfaz —un
     * rival lanza algo, el Cancelar del aviso— llega a
     * {@code IGuiGame.applyYieldUpdate}; desde ahi se le apaga tambien al
     * sentado. Ver {@link #arm}.
     */
    public static void mirrorOff(final IGameController gc, final YieldUpdate update) {
        if (!(update instanceof YieldUpdate.StackYield) || ((YieldUpdate.StackYield) update).active()) {
            return;
        }
        final IGameController engine = gc == null ? null : NeoMatchUI.seated(gc);
        if (engine != null && engine != gc && engine.getYieldController() != null) {
            engine.getYieldController().setAutoPassUntilStackEmpty(false, false);
        }
    }

    /**
     * Cuantas cosas resolveria ahora, o 0 si no toca ofrecerlo.
     *
     * <p>Solo con dos o mas en el stack —con una, OK ya hace lo mismo— y si
     * {@code eligible} (la interfaz sabe si lo que tiene delante es la
     * prioridad de siempre, con OK encendido y nada que elegir ni pagar).
     *
     * <p>De paso limpia la marca que se quedo puesta: el motor la quita cuando
     * la lee con el stack vacio, y si nadie la lee se queda — y entonces no
     * volveria a ofrecerse nunca.
     */
    public static int count(final GameView gv, final IGameController gc, final boolean eligible) {
        int n = 0;
        if (gv != null && gv.getStack() != null) {
            for (final StackItemView ignored : gv.getStack()) {
                n++;
            }
        }
        boolean yielding = false;
        if (gc != null) {
            final IGameController[] both = {gc, NeoMatchUI.seated(gc)};
            for (final IGameController c : both) {
                final YieldController y = c.getYieldController();
                if (y != null && y.autoPassUntilStackEmpty()) {
                    if (n == 0) {
                        y.setAutoPassUntilStackEmpty(false, false);
                    } else {
                        yielding = true;
                    }
                }
            }
        }
        return n < 2 || yielding || !eligible ? 0 : n;
    }

    /** Solo pruebas: si alguno de los dos controladores sigue con ello puesto. */
    public static boolean isOn(final IGameController gc) {
        if (gc == null) {
            return false;
        }
        final IGameController engine = NeoMatchUI.seated(gc);
        return (gc.getYieldController() != null && gc.getYieldController().autoPassUntilStackEmpty())
                || (engine.getYieldController() != null && engine.getYieldController().autoPassUntilStackEmpty());
    }
}
