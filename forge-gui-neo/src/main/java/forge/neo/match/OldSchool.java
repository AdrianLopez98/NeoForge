package forge.neo.match;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.GameFormat;
import forge.util.FileSection;

/**
 * <b>Old School 93/94</b>: Construido con las primeras ediciones de Magic, de
 * Alpha a Fallen Empires (itch.io, 29-10-2026: <i>"There are very few good
 * options for playing this format for free, especially against AI"</i>).
 *
 * <p>Forge no lo trae en {@code res/formats/}, y ahi no se anyade nada (regla
 * de oro). Pero un formato de Forge no es mas que un {@link GameFormat} — sus
 * ediciones, sus prohibidas y sus restringidas — y su constructor es publico:
 * se monta aqui y {@link NeoFormat#poolFormat()} lo devuelve como devuelve
 * Premodern. De ahi para abajo (constructor de mazos, legalidad, lista para
 * jugar) es lo mismo que cualquier otro formato.
 *
 * <p>Hay muchas variantes de reglas (sueca, de Eternal Central, de Cardmarket,
 * de Madrid...). Se sigue la de <b>Eternal Central</b>, que es tambien la de
 * Cardmarket: Alpha, Beta, Unlimited, Revised, Arabian Nights, Antiquities,
 * Legends, The Dark y Fallen Empires, mas las tres promos de los libros de
 * HarperPrism. Lo unico que no se aplica es el <i>mana burn</i>: eso es una
 * regla del motor, y no se toca.
 *
 * <p><b>Los mazos de los rivales vienen en el jar.</b> Ni uno solo de los 505
 * preconstruidos de Forge es de esas ediciones, y sin mazos legales cada rival
 * jugaba una copia del tuyo. Son arquetipos clasicos del formato, con lo que la
 * IA de Forge sabe jugar; {@code run.cmd deckcheck} comprueba que existan,
 * que sean legales y que la IA no tenga ninguna carta apartada.
 *
 * <p>Sin JavaFX: Android lo usa por el jar.
 */
public final class OldSchool {

    /** Lo que se busca en {@code res/formats/}: no esta, y por eso se monta aqui. */
    static final String NAME = "Old School 93/94";

    public static final List<String> SETS = Collections.unmodifiableList(Arrays.asList(
            "LEA", "LEB", "2ED", "3ED", "ARN", "ATQ", "LEG", "DRK", "FEM", "PHPR"));

    /** Las cartas de ante. */
    static final List<String> BANNED = Collections.unmodifiableList(Arrays.asList(
            "Bronze Tablet", "Contract from Below", "Darkpact", "Demonic Attorney",
            "Jeweled Bird", "Rebirth", "Tempest Efreet"));

    static final List<String> RESTRICTED = Collections.unmodifiableList(Arrays.asList(
            "Ancestral Recall", "Balance", "Black Lotus", "Braingeyser", "Channel", "Chaos Orb",
            "Demonic Tutor", "Library of Alexandria", "Mana Drain", "Mind Twist", "Mox Emerald",
            "Mox Jet", "Mox Pearl", "Mox Ruby", "Mox Sapphire", "Recall", "Regrowth", "Sol Ring",
            "Time Vault", "Time Walk", "Timetwister", "Wheel of Fortune"));

    /** La tercera promo de HarperPrism, que Forge lleva en otra edicion. */
    static final List<String> ADDITIONAL = Collections.singletonList("Nalathni Dragon");

    /** Los mazos de {@code forge/neo/decks/oldschool/} (un jar no se puede listar). */
    static final List<String> DECKS = Collections.unmodifiableList(Arrays.asList(
            "black-juzam", "rg-erhnam-berserk", "white-weenie", "blue-skies", "red-artifacts"));

    /** Para {@code deckcheck}: cuantos mazos tendria que haber. */
    public static int decksInJar() {
        return DECKS.size();
    }

    private static GameFormat format;
    private static List<Deck> decks;

    private OldSchool() {
    }

    /** El formato, construido la primera vez (tiene que estar cargada la base de cartas). */
    public static synchronized GameFormat format() {
        if (format == null) {
            format = new GameFormat(NAME,
                    new java.util.GregorianCalendar(1994, java.util.Calendar.DECEMBER, 31).getTime(),
                    SETS, BANNED, RESTRICTED, false, ADDITIONAL, null, 0,
                    GameFormat.FormatType.CASUAL, GameFormat.FormatSubType.CUSTOM);
        }
        return format;
    }

    /** Los mazos que trae el jar, leidos una vez. Copia: quien la recibe puede tocarla. */
    public static synchronized List<Deck> decks() {
        if (decks == null) {
            final List<Deck> out = new ArrayList<>();
            for (final String name : DECKS) {
                final Deck d = read(name);
                if (d != null) {
                    out.add(d);
                }
            }
            decks = out;
        }
        return new ArrayList<>(decks);
    }

    private static Deck read(final String name) {
        final String path = "/forge/neo/decks/oldschool/" + name + ".dck";
        try (InputStream in = OldSchool.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[oldschool] no esta " + path);
                return null;
            }
            final List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String l; (l = r.readLine()) != null; ) {
                    lines.add(l);
                }
            }
            return DeckSerializer.fromSections(FileSection.parseSections(lines));
        } catch (final IOException | RuntimeException e) {
            System.err.println("[oldschool] no se ha podido leer " + path + ": " + e);
            return null;
        }
    }
}
