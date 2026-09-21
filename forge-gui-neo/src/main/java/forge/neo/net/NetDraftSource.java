package forge.neo.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import forge.gamemodes.net.EventParticipant;
import forge.gamemodes.net.event.DraftPickEvent;
import forge.item.PaperCard;
import forge.neo.draft.PackSource;

/**
 * El draft en red, visto desde la pantalla de picks.
 *
 * <p>Es la mitad nuestra de algo que conduce entero el servidor
 * ({@code BoosterDraftHost}, 543 lineas de Forge): el reparto de sobres, el
 * paso al siguiente asiento, los temporizadores de pick, el plazo de gracia
 * cuando alguien se cae y el pick automatico al agotarse el reloj. Aqui no hay
 * ni una regla de draft — hay <b>lo que se esta mirando ahora mismo</b>.
 *
 * <p><b>El modelo es al reves que el del draft de siempre</b>, y de ahi sale
 * casi todo lo raro de esta clase. En local, la pantalla pide el sobre y el
 * motor lo tiene; aqui el sobre <b>llega</b> cuando el anfitrion lo manda. O
 * sea:
 *
 * <ul>
 *   <li>hay ratos <b>sin sobre en la mano</b> ({@link #waiting()}), porque el
 *       de al lado esta pensando;</li>
 *   <li>el draft es <b>asincrono</b>: el que elige rapido acumula sobres en su
 *       cola mientras espera al lento, asi que se ensenya cuantos tiene cada
 *       uno ({@link #seatNotes()}) — es lo que convierte una espera muda en
 *       informacion;</li>
 *   <li>y el pick <b>se manda</b>, no se aplica. El sobre desaparece en cuanto
 *       se envia: seguir ensenyandolo invitaria a un segundo click que el
 *       servidor ya no va a aceptar.</li>
 * </ul>
 *
 * <p><b>Hilos.</b> Todo lo que escribe aqui viene del hilo de netty; todo lo
 * que lee es la pantalla, en el de JavaFX. Los campos que cruzan van
 * {@code volatile} y las listas se copian al entregarlas, que es mas barato
 * que sincronizar una pantalla que se repinta entera de todas formas.
 */
public final class NetDraftSource implements PackSource {

    /** Mi asiento en el pod. Lo dice el primer sobre que llega. */
    private final int seat;

    /** Quien es quien, para poder poner nombres a las colas. */
    private final List<EventParticipant> participants;

    /**
     * Por donde sale un pick.
     *
     * <p>En el anfitrion va directo al {@code BoosterDraftHost} sin tocar la
     * red; en el invitado, por el cable. Lo decide quien nos construye, que es
     * el unico que sabe de que lado esta.
     */
    private final Consumer<DraftPickEvent> send;

    /** De que son los sobres, para la cabecera. */
    private final String product;

    private volatile List<PaperCard> pack = List.of();
    private final List<PaperCard> picked = new ArrayList<>();
    private volatile int packNumber = 1;
    private volatile int pickNumber = 1;
    private volatile int deadlineMs;
    private volatile long deadlineAt;
    private volatile int[] queues = new int[0];

    /** Ya no hay draft: llego el pool o se corto la conexion. */
    private volatile boolean over;

    public NetDraftSource(final int seat, final List<EventParticipant> participants,
                          final String product, final Consumer<DraftPickEvent> send) {
        this.seat = seat;
        this.participants = participants == null ? List.of() : List.copyOf(participants);
        this.product = product;
        this.send = send;
    }

    public int seat() {
        return seat;
    }

    // ------------------------------------------------------------------
    // Lo que llega por el cable
    // ------------------------------------------------------------------

    /**
     * Ha llegado un sobre.
     *
     * @param timerSeconds plazo para este pick, o 0 si el anfitrion no puso
     *                     reloj. Se guarda como <b>instante de vencimiento</b>
     *                     y no como cuenta: la pantalla se repinta cuando
     *                     puede, y restar de un contador propio acumularia el
     *                     retraso de cada repintado.
     */
    public void packArrived(final List<PaperCard> cards, final int number,
                            final int pick, final int timerSeconds) {
        pack = cards == null ? List.of() : List.copyOf(cards);
        packNumber = number;
        // ⚠️ El servidor manda los picks que ese asiento LLEVA HECHOS, o sea
        // 0 en el primer sobre. La pantalla los cuenta desde 1, igual que el
        // draft de siempre, y sin esto la cabecera empezaba diciendo "pick 0".
        pickNumber = pick + 1;
        deadlineMs = Math.max(0, timerSeconds) * 1000;
        deadlineAt = deadlineMs == 0 ? 0 : System.currentTimeMillis() + deadlineMs;
    }

