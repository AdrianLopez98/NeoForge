package forge.neo.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * <b>Los destellos de "esto acaba de recibir"</b>: chispas del color del
 * hechizo sobre su objetivo, y un remolino bajo el permanente que acaba de
 * entrar lanzado.
 *
 * <p>Pedido en Discord el 17-10-2026: <i>"would it be possible to give some
 * sort of animation to cards hitting other cards? Even just a colored sparkle
 * effect ... to indicate what got targeted before it whisks off to the
 * grave"</i>. Y es informacion, no solo adorno: la IA resuelve en
 * milisegundos, y una criatura que se va sin que nada la senyale es una
 * criatura que no sabes quien te quito (principio 9).
 *
 * <p>Tres decisiones que salen de lo que se temia (Ana, el mismo dia: <i>"si
 * aumenta la memoria, te acerca al limite"</i>):
 * <ul>
 *   <li><b>Nada de un lienzo a pantalla completa.</b> Un {@code Canvas} del
 *       tamanyo de la mesa son 15-33 MB de textura viva toda la partida. Aqui
 *       cada efecto son ~20 circulos que viven medio segundo y se van.</li>
 *   <li><b>Un solo reloj</b>, y solo mientras hay algo en pantalla. Se para
 *       solo al vaciarse, y al salir de la escena se para y se vacia (principio
 *       11: lo que vive mas que la pantalla no puede agarrarla).</li>
 *   <li><b>Tope de efectos a la vez</b> ({@link #MAX_LIVE}). Trescientos
 *       disparos con objetivo no son trescientos efectos: los que no caben, no
 *       salen.</li>
 * </ul>
 *
 * <p>No coge el raton nunca: lo que pinta es informacion, y el motor puede
 * estar esperando justo un clic en la carta que destella.
 *
 * <p>Se apaga en Ajustes (Pantalla → "Destellos de hechizos") y tambien con las
 * animaciones. {@code --impact-test} los dispara sin partida para capturarlos.
 */
public final class ImpactLayer extends Pane {

    /** Donde se pinta cada cosa. El mismo de las flechas, con los fantasmas. */
    private CombatOverlay.Locator locator;

    /** Efectos a la vez, como mucho. */
    static final int MAX_LIVE = 12;

    /** Lo que espera a que su carta aparezca (la que entra la pinta el refresco). */
    private static final double LOCATE_TIMEOUT = 0.7;

    private static volatile boolean enabled = true;

    /** -Dneo.impact.log=true: donde ha caido cada destello, para probarlo sin mirar. */
    private static final boolean LOG = Boolean.getBoolean("neo.impact.log");

    public static void setEnabled(final boolean on) {
        enabled = on;
    }

    private static volatile boolean entranceEnabled = true;

    /** La entrada de tu comandante ({@link CommanderEntrance}), con su propio ajuste. */
    public static void setEntranceEnabled(final boolean on) {
        entranceEnabled = on;
    }

    public static boolean isEntranceEnabled() {
        return entranceEnabled && forge.neo.card.CardNode.areAnimationsEnabled();
    }

    /** La que se esta viendo, y las que esperan turno (dos comandantes a la vez). */
    private CommanderEntrance entrance;
    private final java.util.ArrayDeque<Object[]> entrancesWaiting = new java.util.ArrayDeque<>();

    /**
     * Tu comandante acaba de entrar en la mesa. Una a una: si entran dos a la
     * vez (compañeros), la segunda espera a que acabe la primera.
     */
    public void commander(final forge.game.card.CardView card, final List<Color> colors, final String subtitle,
            final forge.neo.match.CommanderSignature signature) {
        if (card == null || !isEntranceEnabled() || getScene() == null) {
            return;
        }
        if (entrance != null) {
            if (entrancesWaiting.size() < 3) {
                entrancesWaiting.add(new Object[] {card, colors, subtitle, signature});
            }
            return;
        }
        entranceOnTop(true);
        final CommanderEntrance started = new CommanderEntrance(this, card, CommanderEntrance.colorsFor(colors),
                subtitle, signature, this::nextEntrance);
        entrance = started;
        started.start();
        // EL TOPE: lo normal es soltar en cuanto acaba (onDone), pero con esta
        // capa delante y comiendose los clics, una entrada que no acabara (un
        // fallo dentro de su temporizador) dejaria la mesa sin respuesta. Asi
        // que, pase lo que pase, a los MAX_ENTRANCE_SECONDS se para y suelta
        // (idea del autor, 19-10-2026). La mas larga dura 2,2 s (1,8 + 0,4 de las
        // firmas). Salvo con la bandera que la congela a proposito.
        if (System.getProperty("neo.entrance.freezeAt") == null) {
            final javafx.animation.PauseTransition guard =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(MAX_ENTRANCE_SECONDS));
            guard.setOnFinished(e -> {
                if (entrance == started) {
                    System.err.println("[entrada] tope de " + MAX_ENTRANCE_SECONDS + " s: se para y suelta la mesa");
                    started.stop();
                    nextEntrance();
                }
            });
            guard.play();
        }
    }

    /**
     * Mientras dura una entrada de comandante (o la cola de ellas), NADA por
     * encima: Ana, 19-10-2026, <i>"al entrar mi comandante se disparaba un
     * encantamiento en la mesa rival y salia el pop up de ese encantamiento
     * encima de la animacion"</i>. Esta capa va de normal DEBAJO de carteles,
     * avisos y dialogos (que es lo correcto para los efectos de hechizo); durante
     * la entrada se pone delante de todos sus hermanos de la mesa y al acabar
     * vuelve a su sitio. Lo que el motor pregunte mientras tanto no se pierde:
     * sigue debajo y aparece en cuanto acaba (dura menos de dos segundos).
     *
     * <p>Y se queda con los clics mientras tanto: delante y transparente al raton,
     * un clic podria contestar sin querer a una pregunta que no se ve.
     */
    private void entranceOnTop(final boolean on) {
        setViewOrder(on ? ENTRANCE_VIEW_ORDER : 0);
        setMouseTransparent(!on);
        setPickOnBounds(on);        if (LOG) {
            System.out.println(on ? "[entrada] delante de todo" : "[entrada] vuelve a su sitio (deja pasar el raton)");
        }
    }

    /** Lo que una entrada puede tener la mesa para ella como mucho. Ver {@link #commander}. */
    static final double MAX_ENTRANCE_SECONDS = 3;

    /** Por delante de todo lo de la mesa (lo mas adelantado ahi va a -1). */
    private static final double ENTRANCE_VIEW_ORDER = -1000;

    @SuppressWarnings("unchecked")
    private void nextEntrance() {
        entrance = null;
        final Object[] next = entrancesWaiting.poll();
        if (next != null) {
            commander((forge.game.card.CardView) next[0], (List<Color>) next[1], (String) next[2],
                    (forge.neo.match.CommanderSignature) next[3]);
        }
        if (entrance == null) {
            entranceOnTop(false);
        }
    }

    /** Los efectos de hechizo famoso en marcha ({@link SpellBlast}); como mucho dos a la vez. */
    private final java.util.List<SpellBlast> blasts = new java.util.ArrayList<>();

    /**
     * Un hechizo famoso acaba de resolverse ({@link forge.neo.match.SpellSignature}).
     * {@code target} en coordenadas de la mesa (la carta objetivo), o null para
     * la mesa entera. {@code onImpact} es el temblor, al golpe.
     */
    public void spell(final forge.neo.match.SpellSignature signature, final Bounds target,
            final Runnable onImpact) {
        if (signature == null || signature == forge.neo.match.SpellSignature.NONE || !isEnabled()
                || getScene() == null || blasts.size() >= 2) {
            return;
        }
        final SpellBlast[] b = new SpellBlast[1];
        b[0] = new SpellBlast(this, signature, target, onImpact, () -> blasts.remove(b[0]));
        blasts.add(b[0]);
        b[0].start();
    }

    /** Si hay un efecto de hechizo en marcha. Para las pruebas. */
    public boolean blastShowing() {
        return !blasts.isEmpty();
    }

    /** Si hay una entrada en marcha. Para las pruebas. */
    public boolean entranceShowing() {
        return entrance != null;
    }

    /** Donde esta pintada esa entidad ahora mismo (o su fantasma), o null. */
    Bounds locate(final Object entity) {
        return locator == null ? null : locator.boundsOf(entity);
    }

    /** Encendido en Ajustes, y las animaciones tambien. */
    public static boolean isEnabled() {
        return enabled && forge.neo.card.CardNode.areAnimationsEnabled();
    }

    private enum Kind {
        /** Lo que tenia por objetivo: vuela hasta el y estalla su elemento. */
        HIT,
        /** Lo que acaba de entrar lanzado: su contorno se materializa. */
        ARRIVE,
        /** El numero del dano que acaba de recibir, como en Arena ({@link #damage}). */
        DAMAGE
    }

    /** Lo que tarda el atacante en llegar con su golpe ({@code Anim.strike}). */
    private static final double COMBAT_REACH = 0.11;
    /**
     * Cuanto espera el numero de un dano que NO es de combate a que aparezca el
     * hechizo que lo hace: el motor avisa del dano antes que de que se ha
     * resuelto, y el proyectil sale despues. Sin proyectil (un barrido sin
     * objetivo, un disparo), sale pasado esto.
     */
    private static final double WAIT_FOR_PROJECTILE = 0.2;

    private final List<Effect> live = new ArrayList<>();
    private final Random random = new Random();
    private boolean running;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            step(now);
        }
    };

    public ImpactLayer() {
        setMouseTransparent(true);
        setPickOnBounds(false);
        // Solo recibe raton durante una entrada (entranceOnTop): ahi se lo come.
        addEventHandler(javafx.scene.input.MouseEvent.ANY, javafx.event.Event::consume);
        addEventHandler(javafx.scene.input.ScrollEvent.ANY, javafx.event.Event::consume);
        getStyleClass().add("impact-layer");
        sceneProperty().addListener((o, was, is) -> {
            if (is == null) {
                clear();
            }
        });
    }

    public void setLocator(final CombatOverlay.Locator locator) {
        this.locator = locator;
    }

    /** Cuantos efectos hay en marcha. Para las pruebas. */
    public int liveCount() {
        return live.size();
    }

    /**
     * Esto acaba de ser el objetivo de algo que se ha resuelto.
     *
     * @param entity {@code CardView} o {@code PlayerView}
     * @param colors los del hechizo; vacio = incoloro
     * @param source de donde sale volando antes de impactar (quien lo lanzo,
     *               o el permanente de la habilidad); null = impacta sin viaje
     */
    public void hit(final Object entity, final List<Color> colors, final Object source) {
        start(entity, colors, Kind.HIT, source);
    }

    public void hit(final Object entity, final List<Color> colors) {
        hit(entity, colors, null);
    }

    /** Este permanente acaba de entrar en la mesa porque lo han lanzado. */
    public void arrive(final Object entity, final List<Color> colors) {
        start(entity, colors, Kind.ARRIVE, null);
    }

    /**
     * <b>El numero del dano</b>, como en Arena (Discord, 18-10-2026: <i>"the
     * animation that the creature does when it hits another creature or
     * another player, with the floating number"</i>): "-3" salta sobre la
     * carta o el retrato que lo recibe, sube un poco y se apaga, con un anillo
     * del golpe. Sale cuando el golpe LLEGA: en combate, cuando la criatura
     * que ataca alcanza a su victima; de un hechizo, cuando su proyectil
     * impacta (lo busca entre lo que hay en marcha).
     *
     * @param poison veneno o contadores -1/-1 (infectar, marchitar): en verde
     * @param onPop lo que pasa en el instante del golpe (sacudir el retrato,
     *              el destello rojo de la carta)
     */
    public void damage(final Object entity, final int amount, final boolean combat, final boolean poison,
            final Runnable onPop) {
        if (amount <= 0) {
            return;
        }
        final int before = live.size();
        start(entity, List.of(poison ? GREEN : RED), Kind.DAMAGE, null);
        if (live.size() > before) {
            final Effect e = live.get(live.size() - 1);
            e.amount = amount;
            e.combat = combat;
            e.poison = poison;
            e.onPop = onPop;
        } else if (onPop != null) {
            // Sin sitio para el numero (o apagado): el golpe se cuenta igual.
            onPop.run();
        }
    }

    private void start(final Object entity, final List<Color> colors, final Kind kind, final Object source) {
        if (entity == null || !isEnabled() || getScene() == null || live.size() >= MAX_LIVE) {
            return;
        }
        live.add(new Effect(entity, colors == null || colors.isEmpty() ? List.of(COLORLESS) : colors, kind,
                source));
        if (!running) {
            running = true;
            timer.start();
        }
    }

    /** Fuera de la escena, o se ha apagado: nada sigue vivo. */
    public void clear() {
        timer.stop();
        running = false;
        entrancesWaiting.clear();
        for (final SpellBlast b : new java.util.ArrayList<>(blasts)) {
            b.stop();
        }
        blasts.clear();
        if (entrance != null) {
            final CommanderEntrance e = entrance;
            entrance = null;
            e.stop();
        }
        entranceOnTop(false);
        for (final Effect e : live) {
            getChildren().remove(e.group);
        }
        live.clear();
    }

    private void step(final long now) {
        if (getScene() == null || !isEnabled()) {
            clear();
            return;
        }
        final Iterator<Effect> it = live.iterator();
        while (it.hasNext()) {
            final Effect e = it.next();
            if (!e.advance(now)) {
                getChildren().remove(e.group);
                it.remove();
            }
        }
        if (live.isEmpty()) {
            timer.stop();
            running = false;
        }
    }

    // ---------------------------------------------------------------
    // Los colores
    // ---------------------------------------------------------------

    /** Los del mana, pero que brillen sobre la mesa oscura (el negro, violeta). */
    public static final Color WHITE = Color.web("#FFF1C9");
    public static final Color BLUE = Color.web("#5AB0FF");
    public static final Color BLACK = Color.web("#B07CE8");
    public static final Color RED = Color.web("#FF6B47");
    public static final Color GREEN = Color.web("#5FD38A");
    public static final Color COLORLESS = Color.web("#D3DEEA");

    /** Los colores de una carta, en el orden WUBRG. Vacio si es incolora. */
    public static List<Color> colorsOf(final forge.card.ColorSet set) {
        final List<Color> out = new ArrayList<>(5);
        if (set == null) {
            return out;
        }
        if (set.hasWhite()) {
            out.add(WHITE);
        }
        if (set.hasBlue()) {
            out.add(BLUE);
        }
        if (set.hasBlack()) {
            out.add(BLACK);
        }
        if (set.hasRed()) {
            out.add(RED);
        }
        if (set.hasGreen()) {
            out.add(GREEN);
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Un efecto
    // ---------------------------------------------------------------

    /**
     * UN ELEMENTO POR COLOR (Ana, 18-10-2026: <i>"cuando juegas un
     * instantaneo negro que salga como humillo morado, cuando juegas uno rojo
     * algo de fuego"</i>). Lo que se ve al impactar sale del color del hechizo,
     * y uno multicolor mezcla los suyos.
     */
    enum Element {
        /** Blanco: un rayo de luz desde arriba y estrellas que titilan. */
        LIGHT(Color.web("#FFF6D8"), Color.web("#FFD36B"), 0.85),
        /** Azul: ondas y gotas que salpican y caen. */
        WATER(Color.web("#B8E6FF"), Color.web("#3E9BF0"), 0.8),
        /** Negro: humo morado que se abre y sube. */
        SMOKE(Color.web("#B48CF0"), Color.web("#5A2F9A"), 1.05),
        /** Rojo: llamas que suben y brasas. */
        FIRE(Color.web("#FFE08A"), Color.web("#FF5A1F"), 0.85),
        /** Verde: hojas en remolino y motas de luz. */
        NATURE(Color.web("#C8F59A"), Color.web("#3FAE5C"), 1.0),
        /** Incoloro: chispas de metal y un hexagono. */
        METAL(Color.web("#F2F6FA"), Color.web("#9AAABB"), 0.6);

        final Color core;
        final Color body;
        final double seconds;

        Element(final Color core, final Color body, final double seconds) {
            this.core = core;
            this.body = body;
            this.seconds = seconds;
        }

        static Element of(final Color c) {
            if (c == WHITE) {
                return LIGHT;
            }
            if (c == BLUE) {
                return WATER;
            }
            if (c == BLACK) {
                return SMOKE;
            }
            if (c == RED) {
                return FIRE;
            }
            if (c == GREEN) {
                return NATURE;
            }
            return METAL;
        }
    }

    /** Lo que se pinta de cada particula en cada fotograma. */
    private interface Painter {
        /** @param u segundos desde el impacto (negativo: aun no) */
        void paint(double u);
    }

    private final class Effect {
        private final Object entity;
        private final Object source;
        private final List<Element> elements = new ArrayList<>();
        private final Kind kind;
        private final Group group = new Group();
        private long startNs = -1;
        /** Ultimo sitio conocido: la carta puede irse a mitad del efecto. */
        private Bounds where;
        private Bounds from;
        private boolean built;
        private double travel;
        private double duration;
        private final List<Painter> painters = new ArrayList<>();
        // DAMAGE: cuanto, de donde y que hacer al golpe.
        private int amount;
        private boolean combat;
        private boolean poison;
        private Runnable onPop;

        Effect(final Object entity, final List<Color> colors, final Kind kind, final Object source) {
            this.entity = entity;
            this.kind = kind;
            this.source = source;
            for (final Color c : colors) {
                final Element e = Element.of(c);
                if (!elements.contains(e)) {
                    elements.add(e);
                }
            }
            if (elements.isEmpty()) {
                elements.add(Element.METAL);
            }
            group.setMouseTransparent(true);
        }

        /** @return false cuando ya ha terminado (o no ha encontrado su carta) */
        boolean advance(final long now) {
            if (startNs < 0) {
                startNs = now;
            }
            final Bounds b = locator == null ? null : locator.boundsOf(entity);
            if (b != null && b.getWidth() > 1 && b.getHeight() > 1) {
                where = b;
            }
            // El numero espera a que el golpe llegue (startNs es, hasta que se
            // construye, cuando se pidio).
            if (kind == Kind.DAMAGE && !built && !readyToPop(now)) {
                return (now - startNs) / 1e9 < LOCATE_TIMEOUT + 1.0;
            }
            if (!built) {
                if (where == null) {
                    // Todavia no esta pintada (la que entra) o ya no se ve.
                    final boolean waiting = (now - startNs) / 1e9 < LOCATE_TIMEOUT;
                    if (!waiting && LOG) {
                        System.out.println("[destellos] no se ve " + entity);
                    }
                    return waiting;
                }
                if (source != null && locator != null) {
                    from = locator.boundsOf(source);
                }
                build();
                if (LOG) {
                    System.out.printf("[destellos] %d %s %s sobre %s, impacta en %d ms%n",
                            System.nanoTime() / 1_000_000L, kind, elements, entity, Math.round(travel * 1000));
                }
                // El reloj empieza cuando se ve: si no, lo que tardo en
                // aparecer se comeria el principio del efecto.
                startNs = now;
            }
            final double t = (now - startNs) / 1e9;
            if (t >= duration) {
                return false;
            }
            for (final Painter p : painters) {
                p.paint(t - travel);
            }
            return true;
        }

        private double size() {
            return Math.max(24, Math.min(where.getWidth(), where.getHeight()));
        }

        /** Si ya ha llegado el golpe de este dano (ver {@link #damage}). */
        private boolean readyToPop(final long now) {
            final double waited = (now - startNs) / 1e9;
            final Effect shot = projectileFor(entity);
            if (shot != null) {
                if (!shot.built) {
                    return waited >= LOCATE_TIMEOUT;
                }
                return now >= shot.startNs + (long) (shot.travel * 1e9);
            }
            return waited >= (combat ? COMBAT_REACH : WAIT_FOR_PROJECTILE);
        }

        private double cx() {
            return where.getCenterX();
        }

        private double cy() {
            return where.getCenterY();
        }

        private double rnd(final double a, final double b) {
            return a + random.nextDouble() * (b - a);
        }

        private <T extends javafx.scene.Node> T add(final T n) {
            n.setOpacity(0);
            group.getChildren().add(n);
            return n;
        }

        private void build() {
            built = true;
            final boolean card = entity instanceof forge.game.card.CardView;
            // Varios colores: cada uno con menos particulas, para que el total
            // no crezca con los colores.
            final double share = elements.size() == 1 ? 1 : elements.size() == 2 ? 0.65 : 0.45;
            double longest = 0;
            for (final Element e : elements) {
                longest = Math.max(longest, e.seconds);
            }
            if (kind == Kind.DAMAGE) {
                buildDamage();
                duration = 1.05;
                getChildren().add(group);
                if (onPop != null) {
                    onPop.run();
                }
                return;
            }
            if (kind == Kind.HIT) {
                buildTravel();
                if (card) {
                    buildTint(elements.get(0));
                }
                buildFlash(elements.get(0));
                for (final Element e : elements) {
                    buildElement(e, share, false);
                }
            } else {
                travel = 0;
                buildMaterialize(elements.get(0));
                for (final Element e : elements) {
                    buildElement(e, share * 0.6, true);
                }
            }
            duration = travel + longest + 0.05;
            getChildren().add(group);
        }

        // ---- el dano: el numero que salta, y el anillo del golpe ----

        private void buildDamage() {
            travel = 0;
            final boolean card = entity instanceof forge.game.card.CardView;
            final double s = size();
            final Color hot = poison ? Color.web("#9BFF7A") : Color.web("#FF5A44");
            final Color deep = poison ? Color.web("#2E8A23") : Color.web("#B3141A");
            // El anillo del golpe, y en un jugador un fogonazo rojo: el retrato
            // es pequenyo, y sin el no se ve que te han dado.
            if (!card) {
                final Circle burst = add(new Circle(1, new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                        CycleMethod.NO_CYCLE,
                        new Stop(0, hot.deriveColor(0, 1, 1, 0.75)),
                        new Stop(1, hot.deriveColor(0, 1, 1, 0)))));
                painters.add(u -> {
                    final double p = Math.min(1, u / 0.35);
                    burst.setCenterX(cx());
                    burst.setCenterY(cy());
                    burst.setRadius(s * (0.5 + 0.35 * p));
                    burst.setOpacity(u < 0.35 ? 0.85 * (1 - p) : 0);
                });
            }
            final Circle ring = add(new Circle());
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(hot);
            painters.add(u -> {
                final double p = Math.min(1, u / 0.32);
                final double e = 1 - Math.pow(1 - p, 3);
                ring.setCenterX(cx());
                ring.setCenterY(cy());
                ring.setRadius(s * (0.22 + (card ? 0.42 : 0.62) * e));
                ring.setStrokeWidth(Math.max(1.5, s * 0.07 * (1 - e)));
                ring.setOpacity(u < 0.32 ? 0.9 * (1 - p) : 0);
            });
            // EL NUMERO: salta con rebote, sube un poco y se apaga.
            final double fs = Math.max(22, Math.min(72, s * (card ? 0.42 : 0.6)));
            final javafx.scene.text.Text n = add(new javafx.scene.text.Text("-" + amount));
            n.setFont(javafx.scene.text.Font.font(null, javafx.scene.text.FontWeight.EXTRA_BOLD, fs));
            n.setFill(new javafx.scene.paint.LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FFF1EC")), new Stop(0.45, hot), new Stop(1, deep)));
            n.setStroke(Color.rgb(25, 0, 0, 0.92));
            n.setStrokeWidth(Math.max(1.5, fs * 0.07));
            n.setStrokeType(javafx.scene.shape.StrokeType.OUTSIDE);
            final double w0 = n.getLayoutBounds().getWidth();
            final double h0 = n.getLayoutBounds().getHeight();
            painters.add(u -> {
                final double sc = u < 0.1 ? 0.4 + 0.9 * (1 - Math.pow(1 - u / 0.1, 2))
                        : u < 0.22 ? 1.3 - 0.3 * ((u - 0.1) / 0.12) : 1.0;
                final double rise = s * (card ? 0.35 : 0.5) * (1 - Math.pow(1 - Math.min(1, u / 0.95), 3));
                n.setScaleX(sc);
                n.setScaleY(sc);
                n.setX(cx() - w0 / 2);
                n.setY(cy() + h0 * 0.3 - rise);
                n.setOpacity(u < 0.6 ? 1 : Math.max(0, 1 - (u - 0.6) / 0.4));
            });
        }

        // ---- el viaje: el hechizo vuela de quien lo lanza a su objetivo ----

        private void buildTravel() {
            travel = 0;
            if (from == null || from.getWidth() < 1) {
                return;
            }
            final double sx = from.getCenterX();
            final double sy = from.getCenterY();
            final double dist = Math.hypot(cx() - sx, cy() - sy);
            if (dist < size() * 0.8) {
                return;
            }
            travel = Math.max(0.2, Math.min(0.36, dist / 1400.0));
            final Element e = elements.get(0);
            final double bend = (random.nextBoolean() ? 1 : -1) * dist * 0.18;
            final double mx = (sx + cx()) / 2 + (-(cy() - sy) / dist) * bend;
            final double my = (sy + cy()) / 2 + ((cx() - sx) / dist) * bend;
            final double r = Math.max(7, Math.min(16, size() * 0.1));
            final int trail = 8;
            for (int k = trail; k >= 0; k--) {
                final int lag = k;
                final double kr = r * (1 - k / (trail + 2.0));
                final Color c = k == 0 ? e.core : (k % 2 == 0 ? e.body : elements.get(k % elements.size()).body);
                final Circle dot = add(new Circle(kr, glow(c)));
                painters.add(u -> {
                    final double tt = u + travel - lag * 0.022;
                    final double p = tt / travel;
                    if (tt < 0 || p >= 1 || u >= 0) {
                        dot.setOpacity(0);
                        return;
                    }
                    final double q = 1 - p;
                    final double e2 = p * p * (3 - 2 * p);
                    final double x = q * q * sx + 2 * q * e2 * mx + e2 * e2 * cx();
                    final double y = q * q * sy + 2 * q * e2 * my + e2 * e2 * cy();
                    dot.setCenterX(x);
                    dot.setCenterY(y);
                    dot.setOpacity(lag == 0 ? 1 : 0.85 * (1 - lag / (double) (trail + 1)));
                });
            }
        }

        // ---- lo comun del impacto ----

        private void buildTint(final Element e) {
            final Rectangle tint = add(new Rectangle());
            tint.setArcWidth(10);
            tint.setArcHeight(10);
            tint.setFill(e == Element.SMOKE ? Color.web("#2A1540") : e.body);
            final double peak = e == Element.SMOKE ? 0.5 : 0.36;
            painters.add(u -> {
                tint.setX(where.getMinX());
                tint.setY(where.getMinY());
                tint.setWidth(where.getWidth());
                tint.setHeight(where.getHeight());
                tint.setOpacity(u < 0 ? 0 : u < 0.06 ? u / 0.06 * peak : Math.max(0, peak * (1 - (u - 0.06) / 0.4)));
            });
        }

        private void buildFlash(final Element e) {
            final Circle bloom = add(new Circle(1, new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.WHITE.deriveColor(0, 1, 1, 0.9)),
                    new Stop(0.3, e.core.deriveColor(0, 1, 1, 0.7)),
                    new Stop(1, e.body.deriveColor(0, 1, 1, 0)))));
            painters.add(u -> {
                if (u < 0 || u > 0.3) {
                    bloom.setOpacity(0);
                    return;
                }
                final double p = u / 0.3;
                bloom.setCenterX(cx());
                bloom.setCenterY(cy());
                bloom.setRadius(size() * (0.3 + 0.55 * (1 - Math.pow(1 - p, 2))));
                bloom.setOpacity(p < 0.2 ? p / 0.2 : 1 - (p - 0.2) / 0.8);
            });
        }

        /** Al entrar un permanente: su contorno se materializa y una onda en el suelo. */
        private void buildMaterialize(final Element e) {
            final Rectangle edge = add(new Rectangle());
            edge.setArcWidth(12);
            edge.setArcHeight(12);
            edge.setFill(Color.TRANSPARENT);
            edge.setStroke(e.core);
            final javafx.scene.shape.Ellipse ground = add(new javafx.scene.shape.Ellipse());
            ground.setFill(Color.TRANSPARENT);
            ground.setStroke(e.body.deriveColor(0, 1, 1.25, 1));
            painters.add(u -> {
                if (u < 0) {
                    return;
                }
                final double w = where.getWidth();
                final double h = where.getHeight();
                final double p = Math.min(1, u / 0.32);
                final double sc = 1.22 - 0.22 * (1 - Math.pow(1 - p, 3));
                edge.setWidth(w * sc);
                edge.setHeight(h * sc);
                edge.setX(cx() - w * sc / 2);
                edge.setY(cy() - h * sc / 2);
                edge.setStrokeWidth(Math.max(1.5, size() * 0.05 * (1 - p * 0.6)));
                edge.setOpacity(u < 0.1 ? u / 0.1 : Math.max(0, 1 - (u - 0.1) / 0.45));
                final double g = Math.min(1, Math.max(0, (u - 0.18) / 0.5));
                final double ge = 1 - Math.pow(1 - g, 3);
                ground.setCenterX(cx());
                ground.setCenterY(where.getMaxY() - h * 0.08);
                ground.setRadiusX(w * (0.4 + 0.9 * ge));
                ground.setRadiusY(w * (0.4 + 0.9 * ge) * 0.28);
                ground.setStrokeWidth(Math.max(1, size() * 0.06 * (1 - ge)));
                ground.setOpacity(g <= 0 || g >= 1 ? 0 : 0.9 * (1 - g));
            });
        }

        private void buildElement(final Element e, final double share, final boolean lite) {
            switch (e) {
                case FIRE -> buildFire(share, lite);
                case SMOKE -> buildSmoke(share, lite);
                case WATER -> buildWater(share, lite);
                case NATURE -> buildNature(share, lite);
                case LIGHT -> buildLight(share, lite);
                default -> buildMetal(share, lite);
            }
        }

        private int count(final int base, final double share) {
            return Math.max(2, (int) Math.round(base * share));
        }

        /** Sube y baja: 0 fuera de la vida, entra en {@code in} y se apaga al final. */
        private double fade(final double lt, final double in) {
            if (lt <= 0 || lt >= 1) {
                return 0;
            }
            return lt < in ? lt / in : 1 - (lt - in) / (1 - in);
        }

        // ---- ROJO: llamas que suben y brasas ----

        private void buildFire(final double share, final boolean lite) {
            final RadialGradient flame = new RadialGradient(0, 0, 0.5, 0.68, 0.55, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FFF6C8", 0.95)),
                    new Stop(0.3, Color.web("#FFC24A", 0.9)),
                    new Stop(0.65, Color.web("#FF5A1F", 0.6)),
                    new Stop(1, Color.web("#B3200A", 0)));
            for (int i = 0, n = count(lite ? 6 : 11, share); i < n; i++) {
                final javafx.scene.shape.Ellipse f = add(new javafx.scene.shape.Ellipse());
                f.setFill(flame);
                final double ox = rnd(-0.48, 0.48);
                final double delay = rnd(0, 0.22);
                final double life = rnd(0.45, 0.7);
                final double rx = rnd(0.12, 0.19);
                final double ph = rnd(0, 6.3);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    f.setOpacity(fade(lt, 0.15) * 0.95);
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double rise = s * 1.15 * Math.pow(lt, 0.8);
                    f.setCenterX(cx() + ox * where.getWidth() + Math.sin(lt * 9 + ph) * s * 0.05);
                    f.setCenterY(cy() + where.getHeight() * 0.3 - rise);
                    final double grow = lt < 0.35 ? 0.6 + lt / 0.35 * 0.6 : 1.2 - (lt - 0.35) * 0.9;
                    f.setRadiusX(s * rx * (1.1 - lt * 0.5));
                    f.setRadiusY(s * rx * 2.1 * grow);
                });
            }
            for (int i = 0, n = count(lite ? 8 : 15, share); i < n; i++) {
                final Circle em = add(new Circle(1, glow(i % 3 == 0 ? Color.web("#FFE08A") : Color.web("#FF8A3D"))));
                final double ox = rnd(-0.45, 0.45);
                final double vx = rnd(-0.35, 0.35);
                final double vy = rnd(1.1, 2.0);
                final double delay = rnd(0, 0.3);
                final double life = rnd(0.45, 0.8);
                final double r = rnd(0.018, 0.034);
                final double ph = rnd(0, 6.3);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    final double a = fade(lt, 0.1);
                    em.setOpacity(a * (0.6 + 0.4 * Math.sin(u * 38 + ph)));
                    if (a <= 0) {
                        return;
                    }
                    final double s = size();
                    final double tt = u - delay;
                    em.setCenterX(cx() + ox * where.getWidth() + vx * s * tt);
                    em.setCenterY(cy() + where.getHeight() * 0.2 - vy * s * tt);
                    em.setRadius(s * r);
                });
            }
        }

        // ---- NEGRO: humo morado ----

        private void buildSmoke(final double share, final boolean lite) {
            final RadialGradient puff = new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#9A6AE6", 0.75)),
                    new Stop(0.45, Color.web("#4A2580", 0.55)),
                    new Stop(1, Color.web("#1A0C2E", 0)));
            for (int i = 0, n = count(lite ? 7 : 13, share); i < n; i++) {
                final Circle p = add(new Circle(1, puff));
                final double ang = rnd(0, Math.PI * 2);
                final double drift = rnd(0.3, 0.62);
                final double delay = rnd(0, 0.18);
                final double life = rnd(0.75, 1.0);
                final double r1 = rnd(0.48, 0.75);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    p.setOpacity(fade(lt, 0.18) * 0.9);
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double e = 1 - Math.pow(1 - lt, 2.2);
                    p.setCenterX(cx() + Math.cos(ang) * s * drift * e);
                    p.setCenterY(cy() + Math.sin(ang) * s * drift * 0.6 * e - s * 0.3 * e);
                    p.setRadius(s * (0.18 + (r1 - 0.18) * e));
                });
            }
            for (int i = 0, n = count(lite ? 4 : 7, share); i < n; i++) {
                final Circle w = add(new Circle(1, glow(Color.web("#C9A2FF"))));
                final double a0 = rnd(0, Math.PI * 2);
                final double delay = rnd(0, 0.25);
                final double life = rnd(0.55, 0.8);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    w.setOpacity(fade(lt, 0.2) * 0.85);
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double a = a0 + lt * Math.PI * 1.6;
                    final double rr = s * (0.12 + 0.5 * lt);
                    w.setCenterX(cx() + Math.cos(a) * rr);
                    w.setCenterY(cy() + Math.sin(a) * rr * 0.7 - s * 0.25 * lt);
                    w.setRadius(s * 0.034 * (1 - lt * 0.5));
                });
            }
        }

        // ---- AZUL: ondas y gotas ----

        private void buildWater(final double share, final boolean lite) {
            for (int i = 0, n = lite ? 2 : 3; i < n; i++) {
                final Circle ring = add(new Circle());
                ring.setFill(Color.TRANSPARENT);
                ring.setStroke(i == 0 ? Color.web("#D8F1FF") : Color.web("#7CC4FF"));
                final double delay = i * 0.12;
                painters.add(u -> {
                    final double lt = (u - delay) / 0.6;
                    ring.setOpacity(fade(lt, 0.08) * 0.95);
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double e = 1 - Math.pow(1 - lt, 2.5);
                    ring.setCenterX(cx());
                    ring.setCenterY(cy());
                    ring.setRadius(s * (0.15 + 0.75 * e));
                    ring.setStrokeWidth(Math.max(1, s * 0.05 * (1 - e)));
                });
            }
            for (int i = 0, n = count(lite ? 8 : 14, share); i < n; i++) {
                final Circle d = add(new Circle(1, glow(i % 2 == 0 ? Color.web("#8FD3FF") : Color.web("#3E9BF0"))));
                final double ang = rnd(-Math.PI * 0.92, -Math.PI * 0.08);
                final double v = rnd(1.6, 2.8);
                final double delay = rnd(0, 0.08);
                final double life = rnd(0.55, 0.8);
                final double r = rnd(0.032, 0.056);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    d.setOpacity(fade(lt, 0.08));
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double tt = u - delay;
                    d.setCenterX(cx() + Math.cos(ang) * v * s * tt);
                    d.setCenterY(cy() + Math.sin(ang) * v * s * tt + 3.2 * s * tt * tt);
                    d.setRadius(s * r * (1 - lt * 0.4));
                });
            }
        }

        // ---- VERDE: hojas en remolino ----

        private void buildNature(final double share, final boolean lite) {
            for (int i = 0, n = count(lite ? 7 : 12, share); i < n; i++) {
                final javafx.scene.shape.Ellipse leaf = add(new javafx.scene.shape.Ellipse());
                leaf.setStroke(Color.web("#1E5E33"));
                leaf.setStrokeWidth(1);
                leaf.setFill(new javafx.scene.paint.LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, i % 2 == 0 ? Color.web("#C8F59A") : Color.web("#8FE36A")),
                        new Stop(1, Color.web("#2E8B4A"))));
                final double a0 = rnd(0, Math.PI * 2);
                final double spin = rnd(0.8, 1.4) * (random.nextBoolean() ? 1 : -1);
                final double delay = rnd(0, 0.18);
                final double life = rnd(0.75, 1.0);
                final double rx = rnd(0.08, 0.12);
                final double turn = rnd(200, 520) * (random.nextBoolean() ? 1 : -1);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    leaf.setOpacity(fade(lt, 0.15));
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double e = 1 - Math.pow(1 - lt, 2);
                    final double a = a0 + spin * Math.PI * e;
                    final double rr = s * (0.15 + 0.8 * e);
                    leaf.setCenterX(cx() + Math.cos(a) * rr);
                    leaf.setCenterY(cy() + Math.sin(a) * rr * 0.8 + s * 0.25 * lt * lt);
                    leaf.setRadiusX(s * rx);
                    leaf.setRadiusY(s * rx * 0.42);
                    leaf.setRotate(turn * lt);
                });
            }
            for (int i = 0, n = count(lite ? 4 : 7, share); i < n; i++) {
                final Circle m = add(new Circle(1, glow(Color.web("#E7FF9A"))));
                final double ox = rnd(-0.4, 0.4);
                final double delay = rnd(0.05, 0.3);
                final double life = rnd(0.6, 0.85);
                final double ph = rnd(0, 6.3);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    m.setOpacity(fade(lt, 0.25) * 0.9);
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    m.setCenterX(cx() + ox * where.getWidth() + Math.sin(lt * 7 + ph) * s * 0.06);
                    m.setCenterY(cy() + where.getHeight() * 0.15 - s * 0.6 * lt);
                    m.setRadius(s * 0.028);
                });
            }
        }

        // ---- BLANCO: rayo de luz y estrellas ----

        private void buildLight(final double share, final boolean lite) {
            final Rectangle beam = add(new Rectangle());
            beam.setFill(new javafx.scene.paint.LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FFE7A0", 0)),
                    new Stop(0.35, Color.web("#FFE7A0", 0.35)),
                    new Stop(0.5, Color.web("#FFFBEA", 0.85)),
                    new Stop(0.65, Color.web("#FFE7A0", 0.35)),
                    new Stop(1, Color.web("#FFE7A0", 0))));
            painters.add(u -> {
                final double lt = u / 0.6;
                final double a = fade(lt, 0.15);
                beam.setOpacity(a * (lite ? 0.7 : 1));
                if (a <= 0) {
                    return;
                }
                final double w = where.getWidth() * (lite ? 0.7 : 1.0) * (0.45 + 0.55 * Math.min(1, lt / 0.2));
                final double bottom = cy() + where.getHeight() * 0.35;
                final double h = Math.min(bottom, where.getHeight() * (lite ? 1.6 : 3.2));
                beam.setX(cx() - w / 2);
                beam.setY(bottom - h);
                beam.setWidth(w);
                beam.setHeight(h);
            });
            for (int i = 0, n = count(lite ? 5 : 9, share); i < n; i++) {
                final javafx.scene.shape.Polygon star = add(star());
                star.setFill(i % 3 == 0 ? Color.web("#FFD36B") : Color.web("#FFFBEA"));
                final double ox = rnd(-0.55, 0.55);
                final double oy = rnd(-0.55, 0.5);
                final double delay = rnd(0.02, 0.45);
                final double life = rnd(0.3, 0.5);
                final double sc = rnd(0.07, 0.13);
                painters.add(u -> {
                    final double lt = (u - delay) / life;
                    final double a = lt <= 0 || lt >= 1 ? 0 : Math.sin(Math.PI * lt);
                    star.setOpacity(a);
                    if (a <= 0) {
                        return;
                    }
                    final double s = size();
                    star.setLayoutX(cx() + ox * where.getWidth());
                    star.setLayoutY(cy() + oy * where.getHeight());
                    star.setScaleX(s * sc * a);
                    star.setScaleY(s * sc * a);
                    star.setRotate(lt * 90);
                });
            }
        }

        /** Una estrella de cuatro puntas de radio 1, centrada en su origen. */
        private javafx.scene.shape.Polygon star() {
            final double in = 0.28;
            return new javafx.scene.shape.Polygon(
                    0, -1, in, -in, 1, 0, in, in, 0, 1, -in, in, -1, 0, -in, -in);
        }

        // ---- INCOLORO: chispas de metal y un hexagono ----

        private void buildMetal(final double share, final boolean lite) {
            final javafx.scene.shape.Polygon hex = add(new javafx.scene.shape.Polygon());
            for (int k = 0; k < 6; k++) {
                final double a = Math.PI / 3 * k + Math.PI / 6;
                hex.getPoints().addAll(Math.cos(a), Math.sin(a));
            }
            hex.setFill(Color.TRANSPARENT);
            hex.setStroke(Color.web("#DCE6F0"));
            painters.add(u -> {
                final double lt = u / 0.5;
                hex.setOpacity(fade(lt, 0.1) * 0.9);
                if (lt <= 0 || lt >= 1) {
                    return;
                }
                final double s = size();
                final double e = 1 - Math.pow(1 - lt, 3);
                final double r = s * (0.2 + 0.6 * e);
                hex.setLayoutX(cx());
                hex.setLayoutY(cy());
                hex.setScaleX(r);
                hex.setScaleY(r);
                hex.setStrokeWidth(Math.max(1, s * 0.04 * (1 - e)) / r);
                hex.setRotate(30 * lt);
            });
            for (int i = 0, n = count(lite ? 8 : 14, share); i < n; i++) {
                final Rectangle sp = add(new Rectangle());
                sp.setFill(i % 2 == 0 ? Color.web("#F2F6FA") : Color.web("#FFE7A0"));
                final double ang = rnd(0, Math.PI * 2);
                final double v = rnd(0.7, 1.2);
                final double life = rnd(0.3, 0.5);
                painters.add(u -> {
                    final double lt = u / life;
                    sp.setOpacity(fade(lt, 0.05));
                    if (lt <= 0 || lt >= 1) {
                        return;
                    }
                    final double s = size();
                    final double d = s * v * (1 - Math.pow(1 - lt, 3));
                    final double len = s * 0.13 * (1 - lt * 0.6);
                    sp.setWidth(len);
                    sp.setHeight(Math.max(1, s * 0.014));
                    sp.setX(cx() + Math.cos(ang) * d - len / 2);
                    sp.setY(cy() + Math.sin(ang) * d - sp.getHeight() / 2);
                    sp.setRotate(Math.toDegrees(ang));
                });
            }
        }
    }

    /** Un punto que brilla: blanco en el centro, el color, y se apaga. */
    /** El hechizo que va hacia esa carta o ese jugador, si hay uno en marcha. */
    private Effect projectileFor(final Object entity) {
        for (final Effect e : live) {
            if (e.kind == Kind.HIT && sameEntity(e.entity, entity)) {
                return e;
            }
        }
        return null;
    }

    private static boolean sameEntity(final Object a, final Object b) {
        if (a instanceof forge.game.card.CardView ca && b instanceof forge.game.card.CardView cb) {
            return ca.getId() == cb.getId();
        }
        if (a instanceof forge.game.player.PlayerView pa && b instanceof forge.game.player.PlayerView pb) {
            return pa.getId() == pb.getId();
        }
        return a != null && a.equals(b);
    }

    private static RadialGradient glow(final Color c) {
        return new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE),
                new Stop(0.35, c),
                new Stop(1, c.deriveColor(0, 1, 1, 0)));
    }
}
