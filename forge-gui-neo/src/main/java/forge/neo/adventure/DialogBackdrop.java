package forge.neo.adventure;

import forge.Forge;
import forge.screens.FScreen;

/**
 * Una pantalla de Forge VACIA (solo el fondo del Adventure) para que sus avisos
 * de fin de duelo tengan donde pintarse.
 *
 * <p><b>La pantalla negra al acabar un match de evento</b> (draft y Jumpstart,
 * itch.io/Discord, 29-09-2026; reproducida en vivo contra la Sliver Queen): al
 * perder contra un JEFE, o jugando con ante, {@code DuelScene.GameEnd} no
 * vuelve al evento directamente: primero ensenya un {@code FOptionPane} (la
 * frase del jefe, las cartas ganadas y perdidas) y espera al OK. Esos avisos
 * solo se dibujan ENCIMA de una pantalla de Forge ({@code Forge.render} pinta
 * {@code currentScreen} y sus capas; sin ella solo pinta la escena, y la de
 * {@code DuelScene} no pinta nada). En el Forge original esa pantalla es la de
 * la partida; con nuestro puente la partida se juega en NUESTRA mesa y
 * {@code DuelScene.enter} nunca llega a abrirla (su {@code super.enter()} va
 * detras del bloque NEOFORGE). Resultado: el aviso existia, esperaba su OK y
 * no se veia — negro con el cursor.
 *
 * <p>Se abre en {@link NeoDuelBridge} justo antes de {@code GameEnd}, sin tocar
 * {@code DuelScene}. La quita el propio Adventure al salir del duelo
 * ({@code Forge.clearScreenStack()} en su {@code afterGameEnd}). Android lo usa
 * igual desde {@code PuenteAventura.terminar} (su decision 180).
 */
public final class DialogBackdrop extends FScreen {

    private static DialogBackdrop instance;

    private DialogBackdrop() {
        super((FScreen.Header) null);
    }

    @Override
    protected void doLayout(final float startY, final float width, final float height) {
        // Nada que colocar: es solo el fondo.
    }

    /**
     * En el hilo de libGDX. Si ya hay una pantalla de Forge, no se toca.
     * Publico porque Android tiene el mismo puente ({@code PuenteAventura}) y
     * el mismo fallo: no depende de JavaFX.
     */
    public static void open() {
        if (Forge.getCurrentScreen() != null) {
            return;
        }
        if (instance == null) {
            instance = new DialogBackdrop();
        }
        Forge.openScreen(instance);
    }
}
