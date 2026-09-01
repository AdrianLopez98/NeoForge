package forge.neo.look;

import java.io.File;

import javafx.scene.image.Image;

/**
 * Una cosa que se puede elegir: un avatar, una funda, un tapete.
 *
 * <p>Da igual que venga de una hoja de sprites de Forge o de un PNG que hayas
 * traido tu: la pantalla las trata igual y solo pregunta por su
 * {@link #image()}. Esa es toda la gracia de esta clase.
 *
 * <p>La imagen se resuelve <b>tarde y una sola vez</b>. Trocear la hoja de
 * avatares son 130 recortes y leer un tapete es un JPEG de un mega: hacerlo al
 * construir la lista se notaria al abrir la pantalla, y la mayoria no se llegan
 * a ver nunca.
 */
public final class LookItem {

    /** {@code sprite:avatar:12}, {@code file:foto.png}, {@code skin:bg_match.jpg}, {@code none}. */
    private final String id;
    private final String label;
    private final File file;
    private final java.util.function.Supplier<Image> source;

    private Image cached;
    private boolean resolved;

    private LookItem(final String id, final String label, final File file,
                     final java.util.function.Supplier<Image> source) {
        this.id = id;
        this.label = label;
        this.file = file;
        this.source = source;
    }

    /** Uno que sale de una hoja de sprites de Forge. */
    static LookItem ofSprite(final String id, final String label,
                             final java.util.function.Supplier<Image> source) {
        return new LookItem(id, label, null, source);
    }

    /** Uno que es un fichero: importado por el jugador, o del skin. */
    static LookItem ofFile(final String id, final String label, final File file) {
        return new LookItem(id, label, file, null);
    }

    /** "Ninguno": el tapete vacio, la mesa de siempre. */
    public static LookItem none() {
        return new LookItem(NeoLook.NO_PLAYMAT, "", null, null);
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /** El fichero, si lo tiene. Null para los sprites y para "ninguno". */
    public File getFile() {
        return file;
    }

    /** Si lo ha traido el jugador (y por tanto se puede borrar). */
    public boolean isImported() {
        return id.startsWith("file:");
    }

    /** Si es el "ninguno". */
    public boolean isNone() {
        return NeoLook.NO_PLAYMAT.equals(id);
    }

    /**
     * La imagen, o null si no hay ninguna o no se ha podido leer.
     *
     * <p>Un fichero corrupto o un formato que JavaFX no entienda no puede
     * tumbar la pantalla: se devuelve null y quien pinte que ponga lo suyo.
     */
    public Image image() {
        if (resolved) {
            return cached;
        }
        resolved = true;
        try {
            if (file != null) {
                cached = new Image(file.toURI().toString(), false);
                if (cached.isError()) {
                    cached = null;
                }
            } else if (source != null) {
                cached = source.get();
            }
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido leer " + id + ": " + e);
            cached = null;
        }
        return cached;
    }

    @Override
    public String toString() {
        return label.isEmpty() ? id : label;
    }
}
