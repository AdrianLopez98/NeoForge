package forge.neo.tournament;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckgenUtil;
import forge.deck.io.DeckGroupSerializer;
import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoSettings;
import forge.neo.match.NeoFormat;
import forge.util.MyRandom;
import forge.util.storage.IStorage;
import forge.util.storage.StorageImmediatelySerialized;

/**
 * El torneo (la auditoría del motor, apartado C6): un cuadro de eliminación directa, 4 u 8
 * participantes, tú y el resto generados por el motor. Se juega ronda a
 * ronda hasta que sale un campeón — pierdes tu partida y quedas fuera, pero
 * el cuadro sigue solo hasta el final para que se pueda ver quién gana.
 *
 * <p><b>Esta clase es sólo el marcador — nunca arranca una partida.</b>
 * Jugar tu propia partida, verla en la mesa o resolverla al azar es cosa de
 * {@code NeoApp} (que sí llama a {@code NeoGame.play}); aquí sólo se anota
 * el resultado con {@link #recordMatch}. Antes había una simulación oculta
 * en segundo plano — real, con el motor, pero invisible — y era un fallo de
 * verdad: cada partida de las ajenas heredaba el tiempo de pensar que
 * hubiera dejado puesto tu ÚLTIMA partida jugada (hasta 20 s por decisión,
 * y encima con DOS IA pensando de verdad), así que el jugador se quedaba
 * mirando una pantalla en negro varios minutos sin saber qué esperar —
 * justo lo que el principio 6 de las notas de diseño pide evitar. Reportado
 * jugando: *"pero no hagas lo que te de la gana... quería poder simularlo
 * al momento o poder ver la partida, poder elegir, pero no la pantalla
 * negra"*. Ahora nunca hay espera sin decir qué se espera: al instante
 * (al azar) o viéndola, elegido por quien juega, cada vez.
 *
 * <p><b>A propósito NO se usa {@code TournamentData}/{@code TournamentIO},</b>
 * la pareja que Forge trae para esto. Dos motivos, y los dos confirmados
 * probando de verdad, no leyendo el código:
 *
 * <ol>
 *   <li>{@code TournamentData.startRound}/{@code nextRound} llaman a
 *       {@code GuiBase.getInterface().hostMatch()} + {@code getNewGuiGame()}
 *       — el ÚNICO método de {@code NeoGuiBase} que sigue sin implementar
 *       (ver las trampas conocidas) — y ese camino nunca aplica
 *       {@code setAppliedVariants}, con lo que Commander deja de perder por
 *       daño de comandante. Las partidas aquí las arranca {@code NeoGame.play}
 *       en su lugar, igual que hace el draft con {@code DraftRun}.</li>
 *   <li><b>{@code TournamentIO} no puede guardar un mazo de verdad.</b> Su
 *       XStream está bloqueado a sólo {@code String}/nulos/primitivos +
 *       las clases del propio paquete ({@code addPermission(NoTypePermission
 *       .NONE)}), y un {@code Deck} real usa por dentro un
 *       {@code java.util.EnumMap} que esa lista no permite:
 *       {@code ForbiddenClassException: java.util.EnumMap} al intentar
 *       RELEER el fichero que el mismo código acababa de escribir. Ninguna
 *       de las dos GUIs oficiales usa {@code TournamentData} — por eso nadie
 *       había tropezado con esto. Reproducible con {@code run.cmd
 *       tournamentcheck}.</li>
 * </ol>
 *
 * <p>En su lugar se reutiliza el almacén que YA usan draft y sellado:
 * {@link DeckGroupSerializer}, el mismo formato de texto {@code .dck} que el
 * resto de mazos — nunca XStream. El cuadro (qué ronda va, quién ha ganado
 * cada emparejamiento) vive en {@code neo.properties}, igual que
 * {@code DraftRun}.
 *
 * <p><b>Commander o Estándar</b>, elegido al montar el cuadro. Los rivales
 * salen de {@code DeckgenUtil.generateCommanderDeck} (el mismo que ya usa
 * {@code HomeScreen} para "Genérame uno", la auditoría del motor, apartado B6) o de
 * {@code DeckgenUtil.buildCardGenDeck} sobre el pozo de Estándar — las dos
 * son del propio motor, no hay generador nuestro. Cada rival se genera UNA
 * VEZ al crear el cuadro y juega con ese mismo mazo toda la ronda que le
 * toque: nadie cambia de mazo entre partidas.
 */
public final class NeoTournament {