    /** Alguien ha elegido: cambian las colas del pod. */
    public void seatPicked(final int[] seatQueueDepths) {
        queues = seatQueueDepths == null ? new int[0] : seatQueueDepths.clone();
    }

    /**
     * El servidor ha elegido por alguien (se le acabo el reloj o se cayo).
     *
     * <p>Si el que no llego a tiempo fui yo, la carta que salio hay que
     * apuntarla igual: es mia y tiene que salir en la tira de lo que llevo. El
     * sobre ya no esta — el servidor lo paso al siguiente al elegir.
     */
    public void autoPicked(final int seatIndex, final PaperCard card) {
        if (seatIndex != seat || card == null) {
            return;
        }
        synchronized (picked) {
            picked.add(card);
        }
        pack = List.of();
        deadlineAt = 0;
    }

    /** Se acabo: llego el pool, o se corto. */
    public void finish() {
        over = true;
        pack = List.of();
        deadlineAt = 0;
    }

    // ------------------------------------------------------------------
    // PackSource
    // ------------------------------------------------------------------

    @Override
    public List<PaperCard> currentCards() {
        return new ArrayList<>(pack);
    }

    @Override
    public List<PaperCard> picked() {
        synchronized (picked) {
            return new ArrayList<>(picked);
        }
    }

    /**
     * Manda el pick.
     *
     * <p>El sobre se vacia <b>aqui</b> y no cuando el servidor conteste: entre
     * una cosa y otra hay un viaje de ida y vuelta, y durante ese rato el
     * sobre seguiria en pantalla invitando a un segundo click que ya no vale.
     * La carta se apunta como cogida por lo mismo — que se vea que la jugada
     * salio.
     */
    @Override
    public PaperCard pick(final PaperCard card) {
        if (card == null || pack.isEmpty() || over) {
            return null;
        }
        synchronized (picked) {
            picked.add(card);
        }
        pack = List.of();
        deadlineAt = 0;
        if (send != null) {
            send.accept(new DraftPickEvent(seat, card));
        }
        return card;
    }

    @Override
    public int round() {
        return packNumber;
    }

    @Override
    public int pickNumber() {
        return pickNumber;
    }

    @Override
    public int cardsLeft() {
        return pack.size();
    }

    /**
     * Nunca "terminado" por falta de sobre.
     *
     * <p>Quien dice que el draft se ha acabado es el anfitrion, mandando el
     * pool; hasta entonces, un sobre vacio significa "espera", no "ya esta".
     * Devolver true aqui cerraria la pantalla a mitad de draft, en el primer
     * hueco entre dos sobres.
     */
    @Override
    public boolean isDone() {
        return over;
    }

    @Override
    public boolean waiting() {
        return !over && pack.isEmpty();
    }

    @Override
    public String productName() {
        return product;
    }

    @Override
    public int secondsLeft() {
        final long at = deadlineAt;
        if (at == 0) {
            return 0;
        }
        return (int) Math.max(0, Math.ceil((at - System.currentTimeMillis()) / 1000.0));
    }

    /**
     * Una linea por asiento: quien es y cuantos sobres tiene esperando.
     *
     * <p>El nombre lo resuelve {@code EventParticipant.resolveName}, que es lo
     * que usan las dos GUIs de Forge y ya pone el "(IA)" donde toca. Si el
     * servidor todavia no ha mandado colas, no se ensenya nada: una lista de
     * ceros diria que no hay nadie esperando, que no es lo mismo que "aun no
     * se sabe".
     */
    @Override
    public List<String> seatNotes() {
        final int[] q = queues;
        if (q.length == 0) {
            return List.of();
        }
        final List<String> out = new ArrayList<>(q.length);
        for (int i = 0; i < q.length; i++) {
            final String who = EventParticipant.resolveName(i, participants, participants);
            out.add(forge.neo.NeoText.get(i == seat ? "draft.seat.you" : "draft.seat.other",
                    who, q[i]));
        }
        return out;
    }
}
