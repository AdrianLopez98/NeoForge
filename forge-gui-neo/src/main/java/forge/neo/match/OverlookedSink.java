package forge.neo.match;

import java.util.Collection;

import forge.game.card.CardView;

/**
 * Una interfaz que sabe iluminar lo que el motor no ilumina: lo que se puede
 * lanzar pagando con artefactos o criaturas girados ({@link TapToPay}).
 *
 * <p>La implementan las dos: {@code NeoMatchUI} y el {@code AndroidMatchUI}
 * (por el jar). Se lo dice {@code SafeActions.Guarded} justo despues de cada
 * resaltado del motor; una lista vacia apaga lo anterior. Java puro, sin API
 * que Android no tenga en la 26.
 */
public interface OverlookedSink {

    void setOverlooked(Collection<CardView> cards);
}
