package forge.neo.ascent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.ImageKeys;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Las reliquias de Ascenso: cartas nuestras que el motor juega como suyas.
 *
 * <h2>Por que esto se puede hacer sin tocar Forge</h2>
 *
 * <p>Una reliquia da una pasiva permanente ("tus criaturas entran con +1/+1",
 * "ganas 1 vida cada mantenimiento"). Escribir eso como logica nuestra seria
 * <b>inventar reglas</b>, que es justo lo que este proyecto no hace: habria que
 * meterse en disparos, capas y prioridad, y ademas se saldria de la interfaz.
 *
 * <p>No hace falta. El motor ya sabe hacerlo, y ademas <b>ya lo hace para el
 * modo Adventure de Forge</b>: los objetos de Shandalar (el Tomo de Chandra, la
 * Vara del Sueño, el Tesoro Maldito) son cartas inventadas que no existen en
 * Magic, viven en {@code res/adventure/common/custom_cards/} y entran en la
 * zona de mando al empezar el combate. Nuestras reliquias son exactamente eso.
 *
 * <h2>Como se registran (el camino de Forge, copiado)</h2>
 *
 * <p>{@code forge.adventure.util.Config.loadResources()} lee cada fichero,
 * lo pasa por {@link CardRules.Reader} y mete la carta resultante en la base
 * con {@code CardDb.addCard}. Todo eso es publico. Tres consecuencias muy
 * buenas:
 *
 * <ol>
 *   <li><b>No hay que escribir nada en el disco del jugador.</b> Los scripts
 *       viajan como recursos dentro del jar y se leen de ahi. La primera idea
 *       era copiarlos a {@code %APPDATA%\Forge\custom\cards\}, que tambien
 *       funciona pero obliga a hacerlo <i>antes</i> de arrancar el motor —
 *       {@code StaticData} esta memoizado y no se relee.
 *   <li><b>No hace falta fichero de edicion.</b> {@link CardEdition#UNKNOWN_CODE}
 *       y {@link CardRarity#Special}, como hace Adventure.
 *   <li><b>Se registra DESPUES de cargar el motor</b>, que es cuando existe la
 *       base de cartas donde meterlas.
 * </ol>
 *
 * <h2>⚠️ Lo que hay que vigilar</h2>
 *
 * <p>{@code addCard} las mete en la base de cartas <b>de verdad</b>, la misma
 * de la que tira el catalogo del deck builder y los mazos aleatorios. Por eso
 * cada script lleva {@code AI:RemoveDeck:All} y por eso {@link AscentProbe}
 * comprueba explicitamente que <b>no</b> aparecen en el catalogo. Una reliquia
 * suelta en el buscador de cartas seria un fallo silencioso y feisimo.
 */
public final class AscentRelics {

    private AscentRelics() {
    }

    /** Donde viven los scripts, dentro del jar. */
    private static final String DIR = "/forge/neo/ascent/relics/";

    /**
     * El catalogo: fichero -> rareza.
     *
     * <p>Se declara a mano y no se descubre leyendo la carpeta a proposito: la
     * rareza no esta en el script, y una reliquia que aparece sola en el juego
     * porque alguien dejo un fichero suelto es justo lo que no queremos.
     */
    private static final Map<String, Entry> CATALOGUE = new LinkedHashMap<>();

    /** Sin requisito de color: sale en cualquier run. Las 37 de siempre. */
    public static final byte COLOURLESS = 0;

    private static final byte W = forge.card.MagicColor.WHITE;
    private static final byte U = forge.card.MagicColor.BLUE;
    private static final byte B = forge.card.MagicColor.BLACK;
    private static final byte R = forge.card.MagicColor.RED;
    private static final byte G = forge.card.MagicColor.GREEN;

    /**
     * Lo que se declara de una reliquia en el catalogo: de que rareza es y
     * <b>que colores pide</b>.
     *
     * <p>Era solo la rareza hasta el 22-09-2026. El color entro para que una
     * reliquia pueda decir {@code add {B}{B}{B}} sin que le toque a un mazo
     * mono-blanco, donde seria un premio vacio — y de paso para que las runs de
     * un color no se parezcan a las de otro, que es lo que pedia Ana.
     */
    private static final class Entry {
        private final AscentRelic.Rarity rarity;
        private final byte colors;

        Entry(final AscentRelic.Rarity rarity, final byte colors) {
            this.rarity = rarity;
            this.colors = colors;
        }
    }

    /** Atajo para las de siempre: rareza y ningun requisito de color. */
    private static void add(final String id, final AscentRelic.Rarity rarity) {
        CATALOGUE.put(id, new Entry(rarity, COLOURLESS));
    }

    /** Una reliquia que solo se le ofrece a un mazo de esos colores. */
    private static void add(final String id, final AscentRelic.Rarity rarity, final byte colors) {
        CATALOGUE.put(id, new Entry(rarity, colors));
    }

    static {
        // Comunes: tesoros y tiendas. Empujoncitos, no vuelcos.
        add("smiths_blessing", AscentRelic.Rarity.COMMON);
        add("pilgrims_chalice", AscentRelic.Rarity.COMMON);
        add("whetstone_sigil", AscentRelic.Rarity.COMMON);
        add("wanderers_compass", AscentRelic.Rarity.COMMON);

        // Raras: lo que paga una elite, que es un nodo que puede costarte la run.
        add("ember_totem", AscentRelic.Rarity.RARE);
        add("warden_seal", AscentRelic.Rarity.RARE);
        add("oracle_lens", AscentRelic.Rarity.RARE);
        add("wellspring_stone", AscentRelic.Rarity.RARE);

        // De jefe. SEIS, y no es un numero al azar: un jefe ofrece TRES a elegir
        // y ninguna que ya lleves, asi que con tres bosses hacen falta seis para
        // que el ultimo siga teniendo de donde elegir. Y pegan de verdad: son el
        // salto que tiene que aguantar el acto siguiente entero.
        add("crown_of_ascent", AscentRelic.Rarity.BOSS);
        add("phoenix_heart", AscentRelic.Rarity.BOSS);
        add("hourglass_of_kings", AscentRelic.Rarity.BOSS);
        add("titans_grasp", AscentRelic.Rarity.BOSS);
        add("ascendant_geode", AscentRelic.Rarity.BOSS);
        add("banner_of_legions", AscentRelic.Rarity.BOSS);

        // ---- el lote grande (02-09-2026) ----
        //
        // La gracia de un roguelike es la VARIEDAD de builds, y con catorce
        // reliquias las runs se parecian demasiado entre si. Con treinta y
        // cinco, dos runs seguidas ya no ofrecen lo mismo.
        add("sharpened_fang", AscentRelic.Rarity.COMMON);
        add("hunters_charm", AscentRelic.Rarity.COMMON);
        add("swiftfoot_anklet", AscentRelic.Rarity.COMMON);
        add("copper_ring", AscentRelic.Rarity.COMMON);
        add("lucky_coin", AscentRelic.Rarity.COMMON);
        add("scouts_map", AscentRelic.Rarity.COMMON);

        add("sunlit_aegis", AscentRelic.Rarity.RARE);
        add("serpent_coil", AscentRelic.Rarity.RARE);
        add("stoneheart_idol", AscentRelic.Rarity.RARE);
        add("berserkers_mask", AscentRelic.Rarity.RARE);
        add("chronicle_page", AscentRelic.Rarity.RARE);
        add("pilgrims_ward", AscentRelic.Rarity.RARE);

        add("warlords_standard", AscentRelic.Rarity.BOSS);
        add("windrider_cloak", AscentRelic.Rarity.BOSS);
        add("font_of_souls", AscentRelic.Rarity.BOSS);
        add("chalice_of_ages", AscentRelic.Rarity.BOSS);

        // Las rotas. Salen casi nunca y se nota cuando salen: eso es el punto.
        add("crown_of_the_eternal", AscentRelic.Rarity.LEGENDARY);
        add("the_infinite_tome", AscentRelic.Rarity.LEGENDARY);
        add("heart_of_the_mountain", AscentRelic.Rarity.LEGENDARY);
        add("aegis_eternal", AscentRelic.Rarity.LEGENDARY);
        add("wings_of_the_ascended", AscentRelic.Rarity.LEGENDARY);

        // ---- las de COLOR (22-09-2026) ----
        //
        // Ideas del autor. Lo que las separa de las 35 de arriba no es la
        // potencia: es que **piden un color**, y por eso pueden hacer cosas que
        // una reliquia universal no puede — {B}{B}{B}, fichas de Humano,
        // pantanos que dan de mas. Una reliquia que vale para todos los mazos
        // acaba siendo "tus criaturas +X/+X" en sus mil variantes; una que
        // sabe de que color eres puede ser otra cosa.
        //
        // Solo salen si tu mazo comparte color con ellas (AscentRelic#fitsColors),
        // asi que dos runs de colores distintos ya no ofrecen lo mismo — que es
        // lo que se pedia. Y al rival NO se le dan: ver AscentBattle.relicsFor.

        // Blancas: vida que se convierte en cuerpos, y anchura.
        add("recruiters_pennant", AscentRelic.Rarity.COMMON, W);
        add("chalice_of_welcome", AscentRelic.Rarity.COMMON, W);
        add("reliquary_of_dawn", AscentRelic.Rarity.COMMON, W);

        // ⚠️ Muster Horn es RARA y no comun, aunque la idea original la ponia
        // abajo. De media partida en adelante atacar con dos criaturas es la
        // jugada normal, o sea que en la practica es UNA CARTA POR TURNO — que
        // es exactamente The Infinite Tome, que es legendaria. Dejarla comun
        // repetia el fallo de Lucky Coin (22-09-2026) pero por disenyo en vez
        // de por un script roto: una comun que tapa a una legendaria hace que
        // encontrar la legendaria no signifique nada.
        add("muster_horn", AscentRelic.Rarity.RARE, W);
        add("ledger_of_mercies", AscentRelic.Rarity.RARE, W);
        add("heralds_laurel", AscentRelic.Rarity.RARE, W);
        add("bulwark_pauldron", AscentRelic.Rarity.RARE, W);
        add("shepherds_lantern", AscentRelic.Rarity.RARE, W);

        add("seraphs_accord", AscentRelic.Rarity.BOSS, W);
        add("gravebound_censer", AscentRelic.Rarity.BOSS, W);
        add("standard_of_kin", AscentRelic.Rarity.BOSS, W);

        // Negras: el cementerio, la mano del rival y su vida.
        add("gravecallers_tithe", AscentRelic.Rarity.COMMON, B);
        add("rotting_hourglass", AscentRelic.Rarity.COMMON, B);
        add("charnel_mound", AscentRelic.Rarity.COMMON, B);

        add("whispering_debt", AscentRelic.Rarity.RARE, B);
        add("midnight_offering", AscentRelic.Rarity.RARE, B);
        add("widows_toll", AscentRelic.Rarity.RARE, B);

        add("tyrants_mirror", AscentRelic.Rarity.BOSS, B);
        add("coffers_key", AscentRelic.Rarity.BOSS, B);
    }

    /**
     * El "segundo aliento" del jefe del acto 3 (Ascension 10): dos scripts,
     * uno por modo — la vida de salida se dobla entre Estandar (20) y
     * Commander (40), y con ella la del jefe, asi que el umbral tiene que
     * doblarse tambien. <b>NO viven en {@link #CATALOGUE}</b>: no son un
     * premio, y ni {@code AscentRewards} ni {@code AscentBattle.relicsFor}
     * pueden sortearlos por accidente porque estan en su propia lista, aparte
     * de {@link #all()}.
     */
    private static final Map<AscentRun.Mode, String> BOSS_PHASE_ID = new LinkedHashMap<>();

    static {
        BOSS_PHASE_ID.put(AscentRun.Mode.STANDARD, "cornered_fury");
        BOSS_PHASE_ID.put(AscentRun.Mode.COMMANDER, "tyrants_last_stand");
    }

    private static final Map<AscentRun.Mode, PaperCard> BOSS_PHASE_CARD = new LinkedHashMap<>();

    private static final List<AscentRelic> LOADED = new ArrayList<>();

    /**
     * Nombre de carta -> reliquia, para poder reconocer una de las nuestras
     * <b>barato</b>.
     *
     * <p>Lo pregunta {@code CardNode} en cada refresco de cada carta de la
     * mesa, para saber si tiene que dibujarle la cara de reliquia. Recorrer
     * {@link #LOADED} ahi seria 37 comparaciones por carta y por repintado.
     */
    private static final Map<String, AscentRelic> BY_NAME = new LinkedHashMap<>();

    private static boolean installed;

    /**
     * Registra las reliquias en la base de cartas del motor.
     *
     * <p><b>Hay que llamarlo despues de cargar el motor</b> ({@code NeoBoot})
     * y antes de montar ninguna partida de Ascenso. Es idempotente: llamarlo
     * dos veces no duplica nada.
     *
     * @return cuantas reliquias quedaron registradas
     */
    public static synchronized int install() {
        if (installed) {
            return LOADED.size();
        }
        installed = true;

        final CardRules.Reader reader = new CardRules.Reader();
        for (final Map.Entry<String, Entry> e : CATALOGUE.entrySet()) {
            final String id = e.getKey();
            final List<String> lines = read(id);
            if (lines.isEmpty()) {
                // Falla ruidoso: una reliquia que no carga es una recompensa
                // que no existe, y en silencio saldria un hueco en la tienda.
                throw new IllegalStateException("no se ha podido leer el script de la reliquia: " + id);
            }
            reader.reset();
            final CardRules rules = reader.readCard(lines, id);
            rules.setCustom();

            final PaperCard card = new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special) {
                @Override
                public String getImageKey(final boolean altState) {
                    // El mismo prefijo que usa Adventure para sus objetos. Si
                    // algun dia hay arte, va en la carpeta que apunte
                    // ImageKeys.ADVENTURE_CARD_PICS_DIR; sin arte se dibuja la
                    // carta a mano, que para un texto de dos lineas se lee
                    // perfectamente.
                    return ImageKeys.ADVENTURECARD_PREFIX + getName();
                }
            };
            // isVariant() decide la base: un Enchantment normal va a la comun.
            // Se pregunta en vez de asumirlo por si alguna reliquia futura es
            // de un tipo raro.
            if (rules.isVariant()) {
                FModel.getMagicDb().getVariantCards().addCard(card);
            } else {
                FModel.getMagicDb().getCommonCards().addCard(card);
            }
            final AscentRelic relic = new AscentRelic(id, rules.getName(), e.getValue().rarity,
                    rules.getOracleText(), e.getValue().colors);
            LOADED.add(relic);
            BY_NAME.put(relic.getCardName(), relic);
        }
        installBossPhase(reader);
        return LOADED.size();
    }

    /**
     * Registra el "segundo aliento" del jefe del acto 3, igual que las
     * reliquias de arriba pero <b>fuera</b> de {@link #LOADED}: no se puede
     * sortear como premio, solo lo pone {@code AscentBattle} en el asiento
     * del jefe.
     */
    private static void installBossPhase(final CardRules.Reader reader) {
        for (final Map.Entry<AscentRun.Mode, String> e : BOSS_PHASE_ID.entrySet()) {
            final String id = e.getValue();
            final List<String> lines = read(id);
            if (lines.isEmpty()) {
                throw new IllegalStateException("no se ha podido leer el segundo aliento: " + id);
            }
            reader.reset();
            final CardRules rules = reader.readCard(lines, id);
            rules.setCustom();
            final PaperCard card = new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special) {
                @Override
                public String getImageKey(final boolean altState) {
                    return ImageKeys.ADVENTURECARD_PREFIX + getName();
                }
            };
            FModel.getMagicDb().getCommonCards().addCard(card);
            BOSS_PHASE_CARD.put(e.getKey(), card);
            // Tambien al indice por nombre: NO para poder sortearlo (para eso
            // esta LOADED, y este no entra ahi ni entrara), sino para que la
            // interfaz sepa dibujarle su cara. Entra en la zona de mando del
            // JEFE, o sea que se ve en la mesa, y es justo la carta que hay que
            // poder leer: dice cuando y con que te va a rematar.
            BY_NAME.put(card.getName(), new AscentRelic(id, card.getName(),
                    AscentRelic.Rarity.BOSS, rules.getOracleText(), COLOURLESS));
        }
    }

    /**
     * La carta del segundo aliento para ese modo (Ascension 10, solo jefe del
     * acto 3). {@code null} si todavia no se ha llamado a {@link #install()}.
     */
    public static PaperCard bossPhaseCard(final AscentRun.Mode mode) {
        return BOSS_PHASE_CARD.get(mode);
    }

    /** Todas las reliquias registradas. Vacio si todavia no se llamo a {@link #install()}. */
    public static List<AscentRelic> all() {
        return Collections.unmodifiableList(LOADED);
    }

    /**
     * Los nombres de TODAS nuestras cartas: las reliquias y el segundo aliento.
     *
     * <p>Lo pide {@code CardIndex} para quitarlas del catalogo del deck
     * builder. Hace falta de verdad: {@code getUniqueCards()} <b>si</b> las
     * trae — se comprobo que solo parecian ausentes porque {@code CardDb}
     * reindexa mas tarde, despues de la primera partida, y el comprobador que
     * decia lo contrario miraba demasiado pronto. Ver {@code AscentCheck}.
     */
    public static java.util.Set<String> allCardNames() {
        final java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (final AscentRelic r : LOADED) {
            names.add(r.getCardName());
        }
        for (final PaperCard c : BOSS_PHASE_CARD.values()) {
            names.add(c.getName());
        }
        return names;
    }

    /**
     * La reliquia que se llama asi, o {@code null} si esa carta no es nuestra.
     *
     * <p>Es la pregunta que hace la interfaz al pintar una carta cualquiera:
     * lo unico que tiene a mano es el nombre. Barata a proposito (ver
     * {@link #BY_NAME}) y segura antes de {@link #install()}: sin reliquias
     * registradas devuelve {@code null} y todo sigue como siempre.
     */
    public static AscentRelic byCardName(final String cardName) {
        return cardName == null ? null : BY_NAME.get(cardName);
    }

/**
     * Si esa reliquia le <b>llena la mano</b> a quien la lleve.
     *
     * <h2>Por que hace falta saberlo</h2>
     *
     * <p>Una reliquia que te hace robar es un premio estupendo <b>para ti</b> y
     * un problema en el asiento de la IA, y no por potencia: por <b>tiempo</b>.
     * Forge evalua cada carta jugable en cada prioridad, asi que tres cartas
     * mas en la mano son muchas mas ramas que recorrer, cada turno, para
     * siempre.
     *
     * <p><b>Medido</b> el 05-09-2026 con {@code -Dneo.ai.relics} sobre partidas
     * de 70 segundos: la IA jugaba <b>17-18 turnos</b> sin reliquias y
     * <b>12-13</b> con <i>Hourglass of Kings</i> (robar tres en tu primer
     * mantenimiento). O sea la mitad de ritmo — y eso, jugando, es una espera
     * larga en el turno del rival: <i>"la pelea contra el boss se lagueaba"</i>.
     * La de mana ({@code Heart of the Mountain}) se quedo en el ruido, 14-19.
     *
     * <p>Se reconoce por el <b>texto</b> de la carta y no por una lista a mano:
     * los scripts de las reliquias los escribimos nosotros y van siempre en
     * ingles, asi que "draw" es fiable aqui — y sobre todo, una reliquia nueva
     * que haga robar queda cubierta el dia que se escriba, sin acordarse de
     * nada.
     */
    public static boolean growsHand(final AscentRelic relic) {
        final PaperCard card = cardOf(relic);
        if (card == null || card.getRules() == null) {
            return false;
        }
        final String oracle = card.getRules().getOracleText();
        return oracle != null && oracle.toLowerCase(java.util.Locale.ROOT).contains("draw");
    }

    /** La reliquia con ese id, o {@code null} si no existe. */
    public static AscentRelic byId(final String id) {
        for (final AscentRelic r : LOADED) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * La carta de esa reliquia, tal y como la tiene el motor.
     *
     * <p>Es lo que se le pasa a {@code RegisteredPlayer.addExtraCardsInCommandZone}.
     */
    public static PaperCard cardOf(final AscentRelic relic) {
        return FModel.getMagicDb().getCommonCards().getCard(relic.getCardName());
    }

    /**
     * El script de esa reliquia, tal cual, para poder comprobarlo.
     *
     * <p>Lo pide {@code AscentCheck}: sin las claves de zona
     * ({@code EffectZone$ Command} para las estaticas,
     * {@code TriggerZones$ Command} para los disparos) la carta esta en el mando
     * <b>sin hacer nada</b>, y Forge no lo dice de ninguna forma.
     */
    public static String scriptOf(final String id) {
        final List<String> lines = read(id);
        // Con salto delante y detras, para que quien lo comprueba pueda buscar
        // "\nS:" y "\nT:" sin pillar una S: que este a mitad de otra linea.
        return lines.isEmpty() ? null : "\n" + String.join("\n", lines) + "\n";
    }

    private static List<String> read(final String id) {
        final List<String> lines = new ArrayList<>();
        try (InputStream in = AscentRelics.class.getResourceAsStream(DIR + id + ".txt")) {
            if (in == null) {
                return lines;
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }
        } catch (final IOException ex) {
            throw new IllegalStateException("fallo leyendo la reliquia " + id, ex);
        }
        return lines;
    }
}
