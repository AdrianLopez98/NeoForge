package forge.neo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * Ajustes de la interfaz Neo.
 *
 * <p><b>Fichero propio, no el de Forge.</b> Compartimos {@code %APPDATA%\Forge\}
 * con la instalacion normal, que es lo que hace que los mazos de Commander del
 * usuario ya esten ahi. Escribir en {@code forge.preferences} le cambiaria los
 * ajustes de la GUI vieja por detras, asi que lo nuestro va en
 * {@code preferences/neo.properties}, un fichero que solo tocamos nosotros.
 *
 * <p>Aqui SOLO van preferencias de presentacion (que mazo, cuantos rivales, que
 * escala). Nada de reglas: eso es del motor.
 */
public final class NeoSettings {

    private NeoSettings() {
    }

    /** Ultimo mazo elegido, por nombre. */
    public static final String DECK = "lastDeck";
    /** Numero de rivales de IA. */
    public static final String OPPONENTS = "opponents";
    /** Perfil de IA (Cautious / Default / Reckless / Experimental). */
    public static final String AI_PROFILE = "aiProfile";
    /** Escala de interfaz; vacio o "auto" para automatica. */
    public static final String UI_SCALE = "uiScale";
    /** Zoom del texto del panel de detalle. */
    public static final String TEXT_ZOOM = "textZoom";
    /** Volumen de los efectos de sonido, 0-100. */
    public static final String SOUND_VOLUME = "soundVolume";
    /** Volumen de la musica, 0-100. */
    public static final String MUSIC_VOLUME = "musicVolume";
    /** Tamano del cuadro (4 u 8 participantes) del proximo torneo (la auditoría del motor C6). */
    public static final String TOURNAMENT_SIZE = "tournamentSize";

    /**
     * Con que volumen se abre el juego la primera vez.
     *
     * <p>Bajo a proposito, y salio de abrirlo de cero: entraba a todo trapo y
     * lo primero que hacia el jugador era ir corriendo a los ajustes. Subir es
     * un gesto que se hace cuando te apetece; bajar es uno que haces con prisa.
     *
     * <p>Estan aqui y no escritos en cada sitio porque el valor por defecto se
     * lee en tres — al aplicarlo al motor y en los dos deslizadores de ajustes
     * — y con el numero suelto basta cambiar dos para que el panel ensenye una
     * cosa y suene otra.
     */
    public static final int SOUND_VOLUME_DEFAULT = 10;
    /** @see #SOUND_VOLUME_DEFAULT */
    public static final int MUSIC_VOLUME_DEFAULT = 10;
    /** Animaciones de carta encendidas. */
    public static final String ANIMATIONS = "animations";
    /**
     * El brillo de las cartas foil (la auditoría del motor D5). Encendido de fabrica:
     * es puro adorno, no cambia el ritmo de la partida — al contrario que
     * {@link #AUTO_MANA} o {@link #SMART_PASS}, que si lo cambian y por eso
     * vienen apagados.
     */
    public static final String FOIL_EFFECT = "foilEffect";
    /** Ventana a pantalla completa. */
    public static final String FULLSCREEN = "fullscreen";
    /** Pagar el mana automaticamente al lanzar, en vez de clicar tierras. */
    public static final String AUTO_MANA = "autoMana";

    /**
     * Y viene APAGADO de fabrica.
     *
     * <p>Decidido jugando: el pago automatico elige por ti que tierras tapar, y
     * elige mal en cuanto hay filtros, duales o una habilidad que querias dejar
     * disponible. Es un ajuste que se enciende a proposito, no uno que haya que
     * descubrir para poder apagarlo.
     *
     * <p>Va aqui y no repetido en cada pantalla: lo leian SIETE sitios, cada
     * uno con su {@code true} copiado a mano, asi que cambiar el defecto era
     * cambiarlo en siete y olvidarse en uno. Es el principio 8 de las notas de diseño.
     */
    public static final boolean AUTO_MANA_DEFAULT = false;

    /** Si el mana se paga solo. Ver {@link #AUTO_MANA_DEFAULT}. */
    public static boolean autoPayMana() {
        return getBool(AUTO_MANA, AUTO_MANA_DEFAULT);
    }

