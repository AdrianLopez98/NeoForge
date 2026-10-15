package forge.neo.look;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import forge.ai.AiProfileUtil;
import forge.ai.AiProps;
import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoSettings;

/**
 * Los perfiles de IA que trae el jugador (los {@code .ai} de Forge).
 *
 * <p>Pedido en Discord el 14-10-2026: <i>"Los archivos .ai son del propio Forge,
 * pero puedes crear las tuyas propias ... he probado a importar la mia en
 * NeoForge y no aparece en la lista"</i>. Un perfil es un fichero de texto con
 * una linea {@code AJUSTE=valor} por ajuste ({@link AiProps}): no cambia como
 * piensa la IA, cambia sus umbrales (cuando mulliga, cuanto arriesga atacando,
 * si guarda la tierra...).
 *
 * <p><b>Por que no basta con dejarlo en {@code res/ai}.</b> El motor si lo
 * cargaria — {@code FModel.initialize} lee esa carpeta entera —, pero
 * {@code res/} es del programa: se pisa al descomprimir la version siguiente, y
 * en el {@code .app} de Mac ni se puede escribir. Asi que los del jugador viven
 * en sus datos ({@link #userDir()}, junto a sus avatares y tapetes), y al
 * arrancar se juntan con los de fabrica en una carpeta de la cache que se le
 * pasa al motor con {@link AiProfileUtil#loadAllProfiles(String)} — el mismo
 * metodo publico que usa {@code FModel}, sin tocar una linea de Forge.
 *
 * <p><b>Y se limpian antes de pasarselos.</b> Forge hace
 * {@code AiProps.valueOf(clave)} con cada linea: un ajuste mal escrito, o uno
 * que ya no exista en esta version de Forge, revienta la carga entera, y si
 * esta en {@code res/ai} el juego no arranca. Y un {@code 3 } con espacio
 * detras pasa la carga pero revienta despues, en {@code Integer.parseInt},
 * en mitad de una partida. Aqui cada linea se mira contra {@link AiProps}: la
 * clave tiene que existir y el valor tiene que ser del tipo del de fabrica
 * (numero o true/false). Lo que no vale se salta y se cuenta; el resto del
 * perfil sigue valiendo.
 *
 * <p>Sin perfiles del jugador no se hace absolutamente nada: el motor sigue
 * leyendo {@code res/ai} como siempre.
 *
 * <p>Java puro y sin JavaFX, como {@link RivalSetup}: Android lo recibe por el
 * jar. Nada de API que Android no tenga en la 26.
 */
public final class AiProfiles {

    private AiProfiles() {
    }

    private static final String EXT = ".ai";

    /** Los perfiles del jugador que se han cargado, en orden alfabetico. */
    private static volatile List<String> custom = Collections.emptyList();

    // ---------------------------------------------------------------
    // Las carpetas
    // ---------------------------------------------------------------

    /** Donde viven los perfiles del jugador: {@code <datos>/neo/ai}. */
    public static File userDir() {
        return new File(ForgeConstants.USER_DIR, "neo" + File.separator + "ai");
    }

    /** Los de fabrica y los suyos juntos, lo que se le pasa al motor. Se rehace en cada carga. */
    static File mergedDir() {
        return new File(ForgeConstants.CACHE_DIR, "neo-ai");
    }

    // ---------------------------------------------------------------
    // Lo que se ofrece
    // ---------------------------------------------------------------

    /** Los perfiles del jugador cargados ahora mismo. */
    public static List<String> custom() {
        return custom;
    }

    /** Si este nombre es un perfil del jugador cargado. */
    public static boolean isCustom(final String name) {
        return name != null && custom.contains(name);
    }

    /** Los de Forge ({@link RivalSetup#PROFILES}, en su orden) y luego los del jugador. */
    public static List<String> all() {
        final List<String> out = new ArrayList<>();
        Collections.addAll(out, RivalSetup.PROFILES);
        out.addAll(custom);
        return out;
    }

    // ---------------------------------------------------------------
    // Cargar
    // ---------------------------------------------------------------

