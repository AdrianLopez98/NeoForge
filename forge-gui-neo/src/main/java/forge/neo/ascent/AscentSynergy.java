package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.deck.CardRelationMatrixGenerator;
import forge.deck.DeckFormat;
import forge.item.PaperCard;

/**
 * Que cartas <b>pegan</b> con un comandante, segun el propio Forge.
 *
 * <h2>De donde sale</h2>
 *
 * <p>No es una lista nuestra ni una heuristica: es
 * {@code CardRelationMatrixGenerator}, la matriz con la que Forge alimenta su
 * boton <i>"Generar mazo"</i>. Se construye contando, sobre los mazos reales de
 * Commander que el motor trae en {@code res/deckgendecks}, <b>cuantas veces
 * aparece cada carta en un mazo que lleva ese comandante</b>. O sea que
 * "sinergia" aqui quiere decir algo comprobable — <i>la gente que juega este
 * comandante juega esta carta</i> — y no lo que a nadie le parezca.
 *
 * <p>Viene precalculada en {@code res/deckgendecks/Commander.dat}, que ya va en
 * el paquete: <b>funciona sin internet</b>, que es condicion para la portable.
 *
 * <h2>Por que existe esta clase y no se llama a la matriz directamente</h2>
 *
 * <p>Por dos cosas que hay que hacer <b>siempre</b> y que es facil olvidar en
 * uno de los dos sitios que la usan:
 *
 * <ol>
 *   <li><b>Cargarla la primera vez.</b> {@code cardPools} nace vacio y solo se
 *       llena si alguien llama a {@code initializeFormat}. Preguntarle sin eso
 *       devuelve "no conozco a nadie" — sin error y sin aviso.</li>
 *   <li><b>Contar con que no conozca al comandante.</b> La matriz solo sabe de
 *       los comandantes que aparecen en los mazos que Forge trae; de los 10.824
 *       jugables, la mayoria <b>no esta</b>. Un comandante desconocido no es un
 *       fallo: es el caso normal, y el que lo llame tiene que seguir
 *       funcionando igual.</li>
 * </ol>
 *
 * <p>El mismo patron que ya usaba {@code forge.neo.adventure.StarterDeck} para
 * el mazo de salida de la Aventura, sacado aqui para que Ascenso no lo
 * duplique.
 *
 * <h2>Lo que cuesta</h2>
 *
 * <p>La carga es de disco y no es gratis, asi que se hace <b>una vez</b> y se
 * queda en el estatico del motor. En Ascenso cae en el sitio bueno sin querer:
 * {@link AscentSeedDeck} pregunta al montar el mazo de salida, o sea al crear
 * la run, que es justo donde el jugador ya esta esperando a que se monte algo.
 * Para cuando terminan el primer combate y hay que ofrecer premio, ya esta.
 */
public final class AscentSynergy {

    private AscentSynergy() {
    }

    /**
     * El pozo de un comandante: cada carta con <b>cuantos mazos suyos</b> la
     * llevan. Vacio si la matriz no lo conoce o no ha podido cargarse.
     */
    public static List<Map.Entry<PaperCard, Integer>> poolOf(final PaperCard commander) {
        if (commander == null) {
            return List.of();
        }
        final Map<String, List<Map.Entry<PaperCard, Integer>>> m = matrix();
        if (m == null) {
            return List.of();
        }
        final List<Map.Entry<PaperCard, Integer>> pool = m.get(commander.getName());
        return pool == null ? List.of() : pool;
    }

    /** Si la matriz sabe algo de este comandante. */
    public static boolean knows(final PaperCard commander) {
        return !poolOf(commander).isEmpty();
    }

    /**
     * Los nombres de todo lo que pega con <b>estos</b> comandantes, juntos.
     *
     * <p>En plural porque un mazo de Commander puede llevar dos (companyeros,
     * <i>partner</i>), y entonces lo que pega con cualquiera de los dos pega
     * con el mazo.
     */
    public static Set<String> namesFor(final Collection<PaperCard> commanders) {
        final Set<String> out = new HashSet<>();
        if (commanders == null) {
            return out;
        }
        for (final PaperCard cmd : commanders) {
            for (final Map.Entry<PaperCard, Integer> e : poolOf(cmd)) {
                out.add(e.getKey().getName());
            }
        }
        return out;
    }