    /**
     * Si el pago automatico prueba tambien las fuentes que el planificador del
     * motor <b>no sabe ver</b>: las que cobran mana para dar mas del que
     * cobran (tierras de filtro, amuletos de {@code {1},{T}: {G}{W}}).
     *
     * <p>Apagado de fabrica (la auditoría del motor 1.1) porque cada intento <b>gira una
     * tierra</b> y eso no se deshace desde aqui. Solo entra cuando el "Auto"
     * del motor ya se ha dado por vencido, o sea cuando hoy te quedas atascado
     * de todas formas. Ver {@code forge.neo.match.FilterSources}.
     */
    public static final String BLIND_MANA = "blindManaSources";

    /**
     * Bajar el arte de las cartas en el idioma del juego.
     *
     * <p>Se puede querer jugar en castellano y seguir viendo las cartas en
     * ingles: hay muchas que no se han impreso en otro idioma, y el arte
     * traducido es una segunda descarga entera. Ver {@code CardArt}.
     */
    public static final String CARD_ART_LANGUAGE = "cardArtLanguage";

    /**
     * Cuanto frenar el turno del rival.
     * 0 nunca · 1 si te afecta · 2 lo que va al stack · 3 en todo.
     */
    public static final String PAUSE_MODE = "pauseMode";

    /**
     * La regla de mulligan (la auditoría del motor B4): {@code MulliganDefs.MulliganRule}
     * — Original, Paris, Vancouver, London u Houston. El motor la trae entera
     * ({@code MulliganService} la lee de {@code StaticData.instance()} en cada
     * partida) pero el jugador nunca podia elegirla: se aplicaba siempre la de
     * fabrica del motor (London), sin ni un ajuste que lo dijera.
     *
     * <p>Va en NUESTRO fichero y no en las preferencias de Forge: la aplica
     * {@code NeoGame.applyEnginePrefs} llamando a
     * {@code StaticData.instance().setMulliganRule(...)}, que es exactamente
     * lo que hacen las dos GUIs oficiales al cambiarla en sus ajustes — nunca
     * se guarda en el fichero de Forge, solo en memoria.
     */
    public static final String MULLIGAN_RULE = "mulliganRule";

    /** Nombre por defecto, igual al de {@code MulliganDefs.getDefaultRule()}. */
    public static final String MULLIGAN_RULE_DEFAULT = "London";

    /**
     * Dificultad de la IA (la auditoría del motor B4): dejarle "hacer trampa" al barajar
     * ({@code GameRules.setAllowCheatShuffle}, lo mismo que
     * {@code UI_ENABLE_AI_CHEATS} de Forge). Solo afecta a
     * {@code AiProps.CHEAT_WITH_MANA_ON_SHUFFLE}, un ajuste concreto de la IA
     * al barajar su biblioteca — no es "mejorar la IA" (fuera de alcance de
     * este proyecto, ver las notas de diseño), es encender un interruptor que Forge ya
     * trae hecho. Apagado de fabrica, igual que en Forge.
     */
    public static final String AI_CHEAT_SHUFFLE = "aiCheatShuffle";

    /**
     * Segundos que la IA se puede tomar para pensar el combate
     * ({@code MATCH_AI_TIMEOUT} de Forge, 5 de fabrica). No es dificultad: es
     * un tope de tiempo para no dejar que un combate enorme cuelgue el turno
     * del rival — {@code AiAttackController} lo usa para cortar la busqueda.
     * {@code HostedMatch.startGame()} ya lo lee solo de
     * {@code FModel.getPreferences()} en cada partida; aqui solo hace falta
     * escribirlo, en {@code NeoGame.applyEnginePrefs}.
     */
    public static final String AI_TIMEOUT = "aiTimeout";

    /** El de fabrica de Forge (5 s). */
    public static final int AI_TIMEOUT_DEFAULT = 5;

