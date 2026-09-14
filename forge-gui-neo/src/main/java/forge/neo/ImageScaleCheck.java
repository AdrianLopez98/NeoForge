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

        System.out.println();
        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del reescalado han fallado");
        }
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
