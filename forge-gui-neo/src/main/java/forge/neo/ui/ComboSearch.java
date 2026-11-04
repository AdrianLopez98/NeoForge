package forge.neo.ui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/**
 * <b>Escribir para filtrar un desplegable largo</b> (Discord, 03-11-2026: <i>"all
 * of the set selection drop downs need some method of filtering ... type the
 * first letters in the set name or code"</i>).
 *
 * <p>Con un desplegable de {@value #MIN_ITEMS} o mas opciones delante (abierto o
 * con el foco), una letra o un numero abre debajo una lista con lo que lleve
 * eso en el nombre: "zen" da Zendikar, "m10" Magic 2010 (el codigo va en el
 * texto de cada expansion). Flechas para moverse, Intro o clic para elegir,
 * Retroceso para borrar y Escape para salir. Sin acentos ni mayusculas.
 *
 * <p>Se pone UNA vez en la escena ({@link #install}), no en cada desplegable:
 * asi valen todos los que hay y los que vengan (principio 8).
 *
 * <p>⚠️ No toca la lista del desplegable. Filtrar sus {@code items} cambia la
 * seleccion por el camino (el valor pasa por null y vuelve) y eso dispara los
 * {@code onAction} y oyentes de cada pantalla, que guardan ajustes o recargan.
 * Aqui se elige con {@code setValue}: lo mismo que elegir con el raton.
 *
 * <p>⚠️ Y el foco se queda en el desplegable: las teclas se leen en la escena
 * mientras la lista esta abierta. Una ventana emergente no promete coger el
 * foco, y un campo de texto dentro que no lo recibe es un buscador mudo.
 */
public final class ComboSearch {

    private ComboSearch() {
    }

    /** Desde cuantas opciones merece la pena buscar. */
    static final int MIN_ITEMS = 12;

    /** Traza lo que filtra (lo usa la bandera de prueba de la enciclopedia). */
    static final boolean TRACE = Boolean.getBoolean("neo.comboSearch.trace")
            || System.getProperty("neo.library.typeSet") != null;

    /** La busqueda abierta, si hay (solo una a la vez). */
    private static Session<?> active;

