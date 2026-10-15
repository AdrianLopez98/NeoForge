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
        final List<String> keys = keysOf(name);
        System.out.println("  " + keys.size() + " impresiones de " + name);
        final String was = NeoSettings.get(NeoSettings.IMAGE_QUALITY, ImageQuality.NORMAL.scryfall);
        try {
            phase(ImageQuality.LARGE, keys, 672);
            phase(ImageQuality.NORMAL, keys, 488);
            phase(ImageQuality.LARGE, keys, 672);
            // La maxima no se probaba, y no bajaba ni una carta suelta: el PNG
            // trae transparencia y se re-escribia en JPG (Discord, 14-10-2026,
            // ver ImageSave). Con OTRA carta y solo las impresiones que no
            // esten en disco en ninguna calidad: con una normal o alta ya
            // bajada se ensenya esa (la mejor que tengas, decision del
            // 13-10-2026) y no se pide. Scryfall la sirve a 745 o 744 de ancho.
            final List<String> fresh = new ArrayList<>();
            for (final String k : keysOf(System.getProperty("neo.quality.pngCard", "Brainstorm"))) {
                if (CardImages.cachedFile(k) == null && fresh.size() < 6) {
                    fresh.add(k);
                }
            }
            if (fresh.isEmpty()) {
                System.out.println("  (maxima saltada: esas impresiones ya estan en disco)");
            } else {
                phase(ImageQuality.PNG, fresh, 745);
            }
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

    private static List<String> keysOf(final String name) {
        final List<String> keys = new ArrayList<>();
        for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards(name)) {
            final String key = CardView.getCardForUi(pc).getCurrentState().getImageKey();
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** Pone esa calidad como lo hace Ajustes y mira que todas se vean a ese ancho. */
    private static void phase(final ImageQuality q, final List<String> keys, final int width) throws InterruptedException {
        NeoSettings.set(NeoSettings.IMAGE_QUALITY, q.scryfall);
        ArtDownload.afterDownload();
        final Map<String, Image> got = new HashMap<>();
        final long until = System.currentTimeMillis() + 120_000;
        // Mientras baja la calidad pedida se ensenya la mejor que ya haya en
        // disco (ImageQuality.lookupOrder): se espera a que cada una llegue en
        // la suya, que es lo que ve el jugador al cabo de un momento.
        int settled = 0;
        while (settled < keys.size() && System.currentTimeMillis() < until) {
            settled = 0;
            for (final String k : keys) {
                final Image img = CardImages.get(k);
                if (img != null) {
                    got.put(k, img);
                    if (Math.abs((int) img.getWidth() - width) <= 1) {
                        settled++;
                    }
                }
            }
            Thread.sleep(250);
        }
        final Map<Integer, Integer> widths = new java.util.TreeMap<>();
        for (final Image img : got.values()) {
            widths.merge((int) img.getWidth(), 1, Integer::sum);
        }
        boolean near = !widths.isEmpty();
        for (final int w : widths.keySet()) {
            near &= Math.abs(w - width) <= 1;
        }
        final boolean ok = got.size() == keys.size() && near;
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] calidad " + q.scryfall + ": "
                + got.size() + " de " + keys.size() + " cargadas, anchos " + widths + " (se esperaba " + width + ")");
        if (!ok) {
            bad++;
        }
    }
}
