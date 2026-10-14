package forge.neo.card;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoSettings;
import javafx.application.Platform;
import javafx.scene.image.Image;

/**
 * <b>La calidad de imagen que se elige es la que se VE</b> (Ajustes -> Arte).
 *
 * <p>Discord, 14-10-2026: en "alta" se bajaba todo a 488x680. Arreglada la
 * descarga, lo que hay que comprobar es el otro lado: que con la alta en disco
 * la carta que pinta la mesa sea la alta, que al pasar a la normal se vea la
 * normal, y que al volver se vea otra vez la alta, sin reiniciar. Se pide cada
 * impresion por {@link CardImages#get} - lo mismo que hace {@code CardNode} - y
 * se mira el tamanyo de lo decodificado.
 *
 * <p>Necesita red la primera vez (baja lo que falte de cada calidad). Las
 * imagenes van a la cache de siempre, como si se jugara. Usar con un
 * {@code -Dneo.settingsFile} aparte: cambia la calidad en memoria y al
 * terminar la deja como estaba, sin guardar.
 */
public final class QualityCheck {

    private QualityCheck() {
    }

    private static int bad;

    public static void run() {
        try {
            runChecks();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void runChecks() throws InterruptedException {
        bad = 0;
        final CountDownLatch fx = new CountDownLatch(1);
        Platform.startup(fx::countDown);
        Platform.setImplicitExit(false);
        fx.await();

        final String name = System.getProperty("neo.quality.card", "Skullclamp");
        final List<String> keys = new ArrayList<>();
        for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards(name)) {
            final String key = CardView.getCardForUi(pc).getCurrentState().getImageKey();
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
        System.out.println("  " + keys.size() + " impresiones de " + name);
        final String was = NeoSettings.get(NeoSettings.IMAGE_QUALITY, ImageQuality.NORMAL.scryfall);
        try {
            phase(ImageQuality.LARGE, keys, 672);
            phase(ImageQuality.NORMAL, keys, 488);
            phase(ImageQuality.LARGE, keys, 672);
        } finally {
            NeoSettings.set(NeoSettings.IMAGE_QUALITY, was);
            ArtDownload.afterDownload();
        }
        System.out.println(bad == 0 ? "  TODO BIEN" : "  " + bad + " FALLO(S)");
        Platform.exit();
        if (bad > 0) {
            throw new IllegalStateException(bad + " comprobaciones de la calidad de imagen han fallado");
        }
    }

    /** Pone esa calidad como lo hace Ajustes y mira que todas se vean a ese ancho. */
    private static void phase(final ImageQuality q, final List<String> keys, final int width) throws InterruptedException {
        NeoSettings.set(NeoSettings.IMAGE_QUALITY, q.scryfall);
        ArtDownload.afterDownload();
        final Map<String, Image> got = new HashMap<>();
        final long until = System.currentTimeMillis() + 120_000;
        while (got.size() < keys.size() && System.currentTimeMillis() < until) {
            for (final String k : keys) {
                if (!got.containsKey(k)) {
                    final Image img = CardImages.get(k);
                    if (img != null) {
                        got.put(k, img);
                    }
                }
            }
            Thread.sleep(250);
        }
        final Map<Integer, Integer> widths = new java.util.TreeMap<>();
        for (final Image img : got.values()) {
            widths.merge((int) img.getWidth(), 1, Integer::sum);
        }
        final boolean ok = got.size() == keys.size() && widths.size() == 1 && widths.containsKey(width);
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] calidad " + q.scryfall + ": "
                + got.size() + " de " + keys.size() + " cargadas, anchos " + widths + " (se esperaba " + width + ")");
        if (!ok) {
            bad++;
        }
    }
}