    /**
     * Tamaños de cuadro permitidos. NO es una cifra libre: {@code
     * DeckGroupSerializer.MAX_DRAFT_PLAYERS} vale 8 y su lector sólo relee
     * {@code ai-1.dck} a {@code ai-7.dck} ({@code for (i=1; i<8; i++)}) — un
     * octavo rival se escribiría pero desaparecería en silencio al recargar
     * el torneo. Con 8 participantes (tú + 7 generados) se toca justo ese
     * techo. Es la misma razón, descubierta aquí, de que draft y sellado
     * hablen siempre de "siete rivales" y no de ocho.
     */
    public static final int[] SIZES = {4, 8};
    public static final int DEFAULT_SIZE = 4;
    private static final int MAX_SIZE = 8;

    private static final String CURRENT_KEY = "tournamentCurrent";

    /** Cómo va el cuadro para TI. El cuadro entero puede seguir jugándose tras un LOST. */
    public enum Status { ONGOING, WON, LOST }

    private final String name;
    private final NeoFormat format;
    private final List<Deck> participants; // [0] = tu mazo; el resto, generados
    private final List<List<Integer>> rounds = new ArrayList<>(); // rounds.get(r) = ganadores (indices) de la ronda r, ya resuelta
    private List<Integer> pendingWinners; // ronda en curso: -1 = pendiente (tu partida)
    private Status status = Status.ONGOING;

    private static IStorage<DeckGroup> storageInstance;

    private NeoTournament(final String name, final NeoFormat format, final List<Deck> participants) {
        this.name = name;
        this.format = format;
        this.participants = participants;

        final int roundCount = NeoSettings.getInt(key(name, "roundCount"), 0);
        for (int i = 0; i < roundCount; i++) {
            rounds.add(parse(NeoSettings.get(key(name, "round." + i), "")));
        }
        final String pending = NeoSettings.get(key(name, "pending"), null);
        pendingWinners = pending == null ? null : parse(pending);

        if (!rounds.isEmpty() && rounds.size() == totalRounds()) {
            final int champion = rounds.get(rounds.size() - 1).get(0);
            status = champion == 0 ? Status.WON : Status.LOST;
        }
    }

    /** El almacén de torneos: mismo mecanismo que draft y sellado, carpeta propia. */
    private static synchronized IStorage<DeckGroup> storage() {
        if (storageInstance == null) {
            final File dir = new File(ForgeConstants.DECK_BASE_DIR, "tournament");
            dir.mkdirs();
            storageInstance = new StorageImmediatelySerialized<>("Tournament decks",
                    new DeckGroupSerializer(dir, ForgeConstants.DECK_BASE_DIR));
        }
        return storageInstance;
    }

    /** El torneo en curso o recién terminado (para poder mirar el cuadro), o null si no hay. */
    public static NeoTournament current() {
        final String name = NeoSettings.get(CURRENT_KEY, null);
        if (name == null || name.isBlank()) {
            return null;
        }
        final DeckGroup g = group(name);
        if (g == null || g.getHumanDeck() == null || g.getAiDecks().isEmpty()) {
            return null;
        }
        final NeoFormat format = parseFormat(NeoSettings.get(key(name, "format"), null));
        final List<Deck> participants = new ArrayList<>();
        participants.add(g.getHumanDeck());
        participants.addAll(g.getAiDecks());
        return new NeoTournament(name, format, participants);
    }

    private static NeoFormat parseFormat(final String raw) {
        if (raw != null) {
            try {
                return NeoFormat.valueOf(raw);
            } catch (final IllegalArgumentException ignored) {
                // cuadro guardado antes de que existiera la eleccion de formato
            }
        }
        return NeoFormat.COMMANDER;
    }

    /**
     * Genera el cuadro (tú + {@code size - 1} rivales) y arranca el evento.
     * Todos los emparejamientos de la primera ronda empiezan pendientes —
     * ver {@link #pendingMatches()} — y se resuelven uno a uno con
     * {@link #recordMatch}/{@link #recordRandom}/{@link #record}.
     *
     * @param size   4 u 8 (potencia de dos, ver {@link #SIZES})
     * @param format {@link NeoFormat#COMMANDER} o {@link NeoFormat#ESTANDAR} —
     *               es lo único que decide qué generador de rivales se usa y
     *               con qué reglas se juega cada partida
     * @return el torneo, o null si el motor no ha podido generar los rivales
     */
    public static NeoTournament start(final Deck userDeck, final int size, final NeoFormat format) {
        final int n = Math.max(2, Math.min(MAX_SIZE, Integer.highestOneBit(Math.max(1, size))));

        final List<Deck> decks = new ArrayList<>();
        decks.add(userDeck);
        for (int i = 1; i < n; i++) {
            final Deck d = generateOpponent(format);
            if (d == null) {
                return null;
            }
            decks.add(d);
        }

        final String tname = nextName();
        final DeckGroup group = new DeckGroup(tname);
        group.setHumanDeck(userDeck);
        for (int i = 1; i < decks.size(); i++) {
            group.addAiDeck(decks.get(i));
        }
        storage().add(group);

        NeoSettings.set(CURRENT_KEY, tname);
        NeoSettings.set(key(tname, "format"), format.name());
        NeoSettings.save();
        return new NeoTournament(tname, format, decks);
    }

