package forge.neo.quest;

import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Una aventura de PRUEBAS, con dinero de sobra.
 *
 * <p>Existe por un motivo muy concreto: la tienda, los drops, los sobres de
 * colector y la venta de repetidas <b>no se pueden mirar sin creditos</b>, y
 * conseguirlos jugando son veinte duelos. Esto fabrica una partida guardada
 * normal — del mismo formato y por el mismo camino que una de verdad — y le
 * mete el dinero.
 *
 * <p>No es un modo trampa escondido dentro del juego: es un comando de consola
 * ({@code run.cmd questmake}) que deja un fichero mas en
 * {@code %APPDATA%\Forge\quest\}. Se elige desde la pantalla de aventuras como
 * cualquier otra, y se borra desde su papelera.
 *
 * <p>El nombre lleva "PRUEBAS" delante a proposito, para que no se confunda con
 * una partida de verdad al elegir con cual seguir.
 */
public final class QuestSandbox {

    private QuestSandbox() {
    }

    /** Como se llama por defecto. */
    public static final String DEFAULT_NAME = "PRUEBAS - Commander";

    /** Cuanto dinero lleva por defecto. */
    public static final int DEFAULT_CREDITS = 500_000;

    /**
     * Fabrica (o rehace) la aventura de pruebas y la deja guardada.
     *
     * @param name     como se llama
     * @param modalidad Commander o Estandar
     * @param credits  cuantos creditos lleva
     * @param wins     victorias de mentira, para que los rivales no sean de
     *                 nivel 1 y la tienda tenga mostrador grande
     */
    public static void create(final String name, final NeoQuest.Modalidad modalidad,
                              final int credits, final int wins) {
        // Si ya existe, se rehace: es una partida de pruebas, y tener quince
        // "PRUEBAS - Commander (2)" no le sirve a nadie.
        NeoQuest.delete(name);

        // Con un preconstruido de partida, como una aventura normal: asi se
        // puede jugar un duelo sin pasar por el constructor de mazos.
        final List<Deck> starters = NeoQuest.starterDecks(modalidad);
        final Deck starter = starters.isEmpty() ? null : starters.get(0);
        NeoQuest.start(name, modalidad, NeoQuest.Dificultad.FACIL, starter);

        NeoQuest.engine().getAssets().addCredits(credits);

        // Las victorias mueven mas cosas de las que parece: el nivel de los
        // rivales, cuantos productos repone la tienda
        // (SHOP_WINS_FOR_ADDITIONAL_PACK), el multiplicador de venta y el tope
        // de precio al vender (SHOP_WINS_FOR_NO_SELL_LIMIT). Con cero, medio
        // mostrador no se puede probar.
        for (int i = 0; i < wins; i++) {
            NeoQuest.engine().getAchievements().addWin();
        }

        NeoQuest.save();

        System.out.printf(Locale.ROOT,
                "%n  Aventura de pruebas lista:%n"
                + "    nombre     %s%n"
                + "    modalidad  %s%n"
                + "    creditos   %,d%n"
                + "    victorias  %d%n"
                + "    mazo       %s%n"
                + "    coleccion  %d cartas%n"
                + "    mostrador  %d cartas sueltas%n"
                + "%n  Sale en la pantalla de aventuras: jugar.cmd -> Aventura.%n",
                name, modalidad.getLabel(), credits, wins,
                starter == null ? "(pool suelto)" : starter.getName(),
                NeoQuest.collectionSize(), NeoQuestShop.singles().size());
    }

    /** El comando de consola. */
    public static void run(final String[] args) {
        final String name = option(args, "--name", DEFAULT_NAME);
        final int credits = intOption(args, "--credits", DEFAULT_CREDITS);
        final int wins = intOption(args, "--wins", 25);
        final NeoQuest.Modalidad modalidad = contains(args, "--estandar")
                ? NeoQuest.Modalidad.ESTANDAR : NeoQuest.Modalidad.COMMANDER;

        create(name, modalidad, credits, wins);

        // Y una muestra de que el dinero sirve para algo: que se vea que la
        // tienda tiene genero y que los drops estan comprables.
        int affordable = 0;
        for (final SecretLairDrops.Drop drop : NeoQuestShop.drops()) {
            if (NeoQuestShop.canAffordDrop(drop)) {
                affordable++;
            }
        }
        System.out.printf(Locale.ROOT, "  Drops de Secret Lair al alcance: %d de %d%n",
                affordable, NeoQuestShop.drops().size());

        final List<PaperCard> singles = NeoQuestShop.singles();
        if (!singles.isEmpty()) {
            System.out.printf(Locale.ROOT, "  Ejemplo del mostrador: %s por %d cr.%n",
                    singles.get(0).getName(), NeoQuestShop.priceOfCard(singles.get(0)));
        }
        System.out.printf(Locale.ROOT, "  Sobres de colector disponibles: %d expansiones%n",
                NeoQuestShop.collectorEditions().size());
        System.out.printf(Locale.ROOT, "  (guardado en %s)%n",
                FModel.getQuest().getName());
    }

    private static boolean contains(final String[] args, final String flag) {
        for (final String a : args) {
            if (flag.equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }

    private static String option(final String[] args, final String key, final String fallback) {
        for (final String a : args) {
            if (a != null && a.startsWith(key + "=")) {
                final String v = a.substring(key.length() + 1).trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return fallback;
    }

    private static int intOption(final String[] args, final String key, final int fallback) {
        try {
            return Integer.parseInt(option(args, key, String.valueOf(fallback)));
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }
}
