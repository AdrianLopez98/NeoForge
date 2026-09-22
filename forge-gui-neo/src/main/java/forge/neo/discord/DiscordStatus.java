package forge.neo.discord;

import forge.game.GameView;
import forge.neo.NeoText;

/**
 * Que se ensenya en Discord. La otra mitad de {@link DiscordRich}, que solo lo
 * lleva.
 *
 * <p>Separado a proposito, y por dos motivos: aqui no hay ni un socket ni un
 * hilo, asi que {@code run.cmd discordcheck} puede comprobar las frases sin
 * Discord delante; y todo lo que decide QUE se cuenta esta en un solo fichero,
 * en vez de repartido por las veinte pantallas.
 *
 * <h2>Los dos unicos sitios que avisan</h2>
 *
 * <p>Principio 8 de las notas de diseño, otra vez: si algo tiene que valer para todas las
 * pantallas, va por donde pasan todas.
 *
 * <ul>
 *   <li><b>La pantalla</b>: un oyente sobre {@code scene.rootProperty()} en
 *       {@code NeoApp}. <b>Uno solo</b>, y no una llamada en cada uno de los
 *       veintitantos {@code scene.setRoot(...)} — que es donde se habria
 *       quedado sin poner la pantalla numero veintitres.</li>
 *   <li><b>La partida</b>: {@code NeoMatchUI.openView}, por donde pasan TODAS
 *       las partidas las monte quien las monte (nosotros, el lobby de red o
 *       {@code QuestUtil} en un duelo de la aventura).</li>
 * </ul>
 *
 * <h2>Lo que NO se cuenta</h2>
 *
 * <p>Ni el nombre del rival, ni su IP, ni el nombre de tu mazo. Los dos
 * primeros son datos de otra persona; el tercero lo escribe el jugador y puede
 * poner cualquier cosa, y una broma privada dentro de un mazo no tiene por que
 * acabar en el perfil publico de nadie.
 *
 * <p>La <b>Aventura</b> tampoco aparece con detalle: corre en su propio proceso
 * (§11d) y desde aqui no se ve lo que pasa dentro. Mientras dura se queda lo
 * ultimo que dijo el menu, que es honesto — el juego sigue abierto.
 */
public final class DiscordStatus {

    private DiscordStatus() {
    }

    /**
     * Cambio de pantalla.
     *
     * <p>Se le pasa el nodo raiz tal cual; quien decide es {@link #keyFor}.
     */
    public static void screen(final Object root) {
        if (!DiscordRich.isEnabled()) {
            return;
        }
        final String key = root == null ? null : keyFor(root.getClass());
        if (key != null) {
            DiscordRich.update(NeoText.get(key), null);
        }
    }

    /**
     * De que habla esa raiz, o {@code null} si no hay que tocar nada.
     *
     * <p>Toma la clase y no el nodo para que {@code discordcheck} pueda
     * probarlo <b>sin ventana</b>: construir un nodo de JavaFX en un
     * comprobador sin interfaz es justo lo que no se puede hacer.
     */
    static String keyFor(final Class<?> type) {
        // Lo que no es nuestro es el StackPane con el que se montan los
        // dialogos ENCIMA de la pantalla de debajo. Abrir Ajustes sobre la
        // mesa no es irse de la partida, y sin este filtro Discord diria
        // "En el menu" en mitad de un turno.
        if (type == null || !type.getName().startsWith("forge.neo.")) {
            return null;
        }
        return screenKey(type.getSimpleName());
    }

    /**
     * De que habla una pantalla, por su nombre de clase.
     *
     * <p>Por prefijo y no por una tabla con las veintitantas: una pantalla
     * nueva de Ascenso se llama {@code Ascent…} y entra sola. Lo que no encaja
     * en ningun prefijo es menu (inicio, logros, personalizacion): decir "En el
     * menu" siempre es verdad, y es lo que menos cuenta de nadie.
     *
     * <p>{@code null} significa <b>no tocar nada</b>, que no es lo mismo que
     * "menu": la carga aun no es una pantalla, y la mesa la cuenta
     * {@link #game} con mucho mas detalle.
     */
    static String screenKey(final String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return null;
        }
        // La mesa: la cuenta openView, con formato, rivales y turno.
        if (simpleName.startsWith("Table")) {
            return null;
        }
        // Todavia arrancando: no hay nada que contar.
        if (simpleName.startsWith("Loading") || simpleName.startsWith("Language")) {
            return null;
        }
        if (simpleName.startsWith("Ascent")) {
            return "discord.ascent";
        }
        if (simpleName.startsWith("Quest") || simpleName.startsWith("PackOpening")) {
            return "discord.quest";
        }
        if (simpleName.startsWith("Draft")) {
            return "discord.draft";
        }
        if (simpleName.startsWith("Sealed")) {
            return "discord.sealed";
        }
        if (simpleName.startsWith("Tournament")) {
            return "discord.tournament";
        }
        if (simpleName.startsWith("Tutorial")) {
            return "discord.tutorial";
        }
        if (simpleName.startsWith("Puzzle")) {
            return "discord.puzzle";
        }
        if (simpleName.startsWith("DeckBuilder")) {
            return "discord.deck";
        }
        if (simpleName.startsWith("Lobby") || simpleName.startsWith("Online")
                || simpleName.startsWith("NetDecks")) {
            return "discord.online";
        }
        return "discord.menu";
    }

    /**
     * Una partida, con lo que se pueda sacar de ella.
     *
     * @param view     la partida; {@code null} no hace nada
     * @param seats    cuantos asientos son tuyos (0 = estas mirando)
     * @param net      si es una partida en red
     */
    public static void game(final GameView view, final int seats, final boolean net) {
        // Con la presencia apagada ni se componen las frases. Esto lo llama el
        // hilo del motor en CADA cambio de fase, tambien en los comprobadores
        // sin ventana: trabajar para tirarlo seria trabajar en el peor sitio.
        if (view == null || !DiscordRich.isEnabled()) {
            return;
        }
        final int players = view.getPlayers() == null ? 0 : view.getPlayers().size();
        DiscordRich.update(gameDetails(format(view), players - Math.max(seats, 0), seats, net),
                gameState(view.getTurn()));
    }

    /**
     * El nombre del formato, <b>traducido por el motor</b>.
     *
     * <p>{@code GameType.toString()} ya devuelve el nombre en el idioma de la
     * partida (lo resuelve con el {@code Localizer} de Forge al cargarse), asi
     * que no hay que mantener aqui una lista de veintitantos formatos que
     * ademas se quedaria vieja en cuanto Forge anyadiera uno.
     */
    private static String format(final GameView view) {
        final Object type = view.getGameType();
        return type == null ? NeoText.get("discord.playing") : type.toString();
    }

    /** El renglon de arriba. Publico-ish para que el comprobador lo mire. */
    static String gameDetails(final String format, final int opponents,
                              final int seats, final boolean net) {
        if (net) {
            // En red no se dice contra cuantos ni contra quien.
            return NeoText.get("discord.game.net", format);
        }
        if (seats <= 0) {
            return NeoText.get("discord.game.watch", format);
        }
        if (opponents == 1) {
            return NeoText.get("discord.game.vs1", format);
        }
        if (opponents > 1) {
            return NeoText.get("discord.game.vs", format, opponents);
        }
        return format;
    }

    /** El renglon de abajo. El turno 0 es antes de repartir: no se ensenya. */
    static String gameState(final int turn) {
        return turn > 0 ? NeoText.get("discord.turn", turn) : null;
    }
}
