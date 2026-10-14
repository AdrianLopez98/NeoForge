package forge.neo.quest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import forge.card.CardEdition;
import forge.item.BoosterPack;
import forge.localinstance.properties.ForgeConstants;
import forge.neo.ascent.AscentSeed;

/**
 * <b>El sobre del dia</b> de la Quest (14-10-2026, Ana: <i>"un sobre diario
 * aleatorio ... de una de las expansiones que tengas dentro del mundo en el que
 * estas ... esto tiene que ser siempre offline y portable"</i>).
 *
 * <p>Un sobre gratis al dia en cada Quest. Las reglas, todas nuestras y sin
 * JavaFX (Android las usa por el jar):
 *
 * <ul>
 *   <li><b>El dia es el del reto de Ascenso</b> ({@link AscentSeed#today}, en
 *       UTC): cambia a las 02:00 de Espanya en verano y a la 01:00 en invierno,
 *       a la vez en todo el mundo, y lo sabe cada ordenador sin conectarse a
 *       nada. Sin internet funciona igual: es la fecha del reloj.</li>
 *   <li><b>La expansion sale de las de tu Quest</b>
 *       ({@link NeoQuestShop#editions}: las que deja el pozo de la Quest y, si
 *       el mundo limita la tienda, las de ese mundo). Quien juega solo con
 *       expansiones de 2005 no recibe uno de Final Fantasy; quien juega con
 *       todas, cualquiera.</li>
 *   <li><b>Cual toca hoy esta decidido de antemano</b>: sale de la fecha y del
 *       nombre de la Quest ({@link #todaysEdition}), asi que el cuartel puede
 *       decir de que es antes de abrirlo y reiniciar no lo cambia.</li>
 *   <li><b>Uno por dia y por Quest</b>, apuntado en {@code <nombre>.sobre}
 *       junto a la partida (como el Coliseo): en la portable viaja dentro de
 *       {@code datos\}. Retrasar el reloj un dia no da otro; un dia apuntado
 *       de mas de mañana se da por roto (un reloj adelantado no puede dejar la
 *       Quest sin sobre hasta esa fecha).</li>
 *   <li><b>No se acumulan</b> (Ana): el dia que no entras, ese sobre se
 *       pierde. Solo hay el de hoy, y abierto, el cuartel ya no lo ensenya.</li>
 * </ul>
 */
public final class NeoDailyPack {

    private NeoDailyPack() {
    }

    /** Lo que ha salido hoy. */
    public static final class Claimed {
        public final CardEdition edition;
        public final NeoQuestShop.Opened opened;

        Claimed(final CardEdition edition, final NeoQuestShop.Opened opened) {
            this.edition = edition;
            this.opened = opened;
        }
    }

    /**
     * Cuantas expansiones se prueban si la de hoy no sale (un sobre que revienta
     * al montarse). Con eso basta: casi todas salen a la primera.
     */
    private static final int TRIES = 12;

    // ---------------------------------------------------------------
    // Hoy

    /** Si hoy hay sobre por abrir en esta Quest. */
    public static boolean available() {
        return available(Instant.now());
    }

    public static boolean available(final Instant now) {
        if (!NeoQuest.isActive()) {
            return false;
        }
        final long last = lastDay(NeoQuest.name());
        final long today = day(now);
        // Un dia apuntado de MAS de mañana es un reloj que iba adelantado (la
        // portable en otro ordenador): se da por roto, o esa Quest se quedaria
        // sin sobre hasta esa fecha en todas partes. Uno de diferencia (husos,
        // el cambio de dia) sigue sin dar otro.
        final boolean due = last < today || last > today + 1;
        return due && todaysEdition(now) != null;
    }

    /** La expansion del sobre de hoy, o null si esta Quest no tiene ninguna con sobre. */
    public static CardEdition todaysEdition() {
        return todaysEdition(Instant.now());
    }

    public static CardEdition todaysEdition(final Instant now) {
        if (!NeoQuest.isActive()) {
            return null;
        }
        final List<CardEdition> order = order(NeoQuest.name(), day(now));
        return order.isEmpty() ? null : order.get(0);
    }

    /** Cuando hay otro: la proxima medianoche UTC, la del reto del dia. */
    public static Instant next(final Instant now) {
        return AscentSeed.nextDaily(now);
    }

    // ---------------------------------------------------------------
    // Abrirlo

    /** Abre el de hoy y lo mete en tu coleccion. Null si no hay. */
    public static Claimed claim() {
        return claim(Instant.now());
    }

    public static synchronized Claimed claim(final Instant now) {
        if (!available(now)) {
            return null;
        }
        final String quest = NeoQuest.name();
        final long today = day(now);
        int tries = 0;
        for (final CardEdition e : order(quest, today)) {
            if (tries++ >= TRIES) {
                break;
            }
            final BoosterPack pack = NeoQuestShop.boosterOf(e);
            // grantFree abre ANTES de meter nada (NeoQuestShop.open): si este
            // revienta, no se ha dado nada y se prueba el siguiente.
            final NeoQuestShop.Opened opened = pack == null ? null : NeoQuestShop.grantFree(pack);
            if (opened == null) {
                System.out.println("[sobre del dia] el de " + e.getCode() + " no sale, pruebo otro");
                continue;
            }
            // Apuntado DESPUES de darlo: si no se pudiera escribir, lo peor es
            // otro sobre gratis, no quedarse sin el.
            write(quest, today, e.getCode());
            System.out.println("[sobre del dia] " + quest + ": " + e.getCode() + " (" + e.getName() + ")");
            return new Claimed(e, opened);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Por dentro

    static long day(final Instant now) {
        return AscentSeed.today(now).toEpochDay();
    }

    /**
     * Las expansiones de la Quest, barajadas con la fecha y el nombre de la
     * Quest: la primera es la de hoy, y las demas, las de reserva por si no
     * sale. {@link NeoQuestShop#editions} viene ordenada por fecha, asi que la
     * misma Quest el mismo dia da siempre la misma.
     */
    static List<CardEdition> order(final String quest, final long day) {
        final List<CardEdition> all = new ArrayList<>(NeoQuestShop.editions());
        Collections.shuffle(all, new Random(day * 1_000_003L + (quest == null ? 0 : quest.hashCode())));
        return all;
    }

    /** Junto a la partida: {@code <nombre>.sobre}, con el dia y la expansion. */
    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".sobre");
    }

    /** El dia (UTC, en dias desde 1970) del ultimo abierto, o -1. */
    static long lastDay(final String questName) {
        final String[] saved = read(questName);
        if (saved == null) {
            return -1;
        }
        try {
            return Long.parseLong(saved[0].trim());
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static String[] read(final String questName) {
        final File f = fileOf(questName);
        if (!f.isFile()) {
            return null;
        }
        try {
            final List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return null;
            }
            return new String[] {lines.get(0).trim(), lines.size() > 1 ? lines.get(1).trim() : ""};
        } catch (final java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static void write(final String questName, final long day, final String code) {
        try {
            Files.write(fileOf(questName).toPath(),
                    (day + "\n" + code + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[sobre del dia] no se ha podido apuntar: " + e);
        }
    }

    /** Al borrar una Quest: su sobre del dia con ella. */
    static void deleteSaved(final String questName) {
        final File f = fileOf(questName);
        if (f.isFile()) {
            f.delete();
        }
    }

    /** Solo las pruebas: como si no se hubiera abierto nunca. */
    static void forgetForTest(final String questName) {
        deleteSaved(questName);
    }
}
