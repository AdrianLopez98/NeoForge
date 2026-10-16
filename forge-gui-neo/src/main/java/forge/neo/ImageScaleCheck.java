package forge.neo;

import java.util.Locale;
import java.util.Random;

import forge.neo.card.Resample;

/**
 * El reescalado de las cartas, sin ventana ({@code run.cmd imagecheck}).
 *
 * <p>Lo que se comprueba es lo que haria que las cartas se vieran MAL sin dar
 * ningun error: que el tamano no sea el pedido, que un color plano cambie de
 * tono, que la imagen se oscurezca o aclare al reducirla, que los pixeles
 * transparentes de las esquinas destinan a sus vecinos, o que los pesos
 * negativos de Lanczos se salgan de 0-255. Ver {@link Resample}.
 */
public final class ImageScaleCheck {

    private ImageScaleCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        // 1. El tamano que se pide.
        final int[] big = solid(488, 680, 0xFF336699);
        final int[] small = Resample.downscale(big, 488, 680, 150, 209);
        check(small.length == 150 * 209, "sale del tamano pedido (150x209)");

        // 2. Un color plano no cambia de tono (los pesos suman 1, tambien en el borde).
        boolean same = true;
        for (final int p : small) {
            if (p != 0xFF336699) {
                same = false;
                break;
            }
        }
        check(same, "un color plano sigue siendo exactamente el mismo, bordes incluidos");

        // 3. Blanco y negro alternos: la media es gris, no uno de los dos.
        final int[] checker = {0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000};
        final int grey = Resample.downscale(checker, 2, 2, 1, 1)[0];
        final int g = (grey >> 8) & 0xFF;
        check(g >= 126 && g <= 129, "un ajedrezado blanco y negro promedia a gris (" + g + ")");

