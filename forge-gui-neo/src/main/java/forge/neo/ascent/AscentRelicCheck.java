package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

/**
 * Una partida por reliquia, comprobando que el <b>efecto</b> ocurre.
 *
 * <h2>Por que hace falta, y por que no basta con lo que ya hay</h2>
 *
 * <p>El dialecto de scripts de Forge <b>no avisa cuando algo esta mal</b>: una
 * carta con un {@code SVar} inventado no da error, no lanza excepcion y no
 * imprime nada — simplemente <b>no hace nada</b>, para siempre y en silencio.
 * Y no es teorico: paso con <i>Banner of Legions</i>, que llevaba un
 * {@code Defined$ TriggeredNewCardLKICopy} que no existe.
 *
 * <p>{@link AscentCheck} cubre la mitad barata: que las 37 se registren, que
 * ninguna colisione con una carta de Magic de verdad y que todas lleven su
 * clave de zona ({@code EffectZone$ Command} / {@code TriggerZones$ Command}),
 * que es el fallo mudo mas comun. {@link AscentProbe} juega <b>una</b> partida
 * con <b>una</b> reliquia. Lo que faltaba era esto: <b>las 37, jugadas</b>.
 *
 * <h2>Como se comprueba, y por que no hay 37 predicados escritos a mano</h2>
 *
 * <p>Las 37 caen en muy pocas familias — pega a tus criaturas, les da una
 * palabra clave, te da vida, te hace robar, te da mana, pone un contador, o se
 * exilia sola cuando bajas de vida. Asi que cada reliquia declara <b>que hay
 * que ver</b> y hay un solo predicado que lo mira. Escribir 37 predicados a
 * mano seria 37 sitios donde equivocarse, y sobre todo haria caro anyadir la
 * numero 38 — que es justo lo que este comprobador tiene que abaratar.
 *
 * <h2>Tres decisiones que no se deducen leyendo el codigo</h2>
 *
 * <ol>
 *   <li><b>El humano se retira en cuanto se ve el efecto</b>
 *       ({@code stopOnHit}). Sin eso cada partida corre hasta que termina sola
 *       o hasta el timeout de 60 s: 37 minutos de reloj para comprobar cosas
 *       que se ven en el turno 1.</li>
 *   <li><b>Robar "una carta" se mide pidiendo DOS.</b> El paso de robo normal
 *       ya roba una, asi que {@code >= 1} daria verde con la reliquia rota. Lo
 *       que se exige es la de la reliquia <b>mas</b> la del turno.</li>
 *   <li><b>La vida se mira por dos caminos</b> — lo ganado este turno y el
 *       total por encima del de salida — porque el primero se borra cada turno
 *       y el segundo lo puede tapar un ataque de la IA. Con los dos, un verde
 *       es verde y un rojo no es una casualidad.</li>
 * </ol>
 *
 * <p>Sin JavaFX, como todo lo del modo: {@code run.cmd reliccheck}.
 */
public final class AscentRelicCheck {

    private AscentRelicCheck() {
    }

    private static int passed;
    private static int failed;

    /** La vida con la que se juega cuando la reliquia no pide otra cosa. */
    private static final int COMMANDER_LIFE = 40;

    // ------------------------------------------------------------------
    //  Que hay que ver en cada una
    // ------------------------------------------------------------------

    /** Lo que se declara de una reliquia para poder comprobarla. */
    private static final class Spec {
        private final String id;
        private int power;
        private int toughness;
        private final List<String> keywords = new ArrayList<>();
        private String playerKeyword;
        private int life;
        private int draw;
        private int mana;
        private int counter;
        private boolean selfExiled;
        private int startingLife = -1;
        private final List<String> board = new ArrayList<>();
        private boolean byTrigger;

        Spec(final String id) {
            this.id = id;
        }

        /** Tus criaturas ganan +p/+t. */
        Spec pump(final int p, final int t) {
            power = p;
            toughness = t;
            return this;
        }

        /** Tus criaturas ganan estas palabras clave. */
        Spec kw(final String... names) {
            keywords.addAll(Arrays.asList(names));
            return this;
        }

        /** <b>Tu</b> ganas esta palabra clave (no tus criaturas). */
        Spec playerKw(final String name) {
            playerKeyword = name;
            return this;
        }

        /** Ganas n vidas. */
        Spec life(final int n) {
            life = n;
            return this;
        }

