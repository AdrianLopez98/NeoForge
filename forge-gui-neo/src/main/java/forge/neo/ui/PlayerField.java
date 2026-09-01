package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.neo.card.CardNode;
import javafx.scene.layout.Pane;

/**
 * El campo de batalla de un jugador, con la disposicion de Arena.
 *
 * <p>Dos filas, pero la de atras partida en dos columnas:
 *
 * <pre>
 *   +-------------------------------------------------------+
 *   |       tierras       |  artefactos / encantamientos / PW |   fila de atras
 *   +-------------------------------------------------------+
 *   |              c r i a t u r a s                        |   fila de delante
 *   +-------------------------------------------------------+
 *                    ^ linea de combate
 * </pre>
 *
 * <p>La clave es que los permanentes que no son criaturas comparten FILA con
 * las tierras pero no se mezclan con ellas: van en su propio grupo, separados
 * por un hueco. Asi se distinguen de un vistazo sin gastar una tercera fila,
 * que en pantallas pequenas dejaria las cartas diminutas.
 *
 * <p>Las criaturas van siempre pegadas a la linea de combate. Para el oponente
 * el orden se invierte, de modo que sus criaturas quedan enfrentadas a las
 * tuyas.
 *
 * <p>Un grupo vacio no ocupa espacio: si no hay artefactos, las tierras usan
 * todo el ancho; si no hay nada detras, las criaturas usan todo el alto.
 *
 * <p>En el borde exterior van el cementerio y el exilio, siempre visibles con
 * su contador.
 */
public class PlayerField extends Pane {

    private static final double ROW_GAP = 5;

    private final BattlefieldPane creatureRow;
    private final BattlefieldPane permanentRow;
    private final BattlefieldPane landRow;
    private final ZonePile graveyard;
    private final ZonePile exile;
    private final boolean opponent;
    private final double baseCardWidth;

    public PlayerField(final double baseCardWidth, final boolean opponent) {
        this.opponent = opponent;
        this.baseCardWidth = baseCardWidth;
        this.creatureRow = new BattlefieldPane(baseCardWidth);
        this.permanentRow = new BattlefieldPane(baseCardWidth);
        this.landRow = new BattlefieldPane(baseCardWidth);
        this.graveyard = new ZonePile(NeoText.get("zone.graveyard"), baseCardWidth * 0.62);
        this.exile = new ZonePile(NeoText.get("zone.exile"), baseCardWidth * 0.62);

        setPickOnBounds(false);
        // Las criaturas avanzan hacia la linea de combate al atacar o bloquear:
        // el campo propio empuja hacia arriba y el del rival hacia abajo, de
        // modo que las dos lineas se acercan como en Arena.
        creatureRow.setCombatDirection(opponent ? 1 : -1);
        // Las criaturas se pintan las ULTIMAS, o sea por encima de la fila de
        // atras. Importa por dos cosas que se salen de su fila: el paso
        // adelante del atacante, y lo que lleven enganchado, que asoma por
        // debajo. Con la fila de atras pintada despues, un equipo quedaba
        // escondido detras de una tierra: visible en la maqueta y en el campo
        // del rival, invisible justo en el tuyo.
        getChildren().addAll(permanentRow, landRow, creatureRow, graveyard, exile);
    }

    public void setOnCardHover(final Consumer<CardNode> handler) {
        creatureRow.setOnCardHover(handler);
        permanentRow.setOnCardHover(handler);
        landRow.setOnCardHover(handler);
    }

    public void setOnCardClick(final Consumer<CardNode> handler) {
        creatureRow.setOnCardClick(handler);
        permanentRow.setOnCardClick(handler);
        landRow.setOnCardClick(handler);
    }

