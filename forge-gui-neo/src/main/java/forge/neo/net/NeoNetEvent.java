package forge.neo.net;

import java.util.ArrayList;
import java.util.List;

import forge.card.DraftOptions;
import forge.deck.Deck;
import forge.deck.DeckProxy;
import forge.gamemodes.limited.BoosterDraft;
import forge.gamemodes.limited.LimitedPoolType;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.net.EventFormat;
import forge.gamemodes.net.NetworkEvent;
import forge.gamemodes.net.NetworkEventView;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.model.FModel;

/**
 * El draft y el sellado en red, envueltos.
 *
 * <p>Es a {@code NetworkEvent} lo que {@link NeoLobby} es a {@code GameLobby}:
 * la fachada minima, sin una sola linea de UI, para que la regla se pueda
 * probar sin abrir una ventana.
 *
 * <p><b>Un "evento" no es un modo de la sala.</b> Esa es la distincion que hay
 * que tener clara antes de tocar nada aqui, y es la razon de que el draft no
 * saliera en la fila de modos: Commander o Estandar son una <i>variante</i> que
 * se aplica al lobby, mientras que un draft es una <b>fase previa</b> entera —
 * se reparten sobres, se pasan picks por el cable, sale un pool, cada uno monta
 * su mazo con el, y <i>luego</i> se juega. Forge lo modela aparte
 * ({@code EventFormat}, {@code NetworkEvent}, {@code BoosterDraftHost}) y
 * nosotros igual.
 *
 * <p><b>Lo que trae Forge y no hay que escribir:</b> el reparto asincrono de
 * sobres (cada asiento con su cola, el rapido acumula mientras espera al
 * lento), los temporizadores por pick, el plazo de gracia cuando alguien se
 * cae — y el pick automatico si no vuelve —, el reparto de pools de sellado y
 * el guardado del pool con sus etiquetas. Son 543 lineas en
 * {@code BoosterDraftHost} mas lo de {@code ServerGameLobby}, mantenidas rio
 * arriba. Lo unico nuestro es la pantalla.
 *
 * <p><b>Y el asistente de montaje sale gratis.</b>
 * {@code BoosterDraft.createDraftForNetwork} pregunta el bloque, la expansion o
 * el cubo con {@code SGuiChoose}, que entra por
 * {@code NeoGuiBase.getChoices} — o sea que esos dialogos ya salen <b>en
 * nuestra interfaz</b> sin tocar nada, igual que ya pasaba con el draft de cubo
 * (la auditoría del motor, apartado C2). Por eso montar un evento son cuatro llamadas seguidas y no
 * una pantalla nueva.
 */
public final class NeoNetEvent {

    private NeoNetEvent() {
    }

    /** Lo que Forge pone de fabrica si el anfitrion no toca el reloj. */
    public static final int DEFAULT_PICK_SECONDS = 60;

    /** Lo que se espera a alguien que se cae a mitad de draft. */
    public static final int DEFAULT_GRACE_SECONDS = 120;

    // ------------------------------------------------------------------
    // Montar el evento (solo el anfitrion)
    // ------------------------------------------------------------------

    /**
     * Los tamanyos de pod que se pueden elegir.
     *
     * <p>El suelo son los asientos que YA hay en la sala: un pod mas pequenyo
     * que la gente sentada dejaria a alguien fuera del draft pero dentro de la
     * partida, o sea sin mazo. El techo lo pone Forge
     * ({@code BoosterDraft.N_PLAYERS}). Los huecos que sobren los rellena el
     * servidor con IA, que draftean pero no juegan.
     */
    public static List<Integer> podSizes(final GameLobby lobby) {
        final int floor = Math.max(2, lobby == null ? 2 : lobby.getNumberOfSlots());
        final List<Integer> out = new ArrayList<>();
        for (int n = floor; n <= BoosterDraft.N_PLAYERS; n++) {
            out.add(n);
        }
        return out;
    }

    /** Si con la gente que hay sentada cabe un draft. */
    public static boolean podFits(final GameLobby lobby) {
        return !podSizes(lobby).isEmpty();
    }

