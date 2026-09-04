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
    private static final Map<String, AscentRelic.Rarity> CATALOGUE = new LinkedHashMap<>();

    static {
        // Comunes: tesoros y tiendas. Empujoncitos, no vuelcos.
        CATALOGUE.put("smiths_blessing", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("pilgrims_chalice", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("whetstone_sigil", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("wanderers_compass", AscentRelic.Rarity.COMMON);

        // Raras: lo que paga una elite, que es un nodo que puede costarte la run.
        CATALOGUE.put("ember_totem", AscentRelic.Rarity.RARE);
        CATALOGUE.put("warden_seal", AscentRelic.Rarity.RARE);
        CATALOGUE.put("oracle_lens", AscentRelic.Rarity.RARE);
        CATALOGUE.put("wellspring_stone", AscentRelic.Rarity.RARE);

        // De jefe. SEIS, y no es un numero al azar: un jefe ofrece TRES a elegir
        // y ninguna que ya lleves, asi que con tres bosses hacen falta seis para
        // que el ultimo siga teniendo de donde elegir. Y pegan de verdad: son el
        // salto que tiene que aguantar el acto siguiente entero.
        CATALOGUE.put("crown_of_ascent", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("phoenix_heart", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("hourglass_of_kings", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("titans_grasp", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("ascendant_geode", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("banner_of_legions", AscentRelic.Rarity.BOSS);

        // ---- el lote grande (02-09-2026) ----
        //
        // La gracia de un roguelike es la VARIEDAD de builds, y con catorce
        // reliquias las runs se parecian demasiado entre si. Con treinta y
        // cinco, dos runs seguidas ya no ofrecen lo mismo.
        CATALOGUE.put("sharpened_fang", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("hunters_charm", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("swiftfoot_anklet", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("copper_ring", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("lucky_coin", AscentRelic.Rarity.COMMON);
        CATALOGUE.put("scouts_map", AscentRelic.Rarity.COMMON);

        CATALOGUE.put("sunlit_aegis", AscentRelic.Rarity.RARE);
        CATALOGUE.put("serpent_coil", AscentRelic.Rarity.RARE);
        CATALOGUE.put("stoneheart_idol", AscentRelic.Rarity.RARE);
        CATALOGUE.put("berserkers_mask", AscentRelic.Rarity.RARE);
        CATALOGUE.put("chronicle_page", AscentRelic.Rarity.RARE);
        CATALOGUE.put("pilgrims_ward", AscentRelic.Rarity.RARE);

        CATALOGUE.put("warlords_standard", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("windrider_cloak", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("font_of_souls", AscentRelic.Rarity.BOSS);
        CATALOGUE.put("chalice_of_ages", AscentRelic.Rarity.BOSS);

        // Las rotas. Salen casi nunca y se nota cuando salen: eso es el punto.
        CATALOGUE.put("crown_of_the_eternal", AscentRelic.Rarity.LEGENDARY);
        CATALOGUE.put("the_infinite_tome", AscentRelic.Rarity.LEGENDARY);
        CATALOGUE.put("heart_of_the_mountain", AscentRelic.Rarity.LEGENDARY);
        CATALOGUE.put("aegis_eternal", AscentRelic.Rarity.LEGENDARY);
        CATALOGUE.put("wings_of_the_ascended", AscentRelic.Rarity.LEGENDARY);
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
        for (final Map.Entry<String, AscentRelic.Rarity> e : CATALOGUE.entrySet()) {
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
            final AscentRelic relic = new AscentRelic(id, rules.getName(), e.getValue(),
                    rules.getOracleText());
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
                    AscentRelic.Rarity.BOSS, rules.getOracleText()));
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
