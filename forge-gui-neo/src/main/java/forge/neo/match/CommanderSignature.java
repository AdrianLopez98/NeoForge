package forge.neo.match;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import forge.game.card.CardView;

/**
 * <b>La firma de los comandantes famosos</b> en su entrada (Ana, 18-10-2026:
 * <i>"comandantes famosos y buenos tuviesen efectos custom que tengan que ver
 * con ella, por ejemplo en Sephiroth unas plumas negras al entrar"</i>).
 *
 * <p>Solo la TABLA: que comandante lleva que firma. La pintan las dos
 * interfaces ({@code forge.neo.ui.CommanderEntrance} y, en Android,
 * {@code EntradaComandante.kt}), y por eso vive aqui: el jar la comparte y las
 * dos leen la misma lista. Anyadir un comandante a una firma que ya existe es
 * una linea de {@link #BY_NAME}; una firma nueva es su coreografia (como
 * {@link BlackFeathers}) y dibujarla en las dos.
 *
 * <p>Por el nombre en ingles, que es el del motor y no cambia con el idioma, y
 * mirando las dos caras: una carta de dos caras puede entrar por la de atras.
 * Algunas solo cuando el comandante es <b>tuyo</b> ({@link #putMine}): Atraxa,
 * Grand Unifier es Sephiroth en <i>Final Fantasy Through the Ages</i> ("Sephiroth,
 * the Savior"), y la del rival sera casi siempre la de Phyrexia.
 * {@code -Dneo.entrance.signature=BLACK_FEATHERS} la fuerza para probarla con
 * cualquier carta ({@code --entrance-test}).
 */
public enum CommanderSignature {

    /** La entrada de siempre. */
    NONE,

    /**
     * Sephiroth, el angel de un ala: un <b>ala negra</b> que se despliega
     * detras de la carta y se deshace en plumas, <b>plumas negras</b> cayendo
     * por toda la pantalla, y un <b>estallido de plumas</b> al posarse.
     */
    BLACK_FEATHERS,

    /** Cloud: el Omnislash, cinco tajos azules que cruzan la carta ({@link CloudSlashes}). */
    CLOUD_SLASHES,

    /** Jin Sakai: hojas de arce en el viento y un tajo de katana ({@link JinLeaves}). */
    JIN_LEAVES,

    // Los diez comandantes mas jugados de EDH (EDHREC, 20-10-2026), con capas
    // (LayeredSignature). Por el nombre de verdad: valen para todas sus impresiones.

    /** Y'shtola, Night's Blessed: la luna y las estrellas. */
    YSHTOLA_MOON,
    /** Edgar Markov: murcielagos y sangre. */
    EDGAR_BATS,
    /** The Ur-Dragon: fuego y ascuas de los cinco colores. */
    URDRAGON_FIRE,
    /** Atraxa, Praetors' Voice: resplandor dorado y plumas blancas. */
    ATRAXA_LIGHT,
    /** Krenko, Mob Boss: chispas que se multiplican. */
    KRENKO_SWARM,
    /** Vivi Ornitier: bolas de fuego que orbitan y estrellas magicas. */
    VIVI_FIRE,
    /** Sauron, the Dark Lord: el Ojo en llamas y ceniza. */
    SAURON_EYE,
    /** Kaalia of the Vast: angeles, demonios y dragones en espiral. */
    KAALIA_HOSTS,
    /** Teval, the Balanced Scale: hojas que caen y almas que suben. */
    TEVAL_BALANCE,
    /** Fire Lord Azula: fuego azul y rayos. */
    AZULA_LIGHTNING,

    /** Isshin, Two Heavens as One: papel de arroz, las dos pinceladas y la firma de Musashi (Vagabond). */
    ISSHIN_INK,

    // Los diez siguientes de EDHREC (21-10-2026) y Lightning.
    /** Giada, Font of Hope: motas doradas y plumas. */
    GIADA_ANGELS,
    /** Pantlaza, Sun-Favored: el sol y llamas doradas. */
    PANTLAZA_SUN,
    /** Ms. Bumbleflower: petalos y estrellas. */
    BUMBLEFLOWER_PETALS,
    /** Lathril, Blade of the Elves: hojas y tajos verdes. */
    LATHRIL_BLADES,
    /** Toph, the First Metalbender: rocas y metal. */
    TOPH_EARTH,
    /** The Wise Mothman: polillas y radiacion. */
    MOTHMAN_MOTHS,
    /** Jodah, the Unifier: los cinco colores. */
    JODAH_PRISM,
    /** Hearthhull, the Worldseed: estrellas y un planeta. */
    HEARTHHULL_COSMOS,
    /** Nekusar, the Mindrazer: la rueda de cartas y llamas moradas. */
    NEKUSAR_WHEEL,
    /** Kefka, Court Mage: el Juicio de Luz. */
    KEFKA_JUDGMENT,
    /** Lightning, Army of One: rayos rosas y su pluma. */
    LIGHTNING_BOLTS,

    // La tercera tanda de EDHREC (22-10-2026).
    /** Yuriko, the Tiger's Shadow: shurikens, un tajo rojo y humo. */
    YURIKO_SHURIKEN,
    /** Miirym, Sentinel Wyrm: llamas azules y rojas. */
    MIIRYM_FLAMES,
    /** Muldrotha, the Gravetide: roca y hojas de la tierra. */
    MULDROTHA_EARTH,
    /** Meren of Clan Nel Toth: almas que suben. */
    MEREN_SOULS,
    /** Korvold, Fae-Cursed King: monedas de oro y llamas. */
    KORVOLD_GOLD,
    /** Prosper, Tome-Bound: llamas moradas y monedas. */
    PROSPER_FLAMES,
    /** Yawgmoth, Thran Physician: aceite negro y humo. */
    YAWGMOTH_OIL,
    /** Chulane, Teller of Tales: el halo y las hojas. */
    CHULANE_TALES,
    /** Wilhelt, the Rotcleaver: ceniza y podredumbre. */
    WILHELT_ROT,
    /** Tiamat: las llamas de sus cinco cabezas. */
    TIAMAT_HEADS,