    private static Deck generateOpponent(final NeoFormat format) {
        try {
            if (format.isCommanderStyle()) {
                return DeckgenUtil.generateCommanderDeck(true, format.getGameType());
            }
            // Estandar: el mismo generador de mazos "de arquetipo" que usan
            // el Gauntlet y las quest de Forge para rivales de construido —
            // no hay equivalente de "Generame uno" propio para este formato
            // (la auditoría del motor, apartado B6 solo cubre los de comandante).
            return DeckgenUtil.buildCardGenDeck(forge.model.FModel.getFormats().getStandard(), true);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido generar un participante de torneo: " + e);
            return null;
        }
    }

    private static String nextName() {
        for (int i = 1; i < 500; i++) {
            final String candidate = "Torneo " + i;
            if (group(candidate) == null) {
                return candidate;
            }
        }
        return "Torneo";
    }

    private static DeckGroup group(final String name) {
        for (final DeckGroup g : storage()) {
            if (g.getName().equals(name)) {
                return g;
            }
        }
        return null;
    }

    // ---- lectura del cuadro --------------------------------------------

    public String getName() {
        return name;
    }

    public int getSize() {
        return participants.size();
    }

    public Deck deckOf(final int participant) {
        return participants.get(participant);
    }

    public boolean isYou(final int participant) {
        return participant == 0;
    }

    public Deck getDeck() {
        return participants.get(0);
    }

    public NeoFormat getFormat() {
        return format;
    }

    public int totalRounds() {
        int n = participants.size();
        int r = 0;
        while (n > 1) {
            n /= 2;
            r++;
        }
        return r;
    }

    /** La ronda que toca ahora (0 = primera), o {@code totalRounds()} si el cuadro ya terminó. */
    public int currentRoundIndex() {
        return rounds.size();
    }

    /** Las rondas YA resueltas, en orden — cada una es la lista de ganadores (por índice de participante). */
    public List<List<Integer>> getResolvedRounds() {
        return rounds;
    }

