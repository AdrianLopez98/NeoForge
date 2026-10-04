package forge.neo.ui;

import forge.game.card.CardView;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;

/**
 * Lo que esta pasando ahora mismo, en el centro y con la carta grande.
 *
 * <p>Copia a Arena: cuando un hechizo o un disparo esta esperando en el stack,
 * se ensenya <b>en mitad de la pantalla, con su arte a tamanyo de lectura</b>.
 * Cuando el juego te para, lo primero que necesitas saber es de que carta viene,
 * y un nombre suelto en una esquina no basta.
 *
 * <h3>Por que casi nunca lleva botones</h3>
 *
 * <p>La primera version repetia aqui los botones del motor (OK / Cancelar), y
 * salio mal jugando: <i>"doy a OK para cerrarlo y lo que hago es pasar turno"</i>.
 * Tenia razon — ese OK no cerraba nada, era el OK de verdad del motor.
 *
 * <p>Asi que se separan las dos cosas, que es ademas lo que hace Arena:
 *
 * <ul>
 *   <li><b>El centro informa</b> ({@link #showInfo}): la carta y el texto, sin
 *       un solo boton. Nada que pulsar, nada que confundir.</li>
 *   <li><b>La barra de la derecha actua</b>: ahi siguen OK y Cancelar, en el
 *       sitio fijo de siempre.</li>
 * </ul>
 *
 * <p>La unica excepcion es {@link #showAlert}: el aviso de "esto te acaba de
 * pasar", donde el motor esta parado esperando solo eso y su boton no hace nada
 * mas que seguir.
 *
 * <p>No es un dialogo: deja pasar el raton, porque debajo hay cartas que hay que
 * poder clicar para contestar ("elige una criatura para sacrificar").
 *
 * <h3>Se mueve, se agranda y se quita (itch.io, 04-10-2026)</h3>
 *
 * <p><i>"Do you know if you can resize and/or move and/or remove the stack pop-up
 * window?"</i>. Un click ya lo apartaba, pero solo hasta lo siguiente que
 * entrara en el stack, y en mitad de la mesa tapa justo lo que estas mirando
 * segun el mazo. Ahora se <b>arrastra</b> a donde estorbe menos, la
 * <b>rueda</b> o la <b>esquina</b> le cambian el tamanyo, y las dos cosas se
 * recuerdan ({@link #KEY_X}, {@link #KEY_Y}, {@link #KEY_SIZE}).
 *
 * <p>⚠️ <b>Solo con el ajuste encendido</b> ({@code NeoSettings.stackBannerEditable},
 * apagado de fabrica — lo pidio Ana: "que no rompa nada"). Apagado es el cartel
 * de siempre: sin esquina, sin cursor de mover, la rueda no hace nada, un click
 * lo aparta, y sale en su sitio y a su tamanyo aunque haya algo guardado. Quitarlo del
 * todo es un ajuste ({@code NeoSettings.stackBanner}): el stack de la derecha
 * ya lo dice todo. El aviso con boton no se quita nunca, solo se mueve: ahi el
 * motor esta esperando.
 *
 * <p>El sitio se guarda como <b>fraccion de la mesa</b> respecto al de siempre
 * (encima de la linea de combate), no en pixeles: asi sobrevive a cambiar el
 * tamanyo de la ventana, y {@link #fitInto} lo mete dentro si no cabe — sin
 * tocar lo guardado, o encoger la ventana un momento te lo descolocaria para
 * siempre.
 */
public class PromptBanner extends VBox {

    private final Label text = new Label();
    /**
     * La carta grande del cartel.
     *
     * <p>Es el unico nodo de toda la interfaz que va cambiando de carta, asi
     * que es el unico que borra el arte viejo mientras llega el nuevo: aqui lo
     * que se quedaria puesto seria el arte del hechizo ANTERIOR, al lado del
     * texto del nuevo. Ver {@code CardNode.setBlankWhileLoading}.
     */
    private final CardNode card = new CardNode(120);
    private final HBox body = new HBox(16);
    private final Button ok = new Button(NeoText.get("banner.understood"));
    private final HBox buttons = new HBox(10, ok);
    private final VBox column = new VBox(10);