    /**
     * Lee los perfiles del jugador y se los da al motor junto a los de fabrica.
     * Se llama justo despues de {@code FModel.initialize}, y otra vez al
     * importar o quitar uno. Nunca lanza: si algo falla, el motor se queda con
     * los de fabrica.
     */
    public static synchronized void install() {
        final List<String> loaded = new ArrayList<>();
        try {
            final File[] files = userDir().listFiles();
            final File merged = mergedDir();
            if (files != null) {
                java.util.Arrays.sort(files);
                for (final File f : files) {
                    final String name = nameOf(f);
                    if (name == null || containsIgnoreCase(loaded, name)) {
                        continue;
                    }
                    if (isReserved(name)) {
                        System.out.println("[neo] perfil de IA con el nombre de uno de Forge, no se carga"
                                + " (importalo desde Personalizar y se numera): " + f);
                        continue;
                    }
                    // Uno a uno: un fichero que no se puede leer (bloqueado,
                    // lo que sea) se salta y los demas siguen.
                    final Parsed p;
                    try {
                        p = parse(readLines(f));
                    } catch (final IOException | RuntimeException e) {
                        System.out.println("[neo] perfil de IA que no se puede leer, no se carga: " + f + " (" + e + ")");
                        continue;
                    }
                    if (p.lines.isEmpty()) {
                        System.out.println("[neo] perfil de IA sin ningun ajuste valido, no se carga: " + f);
                        continue;
                    }
                    if (loaded.isEmpty()) {
                        prepareMerged(merged);
                    }
                    Files.write(new File(merged, name + EXT).toPath(), p.lines, StandardCharsets.UTF_8);
                    loaded.add(name);
                    System.out.println("[neo] perfil de IA propio: " + name + " (" + p.lines.size()
                            + " ajustes" + (p.skipped.isEmpty() ? "" : ", " + p.skipped.size()
                            + " lineas ignoradas: " + p.skipped.get(0)) + ")");
                }
            }
            if (loaded.isEmpty()) {
                // Sin perfiles propios (o se acaba de quitar el ultimo): el
                // motor, con los suyos de siempre.
                if (!custom.isEmpty()) {
                    AiProfileUtil.loadAllProfiles(ForgeConstants.AI_PROFILE_DIR);
                }
            } else {
                AiProfileUtil.loadAllProfiles(merged.getPath());
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[neo] no se han podido cargar los perfiles de IA propios: " + e);
            loaded.clear();
            AiProfileUtil.loadAllProfiles(ForgeConstants.AI_PROFILE_DIR);
        }
        Collections.sort(loaded, String.CASE_INSENSITIVE_ORDER);
        custom = Collections.unmodifiableList(loaded);
    }

    /** La carpeta de la cache, vacia y con los de fabrica copiados tal cual. */
    private static void prepareMerged(final File merged) throws IOException {
        merged.mkdirs();
        final File[] old = merged.listFiles();
        if (old != null) {
            for (final File f : old) {
                if (f.getName().endsWith(EXT) && !f.delete()) {
                    throw new IOException("no se puede borrar " + f);
                }
            }
        }
        final File[] factory = new File(ForgeConstants.AI_PROFILE_DIR).listFiles();
        if (factory != null) {
            for (final File f : factory) {
                if (f.getName().endsWith(EXT)) {
                    // Por si otra instancia la esta rehaciendo a la vez.
                    Files.copy(f.toPath(), new File(merged, f.getName()).toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Al QUITAR un perfil que era el general, se vuelve a Default: si no, el
     * menu no marcaria ninguno y las partidas irian con un nombre que el motor
     * no conoce, o sea con todos los ajustes por defecto sin decirlo. Los de
     * cada rival y el pozo ya se filtran solos ({@link RivalSetup#mode},
     * {@link RivalSetup#pool}).
     *
     * <p>Solo al quitarlo, nunca al cargar: si un dia un fichero no se puede
     * leer (bloqueado, lo que sea), guardar Default lo haria para siempre
     * aunque al siguiente arranque el fichero vuelva a estar bien. Para eso,
     * el menu lo corrige al pintarlo sin guardarlo ({@link #orDefault}).
     */
    private static void forgetGeneral(final String removed) {
        final String general = NeoSettings.get(NeoSettings.AI_PROFILE, "");
        if (removed.equalsIgnoreCase(general)) {
            NeoSettings.set(NeoSettings.AI_PROFILE, "Default");
            NeoSettings.save();
        }
    }

    /**
     * El perfil general para ensenyarlo: el guardado si existe ("" y
     * {@link RivalSetup#RANDOM} incluidos), y si no, Default. No guarda nada.
     */
    public static String orDefault(final String saved) {
        if (saved == null || saved.isEmpty() || RivalSetup.RANDOM.equals(saved)
                || RivalSetup.isProfile(saved)) {
            return saved;
        }
        return "Default";
    }

    // ---------------------------------------------------------------
    // Importar y quitar
    // ---------------------------------------------------------------

    /** Lo que ha pasado al importar, para decirselo al jugador. */
    public static final class Imported {
        /** El nombre con el que ha quedado, o null si no se ha podido. */
        public final String name;
        public final int settings;
        /** Las lineas que se han saltado, con su motivo. */
        public final List<String> skipped;

        Imported(final String name, final int settings, final List<String> skipped) {
            this.name = name;
            this.settings = settings;
            this.skipped = skipped;
        }
    }

    /**
     * Copia un perfil a {@link #userDir()} (ya limpio) y lo carga. El nombre es
     * el del fichero; si es el de uno de Forge, se le anyade un numero — se lo
     * va a encontrar en la lista, y "Default" dos veces no se distinguiria. Si
     * es el de uno suyo, lo sustituye: es como se retoca un perfil.
     *
     * @return lo importado; {@code name} null si no tenia ni un ajuste valido
     */
    public static synchronized Imported importFile(final File source) throws IOException {
        final Parsed p = parse(readLines(source));
        if (p.lines.isEmpty()) {
            return new Imported(null, 0, p.skipped);
        }
        String base = cleanName(source.getName());
        if (base.isEmpty()) {
            base = "IA";
        }
        String name = base;
        for (int n = 2; isReserved(name); n++) {
            name = base + " " + n;
        }
        final File dir = userDir();
        dir.mkdirs();
        // Uno suyo con el mismo nombre se sustituye: fuera el viejo, tenga la
        // extension que tenga.
        removeFiles(name);
        Files.write(new File(dir, name + EXT).toPath(), p.lines, StandardCharsets.UTF_8);
        install();
        return new Imported(name, p.lines.size(), p.skipped);
    }

    /** Quita un perfil del jugador. Los de Forge no se pueden quitar. */
    public static synchronized boolean delete(final String name) {
        if (!isCustom(name)) {
            return false;
        }
        final boolean gone = removeFiles(name);
        install();
        forgetGeneral(name);
        return gone;
    }

    private static boolean removeFiles(final String name) {
        boolean any = false;
        final File[] files = userDir().listFiles();
        if (files != null) {
            for (final File f : files) {
                final String n = nameOf(f);
                if (n != null && n.equalsIgnoreCase(name)) {
                    any |= f.delete();
                }
            }
        }
        return any;
    }

    // ---------------------------------------------------------------
    // Leer un perfil
    // ---------------------------------------------------------------

    /** Un perfil ya limpio: las lineas que valen y las que se han saltado. */
    static final class Parsed {
        final List<String> lines = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
    }

    /**
     * Se queda con las lineas {@code CLAVE=valor} que el motor puede usar, ya
     * sin espacios alrededor. Los comentarios ({@code #}) y las lineas vacias
     * se callan, como en Forge.
     */
    static Parsed parse(final List<String> raw) {
        final Parsed out = new Parsed();
        final java.util.Set<AiProps> seen = java.util.EnumSet.noneOf(AiProps.class);
        for (int i = 0; i < raw.size(); i++) {
            String line = raw.get(i);
            if (i == 0 && line.startsWith("﻿")) {
                line = line.substring(1);
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            final int eq = line.indexOf('=');
            if (eq <= 0 || line.indexOf('=', eq + 1) >= 0) {
                out.skipped.add(line);
                continue;
            }
            final String key = line.substring(0, eq).trim();
            final String value = line.substring(eq + 1).trim();
            final AiProps prop = propOf(key);
            if (prop == null || !fits(prop, value)) {
                out.skipped.add(line);
                continue;
            }
            if (seen.add(prop)) {
                out.lines.add(prop.name() + "=" + value);
            } else {
                // Repetido: Forge se queda con el ultimo, y aqui igual.
                for (int j = 0; j < out.lines.size(); j++) {
                    if (out.lines.get(j).startsWith(prop.name() + "=")) {
                        out.lines.set(j, prop.name() + "=" + value);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static AiProps propOf(final String key) {
        try {
            return AiProps.valueOf(key);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Si el valor es del tipo que el motor va a pedir. El tipo se saca del
     * valor de fabrica: si ese es un numero, el motor hara {@code parseInt}; si
     * es true/false, {@code parseBoolean}. Vacio vale siempre (el motor usa el
     * de fabrica).
     */
    static boolean fits(final AiProps prop, final String value) {
        if (value.isEmpty()) {
            return true;
        }
        final String def = prop.getDefault();
        if (isInt(def)) {
            return isInt(value);
        }
        if ("true".equalsIgnoreCase(def) || "false".equalsIgnoreCase(def)) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        }
        return true;
    }

    private static boolean isInt(final String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * Sin reventar con bytes que no son UTF-8: Forge lee estos ficheros con un
     * lector que los sustituye ({@code FileUtil.readFile}), y una tilde en un
     * comentario guardado en ANSI no puede impedir importar un perfil que
     * Forge cargaria. {@code readAllLines} si lanza.
     */
    private static List<String> readLines(final File f) throws IOException {
        final String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        return java.util.Arrays.asList(text.split("\r\n|\r|\n", -1));
    }

    // ---------------------------------------------------------------
    // Nombres
    // ---------------------------------------------------------------

    /** El nombre de un fichero de perfil ({@code .ai} o {@code .txt}), o null si no es uno. */
    private static String nameOf(final File f) {
        if (!f.isFile()) {
            return null;
        }
        final String lower = f.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(EXT) && !lower.endsWith(".txt")) {
            return null;
        }
        final String name = cleanName(f.getName());
        return name.isEmpty() ? null : name;
    }

    /**
     * Sin extension y sin lo que rompe donde se guarda: la barra y la coma son
     * los separadores de {@code neo.properties} ({@link RivalSetup}), y la ruta
     * no puede salirse de la carpeta.
     */
    static String cleanName(final String fileName) {
        String n = fileName;
        final int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        n = n.replaceAll("[|,/\\\\:*?\"<>]", " ").replaceAll("\\s+", " ").trim();
        return n.length() > 40 ? n.substring(0, 40).trim() : n;
    }

    /**
     * Los nombres que no puede llevar uno del jugador: los de Forge, nuestro
     * "Random" y los dos "Random (...)" que el motor trata aparte en
     * {@code createAiPlayer}. Comparaciones EXACTAS, a proposito: con un
     * prefijo ("empieza por Random (") el nombre numerado seguiria empezando
     * igual, y la numeracion de {@link #importFile} no acabaria nunca — en el
     * hilo de JavaFX, o sea la ventana colgada (revisor, 14-10-2026). La
     * marca {@code neo:} no hace falta: {@link #cleanName} quita los dos puntos.
     */
    static boolean isReserved(final String name) {
        if (RivalSetup.RANDOM.equalsIgnoreCase(name)
                || AiProfileUtil.AI_PROFILE_RANDOM_MATCH.equalsIgnoreCase(name)
                || AiProfileUtil.AI_PROFILE_RANDOM_DUEL.equalsIgnoreCase(name)) {
            return true;
        }
        for (final String p : RivalSetup.PROFILES) {
            if (p.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(final List<String> list, final String s) {
        for (final String x : list) {
            if (x.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
