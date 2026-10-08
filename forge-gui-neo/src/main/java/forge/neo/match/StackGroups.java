package forge.neo.match;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;

/**
 * Las entradas <b>iguales y seguidas</b> del stack, juntas en una (Discord,
 * 08-10-2026, Munkster: <i>"an option to collapse similar triggers ... if
 * there are six of the same trigger"</i>). Seis disparos del mismo encantamiento
 * eran seis filas identicas, y el cartel central cambiaba seis veces para decir
 * lo mismo.
 *
 * <p><b>Es solo como se pinta.</b> Las reglas no dejan juntar seis disparos en
 * uno —cada uno se resuelve aparte, y entre uno y otro todos pueden responder—,
 * asi que el stack del motor no se toca: se cuentan las que son iguales y se
 * ensenya una con su "×6". Lo que hace que vayan rapido es el otro boton,
 * "Resolverlo todo" ({@code NeoMatchUI.resolveStack}).
 *
 * <p>"Iguales" es lo que de verdad no se distingue: la misma carta, del mismo
 * jugador, el mismo texto (que ya lleva sus objetivos y lo que se paga de mas)
 * y los mismos objetivos. Y <b>seguidas</b>: si entre dos iguales hay otra
 * cosa, son dos filas, porque el orden en el que se resuelven es justo lo que
 * hay que poder leer.
 *
 * <p>Compartida con Android por el jar: nada de API de Java que no tenga en
 * la 26.
 */
public final class StackGroups {

    private StackGroups() {
    }

    /** Un tramo de entradas iguales seguidas. */
    public static final class Run {
        private final StackItemView first;
        private final int start;
        private final int count;

        Run(final StackItemView first, final int start, final int count) {
            this.first = first;
            this.start = start;
            this.count = count;
        }

        /** La primera (la que se resuelve antes) del tramo. */
        public StackItemView first() {
            return first;
        }

        /** Donde empieza, contando desde arriba y desde 0. */
        public int start() {
            return start;
        }

        /** Cuantas son: 1 si va sola. */
        public int count() {
            return count;
        }
    }

    /** El stack en tramos, de arriba abajo. Vacio si no hay nada. */
    public static List<Run> runs(final Iterable<StackItemView> stack) {
        final List<Run> out = new ArrayList<>();
        if (stack == null) {
            return out;
        }
        StackItemView first = null;
        int start = 0;
        int count = 0;
        int i = 0;
        for (final StackItemView item : stack) {
            if (item == null) {
                i++;
                continue;
            }
            if (first != null && same(first, item)) {
                count++;
            } else {
                if (first != null) {
                    out.add(new Run(first, start, count));
                }
                first = item;
                start = i;
                count = 1;
            }
            i++;
        }
        if (first != null) {
            out.add(new Run(first, start, count));
        }
        return out;
    }

    /** Cuantas iguales hay arriba del todo: 0 con el stack vacio. */
    public static int topRun(final Iterable<StackItemView> stack) {
        if (stack == null) {
            return 0;
        }
        final Iterator<StackItemView> it = stack.iterator();
        if (!it.hasNext()) {
            return 0;
        }
        final StackItemView top = it.next();
        if (top == null) {
            return 0;
        }
        int n = 1;
        while (it.hasNext()) {
            final StackItemView next = it.next();
            if (next == null || !same(top, next)) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Si dos entradas no se distinguen en nada que se vea. */
    public static boolean same(final StackItemView a, final StackItemView b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.isTrigger() == b.isTrigger()
                    && a.isAbility() == b.isAbility()
                    && sameCard(a.getSourceCard(), b.getSourceCard())
                    && samePlayer(a.getActivatingPlayer(), b.getActivatingPlayer())
                    && Objects.equals(withoutCause(a.getText()), withoutCause(b.getText()))
                    && Objects.equals(a.getOptionalCostString(), b.getOptionalCostString())
                    && sameCards(a.getTargetCards(), b.getTargetCards())
                    && samePlayers(a.getTargetPlayers(), b.getTargetPlayers());
        } catch (final RuntimeException e) {
            // Una vista a medio actualizar: mejor dos filas que juntar mal.
            return false;
        }
    }

    /**
     * El texto sin el apunte final de QUE lo disparo: el motor le pega a cada
     * disparo un {@code [Zone Changer: Grizzly Bears (204)]}, y con eso seis
     * "ganas 1 vida" de Soul Warden no eran nunca iguales. Ese apunte dice que
     * criatura entro, no lo que el disparo hace. Lo que si cambia lo que hace
     * —a quien apunta— se compara aparte.
     */
    public static String withoutCause(final String text) {
        if (text == null) {
            return null;
        }
        return CAUSE.matcher(text).replaceAll("").trim();
    }

    private static final java.util.regex.Pattern CAUSE =
            java.util.regex.Pattern.compile("(\\s*\\[[^\\[\\]]*\\])+\\s*$");

    private static boolean sameCard(final CardView a, final CardView b) {
        return a == null ? b == null : b != null && a.getId() == b.getId();
    }

    private static boolean samePlayer(final PlayerView a, final PlayerView b) {
        return a == null ? b == null : b != null && a.getId() == b.getId();
    }

    private static boolean sameCards(final Iterable<CardView> a, final Iterable<CardView> b) {
        final List<Integer> x = new ArrayList<>();
        final List<Integer> y = new ArrayList<>();
        if (a != null) {
            for (final CardView c : a) {
                x.add(c == null ? -1 : c.getId());
            }
        }
        if (b != null) {
            for (final CardView c : b) {
                y.add(c == null ? -1 : c.getId());
            }
        }
        return x.equals(y);
    }

    private static boolean samePlayers(final Iterable<PlayerView> a, final Iterable<PlayerView> b) {
        final List<Integer> x = new ArrayList<>();
        final List<Integer> y = new ArrayList<>();
        if (a != null) {
            for (final PlayerView p : a) {
                x.add(p == null ? -1 : p.getId());
            }
        }
        if (b != null) {
            for (final PlayerView p : b) {
                y.add(p == null ? -1 : p.getId());
            }
        }
        return x.equals(y);
    }
}