    private Runnable onOk;

    /** Donde lo ha dejado el jugador: milesimas del ancho y del alto de la mesa. */
    public static final String KEY_X = "stackBannerX";
    public static final String KEY_Y = "stackBannerY";
    /** Su tamanyo, en tanto por ciento del de fabrica. */
    public static final String KEY_SIZE = "stackBannerSize";
    private static final double MIN_SCALE = 0.6;
    private static final double MAX_SCALE = 2.0;
    /** Lo que hay que mover el raton para que un click pase a ser arrastrar. */
    private static final double SLOP = 5;

    private final double baseCardWidth;
    /** La esquina de agrandar. */
    private final Region grip = new Region();
    private double fracX;
    private double fracY;
    private double scale = 1;
    /** El hueco en el que tiene que caber (lo dice la mesa en cada layout). */
    private double areaW;
    private double areaH;
    private double pressX;
    private double pressY;
    private double startTx;
    private double startTy;
    private double startScale;
    private double startW;
    /** Este gesto ya es un arrastre: el click que llega al soltar no lo aparta. */
    private boolean dragged;

    /**
     * El cartel de la mesa que hay ahora, para que "volver a su sitio" se note
     * en el acto. DEBIL: un static fuerte agarraria la mesa entera de una
     * partida acabada (principio 11 de las notas de diseño).
     */
    private static java.lang.ref.WeakReference<PromptBanner> live =
            new java.lang.ref.WeakReference<>(null);

    public PromptBanner(final double cardWidth) {
        baseCardWidth = cardWidth;
        live = new java.lang.ref.WeakReference<>(this);
        card.setBlankWhileLoading(true);
        getStyleClass().add("prompt-banner");
        setSpacing(10);
        setPadding(new Insets(16, 22, 16, 22));
        setAlignment(Pos.CENTER);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        // Vista de LECTURA: la carta nunca sale girada aunque este tapada, que
        // es justo cuando quieres leerla.
        card.setCardWidth(cardWidth);
        card.setRotationEnabled(false);
        card.setBadgesVisible(false);
        card.setHoverEnabled(false);

        text.getStyleClass().add("prompt-banner-text");
        text.setWrapText(true);
        text.setMaxWidth(UiScale.px(420));
        text.setMinHeight(Region.USE_PREF_SIZE);

        ok.getStyleClass().addAll("btn-primary", "prompt-banner-btn");
        ok.setOnAction(e -> {
            if (onOk != null) {
                onOk.run();
            }
        });
        buttons.setAlignment(Pos.CENTER);

        column.setAlignment(Pos.CENTER_LEFT);
        body.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(body);

        // Un click en el cartel lo quita de en medio.
        //
        // Es solo informativo — el mismo texto esta en la barra de la derecha —
        // asi que tiene que haber forma de apartarlo sin depender de que
        // nosotros acertemos con cuando estorba. Es la valvula de escape.
        setOnMouseClicked(e -> {
            e.consume();
            if (dragged) {
                // Era un arrastre, no un click: se ha movido, no se aparta.
                dragged = false;
                return;
            }
            dismiss();
        });

        installMoveAndResize();
        loadPlacement();

        setVisible(false);
        setManaged(false);
    }

    // -------------------------------------------------------------------------
    //  Moverlo y agrandarlo
    // -------------------------------------------------------------------------

    /** Si se puede mover y agrandar (el ajuste, apagado de fabrica). */
    private static boolean editable() {
        return forge.neo.NeoSettings.stackBannerEditable();
    }

