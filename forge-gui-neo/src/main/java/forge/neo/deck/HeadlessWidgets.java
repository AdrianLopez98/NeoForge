package forge.neo.deck;

import java.util.ArrayList;
import java.util.List;

import forge.gui.interfaces.ICheckBox;
import forge.gui.interfaces.IComboBox;

/**
 * Implementaciones minimas de los widgets que pide {@code DeckImportController}.
 *
 * <p>El importador de Forge esta escrito contra unas interfaces de widget muy
 * pequenas ({@link ICheckBox}, {@link IComboBox}) en vez de contra Swing. Eso
 * permite reutilizar TODO el parser de decklists sin arrastrar la GUI vieja:
 * basta con darle estos objetos sin pantalla.
 *
 * <p>En la fase 6 el deck builder pasara widgets de JavaFX de verdad
 * implementando estas mismas interfaces, y el importador no se entera.
 */
public final class HeadlessWidgets {

    private HeadlessWidgets() {
    }

    /** Base comun: un widget que existe pero no se pinta. */
    private abstract static class Base {
        private boolean enabled = true;
        private boolean visible = true;
        private String tooltip = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean b) {
            this.enabled = b;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(final boolean b) {
            this.visible = b;
        }

        public String getToolTipText() {
            return tooltip;
        }

        public void setToolTipText(final String s) {
            this.tooltip = s;
        }
    }

    public static final class CheckBox extends Base implements ICheckBox {
        private boolean selected;

        public CheckBox(final boolean initial) {
            this.selected = initial;
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(final boolean b) {
            this.selected = b;
        }
    }

    public static final class ComboBox<E> extends Base implements IComboBox<E> {
        private final List<E> items = new ArrayList<>();
        private int selected = -1;

        @Override
        public void setSelectedItem(final E item) {
            final int i = items.indexOf(item);
            if (i >= 0) {
                selected = i;
            }
        }

        @Override
        public void setSelectedIndex(final int index) {
            if (index >= 0 && index < items.size()) {
                selected = index;
            }
        }

        @Override
        public void addItem(final E item) {
            items.add(item);
            if (selected < 0) {
                selected = 0;
            }
        }

        @Override
        public void removeAllItems() {
            items.clear();
            selected = -1;
        }

        @Override
        public int getSelectedIndex() {
            return selected;
        }

        @Override
        public E getSelectedItem() {
            return selected >= 0 && selected < items.size() ? items.get(selected) : null;
        }
    }
}
