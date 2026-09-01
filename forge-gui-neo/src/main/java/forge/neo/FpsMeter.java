package forge.neo;

import javafx.animation.AnimationTimer;

/**
 * Cuantos frames por segundo esta dando la ventana, por consola.
 *
 * <p>No es un adorno de depuracion cualquiera: es la herramienta con la que se
 * decidio si el reflejo de las cartas foil entraba o no. Sin un numero, "se
 * nota un poco" no es una respuesta \u2014 y el peor caso que habia que descartar
 * (un Commander a 4 con TODO en foil) no se puede sacar jugando.
 *
 * <p>Mide la cadencia del <b>pulso</b> de JavaFX, que es exactamente lo que
 * ve el jugador: si el hilo de interfaz se atasca repintando cartas, este
 * numero baja. Se enciende con {@code -Dneo.fps=true} y se apaga solo si no.
 */
public final class FpsMeter {

    private FpsMeter() {
    }

    private static AnimationTimer timer;

    /** Arranca el contador si {@code -Dneo.fps=true}. Si no, no hace nada. */
    public static void startIfAsked() {
        if (timer != null || !Boolean.getBoolean("neo.fps")) {
            return;
        }
        timer = new AnimationTimer() {
            private long window;
            private int frames;
            private long worst;
            private long previous;

            @Override
            public void handle(final long now) {
                if (previous > 0) {
                    // El frame mas LENTO del segundo, que es lo que se ve como
                    // un tiron. Una media de 60 con un pico de 200 ms tambien
                    // da 60 de media, y se nota igual.
                    worst = Math.max(worst, now - previous);
                }
                previous = now;
                frames++;
                if (window == 0) {
                    window = now;
                } else if (now - window >= 1_000_000_000L) {
                    System.out.println("[fps] " + frames
                            + "  peor frame: " + (worst / 1_000_000L) + " ms"
                            + "  foil animadas: "
                            + forge.neo.card.FoilClock.animatedCount());
                    frames = 0;
                    worst = 0;
                    window = now;
                }
            }
        };
        timer.start();
    }
}
