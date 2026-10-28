package forge.neo.quest;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import forge.card.CardEdition;
import forge.item.PreconDeck;
import forge.item.SealedProduct;
import forge.localinstance.properties.ForgeConstants;

/**
 * <b>Lo que ya has comprado en la tienda, y cuantas veces</b> (Discord,
 * 28-10-2026: <i>"For the thing that shows us what we already purchased ... I
 * can tell I got the Jace deck because, y'know, 0 cards missing"</i>).
 *
 * <p>Forge no lo apunta: un mazo comprado son cartas en la coleccion y un mazo
 * mas en la lista, y nada dice de que producto salieron. Asi que se cuenta
 * aqui, al lado de la partida ({@code <quest>.neo-compras}, como las mejoras o
 * la vitrina), <b>desde hoy</b>: lo comprado antes no se puede reconstruir.
 *
 * <p>Se cuentan los productos que se compran enteros: mazos de Commander,
 * preconstruidos, cajas, sobres Jumpstart y de coleccionista. Lo apunta
 * {@link NeoQuestShop} al cobrar, asi que vale igual para las dos interfaces.
 *
 * <p>Java puro: lo usa tambien Android por el jar.
 */
public final class QuestPurchases {

    private QuestPurchases() {
    }

    /** Cuantas veces se ha comprado ese producto en la Quest cargada. 0 si no se cuenta. */
    public static synchronized int count(final Object product) {
        final String key = keyOf(product);
        if (key == null || !NeoQuest.isActive()) {
            return 0;
        }
        return parse(load(NeoQuest.name()).getProperty(key));
    }

    /** Apunta {@code times} compras mas. Lo llama {@link NeoQuestShop} tras cobrar. */
    static synchronized void record(final Object product, final int times) {
        final String key = keyOf(product);
        if (key == null || times <= 0 || !NeoQuest.isActive()) {
            return;
        }
        final String questName = NeoQuest.name();
        if (questName == null || questName.isBlank()) {
            return;
        }
        final Properties p = load(questName);
        p.setProperty(key, Integer.toString(parse(p.getProperty(key)) + times));
        try (Writer w = Files.newBufferedWriter(fileOf(questName).toPath(), StandardCharsets.UTF_8)) {
            p.store(w, "Neo Forge: cuantas veces se ha comprado cada producto de la tienda");
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se han podido guardar las compras: " + e);
        }
    }

    static synchronized void deleteSaved(final String questName) {
        if (questName == null || questName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(fileOf(questName).toPath());
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se han podido borrar las compras: " + e);
        }
    }

    // ------------------------------------------------------------------

    /**
     * La clave de cada producto, por su nombre y no por su posicion: el
     * mostrador cambia, y un preconstruido que vuelve a salir tiene que traer
     * su cuenta.
     */
    static String keyOf(final Object product) {
        if (product instanceof PreconDeck d) {
            return d.getName() == null ? null : "deck:" + d.getName();
        }
        if (product instanceof SealedProduct s) {
            return s.getName() == null ? null : "box:" + s.getEdition() + ":" + s.getName();
        }
        if (product instanceof NeoJumpstart.Product j) {
            return j.name == null ? null : "jumpstart:" + j.setCode + ":" + j.name;
        }
        if (product instanceof CardEdition e) {
            return "collector:" + e.getCode();
        }
        return null;
    }

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".neo-compras");
    }

    private static Properties load(final String questName) {
        final Properties p = new Properties();
        if (questName == null || questName.isBlank()) {
            return p;
        }
        final File f = fileOf(questName);
        if (f.isFile()) {
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (final IOException | RuntimeException e) {
                System.err.println("[quest] no se han podido leer las compras: " + e);
            }
        }
        return p;
    }

    private static int parse(final String s) {
        try {
            return s == null ? 0 : Math.max(0, Integer.parseInt(s.trim()));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}