    // La cuarta tanda de EDHREC (22-10-2026).
    /** Gishath, Sun's Avatar: el sol, rocas y llamas. */
    GISHATH_SUN,
    /** Breya, Etherium Shaper: engranajes y chispas. */
    BREYA_GEARS,
    /** Sliver Overlord: la colmena que se multiplica. */
    SLIVER_HIVE,
    /** Kenrith, the Returned King: la corona y los cinco colores. */
    KENRITH_CROWN,
    /** Omnath, Locus of Creation: roca y sus cuatro colores. */
    OMNATH_LANDS,
    /** Niv-Mizzet, Parun: cartas y rayos. */
    NIV_MIZZET_BOLTS,
    /** Arcades, the Strategist: escudos y plumas. */
    ARCADES_WALLS,
    /** The Gitrog Monster: tierra que cae al cementerio. */
    GITROG_MUD,
    /** Light-Paws, Emperor's Voice: el halo y los petalos. */
    LIGHT_PAWS_GRACE,
    /** Kinnan, Bonder Prodigy: mana y engranajes. */
    KINNAN_MANA,

    // La quinta tanda de EDHREC (22-10-2026).
    /** Lord Windgrace: roca, hojas y ceniza. */
    WINDGRACE_LANDS,
    /** Zur the Enchanter: estrellas y sus tres colores. */
    ZUR_STARS,
    /** Sythis, Harvest's Hand: el halo, petalos y hojas. */
    SYTHIS_PETALS,
    /** Teysa Karlov: almas y chispas que se duplican. */
    TEYSA_SOULS,
    /** Aesi, Tyrant of Gyre Strait: gotas de agua y hojas. */
    AESI_TIDE,
    /** Animar, Soul of Elements: sus tres colores, llamas y roca. */
    ANIMAR_ELEMENTS,
    /** Urza, Lord High Artificer: engranajes y mana. */
    URZA_GEARS,
    /** Derevi, Empyrial Tactician: plumas y viento. */
    DEREVI_WIND,
    /** Feather, the Redeemed: plumas rojas y fuego. */
    FEATHER_RED,
    /** Najeela, the Blade-Blossom: tres tajos y petalos. */
    NAJEELA_BLADES,

    // La sexta tanda de EDHREC (04-11-2026).
    /** Baylen, the Haymaker: paja, un punyetazo y sus tres colores. */
    BAYLEN_HAY,
    /** Valgavoth, Harrower of Souls: almas que entran en espiral. */
    VALGAVOTH_SOULS,
    /** Hashaton, Scarab's Fist: escarabajos que se multiplican. */
    HASHATON_SCARABS,
    /** Chatterfang, Squirrel General: bellotas que se multiplican y hojas. */
    CHATTERFANG_ACORNS,
    /** Ulalek, Fused Atrocity: el vacio de los Eldrazi. */
    ULALEK_VOID,
    /** Ashling, the Limitless: llamas sin fin. */
    ASHLING_FLAMES,
    /** Frodo, Adventurous Hobbit: el Anillo y las hojas de la Comarca. */
    FRODO_RING,
    /** Esika, God of the Tree: el arbol y el puente prismatico. */
    ESIKA_PRISM,
    /** Bello, Bard of the Brambles: notas y zarzas. */
    BELLO_SONG,
    /** Hakbal of the Surging Soul: agua y exploracion. */
    HAKBAL_TIDE,

    // La septima tanda de EDHREC (04-11-2026).
    /** Rin and Seri, Inseparable: huellas de gato y perro que se multiplican. */
    RIN_SERI_PAWS,
    /** Glarb, Calamity's Augur: cartas que giran y motas del pantano. */
    GLARB_SIGHT,
    /** Maralen, Fae Ascendant: luces de hada. */
    MARALEN_FAE,
    /** Kuja, Genome Sorcerer: la estrella fulgurante. */
    KUJA_FLARE,
    /** Aragorn, the Uniter: la espada y sus cuatro colores. */
    ARAGORN_BLADE,
    /** Mr. House, President and CEO: dados y fichas de casino. */
    HOUSE_DICE,
    /** Ygra, Eater of All: todo se vuelve comida. */
    YGRA_FEAST,
    /** Caesar, Legion's Emperor: escudos de la legion. */
    CAESAR_LEGION,
    /** Ureni of the Unwritten: el dragon espiritu. */
    URENI_DRAGON,
    /** Oloro, Ageless Ascetic: la vida que vuelve. */
    OLORO_LIFE,

    // La octava tanda de EDHREC (04-11-2026).
    /** Flubs, the Fool: el chapuzon y las cartas que van y vienen. */
    FLUBS_SPLASH,
    /** The Necrobloom: brotes que se multiplican y zombis. */
    NECROBLOOM_SPROUTS,
    /** Tidus, Yuna's Guardian: las luciernagas del Farplane, el agua y la pelota. */
    TIDUS_BLITZ,
    /** Zhulodok, Void Gorger: el cascade, cartas que se traga el vacio. */
    ZHULODOK_CASCADE,
    /** Zurgo Stormrender: la tormenta y los guerreros que se multiplican. */
    ZURGO_STORM,
    /** Atla Palani, Nest Tender: los huevos que eclosionan. */
    ATLA_EGGS,
    /** The Wandering Minstrel: la balada y todos los colores. */
    MINSTREL_BALLAD,
    /** Felothar the Steadfast: el escudo y la roca. */
    FELOTHAR_SHIELD,
    /** Tom Bombadil: canciones y flores. */
    BOMBADIL_SONG,
    /** Avatar Aang: los cuatro elementos. */
    AANG_ELEMENTS,

    // La novena tanda de EDHREC (04-11-2026).
    /** Queen Marchesa: la corona del monarca y la daga. */
    MARCHESA_CROWN,
    /** Terra, Herald of Hope: el Trance, su magia rosa. */
    TERRA_TRANCE,
    /** Shorikai, Genesis Engine: engranajes y pilotos. */
    SHORIKAI_ENGINE,
    /** Arabella, Abandoned Doll: corazones de trapo. */
    ARABELLA_HEARTS,
    /** Betor, Ancestor's Voice: las almas de los ancestros. */
    BETOR_ANCESTORS,
    /** Choco, Seeker of Paradise: plumas de chocobo. */
    CHOCO_FEATHERS,
    /** Auntie Ool, Cursewretch: maldiciones y calaveras. */
    OOL_CURSE,
    /** Helga, Skittish Seer: la vidente del estanque. */
    HELGA_POND,
    /** Go-Shintai of Life's Origin: el santuario y los cinco colores. */
    SHINTAI_SHRINE,
    /** Kotis, the Fangkeeper: la biblioteca del rival, exiliada. */
    KOTIS_FANGS,

