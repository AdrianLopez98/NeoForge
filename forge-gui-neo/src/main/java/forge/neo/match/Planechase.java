package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.deck.Deck;
import forge.deck.DeckgenUtil;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;

/**
 * <b>Planechase</b> en las partidas normales (Discord, 08-10-2026: <i>"I'd love
 * to see an option to add planes chasing into the game itself ... I'd rather
 * torture bots with it than try and convince others"</i>).
 *
 * <p>El motor lo trae entero: la variante ({@code GameType.Planechase}), los
 * ~170 planos y fenomenos, el plano activo en la zona de mando, el dado
 * planar como efecto de cada jugador con su accion especial "tira el dado
 * planar" ({@code Player.createPlanechaseEffects}) y los mazos planares al azar
 * ({@code DeckgenUtil.generatePlanarPool}). Lo unico que hacia falta era
 * <b>encenderlo</b>: la variante en las reglas y un mazo planar en cada
 * asiento. Aqui no hay ni una regla.
 *
 * <p>Se monta por la costura de siempre ({@link NeoGame.Seating}) y
 * <b>encima</b> de la que ya hubiera (los equipos): envuelve, no sustituye.
 * Cada jugador lleva su propio mazo planar: el del mazo si trae uno valido
 * (como en el Forge de siempre) y si no, uno al azar.
 *
 * <p>Y con {@link Shared}, <b>un solo mazo para todos</b>: la opcion del mazo
 * planar unico de las reglas (901.15), que es como lo juega mucha gente.
 *
 * <p>Compartida con Android por el jar: su {@code MatchLauncher} llama a
 * {@link #variants} y a {@link #seat} (o a {@link Shared#seat}).
 */
public final class Planechase {

    private Planechase() {
    }

    /** Si tiene sentido en este formato: los construidos, no el puzzle ni el limitado. */
    public static boolean appliesTo(final GameType type) {
        return type != null && type != GameType.Puzzle && type != GameType.Draft
                && type != GameType.Sealed && type != GameType.Quest
                && type != GameType.Planechase && type != GameType.Archenemy;
    }

    /** Las variantes de la partida con Planechase anyadido. */
    public static EnumSet<GameType> variants(final EnumSet<GameType> base) {
        final EnumSet<GameType> out = base == null || base.isEmpty()
                ? EnumSet.noneOf(GameType.class) : EnumSet.copyOf(base);
        out.add(GameType.Planechase);
        return out;
    }

    /**
     * Le da al asiento su mazo planar: <b>el suyo</b> si el mazo trae una
     * seccion de planos valida (la que se monta en el editor de Forge: diez o
     * mas, sin repetir y con dos fenomenos como mucho), y si no uno al azar.
     * Es lo que hace el lobby de Forge ({@code GameLobby}), que lee
     * {@code DeckSection.Planes} y la comprueba con la misma regla.
     */
    public static RegisteredPlayer seat(final RegisteredPlayer rp) {
        if (rp != null) {
            final forge.deck.CardPool own = rp.getDeck() == null ? null
                    : rp.getDeck().get(forge.deck.DeckSection.Planes);
            rp.setPlanes(own != null && forge.deck.DeckFormat.getPlaneSectionConformanceProblem(own) == null
                    ? own.toFlatList() : DeckgenUtil.generatePlanarPool().toFlatList());
        }
        return rp;
    }

    /**
     * <b>UN SOLO MAZO PLANAR PARA TODOS</b> (Discord, 09-10-2026: <i>"The one I
     * played was one deck that everyone rolled on ... When it's the one deck it
     * feels like an actual planes chasing adventure everyone is on"</i>).
     *
     * <p>El motor solo sabe de un mazo planar <b>por jugador</b>: quien se
     * desplaza saca el plano de arriba del suyo ({@code Player.planeswalk}), y
     * un pozo comun de verdad seria tocarlo. Asi que se imita, que en lo que se
     * nota es lo mismo: se monta <b>un</b> mazo, sin repetir ninguna carta
     * (singleton, como pide la regla 901.15a), y se <b>reparte</b> entre los
     * asientos. Ningun plano sale dos veces, y todos vienen del mismo mazo.
     *
     * <p>El tamanyo es el de las reglas: al menos 40, o diez por jugador si son
     * mas de cuatro. Si tu mazo trae una seccion de planos valida y del tamanyo
     * que toca, se usa esa; si no, uno al azar con un fenomeno de cada diez como
     * mucho (el generador de Forge pone dos por mazo).
     *
     * <p>Uno por partida: se crea al sentar al primero.
     */
    public static final class Shared {
        private final List<List<PaperCard>> piles = new ArrayList<>();

