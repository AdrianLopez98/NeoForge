package forge.neo.quest;

import java.util.List;
import java.util.Locale;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gamemodes.quest.QuestEventDuel;
import forge.item.BoosterPack;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.DeckEditor;

/**
 * Comprueba el bucle de la aventura sin abrir una ventana.
 *
 * <p>Lo que hay que verificar aqui no es la pantalla: es que el <b>progreso</b>
 * existe y que <b>los rivales van a la par</b>. Eso ultimo se pidio
 * explicitamente — *"si empiezas el modo aventura de Commander que los rivales
 * del inicio no lleven mazos de bracket 5"* — y es lo que menos se puede
 * comprobar jugando, porque haria falta ganar veinte duelos para llegar al
 * siguiente escalon.
 *
 * <p>{@code run.cmd questcheck}
 */
public final class QuestCheck {

    private QuestCheck() {
    }

    public static void run() {
        boolean ok = true;
        ok &= checkOne(NeoQuest.Modalidad.COMMANDER);
        System.out.println();
        ok &= checkOne(NeoQuest.Modalidad.ESTANDAR);
        System.out.println();
        ok &= checkShop();
        System.out.println();
        ok &= checkPrize();
        System.out.println();
        ok &= checkDeckBuilding();
        System.out.println();
        ok &= checkSideboardTrap();
        System.out.println();
        ok &= checkEveryRivalHasAFace();
        System.out.println();
        ok &= checkChallenges();
        System.out.println();
        ok &= checkBazaar();
        System.out.println();
        ok &= checkEnginePrefs();

        System.out.println();
        System.out.println(ok
                ? "  OK - la aventura arranca, guarda, compra sobres y los rivales suben contigo"
                : "  FALLO - revisa el bucle de la aventura");
    }