    // La decima tanda de EDHREC (04-11-2026).
    /** K'rrik, Son of Yawgmoth: la vida que se paga y el aceite. */
    KRRIK_BLOOD,
    /** Zaxara, the Exemplary: hidras que se multiplican, de noche. */
    ZAXARA_HYDRAS,
    /** Kilo, Apogee Mind: el engranaje y los contadores que proliferan. */
    KILO_PROLIFERATE,
    /** Sisay, Weatherlight Captain: el viento y los cinco colores. */
    SISAY_WEATHERLIGHT,
    /** The First Sliver: el cascade y la colmena. */
    FIRST_SLIVER_CASCADE,
    /** Xyris, the Writhing Storm: la tormenta y el agua. */
    XYRIS_STORM,
    /** Ghyrson Starn, Kelermorph: tres disparos. */
    GHYRSON_SHOTS,
    /** Norman Osborn / Green Goblin: las bombas calabaza. */
    GOBLIN_BOMBS,
    /** Ragost, Deft Gastronaut: los fogones y la comida. */
    RAGOST_KITCHEN,
    /** Eriette of the Charmed Apple: la manzana hechizada. */
    ERIETTE_APPLE,

    // La undecima tanda de EDHREC (04-11-2026).
    /** Hope Estheim: su boomerang y las cartas que pierde el rival. */
    HOPE_BOOMERANG,
    /** Henzie "Toolbox" Torre: el blitz, fuego y prisa. */
    HENZIE_BLITZ,
    /** Captain N'ghathrod: el mar negro y las cartas que se hunden. */
    NGHATHROD_DEPTHS,
    /** Urtet, Remnant of Memnarch: los myr que se multiplican. */
    URTET_MYR,
    /** Witherbloom, the Balancer: las almas que se cobra. */
    WITHERBLOOM_DRAIN,
    /** High Perfect Morcant: la hoja elfa y la plaga. */
    MORCANT_BLIGHT,
    /** Voja, Jaws of the Conclave: la luna del lobo. */
    VOJA_MOON,
    /** Alela, Cunning Conqueror: las hadas que se multiplican. */
    ALELA_FAERIES,
    /** Magda, Brazen Outlaw: el hacha y los tesoros. */
    MAGDA_TREASURE,
    /** Obeka, Splitter of Seconds: el reloj y el tiempo partido. */
    OBEKA_CLOCK,

    // La duodecima tanda de EDHREC (05-11-2026).
    /** Marneus Calgar: los escudos de sus Astartes que se multiplican. */
    MARNEUS_ASTARTES,
    /** Zinnia, Valley's Voice: el canto que se multiplica (offspring) y sus plumas. */
    ZINNIA_SONG,
    /** Sidar Jabari of Zhalfir: los tajos del caballero volador. */
    SIDAR_KNIGHT,
    /** Deadpool, Trading Card: las katanas, las cartas y los corazones. */
    DEADPOOL_CARDS,
    /** Ezio Auditore da Firenze: el salto de fe, la hoja oculta y el humo. */
    EZIO_LEAP,
    /** Mendicant Core, Guidelight: la velocidad (Start your engines!). */
    MENDICANT_SPEED,
    /** Etali, Primal Conqueror: rocas, fuego y el dinosaurio primigenio. */
    ETALI_PRIMAL,
    /** Galadriel, Light of Valinor: la luz de la estrella y las hojas de oro. */
    GALADRIEL_LIGHT,
    /** Urza, Chief Artificer: los engranajes de sus constructos. */
    URZA_ARTIFICE,
    /** Kynaios and Tiro of Meletis: el sol de Theros y las cartas para todos. */
    KYNAIOS_GIFTS,

    // La decimotercera tanda de EDHREC (05-11-2026).
    /** Morophon, the Boundless: los cinco colores girando (es de todos los tipos). */
    MOROPHON_ALL,
    /** Fynn, the Fangbearer: los colmillos y el veneno. */
    FYNN_VENOM,
    /** Tifa Lockhart: el punyetazo al suelo y la fuerza que se duplica. */
    TIFA_FISTS,
    /** Zada, Hedron Grinder: el hechizo que se copia en cada duende. */
    ZADA_COPIES,
    /** Tovolar, Dire Overlord: la luna y los zarpazos del lobo. */
    TOVOLAR_MOON,
    /** Captain America, First Avenger: el escudo lanzado. */
    CAPTAIN_SHIELD,
    /** Iroh, Grand Lotus: el loto, el fuego y el te. */
    IROH_LOTUS,
    /** Storm, Force of Nature: los rayos y la lluvia. */
    STORM_WEATHER,
    /** Be'lakor, the Dark Master: el fuego oscuro y los murcielagos. */
    BELAKOR_DARK,
    /** Eowyn, Shieldmaiden: el tajo de la doncella escudera. */
    EOWYN_BLADE,

    // La decimocuarta tanda de EDHREC (05-11-2026).
    /** Brago, King Eternal: la corona y lo que parpadea. */
    BRAGO_BLINK,
    /** Lord of the Nazgul: los nueve anillos y la sombra. */
    NAZGUL_RINGS,
    /** Eshki, Temur's Roar: los zarpazos y el fuego del dragon. */
    ESHKI_ROAR,
    /** Magus Lucea Kane: lo que se copia (los hechizos con X). */
    LUCEA_COPIES,
    /** Aminatou, Veil Piercer: el ojo que atraviesa el velo. */
    AMINATOU_VEIL,
    /** Kibo, Uktabi Prince: la fruta y el mono. */
    KIBO_FRUIT,
    /** Inalla, Archmage Ritualist: los magos que se copian. */
    INALLA_WIZARDS,
    /** Satya, Aetherflux Genius: la energia y el eter. */
    SATYA_AETHER,
    /** Zimone, Mystery Unraveler: las cartas boca abajo y lo que brota. */
    ZIMONE_MYSTERY,
    /** Jace, Multiverse Architect: los planos que giran. */
    JACE_MULTIVERSE,