    /** Las tres reglas de picks dobles que admite Forge, en su orden. */
    public static List<DraftOptions.DoublePick> pickRules() {
        return List.of(DraftOptions.DoublePick.NEVER,
                DraftOptions.DoublePick.FIRST_PICK,
                DraftOptions.DoublePick.ALWAYS);
    }

    /**
     * Crea el evento y lo deja configurado. <b>Hilo de fondo</b>: por dentro
     * salen los dialogos de "que bloque" y "que expansion".
     *
     * @param draft el draft ya montado ({@code createDraftForNetwork}), o null
     *              en un sellado
     * @return si ha quedado configurado; false tambien cuando el jugador
     *         cancela un dialogo, que no es un fallo
     */
    public static boolean configure(final ServerGameLobby lobby, final EventFormat format,
                                    final LimitedPoolType poolType, final BoosterDraft draft,
                                    final int pickSeconds, final int graceSeconds) {
        if (lobby == null || poolType == null) {
            return false;
        }
        lobby.createEvent(format);
        final boolean ok = lobby.configureEvent(poolType, draft, Math.max(0, pickSeconds),
                Math.max(0, graceSeconds));
        if (!ok) {
            // ⚠️ Sin esto la sala se queda con un evento A MEDIAS y no lo dice.
            // createEvent ya ha dejado puesto el currentEvent, asi que la
            // pantalla ensenya "listo para empezar", el boton se enciende y al
            // pulsarlo no pasa nada: startSealedEvent se planta al no encontrar
            // generador y vuelve sin un solo aviso. Lo caza NetEventCheck.
            lobby.clearCurrentEvent();
        }
        return ok;
    }

    /**
     * Arranca el evento: reparte los sobres o los pools.
     *
     * <p><b>Hilo de fondo</b>: {@code startDraftEvent} inicializa los sobres de
     * todo el pod y {@code startSealedEvent} abre seis por cabeza.
     *
     * @return el motivo por el que no se ha podido, o null si ha arrancado
     */
    public static String start(final ServerGameLobby lobby) {
        final NetworkEvent event = lobby == null ? null : lobby.getCurrentEvent();
        if (event == null) {
            return "noEvent";
        }
        // El motor NO lo comprueba al arrancar el evento, y la mezcla es mala:
        // se reparten los sobres y luego resulta que falta gente por sentarse.
        if (lobby.findFirstUnreadySlot() != null) {
            return "notReady";
        }
        if (event.getFormat() == EventFormat.BOOSTER_DRAFT) {
            return lobby.startDraftEvent() == null ? "draftFailed" : null;
        }
        lobby.startSealedEvent();
        return null;
    }

    // ------------------------------------------------------------------
    // Leer el evento, desde cualquiera de los dos lados
    // ------------------------------------------------------------------

    /**
     * El evento que hay en marcha, visto por <b>los dos lados</b>.
     *
     * <p>El anfitrion tiene el {@code NetworkEvent} entero; el invitado solo
     * recibe esta foto dentro del {@code GameLobbyData}. Se pregunta por aqui
     * siempre, por lo mismo que {@link NeoLobby#formatOf}: lo que no viaja por
     * el cable no se puede usar para pintar la pantalla del invitado.
     */
    public static NetworkEventView viewOf(final GameLobby lobby) {
        return lobby == null || lobby.getData() == null ? null : lobby.getData().getEventView();
    }

    /** Si esta sala esta jugando un evento de limitado. */
    public static boolean isLimited(final GameLobby lobby) {
        return lobby != null && lobby.getData() != null && lobby.getData().isLimitedMode();
    }

    /** El evento cuyo pool hay que usar para elegir mazo, o null. */
    public static String activeEventId(final GameLobby lobby) {
        return lobby == null || lobby.getData() == null ? null : lobby.getData().getActiveEventId();
    }

    /** Si hay que exigir que el mazo salga del pool de ese evento. */
    public static boolean conformance(final GameLobby lobby) {
        return lobby != null && lobby.getData() != null && lobby.getData().isActiveConformance();
    }

    // ------------------------------------------------------------------
    // Los pools guardados
    // ------------------------------------------------------------------

