package forge.neo.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.item.PaperCard;
import forge.neo.NeoText;

/**
 * <b>La carta de premio de la Quest, con contexto</b> (Discord, 26-10-2026,
 * Android): al ganar un desafio salia <i>"Choose a Green card"</i> a secas, y
 * otra vez <i>"Choose a duplicate card"</i>: <i>"I wasn't sure why I was
 * choosing one"</i>. Y pedia ver cuantas tienes y ordenarlas por rareza.
 *
 * <p>La pregunta la hace el motor ({@code QuestWinLoseController}: {@code
 * SGuiChoose.one("Choose " + premio.getName(), cartas)}) y llega al dialogo de
 * elegir generico, que no sabe que es un premio. Esto lo sabe porque
 * {@link NeoQuestRewards} marca el reparto con {@link #during}: fuera de ahi
 * no se toca ninguna pregunta.
 *
 * <p>Compartida con Android por el jar: {@code NeoGuiBase} (PC) y {@code
 * AndroidGuiBase} la consultan antes de abrir el dialogo.
 */
public final class QuestRewardPick {

    private QuestRewardPick() {
    }

    /** Mientras se reparten los premios de un duelo de la Quest. */
    private static volatile boolean active;

    /** Cuantas tienes de cada nombre, calculado una vez por pregunta. */
    private static volatile Map<String, Integer> owned;

    /** Lo de {@code reward} con la marca puesta, pase lo que pase. */
    static void during(final Runnable reward) {
        active = true;
        try {
            reward.run();
        } finally {
            active = false;
            owned = null;
        }
    }

    /** Si esta pregunta es la carta de premio: "Choose ..." con cartas, durante el reparto. */
    public static boolean applies(final String message, final Collection<?> options) {
        if (!active || message == null || !message.startsWith("Choose ") || options == null
                || options.isEmpty()) {
            return false;
        }
        for (final Object o : options) {
            if (!(o instanceof PaperCard)) {
                return false;
            }
        }
        owned = countOwned();
        return true;
    }

    /** "Has ganado una carta: elige una". */
    public static String title() {
        return NeoText.get("quest.rewardPick.title");
    }

    /**
     * Que premio es y como esta ordenado. El nombre del premio ("a Green card")
     * lo escribe el motor, en ingles; el duplicado se explica aparte porque
     * "a duplicate card" no dice de que.
     */
    public static String help(final String message) {
        final String prize = message == null ? "" : message.substring(Math.min(7, message.length())).trim();
        if (prize.toLowerCase(Locale.ROOT).contains("duplicate")) {
            return NeoText.get("quest.rewardPick.duplicate");
        }
        return NeoText.get("quest.rewardPick.help", prize);
    }

    /** Las mismas, de mas rara a menos y por nombre. */
    public static <T> List<T> sorted(final Collection<T> options) {
        final List<T> out = new ArrayList<>(options);
        out.sort(Comparator.<T>comparingInt(o -> -DuelFace.rank(((PaperCard) o).getRarity()))
                .thenComparing(o -> ((PaperCard) o).getName()));
        return out;
    }

    /** "Tienes 2" / "Nueva", o null fuera de la pregunta del premio. */
    public static String caption(final PaperCard card) {
        final Map<String, Integer> counts = owned;
        if (!active || counts == null || card == null) {
            return null;
        }
        final int n = counts.getOrDefault(card.getName(), 0);
        return n == 0 ? NeoText.get("quest.rewardPick.new") : NeoText.get("quest.rewardPick.owned", n);
    }

    /** Copias por nombre en tu coleccion de la Quest (todas las ediciones). */
    private static Map<String, Integer> countOwned() {
        final Map<String, Integer> out = new HashMap<>();
        try {
            for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                out.merge(e.getKey().getName(), e.getValue(), Integer::sum);
            }
        } catch (final RuntimeException e) {
            // Sin coleccion no hay recuento: se elige igual.
        }
        return out;
    }
}
