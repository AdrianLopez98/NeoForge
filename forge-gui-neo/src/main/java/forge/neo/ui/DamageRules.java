package forge.neo.ui;

/**
 * Las reglas del reparto de dano de combate, sin nada de pantalla.
 *
 * <p>Viven aparte de {@link DamageDialog} para poder comprobarlas sin ventana
 * ({@code DamageCheck}): el motor no vuelve a validar el reparto que le llega,
 * asi que si aqui se cuela uno ilegal, la partida lo aplica tal cual. Paso el
 * 25-09-2026: con arrollar y {@code overrideOrder} se podia mandar todo el dano
 * al jugador sin tocar a los bloqueadores.
 *
 * <p>Son las mismas que el dialogo de Forge ({@code VAssignCombatDamage}):
 * <ul>
 *   <li>Un objetivo "con orden" solo recibe dano cuando todos los de delante
 *       tienen el letal. El defensor (arrollar) va siempre con orden; los
 *       bloqueadores, solo si el motor no manda {@code overrideOrder}.</li>
 *   <li>Si al quitar dano un objetivo deja de tener el letal, lo que habia
 *       detras con orden se borra ({@code checkDamageQueue} en Forge).</li>
 * </ul>
 *
 * <p>Es publica porque la usa tambien NeoForge Android (su {@code RepartoDialog}),
 * que lee este jar: la regla vive en un solo sitio.
 */
public final class DamageRules {

    private final int total;
    private final int[] lethal;
    private final boolean[] ordered;
    private final int[] amounts;

    /**
     * @param total   el dano a repartir
     * @param lethal  el letal de cada objetivo, en el orden de la fila
     * @param ordered si cada objetivo tiene que esperar a los de delante
     */
    public DamageRules(final int total, final int[] lethal, final boolean[] ordered) {
        this.total = total;
        this.lethal = lethal.clone();
        this.ordered = ordered.clone();
        this.amounts = new int[lethal.length];
    }

    /**
     * Las reglas de un reparto de combate: el defensor (arrollar) siempre
     * espera a que los bloqueadores tengan el letal; entre bloqueadores solo
     * hay orden si el motor no manda {@code overrideOrder}. Aqui y no en el
     * dialogo, para que {@code DamageCheck} pruebe justo lo que fallo.
     *
     * @param defender cual de los objetivos es el defensor (el resto, bloqueadores)
     */
    public static DamageRules combat(final int total, final int[] lethal, final boolean[] defender,
                              final boolean overrideOrder) {
        final boolean[] ordered = new boolean[lethal.length];
        for (int i = 0; i < lethal.length; i++) {
            ordered[i] = !overrideOrder || defender[i];
        }
        return new DamageRules(total, lethal, ordered);
    }

    public int size() {
        return amounts.length;
    }

    public int amount(final int index) {
        return amounts[index];
    }

    public int lethal(final int index) {
        return lethal[index];
    }

    public int left() {
        int spent = 0;
        for (final int v : amounts) {
            spent += v;
        }
        return total - spent;
    }

    /** Si un punto mas a este objetivo seria legal. */
    public boolean canAdd(final int index) {
        return left() > 0 && (!ordered[index] || frontIsLethal(index));
    }

    /** Suma (o resta) un punto. Devuelve si ha cambiado algo. */
    public boolean add(final int index, final int delta) {
        if (delta > 0 && !canAdd(index)) {
            return false;
        }
        if (delta < 0 && amounts[index] <= 0) {
            return false;
        }
        amounts[index] += delta;
        if (delta < 0) {
            dropUnreachable();
        }
        return true;
    }

    public void reset() {
        java.util.Arrays.fill(amounts, 0);
    }

    /** Reparto letal en orden, y lo que sobre al ultimo: lo que quiere el jugador casi siempre. */
    public void autoAssign() {
        int left = total;
        for (int i = 0; i < amounts.length; i++) {
            final int v = Math.max(0, Math.min(lethal[i], left));
            amounts[i] = v;
            left -= v;
        }
        // En Magic el exceso hay que asignarlo igual: no se puede dejar sin repartir.
        if (left > 0 && amounts.length > 0) {
            amounts[amounts.length - 1] += left;
        }
    }

    /**
     * Si este objetivo esta esperando su turno: tiene orden y alguno de delante
     * aun no tiene el letal. No mira si queda dano (eso es {@link #canAdd}):
     * sirve para decirle al jugador POR QUE no puede darle mas.
     */
    public boolean waiting(final int index) {
        return ordered[index] && !frontIsLethal(index);
    }

    /** Copia del reparto actual, en el orden de la fila. */
    public int[] amounts() {
        return amounts.clone();
    }

    /**
     * Pone un reparto entero de golpe (el automatico de fuera, "reiniciar").
     * No lo valida: es para cuando el reparto ya viene hecho de un sitio legal.
     */
    public void load(final int[] values) {
        if (values.length != amounts.length) {
            throw new IllegalArgumentException("se esperaban " + amounts.length + " cantidades");
        }
        System.arraycopy(values, 0, amounts, 0, amounts.length);
    }

    /** Todos los de delante de este tienen ya el letal. */
    private boolean frontIsLethal(final int index) {
        for (int i = 0; i < index; i++) {
            if (amounts[i] < lethal[i]) {
                return false;
            }
        }
        return true;
    }

    /** Lo que ha dejado de ser alcanzable, a cero. */
    private void dropUnreachable() {
        boolean alive = false;
        for (int i = 0; i < amounts.length; i++) {
            if (alive && ordered[i]) {
                amounts[i] = 0;
            } else {
                alive |= amounts[i] < lethal[i];
            }
        }
    }
}
