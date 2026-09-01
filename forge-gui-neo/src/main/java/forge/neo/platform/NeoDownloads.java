package forge.neo.platform;

import java.util.function.Consumer;

import forge.gui.UiCommand;
import forge.gui.download.GuiDownloadService;
import forge.gui.interfaces.IButton;
import forge.gui.interfaces.IComponent;
import forge.gui.interfaces.IProgressBar;
import forge.gui.interfaces.ITextField;
import forge.localinstance.skin.FSkinProp;

/**
 * Las descargas del motor, sin ventana.
 *
 * <p>{@code GuiDownloadService} es el descargador de Forge — el que trae las
 * imagenes, las actualizaciones y los <b>mazos de internet</b>. Esta escrito
 * contra un dialogo: espera una caja de texto para el proxy, una barra de
 * progreso y un boton que el usuario pulsa dos veces (una para empezar y otra
 * para cerrar cuando acaba).
 *
 * <p>Aqui se le dan esos cuatro widgets sin pintar nada. <b>El boton se pulsa
 * solo</b>: cuando el servicio deja una orden dentro y lo habilita, esa orden
 * se ejecuta. Asi se recorren las dos etapas — empezar y cerrar — sin nadie
 * delante, y quien pidio la descarga recibe su aviso al terminar.
 *
 * <p>Cada orden se ejecuta <b>una sola vez</b>. El servicio habilita y
 * deshabilita el boton varias veces durante la descarga, y sin esa marca la
 * descarga se relanzaria sola en bucle.
 *
 * <p>Dos detalles del motor que hay que respetar:
 * <ul>
 *   <li>La barra de progreso <b>no puede ser null</b>: {@code
 *       GuiDownloadZipService.download} devuelve la cadena vacia si lo es, y
 *       entonces no descarga nada y no lo dice.</li>
 *   <li>{@code initialize} lee el texto de la caja del proxy, asi que tampoco
 *       puede ser null. Con el tipo de proxy a 0 el motor va directo.</li>
 * </ul>
 */
public final class NeoDownloads {

    private NeoDownloads() {
    }

    /** Traza las etapas por consola. Se enciende con -Dneo.net.debug=true */
    private static final boolean DEBUG = Boolean.getBoolean("neo.net.debug");

    /**
     * Lanza el servicio y avisa por {@code callback} cuando termina.
     *
     * <p>No bloquea: el motor llama a esto desde el hilo de interfaz (ver
     * {@code WaitCallback.invokeAndWait}, que es quien espera, desde otro hilo).
     */
    public static void run(final GuiDownloadService service, final Consumer<Boolean> callback) {
        final Consumer<Boolean> once = onceOnly(callback);
        try {
            final Bar bar = new Bar(service.getTitle());
            service.initialize(new Text(""), new Text(""), bar,
                    new AutoButton(once), () -> once.accept(true), null, null);
        } catch (final RuntimeException e) {
            // Sin linea, o con la lista mal: no es motivo para tirar la
            // aplicacion. Quien pidio la descarga se entera de que no hubo.
            System.err.println("[neo] la descarga ha fallado: " + e);
            once.accept(false);
        }
    }

    /** Envuelve el aviso para que no se pueda mandar dos veces. */
    private static Consumer<Boolean> onceOnly(final Consumer<Boolean> callback) {
        final java.util.concurrent.atomic.AtomicBoolean sent =
                new java.util.concurrent.atomic.AtomicBoolean();
        return ok -> {
            if (callback != null && sent.compareAndSet(false, true)) {
                callback.accept(ok);
            }
        };
    }