    // La decimoquinta tanda de EDHREC (05-11-2026).
    /** Cosmic Spider-Man: el poder cosmico y los cinco colores. */
    SPIDER_COSMIC,
    /** Winota, Joiner of Forces: los escudos que llegan y el tajo. */
    WINOTA_FORCES,
    /** Satoru Umezawa: los shuriken y el humo del ninjutsu. */
    SATORU_NINJA,
    /** The Scarab God: los escarabajos y las almas que vuelven. */
    SCARAB_GOD,
    /** Eluge, the Shoreless Sea: la inundacion. */
    ELUGE_FLOOD,
    /** Marrow-Gnawer: las ratas que se multiplican. */
    MARROW_RATS,
    /** Kykar, Wind's Fury: el viento, las plumas y los espiritus. */
    KYKAR_WIND,
    /** Inspirit, Flagship Vessel: los engranajes y la estacion que se carga. */
    INSPIRIT_STATION,
    /** Temmet, Naktamun's Will: el sol de Amonkhet y los zombis que se alzan. */
    TEMMET_EMBALM,
    /** Omnath, Locus of All: el mana de los cinco colores que se cierra sobre el. */
    OMNATH_ALL;

    /**
     * Su coreografia, para una pantalla de ese tamanyo; null en {@link #NONE}.
     * Se crea una por entrada: sortea sus parametros una vez y ya no guarda nada.
     */
    public SignatureMotion motion(final double width, final double height) {
        switch (this) {
            case BLACK_FEATHERS:
                return new BlackFeathers(width, height);
            case CLOUD_SLASHES:
                return new CloudSlashes(width, height);
            case JIN_LEAVES:
                return new JinLeaves(width, height);
            case YSHTOLA_MOON:
                return LayeredSignature.yshtola(width, height);
            case EDGAR_BATS:
                return LayeredSignature.edgar(width, height);
            case URDRAGON_FIRE:
                return LayeredSignature.urDragon(width, height);
            case ATRAXA_LIGHT:
                return LayeredSignature.atraxa(width, height);
            case KRENKO_SWARM:
                return LayeredSignature.krenko(width, height);
            case VIVI_FIRE:
                return LayeredSignature.vivi(width, height);
            case SAURON_EYE:
                return LayeredSignature.sauron(width, height);
            case KAALIA_HOSTS:
                return LayeredSignature.kaalia(width, height);
            case TEVAL_BALANCE:
                return LayeredSignature.teval(width, height);
            case AZULA_LIGHTNING:
                return LayeredSignature.azula(width, height);
            case ISSHIN_INK:
                return LayeredSignature.isshin(width, height);
            case GIADA_ANGELS:
                return LayeredSignature.giada(width, height);
            case PANTLAZA_SUN:
                return LayeredSignature.pantlaza(width, height);
            case BUMBLEFLOWER_PETALS:
                return LayeredSignature.bumbleflower(width, height);
            case LATHRIL_BLADES:
                return LayeredSignature.lathril(width, height);
            case TOPH_EARTH:
                return LayeredSignature.toph(width, height);
            case MOTHMAN_MOTHS:
                return LayeredSignature.mothman(width, height);
            case JODAH_PRISM:
                return LayeredSignature.jodah(width, height);
            case HEARTHHULL_COSMOS:
                return LayeredSignature.hearthhull(width, height);
            case NEKUSAR_WHEEL:
                return LayeredSignature.nekusar(width, height);
            case KEFKA_JUDGMENT:
                return LayeredSignature.kefka(width, height);
            case LIGHTNING_BOLTS:
                return LayeredSignature.lightning(width, height);
            case YURIKO_SHURIKEN:
                return LayeredSignature.yuriko(width, height);
            case MIIRYM_FLAMES:
                return LayeredSignature.miirym(width, height);
            case MULDROTHA_EARTH:
                return LayeredSignature.muldrotha(width, height);
            case MEREN_SOULS:
                return LayeredSignature.meren(width, height);
            case KORVOLD_GOLD:
                return LayeredSignature.korvold(width, height);
            case PROSPER_FLAMES:
                return LayeredSignature.prosper(width, height);
            case YAWGMOTH_OIL:
                return LayeredSignature.yawgmoth(width, height);
            case CHULANE_TALES:
                return LayeredSignature.chulane(width, height);
            case WILHELT_ROT:
                return LayeredSignature.wilhelt(width, height);
            case TIAMAT_HEADS:
                return LayeredSignature.tiamat(width, height);
            case GISHATH_SUN:
                return LayeredSignature.gishath(width, height);
            case BREYA_GEARS:
                return LayeredSignature.breya(width, height);
            case SLIVER_HIVE:
                return LayeredSignature.sliverOverlord(width, height);
            case KENRITH_CROWN:
                return LayeredSignature.kenrith(width, height);
            case OMNATH_LANDS:
                return LayeredSignature.omnath(width, height);
            case NIV_MIZZET_BOLTS:
                return LayeredSignature.nivMizzet(width, height);
            case ARCADES_WALLS:
                return LayeredSignature.arcades(width, height);
            case GITROG_MUD:
                return LayeredSignature.gitrog(width, height);
            case LIGHT_PAWS_GRACE:
                return LayeredSignature.lightPaws(width, height);
            case KINNAN_MANA:
                return LayeredSignature.kinnan(width, height);
            case WINDGRACE_LANDS:
                return LayeredSignature.windgrace(width, height);
            case ZUR_STARS:
                return LayeredSignature.zur(width, height);
            case SYTHIS_PETALS:
                return LayeredSignature.sythis(width, height);
            case TEYSA_SOULS:
                return LayeredSignature.teysa(width, height);
            case AESI_TIDE:
                return LayeredSignature.aesi(width, height);
            case ANIMAR_ELEMENTS:
                return LayeredSignature.animar(width, height);
            case URZA_GEARS:
                return LayeredSignature.urza(width, height);
            case DEREVI_WIND:
                return LayeredSignature.derevi(width, height);
            case FEATHER_RED:
                return LayeredSignature.feather(width, height);
            case NAJEELA_BLADES:
                return LayeredSignature.najeela(width, height);
            case BAYLEN_HAY:
                return LayeredSignature.baylen(width, height);
            case VALGAVOTH_SOULS:
                return LayeredSignature.valgavoth(width, height);
            case HASHATON_SCARABS:
                return LayeredSignature.hashaton(width, height);
            case CHATTERFANG_ACORNS:
                return LayeredSignature.chatterfang(width, height);
            case ULALEK_VOID:
                return LayeredSignature.ulalek(width, height);
            case ASHLING_FLAMES:
                return LayeredSignature.ashling(width, height);
            case FRODO_RING:
                return LayeredSignature.frodo(width, height);
            case ESIKA_PRISM:
                return LayeredSignature.esika(width, height);
            case BELLO_SONG:
                return LayeredSignature.bello(width, height);
            case HAKBAL_TIDE:
                return LayeredSignature.hakbal(width, height);
            case RIN_SERI_PAWS:
                return LayeredSignature.rinSeri(width, height);
            case GLARB_SIGHT:
                return LayeredSignature.glarb(width, height);
            case MARALEN_FAE:
                return LayeredSignature.maralen(width, height);
            case KUJA_FLARE:
                return LayeredSignature.kuja(width, height);
            case ARAGORN_BLADE:
                return LayeredSignature.aragorn(width, height);
            case HOUSE_DICE:
                return LayeredSignature.house(width, height);
            case YGRA_FEAST:
                return LayeredSignature.ygra(width, height);
            case CAESAR_LEGION:
                return LayeredSignature.caesar(width, height);
            case URENI_DRAGON:
                return LayeredSignature.ureni(width, height);
            case OLORO_LIFE:
                return LayeredSignature.oloro(width, height);
            case FLUBS_SPLASH:
                return LayeredSignature.flubs(width, height);
            case NECROBLOOM_SPROUTS:
                return LayeredSignature.necrobloom(width, height);
            case TIDUS_BLITZ:
                return LayeredSignature.tidus(width, height);
            case ZHULODOK_CASCADE:
                return LayeredSignature.zhulodok(width, height);
            case ZURGO_STORM:
                return LayeredSignature.zurgo(width, height);
            case ATLA_EGGS:
                return LayeredSignature.atla(width, height);
            case MINSTREL_BALLAD:
                return LayeredSignature.minstrel(width, height);
            case FELOTHAR_SHIELD:
                return LayeredSignature.felothar(width, height);
            case BOMBADIL_SONG:
                return LayeredSignature.bombadil(width, height);
            case AANG_ELEMENTS:
                return LayeredSignature.aang(width, height);
            case MARCHESA_CROWN:
                return LayeredSignature.marchesa(width, height);
            case TERRA_TRANCE:
                return LayeredSignature.terra(width, height);
            case SHORIKAI_ENGINE:
                return LayeredSignature.shorikai(width, height);
            case ARABELLA_HEARTS:
                return LayeredSignature.arabella(width, height);
            case BETOR_ANCESTORS:
                return LayeredSignature.betor(width, height);
            case CHOCO_FEATHERS:
                return LayeredSignature.choco(width, height);
            case OOL_CURSE:
                return LayeredSignature.ool(width, height);
            case HELGA_POND:
                return LayeredSignature.helga(width, height);
            case SHINTAI_SHRINE:
                return LayeredSignature.shintai(width, height);
            case KOTIS_FANGS:
                return LayeredSignature.kotis(width, height);
            case KRRIK_BLOOD:
                return LayeredSignature.krrik(width, height);
            case ZAXARA_HYDRAS:
                return LayeredSignature.zaxara(width, height);
            case KILO_PROLIFERATE:
                return LayeredSignature.kilo(width, height);
            case SISAY_WEATHERLIGHT:
                return LayeredSignature.sisay(width, height);
            case FIRST_SLIVER_CASCADE:
                return LayeredSignature.firstSliver(width, height);
            case XYRIS_STORM:
                return LayeredSignature.xyris(width, height);
            case GHYRSON_SHOTS:
                return LayeredSignature.ghyrson(width, height);
            case GOBLIN_BOMBS:
                return LayeredSignature.goblin(width, height);
            case RAGOST_KITCHEN:
                return LayeredSignature.ragost(width, height);
            case ERIETTE_APPLE:
                return LayeredSignature.eriette(width, height);
            case HOPE_BOOMERANG:
                return LayeredSignature.hope(width, height);
            case HENZIE_BLITZ:
                return LayeredSignature.henzie(width, height);
            case NGHATHROD_DEPTHS:
                return LayeredSignature.nghathrod(width, height);
            case URTET_MYR:
                return LayeredSignature.urtet(width, height);
            case WITHERBLOOM_DRAIN:
                return LayeredSignature.witherbloom(width, height);
            case MORCANT_BLIGHT:
                return LayeredSignature.morcant(width, height);
            case VOJA_MOON:
                return LayeredSignature.voja(width, height);
            case ALELA_FAERIES:
                return LayeredSignature.alela(width, height);
            case MAGDA_TREASURE:
                return LayeredSignature.magda(width, height);
            case OBEKA_CLOCK:
                return LayeredSignature.obeka(width, height);
            case MARNEUS_ASTARTES:
                return LayeredSignature.marneus(width, height);
            case ZINNIA_SONG:
                return LayeredSignature.zinnia(width, height);
            case SIDAR_KNIGHT:
                return LayeredSignature.sidar(width, height);
            case DEADPOOL_CARDS:
                return LayeredSignature.deadpool(width, height);
            case EZIO_LEAP:
                return LayeredSignature.ezio(width, height);
            case MENDICANT_SPEED:
                return LayeredSignature.mendicant(width, height);
            case ETALI_PRIMAL:
                return LayeredSignature.etali(width, height);
            case GALADRIEL_LIGHT:
                return LayeredSignature.galadriel(width, height);
            case URZA_ARTIFICE:
                return LayeredSignature.urzaChief(width, height);
            case KYNAIOS_GIFTS:
                return LayeredSignature.kynaios(width, height);
            case MOROPHON_ALL:
                return LayeredSignature.morophon(width, height);
            case FYNN_VENOM:
                return LayeredSignature.fynn(width, height);
            case TIFA_FISTS:
                return LayeredSignature.tifa(width, height);
            case ZADA_COPIES:
                return LayeredSignature.zada(width, height);
            case TOVOLAR_MOON:
                return LayeredSignature.tovolar(width, height);
            case CAPTAIN_SHIELD:
                return LayeredSignature.captainAmerica(width, height);
            case IROH_LOTUS:
                return LayeredSignature.iroh(width, height);
            case STORM_WEATHER:
                return LayeredSignature.storm(width, height);
            case BELAKOR_DARK:
                return LayeredSignature.belakor(width, height);
            case EOWYN_BLADE:
                return LayeredSignature.eowyn(width, height);
            case BRAGO_BLINK:
                return LayeredSignature.brago(width, height);
            case NAZGUL_RINGS:
                return LayeredSignature.nazgul(width, height);
            case ESHKI_ROAR:
                return LayeredSignature.eshki(width, height);
            case LUCEA_COPIES:
                return LayeredSignature.lucea(width, height);
            case AMINATOU_VEIL:
                return LayeredSignature.aminatou(width, height);
            case KIBO_FRUIT:
                return LayeredSignature.kibo(width, height);
            case INALLA_WIZARDS:
                return LayeredSignature.inalla(width, height);
            case SATYA_AETHER:
                return LayeredSignature.satya(width, height);
            case ZIMONE_MYSTERY:
                return LayeredSignature.zimone(width, height);
            case JACE_MULTIVERSE:
                return LayeredSignature.jaceArchitect(width, height);
            case SPIDER_COSMIC:
                return LayeredSignature.cosmicSpider(width, height);
            case WINOTA_FORCES:
                return LayeredSignature.winota(width, height);
            case SATORU_NINJA:
                return LayeredSignature.satoru(width, height);
            case SCARAB_GOD:
                return LayeredSignature.scarabGod(width, height);
            case ELUGE_FLOOD:
                return LayeredSignature.eluge(width, height);
            case MARROW_RATS:
                return LayeredSignature.marrowGnawer(width, height);
            case KYKAR_WIND:
                return LayeredSignature.kykar(width, height);
            case INSPIRIT_STATION:
                return LayeredSignature.inspirit(width, height);
            case TEMMET_EMBALM:
                return LayeredSignature.temmet(width, height);
            case OMNATH_ALL:
                return LayeredSignature.omnathAll(width, height);
            default:
                return null;
        }
    }

