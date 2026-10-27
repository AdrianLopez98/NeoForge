package forge.neo.quest;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.bazaar.QuestItemType;
import forge.localinstance.properties.ForgeConstants;

/**
 * <b>Las mejoras del bazar, encendidas y apagadas como las mascotas</b>
 * (Discord, 26-10-2026: <i>"an option, just like for the pets, to select and
 * deselect (or reduce in the case of elixir of life), the perks we have
 * purchased ... may want to return to having just the starting life for better
 * challenge, and increase it again if challenge becomes too much"</i>).
 *
 * <p>Forge solo guarda UN nivel por mejora ({@code QuestAssets.getItemLevel}),
 * y es el que usa en partida: la vida es la base mas el nivel del Elixir. Asi
 * que el nivel de Forge pasa a ser el <b>activo</b>, y el <b>comprado</b> se
 * guarda aqui al lado ({@code <quest>.neo-mejoras}, como el Coliseo o la
 * vitrina). Bajarlo y volver a subirlo es gratis hasta lo comprado; el fichero
 * de Forge no se toca mas que con su propio setter.
 *
 * <p>Una mejora bajada <b>no se vende</b> en el bazar: Forge la ofreceria otra
 * vez al precio de su nivel activo, o sea que pagarias por algo que ya tienes.
 * Se sube aqui, y cuando esta entera vuelve a venderse el nivel siguiente.
 *
 * <p>Java puro: lo usa tambien Android por el jar.
 */
public final class QuestUpgradeLevels {

    private QuestUpgradeLevels() {
    }

    /**
     * Las que se pueden bajar. El zepelin no (es un viaje, no una ventaja) ni el
     * carne del banco (abre una pantalla): apagarlos no hace la Quest mas dificil.
     *
     * <p>⚠️ <b>Pound of Flesh tampoco</b> (Discord, 27-10-2026): es la unica que
     * va al reves, te PAGA 750 creditos por cada vida que te quita. Bajarla
     * devolvia la vida y te dejaba los creditos: <i>"upgraded to 29/29, went to
     * max 10 life, got a lot of creds and then later on just changed to 0/29"</i>.
     * Lo que se vende no se apaga.
     */
    public static boolean adjustable(final QuestItemType type) {
        return type != null && type != QuestItemType.ZEPPELIN && type != QuestItemType.MEMBERSHIP_TOKEN
                && type != QuestItemType.POUND_FLESH;
    }

    /**
     * Al cargar una Quest: lo que ya no se puede bajar y estaba bajado vuelve a
     * su nivel comprado. Es Pound of Flesh bajado antes de cerrar el agujero
     * (ver {@link #adjustable}): los creditos se cobraron, la vida se paga.
     *
     * @return si ha cambiado algo (entonces hay que guardar)
     */
    public static synchronized boolean restoreFixed() {
        final QuestController q = quest();
        if (q == null) {
            return false;
        }
        boolean changed = false;
        for (final QuestItemType type : QuestItemType.values()) {
            if (adjustable(type)) {
                continue;
            }
            final int owned = owned(type);
            if (active(type) < owned) {
                q.getAssets().setItemLevel(type, owned);
                changed = true;
            }
        }
        return changed;
    }

    /** El nivel que se usa en partida (el de Forge). */
    public static int active(final QuestItemType type) {
        final QuestController q = quest();
        return q == null || type == null ? 0 : q.getAssets().getItemLevel(type);
    }

    /** El nivel comprado: lo guardado aqui, o el activo si es mayor (lo de antes de esto, o una compra). */
    public static synchronized int owned(final QuestItemType type) {
        final QuestController q = quest();
        if (q == null || type == null) {
            return 0;
        }
        final int saved = parse(load(q.getName()).getProperty(type.name()));
        return Math.max(saved, active(type));
    }

    /** Si esta bajada: entonces no se vende en el bazar. */
    public static boolean parked(final QuestItemType type) {
        return adjustable(type) && active(type) < owned(type);
    }

    /**
     * Pone el nivel activo, entre 0 y lo comprado, y guarda la partida.
     *
     * @return el nivel que ha quedado
     */
    public static synchronized int setActive(final QuestItemType type, final int level) {
        final QuestController q = quest();
        if (q == null || !adjustable(type)) {
            return active(type);
        }
        final int owned = owned(type);
        final int now = Math.max(0, Math.min(owned, level));
        remember(q.getName(), type, owned);
        q.getAssets().setItemLevel(type, now);
        NeoQuest.save();
        return now;
    }

    /** Tras una compra: lo comprado sube con lo activo. */
    public static synchronized void remember(final QuestItemType type) {
        final QuestController q = quest();
        if (q != null && type != null) {
            remember(q.getName(), type, owned(type));
        }
    }

    static synchronized void deleteSaved(final String questName) {
        if (questName == null || questName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(fileOf(questName).toPath());
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se han podido borrar las mejoras: " + e);
        }
    }

    // ------------------------------------------------------------------

    private static QuestController quest() {
        return NeoQuest.isActive() ? NeoQuest.engine() : null;
    }

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".neo-mejoras");
    }

    private static void remember(final String questName, final QuestItemType type, final int owned) {
        if (questName == null || questName.isBlank()) {
            return;
        }
        final Properties p = load(questName);
        if (parse(p.getProperty(type.name())) == owned) {
            return;
        }
        p.setProperty(type.name(), Integer.toString(owned));
        try (Writer w = Files.newBufferedWriter(fileOf(questName).toPath(), StandardCharsets.UTF_8)) {
            p.store(w, "Neo Forge: nivel comprado de cada mejora del bazar (el activo lo guarda Forge)");
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se han podido guardar las mejoras: " + e);
        }
    }

    private static Properties load(final String questName) {
        final Properties p = new Properties();
        if (questName == null || questName.isBlank()) {
            return p;
        }
        final File f = fileOf(questName);
        if (f.isFile()) {
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (final IOException | RuntimeException e) {
                System.err.println("[quest] no se han podido leer las mejoras: " + e);
            }
        }
        return p;
    }

    private static int parse(final String s) {
        try {
            return s == null ? 0 : Math.max(0, Integer.parseInt(s.trim()));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}
