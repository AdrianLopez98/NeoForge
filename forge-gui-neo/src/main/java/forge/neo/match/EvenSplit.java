package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Repartir a partes iguales</b> (Discord, 07-10-2026: <i>"the split for me
 * just puts all of the counters into the first creature, maybe do something
 * like distribute them equally"</i>).
 *
 * <p>Legal siempre: suma exactamente el total y nadie pasa de su tope. Con
 * {@code atLeastOne}, una a cada uno antes que nada. El resto que no se divide
 * va a los primeros (44 entre 3: 15, 15, 14), y lo que un tope no deja meter
 * pasa a los demas.
 *
 * <p>Pura y compartida con Android por el jar: el boton del escritorio
 * ({@code AmountDialog}) y el de Android ({@code AmountDialog.kt}) la usan.
 */
public final class EvenSplit {

    private EvenSplit() {
    }

    /**
     * @param caps el tope de cada uno (0 o negativo = sin tope: el total)
     * @return cuanto le toca a cada uno, en el mismo orden
     */
    public static List<Integer> of(final List<Integer> caps, final int total, final boolean atLeastOne) {
        final int n = caps == null ? 0 : caps.size();
        final List<Integer> out = new ArrayList<>(n);
        final int[] cap = new int[n];
        for (int i = 0; i < n; i++) {
            final Integer c = caps.get(i);
            cap[i] = c == null || c <= 0 ? total : Math.min(c, total);
            out.add(0);
        }
        if (n == 0 || total <= 0) {
            return out;
        }
        int left = total;
        if (atLeastOne) {
            for (int i = 0; i < n && left > 0; i++) {
                if (cap[i] > 0) {
                    out.set(i, 1);
                    left--;
                }
            }
        }
        // De uno en uno, por turnos, a quien aun le quepa: es la forma mas
        // sencilla de que el reparto salga igualado Y respete los topes.
        while (left > 0) {
            boolean placed = false;
            for (int i = 0; i < n && left > 0; i++) {
                if (out.get(i) < cap[i]) {
                    out.set(i, out.get(i) + 1);
                    left--;
                    placed = true;
                }
            }
            if (!placed) {
                break;
            }
        }
        return out;
    }
}
