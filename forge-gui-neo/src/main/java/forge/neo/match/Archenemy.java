package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;

/**
 * <b>Archenemy</b> en las partidas normales, como {@link Planechase}
 * (Discord, 11-10-2026: el logro de Archenemy de Forge salia en la lista y no
 * habia forma de conseguirlo).
 *
 * <p>El motor lo trae entero, igual que el lobby de Forge
 * ({@code GameLobby.startGame}): la variante ({@code GameType.Archenemy}), los
 * ~100 esquemas, las 40 vidas del archienemigo (60 en Commander: las 40 de la
 * variante mas las 20 de Commander, que es como Forge las apila), que empiece
 * el ({@code GameAction}, regla 904.6) y que ponga en marcha un esquema en cada
 * fase principal suya. Aqui no hay ni una regla: se elige <b>quien</b> es, se
 * le da un mazo de esquemas y se arman los equipos.
 *
 * <p><b>Quien es el archienemigo</b> ({@link Who}), las mismas tres cosas que
 * deja Forge:
 * <ul>
 *   <li><b>Tu</b>, contra todos los rivales en un equipo. Es lo que hace Forge
 *       de fabrica (el primer asiento).</li>
 *   <li><b>Un rival</b>, al azar en cada partida, y tu con los demas rivales
 *       en su contra.</li>
 *   <li><b>Todos</b>: Archenemy Rumble ({@code GameType.ArchenemyRumble}), cada
 *       uno con sus esquemas y 40 vidas, todos contra todos.</li>
 * </ul>
 *
 * <p><b>El mazo de esquemas.</b> El del mazo si trae una seccion de esquemas
 * valida (la del editor de Forge), y si no uno al azar de 20 sin repetir, como
 * {@code DeckgenUtil.generateSchemePool} pero con <b>4 en curso como mucho</b>.
 * Es a proposito: el motor pone en marcha el de arriba del mazo sin mirar si
 * queda alguno ({@code Player.setSchemeInMotion} hace {@code get(0)}), y es lo
 * que tiraba la partida contra el jefe de Ascenso (las trampas conocidas). Los esquemas
 * normales vuelven al fondo del mazo al resolverse; solo los <i>en curso</i>
 * se quedan fuera. Con cuatro como mucho, el mazo no se puede vaciar.
 *
 * <p>Se monta por la costura de siempre ({@link NeoGame.Seating}). Los equipos
 * los decide el archienemigo y por eso <b>sustituye</b> a los de Personalizar
 * (Forge hace lo mismo: con Archenemy no mira el equipo de cada asiento); en
 * Rumble, que es todos contra todos, se dejan los que hubiera. Planechase va
 * por encima: los dos se pueden juntar.
 */
public final class Archenemy {

    private Archenemy() {
    }

    /** Quien es el archienemigo. Se guarda por su id. */
    public enum Who {
        ME("me"), RIVAL("rival"), ALL("all");

        public final String id;

        Who(final String id) {
            this.id = id;
        }

        public static Who byId(final String id) {
            for (final Who w : values()) {
                if (w.id.equals(id)) {
                    return w;
                }
            }
            return ME;
        }
    }

    /**
     * Con cuantos rivales se ofrece en una partida normal: a cuatro, tu y tres
     * (Ana, 12-10-2026). Es uno contra un equipo; a dos o a tres no se ofrece.
     * Lo leen el inicio del escritorio y el panel de rivales de Android.
     */
    public static final int MIN_OPPONENTS = 3;

    /** Cuantos esquemas lleva un mazo al azar (los de Forge). */
    static final int SCHEMES = 20;

    /** Cuantos de ellos pueden ser "en curso" (ver el javadoc de la clase). */
    static final int MAX_ONGOING = 4;

    /** En los mismos formatos que Planechase: los construidos. */
    public static boolean appliesTo(final GameType type) {
        return Planechase.appliesTo(type);
    }

    /** La variante que se anyade: Archenemy, o Rumble si lo son todos. */
    public static GameType variantFor(final Who who) {
        return who == Who.ALL ? GameType.ArchenemyRumble : GameType.Archenemy;
    }

    /** Las variantes de la partida con la de Archenemy anyadida. */
    public static EnumSet<GameType> variants(final EnumSet<GameType> base, final Who who) {
        final EnumSet<GameType> out = base == null || base.isEmpty()
                ? EnumSet.noneOf(GameType.class) : EnumSet.copyOf(base);
        out.add(variantFor(who));
        return out;
    }