    /** Lo que dura de mas una entrada con firma, para que se vea su final. */
    public static final double EXTRA_SECONDS = 0.4;

    /**
     * Cuanto mas despacio va la entrada ENTERA (carta, rayos y firma): 1 en
     * todas menos Isshin, que va un 30% mas lenta para que se lea la tinta
     * (Ana, 21-10-2026: "un 30% mas larga ... pero solo esta"). Los dos
     * pintores dividen el tiempo por esto.
     */
    public double slowdown() {
        return this == ISSHIN_INK ? 1.3 : 1;
    }

    private static final Map<String, CommanderSignature> BY_NAME = new HashMap<>();
    /** Las que solo llevan firma si el comandante es tuyo. */
    private static final Map<String, CommanderSignature> MINE_ONLY = new HashMap<>();

    static {
        put(BLACK_FEATHERS,
                "Sephiroth, Fabled SOLDIER", "Sephiroth, One-Winged Angel",
                "Sephiroth, Planet's Heir", "Sephiroth, Fallen Hero");
        // "Sephiroth, the Savior" (FCA 49), el comandante del autor (18-10-2026).
        putMine(BLACK_FEATHERS, "Atraxa, Grand Unifier");
        // Los otros dos del autor (18-10-2026: "solo haz para jin sakai y cloud").
        put(CLOUD_SLASHES, "Cloud, Ex-SOLDIER", "Cloud, Midgar Mercenary", "Cloud, Planet's Champion");
        put(JIN_LEAVES, "Jin Sakai, Ghost of Tsushima");
        // Los diez mas jugados de EDH (EDHREC, 20-10-2026).
        put(YSHTOLA_MOON, "Y'shtola, Night's Blessed");
        put(EDGAR_BATS, "Edgar Markov");
        put(URDRAGON_FIRE, "The Ur-Dragon");
        put(ATRAXA_LIGHT, "Atraxa, Praetors' Voice");
        put(KRENKO_SWARM, "Krenko, Mob Boss");
        put(VIVI_FIRE, "Vivi Ornitier");
        put(SAURON_EYE, "Sauron, the Dark Lord");
        put(KAALIA_HOSTS, "Kaalia of the Vast");
        put(TEVAL_BALANCE, "Teval, the Balanced Scale");
        put(AZULA_LIGHTNING, "Fire Lord Azula");
        // Pedida por Ana (20-10-2026): Musashi, el de Vagabond, sin el personaje.
        put(ISSHIN_INK, "Isshin, Two Heavens as One");
        // Los diez siguientes de EDHREC (21-10-2026) y Lightning (Final Fantasy XIII).
        put(GIADA_ANGELS, "Giada, Font of Hope");
        put(PANTLAZA_SUN, "Pantlaza, Sun-Favored");
        put(BUMBLEFLOWER_PETALS, "Ms. Bumbleflower");
        put(LATHRIL_BLADES, "Lathril, Blade of the Elves");
        put(TOPH_EARTH, "Toph, the First Metalbender");
        put(MOTHMAN_MOTHS, "The Wise Mothman");
        put(JODAH_PRISM, "Jodah, the Unifier");
        put(HEARTHHULL_COSMOS, "Hearthhull, the Worldseed");
        put(NEKUSAR_WHEEL, "Nekusar, the Mindrazer");
        put(KEFKA_JUDGMENT, "Kefka, Court Mage", "Kefka, Ruler of Ruin");
        put(LIGHTNING_BOLTS, "Lightning, Army of One");
        // La tercera tanda de EDHREC (22-10-2026: "sigue anyadiendo animaciones a
        // comandantes famosos").
        put(YURIKO_SHURIKEN, "Yuriko, the Tiger's Shadow");
        put(MIIRYM_FLAMES, "Miirym, Sentinel Wyrm");
        put(MULDROTHA_EARTH, "Muldrotha, the Gravetide");
        put(MEREN_SOULS, "Meren of Clan Nel Toth");
        put(KORVOLD_GOLD, "Korvold, Fae-Cursed King");
        put(PROSPER_FLAMES, "Prosper, Tome-Bound");
        put(YAWGMOTH_OIL, "Yawgmoth, Thran Physician");
        put(CHULANE_TALES, "Chulane, Teller of Tales");
        put(WILHELT_ROT, "Wilhelt, the Rotcleaver");
        put(TIAMAT_HEADS, "Tiamat");
        // La cuarta tanda de EDHREC (22-10-2026).
        put(GISHATH_SUN, "Gishath, Sun's Avatar");
        put(BREYA_GEARS, "Breya, Etherium Shaper");
        put(SLIVER_HIVE, "Sliver Overlord");
        put(KENRITH_CROWN, "Kenrith, the Returned King");
        put(OMNATH_LANDS, "Omnath, Locus of Creation");
        put(NIV_MIZZET_BOLTS, "Niv-Mizzet, Parun");
        put(ARCADES_WALLS, "Arcades, the Strategist");
        put(GITROG_MUD, "The Gitrog Monster");
        put(LIGHT_PAWS_GRACE, "Light-Paws, Emperor's Voice");
        put(KINNAN_MANA, "Kinnan, Bonder Prodigy");
        // La quinta tanda de EDHREC (22-10-2026).
        put(WINDGRACE_LANDS, "Lord Windgrace");
        put(ZUR_STARS, "Zur the Enchanter");
        put(SYTHIS_PETALS, "Sythis, Harvest's Hand");
        put(TEYSA_SOULS, "Teysa Karlov");
        put(AESI_TIDE, "Aesi, Tyrant of Gyre Strait");
        put(ANIMAR_ELEMENTS, "Animar, Soul of Elements");
        put(URZA_GEARS, "Urza, Lord High Artificer");
        put(DEREVI_WIND, "Derevi, Empyrial Tactician");
        put(FEATHER_RED, "Feather, the Redeemed");
        put(NAJEELA_BLADES, "Najeela, the Blade-Blossom");
        // La sexta tanda de EDHREC (04-11-2026: "dale a los 10 siguientes").
        put(BAYLEN_HAY, "Baylen, the Haymaker");
        put(VALGAVOTH_SOULS, "Valgavoth, Harrower of Souls");
        put(HASHATON_SCARABS, "Hashaton, Scarab's Fist");
        put(CHATTERFANG_ACORNS, "Chatterfang, Squirrel General");
        put(ULALEK_VOID, "Ulalek, Fused Atrocity");
        put(ASHLING_FLAMES, "Ashling, the Limitless");
        put(FRODO_RING, "Frodo, Adventurous Hobbit");
        put(ESIKA_PRISM, "Esika, God of the Tree");
        put(BELLO_SONG, "Bello, Bard of the Brambles");
        put(HAKBAL_TIDE, "Hakbal of the Surging Soul");
        // La septima tanda de EDHREC (04-11-2026: "haz las 10 siguientes").
        put(RIN_SERI_PAWS, "Rin and Seri, Inseparable");
        put(GLARB_SIGHT, "Glarb, Calamity's Augur");
        put(MARALEN_FAE, "Maralen, Fae Ascendant");
        put(KUJA_FLARE, "Kuja, Genome Sorcerer", "Trance Kuja, Fate Defied");
        put(ARAGORN_BLADE, "Aragorn, the Uniter");
        put(HOUSE_DICE, "Mr. House, President and CEO");
        put(YGRA_FEAST, "Ygra, Eater of All");
        put(CAESAR_LEGION, "Caesar, Legion's Emperor");
        put(URENI_DRAGON, "Ureni of the Unwritten");
        put(OLORO_LIFE, "Oloro, Ageless Ascetic");
        // La octava tanda de EDHREC (04-11-2026: "dale cana, calidad").
        put(FLUBS_SPLASH, "Flubs, the Fool");
        put(NECROBLOOM_SPROUTS, "The Necrobloom");
        put(TIDUS_BLITZ, "Tidus, Yuna's Guardian");
        put(ZHULODOK_CASCADE, "Zhulodok, Void Gorger");
        put(ZURGO_STORM, "Zurgo Stormrender");
        put(ATLA_EGGS, "Atla Palani, Nest Tender");
        put(MINSTREL_BALLAD, "The Wandering Minstrel");
        put(FELOTHAR_SHIELD, "Felothar the Steadfast");
        put(BOMBADIL_SONG, "Tom Bombadil");
        put(AANG_ELEMENTS, "Avatar Aang", "Aang, Master of Elements");
        // La novena tanda de EDHREC (04-11-2026).
        put(MARCHESA_CROWN, "Queen Marchesa");
        put(TERRA_TRANCE, "Terra, Herald of Hope", "Terra, Magical Adept", "Esper Terra");
        put(SHORIKAI_ENGINE, "Shorikai, Genesis Engine");
        put(ARABELLA_HEARTS, "Arabella, Abandoned Doll");
        put(BETOR_ANCESTORS, "Betor, Ancestor's Voice");
        put(CHOCO_FEATHERS, "Choco, Seeker of Paradise");
        put(OOL_CURSE, "Auntie Ool, Cursewretch");
        put(HELGA_POND, "Helga, Skittish Seer");
        put(SHINTAI_SHRINE, "Go-Shintai of Life's Origin");
        put(KOTIS_FANGS, "Kotis, the Fangkeeper");
        // La decima tanda de EDHREC (04-11-2026).
        put(KRRIK_BLOOD, "K'rrik, Son of Yawgmoth");
        put(ZAXARA_HYDRAS, "Zaxara, the Exemplary");
        put(KILO_PROLIFERATE, "Kilo, Apogee Mind");
        put(SISAY_WEATHERLIGHT, "Sisay, Weatherlight Captain");
        put(FIRST_SLIVER_CASCADE, "The First Sliver");
        put(XYRIS_STORM, "Xyris, the Writhing Storm");
        put(GHYRSON_SHOTS, "Ghyrson Starn, Kelermorph");
        put(GOBLIN_BOMBS, "Norman Osborn", "Green Goblin");
        put(RAGOST_KITCHEN, "Ragost, Deft Gastronaut");
        put(ERIETTE_APPLE, "Eriette of the Charmed Apple");
        // La undecima tanda de EDHREC (04-11-2026).
        put(HOPE_BOOMERANG, "Hope Estheim");
        put(HENZIE_BLITZ, "Henzie \"Toolbox\" Torre");
        put(NGHATHROD_DEPTHS, "Captain N'ghathrod");
        put(URTET_MYR, "Urtet, Remnant of Memnarch");
        put(WITHERBLOOM_DRAIN, "Witherbloom, the Balancer");
        put(MORCANT_BLIGHT, "High Perfect Morcant");
        put(VOJA_MOON, "Voja, Jaws of the Conclave");
        put(ALELA_FAERIES, "Alela, Cunning Conqueror");
        put(MAGDA_TREASURE, "Magda, Brazen Outlaw");
        put(OBEKA_CLOCK, "Obeka, Splitter of Seconds");
        // La duodecima tanda de EDHREC (05-11-2026), y Alela, Artful Provocateur
        // con las hadas de la otra Alela: es la misma, y tambien hace hadas.
        put(ALELA_FAERIES, "Alela, Artful Provocateur");
        put(MARNEUS_ASTARTES, "Marneus Calgar");
        put(ZINNIA_SONG, "Zinnia, Valley's Voice");
        put(SIDAR_KNIGHT, "Sidar Jabari of Zhalfir");
        put(DEADPOOL_CARDS, "Deadpool, Trading Card");
        put(EZIO_LEAP, "Ezio Auditore da Firenze");
        put(MENDICANT_SPEED, "Mendicant Core, Guidelight");
        put(ETALI_PRIMAL, "Etali, Primal Conqueror", "Etali, Primal Sickness");
        put(GALADRIEL_LIGHT, "Galadriel, Light of Valinor");
        put(URZA_ARTIFICE, "Urza, Chief Artificer");
        put(KYNAIOS_GIFTS, "Kynaios and Tiro of Meletis");
        // La decimotercera tanda de EDHREC (05-11-2026).
        put(MOROPHON_ALL, "Morophon, the Boundless");
        put(FYNN_VENOM, "Fynn, the Fangbearer");
        put(TIFA_FISTS, "Tifa Lockhart");
        put(ZADA_COPIES, "Zada, Hedron Grinder");
        put(TOVOLAR_MOON, "Tovolar, Dire Overlord", "Tovolar, the Midnight Scourge");
        put(CAPTAIN_SHIELD, "Captain America, First Avenger");
        put(IROH_LOTUS, "Iroh, Grand Lotus");
        put(STORM_WEATHER, "Storm, Force of Nature");
        put(BELAKOR_DARK, "Be'lakor, the Dark Master");
        put(EOWYN_BLADE, "\u00C9owyn, Shieldmaiden");
        // La decimocuarta tanda de EDHREC (05-11-2026).
        put(BRAGO_BLINK, "Brago, King Eternal");
        put(NAZGUL_RINGS, "Lord of the Nazg\u00FBl");
        put(ESHKI_ROAR, "Eshki, Temur's Roar");
        put(LUCEA_COPIES, "Magus Lucea Kane");
        put(AMINATOU_VEIL, "Aminatou, Veil Piercer");
        put(KIBO_FRUIT, "Kibo, Uktabi Prince");
        put(INALLA_WIZARDS, "Inalla, Archmage Ritualist");
        put(SATYA_AETHER, "Satya, Aetherflux Genius");
        put(ZIMONE_MYSTERY, "Zimone, Mystery Unraveler");
        put(JACE_MULTIVERSE, "Jace, Multiverse Architect");
        // La decimoquinta tanda de EDHREC (05-11-2026).
        put(SPIDER_COSMIC, "Cosmic Spider-Man");
        put(WINOTA_FORCES, "Winota, Joiner of Forces");
        put(SATORU_NINJA, "Satoru Umezawa");
        put(SCARAB_GOD, "The Scarab God");
        put(ELUGE_FLOOD, "Eluge, the Shoreless Sea");
        put(MARROW_RATS, "Marrow-Gnawer");
        put(KYKAR_WIND, "Kykar, Wind's Fury");
        put(INSPIRIT_STATION, "Inspirit, Flagship Vessel");
        put(TEMMET_EMBALM, "Temmet, Naktamun's Will");
        put(OMNATH_ALL, "Omnath, Locus of All");
    }

