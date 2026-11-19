package forge.neo.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.neo.NeoText;
import forge.neo.look.RivalSetup;

/**
 * <b>Quien se sienta en cada asiento de un draft</b> (Discord, 18-11-2026:
 * <i>"knowing Ajani was to my left and Chandra was to my right during draft
 * and then seeing what they came up with as a final deck ... Even better with
 * customized opponents"</i>).
 *
 * <p>Los siete rivales del draft ya jugaban con lo que draftearon a tu lado,
 * pero sin cara: "Rival 3 de 7", y en la partida se sentaban con tus rivales
 * de Personalizar por su silla en la MESA, no por su asiento en el draft. Esto
 * les da nombre al empezar el draft, lo guarda con el evento y es el mismo en
 * los picks, en el marcador y en la partida.
 *
 * <h2>Los asientos, del motor</h2>
 *
 * <p>Tu eres el 0 y el rival {@code i} (desde 0, el de {@code
 * getComputerDecks()} y el del evento) es el asiento {@code i + 1}. El motor
 * pasa al asiento siguiente en los sobres impares y al anterior en los pares
 * ({@code BoosterDraft.passPacks}): en el sobre 1 le pasas al rival 1 y
 * recibes del ultimo. Lo comprueba {@code DraftCheck.checkSeats} mirando a
 * quien llega de verdad lo que dejas.
 *
 * <h2>Tus rivales, a tu lado</h2>
 *
 * <p>Los de Personalizar ({@link RivalSetup}, hasta {@link RivalSetup#SEATS})
 * se sientan <b>lo mas cerca posible</b>: el 1 a tu izquierda (a quien pasas
 * en el primer sobre), el 2 a tu derecha, el 3 al lado del 1... Son los
 * vecinos los que te cortan o te dejan colores, y es ahi donde importa
 * reconocerlos. Los demas asientos, un nombre del generador de Forge, SIN
 * guardarlo en tus rivales. Ninguno se repite ni se llama como tu.
 *
 * <p>Sin JavaFX: lo usan el escritorio y Android. La cara de un asiento sin
 * rival guardado va vacia, y la pone cada interfaz ({@link #faceIndex}).
 */
public final class DraftSeats {

    private DraftSeats() {
    }

    /** Un asiento: su nombre, su cara (id de {@link RivalSetup#avatar}, o "") y de que rival guardado sale (-1 si de ninguno). */
    public static final class Seat {
        public final String name;
        public final String face;
        public final int savedRival;

        public Seat(final String name, final String face, final int savedRival) {
            this.name = name;
            this.face = face == null ? "" : face;
            this.savedRival = savedRival;
        }
    }

