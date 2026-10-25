package forge.neo.card;

import java.util.Locale;

import forge.deck.CardPool;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>Con "Ocultar las cartas sin arte", no queda ninguna en el juego</b>
 * ({@code run.cmd artlesscheck}). Ver {@link ArtlessCards}.
 *
 * <p>Arranca el motor con el ajuste puesto ({@code NeoMain} pone
 * {@code -Dneo.cards.hideArtless} antes de cargar) y mira:
 * <ol>
 *   <li>que no quede ninguna carta en la edicion desconocida ("???") salida de
 *       un fichero de carta;</li>
 *   <li>que el motor SI conozca A-Faceless Haven (su fichero esta) y aun asi no
 *       la deje en el juego: ni en la base de cartas, ni en el catalogo, ni entre
 *       los comandantes (A-Baba Lysaga);</li>
 *   <li>que un mazo tuyo con A-Faceless Haven la conserve (el motor la carga a
 *       peticion: el ajuste quita lo que se ofrece, no toca tus mazos);</li>
 *   <li>que una A- CON expansion e imagen (A-Dawnbringer Cleric) siga: el ajuste
 *       quita las sin arte, no todas las Alchemy;</li>
 *   <li>que las reliquias de Ascenso, que tambien van a "???" pero son nuestras,
 *       sigan estando.</li>
 * </ol>
 */
public final class ArtlessCheck {

    private ArtlessCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final forge.card.CardDb db = FModel.getMagicDb().getCommonCards();

        check(ArtlessCards.hiddenThisSession(), "el motor ha arrancado con las cartas sin arte fuera",
                "el ajuste no ha llegado al motor: se arranco con ellas dentro");

        final int left = ArtlessCards.present().size();
        check(left == 0, "ninguna carta sin expansion en el juego",
                left + " cartas sin expansion siguen en el juego: " + ArtlessCards.present());

        final String faceless = "A-Faceless Haven";
        check(db.getRules(faceless, false) != null && db.getCard(faceless) == null,
                faceless + ": el motor la conoce (su fichero esta) y no la deja en el juego",
                faceless + ": reglas " + (db.getRules(faceless, false) != null) + ", carta " + db.getCard(faceless));

        boolean inCatalog = false;
        for (final PaperCard p : db.getUniqueCards()) {
            inCatalog |= faceless.equals(p.getName());
        }
        check(!inCatalog, "y no sale en el catalogo (constructor y Enciclopedia)",
                faceless + " sigue en el catalogo");

        boolean baba = false;
        for (final java.util.Map.Entry<PaperCard, Integer> e : FModel.getCommanderPool()) {
            baba |= "A-Baba Lysaga, Night Witch".equals(e.getKey().getName());
        }
        check(!baba, "A-Baba Lysaga, Night Witch no esta entre los comandantes",
                "A-Baba Lysaga, Night Witch sigue entre los comandantes");

        // Un mazo TUYO que la lleve la conserva: el motor la carga a peticion
        // al leerlo (StaticData.attemptToLoadCard, sin mirar el interruptor).
        // Es lo que se quiere: el ajuste quita lo que se OFRECE, no cambia tus
        // mazos sin preguntar. Va al final de lo que mira la base de cartas,
        // porque desde aqui esa carta vuelve a existir en esta sesion.
        final CardPool deck = new CardPool();
        deck.add(faceless, 1);
        String loaded = null;
        for (final java.util.Map.Entry<PaperCard, Integer> e : deck) {
            loaded = e.getKey().getName();
        }
        check(faceless.equals(loaded), "un mazo tuyo que la lleve la conserva (" + loaded + ")",
                "un mazo con " + faceless + " carga " + loaded);

        final PaperCard withArt = db.getCard("A-Dawnbringer Cleric");
        check(withArt != null, "A-Dawnbringer Cleric, Alchemy con expansion e imagen, sigue ("
                        + (withArt == null ? "-" : withArt.getEdition()) + ")",
                "A-Dawnbringer Cleric ha desaparecido: el ajuste quita mas de la cuenta");

