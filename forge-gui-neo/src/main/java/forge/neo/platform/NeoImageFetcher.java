package forge.neo.platform;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import javax.imageio.ImageIO;

import forge.ImageKeys;
import forge.StaticData;
import forge.card.CardEdition;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences;
import forge.model.FModel;
import forge.neo.card.CardArt;
import forge.util.BuildInfo;
import forge.util.ImageFetcher;
import forge.util.ImageUtil;
import forge.util.TextUtil;
import forge.util.ThreadUtil;

/**
 * Descarga de imagenes de carta para la interfaz Neo.
 *
 * <p>Equivalente de {@code SwingImageFetcher} pero sin depender de
 * {@code forge-gui-desktop}. Usa {@code javax.imageio} (que es parte de
 * java.desktop, no de Swing) para decodificar y guardar.
 *
 * <p>Las imagenes vienen de Scryfall. La clase base {@link ImageFetcher} ya
 * gestiona el rate limit y el cooldown: hay que respetarlo llamando a
 * {@code paceScryfall} y {@code noteScryfallRateLimited}, que es justo lo que
 * hace {@link #doFetch}. No descargar por nuestra cuenta saltandonos esto.
 */
public class NeoImageFetcher extends ImageFetcher {

    /** {@code -Dneo.images.debug=true} para ver cada intento de descarga. */
    private static final boolean DEBUG = Boolean.getBoolean("neo.images.debug");

    /**
     * Si Scryfall nos ha limitado y estamos esperando a que se le pase.
     *
     * <p>Lo unico que hace es abrir al publico lo que la clase base guarda como
     * {@code protected}. Hace falta fuera porque durante el enfriamiento — que
     * dura <b>cinco minutos</b> — {@code fetchImage} descarta las peticiones
     * <b>sin avisar a nadie</b>: quien esperaba una imagen no se entera de que
     * no va a llegar. Ver {@code CardImages.watch}.
     */
    public boolean isCoolingDown() {
        return scryfallCoolingDown();
    }

