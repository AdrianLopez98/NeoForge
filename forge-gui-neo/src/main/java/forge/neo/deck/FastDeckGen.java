package forge.neo.deck;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.deck.CardArchetypeLDAGenerator;
import forge.deck.CardRelationMatrixGenerator;
import forge.deck.DeckFormat;
import forge.deck.io.Archetype;
import forge.deck.io.CardThemedLDAIO;
import forge.deck.io.CardThemedMatrixIO;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import org.apache.commons.lang3.tuple.Pair;

/**
 * <b>Las tablas del generador de mazos de Forge, leidas con bufer.</b>
 *
 * <p>Discord, 14-10-2026: <i>"Faster load times for the love of god"</i>.
 * Medido ese dia en el PC: de los 19 s hasta poder jugar, unos 10 se iban en
 * {@code FModel.initialize} leyendo {@code res/deckgendecks} (11 MB, 6,9 de
 * ellos {@code Commander.dat}) con un {@code ObjectInputStream} sobre un
 * {@code FileInputStream} SIN bufer: cientos de miles de lecturas de un byte
 * ({@code FileInputStream.read0} en las muestras), cada una una llamada al
 * sistema. Sin esas tablas no hay mazos generados por sinergia (Ascenso, los
 * duelos de Commander de la Quest, torneos...), asi que no se pueden quitar.
 *
 * <p>Sin tocar Forge (regla de oro): en el gancho de antes de leer las cartas
 * ({@link #skipInEngine}) se apaga {@code DECKGEN_CARDBASED} SOLO en memoria,
 * para que {@code FModel.initialize} no las lea; y justo al acabar
 * ({@link #loadNow}) se repone y se leen los MISMOS ficheros, a los MISMOS
 * mapas publicos de Forge, con un {@link BufferedInputStream} delante. Lo que
 * no se pueda leer asi va por el metodo de Forge de siempre, que sabe
 * rehacerlo. Lo usan el PC y Android (el gancho es el de {@code NeoLanguage}).
 *
 * <p>Lo unico que no se repone es {@code FModel.deckGenMatrixLoaded}, que es
 * privado: solo lo leen las listas de {@code DeckType} para los selectores de
 * mazo de Swing y libGDX, que en este proceso no existen (la Aventura va en el
 * suyo y carga lo suyo).
 */
public final class FastDeckGen {

    private FastDeckGen() {
    }

    /** Si se apago la preferencia y hay que cargar y reponerla. */
    private static volatile boolean deferred;

    /** En el gancho de {@code FModel.initialize}: que el motor no las lea. */
    public static void skipInEngine(final ForgePreferences prefs) {
        if (Boolean.getBoolean("neo.deckgen.engine")) {
            return; // -Dneo.deckgen.engine=true: como Forge, para comparar
        }
        try {
            if (prefs.getPrefBoolean(FPref.DECKGEN_CARDBASED)) {
                prefs.setPref(FPref.DECKGEN_CARDBASED, false);
                deferred = true;
            }
        } catch (final RuntimeException e) {
            deferred = false;
        }
    }

    /**
     * Justo despues de {@code FModel.initialize}: repone la preferencia (antes
     * de que nada la pueda guardar) y carga las tablas. Bloquea, como lo hacia
     * el motor: cuando acaba, quien pida un mazo generado las tiene.
     */
    public static void loadNow() {
        if (!deferred) {
            return;
        }
        deferred = false;
        try {
            FModel.getPreferences().setPref(FPref.DECKGEN_CARDBASED, true);
        } catch (final RuntimeException e) {
            System.err.println("[neo] deckgen: no se ha podido reponer la preferencia: " + e);
        }
        final long t0 = System.currentTimeMillis();
        try {
            for (final DeckFormat f : new DeckFormat[] {DeckFormat.Commander, DeckFormat.Oathbreaker}) {
                final String name = f.toString();
                final HashMap<String, List<Map.Entry<PaperCard, Integer>>> map =
                        read(CardThemedMatrixIO.getMatrixFile(name));
                if (map != null) {
                    CardRelationMatrixGenerator.cardPools.put(name, map);
                } else {
                    CardRelationMatrixGenerator.initializeFormat(f);
                }
            }
            final List<String> formats = new ArrayList<>();
            formats.add(FModel.getFormats().getStandard().getName());
            formats.add(FModel.getFormats().getPioneer().getName());
            formats.add(FModel.getFormats().getHistoric().getName());
            formats.add(FModel.getFormats().getModern().getName());
            formats.add(FModel.getFormats().getPauper().getName());
            formats.add("Legacy");
            formats.add("Vintage");
            for (final String f : formats) {
                final List<Archetype> raw = read(CardThemedLDAIO.getRAWLDAFile(f));
                final Map<String, List<List<Pair<String, Double>>>> lda = read(CardThemedLDAIO.getLDAFile(f));
                if (raw != null && lda != null) {
                    CardArchetypeLDAGenerator.ldaPools.put(f, lda);
                    CardArchetypeLDAGenerator.ldaArchetypes.put(f, CardArchetypeLDAGenerator.pruneArchetypes(raw));
                } else {
                    CardArchetypeLDAGenerator.initializeFormat(f);
                }
            }
        } catch (final RuntimeException e) {
            System.err.println("[neo] deckgen: " + e);
        }
        System.out.println("        deck generator: " + (System.currentTimeMillis() - t0) + " ms");
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(final File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file), 1 << 16))) {
            return (T) in.readObject();
        } catch (final Exception e) {
            System.err.println("[neo] deckgen: no se lee " + file.getName() + ": " + e);
            return null;
        }
    }
}
