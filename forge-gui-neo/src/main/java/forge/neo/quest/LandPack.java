package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.item.SealedProduct;
import forge.item.SealedTemplate;
import forge.item.generation.BoosterSlots;
import forge.model.FModel;
import forge.neo.NeoText;

/**
 * Un <b>sobre de tierras basicas</b>: siete de UN tipo (siete Montanyas, siete
 * Islas...), en todas las tiendas de la Quest y en cualquier mundo.
 *
 * <p>Pedido en Discord (11-10-2026), jugando en Shandalar: <i>"I'm finding it
 * difficult to find basic lands ... Cannot find in shop so far, and not
 * dropping in post match rewards. Let alone getting just one of the colour I
 * need (when I actually want to find more like 15)"</i>. Las primeras
 * expansiones (Legends, The Dark, Antiquities...) no traen basicas en sus
 * sobres, asi que en un mundo hecho de ellas no habia forma de montar un mazo
 * monocolor. Decision del autor: cinco sobres, uno por tipo, y <b>no baratos</b>
 * ({@link #PRICE}) — se quiere que se juegue con el mazo inicial.
 *
 * <p>Es un {@link SealedProduct} de verdad para que entre por el mismo camino
 * que las cajas ({@code NeoQuestShop.boxes / priceOfItem / buyBox}) en las dos
 * interfaces. Las siete son de la MISMA impresion, elegida al azar entre las que
 * valen en la Quest (el formato del mundo, si lo tiene): siete artes distintos
 * serian siete filas en la coleccion para una sola tierra.
 *
 * <p>Clase pura: la usa Android por el jar.
 */
public final class LandPack extends SealedProduct {

    /** Los cinco tipos, en el orden de los colores. */
    static final String[] TYPES = {"Plains", "Island", "Swamp", "Mountain", "Forest"};

    /** Cuantas trae. */
    public static final int CARDS = 7;

    /** Lo que cuesta. Caro a proposito (ver arriba). */
    public static final int PRICE = 250;

    private final PaperCard land;

    private LandPack(final PaperCard land) {
        super(land.getName(), new SealedTemplate(land.getEdition(),
                java.util.Collections.singletonList(
                        org.apache.commons.lang3.tuple.Pair.of(BoosterSlots.BASIC_LAND, CARDS))));
        this.land = land;
    }

    /**
     * Los cinco del mostrador, uno por tipo, cada uno con su impresion. Se
     * montan NUEVOS cada vez que se piden: un {@code SealedProduct} memoriza lo
     * que trae, y el siguiente que se compre puede ser de otra edicion.
     */
    public static List<LandPack> onSale() {
        final List<LandPack> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        for (final String type : TYPES) {
            final PaperCard p = pick(type);
            if (p != null) {
                out.add(new LandPack(p));
            }
        }
        return out;
    }

    /**
     * Una impresion de esa basica: de las expansiones de la Quest y de las de
     * siempre (basica, expansion, reimpresion, inicio) si las hay; si no, de las
     * de la Quest; y si el mundo no trae ninguna, cualquiera.
     */
    static PaperCard pick(final String name) {
        final List<PaperCard> all = FModel.getMagicDb().getCommonCards().getAllCards(name);
        if (all == null || all.isEmpty()) {
            return null;
        }
        final Predicate<PaperCard> inQuest = NeoQuest.engine().getCards().applyFormatFilter(c -> true);
        final List<PaperCard> quest = new ArrayList<>();
        final List<PaperCard> plain = new ArrayList<>();
        for (final PaperCard p : all) {
            if (p == null || p.isFoil() || !inQuest.test(p)) {
                continue;
            }
            quest.add(p);
            final CardEdition e = FModel.getMagicDb().getEditions().get(p.getEdition());
            if (e != null && (e.getType() == CardEdition.Type.CORE || e.getType() == CardEdition.Type.EXPANSION
                    || e.getType() == CardEdition.Type.REPRINT || e.getType() == CardEdition.Type.STARTER)) {
                plain.add(p);
            }
        }
        final List<PaperCard> from = !plain.isEmpty() ? plain : !quest.isEmpty() ? quest : all;
        return from.get(new java.util.Random().nextInt(from.size()));
    }

    /** La tierra que trae. */
    public PaperCard getLand() {
        return land;
    }

    /** Como se llama en la tienda: "Tierras basicas: Montanya ×7". */
    public String displayName() {
        return NeoText.get("shop.lands.name", forge.neo.card.CardText.nameOf(land), CARDS);
    }

    /** Su imagen es la de la tierra que trae. */
    @Override
    public String getImageKey(final boolean altState) {
        return land.getImageKey(altState);
    }

    @Override
    public String getItemType() {
        return NeoText.get("shop.lands.type", CARDS);
    }

    @Override
    protected List<PaperCard> generate() {
        final List<PaperCard> out = new ArrayList<>(CARDS);
        for (int i = 0; i < CARDS; i++) {
            out.add(land);
        }
        return out;
    }
}