    /**
     * Igual que {@link #run}, pero para quien SI puede esperar.
     *
     * <p><b>Nunca llamarlo desde el hilo de JavaFX</b>: una descarga puede
     * tardar minutos y dejaria la ventana congelada.
     */
    public static boolean runAndWait(final GuiDownloadService service, final long timeoutSeconds) {
        final java.util.concurrent.CountDownLatch done =
                new java.util.concurrent.CountDownLatch(1);
        final boolean[] ok = {false};
        run(service, result -> {
            ok[0] = Boolean.TRUE.equals(result);
            done.countDown();
        });
        try {
            if (!done.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[neo] la descarga no ha terminado a tiempo");
                service.setCancel(true);
                return false;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return ok[0];
    }

    // ---------------------------------------------------------------
    // Los widgets que no se pintan
    // ---------------------------------------------------------------

    /** Lo comun a todos: existen, pero no hay nada que mostrar. */
    private abstract static class Base implements IComponent {
        private boolean enabled = true;
        private boolean visible = true;
        private String tooltip = "";

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(final boolean b) {
            this.enabled = b;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void setVisible(final boolean b) {
            this.visible = b;
        }

        @Override
        public String getToolTipText() {
            return tooltip;
        }

        @Override
        public void setToolTipText(final String s) {
            this.tooltip = s;
        }
    }

    private static final class Text extends Base implements ITextField {
        private String text;

        Text(final String initial) {
            this.text = initial == null ? "" : initial;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void setText(final String t) {
            this.text = t == null ? "" : t;
        }

        @Override
        public boolean requestFocusInWindow() {
            return false;
        }
    }

    /**
     * El boton que se pulsa solo.
     *
     * <p>El servicio deja una orden dentro y lo habilita; eso, en un dialogo de
     * verdad, es la invitacion a que el usuario haga click. Aqui el click se da
     * en cuanto llega. Son dos etapas: la primera orden empieza la descarga, la
     * segunda ({@code finish}) es la de cerrar, y es la que dice que ha ido
     * bien.
     */
    private static final class AutoButton extends Base implements IButton {
        private final Consumer<Boolean> done;
        private UiCommand command;
        private String text = "";
        private boolean selected;
        private final java.util.Set<UiCommand> fired =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        AutoButton(final Consumer<Boolean> done) {
            this.done = done;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void setText(final String t) {
            this.text = t == null ? "" : t;
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(final boolean b) {
            this.selected = b;
        }

        @Override
        public boolean requestFocusInWindow() {
            return false;
        }

        @Override
        public void setCommand(final UiCommand c) {
            this.command = c;
            press();
        }

        @Override
        public void setEnabled(final boolean b) {
            super.setEnabled(b);
            if (b) {
                press();
            }
        }

        @Override
        public void setImage(final FSkinProp color) {
        }

        @Override
        public void setTextColor(final int r, final int g, final int b) {
        }

        /** Pulsa la orden que haya, si esta habilitada y no se ha pulsado ya. */
        private void press() {
            final UiCommand c = command;
            if (c == null || !isEnabled() || !fired.add(c)) {
                return;
            }
            if (DEBUG) {
                System.out.println("[net] pulsando " + (text.isBlank() ? "empezar" : text));
            }
            try {
                c.run();
            } catch (final RuntimeException e) {
                System.err.println("[neo] la descarga ha fallado: " + e);
                done.accept(false);
            }
        }
    }

    /**
     * La barra de progreso, que aqui es la consola.
     *
     * <p>No se traza cada tanto por ciento — serian cientos de lineas por
     * descarga. Solo los cambios de etapa ("Downloading...", "Extracting...").
     */
    private static final class Bar implements IProgressBar {
        private final String title;
        private int maximum = 100;
        private String description = "";

        Bar(final String title) {
            this.title = title == null ? "" : title;
        }

        @Override
        public void setDescription(final String s) {
            if (s != null && !s.equals(description)) {
                description = s;
                System.out.println("[net] " + title + ": " + s);
            }
        }

        @Override
        public void setValue(final int progress) {
        }

        @Override
        public void reset() {
        }

        @Override
        public void setShowETA(final boolean b) {
        }

        @Override
        public void setShowCount(final boolean b) {
        }

        @Override
        public void setPercentMode(final boolean b) {
        }

        @Override
        public int getMaximum() {
            return maximum;
        }

        @Override
        public void setMaximum(final int m) {
            this.maximum = m;
        }
    }
}
