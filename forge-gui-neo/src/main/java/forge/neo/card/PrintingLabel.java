package forge.neo.card;

import forge.StaticData;
import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.util.ImageUtil;

/**
 * De que expansion es la impresion de una foto: el codigo ({@code AFR}) y el
 * nombre ({@code Dungeons & Dragons: Adventures in the Forgotten Realms}).
 *
 * <p>Discord, 13-10-2026: <i>"Is there a way to view edition on the card
 * info?"</i>. La carta ampliada lo ensenya arriba a la derecha de su ficha, en
 * el escritorio ({@code forge.neo.ui.CardZoom}) y en Android (su
 * {@code CardZoom.kt}). Por eso es Java puro y sin JavaFX: Android lo usa por
 * el jar. Nada de API que no tenga Android 8 (ni records, ni
 * {@code String.isBlank}).
 *
 * <p>Sale de la clave de la FOTO, no de la carta: en el constructor o la
 * enciclopedia es la impresion que hayas elegido. Una ficha, una carta boca
 * abajo o una clave que el motor no reconoce no tienen: {@link #of} da null y
 * no se ensenya nada. Por lo mismo no puede delatar una carta boca abajo — su
 * clave es la del dorso, no la de la carta.
 */
public final class PrintingLabel {

    /** El codigo de la expansion, {@code AFR}. Nunca vacio. */
    public final String code;

    /** Su nombre en Forge (en ingles, como en todo el juego), o null si no tiene otro que el codigo. */
    public final String name;

    private PrintingLabel(final String code, final String name) {
        this.code = code;
        this.name = name;
    }

    /** La de la impresion de esta foto, o null si no se sabe. */
    public static PrintingLabel of(final String imageKey) {
        if (imageKey == null || imageKey.trim().isEmpty()) {
            return null;
        }
        try {
            final PaperCard pc = ImageUtil.getPaperCardFromImageKey(imageKey);
            if (pc == null || pc.getEdition() == null || pc.getEdition().trim().isEmpty()) {
                return null;
            }
            final String code = pc.getEdition().trim();
            String name = null;
            final CardEdition ed = StaticData.instance().getEditions().get(code);
            if (ed != null && ed.getName() != null && !ed.getName().trim().isEmpty()
                    && !ed.getName().equals(code)) {
                name = ed.getName().trim();
            }
            return new PrintingLabel(code, name);
        } catch (final RuntimeException e) {
            // Una clave rara no vale una excepcion en la carta ampliada.
            return null;
        }
    }

    /** {@code Nombre (CODIGO)}, o el codigo solo. */
    public String full() {
        return name == null ? code : name + " (" + code + ")";
    }
}