    /**
     * Jugar por apuesta (la auditoría del motor B4): solo afecta a los ~30 scripts
     * viejos con una habilidad de ante de verdad (Arabian Nights, Antiquities,
     * Legends, The Dark) — para el resto de las 33.696 cartas esto no cambia
     * nada. Apagado de fabrica, como en Forge: es un mecanismo que PIERDE
     * cartas del mazo de verdad, y algo asi no se puede encender sin que el
     * jugador lo pida.
     */
    public static final String ANTE = "ante";
    /** Si el ante se empareja por rareza (carta rara contra carta rara). */
    public static final String ANTE_MATCH_RARITY = "anteMatchRarity";
    /** Si las tierras basicas pueden entrar en el ante. */
    public static final String ANTE_INCLUDE_BASIC_LANDS = "anteIncludeBasicLands";

    /**
     * Politica global de arte (la auditoría del motor B4: {@code UI_PREFERRED_ART}).
     * Solo decide que impresion se usa cuando NADIE ha elegido una a mano —
     * importar una lista, un mazo que genera el motor, la IA — porque
     * {@code PrintingDialog} sigue mandando carta a carta. Al reves que el
     * idioma, esto SI se puede cambiar en caliente:
     * {@code StaticData.setCardArtPreference(boolean, boolean)} tiene setter
     * de verdad y las dos GUIs oficiales lo llaman al momento, sin reiniciar.
     */
    public static final String CARD_ART_LATEST = "cardArtLatest";
    /** De fabrica: la mas reciente (igual que {@code LATEST_ART_ALL_EDITIONS}). */
    public static final boolean CARD_ART_LATEST_DEFAULT = true;
    /** Si ademas se descartan las ediciones fuera de nucleo/expansion (promos, un-sets...). */
    public static final String CARD_ART_CORE_ONLY = "cardArtCoreOnly";

    /**
     * Arte variado en pools generados al azar (aventura, sellado del motor):
     * {@code UI_RANDOM_ART_IN_POOLS}. Encendido de fabrica en Forge — apagarlo
     * hace que todas las copias de una tierra basica usen el MISMO arte en vez
     * de repartirse entre las que tiene la edicion.
     */
    public static final String RANDOM_ART_IN_POOLS = "randomArtInPools";

    /** No ensenyar el aviso de "la IA no juega bien estas cartas". */
    public static final String HIDE_AI_WARNING = "hideAiWarning";

    /**
     * Preguntar antes de salir de tu fase principal.
     *
     * <p>{@code 0} nunca · {@code 1} solo al ir al combate · {@code 2} en las
     * dos fases principales. Ver {@code NeoMatchUI.confirmLeavingMain}.
     */
    public static final String CONFIRM_PHASE = "confirmPhase";

    /**
     * Y viene puesto en "las dos" de fabrica.
     *
     * <p>Sale de jugar: una fase principal con disparos se contesta a base de
     * OK, uno por disparo, y en cuanto se acaban el siguiente OK — el que ya
     * ibas a dar — te planta en el combate. Eso no se deshace, asi que por
     * defecto se pregunta (principio 6). Quien vaya atento a cada OK lo apaga
     * en Ajustes y vuelve a como estaba.
     */
    public static final int CONFIRM_PHASE_DEFAULT = 2;

    /** Cuando preguntar antes de salir de la fase principal. */
    public static int confirmPhaseMode() {
        return Math.max(0, Math.min(2, getInt(CONFIRM_PHASE, CONFIRM_PHASE_DEFAULT)));
    }

    /**
     * A que ritmo juega la IA, en centesimas de multiplicador.
     *
     * <p>{@code 100} es x1, o sea la pausa base entre carta y carta
     * ({@code NeoMatchUI.AI_STEP_MS}). {@code 200} es x2 (la mitad de espera),
     * {@code 50} es x0,5 (el doble) y {@code 0} es sin pausa ninguna, que es
     * como se jugaba antes de que esto existiera.
     *
     * <p>Va en centesimas y no en milisegundos a proposito: lo que el jugador
     * elige es "x2", no "1500 ms", y guardar lo que se elige evita que cambiar
     * la pausa base deje los ajustes viejos apuntando a otra cosa.
     */
    public static final String AI_SPEED = "aiSpeed";

