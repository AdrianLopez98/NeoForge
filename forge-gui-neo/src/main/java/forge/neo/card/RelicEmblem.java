package forge.neo.card;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * El emblema de cada reliquia, dibujado con figuras.
 *
 * <h2>Por que dibujado</h2>
 *
 * <p>Una reliquia es una carta <b>nuestra</b>: no existe en Magic, asi que
 * Scryfall no tiene arte que bajar y nunca lo va a tener. Hasta el 03-09-2026
 * eso se veia tal cual — la reliquia salia como un rectangulo oscuro con el
 * nombre arriba, y al ampliarla no habia nada mas que leer.
 *
 * <p>Encargar o generar 37 ilustraciones no era una opcion: hay que
 * mantenerlas, reescalarlas y meterlas en el zip (que ya son 455 MB). Es la
 * misma decision que ya se tomo dos veces en este proyecto — {@code FlagIcon}
 * para las banderas y {@link forge.neo.ui.AscentIcon} para los nodos del mapa —
 * y por los mismos motivos: <b>escala a cualquier resolucion, se tine con un
 * parametro y no anyade un byte a la descarga</b>.
 *
 * <h2>Se reconoce por la SILUETA</h2>
 *
 * <p>Principio 3 del las notas de diseño: <i>el estado se ve, no se lee</i>. Llevas hasta
 * ocho reliquias en la barra del mapa, asi que lo que tiene que distinguirlas
 * de un vistazo es el contorno, no un detalle interior. Las siluetas de aqui
 * son distintas <b>en negro</b>.
 *
 * <h2>El motivo NO se deduce del efecto, sino del NOMBRE</h2>
 *
 * <p>Podria dibujarse por familia de efecto (todas las de "+1/+1" con la misma
 * espada). Seria peor: el jugador conoce la reliquia por su nombre — es lo que
 * pone la pastilla del mapa — y una <i>Copper Ring</i> que se dibuja como una
 * gema no se reconoce. Asi que la tabla va por {@code id} de reliquia, y una
 * reliquia nueva sin entrada cae en {@link Motif#SIGIL}, que es un emblema
 * generico digno: <b>anyadir una reliquia no puede reventar el dibujo</b>.
 *
 * <p>Todo se dibuja dentro de un cuadrado de {@code size} x {@code size} con el
 * origen en su centro, para que colocarlo sea poner el centro y ya. Igual que
 * {@code AscentIcon}.
 */
public final class RelicEmblem {

    private RelicEmblem() {
    }

    /** Las siluetas disponibles. */
    public enum Motif {
        CROWN, HEART, HOURGLASS, GAUNTLET, GEM, BANNER, FANG, CHALICE, RING,
        COIN, MAP, COMPASS, SHIELD, SUN, COIL, MASK, BOOK, WINGS, TOTEM, EYE,
        ANVIL, BLADE, IDOL, SEAL, FLAME, ARROW, BOOT, CLOAK, SCROLL, FONT,
        HORN, SIGIL
    }

    /**
     * Que silueta le toca a cada reliquia.
     *
     * <p>Por {@code id} y no por nombre de carta: el id es la clave que no
     * cambia nunca (una run guardada busca por ahi), el nombre podria
     * reescribirse.
     */
    private static final Map<String, Motif> MOTIFS = new LinkedHashMap<>();

    static {
        MOTIFS.put("crown_of_ascent", Motif.CROWN);
        MOTIFS.put("crown_of_the_eternal", Motif.CROWN);
        MOTIFS.put("phoenix_heart", Motif.HEART);
        MOTIFS.put("font_of_souls", Motif.FONT);
        MOTIFS.put("hourglass_of_kings", Motif.HOURGLASS);
        MOTIFS.put("titans_grasp", Motif.GAUNTLET);
        MOTIFS.put("ascendant_geode", Motif.GEM);
        MOTIFS.put("wellspring_stone", Motif.GEM);
        MOTIFS.put("banner_of_legions", Motif.BANNER);
        MOTIFS.put("warlords_standard", Motif.HORN);
        MOTIFS.put("sharpened_fang", Motif.FANG);
        MOTIFS.put("pilgrims_chalice", Motif.CHALICE);
        MOTIFS.put("chalice_of_ages", Motif.CHALICE);
        MOTIFS.put("copper_ring", Motif.RING);
        MOTIFS.put("swiftfoot_anklet", Motif.BOOT);
        MOTIFS.put("lucky_coin", Motif.COIN);
        MOTIFS.put("scouts_map", Motif.MAP);
        MOTIFS.put("wanderers_compass", Motif.COMPASS);
        MOTIFS.put("aegis_eternal", Motif.SHIELD);
        MOTIFS.put("pilgrims_ward", Motif.SHIELD);
        MOTIFS.put("sunlit_aegis", Motif.SUN);
        MOTIFS.put("serpent_coil", Motif.COIL);
        MOTIFS.put("berserkers_mask", Motif.MASK);
        MOTIFS.put("the_infinite_tome", Motif.BOOK);
        MOTIFS.put("chronicle_page", Motif.SCROLL);
        MOTIFS.put("wings_of_the_ascended", Motif.WINGS);
        MOTIFS.put("windrider_cloak", Motif.CLOAK);
        MOTIFS.put("ember_totem", Motif.TOTEM);
        MOTIFS.put("oracle_lens", Motif.EYE);
        MOTIFS.put("smiths_blessing", Motif.ANVIL);
        MOTIFS.put("whetstone_sigil", Motif.BLADE);
        MOTIFS.put("stoneheart_idol", Motif.IDOL);
        MOTIFS.put("warden_seal", Motif.SEAL);
        MOTIFS.put("heart_of_the_mountain", Motif.FLAME);
        MOTIFS.put("hunters_charm", Motif.ARROW);
        // El "segundo aliento" del jefe del acto 3 (Ascension 10). No es un
        // premio y no esta en el catalogo, pero SE VE: entra en la zona de
        // mando del jefe y ahi la mesa la pinta como cualquier otra carta.
        MOTIFS.put("cornered_fury", Motif.FLAME);
        MOTIFS.put("tyrants_last_stand", Motif.FLAME);
    }

    /** La silueta de esa reliquia. Nunca {@code null}: lo que no este, sale como {@link Motif#SIGIL}. */
    public static Motif motifOf(final String relicId) {
        final Motif m = relicId == null ? null : MOTIFS.get(relicId);
        return m == null ? Motif.SIGIL : m;
    }

    /**
     * El emblema, listo para colocar por su centro.
     *
     * @param motif que se dibuja
     * @param size  el lado del cuadrado en el que cabe
     * @param ink   la tinta del metal
     * @param hole  el color de los HUECOS (los ojos de la mascara, la piedra de
     *              un anillo). Tiene que ser el <b>fondo sobre el que se
     *              pinta</b>, no un gris cualquiera: es el mismo fallo que se
     *              vio en los iconos del mapa, donde un hueco claro sobre chapa
     *              oscura no se lee como hueco sino como mancha
     */
    public static Group of(final Motif motif, final double size,
                           final Color ink, final Color hole) {
        final Group g = new Group();
        switch (motif) {
            case CROWN:     crown(g, size, ink, hole); break;
            case HEART:     heart(g, size, ink); break;
            case HOURGLASS: hourglass(g, size, ink, hole); break;
            case GAUNTLET:  gauntlet(g, size, ink, hole); break;
            case GEM:       gem(g, size, ink, hole); break;
            case BANNER:    banner(g, size, ink, hole); break;
            case FANG:      fang(g, size, ink); break;
            case CHALICE:   chalice(g, size, ink, hole); break;
            case RING:      ring(g, size, ink, hole); break;
            case COIN:      coin(g, size, ink, hole); break;
            case MAP:       map(g, size, ink, hole); break;
            case COMPASS:   compass(g, size, ink, hole); break;
            case SHIELD:    shield(g, size, ink, hole); break;
            case SUN:       sun(g, size, ink, hole); break;
            case COIL:      coil(g, size, ink); break;
            case MASK:      mask(g, size, ink, hole); break;
            case BOOK:      book(g, size, ink, hole); break;
            case WINGS:     wings(g, size, ink, hole); break;
            case TOTEM:     totem(g, size, ink, hole); break;
            case EYE:       eye(g, size, ink, hole); break;
            case ANVIL:     anvil(g, size, ink); break;
            case BLADE:     blade(g, size, ink, hole); break;
            case IDOL:      idol(g, size, ink, hole); break;
            case SEAL:      seal(g, size, ink, hole); break;
            case FLAME:     flame(g, size, ink, hole); break;
            case ARROW:     arrow(g, size, ink); break;
            case BOOT:      boot(g, size, ink, hole); break;
            case CLOAK:     cloak(g, size, ink, hole); break;
            case SCROLL:    scroll(g, size, ink, hole); break;
            case FONT:      font(g, size, ink, hole); break;
            case HORN:      horn(g, size, ink, hole); break;
            case SIGIL:
            default:        sigil(g, size, ink, hole); break;
        }
        // Puntas y esquinas redondeadas en todo: una silueta de metal viejo no
        // tiene cantos de vector. Se hace aqui una vez y no figura a figura.
        for (final Node n : g.getChildren()) {
            if (n instanceof Shape) {
                ((Shape) n).setStrokeLineCap(StrokeLineCap.ROUND);
                ((Shape) n).setStrokeLineJoin(StrokeLineJoin.ROUND);
            }
        }
        g.setMouseTransparent(true);
        return g;
    }

    // ------------------------------------------------------------------
    // Ayudas: todo se dibuja con estas tres para que las 27 siluetas se
    // parezcan entre si (mismo grosor relativo, mismo acabado).
    // ------------------------------------------------------------------

    private static Polygon fill(final Group g, final Color c, final double... pts) {
        final Polygon p = new Polygon(pts);
        p.setFill(c);
        g.getChildren().add(p);
        return p;
    }

    private static Line stroke(final Group g, final Color c, final double w,
                               final double x1, final double y1,
                               final double x2, final double y2) {
        final Line l = new Line(x1, y1, x2, y2);
        l.setStroke(c);
        l.setStrokeWidth(w);
        g.getChildren().add(l);
        return l;
    }

    private static Circle dot(final Group g, final Color c, final double x,
                              final double y, final double r) {
        final Circle o = new Circle(x, y, r);
        o.setFill(c);
        g.getChildren().add(o);
        return o;
    }

    // ------------------------------------------------------------------
    // Las siluetas
    // ------------------------------------------------------------------

    /** Corona: base ancha, tres puntas y sus joyas. */
    private static void crown(final Group g, final double s, final Color c, final Color h) {
        fill(g, c,
                -0.42 * s, 0.10 * s, 0.42 * s, 0.10 * s, 0.34 * s, -0.30 * s,
                0.16 * s, -0.06 * s, 0.00, -0.38 * s, -0.16 * s, -0.06 * s,
                -0.34 * s, -0.30 * s);
        fill(g, c, -0.44 * s, 0.12 * s, 0.44 * s, 0.12 * s, 0.44 * s, 0.30 * s, -0.44 * s, 0.30 * s);
        dot(g, h, 0, 0.21 * s, 0.065 * s);
        dot(g, h, -0.24 * s, 0.21 * s, 0.05 * s);
        dot(g, h, 0.24 * s, 0.21 * s, 0.05 * s);
    }

    /** Corazon: dos lobulos y la punta. Con curvas, que un corazon a base de triangulos no se lee. */
    private static void heart(final Group g, final double s, final Color c) {
        final Path p = new Path();
        p.getElements().add(new MoveTo(0, 0.40 * s));
        p.getElements().add(new CubicCurveTo(-0.62 * s, 0.00, -0.44 * s, -0.46 * s, 0, -0.16 * s));
        p.getElements().add(new CubicCurveTo(0.44 * s, -0.46 * s, 0.62 * s, 0.00, 0, 0.40 * s));
        p.setFill(c);
        g.getChildren().add(p);
    }

    /** Reloj de arena: dos conos que se tocan, con sus tapas. */
    private static void hourglass(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.30 * s, -0.30 * s, 0.30 * s, -0.30 * s, 0.03 * s, 0, -0.03 * s, 0);
        fill(g, c, -0.30 * s, 0.30 * s, 0.30 * s, 0.30 * s, 0.03 * s, 0, -0.03 * s, 0);
        fill(g, c, -0.40 * s, -0.42 * s, 0.40 * s, -0.42 * s, 0.40 * s, -0.30 * s, -0.40 * s, -0.30 * s);
        fill(g, c, -0.40 * s, 0.30 * s, 0.40 * s, 0.30 * s, 0.40 * s, 0.42 * s, -0.40 * s, 0.42 * s);
        // La arena que ya cayo: es lo que dice de un vistazo que es un reloj.
        fill(g, h, -0.19 * s, 0.30 * s, 0.19 * s, 0.30 * s, 0.02 * s, 0.06 * s, -0.02 * s, 0.06 * s);
    }

    /**
     * Guantelete: cuatro dedos SUELTOS, pulgar, palma y munyequera.
     *
     * <p>Los dedos van como piezas separadas y no como rayas sobre un bloque:
     * asi lo hacia la primera version y se leia como una caja de madera (se vio
     * en la primera captura). Lo que hace que se reconozca una mano es el
     * contorno dentado de arriba y el pulgar saliendo por un lado.
     */
    private static void gauntlet(final Group g, final double s, final Color c, final Color h) {
        for (int i = 0; i < 4; i++) {
            final double x = -0.28 * s + i * 0.155 * s;
            // El corazon mas largo, como en una mano de verdad: cuatro dedos
            // iguales parecen los dientes de un peine.
            final double top = (i == 1 || i == 2 ? -0.44 : -0.36) * s;
            final Rectangle f = new Rectangle(x, top, 0.13 * s, -top - 0.02 * s);
            f.setArcWidth(0.10 * s);
            f.setArcHeight(0.10 * s);
            f.setFill(c);
            g.getChildren().add(f);
        }
        // El pulgar, saliendo por la izquierda: es lo que dice "mano".
        fill(g, c, -0.30 * s, -0.10 * s, -0.44 * s, 0.02 * s, -0.40 * s, 0.16 * s, -0.26 * s, 0.12 * s);
        final Rectangle palm = new Rectangle(-0.30 * s, -0.06 * s, 0.60 * s, 0.26 * s);
        palm.setArcWidth(0.10 * s);
        palm.setArcHeight(0.10 * s);
        palm.setFill(c);
        g.getChildren().add(palm);
        // Los nudillos: tres remaches, que es lo que lo hace de metal.
        for (int i = 0; i < 3; i++) {
            dot(g, h, -0.15 * s + i * 0.15 * s, 0.04 * s, 0.035 * s);
        }
        fill(g, c, -0.36 * s, 0.22 * s, 0.36 * s, 0.22 * s, 0.30 * s, 0.44 * s, -0.30 * s, 0.44 * s);
        stroke(g, h, 0.035 * s, -0.24 * s, 0.32 * s, 0.24 * s, 0.32 * s);
    }

    /** Gema: octogono tallado con sus facetas. */
    private static void gem(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, 0, -0.42 * s, 0.32 * s, -0.16 * s, 0.32 * s, 0.16 * s, 0, 0.42 * s,
                -0.32 * s, 0.16 * s, -0.32 * s, -0.16 * s);
        stroke(g, h, 0.03 * s, -0.32 * s, -0.16 * s, 0.32 * s, -0.16 * s);
        stroke(g, h, 0.03 * s, -0.16 * s, -0.16 * s, 0, 0.42 * s);
        stroke(g, h, 0.03 * s, 0.16 * s, -0.16 * s, 0, 0.42 * s);
    }

    /** Estandarte: asta, panyo con cola de golondrina y travesanyo. */
    private static void banner(final Group g, final double s, final Color c, final Color h) {
        stroke(g, c, 0.06 * s, -0.26 * s, -0.44 * s, -0.26 * s, 0.44 * s);
        fill(g, c, -0.26 * s, -0.36 * s, 0.36 * s, -0.36 * s, 0.36 * s, 0.14 * s,
                0.05 * s, -0.02 * s, -0.26 * s, 0.14 * s);
        stroke(g, h, 0.035 * s, -0.14 * s, -0.22 * s, 0.24 * s, -0.22 * s);
        stroke(g, h, 0.035 * s, -0.14 * s, -0.08 * s, 0.24 * s, -0.08 * s);
        dot(g, c, -0.26 * s, -0.44 * s, 0.055 * s);
    }

    /** Colmillo: curvo, o parece una punta de flecha. */
    private static void fang(final Group g, final double s, final Color c) {
        final Path p = new Path();
        p.getElements().add(new MoveTo(-0.22 * s, -0.38 * s));
        p.getElements().add(new CubicCurveTo(0.14 * s, -0.34 * s, 0.20 * s, 0.06 * s, 0.02 * s, 0.42 * s));
        p.getElements().add(new CubicCurveTo(-0.06 * s, 0.06 * s, -0.20 * s, -0.10 * s, -0.22 * s, -0.38 * s));
        p.setFill(c);
        g.getChildren().add(p);
    }

    /** Caliz: copa, pie y lo que lleva dentro. */
    private static void chalice(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.30 * s, -0.28 * s, 0.30 * s, -0.28 * s, 0.16 * s, 0.10 * s, -0.16 * s, 0.10 * s);
        stroke(g, c, 0.06 * s, 0, 0.08 * s, 0, 0.30 * s);
        fill(g, c, -0.26 * s, 0.30 * s, 0.26 * s, 0.30 * s, 0.30 * s, 0.40 * s, -0.30 * s, 0.40 * s);
        fill(g, h, -0.25 * s, -0.22 * s, 0.25 * s, -0.22 * s, 0.20 * s, -0.10 * s, -0.20 * s, -0.10 * s);
    }

    /** Anillo: aro y su piedra. */
    private static void ring(final Group g, final double s, final Color c, final Color h) {
        final Circle band = new Circle(0, 0.08 * s, 0.30 * s);
        band.setFill(Color.TRANSPARENT);
        band.setStroke(c);
        band.setStrokeWidth(0.10 * s);
        g.getChildren().add(band);
        fill(g, c, 0, -0.44 * s, 0.15 * s, -0.26 * s, 0, -0.09 * s, -0.15 * s, -0.26 * s);
        dot(g, h, 0, -0.27 * s, 0.045 * s);
    }

    /** Moneda: canto, borde y la marca de dentro. */
    private static void coin(final Group g, final double s, final Color c, final Color h) {
        dot(g, c, 0, 0, 0.40 * s);
        final Circle inner = new Circle(0, 0, 0.30 * s);
        inner.setFill(Color.TRANSPARENT);
        inner.setStroke(h);
        inner.setStrokeWidth(0.035 * s);
        g.getChildren().add(inner);
        // Una estrella de cuatro puntas: se lee acunyada a cualquier tamanyo.
        fill(g, h, 0, -0.20 * s, 0.07 * s, -0.06 * s, 0.20 * s, 0, 0.07 * s, 0.06 * s,
                0, 0.20 * s, -0.07 * s, 0.06 * s, -0.20 * s, 0, -0.07 * s, -0.06 * s);
    }

    /** Mapa: pergamino con la ruta y la cruz. */
    private static void map(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.40 * s, -0.30 * s, -0.02 * s, -0.38 * s, 0.36 * s, -0.28 * s,
                0.40 * s, 0.32 * s, 0.00, 0.40 * s, -0.38 * s, 0.30 * s);
        final Polyline route = new Polyline(
                -0.24 * s, 0.18 * s, -0.10 * s, -0.04 * s, 0.06 * s, 0.06 * s, 0.18 * s, -0.16 * s);
        route.setStroke(h);
        route.setStrokeWidth(0.035 * s);
        route.setFill(Color.TRANSPARENT);
        route.getStrokeDashArray().addAll(0.06 * s, 0.05 * s);
        g.getChildren().add(route);
        stroke(g, h, 0.05 * s, 0.12 * s, -0.22 * s, 0.24 * s, -0.10 * s);
        stroke(g, h, 0.05 * s, 0.24 * s, -0.22 * s, 0.12 * s, -0.10 * s);
    }

    /** Brujula: caja redonda y la aguja de dos colores. */
    private static void compass(final Group g, final double s, final Color c, final Color h) {
        final Circle body = new Circle(0, 0, 0.40 * s);
        body.setFill(Color.TRANSPARENT);
        body.setStroke(c);
        body.setStrokeWidth(0.08 * s);
        g.getChildren().add(body);
        fill(g, c, 0, -0.28 * s, 0.13 * s, 0, 0, 0.06 * s, -0.13 * s, 0);
        fill(g, h, 0, 0.28 * s, 0.13 * s, 0, 0, 0.06 * s, -0.13 * s, 0);
        dot(g, h, 0, -0.44 * s, 0.05 * s);
    }

    /** Escudo: contorno de punta y su nervadura. */
    private static void shield(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.34 * s, -0.36 * s, 0.34 * s, -0.36 * s, 0.34 * s, 0.06 * s,
                0, 0.42 * s, -0.34 * s, 0.06 * s);
        stroke(g, h, 0.04 * s, 0, -0.26 * s, 0, 0.26 * s);
        stroke(g, h, 0.04 * s, -0.20 * s, -0.16 * s, 0.20 * s, -0.16 * s);
    }

    /** Sol: disco y ocho rayos. */
    private static void sun(final Group g, final double s, final Color c, final Color h) {
        for (int i = 0; i < 8; i++) {
            final double a = i * Math.PI / 4;
            stroke(g, c, 0.055 * s, Math.cos(a) * 0.26 * s, Math.sin(a) * 0.26 * s,
                    Math.cos(a) * 0.44 * s, Math.sin(a) * 0.44 * s);
        }
        dot(g, c, 0, 0, 0.22 * s);
        dot(g, h, 0, 0, 0.09 * s);
    }

    /** Serpiente enroscada: tres vueltas y la cabeza. */
    private static void coil(final Group g, final double s, final Color c) {
        for (int i = 0; i < 3; i++) {
            final double r = 0.38 * s - i * 0.11 * s;
            final Ellipse e = new Ellipse(0, 0.06 * s - i * 0.03 * s, r, r * 0.72);
            e.setFill(Color.TRANSPARENT);
            e.setStroke(c);
            e.setStrokeWidth(0.055 * s);
            g.getChildren().add(e);
        }
        fill(g, c, 0.24 * s, -0.42 * s, 0.46 * s, -0.30 * s, 0.24 * s, -0.18 * s);
    }

    /** Mascara: cara, ojos huecos y los colmillos. */
    private static void mask(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.32 * s, -0.36 * s, 0.32 * s, -0.36 * s, 0.26 * s, 0.14 * s,
                0, 0.42 * s, -0.26 * s, 0.14 * s);
        fill(g, h, -0.22 * s, -0.20 * s, -0.06 * s, -0.14 * s, -0.22 * s, -0.04 * s);
        fill(g, h, 0.22 * s, -0.20 * s, 0.06 * s, -0.14 * s, 0.22 * s, -0.04 * s);
        fill(g, h, -0.13 * s, 0.10 * s, 0.13 * s, 0.10 * s, 0.07 * s, 0.24 * s, -0.07 * s, 0.24 * s);
    }

    /** Libro abierto: dos paginas, lomo y renglones. */
    private static void book(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.42 * s, -0.26 * s, -0.02 * s, -0.16 * s, -0.02 * s, 0.34 * s, -0.42 * s, 0.24 * s);
        fill(g, c, 0.42 * s, -0.26 * s, 0.02 * s, -0.16 * s, 0.02 * s, 0.34 * s, 0.42 * s, 0.24 * s);
        for (int i = 0; i < 3; i++) {
            final double y = -0.06 * s + i * 0.11 * s;
            stroke(g, h, 0.03 * s, -0.34 * s, y, -0.10 * s, y);
            stroke(g, h, 0.03 * s, 0.10 * s, y, 0.34 * s, y);
        }
    }

    /** Alas: dos plumas abiertas y la joya del centro. */
    private static void wings(final Group g, final double s, final Color c, final Color h) {
        for (final int dir : new int[]{1, -1}) {
            fill(g, c,
                    0.05 * s * dir, -0.22 * s, 0.46 * s * dir, -0.30 * s,
                    0.40 * s * dir, -0.04 * s, 0.30 * s * dir, 0.14 * s,
                    0.10 * s * dir, 0.10 * s);
            stroke(g, h, 0.03 * s, 0.14 * s * dir, -0.16 * s, 0.36 * s * dir, -0.18 * s);
            stroke(g, h, 0.03 * s, 0.16 * s * dir, -0.04 * s, 0.34 * s * dir, -0.04 * s);
        }
        fill(g, c, 0, -0.40 * s, 0.11 * s, -0.24 * s, 0, -0.06 * s, -0.11 * s, -0.24 * s);
    }

    /** Totem: tres cuerpos apilados y la brasa de arriba. */
    private static void totem(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.24 * s, -0.12 * s, 0.24 * s, -0.12 * s, 0.20 * s, 0.08 * s, -0.20 * s, 0.08 * s);
        fill(g, c, -0.28 * s, 0.10 * s, 0.28 * s, 0.10 * s, 0.24 * s, 0.30 * s, -0.24 * s, 0.30 * s);
        fill(g, c, -0.34 * s, 0.32 * s, 0.34 * s, 0.32 * s, 0.34 * s, 0.44 * s, -0.34 * s, 0.44 * s);
        dot(g, h, -0.10 * s, -0.02 * s, 0.04 * s);
        dot(g, h, 0.10 * s, -0.02 * s, 0.04 * s);
        stroke(g, h, 0.035 * s, -0.14 * s, 0.20 * s, 0.14 * s, 0.20 * s);
        final Path ember = new Path();
        ember.getElements().add(new MoveTo(0, -0.46 * s));
        ember.getElements().add(new CubicCurveTo(0.19 * s, -0.32 * s, 0.18 * s, -0.20 * s, 0, -0.14 * s));
        ember.getElements().add(new CubicCurveTo(-0.18 * s, -0.20 * s, -0.19 * s, -0.32 * s, 0, -0.46 * s));
        ember.setFill(c);
        g.getChildren().add(ember);
    }

    /** Ojo: lente, iris y pupila. */
    private static void eye(final Group g, final double s, final Color c, final Color h) {
        final Path p = new Path();
        p.getElements().add(new MoveTo(-0.46 * s, 0));
        p.getElements().add(new CubicCurveTo(-0.20 * s, -0.34 * s, 0.20 * s, -0.34 * s, 0.46 * s, 0));
        p.getElements().add(new CubicCurveTo(0.20 * s, 0.34 * s, -0.20 * s, 0.34 * s, -0.46 * s, 0));
        p.setFill(c);
        g.getChildren().add(p);
        dot(g, h, 0, 0, 0.17 * s);
        dot(g, c, 0, 0, 0.075 * s);
    }

    /** Yunque: el cuerno, el cuerpo y la peana. */
    private static void anvil(final Group g, final double s, final Color c) {
        fill(g, c, -0.44 * s, -0.24 * s, 0.30 * s, -0.24 * s, 0.44 * s, -0.12 * s,
                0.28 * s, -0.06 * s, -0.30 * s, -0.06 * s);
        fill(g, c, -0.16 * s, -0.06 * s, 0.16 * s, -0.06 * s, 0.10 * s, 0.22 * s, -0.10 * s, 0.22 * s);
        fill(g, c, -0.34 * s, 0.22 * s, 0.34 * s, 0.22 * s, 0.34 * s, 0.40 * s, -0.34 * s, 0.40 * s);
    }

    /** Hoja sobre la piedra de afilar, con su chispa. */
    private static void blade(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.34 * s, 0.16 * s, 0.34 * s, 0.16 * s, 0.40 * s, 0.36 * s, -0.40 * s, 0.36 * s);
        fill(g, c, 0.02 * s, -0.46 * s, 0.13 * s, -0.22 * s, 0.13 * s, 0.06 * s,
                -0.07 * s, 0.06 * s, -0.07 * s, -0.24 * s);
        fill(g, c, -0.16 * s, 0.06 * s, 0.20 * s, 0.06 * s, 0.20 * s, 0.14 * s, -0.16 * s, 0.14 * s);
        stroke(g, h, 0.03 * s, -0.28 * s, -0.16 * s, -0.16 * s, -0.28 * s);
        stroke(g, h, 0.03 * s, -0.30 * s, -0.02 * s, -0.18 * s, -0.06 * s);
    }

    /** Idolo de piedra: figura achaparrada sobre su peana. */
    private static void idol(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.20 * s, -0.42 * s, 0.20 * s, -0.42 * s, 0.24 * s, -0.16 * s, -0.24 * s, -0.16 * s);
        fill(g, c, -0.30 * s, -0.14 * s, 0.30 * s, -0.14 * s, 0.34 * s, 0.24 * s, -0.34 * s, 0.24 * s);
        fill(g, c, -0.40 * s, 0.26 * s, 0.40 * s, 0.26 * s, 0.40 * s, 0.42 * s, -0.40 * s, 0.42 * s);
        dot(g, h, -0.09 * s, -0.30 * s, 0.045 * s);
        dot(g, h, 0.09 * s, -0.30 * s, 0.045 * s);
        stroke(g, h, 0.035 * s, -0.18 * s, 0.04 * s, 0.18 * s, 0.04 * s);
    }

    /** Sello de lacre: disco festoneado, cintas y la marca. */
    private static void seal(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.22 * s, 0.12 * s, -0.06 * s, 0.12 * s, -0.10 * s, 0.44 * s, -0.28 * s, 0.36 * s);
        fill(g, c, 0.22 * s, 0.12 * s, 0.06 * s, 0.12 * s, 0.10 * s, 0.44 * s, 0.28 * s, 0.36 * s);
        for (int i = 0; i < 10; i++) {
            final double a = i * Math.PI / 5;
            dot(g, c, Math.cos(a) * 0.30 * s, Math.sin(a) * 0.30 * s - 0.08 * s, 0.08 * s);
        }
        dot(g, c, 0, -0.08 * s, 0.28 * s);
        stroke(g, h, 0.04 * s, -0.12 * s, -0.18 * s, 0.12 * s, -0.18 * s);
        stroke(g, h, 0.04 * s, 0.12 * s, -0.18 * s, -0.12 * s, 0.02 * s);
        stroke(g, h, 0.04 * s, -0.12 * s, 0.02 * s, 0.12 * s, 0.02 * s);
    }

    /** Llama: la lengua grande y el corazon dentro. */
    private static void flame(final Group g, final double s, final Color c, final Color h) {
        final Path p = new Path();
        p.getElements().add(new MoveTo(0, -0.46 * s));
        p.getElements().add(new CubicCurveTo(0.34 * s, -0.12 * s, 0.36 * s, 0.20 * s, 0, 0.44 * s));
        p.getElements().add(new CubicCurveTo(-0.36 * s, 0.20 * s, -0.34 * s, -0.12 * s, 0, -0.46 * s));
        p.setFill(c);
        g.getChildren().add(p);
        final Path core = new Path();
        core.getElements().add(new MoveTo(0, -0.12 * s));
        core.getElements().add(new CubicCurveTo(0.18 * s, 0.06 * s, 0.16 * s, 0.24 * s, 0, 0.34 * s));
        core.getElements().add(new CubicCurveTo(-0.16 * s, 0.24 * s, -0.18 * s, 0.06 * s, 0, -0.12 * s));
        core.setFill(h);
        g.getChildren().add(core);
    }

    /**
     * Flecha: punta ancha, astil y plumas.
     *
     * <p>La punta va GRANDE. La primera version la tenia del ancho del astil y
     * el conjunto se leia como una antena, no como una flecha.
     */
    private static void arrow(final Group g, final double s, final Color c) {
        fill(g, c, 0, -0.46 * s, 0.32 * s, -0.06 * s, 0.13 * s, -0.06 * s,
                0.13 * s, 0.02 * s, -0.13 * s, 0.02 * s, -0.13 * s, -0.06 * s,
                -0.32 * s, -0.06 * s);
        stroke(g, c, 0.07 * s, 0, -0.02 * s, 0, 0.42 * s);
        fill(g, c, 0, 0.14 * s, -0.26 * s, 0.36 * s, -0.24 * s, 0.44 * s, 0, 0.26 * s);
        fill(g, c, 0, 0.14 * s, 0.26 * s, 0.36 * s, 0.24 * s, 0.44 * s, 0, 0.26 * s);
    }

    /**
     * Bota con alita: <i>swiftfoot</i>.
     *
     * <p>Llevaba el mismo anillo que <i>Copper Ring</i>, y las dos son comunes
     * — o sea mismo dibujo Y mismo color, que en la barra del mapa es
     * indistinguible. Se vio en la maqueta de las 35 juntas.
     */
    private static void boot(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.10 * s, -0.38 * s, 0.14 * s, -0.38 * s, 0.14 * s, 0.08 * s,
                0.42 * s, 0.14 * s, 0.44 * s, 0.32 * s, -0.10 * s, 0.32 * s);
        // La suela, en hueco: es lo que separa el pie de la canya y hace que
        // el conjunto deje de leerse como una simple L.
        stroke(g, h, 0.04 * s, -0.08 * s, 0.24 * s, 0.40 * s, 0.24 * s);
        stroke(g, h, 0.035 * s, -0.08 * s, 0.02 * s, 0.12 * s, 0.02 * s);
        // Y la velocidad: tres rayas detras del tobillo. El ala pegada al
        // tobillo que habia antes se fundia con la bota en una sola mancha
        // (se vio en la maqueta de las 35). Separadas no se pueden fundir.
        for (int i = 0; i < 3; i++) {
            final double y = -0.24 * s + i * 0.13 * s;
            stroke(g, c, 0.055 * s, -0.20 * s, y, (-0.48 + i * 0.06) * s, y);
        }
    }

    /** Capa con capucha: cuerpo acampanado, capucha y el broche. */
    private static void cloak(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.16 * s, -0.20 * s, 0.16 * s, -0.20 * s, 0.42 * s, 0.40 * s,
                0.16 * s, 0.30 * s, -0.16 * s, 0.30 * s, -0.42 * s, 0.40 * s);
        final Circle hood = new Circle(0, -0.24 * s, 0.20 * s);
        hood.setFill(c);
        g.getChildren().add(hood);
        final Circle inner = new Circle(0, -0.22 * s, 0.11 * s);
        inner.setFill(h);
        g.getChildren().add(inner);
        dot(g, h, 0, -0.02 * s, 0.055 * s);
    }

    /** Pergamino enrollado: los dos rollos y el renglon. */
    private static void scroll(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.34 * s, -0.24 * s, 0.34 * s, -0.24 * s, 0.34 * s, 0.24 * s, -0.34 * s, 0.24 * s);
        for (final int dir : new int[]{1, -1}) {
            final Ellipse roll = new Ellipse(0.34 * s * dir, 0, 0.09 * s, 0.28 * s);
            roll.setFill(c);
            g.getChildren().add(roll);
            final Ellipse eye2 = new Ellipse(0.34 * s * dir, 0, 0.035 * s, 0.12 * s);
            eye2.setFill(h);
            g.getChildren().add(eye2);
        }
        for (int i = 0; i < 3; i++) {
            stroke(g, h, 0.03 * s, -0.24 * s, -0.10 * s + i * 0.10 * s,
                    0.24 * s, -0.10 * s + i * 0.10 * s);
        }
    }

    /** Fuente de almas: pila sobre su pie y tres animas subiendo. */
    private static void font(final Group g, final double s, final Color c, final Color h) {
        fill(g, c, -0.40 * s, 0.02 * s, 0.40 * s, 0.02 * s, 0.28 * s, 0.22 * s, -0.28 * s, 0.22 * s);
        fill(g, h, -0.34 * s, 0.05 * s, 0.34 * s, 0.05 * s, 0.28 * s, 0.13 * s, -0.28 * s, 0.13 * s);
        stroke(g, c, 0.07 * s, 0, 0.20 * s, 0, 0.34 * s);
        fill(g, c, -0.28 * s, 0.34 * s, 0.28 * s, 0.34 * s, 0.32 * s, 0.44 * s, -0.32 * s, 0.44 * s);
        // Las tres animas, de tamanyos distintos: iguales parecerian burbujas.
        dot(g, c, 0, -0.20 * s, 0.11 * s);
        dot(g, c, -0.24 * s, -0.30 * s, 0.075 * s);
        dot(g, c, 0.22 * s, -0.36 * s, 0.055 * s);
    }

    /**
     * Cuerno de guerra: la curva que se ensancha, la campana y sus aros.
     *
     * <p>La primera version era una sola curva cerrada y se leia como una
     * hoja. Lo que hace que se reconozca un cuerno son dos cosas: que
     * <b>se ensanche</b> de la boquilla a la campana, y que la campana tenga
     * boca — el ovalo hueco del final.
     */
    private static void horn(final Group g, final double s, final Color c, final Color h) {
        final Path p = new Path();
        p.getElements().add(new MoveTo(-0.40 * s, 0.30 * s));
        p.getElements().add(new CubicCurveTo(-0.34 * s, -0.14 * s, -0.02 * s, -0.36 * s,
                0.30 * s, -0.32 * s));
        p.getElements().add(new CubicCurveTo(0.34 * s, -0.12 * s, 0.36 * s, 0.02 * s,
                0.34 * s, 0.14 * s));
        p.getElements().add(new CubicCurveTo(0.02 * s, 0.10 * s, -0.16 * s, 0.26 * s,
                -0.26 * s, 0.42 * s));
        p.setFill(c);
        g.getChildren().add(p);
        // La boca de la campana.
        final Ellipse bell = new Ellipse(0.32 * s, -0.09 * s, 0.10 * s, 0.24 * s);
        bell.setFill(c);
        g.getChildren().add(bell);
        final Ellipse mouth = new Ellipse(0.33 * s, -0.09 * s, 0.05 * s, 0.16 * s);
        mouth.setFill(h);
        g.getChildren().add(mouth);
        // Los dos aros de refuerzo.
        stroke(g, h, 0.035 * s, -0.20 * s, 0.06 * s, -0.10 * s, -0.20 * s);
        stroke(g, h, 0.035 * s, 0.02 * s, 0.02 * s, 0.06 * s, -0.26 * s);
        // La boquilla.
        fill(g, c, -0.46 * s, 0.24 * s, -0.30 * s, 0.20 * s, -0.22 * s, 0.42 * s, -0.40 * s, 0.44 * s);
    }

    /**
     * El generico: un sigilo dentro de un aro.
     *
     * <p>Es el que le toca a una reliquia nueva que todavia no tiene silueta
     * propia. Digno a proposito: tiene que poder salir en el juego sin que
     * parezca que falta algo.
     */
    private static void sigil(final Group g, final double s, final Color c, final Color h) {
        final Circle o = new Circle(0, 0, 0.40 * s);
        o.setFill(Color.TRANSPARENT);
        o.setStroke(c);
        o.setStrokeWidth(0.07 * s);
        g.getChildren().add(o);
        fill(g, c, 0, -0.26 * s, 0.24 * s, 0.20 * s, -0.24 * s, 0.20 * s);
        dot(g, h, 0, 0.02 * s, 0.07 * s);
        final Rectangle spark = new Rectangle(-0.03 * s, -0.46 * s, 0.06 * s, 0.12 * s);
        spark.setFill(c);
        g.getChildren().add(spark);
    }
}