        // 4. Una imagen cualquiera no se oscurece ni se aclara al reducirla,
        //    tampoco con una proporcion que no es entera (97 -> 31).
        final Random rnd = new Random(14092026);
        final int[] noise = new int[97 * 131];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = 0xFF000000 | rnd.nextInt(0x1000000);
        }
        final int[] reduced = Resample.downscale(noise, 97, 131, 31, 43);
        final double before = meanGreen(noise);
        final double after = meanGreen(reduced);
        check(Math.abs(before - after) < 2.0, String.format(Locale.ROOT,
                "el brillo medio se conserva con proporcion no entera (%.2f -> %.2f)", before, after));

        // 5. Lo transparente no destine: rojo opaco al lado de un "blanco"
        //    transparente sigue siendo rojo, medio transparente.
        final int[] edge = {0x00FFFFFF, 0xFFFF0000};
        final int mixed = Resample.downscale(edge, 2, 1, 1, 1)[0];
        final int a = (mixed >>> 24) & 0xFF;
        final int r = (mixed >> 16) & 0xFF;
        final int gg = (mixed >> 8) & 0xFF;
        check(a >= 126 && a <= 129 && r >= 250 && gg <= 5,
                "un borde transparente no destine al color de al lado (a=" + a + " r=" + r + " g=" + gg + ")");

        // 6. Sin reducir, la imagen sale igual.
        final int[] copy = Resample.downscale(noise, 97, 131, 97, 131);
        check(java.util.Arrays.equals(copy, noise), "al mismo tamano sale identica");

        // 7. Un borde duro (media carta negra, media blanca): los pesos negativos
        //    no pueden sacar ningun canal de 0-255 ni dar la vuelta al color.
        final int[] hard = new int[200 * 10];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 200; x++) {
                hard[y * 200 + x] = x < 100 ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        final int[] hardSmall = Resample.downscale(hard, 200, 10, 37, 2);
        final int left = (hardSmall[0] >> 8) & 0xFF;
        final int right = (hardSmall[36] >> 8) & 0xFF;
        check(left <= 5 && right >= 250, "un borde duro deja negro el negro y blanco el blanco (" + left + ", " + right + ")");

        // 8. Tamanos imposibles no se tragan en silencio.
        boolean refused = false;
        try {
            Resample.downscale(big, 488, 680, 0, 10);
        } catch (final IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "un tamano de cero se rechaza en vez de devolver una imagen vacia");

        // 9. Guardar lo bajado (forge.neo.card.ImageSave). Discord, 14-10-2026:
        //    en calidad "Maxima" no bajaba ni una carta suelta, porque el PNG de
        //    Scryfall trae transparencia y se volvia a escribir en JPG.
        savingKeepsTheBytes();
        planesInEveryQuality();

        System.out.println();
        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del reescalado han fallado");
        }
    }

    /**
     * Lo bajado se guarda tal cual: un PNG con transparencia (las esquinas de
     * la carta) y un JPG salen identicos byte a byte, y lo que no es una imagen
     * no deja nada en la cache. Sin red: las imagenes se fabrican aqui.
     */
    private static void savingKeepsTheBytes() {
        java.io.File dir = null;
        try {
            dir = java.nio.file.Files.createTempDirectory("neo-imgsave-").toFile();
            final java.awt.image.BufferedImage card = new java.awt.image.BufferedImage(
                    60, 84, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 84; y++) {
                for (int x = 0; x < 60; x++) {
                    // Esquinas transparentes, como el PNG de Scryfall.
                    final boolean corner = (x < 4 || x > 55) && (y < 4 || y > 79);
                    card.setRGB(x, y, corner ? 0x00000000 : 0xFF000000 | (x * 4 << 16) | (y * 3 << 8) | 0x40);
                }
            }
            final java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(card, "png", png);

            // La causa, escrita: el escritor JPG de Java no sabe con alfa.
            final boolean oldWay = javax.imageio.ImageIO.write(card, "jpg",
                    new java.io.File(dir, "viejo.jpg"));
            check(!oldWay, "la causa: re-escribir en JPG un PNG con transparencia no escribe nada");

            final java.io.File asPng = new java.io.File(dir, "SET/Carta.fullborder.jpg");
            final boolean savedPng = forge.neo.card.ImageSave.save(png.toByteArray(), asPng);
            check(savedPng && java.util.Arrays.equals(png.toByteArray(),
                    java.nio.file.Files.readAllBytes(asPng.toPath())),
                    "un PNG con transparencia se guarda, y byte a byte igual (sin recomprimir)");
            check(javax.imageio.ImageIO.read(asPng) != null && javax.imageio.ImageIO.read(asPng).getColorModel().hasAlpha(),
                    "y se lee aunque el fichero se llame .jpg (se mira el contenido)");

            final java.awt.image.BufferedImage opaque = new java.awt.image.BufferedImage(
                    60, 84, java.awt.image.BufferedImage.TYPE_INT_RGB);
            final java.io.ByteArrayOutputStream jpg = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(opaque, "jpg", jpg);
            final java.io.File asJpg = new java.io.File(dir, "SET/Otra.fullborder.jpg");
            check(forge.neo.card.ImageSave.save(jpg.toByteArray(), asJpg)
                    && java.util.Arrays.equals(jpg.toByteArray(), java.nio.file.Files.readAllBytes(asJpg.toPath())),
                    "un JPG se guarda tal cual, sin volver a comprimirlo");

            final java.io.File junk = new java.io.File(dir, "SET/Basura.fullborder.jpg");
            final boolean savedJunk = forge.neo.card.ImageSave.save(
                    "<html>Too Many Requests</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8), junk);
            check(!savedJunk && !junk.exists() && !new java.io.File(junk.getPath() + ".tmp").exists(),
                    "lo que no es una imagen no deja nada (ni el fichero ni el .tmp)");
        } catch (final java.io.IOException e) {
            check(false, "guardar imagenes sin excepciones: " + e);
        } finally {
            if (dir != null) {
                deleteTree(dir);
            }
        }
    }

    /**
     * Los PLANOS en alta calidad (Discord, 16-10-2026: "High: 293 missing" con
     * los ficheros ahi). Su clave del motor no lleva ".full" y el descargador
     * los guarda como ".fullborder": {@code exactFile} tiene que encontrarlos.
     */
    private static void planesInEveryQuality() {
        java.io.File root = null;
        try {
            root = java.nio.file.Files.createTempDirectory("neo-planes-").toFile();
            final java.io.File set = new java.io.File(root, "ZZNEOPLN");
            set.mkdirs();
            final java.io.File plane = new java.io.File(set, "Bloodhill Bastion.fullborder.jpg");
            final java.io.File card = new java.io.File(set, "Skullclamp.fullborder.jpg");
            java.nio.file.Files.write(plane.toPath(), new byte[] {1});
            java.nio.file.Files.write(card.toPath(), new byte[] {1});
            check(plane.equals(forge.neo.card.ImageQuality.findIn(root, "ZZNEOPLN/Bloodhill Bastion")),
                    "un plano (clave sin \".full\") se encuentra en alta como .fullborder");
            check(card.equals(forge.neo.card.ImageQuality.findIn(root, "ZZNEOPLN/Skullclamp.full")),
                    "y una carta normal (\".full\") sigue encontrandose");
            check(forge.neo.card.ImageQuality.findIn(root, "ZZNEOPLN/Chaotic Aether") == null,
                    "y lo que no esta sigue sin estar");
        } catch (final java.io.IOException e) {
            check(false, "planos en alta sin excepciones: " + e);
        } finally {
            if (root != null) {
                deleteTree(root);
            }
        }
    }

    private static void deleteTree(final java.io.File f) {
        final java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (final java.io.File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }

    private static int[] solid(final int w, final int h, final int argb) {
        final int[] p = new int[w * h];
        java.util.Arrays.fill(p, argb);
        return p;
    }

    private static double meanGreen(final int[] px) {
        double sum = 0;
        for (final int p : px) {
            sum += (p >> 8) & 0xFF;
        }
        return sum / px.length;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("    OK   " + what);
        } else {
            failed++;
            System.out.println("    MAL  " + what);
        }
    }
}