    /**
     * Que el pase automatico se PARE cuando pasa algo que te importa.
     *
     * <p>Esto no lo programamos nosotros: Forge trae el sistema entero en
     * {@code forge.gamemodes.match.YieldController} (623 lineas) y lo unico
     * que le faltaba era que alguien lo encendiera. Nosotros ya poniamos
     * {@code YIELD_AUTO_PASS_NO_ACTIONS} — o sea "pasa la prioridad sola
     * cuando no tengo nada que hacer" — pero dejabamos apagado el interruptor
     * maestro de las interrupciones, asi que el pase no se paraba NUNCA:
     * el rival te atacaba o te lanzaba algo y la partida seguia de largo.
     *
     * <p>Viene <b>apagado</b>. Es un cambio de ritmo de la partida y esos se
     * eligen, no se imponen: quien ya juega comodo no tiene por que notar
     * nada al actualizar.
     */
    public static final String SMART_PASS = "smartPass";

    /**
     * Cuanto se para, de lo poco a lo todo.
     *
     * <p>{@code 0} ataques y hechizos del rival · {@code 1} y ademas lo que te
     * apunta y los barridos · {@code 2} y ademas los disparos y lo que se
     * revela.
     *
     * <p>Son <b>tres posiciones y no seis casillas</b> a proposito. El motor
     * tiene una preferencia por cada tipo de interrupcion
     * ({@code YIELD_INTERRUPT_ON_*}), pero seis casillas sueltas obligan al
     * jugador a saber que es un "barrido" o un "disparo" antes de poder
     * decidir, y lo que en realidad quiere elegir es cuanto le paran. Las tres
     * posiciones se traducen a las seis preferencias en
     * {@code NeoGame.applyEnginePrefs}.
     */
    public static final String SMART_PASS_LEVEL = "smartPassLevel";

    /** Cuanto para el pase automatico. 0 poco · 1 normal · 2 todo. */
    public static int smartPassLevel() {
        return Math.max(0, Math.min(2, getInt(SMART_PASS_LEVEL, 0)));
    }

    /**
     * Numero de rating sobre cada carta del sobre, en el draft.
     *
     * <p>Viene <b>apagado</b> a proposito, al contrario que la preferencia de
     * Forge de la que sale el dato ({@code UI_OVERLAY_DRAFT_RANKING}, "true" de
     * fabrica): para quien esta aprendiendo a draftear es una ayuda, pero para
     * quien ya sabe es la respuesta puesta antes de pensarla, y a ese le
     * estropea el draft. Se enciende a proposito, como {@link #AUTO_MANA}.
     */
    public static final String DRAFT_RANKING = "draftRanking";

    /** Si se ensenya el numero de rating sobre las cartas del sobre. */
    public static boolean showDraftRanking() {
        return getBool(DRAFT_RANKING, false);
    }

    /**
     * Jugar cada partido del evento (draft o sellado) al mejor de 3, con
     * banquillo entre partida y partida.
     *
     * <p>Apagado de fabrica: una sola partida por rival es lo que ya se
     * jugaba, y sigue siendo lo mas rapido para quien solo quiere avanzar el
     * evento. Se lee una vez, al pulsar "jugar" en el marcador del evento
     * ({@code DraftRunScreen}) — cambiarlo a mitad de un evento solo afecta
     * al PROXIMO partido, nunca al que ya esta en curso.
     */
    public static final String DRAFT_BO3 = "draftBo3";

    /** Si el proximo partido del evento se juega al mejor de 3. */
    public static boolean bo3() {
        return getBool(DRAFT_BO3, false);
    }

    private static final Properties PROPS = new Properties();
    private static boolean loaded;

    private static File file() {
        return new File(ForgeConstants.USER_PREFS_DIR, "neo.properties");
    }

    /**
     * true si ya hay un fichero de ajustes nuestro.
     *
     * <p>Es la forma honesta de saber si alguien ha abierto esto antes. Lo usa
     * el tutorial para decidir si se pone delante del menu: a quien lleva
     * jugando meses no se le puede recibir con un tutorial solo porque hayamos
     * anyadido la marca hoy.
     */
    public static boolean exists() {
        return file().exists();
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        final File f = file();
        if (!f.exists()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            PROPS.load(in);
        } catch (final IOException e) {
            // Unos ajustes ilegibles no son motivo para no arrancar.
            System.err.println("[neo] no se han podido leer los ajustes: " + e.getMessage());
        }
    }