    /**
     * Reparte los asientos de {@code rivals} rivales.
     *
     * @param you tu nombre en la partida, para no repetirlo (puede ser "")
     */
    public static List<Seat> deal(final int rivals, final String you) {
        final Seat[] out = new Seat[Math.max(0, rivals)];
        final Set<String> taken = new HashSet<>();
        if (you != null && !you.isBlank()) {
            taken.add(you);
        }
        final List<Integer> closest = closestFirst(rivals);
        // Primero tus rivales, en los asientos de al lado.
        for (int j = 0; j < closest.size() && j < RivalSetup.SEATS; j++) {
            final String saved = RivalSetup.name(j);
            if (saved == null || saved.isBlank() || taken.contains(saved)) {
                continue;
            }
            taken.add(saved);
            out[closest.get(j)] = new Seat(saved, RivalSetup.avatar(j), j);
        }
        // Y el resto, del generador de Forge, sin apuntarlos en ningun sitio.
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null) {
                final String name = freshName(taken, i);
                taken.add(name);
                out[i] = new Seat(name, "", -1);
            }
        }
        final List<Seat> list = new ArrayList<>();
        Collections.addAll(list, out);
        return list;
    }

    /**
     * QUIEN SE VA A SENTAR A TU LADO, antes de empezar (Discord, 18-11-2026:
     * <i>"I have looked around and really cannot find where to customize the
     * opponents in the draft"</i>).  Los rivales con nombre de Personalizar, en
     * el asiento que les dara {@link #deal} con {@code rivals} rivales, de mas
     * cerca a mas lejos: "Ajani a tu izquierda", "Chandra a tu derecha"...
     * Vacia si no hay ninguno con nombre (entonces todos salen del generador).
     * La usan la pantalla de elegir draft del PC y la de Android.
     */
    public static List<String> neighbors(final int rivals) {
        final List<String> out = new ArrayList<>();
        final Set<String> taken = new HashSet<>();
        final List<Integer> closest = closestFirst(rivals);
        for (int j = 0; j < closest.size() && j < RivalSetup.SEATS; j++) {
            final String saved = RivalSetup.name(j);
            if (saved == null || saved.isBlank() || !taken.add(saved)) {
                continue;
            }
            out.add(neighbor(j, rivals, saved));
        }
        return out;
    }

    /**
     * Donde se sentara el rival {@code slot} de Personalizar (0..SEATS-1) en un
     * draft de {@code rivals} rivales: "Ajani a tu izquierda".  Null si no le
     * toca asiento a tu lado.
     */
    public static String neighbor(final int slot, final int rivals, final String name) {
        final List<Integer> closest = closestFirst(rivals);
        if (slot < 0 || slot >= closest.size()) {
            return null;
        }
        final int i = closest.get(slot);
        return i == 0 ? NeoText.get("draft.neighbors.left", name)
                : i == rivals - 1 ? NeoText.get("draft.neighbors.right", name)
                : NeoText.get("draft.neighbors.away", name, Math.min(i + 1, rivals - i));
    }

    /** Una linea para la pantalla de elegir draft: "Tus vecinos: ..." o que salen al azar. */
    public static String neighborsLine(final int rivals) {
        final List<String> n = neighbors(rivals);
        return n.isEmpty() ? NeoText.get("draft.neighbors.none")
                : NeoText.get("draft.neighbors", String.join(" · ", n));
    }

    /** El orden de los asientos de mas cerca a mas lejos: 0, ultimo, 1, penultimo... */
    static List<Integer> closestFirst(final int rivals) {
        final List<Integer> order = new ArrayList<>();
        int lo = 0;
        int hi = rivals - 1;
        while (lo <= hi) {
            order.add(lo++);
            if (lo <= hi) {
                order.add(hi--);
            }
        }
        return order;
    }

    private static String freshName(final Set<String> taken, final int i) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                final String candidate = forge.util.GuiDisplayUtil.getRandomAiName();
                if (candidate != null && !candidate.isBlank() && !taken.contains(candidate)) {
                    return candidate;
                }
            } catch (final RuntimeException e) {
                break;
            }
        }
        String fallback = "AI-" + (i + 1);
        while (taken.contains(fallback)) {
            fallback = fallback + "'";
        }
        return fallback;
    }

    /**
     * La cara de la hoja de Forge para un asiento sin cara elegida: sale del
     * NOMBRE, asi que es la misma en todas las partidas del evento.
     *
     * @param faces cuantas caras trae la hoja (lo sabe cada interfaz)
     */
    public static int faceIndex(final Seat seat, final int faces) {
        final int sprite = RivalSetup.spriteIndexOf(seat.face);
        if (sprite >= 0) {
            return sprite;
        }
        if (seat.savedRival >= 0 && seat.face.isEmpty()) {
            // Tu rival guardado sin cara elegida: la de siempre, su numero.
            return seat.savedRival;
        }
        return faces <= 0 ? 0 : Math.floorMod(seat.name.hashCode(), faces);
    }

    // ------------------------------------------------------------------
    //  A quien pasas
    // ------------------------------------------------------------------

    /** El rival (desde 0) al que pasas el sobre en esa ronda (1, 2, 3...). */
    public static int passesTo(final int round, final int rivals) {
        return round % 2 == 1 ? 0 : rivals - 1;
    }

    /** El rival (desde 0) que te pasa el sobre en esa ronda. */
    public static int receivesFrom(final int round, final int rivals) {
        return round % 2 == 1 ? rivals - 1 : 0;
    }

    /**
     * "Pasas a Ajani · recibes de Chandra", para la pantalla de picks. Null
     * sin asientos.
     */
    public static String passNote(final List<Seat> seats, final int round) {
        if (seats == null || seats.size() < 2 || round < 1) {
            return null;
        }
        final int n = seats.size();
        return NeoText.get("draft.passNote",
                seats.get(passesTo(round, n)).name, seats.get(receivesFrom(round, n)).name);
    }

    /**
     * Donde se sienta el rival {@code i}, dicho para el marcador: "a tu
     * izquierda", "a tu derecha" o "a 3 asientos".
     */
    public static String whereSits(final int i, final int rivals) {
        if (i == 0) {
            return NeoText.get("draft.seat.left");
        }
        if (i == rivals - 1) {
            return NeoText.get("draft.seat.right");
        }
        return NeoText.get("draft.seat.away", Math.min(i + 1, rivals - i));
    }

    // ------------------------------------------------------------------
    //  En la partida
    // ------------------------------------------------------------------

    /**
     * Los asientos de los rivales de UNA partida, por su indice en el evento.
     * Null donde no hay asiento (un draft de antes, sin nombres).
     */
    public static List<Seat> chairs(final List<Seat> seats, final List<Integer> rivalIndexes) {
        final List<Seat> out = new ArrayList<>();
        for (final Integer i : rivalIndexes) {
            out.add(i != null && i >= 0 && i < seats.size() ? seats.get(i) : null);
        }
        return out;
    }

    /**
     * Sienta a cada rival con el nombre y la cara de su asiento. Lo demas, como
     * estaba: lo de {@code base} si la hay (el Commander Draft) o lo de la
     * partida normal de ese formato, igual que {@code NeoGame} sin asientos.
     *
     * @param sit como se sienta un asiento en esta interfaz (la cara importada
     *            y la funda las pinta cada una a su manera)
     */
    public static forge.neo.match.NeoGame.Seating seating(final forge.neo.match.NeoGame.Seating base,
                                                         final forge.neo.match.NeoFormat format,
                                                         final List<Seat> chairs,
                                                         final java.util.function.BiFunction<Seat, String, forge.LobbyPlayer> sit) {
        return new forge.neo.match.NeoGame.Seating() {
            @Override
            public java.util.EnumSet<forge.game.GameType> variants() {
                return base != null ? base.variants() : java.util.EnumSet.of(format.getGameType());
            }

            @Override
            public forge.game.GameType gameType() {
                return base != null ? base.gameType() : null;
            }

            @Override
            public forge.game.player.RegisteredPlayer human(final forge.deck.Deck deck, final int seats) {
                return base != null ? base.human(deck, seats) : format.register(deck, seats);
            }

            @Override
            public forge.game.player.RegisteredPlayer opponent(final int i, final forge.deck.Deck deck,
                                                               final int seats) {
                return base != null ? base.opponent(i, deck, seats) : format.register(deck, seats);
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String aiProfile) {
                final Seat seat = i < chairs.size() ? chairs.get(i) : null;
                if (seat != null) {
                    return sit.apply(seat, aiProfile);
                }
                return base != null ? base.player(i, aiProfile) : null;
            }
        };
    }

    // ------------------------------------------------------------------
    //  Guardar con el evento
    // ------------------------------------------------------------------

    /** Una linea por asiento: nombre, cara y rival guardado, separados por tabulador. */
    public static String serialize(final List<Seat> seats) {
        final StringBuilder sb = new StringBuilder();
        for (final Seat s : seats) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(clean(s.name)).append('\t').append(clean(s.face)).append('\t').append(s.savedRival);
        }
        return sb.toString();
    }

    public static List<Seat> parse(final String text) {
        final List<Seat> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (final String line : text.split("\n")) {
            final String[] p = line.split("\t", -1);
            if (p.length < 3 || p[0].isBlank()) {
                continue;
            }
            int saved;
            try {
                saved = Integer.parseInt(p[2].trim());
            } catch (final NumberFormatException e) {
                saved = -1;
            }
            out.add(new Seat(p[0], p[1], saved));
        }
        return out;
    }

    private static String clean(final String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
