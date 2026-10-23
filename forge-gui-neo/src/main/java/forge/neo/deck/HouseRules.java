package forge.neo.deck;

import forge.deck.Deck;

/**
 * <b>Reglas de la casa</b>: un mazo que se salta las reglas de construccion A
 * PROPOSITO (Discord, 23-10-2026: un reto de su grupo, <i>"ALL commanders have
 * Partner"</i>, que quiere probar contra la IA).
 *
 * <p>La decision es POR MAZO y nunca global (Ana: <i>"como hacemos la
 * distincion para que no deje jugar con mazos ilegales a la gente que no
 * quiera"</i>). Un ajuste global se enciende para un mazo y se olvida, y el dia
 * que otro mazo es ilegal sin querer, nadie se entera. Asi:
 * <ul>
 *   <li>Un mazo ilegal SIN la marca se bloquea igual que siempre.</li>
 *   <li>Con la marca, el problema sale como aviso y se puede jugar.</li>
 *   <li>Solo en las partidas libres contra la IA (Commander, Estandar,
 *       Brawl...): {@code DeckContext.allowsHouseRules}. Quest, Ascenso, la
 *       Aventura, el draft, los torneos y la red siguen con las reglas.</li>
 * </ul>
 *
 * <p>La marca va en el propio {@code .dck}, en las etiquetas de Forge
 * ({@code Tags=}): el mazo lleva su intencion a donde vaya, PC o Android. El
 * motor no mira la legalidad al empezar la partida (pone en la zona de mando
 * todo lo que haya en la seccion Commander); quien la mira es nuestro inicio.
 *
 * <p>En el jar: la usa NeoForge Android.
 */
public final class HouseRules {

    private HouseRules() {
    }

    /** La etiqueta. Sin comas: es el separador de {@code Tags=}. */
    public static final String TAG = "neo-house-rules";

    public static boolean isOn(final Deck deck) {
        return deck != null && deck.getTags().contains(TAG);
    }

    public static void set(final Deck deck, final boolean on) {
        if (deck == null) {
            return;
        }
        if (on) {
            deck.getTags().add(TAG);
        } else {
            deck.getTags().remove(TAG);
        }
    }

    /**
     * Si este problema de construccion deja jugar: solo si el mazo lleva la
     * marca. {@code problem} es lo que dice {@code getDeckConformanceProblem}
     * (null = legal).
     */
    public static boolean letsPlay(final Deck deck, final String problem) {
        return problem == null || isOn(deck);
    }
}