    /**
     * La ronda en curso: ganador por emparejamiento, o -1 si aún no se sabe
     * (tu propia partida, hasta que la juegues). Null si el cuadro ya
     * terminó o si nadie ha entrado todavía a esta ronda.
     */
    public List<Integer> getPendingWinners() {
        return pendingWinners;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOver() {
        return status != Status.ONGOING;
    }

    /** Quién entra en la ronda {@code r}, en orden de emparejamiento. */
    public List<Integer> entrants(final int r) {
        if (r == 0) {
            final List<Integer> seed = new ArrayList<>();
            for (int i = 0; i < participants.size(); i++) {
                seed.add(i);
            }
            return seed;
        }
        return rounds.get(r - 1);
    }

    /**
     * Tu rival en la ronda actual, o null si ya no estás en el cuadro, éste
     * ha terminado, o TU emparejamiento de esta ronda ya está decidido —
     * aunque la ronda entera siga sin cerrar porque falte una ajena. Sin
     * esto último, "Jugar"/"Que juegue la IA" seguían ofreciéndose después
     * de haber jugado, e invitaban a repetir tu propia partida en vez de
     * resolver la que de verdad falta. Reportado jugando: *"le he dado a
     * que se acabe la otra partida y sí me ha movido a la final, pero es
     * contraintuitivo"*.
     */
    public Deck yourOpponentThisRound() {
        if (isOver() || rounds.size() >= totalRounds()) {
            return null;
        }
        final List<Integer> ent = entrants(rounds.size());
        final int pos = ent.indexOf(0);
        if (pos < 0) {
            return null;
        }
        final int m = pos / 2;
        if (pendingWinners != null && m < pendingWinners.size() && pendingWinners.get(m) != -1) {
            return null;
        }
        return participants.get(ent.get(pos % 2 == 0 ? pos + 1 : pos - 1));
    }

    // ---- avanzar el cuadro ----------------------------------------------

    /**
     * Los emparejamientos de la ronda actual que TODAVÍA no tienen ganador
     * — el tuyo incluido, si te toca — en el orden del cuadro. Vacía si el
     * cuadro ya ha terminado.
     */
    public List<int[]> pendingMatches() {
        if (isOver()) {
            return new ArrayList<>();
        }
        final List<Integer> ent = entrants(rounds.size());
        final int matches = ent.size() / 2;
        final List<int[]> out = new ArrayList<>();
        for (int m = 0; m < matches; m++) {
            if (pendingWinners != null && m < pendingWinners.size() && pendingWinners.get(m) != -1) {
                continue;
            }
            out.add(new int[] {ent.get(2 * m), ent.get(2 * m + 1)});
        }
        return out;
    }

    /**
     * Anota el ganador de un emparejamiento cualquiera de la ronda actual
     * — el tuyo o uno ajeno. Si con esto la ronda queda entera resuelta, la
     * cierra y calcula si el cuadro ha terminado.
     */
    public void recordMatch(final int a, final int b, final int winner) {
        final List<Integer> ent = entrants(rounds.size());
        final int matches = ent.size() / 2;
        if (pendingWinners == null || pendingWinners.size() != matches) {
            pendingWinners = new ArrayList<>();
            for (int i = 0; i < matches; i++) {
                pendingWinners.add(-1);
            }
        }
        for (int m = 0; m < matches; m++) {
            if (ent.get(2 * m) == a && ent.get(2 * m + 1) == b) {
                pendingWinners.set(m, winner);
                break;
            }
        }
        persistPending();
        if (!pendingWinners.contains(-1)) {
            completeRound();
        }
    }

    /** Resuelve un emparejamiento AJENO al instante, a cara o cruz — sin jugar nada. */
    public void recordRandom(final int a, final int b) {
        recordMatch(a, b, MyRandom.getRandom().nextBoolean() ? a : b);
    }

    /** Anota el resultado de TU partida de esta ronda (jugada o vista en la mesa). */
    public void record(final boolean youWon) {
        final List<Integer> ent = entrants(rounds.size());
        final int pos = ent.indexOf(0);
        if (pos < 0) {
            return;
        }
        final int m = pos / 2;
        final int a = ent.get(2 * m);
        final int b = ent.get(2 * m + 1);
        recordMatch(a, b, youWon ? 0 : (a == 0 ? b : a));
    }

    private void completeRound() {
        rounds.add(new ArrayList<>(pendingWinners));
        NeoSettings.set(key(name, "round." + (rounds.size() - 1)), join(rounds.get(rounds.size() - 1)));
        NeoSettings.setInt(key(name, "roundCount"), rounds.size());
        pendingWinners = null;
        NeoSettings.set(key(name, "pending"), null);
        NeoSettings.save();
        if (rounds.size() == totalRounds()) {
            final int champion = rounds.get(rounds.size() - 1).get(0);
            status = champion == 0 ? Status.WON : Status.LOST;
        }
    }

    /** Abandonar a medias, o descartar un cuadro ya terminado para empezar otro. */
    public void abandon() {
        if (group(name) != null) {
            try {
                storage().delete(name);
            } catch (final RuntimeException e) {
                System.err.println("[neo] no se ha podido borrar el torneo: " + e);
            }
        }
        for (int i = 0; i < rounds.size(); i++) {
            NeoSettings.set(key(name, "round." + i), null);
        }
        NeoSettings.set(key(name, "roundCount"), null);
        NeoSettings.set(key(name, "pending"), null);
        NeoSettings.set(key(name, "format"), null);
        if (name.equals(NeoSettings.get(CURRENT_KEY, null))) {
            NeoSettings.set(CURRENT_KEY, null);
        }
        NeoSettings.save();
    }

    // ---- persistencia -----------------------------------------------------

    private static String key(final String name, final String field) {
        return "tournament." + name + "." + field;
    }

    private void persistPending() {
        NeoSettings.set(key(name, "pending"), pendingWinners == null ? null : join(pendingWinners));
        NeoSettings.save();
    }

    private static String join(final List<Integer> xs) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(xs.get(i));
        }
        return sb.toString();
    }

    private static List<Integer> parse(final String csv) {
        final List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (final String s : csv.split(",")) {
            out.add(Integer.parseInt(s.trim()));
        }
        return out;
    }
}
