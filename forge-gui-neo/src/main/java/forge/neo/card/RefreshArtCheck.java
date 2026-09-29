package forge.neo.card;

import java.io.File;

import forge.item.PaperCard;
import forge.model.FModel;

/**
 * {@code run.cmd refreshartcheck}: "volver a bajar la imagen" contra Scryfall
 * de verdad (necesita red; sin ella dice que no ha podido y no falla nada).
 *
 * <p>Lo que importa es lo que NO pasa: con una carta que Scryfall todavia tiene
 * provisional (Final Showdown de PMEI, #2027-1, a 29-09-2026) el fichero que
 * hubiera en disco no se toca. Y con una en HD, se vuelve a bajar.
 */
public final class RefreshArtCheck {

    private RefreshArtCheck() {
    }

    private static int bad;

    public static void run() {
        bad = 0;
        final PaperCard lowres = FModel.getMagicDb().getCommonCards().getCard("Final Showdown", "PMEI");
        final PaperCard hd = FModel.getMagicDb().getCommonCards().getCard("Sol Ring", "C21");
        if (lowres == null || hd == null) {
            System.out.println("  [MAL] no estan las cartas de la prueba: " + lowres + " / " + hd);
            System.exit(1);
            return;
        }
        // PROVISIONAL: se crea un fichero de mentira donde iria su imagen y se
        // comprueba que sigue igual despues.
        final File dir = new File(forge.localinstance.properties.ForgeConstants.CACHE_CARD_PICS_DIR);
        final File fake = new File(dir, lowres.getCardImageKey().replace(".full", ".fullborder") + ".jpg");
        final boolean hadIt = fake.exists();
        try {
            if (!hadIt) {
                fake.getParentFile().mkdirs();
                java.nio.file.Files.write(fake.toPath(), new byte[] {1, 2, 3});
            }
            final long before = fake.length();
            final long stamp = fake.lastModified();
            final CardImages.Refresh r1 = CardImages.refreshBlocking(lowres.getImageKey(false));
            System.out.println("  provisional (Final Showdown PMEI): " + r1);
            if (r1 == CardImages.Refresh.OFFLINE || r1 == CardImages.Refresh.BUSY) {
                System.out.println("  (sin respuesta de Scryfall: no se puede comprobar mas)");
            } else {
                check("una imagen provisional no se vuelve a bajar", r1 == CardImages.Refresh.NOT_YET);
            }
            check("y el fichero que habia sigue igual",
                    fake.exists() && fake.length() == before && fake.lastModified() == stamp);
        } catch (final java.io.IOException e) {
            check("se pudo preparar la prueba: " + e, false);
        } finally {
            if (!hadIt) {
                fake.delete();
            }
        }

        final CardImages.Refresh r2 = CardImages.refreshBlocking(hd.getImageKey(false));
        System.out.println("  en HD (Sol Ring C21): " + r2);
        if (r2 == CardImages.Refresh.OFFLINE || r2 == CardImages.Refresh.BUSY) {
            System.out.println("  (sin respuesta de Scryfall: no se puede comprobar mas)");
        } else {
            check("una imagen en HD se vuelve a bajar", r2 == CardImages.Refresh.UPDATED);
        }
        scanAll();
        System.out.println();
        System.out.println(bad == 0 ? "  TODO BIEN" : "  " + bad + " MAL");
        System.exit(bad == 0 ? 0 : 1);
    }

    /**
     * "Buscar imagenes mejores (HD)" de todo: dos imagenes ya bajadas de la
     * expansion reciente mas nueva. Una se hace pasar por VIEJA (fecha de
     * 1970) y la otra por NUEVA (un anyo en el futuro). La vieja tiene que
     * volver a bajarse; la nueva, quedarse como esta. Luego se les devuelve
     * la fecha que tenian.
     */
    private static void scanAll() {
        check("la fecha va en el enlace de Scryfall",
                ArtHdScan.stampOf("https://cards.scryfall.io/normal/front/a/b/x.jpg?1788003039") == 1788003039L
                        && ArtHdScan.stampOf("https://cards.scryfall.io/x.jpg") == 0);
        final java.util.List<forge.card.CardEdition> sets = ArtHdScan.recentCachedSets();
        System.out.println("  expansiones recientes con imagenes: " + sets.size());
        if (sets.isEmpty()) {
            System.out.println("  (sin imagenes recientes en esta maquina: no se puede probar el barrido)");
            return;
        }
        final File dir = new File(forge.localinstance.properties.ForgeConstants.CACHE_CARD_PICS_DIR,
                sets.get(0).getCode());
        final File[] files = dir.listFiles((d, n) -> n.endsWith(".fullborder.jpg"));
        if (files == null || files.length < 2) {
            System.out.println("  (pocas imagenes en " + dir + ": no se puede probar el barrido)");
            return;
        }
        java.util.Arrays.sort(files);
        final File old = files[0];
        final File fresh = files[1];
        final long oldWas = old.lastModified();
        final long freshWas = fresh.lastModified();
        final long future = System.currentTimeMillis() + 365L * 24 * 3600 * 1000;
        try {
            old.setLastModified(1000L);
            fresh.setLastModified(future);
            final ArtHdScan.Result r = ArtHdScan.run(CardImages.HD_STORE, null, null);
            System.out.println("  barrido: " + r.sets() + " expansiones, " + r.checked()
                    + " impresiones en HD, " + r.updated() + " actualizadas"
                    + (r.stoppedByNetwork() ? " (parado: sin respuesta)" : ""));
            if (r.stoppedByNetwork()) {
                System.out.println("  (sin respuesta de Scryfall: no se puede comprobar mas)");
                return;
            }
            check("la que parecia vieja (" + old.getName() + ") se ha vuelto a bajar",
                    old.lastModified() > 1000L + 60_000L);
            check("la que parecia nueva (" + fresh.getName() + ") no se ha tocado",
                    fresh.lastModified() == future);
        } finally {
            // La descargada se queda con su fecha nueva (es la buena); la otra
            // vuelve a la suya.
            if (old.lastModified() <= 1000L + 60_000L) {
                old.setLastModified(oldWas);
            }
            fresh.setLastModified(freshWas);
        }
    }

    private static void check(final String what, final boolean ok) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        if (!ok) {
            bad++;
        }
    }
}