    /**
     * Las cartas del pozo <b>de mas a menos</b> apariciones.
     *
     * <p>Ordenar cuesta, asi que solo lo hace quien de verdad necesita un
     * ranking — {@link AscentSeedDeck}, para decidir que se salva del recorte.
     * Quien solo quiera saber si una carta pega usa {@link #namesFor}.
     */
    public static List<PaperCard> byWeight(final PaperCard commander) {
        final List<Map.Entry<PaperCard, Integer>> pool = new ArrayList<>(poolOf(commander));
        pool.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        final List<PaperCard> out = new ArrayList<>(pool.size());
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            out.add(e.getKey());
        }
        return out;
    }

    /**
     * Cada carta de la matriz con <b>cuantos mazos de Commander la llevan</b>,
     * sumando los de todos los comandantes, de mas a menos.
     *
     * <p>Es la lista de <i>staples</i>: lo que la gente mete en su mazo sea cual
     * sea el comandante (Sol Ring, Command Tower, Cultivate...). Sirve para
     * rellenar un mazo con cartas que se juegan de verdad cuando el pozo de su
     * comandante no llega, en vez de con cartas al azar del color. Filtrar por
     * identidad de color es cosa de quien la usa.
     *
     * <p>Se calcula una vez y se queda: son unos cientos de miles de sumas.
     * Vacia si la matriz no esta.
     */
    public static List<Map.Entry<PaperCard, Integer>> popularity() {
        List<Map.Entry<PaperCard, Integer>> p = popularity;
        if (p != null) {
            return p;
        }
        final Map<String, List<Map.Entry<PaperCard, Integer>>> m = matrix();
        if (m == null) {
            return List.of();
        }
        synchronized (AscentSynergy.class) {
            if (popularity != null) {
                return popularity;
            }
            final Map<String, Map.Entry<PaperCard, Integer>> sum = new java.util.HashMap<>();
            for (final List<Map.Entry<PaperCard, Integer>> pool : m.values()) {
                for (final Map.Entry<PaperCard, Integer> e : pool) {
                    sum.merge(e.getKey().getName(),
                            new java.util.AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()),
                            (a, b) -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                                    a.getKey(), a.getValue() + b.getValue()));
                }
            }
            final List<Map.Entry<PaperCard, Integer>> out = new ArrayList<>(sum.values());
            out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            popularity = java.util.Collections.unmodifiableList(out);
            return popularity;
        }
    }

    private static volatile List<Map.Entry<PaperCard, Integer>> popularity;

    /**
     * La matriz de Commander, cargada la primera vez que hace falta.
     *
     * <p>Sincronizado porque el premio se pide desde el hilo de interfaz y el
     * mazo de salida desde otro: dos cargas a la vez sobre el mismo estatico de
     * Forge no es algo que haya que averiguar.
     *
     * <p>Si la carga falla —no esta el {@code .dat}, o el fichero esta roto— se
     * marca y <b>no se vuelve a intentar</b>. Reintentar en cada premio seria
     * pagar el disco una y otra vez para llegar a la misma nada.
     */
    private static Map<String, List<Map.Entry<PaperCard, Integer>>> matrix() {
        final String format = DeckFormat.Commander.toString();
        Map<String, List<Map.Entry<PaperCard, Integer>>> m =
                CardRelationMatrixGenerator.cardPools.get(format);
        if (m != null) {
            return m;
        }
        synchronized (AscentSynergy.class) {
            m = CardRelationMatrixGenerator.cardPools.get(format);
            if (m != null) {
                return m;
            }
            if (failed) {
                return null;
            }
            try {
                CardRelationMatrixGenerator.initializeFormat(DeckFormat.Commander);
            } catch (final RuntimeException e) {
                System.out.println("[ascenso] sin matriz de sinergia: " + e);
            }
            m = CardRelationMatrixGenerator.cardPools.get(format);
            if (m == null) {
                failed = true;
                System.out.println("[ascenso] la matriz de sinergia no esta disponible;"
                        + " los mazos y los premios se sortean como antes");
            }
            return m;
        }
    }

    /** Si ya se intento cargar y no salio. No se reintenta. */
    private static boolean failed;
}
