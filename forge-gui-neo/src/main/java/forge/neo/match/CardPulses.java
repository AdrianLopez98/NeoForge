package forge.neo.match;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.neo.ui.TableScreen;

/**
 * Las animaciones de "esto acaba de cambiar" (golpe de contadores, destello de
 * dano), <b>juntas</b> antes de cruzar al hilo de interfaz.
 *
 * <p>Sale de investigar un cierre de Discord (30-09-2026): una mesa con 380
 * fichas y 300 disparos en la pila acabo en {@code OutOfMemoryError}. Cada
 * aviso del motor mandaba SU tarea y SU animacion a la interfaz, sin tope.
 * <b>No era la causa</b> — fue la memoria del motor, ver las trampas conocidas; con
 * 200.000 avisos la interfaz se pone al dia en 2 s — pero lo que se encola
 * no deberia crecer con los avisos.
 *
 * <p>Aqui cada carta se apunta una sola vez, y en la cola de la interfaz hay
 * como mucho UNA tarea que las reparte todas — la misma idea que
 * {@code TableBinder.requestRefresh}. Da igual cuantos avisos lleguen: lo que
 * se encola crece con las cartas, no con los avisos.
 */
public final class CardPulses {

    private final TableScreen table;
    private final Consumer<Runnable> ui;
    private final Set<CardView> bumps = ConcurrentHashMap.newKeySet();
    private final Set<CardView> hits = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    /** @param ui como se manda algo al hilo de interfaz */
    public CardPulses(final TableScreen table, final Consumer<Runnable> ui) {
        this.table = table;
        this.ui = ui;
    }

    boolean isFor(final TableScreen t) {
        return table == t;
    }

    /** Desde cualquier hilo: a esta carta le han cambiado los contadores. */
    public void bump(final CardView card) {
        if (card != null && bumps.add(card)) {
            schedule();
        }
    }

    /** Desde cualquier hilo: a esta carta le acaban de hacer dano. */
    public void hit(final CardView card) {
        if (card != null && hits.add(card)) {
            schedule();
        }
    }

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) {
            ui.accept(this::flush);
        }
    }

    /** En el hilo de interfaz. */
    private void flush() {
        // Antes de vaciar: lo que llegue mientras tanto encola otra ronda.
        scheduled.set(false);
        drain(hits, table::flashHit);
        drain(bumps, table::bumpCard);
    }

    private static void drain(final Set<CardView> set, final Consumer<CardView> action) {
        final Iterator<CardView> it = set.iterator();
        while (it.hasNext()) {
            final CardView c = it.next();
            it.remove();
            action.accept(c);
        }
    }
}