    /**
     * El mazo de esquemas de ese mazo: <b>el suyo</b> si trae una seccion
     * valida (lo que mira el lobby de Forge), y si no uno al azar.
     */
    public static List<PaperCard> schemesFor(final Deck deck) {
        final CardPool own = deck == null ? null : deck.get(DeckSection.Schemes);
        if (own != null && !own.isEmpty() && DeckFormat.getSchemeSectionConformanceProblem(own) == null) {
            return own.toFlatList();
        }
        return randomSchemes();
    }

    /** 20 esquemas sin repetir, con {@link #MAX_ONGOING} en curso como mucho. */
    public static List<PaperCard> randomSchemes() {
        final List<PaperCard> all = new ArrayList<>(forge.model.FModel.getArchenemyCards().toFlatList());
        Collections.shuffle(all, forge.util.MyRandom.getRandom());
        final List<PaperCard> out = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        int ongoing = 0;
        for (final PaperCard c : all) {
            if (out.size() >= SCHEMES) {
                break;
            }
            if (c == null || c.getRules() == null || !c.getRules().getType().isScheme()
                    || !names.add(c.getName())) {
                continue;
            }
            if (isOngoing(c)) {
                if (ongoing >= MAX_ONGOING) {
                    continue;
                }
                ongoing++;
            }
            out.add(c);
        }
        return out;
    }

    static boolean isOngoing(final PaperCard c) {
        return c.getRules().getType().hasSupertype(forge.card.CardType.Supertype.Ongoing);
    }

    /**
     * Los asientos con Archenemy, encima de los que ya hubiera.
     *
     * @param base la costura que ya habia (los equipos), o {@code null} para la
     *             del formato
     * @param on   si esta encendido; apagado devuelve {@code base} tal cual
     * @param who  quien es el archienemigo
     */
    public static NeoGame.Seating wrap(final NeoFormat format, final NeoGame.Seating base,
                                       final boolean on, final Who who) {
        if (!on || format == null || !appliesTo(format.getGameType())) {
            return base;
        }
        final Who w = who == null ? Who.ME : who;
        // El rival que lo es, sorteado al sentar al humano (que va el primero).
        final int[] villain = {-1};
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return Archenemy.variants(base == null
                        ? EnumSet.of(format.getGameType()) : base.variants(), w);
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                villain[0] = pickVillain(seats);
                final RegisteredPlayer rp = base == null ? format.register(deck, seats) : base.human(deck, seats);
                return seat(rp, deck, seats, variants(), w, isArchenemy(w, 0, villain[0]));
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                final RegisteredPlayer rp = base == null
                        ? format.register(deck, seats) : base.opponent(i, deck, seats);
                return seat(rp, deck, seats, variants(), w, isArchenemy(w, i + 1, villain[0]));
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String aiProfile) {
                return base == null ? null : base.player(i, aiProfile);
            }
        };
    }

    /**
     * El asiento (0 el tuyo, 1.. los rivales) del archienemigo con
     * {@link Who#RIVAL}: uno de los rivales al azar, sorteado una vez por
     * partida.
     */
    public static int pickVillain(final int seats) {
        return 1 + forge.util.MyRandom.getRandom().nextInt(Math.max(1, seats - 1));
    }

    /** Si el asiento {@code seat} (0 el tuyo) es archienemigo. */
    public static boolean isArchenemy(final Who who, final int seat, final int villainSeat) {
        switch (who == null ? Who.ME : who) {
            case ALL:
                return true;
            case RIVAL:
                return seat == villainSeat;
            default:
                return seat == 0;
        }
    }

    /**
     * Monta un asiento con Archenemy. Compartido con Android, que sienta a
     * mano (su {@code MatchLauncher}).
     *
     * <p>El archienemigo se vuelve a montar con {@code forVariants}: es la
     * UNICA puerta de los esquemas (no hay setter) y la que pone sus vidas.
     * Lo demas del asiento de antes (los planos los pone Planechase despues)
     * no hace falta copiarlo.
     *
     * @param variants las variantes de la partida, con la de Archenemy
     */
    public static RegisteredPlayer seat(final RegisteredPlayer rp, final Deck deck, final int seats,
                                        final EnumSet<GameType> variants, final Who who,
                                        final boolean isArchenemy) {
        RegisteredPlayer out = rp;
        if (isArchenemy) {
            out = RegisteredPlayer.forVariants(seats, variants, deck, schemesFor(deck), true, null, null);
        }
        if (who != Who.ALL) {
            // Uno contra todos: el archienemigo solo, el resto juntos.
            out.setTeamNumber(isArchenemy ? 0 : 1);
        } else if (out != rp) {
            out.setTeamNumber(rp.getTeamNumber());
        }
        return out;
    }
}