    private static void put(final CommanderSignature signature, final String... names) {
        for (final String n : names) {
            BY_NAME.put(n.toLowerCase(Locale.ROOT), signature);
        }
    }

    private static void putMine(final CommanderSignature signature, final String... names) {
        for (final String n : names) {
            MINE_ONLY.put(n.toLowerCase(Locale.ROOT), signature);
        }
    }

    /**
     * La firma de ese nombre de carta (en ingles); {@link #NONE} si no tiene.
     *
     * @param mine si el comandante es del jugador de esta pantalla
     */
    public static CommanderSignature of(final String englishName, final boolean mine) {
        if (englishName == null) {
            return NONE;
        }
        final String key = englishName.toLowerCase(Locale.ROOT);
        CommanderSignature s = BY_NAME.get(key);
        if (s == null && mine) {
            s = MINE_ONLY.get(key);
        }
        return s == null ? NONE : s;
    }

    /**
     * La firma de ese comandante, mirando sus dos caras. Nunca lanza.
     *
     * @param mine si es del jugador de esta pantalla
     */
    public static CommanderSignature of(final CardView card, final boolean mine) {
        final String forced = System.getProperty("neo.entrance.signature");
        if (forced != null) {
            try {
                return valueOf(forced.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                // Una bandera mal escrita no rompe la entrada: la de la carta.
            }
        }
        if (card == null) {
            return NONE;
        }
        try {
            // EL NOMBRE DE VERDAD (getOracleName), no el que se ve: getName() es
            // el nombre alternativo de la impresion, y la Atraxa de FCA se llama
            // ahi "Sephiroth, the Savior". Despues el que se ve, por si acaso.
            CommanderSignature s = of(card.getOracleName(), mine);
            if (s == NONE && card.getCurrentState() != null) {
                s = of(card.getCurrentState().getOracleName(), mine);
            }
            if (s == NONE && card.getAlternateState() != null) {
                s = of(card.getAlternateState().getOracleName(), mine);
            }
            if (s == NONE) {
                s = of(card.getName(), mine);
            }
            return s;
        } catch (final RuntimeException e) {
            return NONE;
        }
    }
}