        /** Robas n cartas de mas (por encima del robo normal del turno). */
        Spec draw(final int n) {
            draw = n;
            return this;
        }

        /** Anyade n mana a tu reserva. */
        Spec mana(final int n) {
            mana = n;
            return this;
        }

        /** Pone n contadores +1/+1 sobre una criatura tuya. */
        Spec counter(final int n) {
            counter = n;
            return this;
        }

        /**
         * Cartas que hacen falta en TU mesa para que el efecto se pueda ver.
         *
         * <p>Se prueban por orden y se coge la primera que exista: una carta
         * concreta puede desaparecer del catalogo de Forge entre versiones, y
         * un comprobador que se cae porque cambiaron una carta no sirve de
         * nada.
         */
        Spec board(final String... names) {
            board.addAll(Arrays.asList(names));
            return this;
        }

        /**
         * Vale con que el <b>disparo se resuelva</b> si no se puede ver el
         * efecto.
         *
         * <p>Es menos de lo que la reliquia promete y por eso se declara a
         * mano, una por una, en vez de dejarlo como red general: aceptarlo en
         * todas convertiria este comprobador en "los disparos van", que es
         * bastante menos que "hacen lo que dicen". Hoy lo llevan las que no
         * dejan rastro: el mana (la reserva se vacia al acabar la fase y nadie
         * apunta cuanta habia) y el robo por muerte de criatura (la unica
         * criatura que muere sola lo hace en el turno 1, y ahi el robo del
         * disparo no se distingue del robo del turno).
         *
         * <p>Sigue cazando el fallo que importa: un script roto <b>no dispara
         * nada</b> — es lo que pasaba con <i>Banner of Legions</i>.
         */
        Spec byTrigger() {
            byTrigger = true;
            return this;
        }

        /** Se exilia a si misma al dispararse (el "segundo aliento" del jefe). */
        Spec selfExiled(final int withLife) {
            selfExiled = true;
            startingLife = withLife;
            return this;
        }

        /** Que se ve, para poder decirlo cuando falle. */
        String what() {
            final List<String> bits = new ArrayList<>();
            if (power != 0 || toughness != 0) {
                bits.add(String.format(Locale.ROOT, "%+d/%+d", power, toughness));
            }
            bits.addAll(keywords);
            if (playerKeyword != null) {
                bits.add("tu: " + playerKeyword);
            }
            if (life > 0) {
                bits.add("+" + life + " vidas");
            }
            if (draw > 0) {
                bits.add("robar " + draw);
            }
            if (mana > 0) {
                bits.add(mana + " mana");
            }
            if (counter > 0) {
                bits.add(counter + " contador +1/+1");
            }
            if (selfExiled) {
                bits.add("se exilia sola");
            }
            return String.join(", ", bits);
        }
    }

