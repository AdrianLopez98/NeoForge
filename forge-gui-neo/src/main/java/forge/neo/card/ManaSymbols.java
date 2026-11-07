package forge.neo.card;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.skin.FSkinProp;

/**
 * <b>Los simbolos de mana de Forge</b> ({U}, {T}, {2/W}...) en vez del texto
 * entre llaves.
 *
 * <p>itch.io, 06-11-2026: <i>"show the actual mana symbols instead of {U} for
 * Blue Mana and {T} for turn"</i>. Los iconos son los de Forge
 * ({@code res/skins/default/sprite_manaicons.png} y sus dos hojas hermanas),
 * los mismos que ya traemos para las fundas y los avatares: no se anyade ningun
 * fichero. Donde esta cada uno lo dice el propio Forge
 * ({@link FSkinProp#MANA_IMG}, por la clave del simbolo sin barras: "WU",
 * "2W", "WP", "T"), asi que un simbolo nuevo que Forge dibuje llega solo.
 *
 * <p>Solo DENTRO de la partida y de las cartas: la marca (logo, portada,
 * clips) sigue sin simbolos de Wizards (ver memoria "marca sin simbolos").
 * Se apaga en Ajustes ({@link forge.neo.NeoSettings#manaIcons}) y entonces
 * todo vuelve a como estaba.
 *
 * <p>Java puro: lo usa Android por el jar, cada uno recorta con lo suyo
 * (JavaFX en el escritorio, Bitmap en Android).
 */
public final class ManaSymbols {

    private ManaSymbols() {
    }

    /** Donde esta un simbolo: el fichero de la hoja y su recuadro. */
    public static final class Cell {
        public final File file;
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        Cell(final File file, final int x, final int y, final int w, final int h) {
            this.file = file;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** Para las caches: un recuadro de una hoja. */
        public String key() {
            return file.getName() + "@" + x + "," + y + "," + w + "," + h;
        }
    }

    /** Un trozo de texto: o texto llano ({@code symbol} null) o un simbolo. */
    public static final class Piece {
        public final String text;
        /** La casilla del simbolo, o null si es texto. */
        public final Cell symbol;

        Piece(final String text, final Cell symbol) {
            this.text = text;
            this.symbol = symbol;
        }
    }

    /** Si se pintan los simbolos (el ajuste, encendido de fabrica). */
    public static boolean enabled() {
        return forge.neo.NeoSettings.manaIcons();
    }

    /**
     * La casilla del simbolo, o null si Forge no lo dibuja (entonces se queda
     * el texto, o la pastilla de siempre).
     *
     * @param token lo de dentro de las llaves, o con ellas: "U", "{W/U}", "2/W", "T"
     */
    public static Cell cell(final String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.startsWith("{") && t.endsWith("}")) {
            t = t.substring(1, t.length() - 1);
        }
        t = t.replace("/", "").toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            return null;
        }
        FSkinProp prop = FSkinProp.MANA_IMG.get(t);
        // Un hibrido escrito al reves ("UW") es el mismo simbolo.
        if (prop == null && t.length() == 2 && Character.isLetter(t.charAt(0))) {
            prop = FSkinProp.MANA_IMG.get("" + t.charAt(1) + t.charAt(0));
        }
        if (prop == null || prop.getType() == null || prop.getType().getFilename() == null) {
            return null;
        }
        final File file = new File(ForgeConstants.DEFAULT_SKINS_DIR, prop.getType().getFilename());
        if (!file.isFile()) {
            return null;
        }
        final int[] c = prop.getCoords();
        return new Cell(file, c[0], c[1], c[2], c[3]);
    }

    /**
     * La casilla de CUALQUIER icono de las hojas de Forge, por el nombre de su
     * {@code FSkinProp} ("IMG_ABILITY_FLYING"), o null. Para los iconos de
     * habilidad de Android (decision 642).
     */
    public static Cell ofProp(final String propName) {
        try {
            final FSkinProp prop = FSkinProp.valueOf(propName);
            if (prop.getType() == null || prop.getType().getFilename() == null) {
                return null;
            }
            final File file = new File(ForgeConstants.DEFAULT_SKINS_DIR, prop.getType().getFilename());
            if (!file.isFile()) {
                return null;
            }
            final int[] c = prop.getCoords();
            return new Cell(file, c[0], c[1], c[2], c[3]);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** La casilla del mana flotante de un color: "W", "U", "B", "R", "G" o "C". */
    public static Cell ofColor(final String letter) {
        return cell(letter);
    }

    /**
     * Parte un texto de reglas en trozos: texto llano y simbolos. Lo que va
     * entre llaves y Forge no dibuja ({E}, la energia) se queda como texto, tal
     * cual: mejor un {E} que perderlo.
     */
    public static List<Piece> split(final String text) {
        final List<Piece> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        final StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            final char ch = text.charAt(i);
            if (ch == '{') {
                final int close = text.indexOf('}', i);
                if (close > i) {
                    final Cell cell = cell(text.substring(i, close + 1));
                    if (cell != null) {
                        if (plain.length() > 0) {
                            out.add(new Piece(plain.toString(), null));
                            plain.setLength(0);
                        }
                        out.add(new Piece(text.substring(i, close + 1), cell));
                        i = close + 1;
                        continue;
                    }
                }
            }
            plain.append(ch);
            i++;
        }
        if (plain.length() > 0) {
            out.add(new Piece(plain.toString(), null));
        }
        return out;
    }

    /** Si el texto lleva algun simbolo que se pueda dibujar. */
    public static boolean hasSymbols(final String text) {
        for (final Piece p : split(text)) {
            if (p.symbol != null) {
                return true;
            }
        }
        return false;
    }
}
