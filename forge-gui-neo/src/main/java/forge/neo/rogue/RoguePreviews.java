package forge.neo.rogue;

import forge.gamemodes.rogue.KeywordHint;
import forge.gamemodes.rogue.PreviewReference;
import forge.gamemodes.rogue.PreviewReferenceType;
import forge.gamemodes.rogue.TextHelper;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.item.PaperCard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>Lo que nombra una opcion de Rogue, para poder mirarlo antes de elegirla</b>
 * (Discord, 27-11-2026, jugando con pantalla tactil: <i>"When a run starts and
 * I'm supposed to get a starting special ability, I can't check what it actually
 * is—and same thing when there's a text event"</i>).
 *
 * <p>El texto de una opcion nombra un rasgo o una carta ("gana **Wrathful**") y
 * palabras propias del modo ("{{Wound}}", "{{Fellow}}"); lo que hacen de verdad
 * esta en esa carta y en la explicacion de la palabra. Forge lo ensenya en su
 * escritorio AL PASAR EL RATON ({@code RoguePreviewPopup}): con el dedo no hay
 * forma, y tocar la opcion la elige. Aqui se saca lo mismo
 * ({@code getPreviewReferences}, {@code TextHelper.resolveCard},
 * {@code KeywordHint}) para que cada interfaz lo ponga como pastillas que se
 * tocan sin elegir.
 *
 * <p>Pura, sin JavaFX: la usan el escritorio ({@code RogueDialogs}) y Android
 * por el jar. Nada de API de Java que Android no tenga en la 26.
 */
public final class RoguePreviews {

    private RoguePreviews() {
    }

    /** Una pastilla: una carta para ampliar, o una palabra con su explicacion. */
    public static final class Item {
        /** La carta, o null si es una palabra. */
        public final PaperCard card;
        /** Lo que dice la pastilla. */
        public final String title;
        /** La explicacion de la palabra; null si es una carta. */
        public final String hint;

        Item(final PaperCard card, final String title, final String hint) {
            this.card = card;
            this.title = title;
            this.hint = hint;
        }

        public boolean isCard() {
            return card != null;
        }
    }

    /** Lo que nombra un efecto (su texto y su carta de efecto). Vacio si nada. */
    public static List<Item> of(final RogueEffect effect) {
        if (effect == null) {
            return Collections.emptyList();
        }
        try {
            return of(effect.getPreviewReferences());
        } catch (final RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /** Lo que nombra un texto con marcas de Rogue (**carta**, {{palabra}}). */
    public static List<Item> ofText(final String raw) {
        try {
            return of(TextHelper.extractPreviewReferences(raw));
        } catch (final RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Las referencias, en su orden: primero las cartas, luego las palabras
     * (como las saca Forge). Lo que no se resuelve —una carta que no existe, una
     * palabra sin explicacion— se salta: una pastilla que no abre nada es peor
     * que ninguna.
     */
    public static List<Item> of(final List<PreviewReference> refs) {
        if (refs == null || refs.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Item> out = new ArrayList<>();
        final List<String> seen = new ArrayList<>();
        for (final PreviewReference r : refs) {
            if (r == null || r.token() == null) {
                continue;
            }
            try {
                if (r.type() == PreviewReferenceType.CARD) {
                    final PaperCard card = TextHelper.resolveCard(r.token());
                    if (card == null) {
                        continue;
                    }
                    String name = TextHelper.extractEffectCardDisplayNameFromReference(r.token());
                    if (name == null || name.trim().isEmpty()) {
                        name = card.getName();
                    }
                    name = RogueText.clean(name);
                    if (!seen.contains("c:" + name)) {
                        seen.add("c:" + name);
                        out.add(new Item(card, name, null));
                    }
                } else if (r.type() == PreviewReferenceType.KEYWORD) {
                    final KeywordHint hint = KeywordHint.fromToken(r.token().trim());
                    if (hint == null || hint.getHintText() == null) {
                        continue;
                    }
                    if (!seen.contains("k:" + hint.getTitle())) {
                        seen.add("k:" + hint.getTitle());
                        out.add(new Item(null, hint.getTitle(), hint.getHintText()));
                    }
                }
            } catch (final RuntimeException e) {
                // Una referencia rara no puede tumbar el dialogo: se salta.
            }
        }
        return out;
    }
}
