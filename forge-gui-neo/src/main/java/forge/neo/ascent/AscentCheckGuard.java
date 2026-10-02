package forge.neo.ascent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que un comprobador de Ascenso le toma prestado al jugador, y se lo
 * devuelve como estaba: su run, sus hitos y la carpeta {@code decks/ascenso/}.
 *
 * <p>Se crea antes de la primera sonda y se llama a {@link #restore()} en el
 * {@code finally}. Lo usan {@code AscentCheck} y {@code AscentRelicCheck}: los
 * dos montan runs de mentira sobre los mismos ficheros que el juego.
 *
 * <h2>Las tres cosas</h2>
 *
 * <ul>
 *   <li><b>La run</b> ({@code ascent.*} en {@code neo.properties}): cada
 *       {@link AscentRun#begin} la pisa y cada {@code discard()} la borra. Sin
 *       reponerla, pasar los comprobadores le BORRA al jugador la run que tenga
 *       a medias — y en un modo donde no se puede volver atras eso no tiene
 *       arreglo.</li>
 *   <li><b>Los hitos</b>: no viven bajo {@code ascent.} — viven aparte justo
 *       para sobrevivir a la derrota — asi que la foto de la run no los recoge.
 *       Sin esto, los comprobadores le regalarian (o le borrarian) desbloqueos
 *       que no ha ganado.</li>
 *   <li><b>Los mazos</b>: cada run de mentira deja un {@code .dck}, y si una
 *       sonda se olvida del {@code discard()} se queda ahi para siempre. Paso:
 *       el 02-10-2026 habia 106 mazos muertos en la carpeta, todos con el mismo
 *       comandante — la sonda {@code sinergia} montaba su run y no la soltaba,
 *       uno mas en cada {@code ascentcheck} desde el 20-09-2026.</li>
 * </ul>
 *
 * <h2>Se borra lo que escribio ESTE proceso, no "lo nuevo"</h2>
 *
 * <p>Medido el mismo 02-10-2026: mientras corria {@code ascentcheck}, el juego
 * estaba abierto y alguien empezo una run de verdad. Su {@code .dck} aparecio
 * en la carpeta en mitad de la pasada. Borrar lo que no estaba al empezar se lo
 * habria llevado; por eso se borra solo lo que {@link AscentDecks#save} escribio
 * desde aqui, que no estaba antes y que no es el mazo de la run repuesta.
 *
 * <h2>Si tiene que borrar algo, es un fallo</h2>
 *
 * <p>La carpeta se queda limpia igual, pero {@link #restore()} devuelve lo que
 * ha borrado para que el comprobador lo diga en rojo. Si limpiara en silencio,
 * la sonda nueva que se olvide del {@code discard()} quedaria tapada para
 * siempre, que es como se llego a 106.
 */
final class AscentCheckGuard {

    private final Map<String, String> run;
    private final String feats;
    private final Set<String> decks;

    AscentCheckGuard() {
        this.run = AscentRun.snapshot();
        this.feats = AscentUnlocks.rawFeatsForTest();
        this.decks = AscentDecks.names();
    }

    /**
     * Lo devuelve todo.
     *
     * @return los mazos de runs de prueba que se habian quedado en la carpeta
     *         (ya borrados). Vacio si cada sonda solto lo suyo
     */
    List<String> restore() {
        AscentRun.restore(run);
        AscentUnlocks.setRawFeatsForTest(feats);
        final AscentRun current = AscentRun.current();
        return AscentDecks.removeWrittenSince(decks,
                current == null ? null : current.getDeckName());
    }
}