    public static synchronized void save() {
        final File f = file();
        try {
            final File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                PROPS.store(out, "NeoForge - ajustes de la interfaz Neo");
            }
        } catch (final IOException e) {
            System.err.println("[neo] no se han podido guardar los ajustes: " + e.getMessage());
        }
    }

    public static synchronized String get(final String key, final String fallback) {
        load();
        final String v = PROPS.getProperty(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    public static synchronized void set(final String key, final String value) {
        load();
        if (value == null) {
            PROPS.remove(key);
        } else {
            PROPS.setProperty(key, value);
        }
    }

    public static int getInt(final String key, final int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    public static void setInt(final String key, final int value) {
        set(key, String.valueOf(value));
    }

    /** Escala guardada, o {@code null} si es automatica. */
    public static Double getScale() {
        final String v = get(UI_SCALE, "auto");
        if ("auto".equalsIgnoreCase(v)) {
            return null;
        }
        try {
            return Double.valueOf(v);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public static void setScale(final Double scale) {
        set(UI_SCALE, scale == null ? "auto" : String.format(java.util.Locale.ROOT, "%.2f", scale));
    }

    public static double getDouble(final String key, final double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)));
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    public static void setDouble(final String key, final double value) {
        set(key, String.format(java.util.Locale.ROOT, "%.2f", value));
    }

    public static boolean getBool(final String key, final boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    public static void setBool(final String key, final boolean value) {
        set(key, String.valueOf(value));
    }

    /**
     * Pasa el volumen a las preferencias del motor.
     *
     * <p>El {@code SoundSystem} de Forge lee el volumen de SUS preferencias
     * ({@code UI_VOL_SOUNDS}, {@code UI_VOL_MUSIC}), asi que no hay mas remedio
     * que escribirlas. Se hace <b>solo en memoria, sin guardar</b>: ese fichero
     * lo comparte la instalacion normal de Forge y no vamos a cambiarle los
     * ajustes al usuario por detras. Nuestro valor de verdad vive en
     * {@code neo.properties} y se vuelve a aplicar en cada arranque.
     */
    public static void applyAudioToEngine() {
        final int sound = getInt(SOUND_VOLUME, SOUND_VOLUME_DEFAULT);
        final int music = getInt(MUSIC_VOLUME, MUSIC_VOLUME_DEFAULT);
        final ForgePreferences prefs = FModel.getPreferences();
        prefs.setPref(FPref.UI_ENABLE_SOUNDS, sound > 0);
        prefs.setPref(FPref.UI_ENABLE_MUSIC, music > 0);
        prefs.setPref(FPref.UI_VOL_SOUNDS, String.valueOf(sound));
        prefs.setPref(FPref.UI_VOL_MUSIC, String.valueOf(music));
        // Nada de prefs.save(): el cambio muere con el proceso.
        try {
            forge.sound.SoundSystem.instance.refreshVolume();
        } catch (final RuntimeException e) {
            // Todavia no hay musica sonando: no es un problema.
        }
    }

    /**
     * Pasa la politica de arte a las preferencias del motor (la auditoría del motor B4).
     *
     * <p>Se llama una vez al arrancar ({@code NeoApp.applyEngineSettings}, en
     * cuanto el catalogo esta leido) y otra vez cada vez que se toca en
     * Ajustes — al reves que el idioma, esto no necesita reiniciar:
     * {@code StaticData} tiene setters de verdad y las dos GUIs oficiales los
     * llaman al momento.
     */
    public static void applyCardArtToEngine() {
        final boolean latest = getBool(CARD_ART_LATEST, CARD_ART_LATEST_DEFAULT);
        final boolean coreOnly = getBool(CARD_ART_CORE_ONLY, false);
        FModel.getMagicDb().setCardArtPreference(latest, coreOnly);
        // UI_RANDOM_ART_IN_POOLS si es una preferencia de Forge de verdad
        // (Quest y el generador de pools sellados la leen sola de
        // FModel.getPreferences() cada vez), asi que basta con escribirla.
        FModel.getPreferences().setPref(FPref.UI_RANDOM_ART_IN_POOLS,
                getBool(RANDOM_ART_IN_POOLS, true));
        // Nada de prefs.save(): el fichero de Forge no se toca por detras.
    }
}
