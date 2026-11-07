package forge.neo.quest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import forge.gamemodes.quest.QuestSpellShop;
import forge.localinstance.properties.ForgeConstants;

/**
 * <b>Precios reales en la tienda de la Quest</b> (Discord, 07-11-2026:
 * <i>"Real card prices in quest mode shop (already implemented in Forge, there
 * is option to download card prices)"</i>).
 *
 * <p>No hay nada que calcular: {@code QuestSpellShop.getCardValue} ya lee
 * {@code all-prices.txt} si existe (y si no, el precio por rareza), igual que
 * el desbloqueo de expansiones. Faltaba lo que en el Forge de siempre es
 * "Download Card Prices": bajar ese fichero. Aqui es un interruptor: encendido
 * lo baja, apagado lo borra y vuelven los precios por rareza.
 *
 * <p>La red, como manda la casa: solo cuando el jugador lo pide, una vez, con
 * tope de tiempo, y sin red no pasa nada (el interruptor vuelve a apagarse).
 * Java puro: lo usa Android igual.
 */
public final class QuestPrices {

    private QuestPrices() {
    }

    private static final int TIMEOUT_MS = 15_000;

    private static File file() {
        return new File(ForgeConstants.QUEST_CARD_PRICE_FILE);
    }

    /** Si los precios reales estan puestos (el fichero esta). */
    public static boolean isOn() {
        final File f = file();
        return f.isFile() && f.length() > 0;
    }

    /**
     * Baja la lista de precios. Tarda: fuera del hilo de interfaz.
     *
     * @return true si ha quedado puesta
     */
    public static boolean download() {
        final File target = file();
        File tmp = null;
        HttpURLConnection c = null;
        try {
            final File dir = target.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            tmp = new File(target.getPath() + ".part");
            c = (HttpURLConnection) new URL(ForgeConstants.URL_PRICE_DOWNLOAD).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "NeoForge");
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            try (InputStream in = c.getInputStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (tmp.length() < 1024) {
                // Una pagina de error o un fichero vacio no son una lista de precios.
                return false;
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            forget();
            return true;
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se han podido bajar los precios: " + e);
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
            if (tmp != null && tmp.isFile()) {
                tmp.delete();
            }
        }
    }

    /** Quita la lista: vuelven los precios por rareza. */
    public static void remove() {
        final File f = file();
        if (f.isFile() && !f.delete()) {
            System.err.println("[quest] no se ha podido borrar " + f);
        }
        forget();
    }

    /**
     * La tienda guarda la lista leida en un campo estatico privado y no la
     * vuelve a mirar: sin olvidarla, el cambio no se notaria hasta reiniciar.
     */
    private static void forget() {
        try {
            final java.lang.reflect.Field f = QuestSpellShop.class.getDeclaredField("mapPrices");
            f.setAccessible(true);
            f.set(null, null);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[quest] los precios se notaran al reiniciar: " + e);
        }
    }
}
