package forge.neo.look;

import java.util.ArrayList;
import java.util.List;

import forge.LobbyPlayer;
import forge.player.GamePlayerUtil;
import forge.util.GuiDisplayUtil;

/**
 * Quien se sienta a la mesa: tu, con tu nombre y tu cara, y los rivales.
 *
 * <p>Estaban todos llamandose "Human", "IA-1", "IA-2"... y eso es lo que hace
 * que una partida no se recuerde. Forge trae un <b>generador con cientos de
 * nombres</b> ({@code NameGenerator}, de fantasia y normales) que no estabamos
 * usando: una partida contra Sythril, Kaldar y Occelot se cuenta despues; una
 * contra IA-1, IA-2 e IA-3 no.
 *
 * <p>Los nombres de los rivales se <b>fijan al elegirlos</b>: si en la pantalla
 * de personalizacion no has puesto ninguno, se sortea uno la primera vez y se
 * guarda. Sortearlo en cada partida haria que el rival de siempre fuera otro
 * cada vez, que es justo lo contrario de lo que se busca.
 */
public final class NeoPlayers {

    private NeoPlayers() {
    }

    /**
     * Tu, con tu nombre y tu avatar.
     *
     * <p>El avatar que se le pasa al motor es un <b>indice</b> de su hoja de
     * sprites, asi que solo puede llevar los de Forge. El tuyo — que puede ser
     * un PNG que hayas traido — lo pinta la interfaz por su cuenta
     * ({@code PlayerBar}); este indice es el que veran otros sitios del motor.
     */
    public static LobbyPlayer human() {
        final String name = NeoLook.playerName();
        if (name.isBlank()) {
            return GamePlayerUtil.getGuiPlayer();
        }
        // La funda es la del MAZO con el que se va a jugar (si lleva una): el
        // indice que se le pasa al motor tiene que decir lo mismo que pinta la
        // mesa, o el rival de una partida en red veria otra distinta.
        return GamePlayerUtil.getGuiPlayer(name, avatarIndexOf(NeoLook.currentAvatar()),
                sleeveIndexOf(NeoLook.sleeveInPlay()), true);
    }

    /**
     * El rival numero {@code index} (desde 0), con su nombre, su cara y su
     * forma de jugar (ver {@link RivalSetup}).
     *
     * @param aiProfile un perfil, "" (el de Forge), {@link RivalSetup#RANDOM}
     *                  o {@link RivalSetup#perRival(String)} en una partida
     *                  normal, donde cada rival juega con el suyo
     */
    public static LobbyPlayer ai(final int index, final String aiProfile) {
        final String name = aiName(index);
        final String profile = RivalSetup.resolve(aiProfile, index, RANDOM);
        // La cara: si es de la hoja de Forge, su numero va al motor (y la ve
        // tambien un invitado en red); si es un PNG tuyo, el motor no sabe
        // pintarla y se apunta por nombre para que la pinte la mesa.
        final String face = RivalSetup.avatar(index);
        final int sprite = RivalSetup.spriteIndexOf(face);
        if (sprite < 0 && !face.isEmpty()) {
            CUSTOM_FACES.put(name, face);
        }
        System.out.printf(java.util.Locale.ROOT, "  Rival %d: %s | IA %s%s%n", index + 1, name,
                profile.isEmpty() ? "(la de Forge)" : profile,
                face.isEmpty() ? "" : " | cara " + face);
        return GamePlayerUtil.createAiPlayer(name, sprite >= 0 ? sprite : index, profile);
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    /**
     * Rivales con una cara importada (PNG): nombre -> id de avatar.
     *
     * <p>Solo se anyade: se vacia entero al montar cada partida
     * ({@link #newMatch}). Quitar al sentar a un rival sin cara le borraba la
     * suya a otro que se llamara igual.
     */
    private static final java.util.Map<String, String> CUSTOM_FACES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Una partida nueva: las caras importadas de la anterior no valen. */
    public static void newMatch() {
        CUSTOM_FACES.clear();
    }

    /**
     * El id de la cara importada de un rival, o {@code null} si lleva una de la
     * hoja de Forge (entonces vale {@code PlayerView.getAvatarIndex}).
     */
    public static String customFaceOf(final String playerName) {
        return playerName == null ? null : CUSTOM_FACES.get(playerName);
    }

    /**
     * Como se llama el rival numero {@code index}.
     *
     * <p>El que hayas puesto tu; si no, uno sorteado <b>y guardado</b>, para
     * que sea el mismo la proxima vez.
     */
    public static String aiName(final int index) {
        final String saved = NeoLook.aiName(index);
        if (!saved.isBlank()) {
            return saved;
        }
        final String fresh = randomName(index);
        remember(index, fresh);
        return fresh;
    }

    /**
     * Un nombre del generador de Forge que no repita a los que ya hay.
     *
     * <p>Si el generador fallara — no deberia, es una lista en memoria — se cae
     * al "IA-N" de siempre: sin nombre no hay partida.
     */
    private static String randomName(final int index) {
        try {
            final List<String> taken = new ArrayList<>(NeoLook.aiNames());
            taken.add(NeoLook.playerName());
            for (int attempt = 0; attempt < 20; attempt++) {
                final String candidate = GuiDisplayUtil.getRandomAiName();
                if (candidate != null && !candidate.isBlank() && !taken.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (final RuntimeException e) {
            System.err.println("[neo] el generador de nombres ha fallado: " + e);
        }
        return "IA-" + (index + 1);
    }

    /** Guarda el nombre sorteado en su hueco, alargando la lista si hace falta. */
    private static void remember(final int index, final String name) {
        final List<String> names = new ArrayList<>(NeoLook.aiNames());
        while (names.size() <= index) {
            names.add("");
        }
        names.set(index, name);
        NeoLook.setAiNames(names);
    }

    // ---------------------------------------------------------------

    /** El numero de sprite de un avatar, o 0 si el elegido es un fichero tuyo. */
    private static int avatarIndexOf(final LookItem item) {
        return spriteNumber(item, "sprite:avatar:");
    }

    private static int sleeveIndexOf(final LookItem item) {
        return spriteNumber(item, "sprite:sleeve:");
    }

    private static int spriteNumber(final LookItem item, final String prefix) {
        if (item == null || !item.getId().startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(item.getId().substring(prefix.length()));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}
