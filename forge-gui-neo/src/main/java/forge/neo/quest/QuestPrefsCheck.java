package forge.neo.quest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import forge.gamemodes.quest.data.QuestPreferences;
import forge.gamemodes.quest.data.QuestPreferences.QPref;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;

/**
 * Las preferencias de la Quest ({@code run.cmd questprefscheck}). Ver {@link NeoQuestPrefs}.
 *
 * <ul>
 *   <li>que no se repita ninguna y que cada rotulo de Forge exista en los once
 *       idiomas (los diez de Forge y nuestro {@code ar-MA.properties}): si falta,
 *       sale la clave {@code lblXxx} en pantalla;</li>
 *   <li>que lo que el juez de Forge no admite se rechace con NUESTRO mensaje y sin
 *       tocar el valor, y que un decimal con coma valga;</li>
 *   <li>que "volver a como venian" las deje todas de fabrica.</li>
 * </ul>
 *
 * <p>Toca {@code quest.preferences} del jugador, asi que lo apunta entero al
 * empezar y lo repone al acabar, pase lo que pase.
 */
public final class QuestPrefsCheck {

    private QuestPrefsCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final QuestPreferences prefs = FModel.getQuestPreferences();
        final Map<QPref, String> before = new EnumMap<>(QPref.class);
        for (final QPref p : QPref.values()) {
            before.put(p, prefs.getPref(p));
        }
        try {
            checkList();
            checkLabels();
            checkValidation();
            checkReset();
        } finally {
            for (final Map.Entry<QPref, String> e : before.entrySet()) {
                prefs.setPref(e.getKey(), e.getValue());
            }
            prefs.save();
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las preferencias han fallado");
        }
    }

    private static void checkList() {
        final List<NeoQuestPrefs.Item> all = NeoQuestPrefs.all();
        final Set<QPref> seen = new HashSet<>();
        boolean unique = true;
        for (final NeoQuestPrefs.Item i : all) {
            unique &= seen.add(i.getPref());
        }
        check(unique && all.size() >= 60,
                all.size() + " preferencias en " + NeoQuestPrefs.groups().size() + " grupos, ninguna repetida",
                "lista rara: " + all.size() + " preferencias, repetidas " + !unique);
        check(!seen.contains(QPref.BOOSTER_COMMONS) && !seen.contains(QPref.SPECIAL_BOOSTERS)
                        && !seen.contains(QPref.WINS_UNLOCK_SET) && !seen.contains(QPref.CURRENT_QUEST),
                "fuera las que aqui no hacen nada (sobre del motor, especiales, desbloquear) y la Quest en curso",
                "se ha colado una que aqui no hace nada");
    }

    /** Las claves {@code lbl*} de cada rotulo, en los once ficheros. */
    private static void checkLabels() {
        // Se mira por clave en los ficheros: el Localizer solo tiene cargado uno.
        final String[] langs = {"en-US", "es-ES", "de-DE", "fr-FR", "it-IT", "pt-BR", "ru-RU", "ja-JP", "ko-KR", "zh-CN"};
        final Set<String> wanted = labelKeys();
        int missing = 0;
        final StringBuilder where = new StringBuilder();
        for (final String lang : langs) {
            missing += missingIn(new File(ForgeConstants.LANG_DIR, lang + ".properties"), wanted, lang, where);
        }
        final java.io.InputStream ar = QuestPrefsCheck.class.getResourceAsStream("/ar-MA.properties");
        missing += missingIn(ar, wanted, "ar-MA", where);
        check(missing == 0, wanted.size() + " rotulos de Forge, en los once idiomas",
                missing + " rotulos sin traducir:" + where);
    }

    private static void checkValidation() {
        final NeoQuestPrefs.Item base = find(QPref.REWARDS_BASE);
        final String was = base.getValue();
        final String nan = NeoQuestPrefs.set(base, "abc");
        final String negative = NeoQuestPrefs.set(base, "-5");
        check(nan != null && negative != null && base.getValue().equals(was),
                "\"abc\" y -5 se rechazan (\"" + negative + "\") y el valor no cambia",
                "se ha aceptado algo que no vale");
        final String ok = NeoQuestPrefs.set(base, "100");
        check(ok == null && "100".equals(base.getValue()) && !base.isDefault(),
                "100 se guarda y la fila queda cambiada", "100 no se ha guardado: " + ok);

        final NeoQuestPrefs.Item bias = find(QPref.STARTING_POOL_COLOR_BIAS);
        final String tooBig = NeoQuestPrefs.set(bias, "101");
        check(tooBig != null && tooBig.contains("100"),
                "el sesgo de color dice su tope: \"" + tooBig + "\"", "el sesgo admite 101: " + tooBig);

        final NeoQuestPrefs.Item wild = find(QPref.WILD_OPPONENTS_NUMBER);
        final String four = NeoQuestPrefs.set(wild, "4");
        check(four != null && four.contains("3"),
                "rivales salvajes, de 0 a 3: \"" + four + "\"", "admite 4 rivales salvajes: " + four);

        final NeoQuestPrefs.Item multi = find(QPref.REWARDS_WINS_MULTIPLIER);
        final String comma = NeoQuestPrefs.set(multi, "0,5");
        check(comma == null && multi.getKind() == NeoQuestPrefs.Kind.DECIMAL
                        && Double.parseDouble(multi.getValue()) == 0.5,
                "un decimal con coma vale (0,5 → " + multi.getValue() + ")", "el decimal con coma no vale: " + comma);

        final NeoQuestPrefs.Item promos = find(QPref.EXCLUDE_PROMOS_FROM_POOL);
        NeoQuestPrefs.setOn(promos, false);
        check(promos.getKind() == NeoQuestPrefs.Kind.SWITCH && "0".equals(promos.getValue()),
                "los de 0/1 son interruptores", "un interruptor no se ha guardado");
    }

    private static void checkReset() {
        final int changed = NeoQuestPrefs.changedCount();
        final int reset = NeoQuestPrefs.resetAll();
        check(changed >= 3 && reset == changed && NeoQuestPrefs.changedCount() == 0,
                "volver a como venian: " + reset + " cambiadas, ahora todas de fabrica",
                "despues de volver a como venian quedan " + NeoQuestPrefs.changedCount() + " cambiadas");
        final NeoQuestPrefs.Item multi = find(QPref.REWARDS_WINS_MULTIPLIER);
        check(multi.isDefault() && "0.3".equals(multi.getValue()),
                "y un decimal vuelve a su valor de Forge (0.3)", "el decimal no volvio: " + multi.getValue());
    }

    private static NeoQuestPrefs.Item find(final QPref pref) {
        for (final NeoQuestPrefs.Item i : NeoQuestPrefs.all()) {
            if (i.getPref() == pref) {
                return i;
            }
        }
        throw new IllegalStateException("no esta " + pref);
    }

    /** Las claves de los rotulos y titulos, de la propia lista: una fila nueva entra sola. */
    private static Set<String> labelKeys() {
        final Set<String> out = new HashSet<>();
        out.add("lblEnteraNumber");
        for (final NeoQuestPrefs.Group g : NeoQuestPrefs.groups()) {
            out.add(g.getTitleKey());
            for (final NeoQuestPrefs.Item i : g.getItems()) {
                out.add(i.getLabelKey());
            }
        }
        return out;
    }

    private static int missingIn(final File f, final Set<String> wanted, final String lang, final StringBuilder where) {
        try (java.io.InputStream in = new FileInputStream(f)) {
            return missingIn(in, wanted, lang, where);
        } catch (final java.io.IOException e) {
            where.append(" ").append(lang).append("(no se lee)");
            return wanted.size();
        }
    }

    private static int missingIn(final java.io.InputStream in, final Set<String> wanted, final String lang,
                                 final StringBuilder where) {
        if (in == null) {
            where.append(" ").append(lang).append("(no esta)");
            return wanted.size();
        }
        final Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (final java.io.IOException e) {
            where.append(" ").append(lang).append("(no se lee)");
            return wanted.size();
        }
        int n = 0;
        for (final String k : wanted) {
            final String v = p.getProperty(k);
            if (v == null || v.isBlank()) {
                n++;
                where.append(" ").append(lang).append(":").append(k);
            }
        }
        return n;
    }

    private static void check(final boolean ok, final String good, final String bad) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK" : "MAL", ok ? good : bad);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
