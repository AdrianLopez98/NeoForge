package forge.neo.adventure;

import javafx.application.Platform;

/**
 * arranca el Adventure de Forge tal cual, con JavaFX ya vivo al lado
 * para que sus combates salgan en la mesa de NeoForge.
 */
public final class AdventureNeoMain {

    private AdventureNeoMain() {
    }

    /**
     * Las opciones que manda el menu. Normalmente llegan como {@code -D}; desde
     * el {@code .exe} no se pueden pasar {@code -D} al proceso, y llegan en esta
     * variable de entorno ({@code clave=valor} separados por saltos de linea).
     */
    static final String PROPS_ENV = "NEO_ADVENTURE_PROPS";

    public static void main(final String[] args) {
        final String props = System.getenv(PROPS_ENV);
        if (props != null) {
            for (final String line : props.split("\n")) {
                final int eq = line.indexOf('=');
                if (eq > 0) {
                    System.setProperty(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        }
        System.setProperty(forge.neo.NeoSettings.ADVENTURE_PROCESS, "true");
        // JavaFX primero, en su propio hilo; el principal se lo queda libGDX.
        Platform.startup(() -> NeoDuelBridge.log("JavaFX listo"));
        Platform.setImplicitExit(false);
        NeoDuelBridge.log("puente " + (NeoDuelBridge.enabled() ? "ENCENDIDO" : "apagado")
                + ", arrancando el Adventure de Forge");
        SelfTest.arm();
        StarterDeck.arm();
        WindowPlacement.arm();
        // Al cerrar la ventana del Adventure, se cierra todo. Y pase lo que
        // pase: si su bucle revienta (el libro de misiones en espanyol,
        // 19-09-2026), la excepcion sale por aqui, y sin el finally el proceso
        // se quedaba vivo, sin ventana y con la musica sonando — y NeoForge no
        // dejaba volver a abrir la Aventura porque la creia abierta.
        try {
            forge.app.Main.main(WindowPlacement.launcherArgs(args));
        } catch (final Throwable e) {
            NeoDuelBridge.log("el Adventure se ha cerrado por un error: " + e);
            e.printStackTrace();
        } finally {
            Platform.exit();
            System.exit(0);
        }
    }
}