        forge.neo.ascent.AscentRelics.install();
        final java.util.List<forge.neo.ascent.AscentRelic> relics = forge.neo.ascent.AscentRelics.all();
        final PaperCard relic = relics.isEmpty() ? null : forge.neo.ascent.AscentRelics.cardOf(relics.get(0));
        check(relic != null, "las reliquias de Ascenso siguen (" + (relic == null ? "-" : relic.getName()) + ")",
                "las reliquias de Ascenso han desaparecido");

        // Las fichas que llegan sin expansion (Discord, 23-10-2026: el Gingerbrute
        // de Ginger, Queen of Sweets salia sin imagen). Ver TokenKeys.
        final String ginger = TokenKeys.complete("t:gingerbrute");
        check("t:gingerbrute|FRC".equals(ginger),
                "una ficha sin expansion se completa con la que la lista (" + ginger + ")",
                "el Gingerbrute sigue sin expansion: " + ginger);
        check("t:w_1_1_soldier|M10".equals(TokenKeys.complete("t:w_1_1_soldier|M10")),
                "una ficha que ya trae expansion no se toca", "se ha tocado una ficha con expansion");
        check("t:no_existe_esta_ficha".equals(TokenKeys.complete("t:no_existe_esta_ficha")),
                "una ficha que nadie lista se queda como estaba", "se ha inventado una expansion");

