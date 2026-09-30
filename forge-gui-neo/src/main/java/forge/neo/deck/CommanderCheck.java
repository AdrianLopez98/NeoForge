package forge.neo.deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Elegir comandante (Forge #12052) sin ventana: {@code run.cmd commandercheck}.
 *
 * <p>Sobre los preconstruidos de Commander de Forge: que se ofrezca donde
 * debe, que la copia lleve al elegido y siga siendo legal con las mismas
 * cartas, que el mazo guardado no cambie, que se recuerde y se olvide bien,
 * que las parejas salgan, y que lo que recibe el motor (el asiento) lleve al
 * elegido en la zona de mando. Y cuanto tarda: las opciones se calculan
 * mirando cada carta del mazo.
 */
public final class CommanderCheck {

    private CommanderCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final DeckFormat df = DeckFormat.Commander;

        check(CommanderChoice.applies(GameType.Commander) && CommanderChoice.applies(GameType.Brawl)
                        && CommanderChoice.applies(GameType.TinyLeaders),
                "se ofrece en Commander, Brawl y Tiny Leaders",
                "no se ofrece en algun formato con comandante");
        check(!CommanderChoice.applies(GameType.Oathbreaker) && !CommanderChoice.applies(GameType.Constructed),
                "y no en Oathbreaker (hechizo insignia) ni en Construido",
                "se ofrece donde no debe");

        final List<Deck> precons = new ArrayList<>();
        for (final Deck d : FModel.getDecks().getCommanderPrecons()) {
            if (d != null && !d.getCommanders().isEmpty()) {
                precons.add(d);
            }
        }
        check(precons.size() > 50, precons.size() + " preconstruidos de Commander para probar",
                "casi no hay preconstruidos: " + precons.size());

        // Cuanto tarda, y cuantos tienen algo que elegir.
        long worst = 0;
        long total = 0;
        int withChoices = 0;
        Deck single = null;
        CommanderChoice.Option other = null;
        Deck pair = null;
        final int sample = Math.min(40, precons.size());
        for (int i = 0; i < sample; i++) {
            final Deck d = precons.get(i);
            final long t0 = System.nanoTime();
            final List<CommanderChoice.Option> options = CommanderChoice.options(d, df);
            final boolean has = CommanderChoice.hasChoices(d, options, df);
            final long dt = (System.nanoTime() - t0) / 1_000_000;
            worst = Math.max(worst, dt);
            total += dt;
            if (has) {
                withChoices++;
            }
            if (!options.isEmpty() && !options.get(0).isDefault()) {
                check(false, "", d.getName() + ": la primera opcion no es la de siempre");
            }
            if (single == null && d.getCommanders().size() == 1) {
                for (final CommanderChoice.Option o : options) {
                    if (!o.isDefault() && o.commanders.size() == 1) {
                        single = d;
                        other = o;
                        break;
                    }
                }
            }
            if (pair == null && d.getCommanders().size() == 2) {
                pair = d;
            }
        }
        System.out.printf(Locale.ROOT, "  %d mazos: %d con algo que elegir; calcularlo, media %d ms, peor %d ms%n",
                sample, withChoices, total / Math.max(1, sample), worst);
        check(worst < 5000, "calcularlo cabe en otro hilo sin que se note (peor " + worst + " ms)",
                "tarda demasiado: " + worst + " ms en un mazo");

        if (single == null) {
            check(false, "", "ningun preconstruido con otro comandante posible: no se ha podido probar");
        } else {
            final List<PaperCard> before = new ArrayList<>(single.getCommanders());
            final int size = single.getMain().countAll() + single.getCommanders().size();
            final Deck led = CommanderChoice.apply(single, other.commanders);
            System.out.println("  " + single.getName() + ": " + names(before) + " -> " + names(led.getCommanders()));
            check(CommanderChoice.same(led.getCommanders(), other.commanders),
                    "la copia lleva al elegido en la zona de mando", "la copia no lleva al elegido");
            check(led.getMain().countAll() + led.getCommanders().size() == size,
                    "con las mismas cartas (" + size + ")", "ha cambiado el numero de cartas");
            check(led.getMain().contains(before.get(0)),
                    "y el de siempre pasa al mazo", "el de siempre ha desaparecido del mazo");
            check(df.getDeckConformanceProblem(led) == null,
                    "y el mazo sigue siendo legal", "el mazo deja de ser legal: " + df.getDeckConformanceProblem(led));
            check(CommanderChoice.same(single.getCommanders(), before),
                    "el mazo guardado no cambia", "SE HA CAMBIADO EL MAZO GUARDADO");

            CommanderChoice.remember(single, df, other.commanders);
            check(CommanderChoice.same(CommanderChoice.remembered(single, df), other.commanders),
                    "se recuerda lo elegido para ese mazo", "no se recuerda lo elegido");
            CommanderChoice.remember(single, df, single.getCommanders());
            check(CommanderChoice.remembered(single, df) == null,
                    "y volver al de siempre lo olvida", "volver al de siempre no lo olvida");
            check(CommanderChoice.apply(single, null) == single
                            && CommanderChoice.apply(single, single.getCommanders()) == single,
                    "sin eleccion se juega el mazo tal cual (el mismo objeto)",
                    "sin eleccion se hace una copia");

            // Lo que recibe el motor: el asiento que monta NeoGame.
            final RegisteredPlayer rp = RegisteredPlayer.forVariants(4, EnumSet.of(GameType.Commander),
                    led, null, false, null, null);
            check(CommanderChoice.same(rp.getCommanders(), other.commanders),
                    "el asiento de la partida sale con el elegido en la zona de mando",
                    "el asiento sale con otro comandante: " + names(rp.getCommanders()));
        }

        if (pair == null) {
            System.out.println("  (ninguno de la muestra lleva pareja: no se prueban las parejas)");
        } else {
            final List<CommanderChoice.Option> options = CommanderChoice.options(pair, df);
            int halves = 0;
            for (final CommanderChoice.Option o : options) {
                if (!o.isDefault() && o.commanders.size() == 1 && pair.getCommanders().contains(o.commanders.get(0))) {
                    halves++;
                }
            }
            check(halves == 2, pair.getName() + ": cada mitad de la pareja se ofrece sola ("
                    + names(pair.getCommanders()) + ")", "las mitades no se ofrecen: " + halves);
            final List<PaperCard> partners = CommanderChoice.partners(pair,
                    pair.getCommanders().get(0), df);
            check(partners.contains(pair.getCommanders().get(1)),
                    "y al elegir una, su pareja de siempre esta entre las parejas posibles",
                    "su pareja de siempre no sale: " + names(partners));
            check(CommanderChoice.allowsNoPartner(pair, options.get(0), df),
                    "la opcion por defecto admite 'sin pareja'", "la opcion por defecto no admite 'sin pareja'");
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de elegir comandante han fallado");
        }
    }

    private static String names(final List<PaperCard> cards) {
        final List<String> out = new ArrayList<>();
        for (final PaperCard c : cards == null ? Collections.<PaperCard>emptyList() : cards) {
            out.add(c.getName());
        }
        return String.join(" + ", out);
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [ok]   " + good);
        } else {
            failed++;
            System.out.println("  [MAL]  " + bad);
        }
    }
}