    /** En la escena de la ventana, una vez. */
    public static void install(final Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            // Con la lista abierta, cada letra es suya. Un solo filtro para las
            // dos cosas: uno anyadido a mitad de la tecla que la abre la recibia
            // tambien, y "zen" salia "zzen".
            if (active != null) {
                active.typed.handle(e);
                return;
            }
            if (e.isControlDown() || e.isAltDown() || e.isMetaDown() || e.isShortcutDown()) {
                return;
            }
            final String ch = e.getCharacter();
            if (ch == null || ch.isEmpty() || !Character.isLetterOrDigit(ch.charAt(0))) {
                return;
            }
            final ComboBox<?> box = comboOf(e.getTarget());
            if (box == null || box.isEditable() || box.isDisabled() || box.getItems().size() < MIN_ITEMS
                    || box.getScene() == null) {
                return;
            }
            e.consume();
            open(box, ch);
        });
    }

    private static ComboBox<?> comboOf(final Object target) {
        for (Node n = target instanceof Node node ? node : null; n != null; n = n.getParent()) {
            if (n instanceof ComboBox<?> box) {
                return box;
            }
        }
        return null;
    }

    private static <T> void open(final ComboBox<T> box, final String first) {
        box.hide();
        final Session<T> s = new Session<>(box);
        active = s;
        s.show(first);
    }

    /** El texto con el que se busca: el que se ve en el desplegable. */
    static <T> String textOf(final ComboBox<T> box, final T item) {
        if (item == null) {
            return "";
        }
        final String s = box.getConverter() == null ? String.valueOf(item) : box.getConverter().toString(item);
        return s == null ? String.valueOf(item) : s;
    }

    /** 0 el codigo exacto "(ZEN)", 1 empieza asi, 2 una palabra empieza asi, 3 lo lleva dentro. */
    static int rank(final String text, final String q) {
        if (text.contains("(" + q + ")")) {
            return 0;
        }
        if (text.startsWith(q)) {
            return 1;
        }
        return text.contains(" " + q) ? 2 : 3;
    }

    /** Sin acentos ni mayusculas: "zendikar" encuentra "Zendikar". */
    static String norm(final String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static final class Session<T> {
        private final ComboBox<T> box;
        private final Scene scene;
        private final StringBuilder query = new StringBuilder();
        private final FilteredList<T> matching;
        private final javafx.collections.transformation.SortedList<T> shown;
        private final ListView<T> list;
        private final Label field = new Label();
        private final Popup popup = new Popup();
        private final EventHandler<KeyEvent> typed;
        private final EventHandler<KeyEvent> pressed;
        /** Espacio e Intro al SOLTARSE abren/cierran el desplegable de debajo. */
        private final EventHandler<KeyEvent> released = e -> {
            switch (e.getCode()) {
                case SPACE, ENTER, ESCAPE -> e.consume();
                default -> { }
            }
        };

        Session(final ComboBox<T> box) {
            this.box = box;
            this.scene = box.getScene();
            matching = new FilteredList<>(FXCollections.observableArrayList(new ArrayList<>(box.getItems())));
            shown = new javafx.collections.transformation.SortedList<>(matching);
            list = new ListView<>(shown);
            if (box.getCellFactory() != null) {
                list.setCellFactory(box.getCellFactory());
            } else {
                list.setCellFactory(l -> new ListCell<>() {
                    @Override
                    protected void updateItem(final T item, final boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? null : textOf(box, item));
                    }
                });
            }
            list.setFocusTraversable(false);
            list.setPlaceholder(new Label("—"));
            list.setPrefHeight(UiScale.px(340));
            list.setPrefWidth(Math.max(box.getWidth(), UiScale.px(280)));
            list.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 1) {
                    pick();
                }
            });
            field.getStyleClass().add("combo-search-query");
            field.setMaxWidth(Double.MAX_VALUE);

            final VBox root = new VBox(4, field, list);
            // La misma piel que el desplegable: la ventana emergente tiene su
            // propia escena, sin la hoja ni la letra de la nuestra.
            root.getStyleClass().addAll("root", "team-combo", "combo-search");
            root.getStylesheets().setAll(scene.getStylesheets());
            // La escala (UiScale) va en el estilo de la raiz de la escena.
            root.setStyle(scene.getRoot() == null ? "" : scene.getRoot().getStyle());
            popup.getContent().add(root);
            popup.setAutoHide(true);
            popup.setOnHidden(e -> close());

            typed = e -> {
                final String ch = e.getCharacter();
                if (ch != null && !ch.isEmpty() && !Character.isISOControl(ch.charAt(0))
                        && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown()) {
                    query.append(ch);
                    refilter();
                }
                e.consume();
            };
            pressed = e -> {
                switch (e.getCode()) {
                    case BACK_SPACE -> {
                        if (query.length() > 0) {
                            query.setLength(query.length() - 1);
                            refilter();
                        }
                    }
                    case DOWN -> move(1);
                    case UP -> move(-1);
                    case PAGE_DOWN -> move(10);
                    case PAGE_UP -> move(-10);
                    case ENTER -> pick();
                    case ESCAPE, TAB -> popup.hide();
                    case SPACE -> { } // llega como KEY_TYPED; aqui abriria el de debajo
                    default -> {
                        return; // el resto llega como KEY_TYPED
                    }
                }
                e.consume();
            };
        }

        void show(final String first) {
            scene.addEventFilter(KeyEvent.KEY_PRESSED, pressed);
            scene.addEventFilter(KeyEvent.KEY_RELEASED, released);
            query.append(first);
            refilter();
            final Bounds b = box.localToScreen(box.getBoundsInLocal());
            if (b == null) {
                close();
                return;
            }
            // -Dneo.comboSearch.png=f.png: la lista en un PNG (el --snapshot de
            // la escena no ve las ventanas emergentes). Fuera de la pantalla: la
            // ventana de una prueba no se ve, pero esta salia encima de todo.
            final String png = System.getProperty("neo.comboSearch.png");
            popup.show(box, png == null ? b.getMinX() : -20000, png == null ? b.getMaxY() + 2 : -20000);
            if (png != null) {
                javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> {
                    try {
                        forge.neo.Snapshots.writePng(popup.getScene().snapshot(null), new java.io.File(png));
                    } catch (final java.io.IOException e) {
                        System.err.println("[combo] " + e);
                    }
                }));
            }
        }

        private void refilter() {
            final String q = norm(query.toString());
            field.setText(query.toString());
            matching.setPredicate(q.isEmpty() ? null : item -> norm(textOf(box, item)).contains(q));
            // Lo que mejor encaja, arriba: "zen" es Zendikar (ZEN) antes que
            // Zendikar Rising. A igualdad, el orden del desplegable.
            shown.setComparator(q.isEmpty() ? null
                    : java.util.Comparator.comparingInt(item -> rank(norm(textOf(box, item)), q)));
            if (!shown.isEmpty()) {
                list.getSelectionModel().select(0);
                list.scrollTo(0);
            }
            if (TRACE) {
                System.out.println("[combo] \"" + query + "\": " + shown.size() + " de " + matching.getSource().size()
                        + (shown.isEmpty() ? "" : ", la primera " + textOf(box, shown.get(0))));
            }
        }

        private void move(final int by) {
            if (shown.isEmpty()) {
                return;
            }
            final int i = Math.max(0, Math.min(shown.size() - 1,
                    list.getSelectionModel().getSelectedIndex() + by));
            list.getSelectionModel().select(i);
            list.scrollTo(Math.max(0, i - 3));
        }

        private void pick() {
            final T v = list.getSelectionModel().getSelectedItem();
            if (v == null) {
                return;
            }
            popup.hide();
            box.setValue(v);
            box.requestFocus();
        }

        private void close() {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, pressed);
            // El soltar de la tecla con la que se ha elegido llega DESPUES:
            // se quita en la vuelta siguiente.
            javafx.application.Platform.runLater(() -> scene.removeEventFilter(KeyEvent.KEY_RELEASED, released));
            if (active == this) {
                active = null;
            }
        }
    }
}