    /**
     * Cuantos segundos quedan de enfriamiento, o 0 si no hay ninguno.
     *
     * <p>Lo mismo que {@link #isCoolingDown()} pero con el reloj, para poder
     * decirlo en pantalla ("vuelve en 42s") en vez de solo saber que si hay
     * corte. {@code scryfallCooldownTime} es {@code protected static} en la
     * clase base: se lee directo, sin pasar por ningun metodo nuevo alli.
     */
    public long coolingDownSecondsLeft() {
        final Date until = scryfallCooldownTime;
        if (until == null) {
            return 0;
        }
        final long ms = until.getTime() - System.currentTimeMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    @Override
    protected Runnable getDownloadTask(final String[] downloadUrls, final String destPath,
                                       final Runnable notifyObservers) {
        return () -> {
            if (DEBUG) {
                System.out.println("[neo-img] tarea para " + destPath
                        + " con " + downloadUrls.length + " url(s)");
            }
            // El pool se traga las excepciones: capturamos todo aqui o los
            // fallos se pierden en silencio y parece que no pasa nada.
            try {
                for (final String url : downloadUrls) {
                    try {
                        if (DEBUG) {
                            System.out.println("[neo-img] intentando " + url);
                        }
                        if (doFetch(url, destPath, notifyObservers)) {
                            return;
                        }
                    } catch (final IOException e) {
                        System.err.println("[neo-img] fallo al descargar " + destPath
                                + " desde " + url + ": " + e);
                    }
                }
                System.err.println("[neo-img] agotadas las " + downloadUrls.length
                        + " url(s) sin exito para " + destPath);
            } catch (final Throwable t) {
                System.err.println("[neo-img] error inesperado con " + destPath + ": " + t);
                t.printStackTrace();
            }
        };
    }

    /**
     * Baja la carta en el idioma del juego, si Scryfall la tiene.
     *
     * <p><b>Por que no se pasa por {@code ImageFetcher.fetchImage}</b>, que es
     * el camino normal: porque se corta en seco cuando el fichero de destino ya
     * existe, y el destino que calcula es el de la carpeta COMPARTIDA. O sea
     * que para las cartas que ya tienes en ingles — que son casi todas, 172 MB
     * de cache — nunca llegaria a preguntar por la castellana. Aqui se calcula
     * nuestro destino y se pide solo esa.
     *
     * <p>Lo unico que NO se salta es el trato con Scryfall: sigue pasando por
     * {@code paceScryfall}, la cuenta atras de {@code scryfallCoolingDown} y el
     * aviso de {@code noteScryfallRateLimited} — que estan dentro de
     * {@link #doFetch} — porque es lo que evita que nos corten.
     *
     * @param whenDone se llama SIEMPRE al terminar, haya imagen o no: quien
     *                 espera tiene que poder seguir con el camino de siempre
     */
    public void fetchLocalized(final String imageKey, final Runnable whenDone) {
        final String lang = CardArt.language();
        if (lang == null) {
            whenDone.run();
            return;
        }

        // El recorte de arte no lleva texto: traducirlo no cambia nada y seria
        // una descarga entera para ver exactamente lo mismo.
        if ("Crop".equals(FModel.getPreferences()
                .getPref(ForgePreferences.FPref.UI_CARD_ART_FORMAT))) {
            whenDone.run();
            return;
        }

        final String face = imageKey.endsWith(ImageKeys.BACKFACE_POSTFIX) ? "back" : "";
        final PaperCard card = ImageUtil.getPaperCardFromImageKey(imageKey);
        if (card == null || card.getRules() == null || card.getRules().isCustom()
                || card.getCollectorNumber().equals(IPaperCard.NO_COLLECTOR_NUMBER)) {
            whenDone.run();
            return;
        }

        final String filename = "back".equals(face)
                ? card.getCardAltImageKey() : card.getCardImageKey();
        final File dest = CardArt.fileFor(filename);
        if (dest == null || dest.exists() || CardArt.isMissing(filename)) {
            whenDone.run();
            return;
        }

        final CardEdition edition = StaticData.instance().getEditions().get(card.getEdition());
        if (edition == null) {
            whenDone.run();
            return;
        }
        final String url = ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD
                + ImageUtil.getScryfallDownloadUrl(card, face, edition.getScryfallCode(), lang, false);

        ThreadUtil.getServicePool().submit(() -> {
            boolean ok = false;
            try {
                ok = doFetch(url, dest.getPath(), null);
            } catch (final IOException e) {
                if (DEBUG) {
                    System.out.println("[neo-img] " + lang + ": fallo " + url + " -> " + e);
                }
            } catch (final RuntimeException e) {
                System.err.println("[neo-img] error pidiendo " + url + ": " + e);
            }
            if (!ok && !scryfallCoolingDown()) {
                // Que una carta no exista en este idioma es lo normal, no un
                // error. Se anota para no volver a preguntarlo cada arranque —
                // pero NO si lo que ha pasado es que nos han cortado, porque
                // entonces no sabemos nada de esta carta.
                CardArt.markMissing(filename);
            }
            if (DEBUG) {
                System.out.println("[neo-img] " + lang + ": "
                        + (ok ? "OK " : "no hay ") + filename);
            }
            whenDone.run();
        });
    }

    private boolean doFetch(final String urlToDownload, final String destPath,
                            final Runnable notifyObservers) throws IOException {

        if (disableHostedDownload && urlToDownload.startsWith(ForgeConstants.URL_CARDFORGE)) {
            return false;
        }
        if (inScryfallCooldown(urlToDownload)) {
            return false;
        }

        // Scryfall sirve las cartas con borde completo; Forge nombra ese fichero
        // .fullborder.jpg en vez de .full.jpg.
        String destination = destPath;
        if (urlToDownload.contains(".fullborder.jpg")
                || urlToDownload.startsWith(ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD)) {
            destination = TextUtil.fastReplace(destination, ".full.jpg", ".fullborder.jpg");
        }

        final URL url = new URL(urlToDownload);
        paceScryfall(urlToDownload);

        final URLConnection connection = url.openConnection();
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("User-Agent", BuildInfo.getUserAgent());
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);

        if (connection instanceof HttpURLConnection http) {
            final int code = http.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                if (code == 429 && isScryfall(urlToDownload)) {
                    System.err.println("[neo] Scryfall nos ha limitado. Pausando descargas.");
                    noteScryfallRateLimited();
                }
                http.disconnect();
                return false;
            }
        }

        final BufferedImage image;
        try (InputStream is = connection.getInputStream()) {
            image = ImageIO.read(is);
        }
        if (image == null) {
            return false;
        }

        // Escribir primero a .tmp para que nadie lea una descarga a medias.
        final File tmp = new File(destination + ".tmp");
        final File parent = tmp.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (!ImageIO.write(image, "jpg", tmp)) {
            return false;
        }
        final File dest = new File(destination);
        if (dest.exists()) {
            dest.delete();
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            return false;
        }
        if (DEBUG) {
            System.out.println("[neo-img] guardada en " + destination);
        }
        // El notifyObservers de Forge hace assertExecutedByEdt(true): hay que
        // ejecutarlo en el hilo de interfaz o lanza IllegalStateException y el
        // callback nunca llega (la imagen queda en disco pero nadie se entera).
        if (notifyObservers != null) {
            forge.gui.GuiBase.getInterface().invokeInEdtLater(notifyObservers);
        }
        return true;
    }
}
