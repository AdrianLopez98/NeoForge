package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.function.Predicate;

import forge.item.PaperCard;
import forge.neo.card.CardNode;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;

/**
 * Lo que te llevas de un duelo de la aventura.
 *
 * <p>Hasta ahora esto se aplicaba y <b>no se veia</b>: ganabas, el motor
 * repartia creditos, cartas, rachas y logros, y volvias al cuartel general con
 * unos numeros distintos y sin saber por que. Media recompensa de un modo de
 * coleccion es <i>ver</i> lo que has ganado.
 *
 * <p>Sale tambien al perder, y a proposito. Perder cuesta creditos y el motor
 * lo dice ("You lose! You have lost 15 credits"); enterarse de eso es parte de
 * jugar una aventura, y esconderlo solo hace que la cuenta de creditos parezca
 * que se mueve sola.
 *
 * <p>La forma es la misma que al abrir un sobre ({@link CardHaul}) porque para
 * el jugador es el mismo momento: aqui estan tus cartas nuevas.
 */
public class QuestRewardScreen extends StackPane {

    public QuestRewardScreen(final boolean won, final String opponent,
                             final List<String> notes, final List<PaperCard> cards,
                             final Predicate<PaperCard> isNew,
                             final double cardWidth, final Runnable onDone) {
        getStyleClass().addAll("table-root", "quest");
        setAlignment(Pos.CENTER);

        final String title = NeoText.get(won ? "duel.won" : "duel.lost");
        final String subtitle = subtitle(won, opponent, cards);

        this.title = title;
        this.subtitle = subtitle;
        this.notes = notes;
        this.cards = cards;
        this.isNew = isNew;
        this.cardWidth = cardWidth;
        this.onDone = onDone;

        CardZoom.install(this);
    }

    private final String title;
    private final String subtitle;
    private final List<String> notes;
    private final List<PaperCard> cards;
    /**
     * Cuales no tenias antes.
     *
     * <p>Aqui iba un {@code null} y por eso <b>no salia ni una pastilla de
     * NUEVA</b> — ni siquiera en un sobre de premio entero de cartas que no
     * tenias, con la linea del motor diciendo "15 cartas, 15 nuevas" justo
     * encima. Quien lo sabe es {@code NeoQuestRewards}, que hace la foto de la
     * coleccion antes de repartir.
     */
    private final Predicate<PaperCard> isNew;
    private final double cardWidth;
    private final Runnable onDone;
    private boolean built;

    /**
     * El panel se monta en el primer layout, no en el constructor.
     *
     * <p>Las cartas se dimensionan contra el hueco de verdad, y en el
     * constructor todavia no hay hueco: ancho y alto valen cero. Escuchar solo
     * a {@code widthProperty} tampoco vale — JavaFX asigna ancho y alto por
     * separado, asi que el aviso del ancho puede llegar con el alto aun a cero
     * y no vuelve a haber otro. Aqui los dos estan puestos ya.
     */
    @Override
    protected void layoutChildren() {
        if (!built && getWidth() > 0 && getHeight() > 0) {
            built = true;
            getChildren().add(CardHaul.panel(title, subtitle, cards, isNew, notes,
                    cardWidth, getWidth(), getHeight(), onDone));
        }
        super.layoutChildren();
    }

    private static String subtitle(final boolean won, final String opponent,
                                   final List<PaperCard> cards) {
        final StringBuilder sb = new StringBuilder();
        if (opponent != null && !opponent.isBlank()) {
            sb.append(NeoText.get("duel.against", opponent));
        }
        if (cards != null && !cards.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(NeoText.get(cards.size() == 1 ? "duel.card" : "duel.cards",
                    cards.size()));
        }
        return sb.toString();
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