    /**
     * Reparte los permanentes entre las zonas.
     *
     * <p>Lo enganchado (equipamientos, auras, fortificaciones) NO ocupa hueco
     * propio: viaja con la carta a la que esta pegado, como en Arena.
     *
     * @param cards      los permanentes de ESTE jugador
     * @param everywhere todos los permanentes de la mesa, de los dos lados
     */
    public void setCards(final List<CardView> cards, final List<CardView> everywhere) {
        // A que esta enganchado cada cosa. Se pregunta AL REVES — a cada carta
        // de esta mesa, que lleva encima — y eso es lo que arregla el caso que
        // fallaba: un aura del RIVAL sobre una criatura MIA.
        //
        // Antes se recorrian solo las cartas de este jugador buscando su
        // anfitrion, asi que un aura enemiga nunca encontraba a quien encantaba
        // y se quedaba suelta en la fila de encantamientos del rival, como si
        // no hubiera encantado nada. Y eso es justo la mitad de las auras que
        // importan: las de quitarte una criatura.
        //
        // El motor publica las dos direcciones. {@code getAttachedCards()} da
        // todo lo enganchado a una carta, lo controle quien lo controle.
        final Map<Integer, List<CardView>> attachedTo = new HashMap<>();
        for (final CardView cv : cards) {
            if (cv == null) {
                continue;
            }
            final List<CardView> on = cv.getAttachedCards();
            if (on != null && !on.isEmpty()) {
                attachedTo.put(cv.getId(), new ArrayList<>(on));
            }
        }

        // Y lo que se pinta enganchado en algun sitio de la mesa no ocupa
        // ademas su propio hueco aqui. Se mira contra la mesa ENTERA: si el
        // anfitrion no esta en ninguna parte, la carta se queda con su sitio en
        // vez de desaparecer.
        final Set<Integer> hosts = new HashSet<>();
        final List<CardView> board = everywhere == null ? cards : everywhere;
        for (final CardView cv : board) {
            if (cv != null) {
                hosts.add(cv.getId());
            }
        }
        final Set<Integer> isAttachment = new HashSet<>();
        for (final CardView cv : cards) {
            if (cv == null) {
                continue;
            }
            final CardView host = cv.getAttachedTo();
            if (host != null && hosts.contains(host.getId())) {
                isAttachment.add(cv.getId());
            }
        }

        final List<CardView> creatures = new ArrayList<>();
        final List<CardView> permanents = new ArrayList<>();
        final List<CardView> lands = new ArrayList<>();

        for (final CardView cv : cards) {
            final CardStateView st = cv == null ? null : cv.getCurrentState();
            if (st == null || isAttachment.contains(cv.getId())) {
                continue;
            }
            // Una criatura manda sobre el resto de tipos: un artefacto-criatura
            // o un vehiculo tripulado combaten, asi que van con las criaturas.
            if (st.isCreature() || st.isBattle()) {
                creatures.add(cv);
            } else if (st.isLand()) {
                lands.add(cv);
            } else {
                permanents.add(cv);
            }
        }

        creatureRow.setCards(creatures, attachedTo);
        permanentRow.setCards(permanents, attachedTo);
        landRow.setCards(lands, attachedTo);
        requestLayout();
    }

    /** Sin el resto de la mesa: para las maquetas, que solo tienen un lado. */
    public void setCards(final List<CardView> cards) {
        setCards(cards, null);
    }

    /** La pila del cementerio de este campo. */
    public ZonePile getGraveyardPile() {
        return graveyard;
    }

    /** La pila del exilio de este campo. */
    public ZonePile getExilePile() {
        return exile;
    }

    /**
     * La funda de este jugador, en las dos pilas a la vez.
     *
     * <p>Cementerio y exilio son "un monton de cartas boca abajo" — {@link
     * ZonePile} —, que hasta ahora se pintaba con un rectangulo liso porque
     * nadie le pasaba nada mejor. Es justo el sitio donde una funda elegida en
     * Personalizar por fin se ve en la mesa: cada jugador con la suya.
     */
    public void setSleeveImage(final javafx.scene.image.Image image) {
        graveyard.setSleeveImage(image);
        exile.setSleeveImage(image);
    }