    /**
     * Los mazos de eventos en red que hay en el disco.
     *
     * <p>Viven en su propia carpeta ({@code DECK_NET_EVENT_DIR}) y no se
     * mezclan con los tuyos: un pool de draft son 45 cartas sueltas, no un mazo
     * que quieras ver en la lista de Commander.
     *
     * <p>Se relee del disco cada vez porque quien lo escribe puede ser otra
     * pantalla (el constructor, al guardar el mazo montado con el pool) y en
     * memoria nos quedariamos con la lista de antes.
     */
    public static List<Deck> pools() {
        FModel.getDecks().reloadNetworkEventDecks();
        final List<Deck> out = new ArrayList<>();
        FModel.getDecks().getNetworkEventDecks().forEach(out::add);
        return out;
    }

    /**
     * Los mazos con los que se puede jugar ESTE evento.
     *
     * <p>Con el filtro puesto — que es lo normal — solo valen los que salieron
     * de su pool: es lo que impide sentarse en un draft con un mazo de
     * Commander de tu carpeta. El anfitrion puede quitarlo, y entonces vale
     * cualquier mazo de evento.
     */
    public static List<Deck> poolsFor(final String eventId, final boolean conformance) {
        final List<Deck> all = pools();
        if (eventId == null || !conformance) {
            return all;
        }
        final List<Deck> out = new ArrayList<>();
        for (final Deck d : all) {
            if (eventId.equals(DeckProxy.getEventTag(d, "eventId"))) {
                out.add(d);
            }
        }
        return out;
    }

    /** De que evento salio este mazo, o null si no es de ninguno. */
    public static String eventIdOf(final Deck deck) {
        return deck == null ? null : DeckProxy.getEventTag(deck, "eventId");
    }

    /**
     * Guarda el pool que acaba de llegar.
     *
     * <p>Las etiquetas las pone Forge ({@code NetworkEvent.setEventTags}) y son
     * lo que ata el pool a su evento; sin ellas el filtro de mazos no reconoce
     * nada y la sala te deja sentarte con lo que sea. El anfitrion las tiene;
     * el invitado recibe el mazo ya etiquetado desde el servidor, asi que solo
     * se ponen si faltan.
     */
    public static void savePool(final Deck pool, final NetworkEvent event) {
        if (pool == null) {
            return;
        }
        if (event != null && eventIdOf(pool) == null) {
            NetworkEvent.setEventTags(pool, event);
        }
        FModel.getDecks().getNetworkEventDecks().add(pool);
    }

    /**
     * Como se llama un evento en pantalla.
     *
     * <p>Lo compone Forge a partir de las etiquetas del pool guardado
     * ("Draft - Innistrad - 2026-09-20"), asi que un evento de hace un mes se
     * reconoce sin tener que acordarse del identificador.
     */
    public static String label(final String eventId) {
        return NetworkEvent.getEventDisplayLabel(eventId);
    }

    /**
     * Los eventos ya jugados, del mas nuevo al mas viejo.
     *
     * <p>Sirven para volver a montar una partida con un pool que ya tienes sin
     * draftear otra vez — que es lo normal cuando la sesion anterior se corto a
     * mitad, o cuando quedais otro dia con los mismos mazos.
     */
    public static List<NetworkEvent.EventChoice> pastEvents() {
        final java.util.Map<String, String> dates = new java.util.LinkedHashMap<>();
        for (final Deck d : pools()) {
            final String id = eventIdOf(d);
            if (id != null) {
                dates.putIfAbsent(id, DeckProxy.getEventTag(d, "eventDate"));
            }
        }
        final List<String> ids = new ArrayList<>(dates.keySet());
        // eventDate es "yyyy-MM-dd HH:mm", asi que el orden lexico invertido
        // ya deja los mas nuevos arriba.
        ids.sort(java.util.Comparator.comparing(
                (final String id) -> dates.get(id) == null ? "" : dates.get(id),
                java.util.Comparator.reverseOrder()));
        final List<NetworkEvent.EventChoice> out = new ArrayList<>(ids.size());
        for (final String id : ids) {
            out.add(new NetworkEvent.EventChoice(id, label(id)));
        }
        return out;
    }
}
