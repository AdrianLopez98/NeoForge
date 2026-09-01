package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;

import forge.gamemodes.quest.QuestWorld;
import forge.model.FModel;

/**
 * Los mundos de la aventura.
 *
 * <p>Un mundo <b>cambia con que se juega</b>. No es un decorado: cada uno trae
 * su lista de expansiones y su lista de prohibidas ({@code GameFormatQuest}), y
 * ademas su propia carpeta de <b>rivales</b> y de <b>desafios</b>. Jugar en
 * Zendikar significa que en la tienda solo hay sobres de Zendikar, que los
 * premios salen de ahi y que los rivales son los de esa carpeta.
 *
 * <p>Forge trae <b>88</b> en {@code res/quest/world/worlds.txt}, desde Beta y
 * Arabian Nights hasta lo ultimo, mas seis "aleatorios" que no son un bloque
 * sino un formato entero (Estandar, Pioneer, Modern, Commander).
 *
 * <p>Todo esto ya estaba: {@code QuestController.setWorld} cambia de mundo y
 * {@code newGame(..., startingWorld, ...)} lo elige al empezar. Lo unico que
 * habia que hacer era ofrecerlo.
 *
 * <p>⚠️ <b>Cambiar de mundo son TRES llamadas, no una.</b> {@code setWorld}
 * solo apunta el nombre; quien vuelve a leer los rivales y los desafios de la
 * carpeta nueva son {@code resetDuelsManager()} y
 * {@code resetChallengesManager()}. Sin ellas cambias de mundo y sigues
 * peleando con los rivales del anterior — que es el sintoma mas confuso
 * posible, porque la pantalla ya dice el nombre del mundo nuevo.
 */
public final class NeoQuestWorlds {

    private NeoQuestWorlds() {
    }

    /** Todos los mundos, en el orden en que los da el motor. */
    public static List<QuestWorld> all() {
        final List<QuestWorld> out = new ArrayList<>();
        try {
            for (final QuestWorld w : FModel.getWorlds()) {
                if (w != null) {
                    out.add(w);
                }
            }
        } catch (final RuntimeException e) {
            System.err.println("[mundos] no se han podido leer: " + e);
        }
        return out;
    }

    /** El mundo por defecto: todas las cartas y los 238 rivales faciles. */
    public static QuestWorld main() {
        return byName(QuestWorld.MAINWORLDNAME);
    }

    public static QuestWorld byName(final String name) {
        if (name == null) {
            return null;
        }
        try {
            return FModel.getWorlds().get(name);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** En que mundo estas. */
    public static QuestWorld current() {
        return NeoQuest.isActive() ? NeoQuest.engine().getWorld() : null;
    }

    /**
     * Con que se juega en ese mundo, en una linea.
     *
     * <p>El nombre no lo dice: "Jamuraa" o "Sarpadia" no le dicen nada a quien
     * no se sepa los bloques de memoria. Las expansiones si, y son el dato por
     * el que se elige un mundo.
     */
    public static String describe(final QuestWorld world) {
        if (world == null) {
            return "";
        }
        final var format = world.getFormat();
        if (format == null) {
            // Los mundos sin formato son los que no restringen nada: el
            // principal y los aleatorios, que ya se explican por su nombre.
            return "";
        }
        try {
            final List<String> sets = format.getAllowedSetCodes();
            if (sets == null || sets.isEmpty()) {
                return "";
            }
            return String.join(", ", sets);
        } catch (final RuntimeException e) {
            // Hay mundos con ROTACION ("Evolving Wilds"), y sus expansiones no
            // son una lista fija: las calcula QueueRandomRotation a partir del
            // NOMBRE de la partida en curso. Sin partida cargada — que es
            // exactamente el caso de la pantalla de "nueva aventura" — eso
            // revienta con NPE. No se puede saber que trae hasta empezarlo, y
            // decirlo es mas honrado que inventarselo.
            return "";
        }
    }

    /** Cuantas cartas hay en ese mundo. Es lo que separa un bloque de todo Magic. */
    public static int cardCount(final QuestWorld world) {
        if (world == null) {
            return 0;
        }
        try {
            final var cards = world.getAllCards();
            return cards == null ? 0 : cards.size();
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /**
     * Viaja a otro mundo.
     *
     * <p>Las tres llamadas, en orden. Y se guarda, porque el mundo es parte de
     * la partida: si se pierde al salir, el jugador vuelve y esta donde estaba
     * antes de viajar, sin explicacion.
     *
     * @return true si se ha viajado
     */
    public static boolean travelTo(final QuestWorld world) {
        if (world == null || !NeoQuest.isActive()) {
            return false;
        }
        final QuestWorld before = current();
        if (before != null && before.getName().equals(world.getName())) {
            return false;
        }
        NeoQuest.engine().setWorld(world);
        // Sin estas dos, el mundo cambia de nombre y los rivales siguen siendo
        // los de antes.
        NeoQuest.engine().resetDuelsManager();
        NeoQuest.engine().resetChallengesManager();
        NeoQuest.forgetDuels();
        NeoQuest.forgetChallenges();
        NeoQuest.save();
        System.out.printf(java.util.Locale.ROOT, "[mundos] %s -> %s%n",
                before == null ? "(ninguno)" : before.getName(), world.getName());
        return true;
    }
}