    /**
     * Las 37, con lo que hay que ver en cada una.
     *
     * <p>Se declara a mano y no se lee del script a proposito: si esto sacara
     * lo que hay que comprobar del propio fichero, un script mal escrito
     * generaria una comprobacion igual de mal escrita y las dos cuadrarian.
     * <b>Lo que se escribe aqui es lo que la reliquia PROMETE</b>, en el mismo
     * idioma en el que se lo prometemos al jugador.
     */
    private static Map<String, Spec> specs() {
        final Map<String, Spec> out = new LinkedHashMap<>();
        for (final Spec s : List.of(
                // --- estaticas que pegan ---
                new Spec("smiths_blessing").pump(1, 1),
                new Spec("sharpened_fang").pump(1, 0),
                new Spec("hunters_charm").pump(0, 2),
                new Spec("stoneheart_idol").pump(0, 3),
                new Spec("crown_of_ascent").pump(2, 2),
                new Spec("crown_of_the_eternal").pump(3, 3),
                new Spec("berserkers_mask").pump(2, 0).kw("Menace"),
                new Spec("ember_totem").pump(1, 0).kw("Trample"),
                new Spec("warlords_standard").pump(2, 1).kw("Haste"),

                // --- estaticas de palabra clave ---
                new Spec("aegis_eternal").kw("Indestructible"),
                new Spec("serpent_coil").kw("Deathtouch"),
                new Spec("sunlit_aegis").kw("Lifelink"),
                new Spec("swiftfoot_anklet").kw("Haste"),
                new Spec("whetstone_sigil").kw("Vigilance"),
                new Spec("titans_grasp").kw("Trample", "Haste"),
                new Spec("windrider_cloak").kw("Flying", "Vigilance"),
                new Spec("wings_of_the_ascended").kw("Flying", "Trample", "Haste"),

                // --- estatica sobre TI, no sobre tus criaturas ---
                new Spec("pilgrims_ward").playerKw("Hexproof"),

                // --- vida ---
                new Spec("pilgrims_chalice").life(1),
                new Spec("warden_seal").life(2),
                new Spec("phoenix_heart").life(3),
                new Spec("chalice_of_ages").life(4),
                new Spec("lucky_coin").life(4),

                // --- robar ---
                new Spec("the_infinite_tome").draw(1),
                new Spec("wanderers_compass").draw(1),
                new Spec("oracle_lens").draw(2),
                new Spec("hourglass_of_kings").draw(3),
                // Necesita que una criatura TUYA muera, y el humano de la
                // sonda no ataca ni bloquea. Ball Lightning se sacrifica sola
                // al final de cada turno, sin que nadie decida nada: es la
                // unica forma de provocar una muerte a voluntad.
                new Spec("font_of_souls").draw(1).byTrigger()
                        .board("Ball Lightning", "Blistering Firecat",
                                "Spark Elemental", "Groundbreaker"),

                // --- mana ---
                new Spec("copper_ring").mana(1).byTrigger(),
                new Spec("wellspring_stone").mana(1).byTrigger(),
                new Spec("ascendant_geode").mana(2).byTrigger(),
                new Spec("heart_of_the_mountain").mana(3).byTrigger(),

                // --- contadores ---
                // Necesita que una criatura ENTRE, y las que se ponen en la
                // mesa al empezar no disparan entradas. Bitterblossom crea un
                // Hada en cada mantenimiento y NO es opcional, asi que la
                // entrada llega sola.
                new Spec("banner_of_legions").counter(1)
                        .board("Bitterblossom", "Ophiomancer", "Verdant Force"),

                // --- adivinar: no se puede ver desde fuera (ver abajo) ---
                new Spec("scouts_map"),
                new Spec("chronicle_page"),

                // --- el segundo aliento del jefe (Ascension 10) ---
                new Spec("cornered_fury").pump(2, 2).selfExiled(15),
                new Spec("tyrants_last_stand").pump(2, 2).selfExiled(30))) {
            out.put(s.id, s);
        }
        return out;
    }

    // ------------------------------------------------------------------

    public static void run() {
        run(null);
    }

    /**
     * @param solo si no es {@code null}, solo esa reliquia. Es lo que hace que
     *             arreglar una no cueste dos minutos de reloj:
     *             {@code run.cmd reliccheck --solo=copper_ring}
     */
    public static void run(final String solo) {
        passed = 0;
        failed = 0;

        AscentRelics.install();
        final Map<String, Spec> specs = specs();

        // 1. Que la tabla y el catalogo digan lo mismo. Si alguien anyade una
        //    reliquia y no la declara aqui, se quedaria sin comprobar — y ese
        //    es exactamente el agujero que este comprobador viene a tapar.
        final List<String> sinSpec = new ArrayList<>();
        for (final AscentRelic r : AscentRelics.all()) {
            if (!specs.containsKey(r.getId())) {
                sinSpec.add(r.getId());
            }
        }
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            final PaperCard boss = AscentRelics.bossPhaseCard(mode);
            if (boss != null && !specs.containsKey(idOfBossPhase(mode))) {
                sinSpec.add(idOfBossPhase(mode));
            }
        }
        if (sinSpec.isEmpty()) {
            ok("todas las reliquias del catalogo estan declaradas aqui ("
                    + AscentRelics.all().size() + " + 2 del jefe)");
        } else {
            fail("sin declarar, o sea sin comprobar: " + String.join(", ", sinSpec));
        }

