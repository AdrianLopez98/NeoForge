package forge.neo.card;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.skin.FSkinProp;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

/**
 * Los iconos de habilidad de una carta de la mesa (volar, toque mortal,
 * arrollar, protección de colores...), <b>los mismos que el Forge de siempre</b>.
 *
 * <p>Pedido desde Reddit el 26-09-2026: <i>"the small ability badges like
 * flying, deathtouch... visible on the cards on the battlefield"</i>. El Forge
 * Swing ya lo hace, encendido de fábrica (Card overlays → Ability icons), y lo
 * hace bien, así que no se reinventa nada — se usa lo suyo tal cual:
 *
 * <ul>
 *   <li><b>Qué iconos</b>: {@code FSkinProp.iconsFromCardState}, en
 *       {@code forge-gui} y Java puro. Ya resuelve lo fino: la protección de
 *       dos colores con su icono bicolor, el antimaleficio "de" un color, que
 *       dañar dos veces se coma a dañar primero, el comandante y el portador
 *       del Anillo. Y lee las palabras clave que la carta tiene <b>ahora</b>:
 *       si le regalan el volar sale, si lo pierde se va.</li>
 *   <li><b>Cómo se ven</b>: el recorte de cada uno en el sprite de la skin
 *       ({@code FSkinProp.getCoords()} sobre {@code sprite_ability.png}), el
 *       mismo fichero que pinta Swing. Casi todos viven ahí; el del portador
 *       del Anillo, en {@code sprite_manaicons.png} — por eso el fichero sale
 *       del propio {@code FSkinProp} y no está escrito aquí.</li>
 * </ul>
 *
 * <p>Se lee de la skin <b>por defecto</b>, como {@code forge.neo.look.Sprites}:
 * es la que siempre está y es la que ve cualquiera que venga del Forge normal.
 */
public final class KeywordIcon {

    private KeywordIcon() {
    }

    /** Cada sprite, cargado una vez. {@code null} si no está (se apunta igual). */
    private static final Map<String, Image> SHEETS = new HashMap<>();

    /** Cada icono, recortado una vez. */
    private static final Map<FSkinProp, Image> ICONS = new EnumMap<>(FSkinProp.class);

    /**
     * Los iconos de esa carta, en el orden en que los pinta Forge.
     * Vacío fuera del campo de batalla (eso ya lo mira Forge) o si algo falla:
     * un icono que no sale no puede tumbar el repintado de la mesa.
     */
    public static List<FSkinProp> of(final CardView card) {
        if (card == null) {
            return Collections.emptyList();
        }
        try {
            final CardStateView st = card.getCurrentState();
            if (st == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(FSkinProp.iconsFromCardState(st));
        } catch (final RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Todos los iconos de habilidad que trae la skin, para la prueba
     * {@code -Dneo.kwbadges.all=true}: la forma de verlos todos sin buscar una
     * carta que no existe.
     */
    static List<FSkinProp> all() {
        final List<FSkinProp> out = new ArrayList<>();
        for (final FSkinProp p : FSkinProp.values()) {
            if (p.name().startsWith("IMG_ABILITY_")) {
                out.add(p);
            }
        }
        return out;
    }

    /** El icono, de {@code size} x {@code size}; {@code null} si la skin no lo tiene. */
    public static ImageView view(final FSkinProp prop, final double size) {
        final Image img = image(prop);
        if (img == null) {
            return null;
        }
        final ImageView v = new ImageView(img);
        v.setFitWidth(size);
        v.setFitHeight(size);
        v.setSmooth(true);
        v.setMouseTransparent(true);
        return v;
    }

    private static synchronized Image image(final FSkinProp prop) {
        if (ICONS.containsKey(prop)) {
            return ICONS.get(prop);
        }
        Image cut = null;
        try {
            final int[] c = prop.getCoords();
            final Image sheet = sheet(prop.getType().getFilename());
            if (c != null && c.length >= 4 && sheet != null
                    && c[0] + c[2] <= sheet.getWidth() && c[1] + c[3] <= sheet.getHeight()) {
                cut = new WritableImage(sheet.getPixelReader(), c[0], c[1], c[2], c[3]);
            }
        } catch (final RuntimeException ignored) {
            cut = null;
        }
        ICONS.put(prop, cut);
        return cut;
    }

    private static Image sheet(final String fileName) {
        if (fileName == null) {
            return null;
        }
        if (SHEETS.containsKey(fileName)) {
            return SHEETS.get(fileName);
        }
        Image img = null;
        final File f = new File(ForgeConstants.DEFAULT_SKINS_DIR, fileName);
        if (f.isFile()) {
            final Image loaded = new Image(f.toURI().toString());
            img = loaded.isError() ? null : loaded;
        }
        SHEETS.put(fileName, img);
        return img;
    }
}
