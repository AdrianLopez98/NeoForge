package forge.neo.look;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import forge.localinstance.properties.ForgeConstants;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/**
 * Los avatares y las fundas que trae Forge, recortados de sus hojas.
 *
 * <p>Forge no guarda los avatares como ficheros sueltos: son <b>una sola
 * imagen</b> con todos pegados, y cada GUI la recorta a su manera. La Swing lo
 * hace con {@code BufferedImage.getSubimage}.
 *
 * <pre>
 * sprite_avatars.png    1200x1100, casillas de 100x100  ->  ~130 avatares
 * sprite_sleeves.png    1800x2000, casillas de 360x500  ->  ~20 fundas
 * sprite_sleeves2.png   otras tantas
 * </pre>
 *
 * <h2>Se mira con ImageIO y se pinta con JavaFX, y no es capricho</h2>
 *
 * El primer intento troceaba con {@code javafx.scene.image.Image} y reventaba
 * en cuanto no habia ventana:
 *
 * <pre>
 * java.lang.RuntimeException: Internal graphics not initialized yet
 * </pre>
 *
 * <p>Y eso importa porque el <b>motor</b> pregunta cuantos avatares hay
 * ({@code IGuiBase.getAvatarCount()}) para repartir cara a cada IA, tambien en
 * {@code run.cmd play} y en los comprobadores, donde no hay ventana ninguna.
 *
 * <p>Asi que la estructura — cuantas casillas hay y cuales estan vacias — se
 * saca con {@code javax.imageio}, que va sin ventana; y la imagen de una
 * casilla concreta solo se construye <b>cuando alguien la va a pintar</b>, que
 * por definicion es con la ventana ya abierta.
 *
 * <h2>Dos detalles copiados de la GUI vieja</h2>
 *
 * <ol>
 *   <li>La casilla de arriba a la izquierda de los avatares <b>se salta</b>.
 *       No es un avatar.</li>
 *   <li>Una casilla cuyo centro es <b>transparente</b> esta vacia y tambien se
 *       salta. La hoja no esta llena: sin esto saldrian huecos en blanco
 *       mezclados con los avatares de verdad.</li>
 * </ol>
 */
final class Sprites {

    private Sprites() {
    }

    /** Solo para el javadoc de {@code NeoLook}. */
    static final int AVATAR_COUNT_HINT = 130;

    private static final int AVATAR_TILE = 100;
    private static final int SLEEVE_W = 360;
    private static final int SLEEVE_H = 500;

    private static List<LookItem> avatarCache;
    private static List<LookItem> sleeveCache;

    /**
     * Cuantos avatares hay. Cero si no se ha podido leer la hoja.
     *
     * <p>Lo pregunta el motor para repartir caras entre las IAs.
     */
    static int avatarCount() {
        return avatars().size();
    }

    /** Cuantas fundas hay. */
    static int sleeveCount() {
        return sleeves().size();
    }

    /** Los avatares de Forge. */
    static synchronized List<LookItem> avatars() {
        if (avatarCache == null) {
            avatarCache = slice("avatar", ForgeConstants.SPRITE_AVATARS_FILE,
                    AVATAR_TILE, AVATAR_TILE, true, 0);
        }
        return avatarCache;
    }

    /** Las fundas de Forge: dos hojas, numeradas de corrido. */
    static synchronized List<LookItem> sleeves() {
        if (sleeveCache == null) {
            final List<LookItem> out = new ArrayList<>(
                    slice("sleeve", ForgeConstants.SPRITE_SLEEVES_FILE, SLEEVE_W, SLEEVE_H, false, 0));
            out.addAll(slice("sleeve", ForgeConstants.SPRITE_SLEEVES2_FILE,
                    SLEEVE_W, SLEEVE_H, false, out.size()));
            sleeveCache = out;
        }
        return sleeveCache;
    }

    /**
     * Localiza las casillas de una hoja. NO construye ninguna imagen de JavaFX.
     *
     * @param skipFirst saltarse la casilla de arriba a la izquierda
     * @param startAt   por que numero empieza a contar (las fundas van en dos
     *                  hojas y se numeran de corrido)
     */
    private static List<LookItem> slice(final String kind, final String fileName,
                                        final int tileW, final int tileH,
                                        final boolean skipFirst, final int startAt) {
        final List<LookItem> out = new ArrayList<>();
        final File file = new File(ForgeConstants.DEFAULT_SKINS_DIR, fileName);
        if (!file.isFile()) {
            System.err.println("[neo] no encuentro la hoja " + file);
            return out;
        }

        final BufferedImage sheet;
        try {
            sheet = ImageIO.read(file);
        } catch (final Exception e) {
            System.err.println("[neo] no se ha podido leer " + file + ": " + e);
            return out;
        }
        if (sheet == null) {
            return out;
        }

        int n = startAt;
        for (int y = 0; y + tileH <= sheet.getHeight(); y += tileH) {
            for (int x = 0; x + tileW <= sheet.getWidth(); x += tileW) {
                if (skipFirst && x == 0 && y == 0) {
                    continue;
                }
                // El centro transparente delata una casilla vacia. Es la misma
                // comprobacion que hace la GUI vieja.
                final int argb = sheet.getRGB(x + tileW / 2, y + tileH / 2);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                final int px = x;
                final int py = y;
                final int number = n++;
                out.add(LookItem.ofSprite(
                        "sprite:" + kind + ":" + number,
                        String.valueOf(number + 1),
                        () -> cut(file, px, py, tileW, tileH)));
            }
        }
        return out;
    }

    /**
     * Recorta una casilla, ya para pintarla.
     *
     * <p>La hoja entera se lee UNA vez y se queda: son tres ficheros y hasta
     * ciento treinta recortes, y volver a leer un PNG de un mega por cada
     * casilla se notaria al abrir la pantalla.
     */
    private static Image cut(final File sheetFile, final int x, final int y,
                             final int w, final int h) {
        try {
            final Image sheet = sheet(sheetFile);
            if (sheet == null) {
                return null;
            }
            final PixelReader reader = sheet.getPixelReader();
            return reader == null ? null : new WritableImage(reader, x, y, w, h);
        } catch (final Throwable t) {
            // Sin ventana esto no se puede hacer, y no pasa nada: quien pinta
            // ya sabe tratar con un null.
            return null;
        }
    }

    private static final Map<String, Image> SHEETS = new HashMap<>();

    private static synchronized Image sheet(final File file) {
        final String key = file.getAbsolutePath();
        if (SHEETS.containsKey(key)) {
            return SHEETS.get(key);
        }
        Image image;
        try {
            image = new Image(file.toURI().toString(), false);
            if (image.isError()) {
                image = null;
            }
        } catch (final Throwable t) {
            image = null;
        }
        SHEETS.put(key, image);
        return image;
    }
}
