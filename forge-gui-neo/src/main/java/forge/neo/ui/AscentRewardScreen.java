package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRewards;
import forge.neo.ascent.AscentRun;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

/**
 * Lo que te llevas de un nodo, y lo que hay que <b>elegir</b>.
 *
 * <h2>Elegir 1 de 3 es el modo entero</h2>
 *
 * <p>El mazo de salida es flojo a proposito ({@code AscentSeedDeck}) para que
 * esta pantalla importe: es lo unico que hace que el mazo del final de la run no
 * se parezca al del principio. Por eso la carta se ve <b>grande</b> y no en una
 * lista — la decision se toma mirando la carta, no leyendo su nombre.
 *
 * <p><b>En Commander es 2 de 6</b>, que es la <i>misma</i> proporcion hecha dos
 * veces ({@code AscentRun.cardBatch}): alli el mazo es de 60 cartas y una sola
 * no se nota. Se eligen de una en una, y la segunda se decide viendo lo que ya
 * te llevaste — la elegida <b>desaparece</b> de la mesa en vez de quedarse
 * apagada.
 *
 * <h2>Y se puede no coger nada</h2>
 *
 * <p>Hay boton de <b>saltar</b>, y no es un descuido: en un mazo de treinta
 * cartas, meter una mala es peor que no meter ninguna. Que quitar valga tanto
 * como anyadir es de lo que vive el modo (por eso el descanso ofrece borrar una
 * carta), asi que negarse tiene que ser una opcion de verdad.
 *
 * <h2>O tierras, en vez de la carta</h2>
 *
 * <p>Debajo de las tres hay una fila de <b>basicas</b>. Es la unica forma de
 * arreglar la base de mana dentro de una run: el premio ofrece siempre
 * hechizos, asi que sin esto el mazo crecia de 30 a 40 cartas con las mismas
 * doce tierras. Ver {@code AscentRewards.takeLands}.
 *
 * <h2>Las reliquias del jefe</h2>
 *
 * <p>Un jefe ofrece <b>tres a elegir</b>. Es el momento en el que se decide si
 * el acto siguiente se aguanta, y darla al azar convertiria lo mas importante de
 * la run en una tirada. Los demas nodos dan una y se lleva sola: ahi no hay
 * decision que tomar.
 */