        // La imagen propia y las fichas que crea una carta (Discord, 23-10-2026).
        final String tokenPath = String.valueOf(OwnImage.shownPath("t:gingerbrute"));
        check("tokens/FRC/gingerbrute.jpg".equals(tokenPath),
                "la imagen propia de una ficha va donde la buscaria el juego (" + tokenPath + ")",
                "la ficha iria a " + tokenPath);
        final forge.item.PaperCard krenko = forge.model.FModel.getMagicDb().getCommonCards().getCard("Krenko, Mob Boss");
        final forge.item.PaperCard gingerCard = forge.model.FModel.getMagicDb().getCommonCards().getCard("Ginger, Queen of Sweets");
        final String cardPath = gingerCard == null ? "null" : String.valueOf(OwnImage.shownPath(gingerCard.getImageKey(false)));
        check(cardPath.startsWith("cards/FRC/Ginger, Queen of Sweets") && cardPath.endsWith(".jpg"),
                "y la de una carta, en su impresion (" + cardPath + ")", "la carta iria a " + cardPath);
        check(".png".equals(OwnImage.extensionOf("mia.PNG")) && OwnImage.extensionOf("mia.gif") == null,
                "se aceptan JPG y PNG y nada mas", "las extensiones no van bien");
        final java.util.List<String> krenkoTokens = new java.util.ArrayList<>();
        for (final forge.game.card.CardView t : CardTokens.of(krenko)) {
            krenkoTokens.add(t.getName());
        }
        check(krenkoTokens.size() == 1 && krenkoTokens.get(0).contains("Goblin"),
                "Krenko crea su Goblin (" + krenkoTokens + ")", "las fichas de Krenko: " + krenkoTokens);
        final java.util.List<forge.game.card.CardView> gingerTokens = CardTokens.of(gingerCard);
        check(gingerTokens.size() == 1 && gingerTokens.get(0).getCurrentState().getImageKey(null).startsWith("t:gingerbrute|FRC"),
                "Ginger crea su Gingerbrute, con la imagen de FRC", "las fichas de Ginger: " + gingerTokens.size());
        check(CardTokens.of(forge.model.FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears")).isEmpty(),
                "una carta que no crea fichas no ensenya ninguna", "Grizzly Bears crea fichas?");

        // "Bajar todas las fichas" tambien las de borde antiguo que Scryfall no
        // tiene y Forge sirve de su lista (Discord, 25-10-2026: OBC, PAST, TDLS).
        final String djinn = TokenArtDownload.hostedUrlForTest("c_5_5_a_djinn_flying");
        check(djinn != null && djinn.contains("/PAST/"),
                "una ficha que solo esta en Astral Cards sale de la lista de Forge (" + djinn + ")",
                "la ficha de Astral Cards no encuentra su direccion: " + djinn);
        // Y todas las que SOLO salen en esas expansiones (Scryfall no las tiene).
        final java.util.Set<String> oldBorder = java.util.Set.of("OBC", "PAST", "TDLS");
        int only = 0;
        final java.util.List<String> lost = new java.util.ArrayList<>();
        for (final java.util.Map.Entry<String, java.util.List<String[]>> e : TokenArtDownload.allPrintings().entrySet()) {
            if (e.getValue().stream().allMatch(w -> w.length > 2 && oldBorder.contains(w[2]))) {
                only++;
                if (TokenArtDownload.hostedUrlForTest(e.getKey()) == null) {
                    lost.add(e.getKey());
                }
            }
        }
        check(only > 0 && lost.isEmpty(),
                "las " + only + " fichas que solo estan en OBC, PAST o TDLS tienen todas su direccion",
                only + " fichas solo de borde antiguo; sin direccion: " + lost);

        // Y de verdad: una imagen propia para una ficha que no tiene, y el juego la encuentra.
        try {
            final String key = "t:zz_ficha_de_prueba";
            final java.io.File src = java.io.File.createTempFile("neo-propia", ".png");
            final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 4,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageIO.write(img, "png", src);
            final boolean before = CardImages.hasImageOnDisk(key);
            final java.io.File written = CardImages.useOwnImage(key, src);
            final boolean after = CardImages.hasImageOnDisk(key);
            check(!before && written != null && written.getName().equals("zz_ficha_de_prueba.png") && after,
                    "una imagen propia se copia y el juego la encuentra (" + written + ")",
                    "la imagen propia no se encuentra: antes=" + before + " despues=" + after + " en " + written);
            check(OwnImage.isOwn(key), "y queda marcada como tuya", "no queda marcada como propia");
            OwnImage.restore(key);
            check(written != null && !written.exists() && !OwnImage.isOwn(key),
                    "volver a la original la quita (no habia otra)", "la propia sigue ahi tras volver");

            // Sobre una imagen que YA estaba (25-10-2026, constructor): se aparta y vuelve.
            final java.io.File downloaded = OwnImage.target(key, ".jpg");
            downloaded.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "jpg", downloaded);
            final long size = downloaded.length();
            final java.io.File mine = CardImages.useOwnImage(key, src);
            check(mine != null && mine.isFile() && !downloaded.exists()
                            && new java.io.File(downloaded.getPath() + ".original").isFile(),
                    "la imagen que habia se aparta, no se borra", "la que habia: " + downloaded.exists()
                            + ", apartada: " + new java.io.File(downloaded.getPath() + ".original").isFile());
            CardImages.restoreOriginalImage(key);
            check(downloaded.isFile() && downloaded.length() == size && !mine.exists() && !OwnImage.isOwn(key),
                    "y al volver a la original esta igual que antes", "no ha vuelto la original");
            downloaded.delete();
            src.delete();

            // Un fondo de pantalla (25-10-2026): se queda con forma de carta y
            // a tamanyo de Scryfall, no aplastado ni con 8 millones de pixeles.
            final java.io.File wall = java.io.File.createTempFile("neo-fondo", ".jpg");
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3840, 2160,
                    java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", wall);
            final java.io.File fitted = CardImages.useOwnImage(key, wall);
            final java.awt.image.BufferedImage got = fitted == null ? null : javax.imageio.ImageIO.read(fitted);
            final double ratio = got == null ? 0 : (double) got.getWidth() / got.getHeight();
            check(got != null && got.getWidth() <= OwnImage.MAX_WIDTH && got.getHeight() <= OwnImage.MAX_HEIGHT
                            && Math.abs(ratio - 5.0 / 7.0) < 0.02,
                    "un fondo de pantalla de 3840x2160 se recorta a forma de carta y se reduce ("
                            + (got == null ? "?" : got.getWidth() + "x" + got.getHeight()) + ")",
                    "el fondo de pantalla se ha guardado como " + (got == null ? "nada" : got.getWidth() + "x" + got.getHeight()));
            OwnImage.restore(key);
            wall.delete();
        } catch (final Exception e) {
            check(false, "", "no se pudo probar la imagen propia: " + e);
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las cartas sin arte han fallado");
        }
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [OK ] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }
}
