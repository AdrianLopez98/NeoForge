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
        private final List<List<String>> board = new ArrayList<>();
        private boolean byTrigger;
        private boolean once;
        private int tokens;
        private int oppLife;
        private int blockExtra;
        private String cannotSee;

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
            // Cada llamada es UNA ranura; los nombres de dentro son
            // alternativas de esa ranura. Llamarlo dos veces pone dos cartas —
            // que es como se consiguen las 6 vidas por turno que necesita
            // seraphs_accord, o los 3 molinos por turno de charnel_mound.
            board.add(Arrays.asList(names));
            return this;
        }

        /** Tienes n fichas de criatura en la mesa. */
        Spec token(final int n) {
            tokens = n;
            return this;
        }

        /** El rival ha perdido n vidas (y no por un ataque tuyo: la sonda no ataca). */
        Spec oppLife(final int n) {
            oppLife = n;
            return this;
        }

        /** Tus criaturas pueden bloquear n criaturas de mas. */
        Spec blockExtra(final int n) {
            blockExtra = n;
            return this;
        }

        /**
         * No se puede ver jugando, y <b>por que</b>.
         *
         * <p>Se declara a mano y con el motivo escrito, como las dos de
         * adivinar. Callarselo seria peor: asi queda negro sobre blanco cuales
         * son las unicas que no estan cubiertas, y que no es por olvido.
         */
        Spec cannotSee(final String why) {
            cannotSee = why;
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

        /**
         * Solo se dispara <b>una vez por partida</b>, no cada turno.
         *
         * <p>Lo llevan las que dicen <i>"at the beginning of your FIRST
         * upkeep"</i>. Hace falta declararlo porque el resto del comprobador
         * mira si el efecto <b>ocurre</b>, y se para en cuanto lo ve: una
         * reliquia que se dispara todos los turnos pasa <b>exactamente igual</b>
         * que una que se dispara una. Fallo real (22-09-2026): las cuatro de
         * "primer mantenimiento" llevaban la condicion en {@code
         * ConditionCheckSVar}, que en una linea {@code T:} <b>el motor ni
         * lee</b> — o sea sin condicion — y <i>Lucky Coin</i>, una comun,
         * curaba 4 cada turno: mas que su legendaria equivalente.
         */
        Spec once() {
            once = true;
            return this;
        }

        /** Se exilia a si misma al dispararse (el "segundo aliento" del jefe). */
        Spec selfExiled(final int withLife) {
            selfExiled = true;
            startingLife = withLife;
            return this;
        }

        /** Si lo unico que se declara es que el disparo se resuelva. */
        boolean onlyTrigger() {
            return byTrigger && power == 0 && toughness == 0 && keywords.isEmpty()
                    && playerKeyword == null && life == 0 && draw == 0 && mana == 0
                    && counter == 0 && tokens == 0 && oppLife == 0 && blockExtra == 0
                    && !selfExiled;
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
            if (once) {
                bits.add("una sola vez");
            }
            if (tokens > 0) {
                bits.add(tokens + " ficha(s)");
            }
            if (oppLife > 0) {
                bits.add("el rival pierde " + oppLife);
            }
            if (blockExtra > 0) {
                bits.add("bloquea " + blockExtra + " de mas");
            }
            if (bits.isEmpty() && byTrigger) {
                // ⚠️ Sin esto what() sale VACIA y probar() la manda al hueco de
                // adivinar: la reliquia se saltaba con un motivo que ademas era
                // mentira. Cazado el 22-09-2026 con las cinco nuevas que solo
                // declaran byTrigger.
                bits.add("el disparo se resuelve");
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
                new Spec("lucky_coin").life(4).once(),

                // --- robar ---
                new Spec("the_infinite_tome").draw(1),
                new Spec("wanderers_compass").draw(1).once(),
                new Spec("oracle_lens").draw(2).once(),
                new Spec("hourglass_of_kings").draw(3).once(),
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

                // ==========================================================
                //  Las de COLOR (22-09-2026)
                // ==========================================================
                //
                // Estas hacen cosas que las 35 de siempre no hacian, asi que
                // casi todas necesitan una carta en la mesa que PROVOQUE el
                // efecto: el humano de la sonda no lanza, no ataca y no
                // bloquea, o sea que nada pasa por si solo. Cada ayudante esta
                // elegido por ser automatico y no pedir ninguna decision.

                // --- blancas ---
                // Entra en juego con la partida: no hace falta provocar nada.
                new Spec("recruiters_pennant").token(1),
                // Bitterblossom crea un Hada CADA mantenimiento y no es
                // opcional: es la unica forma de que entre una criatura sin
                // que nadie decida nada.
                new Spec("chalice_of_welcome").life(1)
                        .board("Bitterblossom", "Ophiomancer"),
                // Celestial Force da 3 vidas en cada mantenimiento, sola.
                new Spec("reliquary_of_dawn").token(1).board("Celestial Force"),
                new Spec("ledger_of_mercies").draw(1).board("Celestial Force"),
                // DOS Celestial Force = 6 vidas por turno, que es lo que pide
                // el umbral de 4 de esta.
                new Spec("seraphs_accord").token(1)
                        .board("Celestial Force").board("Celestial Force"),
                new Spec("heralds_laurel").kw("Renown"),
                new Spec("bulwark_pauldron").blockExtra(1),
                new Spec("shepherds_lantern").byTrigger().board("Bitterblossom"),
                // Ball Lightning se sacrifica sola al final de cada turno y
                // cuesta 3, o sea que deja en el cementerio justo lo que esta
                // reliquia sabe devolver.
                new Spec("gravebound_censer").byTrigger()
                        .board("Ball Lightning", "Blistering Firecat", "Spark Elemental"),
                // Dos Hadas comparten tipo, asi que cada una se lleva +1/+1.
                new Spec("standard_of_kin").pump(1, 1).board("Bitterblossom"),
                // El humano de la sonda no ataca, y esto pide atacar.
                new Spec("muster_horn").cannotSee(
                        "pide atacar con dos criaturas y el humano de la sonda no ataca"),

                // --- negras ---
                // Que se muera una criatura DEL RIVAL sin que nadie ataque ni
                // bloquee. The Abyss destruye una criatura del jugador ACTIVO
                // en cada mantenimiento, obligatorio y sin elegir nada: o sea
                // una criatura del rival en cada turno suyo, para siempre.
                //
                // Dos que parecian valer y NO valen: Braids deja entregar "un
                // artefacto, criatura o tierra" y la IA da una tierra; y
                // Ball Lightning + Grave Pact solo dispara UNA vez (el rayo
                // muere en el primer turno y ya no vuelve), justo cuando el
                // rival todavia no tiene criaturas que sacrificar.
                new Spec("gravecallers_tithe").life(1).board("The Abyss"),
                new Spec("widows_toll").byTrigger().board("The Abyss"),
                new Spec("rotting_hourglass").byTrigger(),
                // Cuatro Nyx Weaver = 8 cartas al cementerio en tu primer
                // mantenimiento, o sea el umbral de 7 cruzado antes del primer
                // paso final.
                //
                // ⚠️ Bloodcurdler parecia el ayudante obvio (mila 1 en cada
                // mantenimiento) y es el peor posible: al llegar a 7 cartas
                // gana "al principio de tu paso final, exilia dos cartas de tu
                // cementerio", o sea que compite con esta misma reliquia y le
                // vacia el cementerio justo al cruzar el umbral. Medido: el
                // cementerio bajaba de 6 a 2 solo.
                new Spec("charnel_mound").token(1)
                        .board("Nyx Weaver", "Splinterfright").board("Nyx Weaver", "Splinterfright")
                        .board("Nyx Weaver", "Splinterfright").board("Nyx Weaver", "Splinterfright"),
                // El rival roba cada turno, asi que esto dispara solo. Y la
                // vida que pierde es atribuible: la sonda no ataca.
                new Spec("whispering_debt").oppLife(1),
                new Spec("midnight_offering").mana(3).byTrigger(),
                new Spec("tyrants_mirror").byTrigger(),
                // Pide TAPAR un pantano para mana, y el humano de la sonda no
                // lanza nada, asi que no llega a tapar una tierra en su vida.
                new Spec("coffers_key").cannotSee(
                        "pide tapar un pantano para mana y el humano de la sonda no lanza nada"),

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

        // 3. Y AHORA, con partidas jugadas detras, mirar que ninguna reliquia
        //    se haya colado en el pozo de premios. Aqui y no en AscentCheck:
        //    ver el javadoc.
        reliquiasFueraDelPremio();
        resumen();
    }

    /**
     * Que el <b>premio de un nodo</b> no ofrezca nunca una reliquia como carta
     * de mazo.
     *
     * <h2>El fallo que viene a tapar</h2>
     *
     * <p>Reportado jugando (22-09-2026): <i>"sometimes player artifacts are
     * added as playing card and they are only discard fodder since they cant be
     * used"</i>. Y es literal: una reliquia es una carta nuestra con
     * {@code ManaCost:no cost}, o sea que <b>en la mano no se puede lanzar</b>.
     * Metida en el mazo no es una carta mala: es una carta muerta, y en un mazo
     * de 30 eso es una carta menos para el resto de la run.
     *
     * <p>Puede pasar porque el filtro vive en <b>un solo camino</b> y no es
     * este: {@code CardIndex.withoutOurCustomCards} saca las reliquias del
     * catalogo del <i>deck builder</i>, pero {@code AscentRewards} lee
     * {@code getCommonCards().getUniqueCards()} <b>en crudo</b>. El principio 8
     * al reves. Y son <b>incoloras</b>, o sea que el filtro de color del pozo
     * las deja pasar con cualquier comandante, en todas las runs.
     *
     * <h2>⚠️ Por que esto vive aqui y no en {@code AscentCheck}</h2>
     *
     * <p>Porque {@code CardDb.addCard} <b>no reindexa</b>: mete la carta en
     * {@code allCardsByName} y se va. {@code getUniqueCards()} lee otro mapa,
     * {@code uniqueCardsByRules}, que solo se rehace cuando el motor carga
     * cartas — o sea <b>cuando se juega</b>. Medido: en {@code ascentcheck},
     * que no juega ninguna partida antes, las reliquias visibles en el catalogo
     * son <b>0 de 37</b>, asi que el comprobador pasaba en verde <i>sin mirar
     * nada</i>. Es el mismo verde vacio que ya enganyo una vez en
     * {@code DeckRulesCheck}. Aqui detras van 37 partidas, que es justo la
     * condicion que hace falta — y se dice cuantas se ven, para que un verde
     * vacio no pueda volver a disfrazarse de verde.
     */
    private static void reliquiasFueraDelPremio() {
        final java.util.Set<String> nuestras = AscentRelics.allCardNames();
        int visibles = 0;
        for (final PaperCard c : forge.model.FModel.getMagicDb()
                .getCommonCards().getUniqueCards()) {
            if (nuestras.contains(c.getName())) {
                visibles++;
            }
        }
        if (visibles == 0) {
            skip("premio: el motor no tiene ninguna reliquia en el catalogo todavia,"
                    + " asi que mirar el pozo no demostraria nada");
            return;
        }

        PaperCard cmd = null;
        for (final PaperCard c : AscentSeedDeck.commanderPool()) {
            cmd = c;
            break;
        }
        if (cmd == null) {
            fail("premio: no hay comandantes en el pozo");
            return;
        }
        final AscentRun run = AscentRun.begin(AscentRun.Mode.COMMANDER, 0, 40, cmd);
        try {
            // Por los TRES actos: el pozo cambia de rareza con la altura, asi
            // que mirar solo el acto 1 dejaria sin ver dos tercios de lo que
            // una run llega a ofrecer.
            final List<String> coladas = new ArrayList<>();
            int vistas = 0;
            for (int acto = 1; acto <= 3; acto++) {
                final List<PaperCard> muestra =
                        AscentRewards.offer(run, acto, new java.util.Random(1000L + acto), 400);
                vistas += muestra.size();
                for (final PaperCard c : muestra) {
                    if (nuestras.contains(c.getName()) && !coladas.contains(c.getName())) {
                        coladas.add(c.getName());
                    }
                }
            }
            if (coladas.isEmpty()) {
                ok("premio: con las " + visibles + " reliquias visibles en el catalogo, ninguna"
                        + " se ofrece como carta de mazo (" + vistas + " cartas, tres actos)");
            } else {
                fail("premio: " + coladas.size() + " reliquia(s) se ofrecen como carta de mazo y"
                        + " no se pueden lanzar (ManaCost:no cost): "
                        + String.join(", ", coladas));
            }
        } finally {
            run.discard();
        }
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
        if (spec.cannotSee != null) {
            skip(spec.id + ": " + spec.cannotSee);
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
        for (final List<String> ranura : spec.board) {
            final PaperCard helper = firstThatExists(ranura);
            if (helper == null) {
                fail(spec.id + ": no existe ninguna de las cartas que hacen falta para verla ("
                        + String.join(", ", ranura) + ")");
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
            // Y las de "primer mantenimiento", una SEGUNDA partida para ver
            // que no vuelve a pasar. Ver Spec#once().
            if (spec.once && !soloUnaVez(spec, deck, card, life)) {
                return;
            }
            ok(spec.id + " (" + spec.what() + "): " + visto[0]);
        } else {
            fail(spec.id + " (" + spec.what() + ") NO hace lo que dice: " + r.why
                    + " — ¿script mal escrito? Forge NO avisa de eso");
        }
    }

    /** Hasta que turno TUYO se vigila que la reliquia no se repita. */
    private static final int TURNOS_VIGILADOS = 3;

    /**
     * Que una reliquia de "primer mantenimiento" <b>no vuelva a dispararse</b>.
     *
     * <h2>Por que hace falta una segunda partida</h2>
     *
     * <p>La primera se para en cuanto ve el efecto ({@code stopOnHit}), que es
     * lo que la hace rapida — y por eso no puede contestar esta pregunta: en el
     * momento en que mira, una reliquia rota y una buena son indistinguibles.
     * Las dos han curado 4. La diferencia esta en el turno siguiente.
     *
     * <p>Asi que esta corrida <b>no se para en el efecto</b>: se para cuando
     * sabe la respuesta, sea cual sea. Si el efecto vuelve a ocurrir en un
     * turno que no es el primero, rojo; si llegas al turno
     * {@value #TURNOS_VIGILADOS} sin que ocurra, verde. No hace falta jugar la
     * partida entera: el fallo que caza es un disparo <b>sin condicion</b>, y
     * uno de esos salta en el primer mantenimiento que le toca.
     */
    private static boolean soloUnaVez(final Spec spec, final Deck deck,
                                      final PaperCard card, final int life) {
        final String[] veredicto = {null};
        final AscentProbe.Result r = AscentProbe.play("repite-" + spec.id,
                EnumSet.of(GameType.Commander), deck, life, null, List.of(card), List.of(),
                true, 100L, game -> {
                    final Player me = mine(game);
                    // El primer turno es el suyo POR CONTRATO: lo que se busca
                    // es el segundo.
                    if (me == null || me.getTurn() < 2) {
                        return false;
                    }
                    if (repitio(me, spec)) {
                        veredicto[0] = "repite";
                        return true;
                    }
                    if (me.getTurn() >= TURNOS_VIGILADOS) {
                        veredicto[0] = "no repite";
                        return true;
                    }
                    return false;
                });

        if (!r.reached) {
            fail(spec.id + ": no se ha podido comprobar si se repite (" + r.why + ")");
            return false;
        }
        if ("repite".equals(veredicto[0])) {
            fail(spec.id + ": su texto dice \"first upkeep\" pero se dispara TODOS los turnos"
                    + " — el disparo se ha quedado sin condicion, y Forge NO avisa de eso");
            return false;
        }
        return true;
    }

    /**
     * Si el efecto ha vuelto a ocurrir en un turno que <b>no</b> es el primero.
     *
     * <p>Se mide con los contadores del turno y no con el total: el total lo
     * tapa un ataque de la IA (la vida) y no distingue el robo del turno (las
     * cartas). Robar se pide otra vez con {@code +1} por lo mismo que en la
     * primera partida — esa carta la roba el paso de robo de todas formas.
     */
    private static boolean repitio(final Player me, final Spec spec) {
        if (spec.life > 0) {
            return me.getLifeGainedThisTurn() >= spec.life;
        }
        if (spec.draw > 0) {
            return me.getNumDrawnThisTurn() >= spec.draw + 1;
        }
        return false;
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

        if (spec.onlyTrigger()) {
            // ⚠️ Tiene que salir ANTES del bucle de criaturas de abajo: alli se
            // compara contra +0/+0 y sin palabras clave, o sea que CUALQUIER
            // criatura tuya daria verde. Una reliquia que solo declara
            // byTrigger se comprueba por el registro y por nada mas.
            return byTrigger(game, spec, cardName, visto);
        }

        if (spec.playerKeyword != null) {
            if (!me.hasKeyword(spec.playerKeyword)) {
                return false;
            }
            visto[0] = "tienes " + spec.playerKeyword;
            return true;
        }

        if (spec.tokens > 0) {
            int fichas = 0;
            for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                if (c.isCreature() && c.isToken()) {
                    fichas++;
                }
            }
            if (fichas >= spec.tokens) {
                visto[0] = fichas + " ficha(s) de criatura en tu mesa";
                return true;
            }
            return false;
        }

        if (spec.oppLife > 0) {
            // La vida del rival, no la tuya. Es atribuible porque el humano de
            // la sonda NO ataca: si baja, ha bajado por la reliquia.
            for (final Player p : game.getPlayers()) {
                if (p != me && p.getStartingLife() - p.getLife() >= spec.oppLife) {
                    visto[0] = "el rival ha bajado de " + p.getStartingLife()
                            + " a " + p.getLife();
                    return true;
                }
            }
            return false;
        }

        if (spec.blockExtra > 0) {
            for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                if (c.isCreature() && c.canBlockAdditional() >= spec.blockExtra) {
                    visto[0] = c.getName() + " puede bloquear "
                            + c.canBlockAdditional() + " criatura(s) de mas";
                    return true;
                }
            }
            return false;
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
            //
            // ⚠️ Salvo en el turno 1 de la partida, porque el que SALE no roba
            // ese turno. A una reliquia de "primer mantenimiento" eso le pasa
            // la mitad de las veces, asi que pedir siempre +1 hacia que el
            // comprobador saliera verde o rojo segun quien ganase el sorteo de
            // salida — y un rojo que va y viene es peor que un rojo fijo:
            // acabas mirando el script bueno. Cazado el 22-09-2026, cuando
            // wanderers_compass y oracle_lens pasaron en --solo y fallaron en
            // la bateria: la unica diferencia era que Ana salio segundo.
            final int need = spec.draw + (game.getPhaseHandler().getTurn() == 1 ? 0 : 1);
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
                if (!tieneKeyword(c, kw)) {
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

    /**
     * Si la criatura tiene esa palabra clave, <b>o una que empieza igual</b>.
     *
     * <p>El prefijo hace falta para las que llevan numero: {@code Renown:1} se
     * concede asi en el script pero el motor la guarda con su propio formato, y
     * un {@code hasKeyword("Renown")} pelado no la encuentra. Comparar por
     * prefijo cubre las dos formas sin tener que adivinar cual usa cada
     * version de Forge.
     */
    private static boolean tieneKeyword(final Card c, final String kw) {
        if (c.hasKeyword(kw)) {
            return true;
        }
        final String buscado = kw.toLowerCase(Locale.ROOT);
        for (final forge.game.keyword.KeywordInterface k : c.getKeywords()) {
            final String tiene = k.getOriginal();
            if (tiene != null && tiene.toLowerCase(Locale.ROOT).startsWith(buscado)) {
                return true;
            }
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