public class AscentRewardScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /** Terminado: al mapa. */
        void done();
    }

    private final AscentRun run;
    private final AscentRewards.Reward reward;
    private final Actions actions;
    private final double cardWidth;

    private final VBox body = new VBox(16);
    /**
     * Las que siguen sobre la mesa. Se saca la elegida en vez de dejarla
     * apagada: con dos elecciones seguidas, volver a ver la que ya te llevaste
     * invita a clicarla otra vez.
     */
    private final List<PaperCard> onOffer = new ArrayList<>();
    /** Cuantas CARTAS te has llevado ya. Las tierras no cuentan aqui. */
    private int cardsTaken;
    /** Si ya te has llevado la tierra del nodo. Solo hay una. */
    private boolean landTaken;
    /** Y si has dicho que no quieres mas. */
    private boolean cardsDone;
    private boolean relicTaken;

    public AscentRewardScreen(final AscentRun run, final AscentRewards.Reward reward,
                              final double cardWidth, final Actions actions) {
        this.run = run;
        this.reward = reward;
        this.actions = actions;
        this.cardWidth = cardWidth;
        this.onOffer.addAll(reward.cards);
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 77L, Color.web("#E8D7B0"));
        StackPane.setMargin(paper, new Insets(10));

        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(24, 24, Parchment.SAFE_EDGE, 24));
        rebuild();

        getChildren().addAll(paper, body);
        // Click derecho = la carta grande, como en el resto del juego. Aqui
        // hace mas falta que en ninguna parte: se esta eligiendo 1 de 3 y la
        // decision se toma LEYENDO la carta.
        CardZoom.install(this);

        // Y que quepa, pase lo que pase. Ver applyFit().
        body.getTransforms().add(fit);
        body.layoutBoundsProperty().addListener((o, a, b) -> applyFit());
        heightProperty().addListener((o, a, b) -> applyFit());
        widthProperty().addListener((o, a, b) -> applyFit());
    }

    /** Lo que encoge la pantalla entera cuando no cabe. Ver {@link #applyFit()}. */
    private final Scale fit = new Scale(1, 1);

    /**
     * Encoge la pantalla entera hasta que cabe. <b>Sin recortar nada.</b>
     *
     * <h2>Por que hace falta</h2>
     *
     * <p>El tamanyo de carta lo decide {@code NeoApp} a partir del <b>ancho</b>
     * de la ventana, que es lo correcto para la mesa — pero esta pantalla apila
     * en vertical titulo, creditos, tres cartas grandes, la fila de tierras y el
     * boton de no coger ninguna, y eso lo que consume es <b>alto</b>. En una
     * ventana ancha y baja (o con la escala de interfaz subida) el resultado era
     * el titulo cortado por arriba y el boton cortado por abajo: la pantalla mas
     * importante de la run, sin salida visible. Es el principio 5 — si algo se
     * recorta, tiene que haber forma de llegar a lo recortado — y aqui la forma
     * de arreglarlo no es un {@code ScrollPane} (nadie va a buscar con la rueda
     * un boton que deberia estar delante) sino <b>que entre todo, mas pequenyo</b>.
     *
     * <h2>Como</h2>
     *
     * <p>Un {@code Scale} sobre el contenido, con el pivote en su centro. Un
     * {@code Scale} <b>no</b> toca {@code layoutBounds} — solo
     * {@code boundsInParent} — asi que medir, escalar y volver a medir no se
     * realimenta: es lo que permite hacerlo desde el propio listener sin entrar
     * en bucle.
     */
    private void applyFit() {
        final double h = getHeight();
        final double w = getWidth();
        final double bh = body.getLayoutBounds().getHeight();
        final double bw = body.getLayoutBounds().getWidth();
        if (h <= 0 || bh <= 0) {
            return;
        }
        // El margen del pergamino (10 por lado) mas un respiro: el borde del
        // papel esta roto y muerde hacia dentro (Parchment.SAFE_EDGE).
        double s = Math.min(1.0, (h - 24) / bh);
        if (w > 0 && bw > 0) {
            s = Math.min(s, (w - 24) / bw);
        }
        fit.setPivotX(bw / 2);
        fit.setPivotY(bh / 2);
        fit.setX(s);
        fit.setY(s);
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        body.getChildren().clear();

        final Label title = new Label(NeoText.get("ascent.reward.title"));
        title.getStyleClass().add("ascent-act");
        body.getChildren().add(title);

        if (reward.credits > 0) {
            final Label credits = new Label(
                    NeoText.get("ascent.reward.credits", reward.credits));
            credits.getStyleClass().addAll("ascent-pill-base", "ascent-pill");
            body.getChildren().add(credits);
        }

        // ---- la reliquia, primero: es lo que mas pesa ----
        //
        // ⚠️ Y SOLA: mientras hay reliquia pendiente, las tres cartas no se
        // pintan. No es una preferencia — el premio de un jefe son tres
        // reliquias con su texto MAS tres cartas MAS la fila de tierras, y todo
        // junto no cabe: se comio el titulo por arriba y el boton de "no coger
        // ninguna" por abajo, o sea que la pantalla mas importante de la run se
        // quedaba sin salida visible (principio 5). Partido en dos pasos cabe
        // siempre, y encima se lee mejor: primero la reliquia, que es la
        // decision gorda, y despues la carta.
        if (!reward.relics.isEmpty() && !relicTaken) {
            final Label what = new Label(NeoText.get(reward.chooseOne
                    ? "ascent.reward.pickRelic" : "ascent.reward.gotRelic"));
            what.getStyleClass().add("ascent-info-title");
            body.getChildren().addAll(what, relicRow());
            return;
        }

        // ---- y las cartas ----
        //
        // Cuantas se llevan lo dice el modo: 1 en Estandar y 2 en Commander,
        // donde el mazo es de 60 y una sola no se notaria
        // (AscentRun.cardBatch). Se eligen de una en una y la pantalla se
        // vuelve a montar: asi la segunda se decide viendo lo que YA te has
        // llevado, que es informacion que cambia la eleccion.
        final int left = reward.picks - cardsTaken;
        final boolean cardsPending = !onOffer.isEmpty() && left > 0;
        final boolean landPending = !landTaken && !reward.lands.isEmpty();
        if ((cardsPending || landPending) && !cardsDone) {
            if (cardsPending) {
                final Label what = new Label(left > 1
                        ? NeoText.get("ascent.reward.pickCards", left)
                        : NeoText.get("ascent.reward.pickCard"));
                what.getStyleClass().add("ascent-info-title");
                body.getChildren().addAll(what, cardRow());
            }

            // "No coger ninguna" solo es verdad si no has cogido ninguna. Con
            // una ya en el bolsillo, lo que se rechaza es el resto
            // (principio 1: un boton no puede decir algo que no es).
            final Button skip = new Button(cardsTaken == 0 && !landTaken
                    ? NeoText.get("ascent.reward.skip")
                    : NeoText.get("ascent.reward.skipRest"));
            skip.getStyleClass().add("ascent-button");
            // Saltar es una opcion de verdad: en un mazo de treinta cartas,
            // meter una mala es peor que no meter ninguna.
            skip.setOnAction(e -> {
                cardsDone = true;
                rebuild();
            });

            // ---- la tierra, que NO cuesta tu carta ----
            //
            // Va en la misma fila que el boton de salir y en horizontal a
            // proposito: esta pantalla se queda sin ALTO mucho antes que sin
            // ancho (ver applyFit), y en filas apiladas obligaba a encoger toda
            // la pantalla, cartas incluidas, para caber.
            final Region lands = landPending ? landRow() : null;
            final HBox bottom = new HBox(18);
            bottom.setAlignment(Pos.CENTER);
            if (lands != null) {
                final Label alsoLand = new Label(NeoText.get("ascent.reward.alsoLand"));
                alsoLand.getStyleClass().add("ascent-info-title");
                bottom.getChildren().addAll(alsoLand, lands);
            }
            bottom.getChildren().add(skip);
            body.getChildren().add(bottom);
        }

        if (nothingLeft()) {
            final Button done = new Button(NeoText.get("ascent.reward.toMap"));
            done.getStyleClass().addAll("ascent-button", "btn-primary");
            done.setOnAction(e -> actions.done());
            body.getChildren().add(done);
        }
    }

    private boolean nothingLeft() {
        final boolean cards = onOffer.isEmpty() || cardsTaken >= reward.picks;
        final boolean land = landTaken || reward.lands.isEmpty();
        final boolean relics = reward.relics.isEmpty() || relicTaken;
        return (cardsDone || (cards && land)) && relics;
    }

    /**
     * Las cartas, grandes y clicables. En <b>filas de tres como mucho</b>.
     *
     * <p>En Commander se ofrecen seis, y seis en una sola fila no caben de
     * ancho: {@link #applyFit()} las encogeria hasta que la carta —que es lo
     * unico que hay que mirar aqui— deja de leerse. Tres y tres cabe, y ademas
     * es la forma en la que ya se lee el premio: dos ofertas de 1 de 3, que es
     * exactamente lo que son.
     */
    private Region cardRow() {
        final VBox rows = new VBox(14);
        rows.setAlignment(Pos.CENTER);
        HBox row = null;
        int i = 0;
        for (final PaperCard card : new ArrayList<>(onOffer)) {
            if (i % PER_ROW == 0) {
                row = new HBox(18);
                row.setAlignment(Pos.CENTER);
                rows.getChildren().add(row);
            }
            final CardNode node = new CardNode(cardWidth * 1.35);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(card));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(e -> {
                // Solo el boton izquierdo elige. El derecho es para leerla
                // (CardZoom), y elegir una carta AQUI no se deshace: es el
                // principio 6, y por eso el gesto de mirar no puede escoger.
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                AscentRewards.take(run, card);
                onOffer.remove(card);
                cardsTaken++;
                rebuild();
            });
            row.getChildren().add(node);
            Anim.dealIn(node, i, onOffer.size(), true);
            i++;
        }
        return rows;
    }

    /** Cuantas cartas caben en una fila antes de partir en dos. */
    private static final int PER_ROW = 3;

    /**
     * Las basicas que se pueden coger <b>en vez de</b> la carta.
     *
     * <h2>Por que esta fila esta aqui</h2>
     *
     * <p>Reportado jugando (05-09-2026): el mazo crece nodo a nodo y la base de
     * mana no, porque lo que se ofrece son siempre hechizos. Empiezas con 12
     * tierras de 30 y acabas con 12 de 40, o sea que la run <b>empeoraba</b> tu
     * mazo mientras parecia mejorarlo.
     *
     * <p>Va justo debajo de las tres cartas y no en otra pantalla porque la
     * decision es <i>esta carta o poder lanzarla</i>, y esa se toma con las tres
     * cartas delante. Cambiar la carta por tierras <b>cuenta como el premio</b>:
     * no se pueden hacer las dos cosas.
     *
     * <p><b>No son solo basicas.</b> Con una probabilidad que sube acto a acto
     * salen tambien <b>duales, triples y monocolores buenas</b>
     * ({@code AscentRewards.landsFor}): una base de mana no se arregla solo con
     * basicas, y en el acto 3 una basica ya no es un premio.
     *
     * <p>Cada una lleva debajo <b>cuantas copias entran</b>, porque ya no son
     * siempre dos: en Commander todo lo que no sea basica es de una sola copia,
     * y el numero lo contesta el motor.
     *
     * <p>Las tierras se pintan mucho mas pequenyas que las cartas a proposito:
     * lo que se viene a mirar aqui son las cartas, y una tierra se reconoce por
     * su simbolo a cualquier tamanyo. Van ademas en la <b>misma
     * fila</b> que "no coger ninguna", que es la otra forma de no quedarse la
     * carta — y de paso el alto que ahorran es alto que no hay que quitarle a
     * las tres cartas (ver {@link #applyFit()}).
     *
     * @return la fila, o {@code null} si no hay ninguna basica que ofrecer
     */
    private Region landRow() {
        final List<PaperCard> offered = reward.lands;
        if (offered.isEmpty()) {
            return null;
        }
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        for (final PaperCard land : offered) {
            // Cuantas entran de verdad. En Commander una dual es UNA copia:
            // lo dice el motor (AscentRewards.copiesOf), no esta pantalla.
            final int copies = AscentRewards.copiesOf(run, land);
            if (copies <= 0) {
                continue;
            }
            final VBox cell = new VBox(2);
            cell.setAlignment(Pos.CENTER);
            final CardNode node = new CardNode(cardWidth * 0.62);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(land));
            cell.setCursor(javafx.scene.Cursor.HAND);
            cell.getChildren().add(node);
            if (copies > 1) {
                // Solo cuando de verdad entra mas de una. Hoy nunca
                // (LANDS_INSTEAD es 1), pero el numero lo decide el motor y una
                // etiqueta que siempre pone "x1" es ruido.
                final Label howMany = new Label("x" + copies);
                howMany.getStyleClass().add("ascent-hint");
                cell.getChildren().add(howMany);
            }
            cell.setOnMouseClicked(e -> {
                // Igual que con las cartas: el derecho es para leerla, y esto
                // no se deshace (principio 6).
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                AscentRewards.takeLands(run, land);
                // ⚠️ NO toca cardsTaken: la tierra es un EXTRA, no una de tus
                // elecciones. Reportado jugando — "que no te haga elegir entre
                // tierra o criatura, porque todo escala muy rapido" — y era
                // sobre todo cierto en Estandar, donde con una sola eleccion
                // arreglar el mana costaba el premio entero.
                //
                // Pero solo UNA por nodo, y hay que cogerla: "que puedas
                // llevartela o no, no que te la den siempre, porque entonces al
                // final de la run acabas con tierras de mas".
                landTaken = true;
                rebuild();
            });
            row.getChildren().add(cell);
        }
        return row.getChildren().isEmpty() ? null : row;
    }

    /** Las reliquias. Se pintan como cartas porque <b>son</b> cartas. */
    private Region relicRow() {
        final HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER);
        final List<AscentRelic> shown = new ArrayList<>(reward.relics);
        int i = 0;
        for (final AscentRelic relic : shown) {
            final PaperCard card = AscentRelics.cardOf(relic);
            final VBox cell = new VBox(6);
            cell.setAlignment(Pos.TOP_CENTER);
            if (card != null) {
                final CardNode node = new CardNode(cardWidth * 1.2);
                node.setRotationEnabled(false);
                node.setCard(CardView.getCardForUi(card));
                cell.getChildren().add(node);
                Anim.dealIn(node, i, shown.size(), true);
            }
            final Label name = new Label(relic.getCardName());
            name.getStyleClass().add("ascent-info-title");
            cell.getChildren().add(name);

            // ⚠️ QUE HACE la reliquia, debajo y siempre. Sin esto se elige a
            // ciegas: una reliquia nuestra no tiene arte, asi que la carta sale
            // como un rectangulo oscuro con el nombre — y entre "+2/+2 a tus
            // criaturas" y "robas tres cartas" no hay decision posible si no se
            // puede leer cual es cual. Se vio en la primera captura.
            final String text = card == null || card.getRules() == null
                    ? null : card.getRules().getOracleText();
            if (text != null && !text.isBlank()) {
                final Label what = new Label(text);
                what.getStyleClass().add("ascent-info-text");
                what.setWrapText(true);
                what.setMaxWidth(cardWidth * 1.2);
                cell.getChildren().add(what);
            }

            if (reward.chooseOne) {
                cell.setCursor(javafx.scene.Cursor.HAND);
                cell.setOnMouseClicked(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    run.addRelic(relic);
                    relicTaken = true;
                    rebuild();
                });
            }
            row.getChildren().add(cell);
            i++;
        }
        if (!reward.chooseOne) {
            // Se lleva sola: no hay nada que elegir, solo que verla.
            for (final AscentRelic relic : shown) {
                run.addRelic(relic);
            }
            final Button ok = new Button(NeoText.get("ascent.reward.toMap"));
            ok.getStyleClass().addAll("ascent-button", "btn-primary");
            ok.setOnAction(e -> {
                relicTaken = true;
                rebuild();
            });
            final VBox wrap = new VBox(12, row, ok);
            wrap.setAlignment(Pos.CENTER);
            return wrap;
        }
        return row;
    }
}