        /**
         * @param seats  cuantos asientos hay
         * @param source el mazo del humano (sus planos, si los trae), o null
         */
        public Shared(final int seats, final Deck source) {
            final int n = Math.max(1, seats);
            final List<PaperCard> deck = communal(n, source);
            for (int i = 0; i < n; i++) {
                piles.add(new ArrayList<>());
            }
            for (int i = 0; i < deck.size(); i++) {
                piles.get(i % n).add(deck.get(i));
            }
        }

        /** Le da al asiento {@code index} (0 el primero) su parte del mazo comun. */
        public RegisteredPlayer seat(final RegisteredPlayer rp, final int index) {
            if (rp != null && !piles.isEmpty()) {
                final List<PaperCard> pile = piles.get(Math.floorMod(index, piles.size()));
                rp.setPlanes(pile.isEmpty() ? DeckgenUtil.generatePlanarPool().toFlatList() : pile);
            }
            return rp;
        }

        /** Cuantas cartas tiene el mazo comun (para las pruebas). */
        public int size() {
            int total = 0;
            for (final List<PaperCard> p : piles) {
                total += p.size();
            }
            return total;
        }

        /** Las partes de cada asiento (para las pruebas). */
        public List<List<PaperCard>> piles() {
            return Collections.unmodifiableList(piles);
        }
    }

    /** Lo menos que tiene que tener el mazo comun (regla 901.15a). */
    static int communalSize(final int seats) {
        return Math.max(40, 10 * seats);
    }

    private static List<PaperCard> communal(final int seats, final Deck source) {
        final int want = communalSize(seats);
        final forge.deck.CardPool own = source == null ? null : source.get(forge.deck.DeckSection.Planes);
        if (own != null && forge.deck.DeckFormat.getPlaneSectionConformanceProblem(own) == null
                && own.countAll() >= Math.min(40, 10 * seats)) {
            final List<PaperCard> out = new ArrayList<>(own.toFlatList());
            Collections.shuffle(out, forge.util.MyRandom.getRandom());
            return out;
        }
        final List<PaperCard> all = new ArrayList<>(forge.model.FModel.getPlanechaseCards().toFlatList());
        Collections.shuffle(all, forge.util.MyRandom.getRandom());
        final List<PaperCard> out = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        final int maxPhenomena = Math.max(1, want / 10);
        int phenomena = 0;
        for (final PaperCard c : all) {
            if (out.size() >= want) {
                break;
            }
            if (c == null || c.getRules() == null || !names.add(c.getName())) {
                continue;
            }
            final forge.card.CardType type = c.getRules().getType();
            if (type.isPhenomenon()) {
                if (phenomena >= maxPhenomena) {
                    continue;
                }
                phenomena++;
            } else if (!type.isPlane()) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /**
     * Los asientos con Planechase, encima de los que ya hubiera.
     *
     * @param base la costura que ya habia (los equipos), o {@code null} para la
     *             del formato
     * @param on   si esta encendido; apagado devuelve {@code base} tal cual
     */
    public static NeoGame.Seating wrap(final forge.neo.match.NeoFormat format,
                                       final NeoGame.Seating base, final boolean on) {
        return wrap(format, base, on, false);
    }

    /** Lo mismo, y con {@code shared} un solo mazo para todos ({@link Shared}). */
    public static NeoGame.Seating wrap(final forge.neo.match.NeoFormat format,
                                       final NeoGame.Seating base, final boolean on, final boolean shared) {
        if (!on || format == null || !appliesTo(format.getGameType())) {
            return base;
        }
        // Uno por partida, montado al sentar al humano (que va el primero).
        final Shared[] deal = new Shared[1];
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return Planechase.variants(base == null
                        ? EnumSet.of(format.getGameType()) : base.variants());
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                final RegisteredPlayer rp = base == null ? format.register(deck, seats) : base.human(deck, seats);
                if (!shared) {
                    return seat(rp);
                }
                deal[0] = new Shared(seats, deck);
                return deal[0].seat(rp, 0);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                final RegisteredPlayer rp = base == null ? format.register(deck, seats) : base.opponent(i, deck, seats);
                if (!shared) {
                    return seat(rp);
                }
                if (deal[0] == null) {
                    deal[0] = new Shared(seats, null);
                }
                return deal[0].seat(rp, i + 1);
            }
        };
    }
}