    /**
     * Los ajustes que el motor necesita, en CUALQUIER partida.
     *
     * <p>Existe por un fallo real: se aplicaban en {@code NeoGame.play} y en
     * {@code NeoGame.playPuzzle}, y el duelo de la aventura no pasa por
     * ninguno de los dos — lo monta {@code QuestUtil.finishStartingGame()}. En
     * la aventura, entonces, {@code UI_SELECT_FROM_CARD_DISPLAYS} seguia en
     * {@code true} y <b>buscar una tierra en la biblioteca clavaba la
     * partida</b>: el motor esperaba clicks sobre una biblioteca pintada como
     * panel, que nuestra mesa no pinta.
     *
     * <p>Ahora se aplican en {@code NeoMatchUI.openView}, que el motor llama al
     * empezar cada partida. Esto lo comprueba: se pone la preferencia al reves
     * a proposito y se mira que abrir una vista la deje como tiene que estar.
     * Si alguien mueve esa llamada de sitio, esto se cae.
     */
    private static boolean checkEnginePrefs() {
        System.out.println("  --- Ajustes del motor en cualquier partida ---");
        final forge.localinstance.properties.ForgePreferences prefs =
                forge.model.FModel.getPreferences();

        // Al reves de como tienen que quedar, para que el OK signifique algo.
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref
                .UI_SELECT_FROM_CARD_DISPLAYS, true);
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref
                .YIELD_AUTO_PASS_NO_ACTIONS, false);
        // Y las del pase inteligente, tambien al reves de lo que toca con el
        // ajuste apagado. Ver mas abajo por que esto merece su propia
        // comprobacion.
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref
                .YIELD_AUTO_PASS_RESPECTS_INTERRUPTS, true);
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref
                .YIELD_INTERRUPT_ON_ATTACKERS, false);
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref
                .YIELD_INTERRUPT_ON_TRIGGERS, true);

        // Sin mesa y sin binder: openView tiene que aguantarlo igual.
        new forge.neo.match.NeoMatchUI(forge.neo.match.NeoMatchUI.Mode.HUMAN, false)
                .openView(null);

        final boolean displays = prefs.getPrefBoolean(
                forge.localinstance.properties.ForgePreferences.FPref.UI_SELECT_FROM_CARD_DISPLAYS);
        final boolean autoPass = prefs.getPrefBoolean(
                forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS);

        // ---- el pase inteligente, APAGADO ----
        //
        // Merece comprobacion propia porque "apagado" aqui NO es "todo a
        // false": es dejarlo como viene de fabrica en Forge, o sea ATTACKERS y
        // OPPONENT_SPELL en true y las otras cuatro en false. Se escribio mal
        // la primera vez (30-08-2026) y no lo habria pillado nadie: el
        // interruptor maestro apagado hace que casi todo de igual, salvo que
        // YieldController.applyInterrupt limpia un pase activo AUNQUE el
        // maestro este apagado. O sea que forzarlas a false cambiaba el
        // comportamiento de quien no ha tocado el ajuste, que es justo lo que
        // un interruptor apagado no puede hacer.
        final forge.localinstance.properties.ForgePreferences.FPref respects =
                forge.localinstance.properties.ForgePreferences.FPref
                        .YIELD_AUTO_PASS_RESPECTS_INTERRUPTS;
        final boolean maestro = prefs.getPrefBoolean(respects);
        final boolean ataques = prefs.getPrefBoolean(
                forge.localinstance.properties.ForgePreferences.FPref.YIELD_INTERRUPT_ON_ATTACKERS);
        final boolean disparos = prefs.getPrefBoolean(
                forge.localinstance.properties.ForgePreferences.FPref.YIELD_INTERRUPT_ON_TRIGGERS);
        final boolean pase = !maestro && ataques && !disparos;

        System.out.printf(Locale.ROOT,
                "  Elegir en zonas ocultas por dialogo: %s | auto-pass: %s%n",
                !displays, autoPass);
        System.out.printf(Locale.ROOT,
                "  Pase inteligente apagado: maestro %s, ataques %s (de fabrica), disparos %s%n",
                maestro, ataques, disparos);
        final boolean ok = !displays && autoPass && pase;
        System.out.println(ok
                ? "  OK - abrir una partida deja los ajustes que hace falta"
                : "  FALLO: openView no aplica los ajustes del motor");
        return ok;
    }

    /**
     * La tienda de sobres, sin ventana.
     *
     * <p>Es el pilar del modo, y lo que hay que blindar no es la animacion:
     * son los <b>numeros</b>. Que se cobre el precio exacto, que la coleccion
     * crezca exactamente en las cartas que han salido, y que abrir dos veces
     * seguidas NO de las mismas cartas — que es la trampa de
     * {@code SealedProduct}, que memoriza su contenido.
     */
    private static boolean checkShop() {
        final String name = "neo-questcheck-shop";
        NeoQuest.delete(name);
        System.out.println("  --- Tienda de sobres ---");

        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.COMMANDER);
        NeoQuest.start(name, NeoQuest.Modalidad.COMMANDER, NeoQuest.Dificultad.NORMAL,
                starters.isEmpty() ? null : starters.get(0));

        boolean ok = true;

        // ---- que haya de que elegir ----
        final List<CardEdition> editions = NeoQuestShop.editions();
        System.out.printf(Locale.ROOT, "  Expansiones con sobre: %d%n", editions.size());
        ok &= editions.size() > 50;
        if (editions.isEmpty()) {
            System.out.println("  FALLO: no hay ninguna expansion con sobre");
            NeoQuest.delete(name);
            return false;
        }

        // ---- con lo que traes de fabrica no te llega a casi nada ----
        //
        // Es progresion sana y esta puesto a proposito, pero conviene dejarlo
        // por escrito: si un dia el filtro "solo lo que puedo pagar" sale
        // vacio de golpe, la causa es esta.
        int affordable = 0;
        int cheapest = Integer.MAX_VALUE;
        CardEdition cheapestEdition = null;
        for (final CardEdition e : editions) {
            final int price = NeoQuestShop.priceOf(NeoQuestShop.boosterOf(e));
            if (price < cheapest) {
                cheapest = price;
                cheapestEdition = e;
            }
            if (price <= NeoQuest.credits()) {
                affordable++;
            }
        }
        System.out.printf(Locale.ROOT,
                "  Con %d creditos puedes pagar %d de %d | el mas barato: %s a %d cr.%n",
                NeoQuest.credits(), affordable, editions.size(),
                cheapestEdition.getCode(), cheapest);

        // ---- no se fia el sobre ----
        BoosterPack pack = NeoQuestShop.boosterOf(cheapestEdition);
        final int price = NeoQuestShop.priceOf(pack);
        if (NeoQuest.credits() < price) {
            final boolean refused = NeoQuestShop.buyAndOpen(pack) == null;
            System.out.printf(Locale.ROOT, "  Sin dinero suficiente la tienda %s%n",
                    refused ? "no vende (bien)" : "VENDE IGUAL (mal)");
            ok &= refused;
        }

        // ---- y con dinero, se compra ----
        NeoQuest.engine().getAssets().addCredits(20000);
        final long creditsBefore = NeoQuest.credits();
        final int collectionBefore = NeoQuest.collectionSize();

        pack = NeoQuestShop.boosterOf(cheapestEdition);
        final NeoQuestShop.Opened first = NeoQuestShop.buyAndOpen(pack);
        if (first == null) {
            System.out.println("  FALLO: no se ha podido comprar el sobre");
            NeoQuest.delete(name);
            return false;
        }

        System.out.printf(Locale.ROOT,
                "  Abierto %s: %d cartas (%d nuevas) | creditos %d -> %d | coleccion %d -> %d%n",
                pack.getName(), first.getCards().size(), first.getNewCount(),
                creditsBefore, NeoQuest.credits(), collectionBefore, NeoQuest.collectionSize());
        // Informativo, no falla el check: el mismo BoosterGenerator que abre
        // sobres en draft y sellado los abre aqui (la auditoría del motor, apartado D5) — sortea
        // foil solo, sin que la tienda tenga que pedirlo. Un sobre puede
        // tocar cero foil por pura suerte (~4 de cada 5 sobres), asi que no
        // se puede exigir que salga una.
        final long questFoils = first.getCards().stream().filter(PaperCard::isFoil).count();
        System.out.printf(Locale.ROOT, "  De esas, %d son foil%n", questFoils);

        // El precio EXACTO, no "algo menos". Un sobre que cobra de mas o de
        // menos rompe la economia del modo sin que se note jugando.
        ok &= first.getPaid() == price;
        ok &= NeoQuest.credits() == creditsBefore - first.getPaid();
        // Y la coleccion crece exactamente en lo que ha salido: si crece de
        // menos, hay cartas del sobre que no han llegado a tu coleccion.
        ok &= NeoQuest.collectionSize() == collectionBefore + first.getCards().size();
        ok &= !first.getCards().isEmpty();

        // ---- el sobre usa la plantilla de SU expansion ----
        int rares = 0;
        for (final PaperCard c : first.getCards()) {
            if (c.getRarity() == CardRarity.Rare || c.getRarity() == CardRarity.MythicRare) {
                rares++;
            }
        }
        System.out.printf(Locale.ROOT, "  Raras o miticas: %d%n", rares);
        ok &= rares > 0;

        // ---- LA TRAMPA: dos sobres seguidos tienen que ser distintos ----
        //
        // SealedProduct.getCards() memoriza. Si no se genera un sobre nuevo
        // despues de cada compra, el jugador "abre" una y otra vez exactamente
        // las mismas quince cartas.
        final BoosterPack second = NeoQuestShop.boosterOf(cheapestEdition);
        final NeoQuestShop.Opened opened2 = NeoQuestShop.buyAndOpen(second);
        final boolean different = opened2 != null
                && !sameCards(first.getCards(), opened2.getCards());
        System.out.printf(Locale.ROOT, "  El segundo sobre trae cartas %s%n",
                different ? "distintas (bien)" : "IDENTICAS (mal: se reusa el sobre)");
        ok &= different;

        // ---- cartas sueltas ----
        //
        // El mostrador se genera solo la primera vez que se le pide. Lo que hay
        // que comprobar es que se cobra el precio del motor, que la carta entra
        // en la coleccion y que DESAPARECE del mostrador: buyCard no la quita,
        // asi que sin retirarla a mano se podria comprar la misma copia siempre.
        final java.util.List<PaperCard> onSale = NeoQuestShop.singles();
        System.out.printf(Locale.ROOT, "  Mostrador: %d cartas sueltas%n", onSale.size());
        ok &= !onSale.isEmpty();
        if (!onSale.isEmpty()) {
            final PaperCard single = onSale.get(0);
            final int cardPrice = NeoQuestShop.priceOfCard(single);
            final long moneyBefore = NeoQuest.credits();
            final int cardsBefore = NeoQuest.collectionSize();
            final NeoQuestShop.Opened bought = NeoQuestShop.buySingle(single);
            final boolean gone = NeoQuestShop.singles().size() < onSale.size();
            System.out.printf(Locale.ROOT,
                    "  Comprada \"%s\" por %d cr. | creditos %d -> %d | coleccion %d -> %d | %s%n",
                    single.getName(), cardPrice, moneyBefore, NeoQuest.credits(),
                    cardsBefore, NeoQuest.collectionSize(),
                    gone ? "fuera del mostrador (bien)" : "SIGUE EN VENTA (mal)");
            ok &= bought != null
                    && bought.getPaid() == cardPrice
                    && NeoQuest.credits() == moneyBefore - cardPrice
                    && NeoQuest.collectionSize() == cardsBefore + 1
                    && gone;
        }

        // ---- sobres de colector ----
        //
        // Lo que hay que blindar es que de verdad traigan lo que prometen: si
        // un "sobre de colector" saca las mismas cartas que el normal, es un
        // sobre normal al triple de precio. Se comprueba que al menos una carta
        // venga de una hoja de arte especial, comparandola con lo que trae la
        // hoja normal de la expansion.
        final java.util.List<CardEdition> collectorSets = NeoQuestShop.collectorEditions();
        System.out.printf(Locale.ROOT, "  Colector: %d expansiones lo tienen%n",
                collectorSets.size());
        ok &= collectorSets.size() > 10;

        if (!collectorSets.isEmpty()) {
            final CardEdition set = collectorSets.get(0);
            final java.util.List<String> sheets = NeoQuestShop.artSheetsOf(set);
            System.out.printf(Locale.ROOT, "  %s (%s) tiene hojas: %s | normal %d cr., colector %d cr.%n",
                    set.getName(), set.getCode(), sheets,
                    NeoQuestShop.priceOf(NeoQuestShop.boosterOf(set)),
                    NeoQuestShop.collectorPrice(set));
            ok &= NeoQuestShop.collectorPrice(set)
                    == NeoQuestShop.priceOf(NeoQuestShop.boosterOf(set)) * 3;

            // Los numeros de coleccionista de las hojas de arte de ESTE set.
            final java.util.Set<String> fancy = new java.util.HashSet<>();
            for (final String sheet : sheets) {
                final forge.card.PrintSheet ps = forge.model.FModel.getMagicDb()
                        .getPrintSheets().get(set.getCode() + " " + sheet);
                if (ps != null) {
                    for (final PaperCard c : ps.toFlatList()) {
                        fancy.add(c.getName() + "|" + c.getCollectorNumber());
                    }
                }
            }
            System.out.printf(Locale.ROOT, "  La expansion tiene %d impresiones especiales%n",
                    fancy.size());

            final forge.item.BoosterPack collector = NeoQuestShop.collectorBooster(set);
            ok &= collector != null;
            if (collector != null) {
                final java.util.List<PaperCard> got = collector.getCards();
                int special = 0;
                int foils = 0;
                for (final PaperCard c : got) {
                    if (fancy.contains(c.getName() + "|" + c.getCollectorNumber())) {
                        special++;
                    }
                    if (c.isFoil()) {
                        foils++;
                    }
                }
                System.out.printf(Locale.ROOT,
                        "  Un colector trae %d cartas: %d de arte especial, %d en foil%n",
                        got.size(), special, foils);
                ok &= got.size() >= 10 && special > 0 && foils > 0;

                // Y no se puede reusar, igual que los demas sobres.
                ok &= !sameCards(got, NeoQuestShop.collectorBooster(set).getCards());
            }
        }

        // ---- el sobre de Secret Lair ----
        //
        // Lo que hay que blindar es el CRITERIO: que las cartas sean de las que
        // no salen en ningun otro sitio. Un Secret Lair lleno de Sol Rings con
        // arte nuevo no le da nada a nadie, porque la carta ya la tienes.
        final java.util.List<PaperCard> pool = NeoQuestShop.secretLairPool();
        System.out.printf(Locale.ROOT, "  Secret Lair: %d cartas exclusivas%n", pool.size());
        // Se listan: son pocas y son EL contenido del producto. Si un dia el
        // criterio se afloja y empiezan a colarse reimpresiones con arte nuevo,
        // se ve aqui de un vistazo.
        for (final PaperCard c : pool) {
            System.out.println("      . " + c.getName() + " (" + c.getRarity() + ")");
        }
        ok &= pool.size() > 20;

        boolean onlyInSecretLair = true;
        for (final PaperCard c : pool) {
            for (final PaperCard other
                    : forge.model.FModel.getMagicDb().getCommonCards().getAllCards(c.getName())) {
                final String set = other.getEdition();
                // SLX (Universes Within) es la misma carta reflavorizada y
                // tampoco sale en ningun sobre: cuenta como Secret Lair.
                if (!"SLD".equals(set) && !"SLC".equals(set) && !"SLU".equals(set)
                        && !"SLX".equals(set)) {
                    onlyInSecretLair = false;
                    System.out.println("  FALLA: " + c.getName() + " tambien esta en " + set);
                    break;
                }
            }
            if (!onlyInSecretLair) {
                break;
            }
        }
        System.out.printf(Locale.ROOT, "  Ninguna sale en otra expansion: %s%n",
                onlyInSecretLair ? "bien" : "MAL");
        ok &= onlyInSecretLair;

        final java.util.List<PaperCard> drop = NeoQuestShop.secretLairPack();
        final java.util.Set<String> distinct = new java.util.HashSet<>();
        for (final PaperCard c : drop) {
            distinct.add(c.getName());
        }
        System.out.printf(Locale.ROOT, "  Un drop trae %d cartas, %d distintas%n",
                drop.size(), distinct.size());
        ok &= drop.size() == NeoQuestShop.SECRET_LAIR_SIZE
                && distinct.size() == drop.size();

        // Dos drops seguidos no pueden ser el mismo, igual que los sobres.
        ok &= !sameCards(drop, NeoQuestShop.secretLairPack());

        final long slMoneyBefore = NeoQuest.credits();
        final int slCardsBefore = NeoQuest.collectionSize();
        final NeoQuestShop.Opened lair = NeoQuestShop.buySecretLair(drop);
        System.out.printf(Locale.ROOT,
                "  Secret Lair comprado por %d cr. | creditos %d -> %d | coleccion %d -> %d%n",
                NeoQuestShop.SECRET_LAIR_PRICE, slMoneyBefore, NeoQuest.credits(),
                slCardsBefore, NeoQuest.collectionSize());
        ok &= lair != null
                && lair.getPaid() == NeoQuestShop.SECRET_LAIR_PRICE
                && NeoQuest.credits() == slMoneyBefore - NeoQuestShop.SECRET_LAIR_PRICE
                && NeoQuest.collectionSize() == slCardsBefore + drop.size();

        // ---- los drops CON NOMBRE ----
        //
        // La tabla la escribimos nosotros (Forge no sabe a que drop pertenece
        // cada carta), asi que lo que hay que comprobar es que sus rangos
        // siguen resolviendo cartas de verdad: si Forge renumera el fichero de
        // edicion, un rango se queda vacio y el drop desapareceria de la tienda
        // en silencio. Aqui se ve.
        final java.util.List<forge.neo.quest.SecretLairDrops.Drop> named = NeoQuestShop.drops();
        System.out.printf(Locale.ROOT, "  Drops con nombre: %d%n", named.size());
        boolean dropsOk = named.size() >= 15;
        int withExclusives = 0;
        for (final forge.neo.quest.SecretLairDrops.Drop dr : named) {
            System.out.printf(Locale.ROOT, "      . %-42s %2d cartas, %d exclusivas, %d cr.%n",
                    dr.getName(), dr.getCards().size(), dr.getExclusiveCount(), dr.getPrice());
            // Un drop vacio o sin precio no puede llegar al mostrador.
            dropsOk &= !dr.getCards().isEmpty() && dr.getPrice() > 0;
            // Y ninguna carta puede salir dos veces: los rangos se solapan con
            // las variantes del mismo numero (1508 liso y 1508 con marca).
            final java.util.Set<String> names = new java.util.HashSet<>();
            for (final PaperCard c : dr.getCards()) {
                dropsOk &= names.add(c.getName());
            }
            if (dr.getExclusiveCount() > 0) {
                withExclusives++;
            }
        }
        // Y lo que da sentido a la tabla: casi todos tienen que traer algo que
        // no se pueda conseguir de otra forma. Si no, son sobres caros.
        System.out.printf(Locale.ROOT, "  Con al menos una exclusiva: %d de %d%n",
                withExclusives, named.size());
        dropsOk &= withExclusives >= named.size() - 1;
        ok &= dropsOk;

        // Comprar uno: cobra lo que dice y mete EXACTAMENTE sus cartas.
        if (!named.isEmpty()) {
            final forge.neo.quest.SecretLairDrops.Drop firstDrop = named.get(0);
            NeoQuest.engine().getAssets().addCredits(firstDrop.getPrice());
            final long dropMoney = NeoQuest.credits();
            final int dropCards = NeoQuest.collectionSize();
            final NeoQuestShop.Opened bought = NeoQuestShop.buyDrop(firstDrop);
            System.out.printf(Locale.ROOT,
                    "  %s comprado por %d cr. | creditos %d -> %d | coleccion %d -> %d%n",
                    firstDrop.getName(), firstDrop.getPrice(), dropMoney, NeoQuest.credits(),
                    dropCards, NeoQuest.collectionSize());
            ok &= bought != null
                    && bought.getPaid() == firstDrop.getPrice()
                    && NeoQuest.credits() == dropMoney - firstDrop.getPrice()
                    && NeoQuest.collectionSize() == dropCards + firstDrop.getCards().size();

            // Un drop trae SIEMPRE lo mismo: es su gracia, y lo contrario de
            // un sobre. Si esto dejara de cumplirse, lo que se ensenya antes
            // de pagar dejaria de ser lo que compras.
            ok &= sameCards(firstDrop.getCards(), NeoQuestShop.drops().get(0).getCards());
        }

        // Y sin dinero no se vende, que es lo unico que no se puede deshacer.
        NeoQuest.engine().getAssets().subtractCredits(NeoQuest.credits());
        final boolean refusedDrop = named.isEmpty()
                || NeoQuestShop.buyDrop(named.get(0)) == null;
        System.out.printf(Locale.ROOT, "  Sin dinero, el drop %s%n",
                refusedDrop ? "no se vende (bien)" : "SE VENDE IGUAL (mal)");
        ok &= refusedDrop;
        final boolean refusedLair = NeoQuestShop.buySecretLair(NeoQuestShop.secretLairPack()) == null;
        System.out.printf(Locale.ROOT, "  Sin dinero, el Secret Lair %s%n",
                refusedLair ? "no se vende (bien)" : "SE VENDE IGUAL (mal)");
        ok &= refusedLair;
        NeoQuest.engine().getAssets().addCredits(5000);

        // ---- el ARTE, restringido a lo que tienes ----
        //
        // Es la mitad de la gracia de un sobre de colector: si el editor te
        // ofrece las 95.000 impresiones de Forge, abrir un borderless no
        // significa nada porque ya lo tenias puesto de fabrica. Aqui se
        // comprueba que en la aventura solo se ofrecen los artes abiertos, y
        // que FUERA de ella se siguen ofreciendo todos.
        final forge.neo.quest.QuestDeckContext ctx = new forge.neo.quest.QuestDeckContext();
        PaperCard sample = null;
        for (final PaperCard c : ctx.pool()) {
            if (forge.model.FModel.getMagicDb().getCommonCards().getAllCards(c.getName()).size() > 2) {
                sample = c;
                break;
            }
        }
        if (sample == null) {
            System.out.println("  - no hay ninguna carta con varias impresiones para probar");
        } else {
            final int inTheGame =
                    forge.model.FModel.getMagicDb().getCommonCards().getAllCards(sample.getName()).size();
            final java.util.List<PaperCard> mine = ctx.printingsOf(sample);
            System.out.printf(Locale.ROOT,
                    "  Arte de \"%s\": tienes %d de las %d impresiones que existen%n",
                    sample.getName(), mine.size(), inTheGame);
            ok &= !mine.isEmpty() && mine.size() < inTheGame;

            // Y todas las que ofrece tienen que estar de verdad en tu coleccion.
            boolean allOwned = true;
            for (final PaperCard c : mine) {
                boolean found = false;
                for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                    if (e.getKey().equals(c)) {
                        found = true;
                        break;
                    }
                }
                allOwned &= found;
            }
            System.out.printf(Locale.ROOT, "  Y todas son tuyas: %s%n", allOwned ? "bien" : "MAL");
            ok &= allOwned;

            // Una carta que NO tienes no ofrece ningun arte.
            final PaperCard notMine = forge.model.FModel.getMagicDb()
                    .getCommonCards().getCard("Black Lotus");
            if (notMine != null && ctx.owned(notMine) == 0) {
                System.out.printf(Locale.ROOT, "  Una carta que no tienes ofrece %d artes%n",
                        ctx.printingsOf(notMine).size());
                ok &= ctx.printingsOf(notMine).isEmpty();
            }

            // Fuera de la aventura, todos.
            final int free = forge.neo.match.NeoFormat.COMMANDER.printingsOf(sample) == null
                    ? inTheGame : forge.neo.match.NeoFormat.COMMANDER.printingsOf(sample).size();
            System.out.printf(Locale.ROOT,
                    "  Fuera de la aventura se ofrecen las %d%n", free);
            ok &= free == inTheGame;
        }


        // ---- el ida y vuelta del ARTE: abrir uno nuevo de algo que ya tienes ----
        //
        // Esto es lo que faltaba por probar de punta a punta. Lo de arriba
        // comprueba que solo se ofrecen los artes que tienes; lo que no estaba
        // comprobado es el paso siguiente: que al APARECER un arte nuevo de una
        // carta que ya tenias, el editor lo ofrezca ademas del viejo.
        //
        // ⚠️ Y de paso sale la trampa que tiene montarlo: QuestDeckContext lee
        // la coleccion en su CONSTRUCTOR. Si se anyade una carta despues, el
        // contexto que ya estaba abierto no se entera — o sea que comprar un
        // sobre de colector con el editor abierto no anyade los artes hasta
        // volver a entrar. Aqui se ve, porque el contexto viejo sigue diciendo
        // una y el nuevo dice dos.
        final PaperCard twoArts = pickWithSeveralPrintings();
        if (twoArts == null) {
            System.out.println("  - no hay ninguna carta con dos impresiones para el ida y vuelta");
        } else {
            final java.util.List<PaperCard> printings = forge.model.FModel.getMagicDb()
                    .getCommonCards().getAllCards(twoArts.getName());
            final PaperCard artA = printings.get(0);
            final PaperCard artB = printings.get(1);

            // Se parte de tener SOLO artA. Se quita todo lo que hubiera de esa
            // carta y se pone una copia: si quedara alguna otra impresion
            // suelta de antes, la cuenta de artes empezaria en 2 y la prueba
            // no probaria nada.
            NeoQuest.engine().getCards().getCardpool()
                    .removeIf(c -> c.getName().equals(twoArts.getName()));
            NeoQuest.engine().getCards().addSingleCard(artA, 1);
            final forge.neo.quest.QuestDeckContext before =
                    new forge.neo.quest.QuestDeckContext();
            final int artsBefore = before.printingsOf(artA).size();

            // Y ahora "abres un colector" que trae la MISMA carta con otro arte.
            NeoQuest.engine().getCards().addSingleCard(artB, 1);
            final forge.neo.quest.QuestDeckContext after =
                    new forge.neo.quest.QuestDeckContext();
            final int artsAfter = after.printingsOf(artA).size();

            System.out.printf(Locale.ROOT,
                    "  \"%s\": con un arte el editor ofrece %d; tras abrir el segundo (%s), %d%n",
                    twoArts.getName(), artsBefore, artB.getEdition(), artsAfter);
            ok &= artsBefore == 1 && artsAfter == 2;

            // El contexto que ya estaba abierto NO se entera: es un dato real,
            // no un defecto de la prueba. Quien abra el editor y compre a la vez
            // tiene que volver a entrar.
            System.out.printf(Locale.ROOT,
                    "  El contexto abierto antes sigue ofreciendo %d (hay que reabrir el editor)%n",
                    before.printingsOf(artA).size());

            // Y cuantas copias llevas se cuenta por NOMBRE, no por impresion:
            // dos artes distintos de la misma carta son dos copias de esa carta.
            System.out.printf(Locale.ROOT, "  Copias que posees de \"%s\": %d%n",
                    twoArts.getName(), after.owned(artA));
            ok &= after.owned(artA) == 2;
        }

        // ---- vender las repetidas ----
        //
        // La regla la trae el motor (QuestSpellShop.sellExtras) y ya sabe de
        // Commander: en una aventura de Commander se conserva UNA copia; en una
        // de Estandar, cuatro. Lo que se comprueba aqui es lo que de verdad se
        // puede romper: que se cuenta por IMPRESION y no por nombre. Contarlo
        // por nombre venderia artes que ya no podrias volver a poner en un mazo,
        // y eso se cargaria justo la restriccion de arte de aqui arriba.
        final PaperCard dupe = pickWithSeveralPrintings();
        if (dupe != null) {
            final java.util.List<PaperCard> printings = forge.model.FModel.getMagicDb()
                    .getCommonCards().getAllCards(dupe.getName());
            final PaperCard artA = printings.get(0);
            final PaperCard artB = printings.get(1);
            NeoQuest.engine().getCards().getCardpool()
                    .removeIf(c -> c.getName().equals(dupe.getName()));

            // Un arte y otro arte: en Commander eso NO es una repetida.
            NeoQuest.engine().getCards().addSingleCard(artA, 1);
            NeoQuest.engine().getCards().addSingleCard(artB, 1);
            forge.neo.quest.NeoQuestSell.Sold preview =
                    forge.neo.quest.NeoQuestSell.preview();
            boolean touchesArts = false;
            for (final PaperCard c : preview.getCards()) {
                touchesArts |= c.getName().equals(dupe.getName());
            }
            System.out.printf(Locale.ROOT,
                    "  Dos ARTES de \"%s\": la venta automatica %s%n", dupe.getName(),
                    touchesArts ? "SE LOS LLEVA (mal)" : "no los toca (bien)");
            ok &= !touchesArts;

            // Dos copias de la MISMA impresion si lo son.
            NeoQuest.engine().getCards().addSingleCard(artA, 2);
            preview = forge.neo.quest.NeoQuestSell.preview();
            int spare = 0;
            for (final PaperCard c : preview.getCards()) {
                if (c.equals(artA)) {
                    spare = 1;
                }
            }
            System.out.printf(Locale.ROOT,
                    "  Tres copias del MISMO arte: sobran, la venta las ve: %s%n",
                    spare == 1 ? "bien" : "MAL");
            ok &= spare == 1;

            // Y una tierra basica no se vende nunca, por muchas que tengas.
            final PaperCard forest = forge.model.FModel.getMagicDb()
                    .getCommonCards().getCard("Forest");
            if (forest != null) {
                System.out.printf(Locale.ROOT, "  De \"Forest\" se conservan %d%n",
                        forge.neo.quest.NeoQuestSell.howManyToKeep(forest));
                ok &= forge.neo.quest.NeoQuestSell.howManyToKeep(forest) > 4;
            }

            // Vender de verdad: paga, quita las copias y las devuelve al
            // mostrador, que es lo que hace que sea reversible.
            final long moneyBefore = NeoQuest.credits();
            final int shopBefore = NeoQuestShop.singles().size();
            final forge.neo.quest.NeoQuestSell.Sold done =
                    forge.neo.quest.NeoQuestSell.sell();
            System.out.printf(Locale.ROOT,
                    "  Vendidas %d copias por %d cr. | creditos %d -> %d | mostrador %d -> %d%n",
                    done.getCopies(), done.getCredits(), moneyBefore, NeoQuest.credits(),
                    shopBefore, NeoQuestShop.singles().size());
            ok &= done.getCopies() > 0
                    && NeoQuest.credits() == moneyBefore + done.getCredits()
                    && NeoQuestShop.singles().size() == shopBefore + done.getCopies();

            // Y despues de vender no queda nada que vender: si esto fallara,
            // llamarlo dos veces seguidas seguiria sacando dinero de la nada.
            final forge.neo.quest.NeoQuestSell.Sold again =
                    forge.neo.quest.NeoQuestSell.preview();
            System.out.printf(Locale.ROOT, "  Y una segunda pasada vende %d%n",
                    again.getCopies());
            ok &= again.isEmpty();

            // Y vender a mano UNA carta concreta, que es la otra mitad del
            // mostrador: aqui si se puede vender la ultima copia.
            final long handMoney = NeoQuest.credits();
            final int had = forge.neo.quest.NeoQuestSell.owned(artA);
            final int paid = forge.neo.quest.NeoQuestSell.sellOne(artA, 1);
            System.out.printf(Locale.ROOT,
                    "  A mano: vendida 1 de \"%s\" por %d cr. | tenias %d, quedan %d%n",
                    artA.getName(), paid, had,
                    forge.neo.quest.NeoQuestSell.owned(artA));
            ok &= paid > 0
                    && forge.neo.quest.NeoQuestSell.owned(artA) == had - 1
                    && NeoQuest.credits() == handMoney + paid;

            // Y no se pueden vender mas copias de las que tienes: pedir cien
            // vende las que haya y ni una mas.
            final int left = forge.neo.quest.NeoQuestSell.owned(artA);
            forge.neo.quest.NeoQuestSell.sellOne(artA, 100);
            System.out.printf(Locale.ROOT,
                    "  Pedir 100 con %d en la mano deja %d%n", left,
                    forge.neo.quest.NeoQuestSell.owned(artA));
            ok &= forge.neo.quest.NeoQuestSell.owned(artA) == 0;
        }

        // ---- y lo comprado sobrevive a guardar ----
        NeoQuest.save();
        final int collection = NeoQuest.collectionSize();
        final long credits = NeoQuest.credits();
        ok &= NeoQuest.load(name)
                && NeoQuest.collectionSize() == collection
                && NeoQuest.credits() == credits;
        System.out.printf(Locale.ROOT, "  Guardado y recargado: %d cartas, %d creditos%n",
                NeoQuest.collectionSize(), NeoQuest.credits());

        NeoQuest.delete(name);
        System.out.println(ok ? "  OK" : "  FALLO");
        return ok;
    }

    /**
     * Todos los rivales con nombre ensenyan una carta. El sorpresa, no.
     *
     * <p><b>Lo que esto protege es una decision, no un adorno.</b> En Estandar
     * la mitad de los duelos salia con un interrogante — el mismo que el duelo
     * sorpresa — porque su {@code Title} no es una carta ("King Goldemar",
     * "Bamm Bamm Rubble"). Y si todos van a ciegas, coger siempre al sorpresa
     * es gratis: la ventaja de elegir un rival conocido no existe.
     *
     * <p>Se recorren <b>los 1.145 duelos</b> de {@code res/quest/duels}, no los
     * cuatro que te tocan hoy: el fallo depende del nombre de cada mazo, asi
     * que mirando una tanda se escapa. Y se comprueba ademas que la eleccion es
     * <b>estable</b>: la casilla se repinta cada vez que vuelves al cuartel
     * general, y un {@code CardPool} sin orden garantizado haria que el rival
     * cambiara de carta entre un repintado y el siguiente.
     */
    private static boolean checkEveryRivalHasAFace() {
        System.out.println("  --- Con que carta se presenta cada rival ---");
        boolean ok = true;

        final forge.gamemodes.quest.QuestEventDuelManagerInterface duels =
                new forge.gamemodes.quest.MainWorldEventDuelManager(
                        new java.io.File(forge.localinstance.properties
                                .ForgeConstants.DEFAULT_DUELS_DIR));

        int total = 0;
        int byName = 0;
        int byDeck = 0;
        final List<String> sinCara = new java.util.ArrayList<>();
        String ejemplo = null;

        for (final QuestEventDuel d : duels.getAllDuels()) {
            if (d.getEventDeck() == null || d.getEventDeck().getMain().isEmpty()) {
                continue;   // los mazos de construido que el lector cuela como "wild"
            }
            total++;
            final PaperCard face = DuelFace.of(d);
            if (face == null) {
                sinCara.add(d.getTitle());
                continue;
            }
            if (face.getName().equalsIgnoreCase(d.getTitle())) {
                byName++;
            } else {
                byDeck++;
                if (ejemplo == null) {
                    ejemplo = d.getTitle() + " -> " + face.getName()
                            + " (" + face.getRarity() + ")";
                }
            }
            // Estable: preguntar dos veces tiene que dar lo mismo.
            final PaperCard again = DuelFace.of(d);
            if (again == null || !again.getName().equals(face.getName())) {
                System.out.println("  FALLO: " + d.getTitle() + " cambia de carta al repintar");
                ok = false;
            }
        }

        System.out.printf(Locale.ROOT,
                "  %d duelos | %d se presentan con su propio nombre | %d con la mejor carta"
                        + " de su mazo | %d sin cara%n",
                total, byName, byDeck, sinCara.size());
        if (ejemplo != null) {
            System.out.println("    p.ej. " + ejemplo);
        }
        for (final String s : sinCara.subList(0, Math.min(5, sinCara.size()))) {
            System.out.println("    SIN CARA: " + s);
        }
        ok &= total > 0 && sinCara.isEmpty();

        // Y el sorpresa NO tiene que ensenyar nada: su mazo es el de otro rival
        // prestado, asi que una carta suya seria informacion falsa.
        boolean surpriseChecked = false;
        for (final QuestEventDuel d : NeoQuest.duels()) {
            if (!d.showDifficulty()) {
                System.out.println("  El duelo sorpresa se marca como tal: bien"
                        + " (la pantalla no le pide carta)");
                surpriseChecked = true;
            }
        }
        ok &= surpriseChecked;

        System.out.println(ok
                ? "  OK - elegir rival vuelve a ser una decision"
                : "  FALLO - hay rivales que no ensenyan nada");
        return ok;
    }

    /**
     * El banquillo que no se ve, que es lo que dejaba el mazo injugable.
     *
     * <p><b>El fallo, tal cual se reporto:</b> se empieza una aventura de
     * Estandar con el preconstruido <i>Cavalcade Charge</i>, se meten tres
     * Experimental Frenzy desde el catalogo — el editor deja — y el cuartel
     * general contesta <i>"must not contain more than 4 copies"</i> con un mazo
     * en el que solo se ven tres. Las otras tres estaban en el <b>banquillo</b>
     * del preconstruido: una zona que ninguna pantalla de NeoForge ensenya, que
     * en un duelo de la aventura no se usa nunca (cada duelo es UNA partida) y
     * que sin embargo el motor <b>si suma</b> al contar copias.
     *
     * <p>Se comprueban las dos mitades del arreglo, porque son independientes:
     *
     * <ol>
     *   <li>Que el mazo adoptado llegue <b>sin banquillo</b> y que sus cartas
     *       sigan estando en tu coleccion — si no, seria una perdida.</li>
     *   <li>Que {@code DeckEditor} cuente como cuenta el motor: por
     *       <b>nombre</b> y en <b>todas las zonas</b>. Eso se prueba con un mazo
     *       montado a mano, porque el caso puede volver por otro camino (una
     *       lista pegada, un mazo viejo).</li>
     * </ol>
     */
    private static boolean checkSideboardTrap() {
        final String name = "neo-questcheck-banquillo";
        NeoQuest.delete(name);
        System.out.println("  --- El banquillo invisible ---");
        boolean ok = true;

        // Un preconstruido de Estandar que TENGA banquillo. El de la captura es
        // Cavalcade Charge, pero vale cualquiera: lo que se prueba es la forma.
        Deck precon = null;
        for (final Deck d : NeoQuest.starterDecks(NeoQuest.Modalidad.ESTANDAR)) {
            if (d.has(DeckSection.Sideboard)) {
                precon = d;
                break;
            }
        }
        if (precon == null) {
            System.out.println("  FALLO: ningun preconstruido de Estandar trae banquillo");
            return false;
        }
        final int sideCards = precon.get(DeckSection.Sideboard).countAll();
        final List<PaperCard> sideList = precon.get(DeckSection.Sideboard).toFlatList();
        System.out.printf(Locale.ROOT, "  Preconstruido: %s (%d principal + %d banquillo)%n",
                precon.getName(), precon.getMain().countAll(), sideCards);

        NeoQuest.start(name, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL, precon);

        // 1a. El mazo de la aventura ya no lo lleva.
        final Deck mine = NeoQuest.currentDeck();
        final boolean stripped = mine != null && !mine.has(DeckSection.Sideboard);
        System.out.printf(Locale.ROOT, "  Tu mazo tras adoptarlo: %s%n",
                stripped ? "sin banquillo (bien)" : "SIGUE CON BANQUILLO (mal)");
        ok &= stripped;

        // 1b. Pero el preconstruido del catalogo sigue entero: es el MISMO
        //     objeto que usa la tienda, y vaciarlo se lo quitaria a todos.
        final boolean preconIntact = precon.has(DeckSection.Sideboard)
                && precon.get(DeckSection.Sideboard).countAll() == sideCards;
        System.out.printf(Locale.ROOT, "  El preconstruido del catalogo: %s%n",
                preconIntact ? "intacto (bien)" : "LO HEMOS VACIADO (mal)");
        ok &= preconIntact;

        // 1c. Y no se ha perdido ni una carta: estan en tu coleccion.
        final QuestDeckContext context = new QuestDeckContext();
        int missing = 0;
        for (final PaperCard c : sideList) {
            if (context.owned(c) <= 0) {
                missing++;
            }
        }
        System.out.printf(Locale.ROOT, "  Cartas del banquillo en tu coleccion: %d de %d%n",
                sideList.size() - missing, sideList.size());
        ok &= missing == 0;

        // 1d. Y el mazo se puede jugar tal cual, que es lo que fallaba.
        final String problem = NeoQuest.problemWith(mine);
        System.out.printf(Locale.ROOT, "  Se puede jugar: %s%n",
                problem == null ? "si" : problem);
        ok &= problem == null;

        // ---- 2. Y que contar copias mire donde mira el motor ----
        ok &= checkCountsLikeEngine(context);

        NeoQuest.delete(name);
        System.out.println(ok
                ? "  OK - el banquillo ya no puede dejar un mazo injugable"
                : "  FALLO - el banquillo sigue contando a escondidas");
        return ok;
    }

    /**
     * Contar copias como el motor: por NOMBRE y en TODAS las zonas.
     *
     * <p>Se monta el mazo a mano con las dos trampas juntas — copias en el
     * banquillo y copias de <b>otra impresion</b> — porque las dos daban cero
     * en el editor y cuatro en el motor.
     */
    private static boolean checkCountsLikeEngine(final QuestDeckContext context) {
        boolean ok = true;

        // Una carta de la que tengas al menos dos impresiones distintas; si no
        // hay ninguna, vale con una sola y se prueba solo lo del banquillo.
        PaperCard artA = null;
        PaperCard artB = null;
        for (final PaperCard c : context.pool()) {
            if (c.getRules().getType().isBasicLand()) {
                continue;
            }
            final List<PaperCard> arts = FModel.getMagicDb().getCommonCards().getAllCards(c);
            if (arts.size() >= 2) {
                artA = arts.get(0);
                artB = arts.get(1);
                break;
            }
            if (artA == null) {
                artA = c;
                artB = c;
            }
        }
        if (artA == null) {
            System.out.println("  FALLO: la coleccion no da ninguna carta con la que probar");
            return false;
        }

        final Deck test = new Deck("neo-questcheck-contar");
        test.getMain().add(artA, 3);
        test.getOrCreate(DeckSection.Sideboard).add(artB, 3);
        final DeckEditor editor = new DeckEditor(context, test);

        final int counted = editor.countOf(artA);
        System.out.printf(Locale.ROOT,
                "  %s: 3 en el principal (%s) + 3 en el banquillo (%s) -> el editor cuenta %d%n",
                artA.getName(), artA.getEdition(), artB.getEdition(), counted);
        ok &= counted == 6;

        // Y con seis copias tiene que verlo ILEGAL y saber arreglarlo. Sin lo
        // segundo el jugador se queda encerrado: las copias que sobran estan en
        // la zona que no puede abrir.
        final String flaggedName = artA.getName();
        final boolean flagged = editor.illegalCards().stream()
                .anyMatch(c -> c.getName().equals(flaggedName));
        System.out.printf(Locale.ROOT, "  Lo marca como que no cabe: %s%n",
                flagged ? "si" : "NO (mal)");
        ok &= flagged;

        final int byRules = editor.deckFormat().getMaxCardCopies(artA);
        final int have = context.owned(artA);
        final int removed = editor.removeIllegal();
        final int left = editor.countOf(artA);
        final int leftMain = test.getMain().count(artA);
        System.out.printf(Locale.ROOT,
                "  Al quitar lo que sobra: %d fuera | quedan %d (tope del formato %s)"
                        + " | %d en el principal (tienes %d)%n",
                removed, left, byRules == Integer.MAX_VALUE ? "sin limite" : byRules,
                leftMain, have);
        // Los dos techos, cada uno sobre lo suyo: el del formato cuenta TODO,
        // el de tu coleccion solo lo que has puesto en el principal.
        ok &= byRules == Integer.MAX_VALUE || left <= byRules;
        ok &= have == Integer.MAX_VALUE || leftMain <= have;

        return ok;
    }

    /**
     * Montar un mazo con TU coleccion.
     *
     * <p>Lo que hay que blindar aqui es el techo: en la aventura no se monta
     * con lo que existe, se monta con lo que tienes. Que el editor deje meter
     * una carta que no tienes no se ve en una captura — se ve tres duelos
     * despues, cuando el mazo no arranca.
     */
    private static boolean checkDeckBuilding() {
        final String name = "neo-questcheck-deck";
        NeoQuest.delete(name);
        System.out.println("  --- Montar mazo con tu coleccion ---");

        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.ESTANDAR);
        NeoQuest.start(name, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                starters.isEmpty() ? null : starters.get(0));

        boolean ok = true;

        final QuestDeckContext context = new QuestDeckContext();
        System.out.printf(Locale.ROOT,
                "  Tu coleccion: %d cartas distintas, %d en total | catalogo del editor: %d%n",
                context.uniqueCount(), context.totalCount(), context.pool().size());
        ok &= context.isLimited();
        ok &= context.uniqueCount() > 0;
        ok &= context.pool().size() == context.uniqueCount();

        // ---- una carta que NO tienes no se puede meter ----
        //
        // Se busca a proposito una que exista en Magic y no este en tu
        // coleccion: es el caso que rompe el modo si se cuela.
        PaperCard notOwned = null;
        for (final PaperCard c
                : forge.model.FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (context.owned(c) == 0) {
                notOwned = c;
                break;
            }
        }
        final forge.neo.deck.DeckEditor editor =
                forge.neo.deck.DeckEditor.createNew(context, name + "-mazo");
        if (notOwned != null) {
            final String why = editor.rejectionReason(notOwned);
            final int added = editor.add(notOwned, 1);
            System.out.printf(Locale.ROOT, "  %s (no la tienes): %s | metidas: %d%n",
                    notOwned.getName(), why == null ? "LA DEJA METER (mal)" : why, added);
            ok &= why != null && added == 0;
        }

        // ---- y de las que tienes, no mas de las que tienes ----
        PaperCard owned = null;
        for (final PaperCard c : context.pool()) {
            if (!c.getRules().getType().isBasicLand()) {
                owned = c;
                break;
            }
        }
        if (owned == null) {
            System.out.println("  FALLO: la coleccion no tiene ninguna carta que meter");
            NeoQuest.delete(name);
            return false;
        }
        final int have = context.owned(owned);
        final int room = editor.roomFor(owned);
        final int got = editor.add(owned, 99);
        System.out.printf(Locale.ROOT,
                "  %s: tienes %d | caben %d | al pedir 99 mete %d%n",
                owned.getName(), have, room, got);
        // El techo es el menor de los dos: las reglas del formato y lo que
        // tienes. Pedir de mas nunca puede pasarse de ninguno.
        ok &= got <= have;
        ok &= got <= editor.deckFormat().getMaxCardCopies(owned);
        ok &= editor.rejectionReason(owned) != null || editor.countOf(owned) < have;

        // ---- ni un comandante que no tienes ----
        //
        // Por el catalogo no puede colarse (solo ensenya lo tuyo), pero por la
        // importacion de una lista pegada si.
        if (notOwned != null) {
            final boolean set = editor.setCommander(notOwned);
            System.out.printf(Locale.ROOT, "  Nombrar comandante una carta que no tienes: %s%n",
                    set ? "LO ACEPTA (mal)" : "lo rechaza (bien)");
            ok &= !set;
        }

        // ---- se guarda, y aparece en los mazos de la aventura ----
        final int decksBefore = NeoQuest.decks().size();
        editor.save();
        NeoQuest.save();
        final int decksAfter = NeoQuest.decks().size();
        System.out.printf(Locale.ROOT, "  Mazos de la aventura: %d -> %d (guardado '%s')%n",
                decksBefore, decksAfter, editor.getName());
        ok &= decksAfter == decksBefore + 1;

        // ---- y sobrevive a recargar la aventura ----
        NeoQuest.load(name);
        boolean found = false;
        for (final Deck d : NeoQuest.decks()) {
            if (d.getName().equals(editor.getName())) {
                found = true;
                break;
            }
        }
        System.out.printf(Locale.ROOT, "  Tras recargar la aventura, el mazo %s%n",
                found ? "sigue ahi" : "SE HA PERDIDO");
        ok &= found;

        // ---- las reglas que se aplican son las de la AVENTURA ----
        //
        // No las del formato general: en Quest se juntan el reglamento de Quest
        // y, si la modalidad lo es, ademas el de Commander.
        final String problem = editor.problem();
        System.out.printf(Locale.ROOT, "  El mazo a medias dice: %s%n",
                problem == null ? "listo para jugar" : problem);
        // Un mazo de dos cartas NO puede estar listo: si lo dice, es que se
        // esta preguntando al reglamento equivocado.
        ok &= problem != null;

        NeoQuest.delete(name);
        System.out.println(ok ? "  OK" : "  FALLO");
        return ok;
    }

    /**
     * El sorteo del sobre de premio.
     *
     * <p>Esto no se puede comprobar jugando: haria falta ganar cincuenta
     * duelos para saber si las expansiones caras salen menos. Se sortea 300
     * veces y se miran los numeros.
     */
    private static boolean checkPrize() {
        final String name = "neo-questcheck-prize";
        NeoQuest.delete(name);
        System.out.println("  --- Sobre de premio ---");

        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.COMMANDER);
        NeoQuest.start(name, NeoQuest.Modalidad.COMMANDER, NeoQuest.Dificultad.NORMAL,
                starters.isEmpty() ? null : starters.get(0));

        boolean ok = true;

        // La ventana de "lo nuevo": las cinco primeras, que vienen ordenadas de
        // la mas nueva a la mas vieja.
        final List<CardEdition> all = NeoQuestShop.editions();
        final java.util.Set<String> newest = new java.util.HashSet<>();
        for (int i = 0; i < Math.min(5, all.size()); i++) {
            newest.add(all.get(i).getCode());
        }

        // El precio medio del catalogo entero, para comparar.
        long catalogTotal = 0;
        for (final CardEdition e : all) {
            catalogTotal += NeoQuestShop.priceOf(NeoQuestShop.boosterOf(e));
        }
        final long catalogAvg = all.isEmpty() ? 0 : catalogTotal / all.size();

        final int rounds = 300;
        long offeredTotal = 0;
        int offeredCount = 0;
        int worstRecent = Integer.MAX_VALUE;
        int expensiveSeen = 0;
        for (int r = 0; r < rounds; r++) {
            final List<CardEdition> options = NeoQuestPrize.choices();
            if (options.size() != 10) {
                System.out.printf(Locale.ROOT, "  FALLO: %d opciones en vez de 10%n",
                        options.size());
                ok = false;
                break;
            }
            final java.util.Set<String> seen = new java.util.HashSet<>();
            int recent = 0;
            for (final CardEdition e : options) {
                if (!seen.add(e.getCode())) {
                    System.out.println("  FALLO: la misma expansion dos veces en el sorteo");
                    ok = false;
                }
                if (newest.contains(e.getCode())) {
                    recent++;
                }
                final int price = NeoQuestShop.priceOf(NeoQuestShop.boosterOf(e));
                offeredTotal += price;
                offeredCount++;
                if (price >= 5000) {
                    expensiveSeen++;
                }
            }
            worstRecent = Math.min(worstRecent, recent);
        }

        final long offeredAvg = offeredCount == 0 ? 0 : offeredTotal / offeredCount;
        System.out.printf(Locale.ROOT,
                "  %d sorteos de 10 | de las 5 mas nuevas, en el PEOR caso: %d%n",
                rounds, worstRecent);
        System.out.printf(Locale.ROOT,
                "  Precio medio ofrecido: %d cr. (el del catalogo entero es %d)%n",
                offeredAvg, catalogAvg);
        System.out.printf(Locale.ROOT,
                "  Sobres de 5.000 cr. o mas ofrecidos: %d de %d (%.1f%%)%n",
                expensiveSeen, offeredCount, 100.0 * expensiveSeen / Math.max(1, offeredCount));

        // Siempre al menos las dos garantizadas.
        ok &= worstRecent >= 2;
        // Y las caras tienen que salir MENOS: si el peso por precio no se
        // aplicara, la media ofrecida seria la del catalogo.
        ok &= offeredAvg < catalogAvg;

        // Cada cuantas victorias toca, y que la preferencia del motor se repone.
        // OJO: la preferencia va por dificultad, y esta aventura es Normal ->
        // WINS_BOOSTER_MEDIUM. Preguntar por la de siempre (_EASY) daba un
        // FALLO que no existia.
        final forge.gamemodes.quest.data.QuestPreferences.QPref pref =
                NeoQuestPrize.boosterPref();
        final String was = forge.model.FModel.getQuestPreferences().getPref(pref);
        final String muted = NeoQuestPrize.muteEngineBooster();
        final String during = forge.model.FModel.getQuestPreferences().getPref(pref);
        NeoQuestPrize.unmuteEngineBooster(muted);
        final String after = forge.model.FModel.getQuestPreferences().getPref(pref);
        System.out.printf(Locale.ROOT,
                "  Sobre del motor (%s): %s -> %s -> %s (toca cada %d victorias)%n",
                pref, was, during, after, NeoQuestPrize.winsPerPrize());
        // Apagado mientras reparte, y REPUESTO antes de que se guarde: ese
        // fichero se comparte con la instalacion normal de Forge del usuario.
        ok &= "0".equals(during) && was.equals(after);

        // ---- Y LO MAS IMPORTANTE: un premio es GRATIS ----
        //
        // El sobre que eliges al ganar entra en la coleccion sin cobrar. Es
        // facil que esto se rompa sin que se note (basta con que award() acabe
        // pasando por buyAndOpen en vez de por grantFree), y el jugador lo
        // descubriria pagando por su propio premio.
        final long creditsBefore = NeoQuest.credits();
        final int collectionBefore = NeoQuest.collectionSize();
        final forge.neo.quest.NeoQuestShop.Opened prize = NeoQuestPrize.award();
        System.out.printf(Locale.ROOT,
                "  Premio cobrado: %d cr. | creditos %d -> %d | coleccion %d -> %d%n",
                prize == null ? -1 : prize.getPaid(), creditsBefore, NeoQuest.credits(),
                collectionBefore, NeoQuest.collectionSize());
        ok &= prize != null && prize.getPaid() == 0;
        ok &= NeoQuest.credits() == creditsBefore;
        ok &= NeoQuest.collectionSize() == collectionBefore + (prize == null ? -1
                : prize.getCards().size());

        NeoQuest.delete(name);
        System.out.println(ok ? "  OK" : "  FALLO");
        return ok;
    }

    /** Mismas cartas y en el mismo orden: es lo que pasa si se reusa el sobre. */
    /**
     * Los desafios, sin ventana.
     *
     * <p>Lo que hay que blindar no es la pantalla: es que los 37 ficheros de
     * {@code res/quest/challenges} se lean, que el motor abra los que tocan
     * segun tus victorias, y sobre todo que un desafio se pueda <b>jugar por el
     * mismo camino que un duelo</b> — {@code QuestUtil.setEvent} recibe la
     * clase padre, asi que si eso se rompiera lo haria en silencio.
     */
    private static boolean checkChallenges() {
        System.out.println("  --- Desafios ---");
        final String name = "neo-questcheck-challenge";
        NeoQuest.delete(name);
        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.COMMANDER);
        NeoQuest.start(name, NeoQuest.Modalidad.COMMANDER, NeoQuest.Dificultad.FACIL,
                starters.isEmpty() ? null : starters.get(0));

        boolean ok = true;

        // Sin victorias no hay ninguno abierto, y la pantalla tiene que poder
        // decir cuantas faltan en vez de un "no hay" a secas.
        NeoQuest.forgetChallenges();
        System.out.printf(Locale.ROOT, "  Recien empezada: %d abiertos, uno cada %d victorias,"
                + " faltan %d%n", NeoQuest.challenges().size(), NeoQuest.winsPerChallenge(),
                NeoQuest.winsToNextChallenge());
        ok &= NeoQuest.winsPerChallenge() > 0;

        // Con victorias se abren, y como mucho cinco: lo decide
        // regenerateChallenges(), no nosotros.
        for (int i = 0; i < 40; i++) {
            NeoQuest.engine().getAchievements().addWin();
        }
        NeoQuest.forgetChallenges();
        final java.util.List<forge.gamemodes.quest.QuestEventChallenge> open =
                NeoQuest.challenges();
        System.out.printf(Locale.ROOT, "  Con 40 victorias: %d desafios abiertos%n", open.size());
        ok &= !open.isEmpty() && open.size() <= 5;

        for (final forge.gamemodes.quest.QuestEventChallenge c : open) {
            System.out.printf(Locale.ROOT, "      . %-34s %s | rival a %d vidas | %d cr. | %s%n",
                    c.getTitle(), c.getDifficulty(), c.getAILife(), c.getCreditsReward(),
                    c.isRepeatable() ? "repetible" : "una sola vez");
            // Un desafio sin mazo no se puede jugar, y eso no se ve hasta que
            // le das a JUGAR y la partida no arranca.
            ok &= c.getEventDeck() != null && !c.getEventDeck().getMain().isEmpty();
        }

        // Y jugarlo de verdad, que es lo unico que demuestra que el camino
        // comun con los duelos funciona. Se amanya para ganar: al rival se le
        // deja un mazo minusculo y se queda sin biblioteca.
        if (!open.isEmpty()) {
            final forge.gamemodes.quest.QuestEventChallenge c = open.get(0);
            c.setEventDeck(NeoQuest.tinyCopyOf(c.getEventDeck()));
            final int playedBefore = NeoQuest.challengesPlayed();
            final long creditsBefore = NeoQuest.credits();
            System.out.printf(Locale.ROOT, "  Jugando el desafio \"%s\"...%n", c.getTitle());
            final NeoQuestRewards rewards = NeoQuestMatch.play(c, null, true,
                    forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY);
            System.out.printf(Locale.ROOT,
                    "  Resultado: desafios jugados %d -> %d | creditos %d -> %d%n",
                    playedBefore, NeoQuest.challengesPlayed(), creditsBefore, NeoQuest.credits());
            for (final String line : rewards.getMessages()) {
                System.out.println("    " + line);
            }
            // Lo que de verdad prueba que el motor lo ha tratado COMO desafio y
            // no como un duelo cualquiera: el contador de desafios jugados.
            ok &= NeoQuest.challengesPlayed() > playedBefore;
        }

        System.out.println(ok ? "  OK" : "  FALLO");
        NeoQuest.delete(name);
        return ok;
    }

    /**
     * El bazar, sin ventana.
     *
     * <p>Lo que se comprueba no es que la lista se lea — eso se ve en la
     * captura — sino que <b>comprar hace efecto</b>, que es justo lo que una
     * captura no puede ver. El bazar toca cosas que estan lejos: las vidas de
     * partida, cada cuantas victorias llega un desafio, lo que te pagan al
     * vender. Si una de esas dejara de moverse, la pantalla seguiria
     * ensenyandolo todo perfecto.
     */
    private static boolean checkBazaar() {
        System.out.println("  --- Bazar ---");
        final String name = "neo-questcheck-bazaar";
        NeoQuest.delete(name);
        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.COMMANDER);
        NeoQuest.start(name, NeoQuest.Modalidad.COMMANDER, NeoQuest.Dificultad.NORMAL,
                starters.isEmpty() ? null : starters.get(0));
        NeoQuest.engine().getAssets().addCredits(100000);

        boolean ok = true;
        final java.util.List<forge.neo.quest.NeoQuestBazaar.Stall> stalls =
                forge.neo.quest.NeoQuestBazaar.stalls();
        System.out.printf(Locale.ROOT, "  Puestos: %d%n", stalls.size());
        ok &= stalls.size() >= 5;

        int items = 0;
        for (final forge.neo.quest.NeoQuestBazaar.Stall s : stalls) {
            System.out.printf(Locale.ROOT, "      %s (%d a la venta)%n",
                    s.getTitle(), s.getItems().size());
            items += s.getItems().size();
            // Por su nombre DE VENTA, que no es el interno: "Map" se vende
            // como "Adventurer's Map". Buscarlo por el interno no lo encuentra
            // y la comprobacion se saltaria sin decir nada.
            for (final forge.gamemodes.quest.bazaar.IQuestBazaarItem it : s.getItems()) {
                System.out.printf(Locale.ROOT, "          - %-28s %d cr.%n",
                        it.getPurchaseName(), forge.neo.quest.NeoQuestBazaar.priceOf(it));
            }
            // Un puesto sin nombre ni ambientacion es un puesto que no se ha
            // leido bien del XML.
            ok &= s.getTitle() != null && !s.getTitle().isBlank();
        }
        System.out.printf(Locale.ROOT, "  Objetos a la venta hoy: %d%n", items);
        ok &= items >= 10;

        // El Elixir sube las vidas de partida. Es el efecto mas facil de
        // comprobar y el mas facil de romper sin enterarse.
        final int lifeBefore = NeoQuest.life();
        final forge.gamemodes.quest.bazaar.IQuestBazaarItem elixir =
                findItem(stalls, "Elixir of Life");
        if (elixir != null) {
            final long before = NeoQuest.credits();
            final boolean bought = forge.neo.quest.NeoQuestBazaar.buy(elixir);
            System.out.printf(Locale.ROOT,
                    "  Elixir of Life: comprado %s | creditos %d -> %d | vidas %d -> %d%n",
                    bought, before, NeoQuest.credits(), lifeBefore, NeoQuest.life());
            ok &= bought && NeoQuest.life() == lifeBefore + 1;
        } else {
            System.out.println("  FALLA: no esta el Elixir of Life en ningun puesto");
            ok = false;
        }

        // El mapa acorta la espera de los desafios, y esa cuenta la hace el
        // motor en getTurnsToUnlockChallenge(). Si esto se rompiera, la
        // pantalla de desafios diria mal cuantas victorias faltan.
        final int everyBefore = NeoQuest.winsPerChallenge();
        final forge.gamemodes.quest.bazaar.IQuestBazaarItem map =
                findItem(forge.neo.quest.NeoQuestBazaar.stalls(), "Adventurer's Map");
        if (map == null) {
            System.out.println("  FALLA: no esta el mapa en ningun puesto");
            ok = false;
        } else {
            forge.neo.quest.NeoQuestBazaar.buy(map);
            System.out.printf(Locale.ROOT,
                    "  Map: un desafio cada %d victorias -> cada %d%n",
                    everyBefore, NeoQuest.winsPerChallenge());
            ok &= NeoQuest.winsPerChallenge() < everyBefore;
        }

        // Lo comprado desaparece del mostrador: es como el motor dice "ya lo
        // tienes", y si no pasara se podria comprar dos veces.
        final boolean stillThere =
                findItem(forge.neo.quest.NeoQuestBazaar.stalls(), "Adventurer's Map") != null;
        System.out.printf(Locale.ROOT, "  Y el mapa %s%n",
                stillThere ? "SIGUE A LA VENTA (mal)" : "ya no se vende (bien)");
        ok &= !stillThere;

        // Una mascota: comprarla no basta, hay que ponersela. Son dos cosas y
        // la segunda se olvida — sin selectPet la mascota esta pagada y no sale
        // a la mesa.
        final forge.gamemodes.quest.bazaar.IQuestBazaarItem pet =
                findItem(forge.neo.quest.NeoQuestBazaar.stalls(), "Hound");
        if (pet != null) {
            forge.neo.quest.NeoQuestBazaar.buy(pet);
            // Los animales van en el hueco 1; el 0 es de la planta. Lo dice
            // index.xml y no es evidente: buscar el perro en el hueco 0 no lo
            // encuentra y parece que la compra no ha hecho nada.
            final java.util.List<forge.gamemodes.quest.bazaar.QuestPetController> owned =
                    forge.neo.quest.NeoQuestBazaar.ownedPets(1);
            System.out.printf(Locale.ROOT, "  Mascotas tuyas en el hueco 2: %d%n", owned.size());
            ok &= !owned.isEmpty();

            forge.neo.quest.NeoQuestBazaar.selectPet(1, "Hound");
            System.out.printf(Locale.ROOT, "  Puesta: %s%n",
                    forge.neo.quest.NeoQuestBazaar.selectedPet(1));
            ok &= "Hound".equals(forge.neo.quest.NeoQuestBazaar.selectedPet(1));

            // Y quitarsela tiene que poder hacerse.
            forge.neo.quest.NeoQuestBazaar.selectPet(1, null);
            ok &= forge.neo.quest.NeoQuestBazaar.selectedPet(1) == null;
        }

        // ---- los mundos ----
        //
        // Un mundo cambia CON QUE se juega: sus expansiones, sus rivales y sus
        // desafios. Lo que hay que blindar es que viajar cambie las TRES cosas:
        // setWorld solo apunta el nombre, y sin resetDuelsManager() /
        // resetChallengesManager() sigues peleando con los rivales del mundo
        // anterior mientras la pantalla ya dice el nombre del nuevo.
        final java.util.List<forge.gamemodes.quest.QuestWorld> worlds =
                forge.neo.quest.NeoQuestWorlds.all();
        System.out.printf(Locale.ROOT, "  Mundos: %d | estas en %s%n", worlds.size(),
                forge.neo.quest.NeoQuestWorlds.current() == null ? "(ninguno)"
                        : forge.neo.quest.NeoQuestWorlds.current().getName());
        ok &= worlds.size() >= 20;

        forge.gamemodes.quest.QuestWorld target = null;
        for (final forge.gamemodes.quest.QuestWorld w : worlds) {
            // Uno con expansiones de verdad, no un "aleatorio": los de rotacion
            // no publican su lista sin partida en curso.
            if (!forge.neo.quest.NeoQuestWorlds.describe(w).isEmpty()) {
                target = w;
                break;
            }
        }
        if (target == null) {
            System.out.println("  FALLA: ningun mundo dice sus expansiones");
            ok = false;
        } else {
            final int duelsBefore = NeoQuest.duels().size();
            final boolean travelled = forge.neo.quest.NeoQuestWorlds.travelTo(target);
            System.out.printf(Locale.ROOT, "  Viaje a %s (%s): %s | rivales %d -> %d%n",
                    target.getName(), forge.neo.quest.NeoQuestWorlds.describe(target),
                    travelled, duelsBefore, NeoQuest.duels().size());
            ok &= travelled
                    && forge.neo.quest.NeoQuestWorlds.current() != null
                    && target.getName().equals(
                            forge.neo.quest.NeoQuestWorlds.current().getName());
            // Y viajar al mismo sitio no hace nada, que es lo que evita
            // regenerar rivales por clicar dos veces.
            ok &= !forge.neo.quest.NeoQuestWorlds.travelTo(target);
        }

        // Y todo esto sobrevive a guardar y recargar, que es donde se pierde
        // lo que no se apunta.
        NeoQuest.save();
        final int life = NeoQuest.life();
        NeoQuest.load(name);
        System.out.printf(Locale.ROOT, "  Tras recargar: %d vidas, un desafio cada %d, en %s%n",
                NeoQuest.life(), NeoQuest.winsPerChallenge(),
                forge.neo.quest.NeoQuestWorlds.current() == null ? "(ninguno)"
                        : forge.neo.quest.NeoQuestWorlds.current().getName());
        ok &= NeoQuest.life() == life;
        // El mundo es parte de la partida: si se perdiera al guardar, el
        // jugador viaja, sale y vuelve a estar donde estaba.
        ok &= target == null || (forge.neo.quest.NeoQuestWorlds.current() != null
                && target.getName().equals(forge.neo.quest.NeoQuestWorlds.current().getName()));

        System.out.println(ok ? "  OK" : "  FALLO");
        NeoQuest.delete(name);
        return ok;
    }

    /** Busca un objeto del bazar por su nombre, mire en el puesto que mire. */
    private static forge.gamemodes.quest.bazaar.IQuestBazaarItem findItem(
            final java.util.List<forge.neo.quest.NeoQuestBazaar.Stall> stalls,
            final String name) {
        for (final forge.neo.quest.NeoQuestBazaar.Stall s : stalls) {
            for (final forge.gamemodes.quest.bazaar.IQuestBazaarItem item : s.getItems()) {
                if (name.equalsIgnoreCase(item.getPurchaseName())) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Una carta de tu coleccion que exista en dos impresiones o mas.
     *
     * <p>Hace falta para probar el arte: sin dos artes no hay nada que
     * comparar. Se busca en la coleccion y no en la base entera para que la
     * carta sea manipulable con {@code addSingleCard} sin inventarse nada.
     */
    private static PaperCard pickWithSeveralPrintings() {
        for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            final PaperCard c = e.getKey();
            if (c != null && forge.model.FModel.getMagicDb()
                    .getCommonCards().getAllCards(c.getName()).size() >= 2) {
                return c;
            }
        }
        return null;
    }

    private static boolean sameCards(final List<PaperCard> a, final List<PaperCard> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getName().equals(b.get(i).getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Juega un duelo de verdad y comprueba que el motor cobra.
     *
     * <p>Lo que se verifica no es quien gana — da igual — sino que la partida
     * termina, que {@code QuestWinLoseController} corre, y que el marcador se
     * mueve. Si esto funciona, la aventura progresa.
     */
    private static boolean playOneDuel(final boolean rigWin) {
        final List<QuestEventDuel> duels = NeoQuest.duels();
        if (duels.isEmpty()) {
            System.out.println("  FALLO: no hay duelo que jugar");
            return false;
        }
        final QuestEventDuel duel = duels.get(0);
        if (rigWin) {
            // El piloto automatico no juega nada, asi que pierde siempre y el
            // camino de VICTORIA — creditos, subir de nivel, el sobre de
            // premio — se quedaria sin probar. Se le da al rival un mazo de 20
            // montanyas: se queda sin biblioteca antes que tu y pierde.
            duel.setEventDeck(NeoQuest.tinyCopyOf(duel.getEventDeck()));
        }
        final int winsBefore = NeoQuest.wins();
        final int lossesBefore = NeoQuest.losses();
        final long creditsBefore = NeoQuest.credits();
        final long t0 = System.currentTimeMillis();

        System.out.printf(Locale.ROOT, "  Jugando un duelo contra %s (%s)...%n",
                duel.getTitle(), rigWin ? "amanyado para ganar" : "a las malas");
        final NeoQuestRewards rewards = NeoQuestMatch.play(duel, null, true,
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY);

        final int winsAfter = NeoQuest.wins();
        final int lossesAfter = NeoQuest.losses();
        System.out.printf(Locale.ROOT,
                "  Resultado: %d-%d -> %d-%d | creditos %d -> %d | %ds%n",
                winsBefore, lossesBefore, winsAfter, lossesAfter,
                creditsBefore, NeoQuest.credits(), (System.currentTimeMillis() - t0) / 1000);
        for (final String line : rewards.getMessages()) {
            System.out.println("    " + line);
        }
        if (!rewards.getCards().isEmpty()) {
            System.out.printf(Locale.ROOT, "    y %d cartas nuevas%n", rewards.getCards().size());
        }

        final boolean moved = rigWin ? winsAfter > winsBefore : lossesAfter > lossesBefore;
        if (!moved) {
            System.out.println("  FALLO: el marcador no ha registrado el resultado");
        }
        return moved;
    }

    private static boolean checkOne(final NeoQuest.Modalidad modalidad) {
        final String name = "neo-questcheck-" + modalidad.name().toLowerCase(Locale.ROOT);
        NeoQuest.delete(name);

        System.out.printf(Locale.ROOT, "  --- Aventura de %s ---%n", modalidad.getLabel());

        // Empezar con un PRECONSTRUIDO: un mazo de verdad, jugable y flojo, que
        // es lo que se pidio. Sin esto empiezas con cartas sueltas y sin mazo.
        final List<forge.deck.Deck> starters = NeoQuest.starterDecks(modalidad);
        System.out.printf(Locale.ROOT, "  Preconstruidos disponibles: %d%n", starters.size());
        final forge.deck.Deck starter = starters.isEmpty() ? null : starters.get(0);
        NeoQuest.start(name, modalidad, NeoQuest.Dificultad.NORMAL, starter);
        if (starter != null) {
            System.out.printf(Locale.ROOT, "  Empiezas con: %s%n", starter.getName());
        }

        boolean ok = NeoQuest.isActive() && name.equals(NeoQuest.name());
        ok &= NeoQuest.modalidad() == modalidad;

        final long credits = NeoQuest.credits();
        final int collection = NeoQuest.collectionSize();
        System.out.printf(Locale.ROOT, "  Empiezas con %d creditos y %d cartas | vidas: %d%n",
                credits, collection, NeoQuest.life());
        ok &= credits > 0 && collection > 0;

        // En Commander la aventura entera se juega a 40 vidas. Lo pone el motor
        // a partir de DeckConstructionRules, no nosotros.
        if (modalidad == NeoQuest.Modalidad.COMMANDER) {
            ok &= NeoQuest.engine().getDeckConstructionRules()
                    == forge.gamemodes.quest.data.DeckConstructionRules.Commander;
        }

        // ---- el mazo con el que empiezas tiene que ser JUGABLE ----
        final forge.deck.Deck mine = NeoQuest.currentDeck();
        final String problem = NeoQuest.problemWith(mine);
        System.out.printf(Locale.ROOT, "  Tu mazo: %s -> %s%n",
                mine == null ? "(ninguno)" : mine.getName() + " (" + mine.getMain().countAll() + " cartas)",
                problem == null ? "legal" : problem);
        ok &= mine != null && problem == null;

        // ---- LO IMPORTANTE: con que rival te encuentras al empezar ----
        final List<QuestEventDuel> duels = NeoQuest.engine().getDuelsManager().generateDuels();
        System.out.printf(Locale.ROOT, "  Con 0 victorias: rivales %s, %d duelos disponibles%n",
                NeoQuest.tierLabel(), duels == null ? 0 : duels.size());
        for (final QuestEventDuel d : duels == null ? List.<QuestEventDuel>of() : duels) {
            System.out.printf(Locale.ROOT, "    %-28s %s%n", d.getTitle(), d.getDifficulty());
        }
        ok &= NeoQuest.tier() == forge.gamemodes.quest.QuestEventDifficulty.EASY;
        ok &= duels != null && !duels.isEmpty();

        // ---- y que suben cuando ganas ----
        final int needed = NeoQuest.winsToNextTier();
        System.out.printf(Locale.ROOT, "  Faltan %d victorias para que suban de nivel%n", needed);
        ok &= needed > 0;

        for (int i = 0; i < needed; i++) {
            FModel.getQuest().getAchievements().addWin();
        }
        System.out.printf(Locale.ROOT, "  Con %d victorias: rivales %s (nivel %d, %s)%n",
                NeoQuest.wins(), NeoQuest.tierLabel(), NeoQuest.level(), NeoQuest.rank());
        ok &= NeoQuest.tier() != forge.gamemodes.quest.QuestEventDifficulty.EASY;

        // ---- EL BUCLE ENTERO: jugar un duelo y cobrar ----
        //
        // Es lo unico que de verdad cierra el modo. Se juega en modo automatico
        // (la interfaz contesta sola) para no depender de una partida a mano.
        if (!Boolean.getBoolean("neo.questcheck.skipDuel")) {
            ok &= playOneDuel(false);   // perdiendo
            ok &= playOneDuel(true);    // y ganando
        }

        // ---- guardar y volver a cargar: el progreso tiene que sobrevivir ----
        NeoQuest.save();
        final int winsBefore = NeoQuest.wins();
        final long creditsBefore = NeoQuest.credits();
        final boolean loaded = NeoQuest.load(name);
        ok &= loaded && NeoQuest.wins() == winsBefore && NeoQuest.credits() == creditsBefore;
        System.out.printf(Locale.ROOT, "  Guardado y recargado: %d victorias, %d creditos%n",
                NeoQuest.wins(), NeoQuest.credits());

        // ---- y se puede tirar para empezar otra ----
        NeoQuest.delete(name);
        ok &= !NeoQuest.saves().contains(name);

        System.out.println(ok ? "  OK" : "  FALLO");
        return ok;
    }
}
