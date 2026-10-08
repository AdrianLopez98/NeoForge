package forge.neo.match;

import java.util.EnumSet;

import forge.deck.Deck;
import forge.deck.DeckgenUtil;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;

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
 * <p>Compartida con Android por el jar: su {@code MatchLauncher} llama a
 * {@link #variants} y a {@link #seat}.
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
     * Los asientos con Planechase, encima de los que ya hubiera.
     *
     * @param base la costura que ya habia (los equipos), o {@code null} para la
     *             del formato
     * @param on   si esta encendido; apagado devuelve {@code base} tal cual
     */
    public static NeoGame.Seating wrap(final forge.neo.match.NeoFormat format,
                                       final NeoGame.Seating base, final boolean on) {
        if (!on || format == null || !appliesTo(format.getGameType())) {
            return base;
        }
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return Planechase.variants(base == null
                        ? EnumSet.of(format.getGameType()) : base.variants());
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return seat(base == null ? format.register(deck, seats) : base.human(deck, seats));
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return seat(base == null ? format.register(deck, seats) : base.opponent(i, deck, seats));
            }
        };
    }
}