        // 2. Y una partida por cada una.
        final Deck deck = AscentProbe.runDeck();
        if (deck == null) {
            fail("no se ha podido montar el mazo de sonda");
            resumen();
            return;
        }
        for (final Spec spec : specs.values()) {
            if (solo != null && !solo.equals(spec.id)) {
                continue;
            }
            probar(spec, deck);
        }
        resumen();
    }

    /** El id del script del segundo aliento de ese modo. */
    private static String idOfBossPhase(final AscentRun.Mode mode) {
        return mode == AscentRun.Mode.STANDARD ? "cornered_fury" : "tyrants_last_stand";
    }

    /** La carta de una reliquia, sea del catalogo o del segundo aliento del jefe. */
    private static PaperCard cardOf(final String id) {
        final AscentRelic relic = AscentRelics.byId(id);
        if (relic != null) {
            return AscentRelics.cardOf(relic);
        }
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            if (idOfBossPhase(mode).equals(id)) {
                return AscentRelics.bossPhaseCard(mode);
            }
        }
        return null;
    }

    /**
     * Una partida con esa reliquia puesta, y a mirar.
     *
     * <p>Una reliquia sin nada que ver declarado no se juega: se dice y se
     * pasa. Es el caso de <i>adivinar</i> (scry), que <b>el motor no publica</b>
     * — no hay contador de "veces que has adivinado" ni en {@code Player} ni en
     * {@code PlayerView}, y lo unico observable seria el orden de la biblioteca,
     * que tambien cambia por otros motivos. Callarselo seria peor: al menos asi
     * queda escrito que esas dos son las unicas que no se comprueban jugando.
     */
    private static void probar(final Spec spec, final Deck deck) {
        final PaperCard card = cardOf(spec.id);
        if (card == null) {
            fail(spec.id + ": no esta registrada, el motor no la encuentra");
            return;
        }
        if (spec.what().isEmpty()) {
            skip(spec.id + ": adivinar no lo publica el motor, no se puede ver desde fuera");
            return;
        }

        final int life = spec.startingLife > 0 ? spec.startingLife : -1;
        final int baseLife = spec.startingLife > 0 ? spec.startingLife : COMMANDER_LIFE;
        final String[] visto = {null};

        final List<PaperCard> board = new ArrayList<>();
        if (!spec.board.isEmpty()) {
            final PaperCard helper = firstThatExists(spec.board);
            if (helper == null) {
                fail(spec.id + ": no existe ninguna de las cartas que hacen falta para verla ("
                        + String.join(", ", spec.board) + ")");
                return;
            }
            board.add(helper);
        }

        // El mana aparece y desaparece dentro de una fase: a 100 ms no se pilla
        // nunca (medido, 0 de 4). Lo demas no corre ninguna prisa.
        final long poll = spec.mana > 0 ? 5L : 100L;

        final AscentProbe.Result r = AscentProbe.play("reliquia-" + spec.id,
                EnumSet.of(GameType.Commander), deck, life, null, List.of(card), board,
                true, poll, game -> cumple(game, spec, card.getName(), baseLife, visto));

        if (r.reached) {
            ok(spec.id + " (" + spec.what() + "): " + visto[0]);
        } else {
            fail(spec.id + " (" + spec.what() + ") NO hace lo que dice: " + r.why
                    + " — ¿script mal escrito? Forge NO avisa de eso");
        }
    }

    // ------------------------------------------------------------------
    //  El unico predicado
    // ------------------------------------------------------------------

    private static boolean cumple(final Game game, final Spec spec, final String cardName,
                                  final int baseLife, final String[] visto) {
        final Player me = mine(game);
        if (me == null) {
            return false;
        }

        // La reliquia tiene que estar en el mando... salvo la que se exilia
        // sola, que precisamente demuestra que funciono al NO estar.
        if (!spec.selfExiled && !inCommandZone(me, cardName)) {
            return false;
        }

        if (spec.playerKeyword != null) {
            if (!me.hasKeyword(spec.playerKeyword)) {
                return false;
            }
            visto[0] = "tienes " + spec.playerKeyword;
            return true;
        }

        if (spec.life > 0) {
            // Dos caminos, y con uno basta: lo ganado ESTE turno (que se borra
            // en cada turno, asi que hay que pillarlo al vuelo) y el total por
            // encima del de salida (que un ataque de la IA puede tapar).
            if (me.getLifeGainedThisTurn() >= spec.life) {
                visto[0] = "has ganado " + me.getLifeGainedThisTurn() + " vidas este turno";
                return true;
            }
            if (me.getLife() >= baseLife + spec.life) {
                visto[0] = "vidas " + baseLife + " -> " + me.getLife();
                return true;
            }
            return false;
        }

        if (spec.draw > 0) {
            // +1 por el robo normal del turno: pedir solo spec.draw daria verde
            // con la reliquia rota, porque esa carta la roba el turno de todas
            // formas.
            final int need = spec.draw + 1;
            if (me.getNumDrawnThisTurn() >= need) {
                visto[0] = "has robado " + me.getNumDrawnThisTurn() + " este turno";
                return true;
            }
            return byTrigger(game, spec, cardName, visto);
        }

        if (spec.mana > 0) {
            final int pool = me.getManaPool().totalMana();
            if (pool >= spec.mana) {
                visto[0] = "reserva de mana: " + pool;
                return true;
            }
            return byTrigger(game, spec, cardName, visto);
        }

        if (spec.counter > 0) {
            for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                if (c.isCreature() && c.getCounters(CounterEnumType.P1P1) >= spec.counter) {
                    visto[0] = c.getName() + " con "
                            + c.getCounters(CounterEnumType.P1P1) + " contador(es)";
                    return true;
                }
            }
            return false;
        }

        // Lo que queda mira una criatura tuya: fuerza, resistencia y palabras
        // clave, todo sobre la MISMA — si se mirara cada cosa sobre cualquiera,
        // dos reliquias a medias pasarian por una entera.
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if (!c.isCreature()) {
                continue;
            }
            final int baseP = c.getCurrentState().getBasePower();
            final int baseT = c.getCurrentState().getBaseToughness();
            if (c.getNetPower() < baseP + spec.power
                    || c.getNetToughness() < baseT + spec.toughness) {
                continue;
            }
            boolean todas = true;
            for (final String kw : spec.keywords) {
                if (!c.hasKeyword(kw)) {
                    todas = false;
                    break;
                }
            }
            if (!todas) {
                continue;
            }
            if (spec.selfExiled) {
                // El segundo aliento pega Y se va. Que se haya ido es la
                // prueba de que el disparo se resolvio entero: si solo se
                // mirara el +2/+2, un SubAbility roto pasaria desapercibido.
                if (inCommandZone(me, cardName) || !inExile(me, cardName)) {
                    continue;
                }
            }
            visto[0] = c.getName() + " " + baseP + "/" + baseT
                    + " -> " + c.getNetPower() + "/" + c.getNetToughness()
                    + (spec.keywords.isEmpty() ? "" : " con " + String.join(", ", spec.keywords))
                    + (spec.selfExiled ? " y la carta ya esta en el exilio" : "");
            return true;
        }
        return false;
    }

    /** La red de la que habla {@link Spec#byTrigger()}, y solo para las que la piden. */
    private static boolean byTrigger(final Game game, final Spec spec, final String cardName,
                                     final String[] visto) {
        if (!spec.byTrigger || !triggerResolved(game, cardName)) {
            return false;
        }
        visto[0] = "el disparo se resuelve (el efecto en si no deja rastro que mirar)";
        return true;
    }

    /**
     * Si el registro de la partida dice que un disparo de esa carta llego a la
     * pila.
     *
     * <p>Es lo unico duradero que deja un disparo de mana: el {@code MagicStack}
     * apunta cada cosa que entra, con su carta origen. La reserva en si no deja
     * rastro.
     */
    private static boolean triggerResolved(final Game game, final String cardName) {
        for (final forge.game.GameLogEntry e : game.getGameLog().getLogEntries(null)) {
            if (e.sourceCard() != null && cardName.equals(e.sourceCard().getName())) {
                return true;
            }
            if (e.message() != null && e.message().contains(cardName)) {
                return true;
            }
        }
        return false;
    }

    /** La primera de esas cartas que exista de verdad en el motor. */
    private static PaperCard firstThatExists(final List<String> names) {
        for (final String n : names) {
            final PaperCard c = forge.model.FModel.getMagicDb().getCommonCards().getCard(n);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static boolean inCommandZone(final Player me, final String name) {
        for (final Card c : me.getCardsIn(ZoneType.Command)) {
            if (name.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inExile(final Player me, final String name) {
        for (final Card c : me.getGame().getCardsIn(ZoneType.Exile)) {
            if (name.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /** El jugador humano de la sonda. */
    private static Player mine(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.getController().isAI()) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------

    private static void resumen() {
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " reliquia(s) no hacen lo que dicen");
        }
    }

    private static void ok(final String msg) {
        passed++;
        System.out.println("  [ok]   " + msg);
    }

    private static void skip(final String msg) {
        System.out.println("  [--]   " + msg);
    }

    private static void fail(final String msg) {
        failed++;
        System.out.println("  [MAL]  " + msg);
    }
}
