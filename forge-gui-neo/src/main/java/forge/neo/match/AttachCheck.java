package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * <b>A quien va una maldicion, y a quien protege una batalla</b>
 * ({@code run.cmd attachcheck}). Ver {@link AttachedToPlayer}.
 *
 * <p>Discord, 03-10-2026: una maldicion de la IA sobre otra IA se quedaba en
 * la mesa de quien la lanzo y no habia forma de saber a quien encantaba sin
 * mirar el registro; y lo mismo con las batallas. Una mesa con una maldicion
 * de la IA sobre ti, una tuya sobre ella y una batalla de la IA. El protector
 * de una batalla se elige al entrar, y una posicion la pone en la mesa sin
 * entrar, asi que se le pone con el motor parado, como haria el propio motor.
 */
public final class AttachCheck {

    private AttachCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final AtomicReference<String> result = new AtomicReference<>();
        final int[] misses = {0};
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
            ui.setAutoPlayHold(() -> {
                if (result.get() != null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
                    return false;
                }
                final Game game = ui.getGameView().getGame();
                Player human = null;
                Player ai = null;
                for (final Player p : game.getRegisteredPlayers()) {
                    if (p.isAI()) {
                        ai = p;
                    } else {
                        human = p;
                    }
                }
                if (human == null || ai == null) {
                    return false;
                }
                Card battle = null;
                for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
                    if (c.isBattle()) {
                        battle = c;
                    }
                }
                if (battle == null) {
                    // La posicion puede no estar puesta aun en la primera
                    // pregunta: se espera unas cuantas antes de rendirse.
                    if (++misses[0] > 20) {
                        final StringBuilder names = new StringBuilder();
                        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
                            names.append(c.getName()).append("; ");
                        }
                        result.set("sin batalla en la mesa de la IA: " + names);
                    }
                    return false;
                }
                // El motor ya le pone protector al entrar (la IA elige); solo
                // si no lo tuviera se le pone uno, como haria el.
                if (battle.getProtectingPlayer() == null) {
                    battle.setProtectingPlayer(battle.getController() == ai ? human : ai);
                }
                System.out.println("  Batalla: " + battle.getName() + " | la controla "
                        + battle.getController().getName() + " | la protege "
                        + battle.getProtectingPlayer().getName());
                final forge.game.GameView gv = ui.getGameView();
                final forge.game.player.PlayerView me = human.getView();
                final forge.game.player.PlayerView them = ai.getView();
                final List<forge.game.card.CardView> onMe = AttachedToPlayer.enchanting(gv, me);
                final List<forge.game.card.CardView> onThem = AttachedToPlayer.enchanting(gv, them);
                final List<forge.game.card.CardView> myBattles = AttachedToPlayer.protecting(gv, me);
                final List<forge.game.card.CardView> theirBattles = AttachedToPlayer.protecting(gv, them);
                result.set(String.format(Locale.ROOT, "%d|%s|%d|%s|%d|%s|%d|%s",
                        onMe.size(), onMe.isEmpty() ? "" : onMe.get(0).getName(),
                        onThem.size(), onThem.isEmpty() ? "" : onThem.get(0).getName(),
                        myBattles.size(), myBattles.isEmpty() ? "" : myBattles.get(0).getName(),
                        theirBattles.size(),
                        AttachedToPlayer.protector(battle.getView()) == null ? "" : AttachedToPlayer.protector(battle.getView()).getName()));
                return false;
            });
        });

        final String r = result.get();
        System.out.println("  Resultado: " + r);
        final String[] f = r == null ? new String[0] : r.split("\\|", -1);
        check(f.length == 8, "la mesa se monto y se pudo leer", "no se llego a leer la mesa: " + r);
        if (f.length == 8) {
            check("1".equals(f[0]) && f[1].startsWith("Curse of the Pierced Heart"),
                    "la maldicion de la IA sale en TU barra (" + f[1] + ")",
                    "tu barra no tiene la maldicion de la IA: " + f[0] + " " + f[1]);
            check("1".equals(f[2]) && f[3].startsWith("Curse of Death"),
                    "la tuya sale en la barra de la IA (" + f[3] + ")",
                    "la barra de la IA no tiene tu maldicion: " + f[2] + " " + f[3]);
            check("1".equals(f[4]) && f[5].startsWith("Invasion of Zendikar"),
                    "la batalla de la IA sale en TU barra, que la proteges",
                    "tu barra no tiene la batalla que proteges: " + f[4] + " " + f[5]);
            check("0".equals(f[6]) && !f[7].isEmpty(),
                    "y no en la de la IA, que la controla; la carta dice quien la protege (" + f[7] + ")",
                    "la batalla sale donde no toca: " + f[6] + " / protector " + f[7]);
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de maldiciones y batallas han fallado");
        }
    }

    private static TutorialLesson position() {
        final String library = String.join(";", java.util.Collections.nCopies(10, "Forest"));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Curse of Death's Hold|EnchantingPlayer:ai;Forest;Swamp",
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=Curse of the Pierced Heart|EnchantingPlayer:human;Invasion of Zendikar|Set:MOM|Counters:DEFENSE=3;Mountain",
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("attach", state, List.of());
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
