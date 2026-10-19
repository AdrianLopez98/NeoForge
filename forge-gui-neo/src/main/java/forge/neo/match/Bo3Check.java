package forge.neo.match;

import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.model.FModel;

/**
 * <b>Al mejor de 3 fuera del draft</b> (Discord, 19-10-2026: <i>"there is no bo3
 * option, correct?"</i>): el boton PARTIDO de la pantalla de mazos pide
 * {@code NeoSettings.bestOf} partidas a {@link NeoGame#play}. Esto juega un
 * partido de Estandar con banquillo y uno de Commander, enteros y sin ventana
 * ({@code -Dneo.bo3.auto}: el piloto automatico pide la siguiente partida en
 * vez de cerrar el partido), y comprueba que se juega mas de una partida y que
 * hay ganador del partido. Y que con 1 se sigue jugando una sola.
 */
public final class Bo3Check {

    private Bo3Check() {
    }

    public static void run() {
        int bad = 0;
        System.setProperty("neo.bo3.auto", "true");
        try {
            final Deck std = standardDeck();
            if (std == null) {
                bad += check(false, "hay un preconstruido de Estandar con banquillo");
            } else {
                System.out.println("  Estandar con " + std.getName() + " (banquillo de "
                        + std.get(DeckSection.Sideboard).countAll() + ")");
                final NeoGame.Result bo3 = play(std, NeoFormat.ESTANDAR, 3);
                bad += check(bo3 != null && bo3.matchWinner != null && bo3.games >= 2,
                        "Estandar al mejor de 3: " + describe(bo3));
                final NeoGame.Result bo1 = play(std, NeoFormat.ESTANDAR, 1);
                bad += check(bo1 != null && bo1.games == 1,
                        "Estandar a una partida: " + describe(bo1));
            }

            final Deck cmd = FModel.getDecks().getCommanderPrecons().iterator().next();
            System.out.println("  Commander con " + cmd.getName());
            final NeoGame.Result cbo3 = play(cmd, NeoFormat.COMMANDER, 3);
            bad += check(cbo3 != null && cbo3.matchWinner != null && cbo3.games >= 2,
                    "Commander al mejor de 3 (sin banquillo): " + describe(cbo3));
        } finally {
            System.clearProperty("neo.bo3.auto");
        }

        System.out.printf(Locale.ROOT, "%n  %d mal%n", bad);
        if (bad > 0) {
            throw new IllegalStateException(bad + " comprobacion(es) del al mejor de 3 han fallado");
        }
    }

    /** Un preconstruido de Estandar que traiga banquillo, para que el motor lo pida. */
    private static Deck standardDeck() {
        for (final forge.item.PreconDeck p : forge.gamemodes.quest.QuestController.getPrecons()) {
            final Deck d = p.getDeck();
            if (d != null && d.has(DeckSection.Sideboard) && d.get(DeckSection.Sideboard).countAll() >= 5
                    && d.getMain().countAll() >= 60) {
                return d;
            }
        }
        return null;
    }

    private static NeoGame.Result play(final Deck deck, final NeoFormat format, final int games) {
        try {
            return NeoGame.play(deck, 1, NeoMatchUI.Mode.AUTO_PLAY, 600, false, null, null, true,
                    format, List.of(deck), games);
        } catch (final Exception e) {
            System.out.println("  la partida ha reventado: " + e);
            e.printStackTrace();
            return null;
        }
    }

    private static String describe(final NeoGame.Result r) {
        return r == null ? "REVENTADA"
                : r.games + " partida(s), gana el partido " + r.matchWinner;
    }

    private static int check(final boolean ok, final String what) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        return ok ? 0 : 1;
    }
}