    public void setZoneCounts(final int graveyardSize, final int exileSize) {
        setZoneCounts(graveyardSize, exileSize, false, false);
    }

    /**
     * @param gravePlayable hay algo en el cementerio que se puede lanzar ya
     * @param exilePlayable idem en el exilio
     */
    public void setZoneCounts(final int graveyardSize, final int exileSize,
                              final boolean gravePlayable, final boolean exilePlayable) {
        graveyard.setCount(graveyardSize);
        exile.setCount(exileSize);
        graveyard.setHasPlayable(gravePlayable);
        exile.setHasPlayable(exilePlayable);
        // Una pila vacia no responde: no hay nada que ensenyar y un cursor de
        // mano sobre algo que no hace nada confunde mas que ayuda.
        wireZone(graveyard, forge.game.zone.ZoneType.Graveyard, graveyardSize);
        wireZone(exile, forge.game.zone.ZoneType.Exile, exileSize);
    }

    private void wireZone(final ZonePile pile, final forge.game.zone.ZoneType zone,
                          final int count) {
        pile.setOnClick(count > 0 && onZone != null && owner != null
                ? () -> onZone.accept(owner, zone) : null);
    }

    /**
     * De quien es este campo y que hacer al clicar sus pilas.
     *
     * <p>El campo del rival se REUTILIZA para los tres oponentes al cambiar de
     * pestanya, asi que el duenyo se actualiza en cada vuelco en vez de fijarse
     * al construirlo.
     */
    public void setZoneOwner(final forge.game.player.PlayerView player) {
        this.owner = player;
    }

    public void setOnZoneClicked(
            final java.util.function.BiConsumer<forge.game.player.PlayerView,
                    forge.game.zone.ZoneType> handler) {
        this.onZone = handler;
    }

    private forge.game.player.PlayerView owner;
    private java.util.function.BiConsumer<forge.game.player.PlayerView,
            forge.game.zone.ZoneType> onZone;

    /** Ver {@link BattlefieldPane#skipRemovalAnimation()}. */
    public void skipRemovalAnimation() {
        creatureRow.skipRemovalAnimation();
        permanentRow.skipRemovalAnimation();
        landRow.skipRemovalAnimation();
    }

    /** Ver {@link BattlefieldPane#setGroupingEnabled(boolean)}. */
    public void setGroupingEnabled(final boolean on) {
        creatureRow.setGroupingEnabled(on);
        permanentRow.setGroupingEnabled(on);
        landRow.setGroupingEnabled(on);
    }

    public void refreshAll() {
        creatureRow.refreshAll();
        permanentRow.refreshAll();
        landRow.refreshAll();
    }

    public List<CardNode> nodes() {
        final List<CardNode> out = new ArrayList<>(creatureRow.nodes());
        out.addAll(permanentRow.nodes());
        out.addAll(landRow.nodes());
        return out;
    }

    // ---------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();

        final double stripW = graveyard.getCardWidth() * 2 + 18;
        final double rowsW = Math.max(120, w - stripW - 12);

        final int nCreatures = creatureRow.slotCount();
        final int nPermanents = permanentRow.slotCount();
        final int nLands = landRow.slotCount();

        // --- ancho de los dos grupos de la fila de atras ---
        // Se reparte a proporcion de cuantas cartas tiene cada uno, con un
        // hueco en medio que es lo que hace que se lean como grupos distintos.
        final double groupGap = baseCardWidth * 0.55;
        double permW = 0;
        double landW = 0;
        if (nPermanents > 0 && nLands > 0) {
            final double avail = Math.max(80, rowsW - groupGap);
            permW = avail * nPermanents / (double) (nPermanents + nLands);
            landW = avail - permW;
            // Suelo de un ancho de carta por grupo.
            //
            // El reparto a proporcion de cuantas cartas tiene cada grupo se
            // rompe justo en el caso mas normal: un encantamiento suelto al
            // lado de diez tierras se lleva 1/11 de la fila, o sea menos de lo
            // que mide UNA carta. Entonces o sale cortado o encoge a todas las
            // demas para caber, que es peor todavia. Fallo real jugando.
            final double minGroup = Math.min(avail / 2, baseCardWidth + 12);
            if (permW < minGroup) {
                permW = minGroup;
                landW = avail - permW;
            } else if (landW < minGroup) {
                landW = minGroup;
                permW = avail - landW;
            }
        } else if (nPermanents > 0) {
            permW = rowsW;
        } else if (nLands > 0) {
            landW = rowsW;
        }

