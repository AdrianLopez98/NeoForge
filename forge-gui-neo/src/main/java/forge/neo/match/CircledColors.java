package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.eventbus.Subscribe;

import forge.card.ColorSet;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.event.GameEventGameStarted;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.neo.NeoText;

/**
 * Los colores "rodeados" de <i>Cryptic Spires</i> ("As you create your deck,
 * circle two of the colors below"), para el jugador.
 *
 * <p>Itch.io, 27-10-2026, en un draft de Double Masters 2022: <i>"the cryptic
 * spires card only taps for colourless mana so its now fucked my whole
 * draft"</i>. El motor los pregunta al empezar la partida <b>solo a la IA</b>
 * ({@code GameAction.runPreOpeningHandActions}); al humano se los lee de la
 * carta en papel del mazo, que es lo que apunta el editor de la GUI Swing
 * ({@code ACEditorBase.addSetColorID}). Nuestros editores no lo apuntan, asi
 * que la carta salia sin colores y su "Combo ColorID" no daba nada.
 *
 * <p>Aqui se le pregunta al jugador, con la misma pregunta que el motor le
 * hace a la IA y por su propio controlador (en red le llega al invitado). Se
 * hace al empezar la partida, en el hilo del motor (el bus de eventos es
 * sincrono), ANTES de que el motor mire las suyas: las que ya traen colores
 * del mazo no se tocan, y las de la IA tampoco, que esas las pone el motor.
 *
 * <p>Pura, sin JavaFX: la usa tambien Android ({@code AndroidMatchUI}).
 */
public final class CircledColors {

    private static final Map<Game, Boolean> WATCHED = Collections.synchronizedMap(new WeakHashMap<>());

    private final Game game;

    private CircledColors(final Game game) {
        this.game = game;
    }

    /** Escucha esta partida, si no se escuchaba ya. Se llama desde openView. */
    public static void watch(final Game game) {
        if (game == null || WATCHED.putIfAbsent(game, Boolean.TRUE) != null) {
            return;
        }
        game.subscribeToEvents(new CircledColors(game));
    }

    @Subscribe
    public void receive(final GameEventGameStarted ev) {
        try {
            ask(game);
        } catch (final RuntimeException e) {
            // Sin poder preguntarlo la carta se queda como venia: nunca tumbar el arranque.
            System.err.println("CircledColors: " + e);
        }
    }

    /** Pregunta a cada humano por las suyas sin colores. Una vez por jugador: todas sus copias igual. */
    static void ask(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (p.isAI()) {
                continue;
            }
            final List<Card> unmarked = new ArrayList<>();
            int count = 0;
            for (final Card c : game.getCardsInGame()) {
                if (c.getOwner() != p || c.hasMarkedColor() || c.getRules() == null) {
                    continue;
                }
                final int n = c.getRules().getSetColorID();
                if (n > 0) {
                    unmarked.add(c);
                    count = n;
                }
            }
            if (unmarked.isEmpty()) {
                continue;
            }
            final Card first = unmarked.get(0);
            // Texto nuestro: el del motor ("Choose two colors (click, then
            // CTRL/CMD + click)") deja el numero en ingles y habla de teclas.
            final String prompt = NeoText.get("match.circledColors", first.getTranslatedName(), count);
            final SpellAbility sa = new SpellAbility.EmptySa(ApiType.ChooseColor, first, p);
            final ColorSet chosen = p.getController().chooseColors(prompt, sa, count, count, ColorSet.WUBRG);
            if (chosen == null || chosen.isColorless()) {
                continue;
            }
            for (final Card c : unmarked) {
                c.setMarkedColors(chosen);
            }
            System.out.println("CircledColors: " + p.getName() + " -> " + chosen + " (" + unmarked.size() + " " + first.getName() + ")");
        }
    }
}
