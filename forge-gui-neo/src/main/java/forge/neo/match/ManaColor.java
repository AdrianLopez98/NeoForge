package forge.neo.match;

import forge.LobbyPlayer;
import forge.card.ColorSet;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * Que una tierra de dos colores PREGUNTE cual quieres que de.
 *
 * <p><b>De donde sale esto.</b> Reportado jugando, y no es una molestia: es
 * quedarse sin poder pagar. <i>"Tengo dos montanyas y una dual rojo/verde, juego
 * algo que pide rojo y verde, y la dual me coge rojo por su cuenta: ya no tengo
 * verde en ningun lado."</i>
 *
 * <p><b>Por que pasa.</b> En Forge una tierra dual es <b>una sola habilidad</b>
 * ({@code A:AB$ Mana | Cost$ T | Produced$ Combo U R}), asi que nunca llega a
 * nuestro menu de habilidades: {@code InputPayMana} ve una y la activa directa.
 * Antes de activarla apunta en la habilidad los colores que hacen falta — el
 * <i>express choice</i> — y ahi va bien: con un coste de {@code {R}{R}{G}} apunta
 * "R G", los dos. El problema esta despues, en {@code ManaEffect}:
 *
 * <pre>
 *   if (colorsNeeded != null &amp;&amp; colorsNeeded.length &gt; nMana) {
 *       colorOptions = fromMask(fullOptions.getColor() &amp; fromName(colorsNeeded[nMana]));
 *   }
 *   ...
 *   byte chosenColor = chooser.getController().chooseColor(msg, sa, colorOptions);
 * </pre>
 *
 * <p>Trata esa lista como <i>"el primer mana de este color, el segundo de este
 * otro"</i>, asi que para el unico mana que produce la tierra coge
 * {@code colorsNeeded[0]}. El orden es WUBRG, o sea que <b>siempre gana el
 * rojo</b> sobre el verde. Y entonces {@code chooseColor} recibe UN color:
 * {@code PlayerControllerHuman.chooseColor} devuelve el unico que hay sin
 * preguntar nada, ni un dialogo, ni un aviso.
 *
 * <p><b>Como se arregla sin tocar el motor.</b> Lo unico que hay que deshacer es
 * ese estrechamiento, y {@code chooseColor} es un metodo publico y
 * sobrescribible de {@code PlayerControllerHuman}. Asi que se le cambia el
 * controlador al jugador por uno igual que <b>ensancha la lista</b> a todo lo
 * que la habilidad puede producir de verdad ({@code AbilityManaPart.getComboColors}),
 * y a partir de ahi es el propio motor el que pregunta, con su dialogo y su
 * texto traducido. No se anyade ni una regla: se le devuelve al jugador una
 * eleccion que las reglas ya le daban.
 *
 * <p><b>El asiento.</b> {@code Player.dangerouslySetController} es publico — lo
 * usa el propio Forge para sentar a otro en el sitio de alguien — y
 * {@code PlayerControllerHuman} tiene un constructor de <b>relevo</b>
 * ({@code (Player, LobbyPlayer, PlayerControllerHuman)}, el del control mental)
 * que <b>comparte</b> el {@code IGuiGame}, la {@code InputQueue} y el
 * {@code InputProxy} con el controlador que releva. Eso es lo que hace que esto
 * sea seguro: todo lo que {@code HostedMatch} cableo con el controlador de antes
 * — la lista de humanos, el reenvio de eventos, conceder, "otra partida" — sigue
 * apuntando a un objeto vivo y con las mismas tuberias. Lo unico que cambia es a
 * quien le pregunta <i>el motor</i>, que es {@code player.getController()}.
 *
 * <p>Se instala desde {@code NeoMatchUI.openView}, que es por donde pasan TODAS
 * las partidas (principio 8): la normal, el puzzle, el tutorial, el duelo de la
 * aventura — que lo monta el motor y no pasa por {@code NeoGame} — y el asiento
 * local de una partida en red.
 *
 * @see SafeAi el otro sitio donde se releva un controlador, y por el mismo motivo
 */
public final class ManaColor {

    /** {@code -Dneo.mana.debug=true}: decir cada color que se pregunta. */
    private static final boolean DEBUG = Boolean.getBoolean("neo.mana.debug");

    private ManaColor() {
    }

    /**
     * Sienta nuestro controlador en este asiento, si es un humano local.
     *
     * @return si de verdad se ha cambiado algo
     */
    public static boolean install(final IGameController controller) {
        if (!(controller instanceof PlayerControllerHuman human)) {
            // Un observador, o el asiento de un invitado que se conduce por red:
            // ahi el controlador no es este objeto y no hay nada que relevar.
            return false;
        }
        final Player player = human.getPlayer();
        if (player == null || player.getController() instanceof Asking) {
            return false;
        }
        final LobbyPlayer lobby = human.getLobbyPlayer();
        player.dangerouslySetController(new Asking(player, lobby, human));
        return true;
    }

    /** Si este asiento ya tiene nuestro controlador. Lo usa el comprobador. */
    public static boolean isInstalled(final Player player) {
        return player != null && player.getController() instanceof Asking;
    }

    /**
     * Todos los colores que esta habilidad puede producir de verdad.
     *
     * <p>Solo toca las habilidades de <b>mana combinado</b> ({@code Produced$
     * Combo ...}), que son las duales, los templos, las de "un mana del color
     * elegido" y la Torre de mando. Lo demas se devuelve tal cual: por
     * {@code chooseColor} pasan muchas mas preguntas — elegir un color para una
     * proteccion, para un {@code ChooseColor} de entrada — y ahi la lista que
     * manda el motor ya es la correcta.
     *
     * <p>Y si aun asi solo sale un color, se deja como estaba: preguntar con una
     * sola respuesta posible es un dialogo que no decide nada.
     */
    static ColorSet widen(final SpellAbility sa, final ColorSet colors) {
        if (sa == null) {
            return colors;
        }
        final AbilityManaPart mana = sa.getManaPart();
        if (mana == null || !mana.isComboMana()) {
            return colors;
        }
        final String combo = mana.getComboColors(sa);
        if (combo == null || combo.isBlank()) {
            return colors;
        }
        final ColorSet produced = ColorSet.fromNames(combo.split(" "));
        final byte mask = (byte) ((colors == null ? 0 : colors.getColor()) | produced.getColor());
        final ColorSet all = ColorSet.fromMask(mask);
        return all.countColors() > 1 ? all : colors;
    }

    /**
     * El controlador humano de siempre, pero preguntando el color.
     *
     * <p>Se sobrescribe <b>un</b> metodo. Todo lo demas —- prioridad, objetivos,
     * bloqueos, conceder— es el de Forge sin tocar.
     */
    private static final class Asking extends PlayerControllerHuman {

        Asking(final Player player, final LobbyPlayer lobby, final PlayerControllerHuman owner) {
            super(player, lobby, owner);
        }

        @Override
        public byte chooseColor(final String message, final SpellAbility sa, final ColorSet colors) {
            final ColorSet options = widen(sa, colors);
            if (DEBUG) {
                final int was = colors == null ? 0 : colors.countColors();
                System.out.printf("[mana] chooseColor %s: %d -> %d color(es)%s%n",
                        sa == null || sa.getHostCard() == null ? "?" : sa.getHostCard().getName(),
                        was, options.countColors(),
                        options.countColors() > was ? "  <-- ENSANCHADO, ahora pregunta" : "");
            }
            return super.chooseColor(message, sa, options);
        }
    }
}