    private void installMoveAndResize() {
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            dragged = false;
            // Sobre el boton del aviso se pulsa el boton, no se mueve el cartel:
            // si no, un gesto de apartarlo contestaria al motor al soltar.
            if (e.getButton() != MouseButton.PRIMARY || !editable()
                    || isWithin(e.getTarget(), ok)) {
                pressX = Double.NaN;
                return;
            }
            pressX = e.getSceneX();
            pressY = e.getSceneY();
            startTx = getTranslateX();
            startTy = getTranslateY();
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!e.isPrimaryButtonDown() || !editable() || Double.isNaN(pressX)) {
                return;
            }
            final double dx = e.getSceneX() - pressX;
            final double dy = e.getSceneY() - pressY;
            if (!dragged && Math.hypot(dx, dy) < SLOP) {
                return;
            }
            dragged = true;
            moveTo(startTx + dx, startTy + dy, true);
            e.consume();
        });
        addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (dragged) {
                savePlacement();
                e.consume();
                // Si se suelta fuera del cartel (frenado en el borde) no llega
                // el click que limpiaria la marca: se limpia aqui, despues de
                // que pase ese click si llega.
                javafx.application.Platform.runLater(() -> dragged = false);
            }
        });

        // La rueda, sin Ctrl (Ctrl + rueda es acercar la mesa, §12b).
        addEventHandler(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown() || e.isShortcutDown() || e.getDeltaY() == 0 || !editable()) {
                return;
            }
            setScale(scale * Math.pow(1.0015, e.getDeltaY()));
            // Se guarda al acabar el gesto, no en cada muesca: un trackpad
            // manda decenas por segundo y cada una seria escribir en disco.
            wheelSave.playFromStart();
            e.consume();
        });

        // La esquina: arrastrarla agranda o encoge, como una ventana.
        grip.getStyleClass().add("prompt-banner-grip");
        grip.setManaged(false);
        grip.setCursor(Cursor.SE_RESIZE);
        grip.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            pressX = e.getSceneX();
            pressY = e.getSceneY();
            startScale = scale;
            startW = Math.max(1, getWidth());
            e.consume();
        });
        grip.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            final double d = ((e.getSceneX() - pressX) + (e.getSceneY() - pressY)) / 2;
            setScale(startScale * (startW + d * 2) / startW);
            e.consume();
        });
        grip.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            savePlacement();
            e.consume();
        });
        grip.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        getChildren().add(grip);
    }

    private final javafx.animation.PauseTransition wheelSave = savePause();

    private javafx.animation.PauseTransition savePause() {
        final javafx.animation.PauseTransition p =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
        p.setOnFinished(e -> savePlacement());
        return p;
    }

    private static boolean isWithin(final Object target, final javafx.scene.Node node) {
        javafx.scene.Node n = target instanceof javafx.scene.Node ? (javafx.scene.Node) target : null;
        while (n != null) {
            if (n == node) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        final double g = UiScale.px(14);
        grip.resizeRelocate(getWidth() - g - 3, getHeight() - g - 3, g, g);
    }

    private void setScale(final double s) {
        final double clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
        if (Math.abs(clamped - scale) < 0.001) {
            return;
        }
        scale = clamped;
        applyScale();
    }

    /** La carta, el ancho del texto y la letra, todo a la vez. */
    private void applyScale() {
        card.setCardWidth(baseCardWidth * scale);
        text.setMaxWidth(UiScale.px(420) * scale);
        setStyle(Math.abs(scale - 1) < 0.001 ? ""
                : String.format(java.util.Locale.ROOT, "-fx-font-size: %.3fem;", scale));
        requestLayout();
    }

    /**
     * Lo coloca a {@code (tx, ty)} de su sitio de siempre, sin salirse de la
     * mesa. Si {@code remember}, eso pasa a ser lo que se guarda.
     */
    private void moveTo(final double tx, final double ty, final boolean remember) {
        double x = tx;
        double y = ty;
        if (areaW > 0 && areaH > 0) {
            x = Math.max(-getLayoutX(), Math.min(areaW - getWidth() - getLayoutX(), x));
            y = Math.max(-getLayoutY(), Math.min(areaH - getHeight() - getLayoutY(), y));
        }
        setTranslateX(x);
        setTranslateY(y);
        if (remember && areaW > 0 && areaH > 0) {
            fracX = x / areaW;
            fracY = y / areaH;
        }
    }

    /**
     * Lo llama la mesa despues de ponerlo en su sitio de siempre: le dice cuanto
     * hueco hay y lo deja donde lo dejo el jugador, metido dentro si no cabe.
     */
    public void fitInto(final double width, final double height) {
        areaW = width;
        areaH = height;
        moveTo(fracX * width, fracY * height, false);
    }

    private void loadPlacement() {
        final boolean on = editable();
        // Apagado, el de siempre: lo guardado se queda guardado para cuando se
        // vuelva a encender, pero no se usa.
        fracX = on ? forge.neo.NeoSettings.getInt(KEY_X, 0) / 1000.0 : 0;
        fracY = on ? forge.neo.NeoSettings.getInt(KEY_Y, 0) / 1000.0 : 0;
        scale = on ? Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                forge.neo.NeoSettings.getInt(KEY_SIZE, 100) / 100.0)) : 1;
        setCursor(on ? Cursor.MOVE : null);
        grip.setVisible(on);
        applyScale();
    }

    /** Lo llama Ajustes al tocar cualquiera de los dos ajustes: se nota en el acto. */
    public static void refreshLive() {
        final PromptBanner b = live.get();
        if (b == null) {
            return;
        }
        // Quitado: fuera ya, salvo el aviso con boton (el motor lo espera).
        if (!forge.neo.NeoSettings.stackBanner() && b.isVisible() && !b.isAlerting()) {
            b.hide();
        }
        b.loadPlacement();
        b.moveTo(b.fracX * b.areaW, b.fracY * b.areaH, false);
    }

    private void savePlacement() {
        forge.neo.NeoSettings.setInt(KEY_X, (int) Math.round(fracX * 1000));
        forge.neo.NeoSettings.setInt(KEY_Y, (int) Math.round(fracY * 1000));
        forge.neo.NeoSettings.setInt(KEY_SIZE, (int) Math.round(scale * 100));
        forge.neo.NeoSettings.save();
    }

    /** "Volver a su sitio", desde Ajustes: el de siempre y del tamanyo de siempre. */
    public static void resetPlacement() {
        forge.neo.NeoSettings.setInt(KEY_X, 0);
        forge.neo.NeoSettings.setInt(KEY_Y, 0);
        forge.neo.NeoSettings.setInt(KEY_SIZE, 100);
        forge.neo.NeoSettings.save();
        final PromptBanner b = live.get();
        if (b != null) {
            b.loadPlacement();
            b.moveTo(0, 0, false);
        }
    }

    /**
     * Ensenya lo que esta pasando. <b>Sin botones.</b>
     *
     * <p>Es puro cartel: informa de que hay en el stack o de que te estan
     * preguntando. Para contestar se usa la barra de la derecha o se clica una
     * carta de la mesa.
     */
    public void showInfo(final CardView source, final String message) {
        if (message == null || message.isBlank()) {
            hide();
            return;
        }
        onOk = null;
        layoutContent(source, message, false);
    }

    /**
     * Aviso con un solo boton para seguir.
     *
     * <p>Solo para "esto te acaba de pasar": ahi el motor esta detenido
     * esperando exactamente esto, asi que el boton no puede confundirse con una
     * jugada.
     */
    public void showAlert(final String message, final String okLabel, final Runnable onContinue) {
        if (message == null || message.isBlank()) {
            hide();
            return;
        }
        onOk = onContinue;
        ok.setText(withKey(okLabel == null || okLabel.isBlank()
                ? NeoText.get("banner.understood") : okLabel));
        layoutContent(null, message, true);
    }

    /**
     * "Entendido (Space)": el boton dice con que tecla se cierra.
     *
     * <p>Reportado en r/forgeMTG el 14-09-2026: <i>that "Got it" message is really
     * annoying and I have to click on it (again, no shortcut)</i>. Ahora la tecla
     * de pasar prioridad lo cierra ({@link #acknowledge}), y se escribe aqui
     * porque un atajo que nadie sabe que existe no le quita el click a nadie.
     */
    private static String withKey(final String label) {
        final java.util.List<forge.neo.NeoShortcuts.Chord> keys =
                forge.neo.NeoShortcuts.bindings(forge.neo.NeoShortcuts.Action.PASS_PRIORITY);
        return keys.isEmpty() ? label : label + " (" + keys.get(0) + ")";
    }

    /**
     * Pulsa el boton del aviso, si hay uno puesto.
     *
     * <p>Lo usa la tecla de pasar prioridad: mientras el aviso esta puesto el
     * motor esta parado esperando SOLO este boton (ver {@link #isAlerting}), asi
     * que es lo unico que esa tecla puede querer decir. Se dispara el boton y no
     * {@code onOk} a secas, para que haga exactamente lo mismo que el click.
     *
     * @return false si no habia aviso que cerrar
     */
    public boolean acknowledge() {
        if (onOk == null || !isVisible()) {
            return false;
        }
        ok.fire();
        return true;
    }

    /**
     * true mientras hay un aviso <b>con boton</b> delante.
     *
     * <p>Hay que preguntarlo antes de repintar el cartel desde ningun sitio.
     * Cuando esto es cierto, el hilo del MOTOR esta detenido esperando ese
     * boton y nada mas: pisar el cartel se lleva el boton por delante y la
     * partida se queda muerta <b>para siempre</b>, sin excepcion, sin aviso y
     * con una mesa que se ve perfectamente normal.
     *
     * <p>Salio en cuanto los eventos del motor empezaron a llegar de verdad en
     * una partida local: el espejo del stack se repinta con cada evento, y el
     * evento que levanta el aviso trae otro detras.
     */
    public boolean isAlerting() {
        return onOk != null;
    }

    private void layoutContent(final CardView source, final String message,
                               final boolean withButton) {
        text.setText(message);

        column.getChildren().setAll(text);
        if (withButton) {
            column.getChildren().add(buttons);
        }

        body.getChildren().clear();
        if (source != null) {
            card.setCard(source);
            body.getChildren().add(card);
            text.setAlignment(Pos.CENTER_LEFT);
            text.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
            column.setAlignment(Pos.CENTER_LEFT);
        } else {
            text.setAlignment(Pos.CENTER);
            text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            column.setAlignment(Pos.CENTER);
        }
        body.getChildren().add(column);

        setVisible(true);
        setManaged(true);
        // Nada de toFront(): su sitio en el orden de hijos ya es el correcto,
        // justo DEBAJO de las capas de dialogo. Con toFront() se ponia por
        // delante de todo y tapaba lo que el motor estaba ensenyando — salio
        // jugando: un rival busco en su biblioteca y el cartel del hechizo
        // tapaba el panel que decia que carta se habia llevado.
    }

    public void hide() {
        onOk = null;
        setVisible(false);
        setManaged(false);
    }

    /**
     * Lo cierra el jugador.
     *
     * <p>Distinto de {@link #hide()}: aqui se avisa a quien lo mostro para que
     * NO lo vuelva a levantar con lo mismo. Sin eso, el siguiente aviso del
     * motor lo pintaria otra vez y cerrarlo no serviria de nada.
     *
     * <p>El aviso de "esto te acaba de pasar" no se puede descartar asi: ahi el
     * motor esta detenido esperando su boton.
     */
    private void dismiss() {
        if (onOk != null) {
            return;
        }
        hide();
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    private Runnable onDismiss;

    public void setOnDismiss(final Runnable r) {
        this.onDismiss = r;
    }

    /** Lo aparta un click dado en otro sitio de la mesa. */
    public void fireDismiss() {
        dismiss();
    }

    /** Vuelve a leer la imagen si ha llegado de Scryfall. */
    public void refreshArt() {
        card.refresh();
    }
}