        final boolean hasBack = nPermanents > 0 || nLands > 0;
        final int visible = (nCreatures > 0 ? 1 : 0) + (hasBack ? 1 : 0);
        if (visible == 0) {
            creatureRow.resizeRelocate(0, 0, rowsW, 0);
            permanentRow.resizeRelocate(0, 0, 0, 0);
            landRow.resizeRelocate(0, 0, 0, 0);
            layoutPiles(w, h, stripW);
            return;
        }

        // --- un unico tamano de carta para toda la mesa del jugador ---
        double cardW = Double.MAX_VALUE;
        if (nCreatures > 0) {
            cardW = Math.min(cardW, creatureRow.desiredCardWidth(rowsW));
        }
        if (nPermanents > 0) {
            cardW = Math.min(cardW, permanentRow.desiredCardWidth(permW));
        }
        if (nLands > 0) {
            cardW = Math.min(cardW, landRow.desiredCardWidth(landW));
        }

        final double gaps = ROW_GAP * (visible - 1);
        if (cardW * CardNode.ASPECT * visible + gaps > h) {
            cardW = Math.max(24, (h - gaps) / visible / CardNode.ASPECT);
        }

        final double cardH = cardW * CardNode.ASPECT;
        final double usedH = cardH * visible + gaps;
        double y = Math.max(0, (h - usedH) / 2);

        // El oponente lleva la fila de atras arriba; tu, abajo. En ambos casos
        // las criaturas quedan pegadas a la linea de combate.
        final double backY;
        final double creatureY;
        if (opponent) {
            backY = y;
            creatureY = hasBack ? y + cardH + ROW_GAP : y;
        } else {
            creatureY = y;
            backY = nCreatures > 0 ? y + cardH + ROW_GAP : y;
        }

        if (nCreatures > 0) {
            creatureRow.setMaxCardWidth(cardW);
            // Hueco para el paso adelante del atacante, solo hacia el combate.
            final double step = cardH * 0.30;
            creatureRow.setOverflow(opponent ? 0 : step, opponent ? step : 0);
            creatureRow.resizeRelocate(0, creatureY, rowsW, cardH);
        } else {
            creatureRow.resizeRelocate(0, creatureY, rowsW, 0);
        }

        // Tierras a la izquierda, resto de permanentes a la derecha (Arena).
        if (nLands > 0) {
            landRow.setMaxCardWidth(cardW);
            landRow.resizeRelocate(0, backY, landW, cardH);
        } else {
            landRow.resizeRelocate(0, backY, 0, 0);
        }

        if (nPermanents > 0) {
            permanentRow.setMaxCardWidth(cardW);
            final double permX = nLands > 0 ? landW + groupGap : 0;
            permanentRow.resizeRelocate(permX, backY, permW, cardH);
        } else {
            permanentRow.resizeRelocate(0, backY, 0, 0);
        }

        layoutPiles(w, h, stripW);
    }

    private void layoutPiles(final double w, final double h, final double stripW) {
        final double pileW = graveyard.getCardWidth();
        final double pileH = graveyard.prefHeight(pileW);
        final double x = w - stripW + 6;
        final double y = Math.max(0, (h - pileH) / 2);
        graveyard.resizeRelocate(x, y, pileW + 6, pileH);
        exile.resizeRelocate(x + pileW + 12, y, pileW + 6, pileH);
    }

    public double getBaseCardWidth() {
        return baseCardWidth;
    }
}
