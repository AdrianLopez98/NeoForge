package forge.neo.ascent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoText;

/**
 * Una run entera de Ascenso, sin ventana.
 *
 * <h2>Por que hace falta, y por que no basta con {@link AscentProbe}</h2>
 *
 * <p>La sonda contesta <i>el motor deja hacer esto</i>. Esto otro contesta <i>el
 * bucle del modo se cierra</i>: empezar, recorrer un mapa nodo a nodo eligiendo
 * camino, cobrar los premios, arrastrar la vida, pasar de acto, guardar,
 * recargar a mitad y terminar — ganando y perdiendo.
 *
 * <p><b>Ninguno de los fallos que caza aqui revienta.</b> Un premio que se
 * sortea dos veces distinto, una vida que no se arrastra, un mapa que al
 * recargar sale otro, un nodo al que no se puede llegar: todos dejan el juego
 * funcionando y el modo roto. Por eso se comprueba a volumen y no jugando una
 * partida.
 *
 * <h2>Aqui no se juega ninguna partida</h2>
 *
 * <p>A proposito, y es la decision que hace que esto sirva: una run son ~37
 * nodos y jugarlos de verdad son horas. El resultado de cada combate se
 * <b>simula</b> (ganas y pierdes vida) y lo que se comprueba es <b>todo lo que
 * hay alrededor</b>, que es donde estan los fallos mudos. Que una partida de
 * Ascenso arranca de verdad —con su vida arrastrada, sus esquemas y sus
 * reliquias— ya lo miden las sondas 1 a 4 de {@link AscentProbe}, jugando.
 */
public final class AscentCheck {

    private AscentCheck() {
    }

    private static int passed;
    private static int failed;

    /** Cuantas runs se recorren de punta a punta. */
    private static final int RUNS = 20;

    /**
     * Cuanto tiene que subir la potencia de los rivales del acto 1 al 3.
     *
     * <p>Se exige <b>de punta a punta</b> y no escalon a escalon, y eso se
     * decidio midiendo: los 173 preconstruidos de Commander son todos mazos de
     * verdad con una proporcion de raras parecida, asi que entre dos escalones
     * seguidos la diferencia es pequenya (x1,17) por mucho que del primero al
     * ultimo haya un x1,5 claro. Pedir x1,25 <b>en cada paso</b> no habria
     * cazado ningun fallo: habria obligado a inventarse una puntuacion que
     * separara mas de lo que el pozo separa.
     *
     * <p>Lo que si se exige en cada paso es que <b>suba</b>. Las dos cosas
     * juntas son la comprobacion util: la escalera no baja en ningun escalon, y
     * el final es de verdad otra cosa que el principio.
     */
    private static final double GAP = 1.40;

    public static void run() {
        passed = 0;
        failed = 0;

        // ⚠️ Esto escribe en ascent.* de verdad: son runs de mentira, pero el
        // marcador es el mismo fichero. Sin guardarlo y reponerlo, pasar los
        // comprobadores le BORRA al jugador la run que tenga a medias — y en
        // un modo donde no se puede volver atras, eso no tiene arreglo.
        final Map<String, String> suRun = AscentRun.snapshot();
        // Los hitos NO viven bajo "ascent." — viven aparte justo para
        // sobrevivir a la derrota — asi que el snapshot de arriba no los
        // recoge. Sin esto, pasar los comprobadores le regalaria (o le
        // borraria) al jugador desbloqueos que no ha ganado.
        final String susHitos = AscentUnlocks.rawFeatsForTest();
        try {
            curvaDeDificultad();
            curvaSigueTuPoder();
            reliquiasBienEscritas();
            legendarias();
            reliquiasDeJefe();
            elJefeNoEsArchienemigo();
            recorrerRuns();
            premios();
            tierrasEnElPremio();
            elDobleEnCommander();
            comandanteMulticolor();
            singletonEnCommander();
            calidadDelPremio();
            reliquiasQueLaIaPuedeLlevar();
            descanso();
            tienda();
            eventos();
            planos();
            resumen();
            guardarYRecargar();
            perderYAbandonar();
            desbloqueos();
            hitos();
            nivelesDeAscension();
        } finally {
            AscentRun.restore(suRun);
            AscentUnlocks.setRawFeatsForTest(susHitos);
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la run han fallado");
        }
    }

    // ------------------------------------------------------------------
    //  1. La curva de dificultad sube de verdad
    // ------------------------------------------------------------------

    /**
     * Que el acto 3 sea mas duro que el 1, <b>medido</b>.
     *
     * <p>"Los rivales del acto 3 son mas fuertes" es una frase, no un hecho:
     * los mazos salen de una lista ordenada por una puntuacion nuestra, y si
     * esa puntuacion no separara nada, los tres actos darian rivales
     * indistinguibles y el modo entero se sentiria plano <b>sin que nada
     * fallara</b>. Asi que se mide: la media del tercio de arriba tiene que
     * ganarle a la del tercio de abajo.
     */
    private static void curvaDeDificultad() {
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            final List<Deck> pool = AscentBattle.pool(mode);
            if (pool.size() < 30) {
                fail("curva (" + mode + "): el pozo de rivales son solo " + pool.size()
                        + " mazos, no da para tres escalones");
                continue;
            }
            final double bajo = averagePower(pool, 0);
            final double medio = averagePower(pool, 1);
            final double alto = averagePower(pool, 2);
            // No basta con "sube": con "sube" a secas esto pasaba con
            // 0,05 -> 0,06 -> 0,18, o sea con el acto 2 dando rivales
            // indistinguibles de los del 1 y la comprobacion diciendo que todo
            // bien. Se pide que suba en cada escalon Y que el final sea de
            // verdad otra cosa que el principio.
            if (bajo < medio && medio < alto && bajo * GAP <= alto) {
                ok(String.format(Locale.ROOT,
                        "curva (%s): %d rivales, potencia media %.2f -> %.2f -> %.2f",
                        mode, pool.size(), bajo, medio, alto));
            } else {
                fail(String.format(Locale.ROOT,
                        "curva (%s): la escalera no se sostiene (%.2f, %.2f, %.2f; "
                                + "cada escalon tiene que subir y el ultimo ser x%.2f del primero)",
                        mode, bajo, medio, alto, GAP));
            }
        }

        curvaDeLaRun();
        jefeYElite();
    }

    /**
     * La curva de la run entera, nodo a nodo, <b>impresa</b>.
     *
     * <p>Se comprueba sobre la <b>altura</b> de la run y no sobre el acto,
     * porque es asi como esta escrita: con la dificultad atada al acto, los
     * doce nodos de un acto salian identicos y el salto llegaba de golpe al
     * cambiar de mapa — cuando lo que ha crecido durante esos doce nodos es tu
     * mazo, una carta por combate.
     *
     * <p>Y lo primero que exige es que <b>el principio sea un paseo</b>: con 20
     * de vida arrastrada, el rival del primer nodo tiene 6. Un roguelike que te
     * mata en el nodo 1 con el mazo de salida no es dificil, es injusto.
     */
    private static void curvaDeLaRun() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("curva: no se ha podido montar la run de prueba");
            return;
        }
        final List<Integer> vidas = new ArrayList<>();
        final List<String> perfiles = new ArrayList<>();
        final List<Integer> ventajas = new ArrayList<>();
        final StringBuilder tabla = new StringBuilder();
        try {
            for (int act = 1; act <= AscentRun.ACTS; act++) {
                while (run.getAct() < act && run.nextAct()) {
                    // subir de acto sin pelear: aqui solo se mira el plan
                }
                // Filas 0, 4 y 9. No {0, 6, 10}: la 10 es el descanso obligatorio
                // y la 11 el jefe, asi que el tercer muestreo no encontraba
                // ningun combate y la tabla se quedaba SIN la parte alta de cada
                // acto — justo el tramo que hay que mirar.
                for (final int row : new int[]{0, 4, AscentMap.ROWS - 3}) {
                    final AscentNode node = combatAt(run, row);
                    if (node == null) {
                        continue;
                    }
                    final AscentBattle.Plan plan = AscentBattle.plan(run, node);
                    vidas.add(plan.opponentLife);
                    perfiles.add(plan.aiProfile);
                    ventajas.add(plan.opponentHeadStart.size());
                    tabla.append(String.format(Locale.ROOT, "%n         acto %d fila %2d -> "
                                    + "%2d vidas | %-10s | ventaja %d | %s",
                            act, node.getRow(), plan.opponentLife, plan.aiProfile,
                            plan.opponentHeadStart.size(),
                            AscentBattle.name(plan.opponentDeck)));
                }
            }
        } finally {
            run.discard();
        }
        if (vidas.size() < 6) {
            fail("curva: no se han podido muestrear suficientes nodos de combate");
            return;
        }

        // 1. El primer rival tiene que ser un paseo.
        final int primera = vidas.get(0);
        final int tuya = 20;
        if (primera <= tuya * 0.4) {
            ok("curva: el PRIMER rival tiene " + primera + " vidas contra tus " + tuya
                    + " — sales con ventaja de sobra" + tabla);
        } else {
            fail("curva: el primer rival ya tiene " + primera + " vidas contra tus " + tuya
                    + "; los primeros nodos tienen que ser un paseo" + tabla);
        }

        // 2. Y no puede bajar en ningun tramo.
        int baja = -1;
        for (int i = 1; i < vidas.size(); i++) {
            if (vidas.get(i) < vidas.get(i - 1)) {
                baja = i;
            }
        }
        if (baja < 0) {
            ok("curva: la vida del rival no baja en ningun tramo (" + vidas.get(0) + " -> "
                    + vidas.get(vidas.size() - 1) + ")");
        } else {
            fail("curva: la vida del rival BAJA en el tramo " + baja + ": " + vidas);
        }

        // 3. Y el final tiene que ser otra cosa que el principio.
        if (vidas.get(vidas.size() - 1) >= primera * 3) {
            ok("curva: el ultimo rival tiene " + (vidas.get(vidas.size() - 1) / (double) primera)
                    + "x la vida del primero");
        } else {
            fail("curva: del primer al ultimo rival la vida solo pasa de " + primera + " a "
                    + vidas.get(vidas.size() - 1) + ", que no se nota");
        }

        // 4. Sube DENTRO del acto 1, no solo al cambiar de acto.
        if (vidas.get(2) > vidas.get(0)) {
            ok("curva: dentro del acto 1 ya sube (" + vidas.get(0) + " -> " + vidas.get(2) + ")");
        } else {
            fail("curva: los doce nodos del acto 1 dan el mismo rival ("
                    + vidas.get(0) + "); el salto solo llega al cambiar de mapa");
        }

        // 5. Las otras dos palancas.
        if (perfiles.get(0).equals("Cautious")
                && perfiles.get(perfiles.size() - 1).equals("Reckless")) {
            ok("curva: la IA va de " + perfiles.get(0) + " a " + perfiles.get(perfiles.size() - 1));
        } else {
            fail("curva: el caracter de la IA no recorre la escalera: " + perfiles);
        }
        if (ventajas.get(0) == 0 && ventajas.get(ventajas.size() - 1) > 0) {
            ok("curva: la ventaja de salida va de " + ventajas.get(0) + " a "
                    + ventajas.get(ventajas.size() - 1) + " tierras");
        } else {
            fail("curva: la ventaja de salida no escala: " + ventajas);
        }
    }

    /**
     * Que el rival se estire cuando TU te haces fuerte.
     *
     * <p>Decision del autor: la curva escala con las pasivas y las cartas buenas
     * que consigues, no solo con el numero de nodo. Sin esto, una run que
     * engancha dos reliquias de jefe pasea por el acto 3 — la dificultad iba
     * contando pasos mientras el jugador multiplicaba su potencia.
     *
     * <p>Y lo segundo que se exige es lo contrario, y es igual de importante:
     * que <b>suba menos de lo que te ha dado</b>. Si el rival recuperara toda la
     * ventaja, mejorar el mazo no serviria de nada y el modo entero se quedaria
     * sin su bucle.
     */
    private static void curvaSigueTuPoder() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("poder: no se ha podido montar la run de prueba");
            return;
        }
        final int sinNada;
        final int cargado;
        try {
            final double climb = AscentBattle.progress(2, 6);
            sinNada = AscentBattle.lifeAt(run.getMaxLife(), climb, AscentBattle.playerEdge(run));

            // La misma run, pero con todo: las reliquias puestas y el mazo
            // lleno de cartas buenas.
            for (final AscentRelic r : AscentRelics.all()) {
                run.addRelic(r);
            }
            final Deck mazo = AscentDecks.load(run);
            if (mazo != null) {
                for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
                    if (c.getRarity() == forge.card.CardRarity.MythicRare
                            && c.getRules() != null && !c.getRules().getType().isLand()) {
                        mazo.getMain().add(c, 12);
                        break;
                    }
                }
                AscentDecks.save(mazo);
            }
            cargado = AscentBattle.lifeAt(run.getMaxLife(), climb, AscentBattle.playerEdge(run));
        } finally {
            run.discard();
        }

        if (cargado > sinNada) {
            ok("poder: el rival se estira con lo que llevas — " + sinNada
                    + " vidas con las manos vacias, " + cargado
                    + " con todas las reliquias y el mazo cargado");
        } else {
            fail("poder: el rival NO nota que te has hecho fuerte (" + sinNada + " frente a "
                    + cargado + "); una run que engancha dos reliquias pasearia el acto 3");
        }
        if (cargado <= sinNada * 1.6) {
            ok("poder: y sube MENOS de lo que te ha dado, asi que mejorar sigue"
                    + " mereciendo mucho la pena");
        } else {
            fail("poder: el rival recupera demasiada ventaja (" + sinNada + " -> " + cargado
                    + "); asi mejorar el mazo no serviria de nada");
        }
    }

    /**
     * Que un jefe ofrezca TRES reliquias distintas, y en los tres actos.
     *
     * <p>Se comprueba en los tres jefes seguidos y no en uno: el pozo de
     * reliquias de jefe se va vaciando segun las coges, asi que el caso que se
     * rompe es el <b>tercero</b> — y solo aparece si se recorre la run entera.
     */
    private static void reliquiasDeJefe() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("jefe: no se ha podido montar la run de prueba");
            return;
        }
        try {
            final Set<String> mias = new HashSet<>();
            for (int act = 1; act <= AscentRun.ACTS; act++) {
                while (run.getAct() < act && run.nextAct()) {
                    // subir sin pelear: aqui solo interesa el premio del jefe
                }
                final AscentRewards.Reward premio = AscentRewards.of(run, run.map().boss());
                if (!premio.chooseOne || premio.relics.size() != AscentRewards.RELIC_CHOICES) {
                    fail("jefe: el del acto " + act + " ofrece " + premio.relics.size()
                            + " reliquias en vez de " + AscentRewards.RELIC_CHOICES + " a elegir");
                    return;
                }
                final Set<String> ids = new HashSet<>();
                for (final AscentRelic r : premio.relics) {
                    ids.add(r.getId());
                    if (mias.contains(r.getId())) {
                        fail("jefe: el del acto " + act + " ofrece " + r.getCardName()
                                + ", que ya llevas puesta");
                        return;
                    }
                }
                if (ids.size() != AscentRewards.RELIC_CHOICES) {
                    fail("jefe: el del acto " + act + " ofrece la misma reliquia dos veces");
                    return;
                }
                // Te quedas una, como en la partida de verdad.
                final AscentRelic elegida = premio.relics.get(act - 1);
                run.addRelic(elegida);
                mias.add(elegida.getId());
            }
            ok("jefe: los tres ofrecen " + AscentRewards.RELIC_CHOICES
                    + " reliquias distintas, y ninguna que ya lleves");
        } finally {
            run.discard();
        }

        int deJefe = 0;
        for (final AscentRelic r : AscentRelics.all()) {
            if (r.getRarity() == AscentRelic.Rarity.BOSS) {
                deJefe++;
            }
        }
        final int hacenFalta = AscentRun.ACTS + AscentRewards.RELIC_CHOICES - 1;
        if (deJefe >= hacenFalta) {
            ok("jefe: hay " + deJefe + " reliquias de jefe en el catalogo (hacen falta "
                    + hacenFalta + " para que el ultimo siga eligiendo entre tres)");
        } else {
            fail("jefe: solo hay " + deJefe + " reliquias de jefe y hacen falta " + hacenFalta
                    + "; el tercer jefe se quedaria sin de donde elegir");
        }
    }

    /**
     * Que TODAS las reliquias se registren y lleven su clave de zona.
     *
     * <p>Son {@code EffectZone$ Command} para las estaticas y
     * {@code TriggerZones$ Command} para los disparos, y sin ellas <b>la carta
     * esta en el mando y no hace absolutamente nada</b>: por defecto una
     * estatica y un disparo solo miran el campo de batalla. Forge no avisa —
     * ni error, ni excepcion, ni traza.
     *
     * <p>Esto NO sustituye a jugar cada reliquia ({@code reliccheck}, fase 2):
     * un script puede llevar las dos claves y aun asi no hacer lo que dice. Pero
     * es el fallo mudo mas comun del mecanismo y cuesta cero comprobarlo.
     */
    /**
     * Que al jefe NO se le pongan esquemas de archienemigo.
     *
     * <h2>Por que se comprueba algo que "ya no esta"</h2>
     *
     * <p>Porque estuvo tres dias y volver a ponerlo parece una mejora. No lo
     * es: Archienemigo esta disenyado para <b>3 contra 1</b>, y en el 1c1 de
     * Ascenso cada esquema cae entero sobre una sola persona y encima cada
     * turno. Reportado jugando el 03-09-2026: <i>"el boss me ha reventado en
     * turno 3, 0 chances... es imposible ganar a un boss"</i>.
     *
     * <p>Y de paso cubre un cuelgue: sin esquemas no se puede llegar al fallo
     * del motor que tiraba la partida a mitad del combate contra el jefe
     * ({@code setSchemeInMotion} pidiendo el primero de un mazo vacio).
     *
     * <p>Se comprueba en los tres actos y en los dos modos, porque la variante
     * se anyade en un solo sitio pero el asiento se monta por acto.
     */
    private static void elJefeNoEsArchienemigo() {
        boolean limpio = true;
        boolean conReliquias = true;
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            final AscentRun run = demoRun(mode);
            if (run == null) {
                return;
            }
            try {
                final AscentBattle.Plan plan = AscentBattle.plan(run, run.map().boss());
                if (plan.schemes != null
                        || plan.variants.contains(forge.game.GameType.Archenemy)) {
                    limpio = false;
                }
                // Y que lo que SI le hace jefe siga ahi: si esto se cae, el
                // jefe se ha quedado en un combate con mas vida.
                //
                // Una en el acto 1 y dos despues (AscentBattle.bossRelics): al
                // jefe del acto 1 le puede tocar una reliquia que multiplique su
                // salida, y ahi el jugador todavia no tiene con que responder.
                if (plan.opponentRelics == null || plan.opponentRelics.isEmpty()) {
                    conReliquias = false;
                }
            } finally {
                discard(run);
            }
        }
        if (limpio) {
            ok("el jefe no es archienemigo en ningun acto ni modo (Archenemy es de 3c1)");
        } else {
            fail("el jefe lleva esquemas de archienemigo: en 1c1 eso es imposible de ganar");
        }
        if (conReliquias) {
            ok("y sigue llevando sus reliquias, que es lo que le hace jefe");
        } else {
            fail("el jefe se ha quedado sin reliquias");
        }
    }

    private static void reliquiasBienEscritas() {
        final List<String> mal = new ArrayList<>();
        final List<String> sinCarta = new ArrayList<>();
        for (final AscentRelic r : AscentRelics.all()) {
            final String script = AscentRelics.scriptOf(r.getId());
            if (AscentRelics.cardOf(r) == null || script == null) {
                sinCarta.add(r.getId());
                continue;
            }
            final boolean estatica = script.contains("\nS:");
            final boolean disparo = script.contains("\nT:");
            if (estatica && !script.contains("EffectZone$ Command")) {
                mal.add(r.getId() + " (estatica sin EffectZone$ Command)");
            }
            if (disparo && !script.contains("TriggerZones$ Command")) {
                mal.add(r.getId() + " (disparo sin TriggerZones$ Command)");
            }
            if (!estatica && !disparo) {
                mal.add(r.getId() + " (ni estatica ni disparo: no hace nada)");
            }
        }
        if (sinCarta.isEmpty()) {
            ok("reliquias: las " + AscentRelics.all().size()
                    + " del catalogo existen como carta del motor");
        } else {
            fail("reliquias: el motor no encuentra la carta de " + sinCarta);
        }
        if (mal.isEmpty()) {
            ok("reliquias: todas llevan su clave de zona (sin ella estarian en el mando"
                    + " sin hacer nada, y Forge no lo dice)");
        } else {
            fail("reliquias: sin la clave de zona NO hacen nada: " + mal);
        }
    }

    /**
     * El descanso: que cure algo, que no pase del maximo y que la Ascension 2
     * lo recorte de verdad.
     *
     * <p>Es la mitad de la decision mas pensada de la run — curarte <b>o</b>
     * quitar una carta — asi que si curara cero, o curara por encima del
     * maximo, el nodo estaria mintiendo sobre lo que ofrece. Y el recorte de la
     * Ascension 2 es de los que no se ven jugando: son dos vidas de diferencia
     * repartidas por toda la run.
     */
    private static void descanso() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("descanso: no se ha podido montar la run de prueba");
            return;
        }
        try {
            // A media vida: es donde se cura de verdad.
            run.recordLife(run.getMaxLife() / 2);
            final int cura = run.restHeal();
            if (cura > 0 && cura <= run.getMaxLife() - run.getLife()) {
                ok("descanso: cura " + cura + " con " + run.getLife() + "/"
                        + run.getMaxLife() + ", sin pasarse del maximo");
            } else {
                fail("descanso: cura " + cura + " con " + run.getLife() + "/"
                        + run.getMaxLife() + ", que no tiene sentido");
            }

            // Curar de verdad no puede pasar del maximo.
            run.heal(999);
            if (run.getLife() == run.getMaxLife()) {
                ok("descanso: curar de mas deja la vida al maximo, no por encima");
            } else {
                fail("descanso: curando 999 la vida ha quedado en " + run.getLife()
                        + " de " + run.getMaxLife());
            }

            // Y a tope, sigue sin devolver cero: la pantalla decide si lo
            // ofrece, pero el numero nunca es una mentira.
            if (run.restHeal() > 0) {
                ok("descanso: a vida llena el numero sigue siendo valido (lo que"
                        + " se apaga es el boton, no el calculo)");
            } else {
                fail("descanso: a vida llena devuelve cero, y un nodo que cura cero miente");
            }
        } finally {
            run.discard();
        }

        // Ascension 2: cura menos. No se ve jugando y es justo lo que endurece.
        final AscentRun facil = demoRun(AscentRun.Mode.STANDARD);
        final AscentRun duro = AscentRun.start(AscentRun.Mode.STANDARD, 2, 20,
                facil == null ? null : facil.getDeckName());
        final int curaFacil;
        final int curaDuro;
        try {
            duro.recordLife(1);
            curaDuro = duro.restHeal();
        } finally {
            duro.discard();
        }
        if (facil == null) {
            fail("descanso: no se ha podido comparar con Ascension 2");
            return;
        }
        try {
            facil.recordLife(1);
            curaFacil = facil.restHeal();
        } finally {
            facil.discard();
        }
        if (curaDuro < curaFacil) {
            ok("descanso: la Ascension 2 recorta la cura (" + curaFacil + " -> " + curaDuro + ")");
        } else {
            fail("descanso: la Ascension 2 cura lo mismo (" + curaFacil + " y " + curaDuro + ")");
        }
    }

    /**
     * Las legendarias: que existan, que salgan <b>poco</b> y que salgan.
     *
     * <p>Las dos mitades importan igual. Una legendaria que sale a menudo deja
     * de ser un regalo y pasa a ser el modo; una que no sale nunca es codigo
     * muerto y cinco reliquias escritas para nada. Y ninguna de las dos cosas se
     * ve jugando: harian falta veinte runs para notar que la probabilidad esta
     * mal, y para entonces ya se ha decidido que el modo "no engancha".
     *
     * <p>Se mide sobre diez mil tiradas, que es lo unico que distingue un 4% de
     * un 0% y de un 40%.
     */
    private static void legendarias() {
        int cuantas = 0;
        for (final AscentRelic r : AscentRelics.all()) {
            if (r.getRarity() == AscentRelic.Rarity.LEGENDARY) {
                cuantas++;
            }
        }
        if (cuantas >= 3) {
            ok("legendarias: hay " + cuantas + " en el catalogo de "
                    + AscentRelics.all().size() + " reliquias");
        } else {
            fail("legendarias: solo hay " + cuantas + "; con tan pocas, la que salga"
                    + " sera siempre la misma y deja de ser una sorpresa");
        }

        final int tiradas = 10000;
        final Random rnd = new Random(20260902L);
        final int[] deComun = new int[AscentRelic.Rarity.values().length];
        final int[] deJefe = new int[AscentRelic.Rarity.values().length];
        for (int i = 0; i < tiradas; i++) {
            deComun[AscentRewards.rollRarity(AscentRelic.Rarity.COMMON, rnd).ordinal()]++;
            deJefe[AscentRewards.rollRarity(AscentRelic.Rarity.BOSS, rnd).ordinal()]++;
        }
        final double pct = 100.0 * deComun[AscentRelic.Rarity.LEGENDARY.ordinal()] / tiradas;
        final double pctJefe = 100.0 * deJefe[AscentRelic.Rarity.LEGENDARY.ordinal()] / tiradas;
        if (pct > 0.5 && pct < 12) {
            ok(String.format(Locale.ROOT, "legendarias: salen el %.1f%% de las veces en un"
                    + " tesoro y el %.1f%% en un jefe — raras, pero no imposibles",
                    pct, pctJefe));
        } else {
            fail(String.format(Locale.ROOT, "legendarias: salen el %.1f%% de las veces en un"
                    + " tesoro, que no es 'muy raro pero posible'", pct));
        }

        // Y que una comun siga siendo lo normal: si el sorteo subiera de rareza
        // demasiado, el escalon comun no significaria nada.
        final double comunes = 100.0 * deComun[AscentRelic.Rarity.COMMON.ordinal()] / tiradas;
        if (comunes > 60) {
            ok(String.format(Locale.ROOT,
                    "legendarias: un tesoro sigue dando comun el %.0f%% de las veces", comunes));
        } else {
            fail(String.format(Locale.ROOT, "legendarias: un tesoro solo da comun el %.0f%%"
                    + " de las veces; el sorteo sube de rareza demasiado", comunes));
        }
    }

    /**
     * Los desbloqueos: que suban al ganar y que <b>sobrevivan a la derrota</b>.
     *
     * <p>La segunda mitad es la que importa. {@link AscentRun#discard()} borra
     * todas las claves que empiezan por {@code ascent.} — que perder duela es el
     * modo — asi que un desbloqueo guardado ahi dentro se iria con la primera
     * derrota. Y ese fallo <b>no da ningun error</b>: solo hace que ganar no
     * signifique nada y que el modo no progrese jamas. Por eso los desbloqueos
     * viven en sus propias claves y por eso esto se comprueba.
     */
    private static void desbloqueos() {
        final int maxAntes = AscentUnlocks.maxAscension();
        final int winsAntes = AscentUnlocks.wins();
        try {
            AscentUnlocks.resetForTest(0, 0);

            // Ganar sube uno.
            final boolean subio = AscentUnlocks.recordWin(0);
            if (subio && AscentUnlocks.maxAscension() == 1 && AscentUnlocks.wins() == 1) {
                ok("desbloqueos: completar una run sube la Ascension a 1");
            } else {
                fail("desbloqueos: tras ganar, ascension=" + AscentUnlocks.maxAscension()
                        + " victorias=" + AscentUnlocks.wins());
            }

            // Ganar en FACIL no puede bajar lo que ya tenias.
            AscentUnlocks.resetForTest(5, 3);
            AscentUnlocks.recordWin(0);
            if (AscentUnlocks.maxAscension() == 5) {
                ok("desbloqueos: ganar en una Ascension baja no baja la que llevabas");
            } else {
                fail("desbloqueos: ganar en Ascension 0 con la 5 desbloqueada la ha dejado en "
                        + AscentUnlocks.maxAscension());
            }

            // Y no se pasa del tope.
            AscentUnlocks.resetForTest(AscentUnlocks.MAX, 0);
            AscentUnlocks.recordWin(AscentUnlocks.MAX);
            if (AscentUnlocks.maxAscension() == AscentUnlocks.MAX) {
                ok("desbloqueos: no se pasa del tope (" + AscentUnlocks.MAX + ")");
            } else {
                fail("desbloqueos: se ha pasado del tope: " + AscentUnlocks.maxAscension());
            }

            // LO IMPORTANTE: perder una run no se los lleva.
            AscentUnlocks.resetForTest(4, 7);
            final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
            if (run == null) {
                fail("desbloqueos: no se ha podido montar la run de prueba");
                return;
            }
            run.discard();
            if (AscentUnlocks.maxAscension() == 4 && AscentUnlocks.wins() == 7) {
                ok("desbloqueos: perder la run NO se lleva lo que te habias ganado"
                        + " (ascension 4, 7 victorias siguen ahi)");
            } else {
                fail("desbloqueos: perder la run se ha llevado los desbloqueos —"
                        + " ascension " + AscentUnlocks.maxAscension()
                        + ", victorias " + AscentUnlocks.wins()
                        + "; asi ganar no significa nada y el modo no progresa nunca");
            }
        } finally {
            AscentUnlocks.resetForTest(maxAntes, winsAntes);
        }
    }

    /**
     * Los niveles de Ascension sueltos: 1, 3, 7, 8 y 9 (el 2, 4, 5 y 6 ya
     * tienen su propio comprobador donde se miden — descanso, curva y jefe).
     *
     * <p>Cada uno se compara CONTRA si mismo sin el nivel, no "a ojo": son
     * cambios de dos vidas o de una fila de mapa, del tipo que <b>no se nota
     * jugando</b> y que aqui SI se puede medir con exactitud.
     */
    private static void nivelesDeAscension() {
        ascension1EliteUnaFilaAntes();
        ascension3CartaMaldita();
        ascension5JefeConVentajaExtra();
        ascension7UnaEliteMas();
        ascension8EmpiezaMagullado();
        ascension9TiendaMasCara();
        ascension10SegundoAliento();
    }

    /**
     * Ascension 5: "el jefe roba un esquema extra el primer turno" no se
     * puede hacer sin tocar {@code forge-game} — el robo de esquema del
     * archienemigo esta escrito a fuego en {@code PhaseHandler}, sin gancho
     * de carta (ver el comentario en {@code AscentBattle.plan}). Mismo
     * espiritu por un camino que SI es nuestro: el jefe entra con una carta
     * mas ya en mesa. Se compara sobre el mismo nodo (el jefe siempre esta en
     * la ultima fila, y la altura de la run en el primer acto no depende de
     * la semilla) asi que la unica diferencia posible es esa carta de mas.
     */
    private static void ascension5JefeConVentajaExtra() {
        final AscentRun normal = demoRun(AscentRun.Mode.STANDARD);
        final AscentRun ascendido = begin(AscentRun.Mode.STANDARD, 5);
        try {
            if (normal == null || ascendido == null) {
                fail("ascension 5: no se ha podido montar la run de prueba");
                return;
            }
            final int ventajaNormal =
                    AscentBattle.plan(normal, normal.map().boss()).opponentHeadStart.size();
            final int ventajaAscendida =
                    AscentBattle.plan(ascendido, ascendido.map().boss()).opponentHeadStart.size();
            if (ventajaAscendida == ventajaNormal + 1) {
                ok("ascension 5: el jefe entra con una carta mas en mesa (" + ventajaNormal
                        + " -> " + ventajaAscendida + ")");
            } else {
                fail("ascension 5: ventaja del jefe sin Ascension=" + ventajaNormal
                        + ", con Ascension 5=" + ventajaAscendida + " (deberia ser una mas)");
            }
        } finally {
            discard(normal);
            discard(ascendido);
        }
    }

    /**
     * Ascension 10: el jefe del acto 3 lleva una TERCERA reliquia — su
     * "segundo aliento" — que dispara sola con la vida baja
     * ({@code Count$YourLifeTotal}, ver los scripts). Tres cosas, y las tres
     * hacen falta: que las dos cartas (una por modo) esten registradas; que
     * el jefe del acto 3 la lleve; y que el jefe del acto 1 —el mismo
     * Ascension, otro acto— NO la lleve, porque es "el jefe del acto 3", no
     * todos.
     *
     * <p>⚠️ Que NO aparezcan en el catalogo del deck builder se comprueba en
     * {@code run.cmd deckcheck} y no aqui: {@code getUniqueCards()} SI las
     * trae en cuanto se juega una partida de verdad y el motor reindexa —
     * mirarlo aqui, antes de jugar nada, habria dado un verde que no
     * demuestra nada. El filtro de verdad vive en {@code CardIndex}.
     */
    private static void ascension10SegundoAliento() {
        final List<String> mal = new ArrayList<>();
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            if (AscentRelics.bossPhaseCard(mode) == null) {
                mal.add(mode + " (no registrada)");
            }
        }
        if (mal.isEmpty()) {
            ok("ascension 10: el segundo aliento esta registrado para los dos modos");
        } else {
            fail("ascension 10: " + mal);
        }

        final AscentRun jefe3run = begin(AscentRun.Mode.STANDARD, 10);
        if (jefe3run == null) {
            fail("ascension 10: no se ha podido montar la run de prueba");
        } else {
            try {
                jefe3run.nextAct();
                jefe3run.nextAct(); // acto 3
                final AscentBattle.Plan jefe3 = AscentBattle.plan(jefe3run, jefe3run.map().boss());
                final String nombreEsperado = AscentRelics.bossPhaseCard(AscentRun.Mode.STANDARD).getName();
                boolean loTiene = false;
                for (final PaperCard c : jefe3.opponentRelics) {
                    if (c.getName().equals(nombreEsperado)) {
                        loTiene = true;
                        break;
                    }
                }
                if (loTiene && jefe3.opponentRelics.size() == 3) {
                    ok("ascension 10: el jefe del acto 3 lleva su tercera reliquia,"
                            + " el segundo aliento");
                } else {
                    fail("ascension 10: el jefe del acto 3 lleva " + jefe3.opponentRelics.size()
                            + " reliquias con el segundo aliento=" + loTiene
                            + " (deberian ser 3 y true)");
                }
            } finally {
                discard(jefe3run);
            }
        }

        final AscentRun jefe1run = begin(AscentRun.Mode.STANDARD, 10);
        if (jefe1run == null) {
            fail("ascension 10: no se ha podido montar la segunda run de prueba");
        } else {
            try {
                final AscentBattle.Plan jefe1 = AscentBattle.plan(jefe1run, jefe1run.map().boss());
                if (jefe1.opponentRelics.size() == 1) {
                    ok("ascension 10: el jefe del acto 1 (no el 3) se queda con su reliquia"
                            + " de siempre, sin segundo aliento");
                } else {
                    fail("ascension 10: el jefe del acto 1 con Ascension 10 lleva "
                            + jefe1.opponentRelics.size() + " reliquias (deberia ser 1:"
                            + " el segundo aliento es solo del acto 3)");
                }
            } finally {
                discard(jefe1run);
            }
        }
    }

    /**
     * Ascension 1: una elite puede salir en la fila 3, que sin Ascension esta
     * vedada (§{@code AscentMap.NO_HARD_BEFORE}). Se generan 200 mapas de cada
     * lado: sin Ascension NUNCA tiene que verse una elite ahi; con Ascension 1,
     * alguna vez si — no en todos, porque sigue siendo un sorteo.
     */
    private static void ascension1EliteUnaFilaAntes() {
        boolean sinAscensionEnFila3 = false;
        boolean conAscensionEnFila3 = false;
        for (int i = 0; i < 200; i++) {
            if (countKindAt(new AscentMap(9001L + i, 1, 0), AscentNode.Kind.ELITE, 3) > 0) {
                sinAscensionEnFila3 = true;
            }
            if (countKindAt(new AscentMap(9001L + i, 1, 1), AscentNode.Kind.ELITE, 3) > 0) {
                conAscensionEnFila3 = true;
            }
        }
        if (!sinAscensionEnFila3 && conAscensionEnFila3) {
            ok("ascension 1: las elites pueden salir en la fila 3 (una antes de lo normal)"
                    + " — nunca sin Ascension, alguna vez con ella");
        } else {
            fail("ascension 1: fila 3 con elite — sin Ascension=" + sinAscensionEnFila3
                    + " (deberia ser siempre false), con Ascension 1=" + conAscensionEnFila3
                    + " (deberia ser true alguna vez)");
        }
    }

    /**
     * Ascension 3: el mazo de salida trae la carta maldita (Millstone) y NO
     * crece de tamano — se sustituye un hechizo, no se anyade uno de mas — y
     * sin Ascension esa carta no aparece.
     */
    private static void ascension3CartaMaldita() {
        final AscentRun sana = demoRun(AscentRun.Mode.STANDARD);
        final AscentRun maldita = begin(AscentRun.Mode.STANDARD, 3);
        try {
            if (sana == null || maldita == null) {
                fail("ascension 3: no se ha podido montar la run de prueba");
                return;
            }
            final Deck deckSana = AscentDecks.load(sana);
            final Deck deckMaldita = AscentDecks.load(maldita);
            final boolean laTieneMaldita = hasCard(deckMaldita, "Millstone");
            // ⚠️ "El mazo sano NO la lleva" no se puede exigir de UN mazo, y
            // exigirlo hacia que esta sonda saliera en rojo sola de vez en
            // cuando (visto el 05-09-2026, 1 de cada 4 corridas). El motivo es
            // que Millstone es una carta REAL del motor —se eligio asi a
            // proposito, ver AscentSeedDeck.CURSED_CARD— y un generador de
            // mazos incoloros puede meterla por su cuenta. Una prueba que falla
            // sola es peor que no tenerla: acostumbra a mirar el rojo y seguir.
            //
            // Lo que si es cierto siempre: con la Ascension puesta la lleva
            // SIEMPRE, y sin ella casi nunca. Se mide sobre una muestra.
            int sanasConLaCarta = 0;
            final int MUESTRA = 8;
            for (int i = 0; i < MUESTRA; i++) {
                final Deck otra = AscentSeedDeck.generate(
                        AscentRun.Mode.STANDARD, null, "sonda-maldicion-" + i, 0);
                if (hasCard(otra, "Millstone")) {
                    sanasConLaCarta++;
                }
            }
            final boolean laTieneSana = sanasConLaCarta > MUESTRA / 2;
            final int tamSana = size(deckSana);
            final int tamMaldita = size(deckMaldita);
            if (laTieneMaldita && !laTieneSana && tamSana == tamMaldita) {
                ok("ascension 3: el mazo sale con la carta maldita (Millstone), sin cambiar"
                        + " de tamano (" + tamMaldita + " cartas)");
            } else {
                fail("ascension 3: con Ascension la lleva=" + laTieneMaldita
                        + " (deberia ser true); sin Ascension sale en " + sanasConLaCarta
                        + " de " + MUESTRA + " mazos (deberian ser pocos); tamanos "
                        + tamSana + "/" + tamMaldita + " (deberian ser iguales)");
            }
        } finally {
            discard(sana);
            discard(maldita);
        }
    }

    /**
     * Ascension 7: una elite mas por acto, GARANTIZADA. Se compara contra la
     * Ascension 6 (que ya trae el adelanto de fila de la 1, acumulativo) para
     * aislar justo el efecto de la 7: subir el peso del sorteo solo movería
     * una MEDIA, y el jugador juega un mapa, no quinientos — por eso aqui se
     * exige que NUNCA salga con menos o igual.
     */
    private static void ascension7UnaEliteMas() {
        int totalSinForzar = 0;
        int totalForzado = 0;
        int vecesSinSubir = 0;
        final int muestras = 200;
        for (int i = 0; i < muestras; i++) {
            final int sinForzar = countKindAt(new AscentMap(9500L + i, 2, 6), AscentNode.Kind.ELITE, -1);
            final int forzado = countKindAt(new AscentMap(9500L + i, 2, 7), AscentNode.Kind.ELITE, -1);
            totalSinForzar += sinForzar;
            totalForzado += forzado;
            if (forzado <= sinForzar) {
                vecesSinSubir++;
            }
        }
        if (vecesSinSubir == 0) {
            ok(String.format(Locale.ROOT,
                    "ascension 7: una elite mas GARANTIZADA por acto (media %.2f -> %.2f)",
                    totalSinForzar / (double) muestras, totalForzado / (double) muestras));
        } else {
            fail("ascension 7: " + vecesSinSubir + " de " + muestras + " mapas no ganaron"
                    + " ninguna elite de mas — no esta garantizado");
        }
    }

    /** Ascension 8: se empieza al 90% del techo de vida, redondeado y con suelo de 1. */
    private static void ascension8EmpiezaMagullado() {
        final AscentRun normal = AscentRun.start(AscentRun.Mode.STANDARD, 0, 20, "sonda-8-normal");
        final int vidaNormal = normal.getLife();
        normal.discard();
        final AscentRun magullado = AscentRun.start(AscentRun.Mode.STANDARD, 8, 20, "sonda-8-magullado");
        final int vidaMagullada = magullado.getLife();
        magullado.discard();
        if (vidaNormal == 20 && vidaMagullada == 18) {
            ok("ascension 8: se empieza al 90% de la vida (" + vidaNormal + " -> "
                    + vidaMagullada + ")");
        } else {
            fail("ascension 8: vida al empezar sin Ascension=" + vidaNormal
                    + " (deberia ser 20), con Ascension 8=" + vidaMagullada
                    + " (deberia ser 18)");
        }
    }

    /**
     * Ascension 9: todo cuesta un 25% mas. Se mide sobre el servicio de
     * quitar carta porque es el UNICO precio del mostrador sin el ±15% de
     * jitter — asi la comparacion es exacta y no aproximada.
     */
    private static void ascension9TiendaMasCara() {
        final AscentRun barata = demoRun(AscentRun.Mode.STANDARD);
        final AscentRun cara = begin(AscentRun.Mode.STANDARD, 9);
        try {
            if (barata == null || cara == null) {
                fail("ascension 9: no se ha podido montar la run de prueba");
                return;
            }
            final int precioNormal = removePrice(barata);
            final int precioAscension9 = removePrice(cara);
            if (precioNormal > 0 && precioAscension9 == Math.round(precioNormal * 1.25f / 5) * 5) {
                ok("ascension 9: quitar una carta cuesta un 25% mas (" + precioNormal + " -> "
                        + precioAscension9 + ")");
            } else {
                fail("ascension 9: quitar carta cuesta " + precioNormal + " sin Ascension y "
                        + precioAscension9 + " con la 9 — no cuadra con un 25% mas");
            }
        } finally {
            discard(barata);
            discard(cara);
        }
    }

    /** Lo que cuesta quitar una carta en la tienda de la primera fila de este mapa. */
    private static int removePrice(final AscentRun run) {
        final AscentNode node = run.map().entries().get(0);
        for (final AscentShop.Item item : AscentShop.stock(run, node)) {
            if (item.getKind() == AscentShop.Kind.REMOVE) {
                return item.getPrice();
            }
        }
        return -1;
    }

    /** Igual que {@link #demoRun}, pero con una Ascension concreta. */
    private static AscentRun begin(final AscentRun.Mode mode, final int ascension) {
        try {
            return AscentRun.begin(mode, ascension, mode == AscentRun.Mode.COMMANDER ? 40 : 20, null);
        } catch (final RuntimeException e) {
            System.out.println("  [MAL]  no se ha podido empezar una run de Ascension "
                    + ascension + ": " + e);
            return null;
        }
    }

    /** {@code run.discard()}, aguantando un {@code null} de una run que no se pudo montar. */
    private static void discard(final AscentRun run) {
        if (run != null) {
            run.discard();
        }
    }

    /** Si esa carta esta en el mazo principal, por nombre. */
    private static boolean hasCard(final Deck deck, final String name) {
        if (deck == null) {
            return false;
        }
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cuantos nodos de ese tipo hay en el mapa. Con {@code row >= 0} cuenta
     * solo esa fila; con {@code -1}, el mapa entero.
     */
    private static int countKindAt(final AscentMap map, final AscentNode.Kind kind, final int row) {
        int n = 0;
        if (row >= 0) {
            for (final AscentNode node : map.row(row)) {
                if (node.getKind() == kind) {
                    n++;
                }
            }
            return n;
        }
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode node : map.row(r)) {
                if (node.getKind() == kind) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Un nodo de combate en esa fila, o el mas cercano por encima. */
    private static AscentNode combatAt(final AscentRun run, final int row) {
        final AscentMap map = run.map();
        for (int r = row; r < AscentMap.ROWS - 1; r++) {
            for (final AscentNode n : map.row(r)) {
                if (n.getKind() == AscentNode.Kind.COMBAT) {
                    return n;
                }
            }
        }
        return null;
    }

    /**
     * Que un jefe sea un jefe y una elite sea una elite.
     *
     * <p>Un jefe sin nada que lo distinga es <b>un rival normal con el doble de
     * vida</b>, y eso no da ningun error: la partida se juega, se gana y nadie
     * se entera de que la pelea de jefe no existio. Lo mismo con la reliquia de
     * la elite puesta en el asiento equivocado, que ademas te la REGALA.
     *
     * <p>Lo que le hace jefe ya <b>no son los esquemas</b> (se quitaron el
     * 03-09-2026: Archienemigo es de 3c1 y en 1c1 no se puede ganar — ver
     * {@code elJefeNoEsArchienemigo}), sino sus <b>dos reliquias</b>, el doble
     * de vida y un mazo un escalon por encima del de su piso.
     */
    private static void jefeYElite() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("jefe: no se ha podido montar la run de prueba");
            return;
        }
        try {
            final AscentNode boss = run.map().boss();
            final AscentBattle.Plan jefe = AscentBattle.plan(run, boss);
            final AscentNode combate = firstOfKind(run, AscentNode.Kind.COMBAT);
            final AscentBattle.Plan corriente = combate == null ? null
                    : AscentBattle.plan(run, combate);
            if (corriente != null && jefe.opponentLife > corriente.opponentLife) {
                ok("jefe: " + jefe.opponentLife + " vidas contra las "
                        + corriente.opponentLife + " de un combate de su piso");
            } else {
                fail("jefe: no tiene mas vida que un combate normal — seria un rival mas");
            }
            // El jefe del acto 1 lleva UNA; los de los actos 2 y 3, dos. Se
            // comprueban los tres seguidos: el numero por acto es justo lo que
            // se cambio el 05-09-2026 ("me destrozo, le toco una reliquia que le
            // daba 3 manas incoloros"), asi que mirar solo uno dejaria sin
            // comprobar la mitad de la regla.
            final int[] esperadas = {1, 2, 2};
            final List<String> mal = new ArrayList<>();
            for (int act = 1; act <= AscentRun.ACTS; act++) {
                final int llevan = AscentBattle.plan(run, run.map().boss()).opponentRelics.size();
                if (llevan != esperadas[act - 1]) {
                    mal.add("acto " + act + ": " + llevan + " en vez de " + esperadas[act - 1]);
                }
                if (act < AscentRun.ACTS && !run.nextAct()) {
                    break;
                }
            }
            if (mal.isEmpty()) {
                ok("jefe: lleva 1 reliquia en el acto 1 y 2 en los siguientes, y en SU asiento");
            } else {
                fail("jefe: reliquias por acto — " + mal);
            }
            if (jefe.yourRelics.isEmpty()) {
                ok("jefe: las reliquias del rival no se cuelan en tu asiento");
            } else {
                fail("jefe: al jugador le han salido " + jefe.yourRelics.size()
                        + " reliquias que no ha ganado");
            }

            // La elite se compara con un combate de SU MISMA ALTURA: contra uno
            // de mas abajo saldria mas dura por estar arriba, no por ser elite.
            //
            // ⚠️ Y se busca en VARIOS mapas, no en uno. Un acto trae ~2 elites
            // de 12 nodos, asi que hay mapas sin ninguna — y con un solo mapa
            // esta comprobacion salia en ROJO una corrida de cada tantas, sin
            // que nada estuviera mal. Una prueba que falla sola es peor que no
            // tenerla: acostumbra a mirar el rojo y seguir.
            AscentRun conElite = run;
            AscentNode elite = firstOfKind(run, AscentNode.Kind.ELITE);
            int mapasMirados = 1;
            int sinElite = elite == null ? 1 : 0;
            final List<AscentRun> descartar = new ArrayList<>();
            while (elite == null && mapasMirados < 20) {
                final AscentRun otra = demoRun(AscentRun.Mode.STANDARD);
                if (otra == null) {
                    break;
                }
                mapasMirados++;
                descartar.add(otra);
                elite = firstOfKind(otra, AscentNode.Kind.ELITE);
                if (elite == null) {
                    sinElite++;
                } else {
                    conElite = otra;
                }
            }
            if (elite == null) {
                fail("elite: 20 mapas seguidos sin una sola elite — eso no es mala suerte");
                for (final AscentRun r : descartar) {
                    r.discard();
                }
                return;
            }
            if (sinElite > 0) {
                ok("elite: encontrada al " + mapasMirados + "º mapa (" + sinElite
                        + " sin ninguna: un acto de 12 nodos puede quedarse sin elite)");
            }
            final AscentRun dueno = conElite;
            final AscentBattle.Plan pe = AscentBattle.plan(dueno, elite);
            final int normal = AscentBattle.lifeAt(dueno.getMaxLife(),
                    AscentBattle.progress(dueno.getAct(), elite.getRow()),
                    AscentBattle.playerEdge(dueno));
            if (pe.opponentLife > normal && pe.opponentRelics.size() == 1) {
                ok("elite: " + pe.opponentLife + " vidas contra las " + normal
                        + " de un combate a su misma altura, y una reliquia propia");
            } else {
                fail("elite: no se distingue de un combate de su altura (" + pe.opponentLife
                        + " frente a " + normal + ", " + pe.opponentRelics.size() + " reliquias)");
            }
            for (final AscentRun r : descartar) {
                r.discard();
            }
        } finally {
            run.discard();
        }
    }

    // ------------------------------------------------------------------
    //  2. Recorrer runs enteras
    // ------------------------------------------------------------------

    /**
     * Veinte runs de tres actos, de la primera fila al jefe.
     *
     * <p>Lo que se comprueba en cada paso es que <b>solo se puede ir a donde el
     * mapa deja ir</b>: el movimiento se pide con {@link AscentRun#clear} y se
     * exige que acepte los nodos accesibles y rechace el resto. Un mapa que
     * dejara saltar de cualquier nodo a cualquier otro seria un mapa sin
     * decisiones, que es de lo unico que vive el modo.
     */
    private static void recorrerRuns() {
        int completadas = 0;
        int nodos = 0;
        final Map<AscentNode.Kind, Integer> vistos = new LinkedHashMap<>();
        String porQue = null;

        for (int i = 0; i < RUNS && porQue == null; i++) {
            final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
            if (run == null) {
                porQue = "no se ha podido empezar la run " + i;
                break;
            }
            try {
                final Random rnd = new Random(i * 7919L);
                for (int act = 1; act <= AscentRun.ACTS; act++) {
                    while (!run.actCleared()) {
                        final List<AscentNode> opciones = run.available();
                        if (opciones.isEmpty()) {
                            porQue = "run " + i + " acto " + act
                                    + ": callejon sin salida despues de "
                                    + run.getCleared() + " nodos";
                            break;
                        }
                        final AscentNode elegido = opciones.get(rnd.nextInt(opciones.size()));
                        // Un nodo de otra rama tiene que estar prohibido: si se
                        // pudiera entrar, elegir camino no significaria nada.
                        final AscentNode lejano = farFrom(run, opciones);
                        if (lejano != null && run.canEnter(lejano)) {
                            porQue = "run " + i + ": se puede saltar a un nodo que no toca ("
                                    + lejano + ")";
                            break;
                        }
                        if (!run.clear(elegido)) {
                            porQue = "run " + i + ": el mapa no deja entrar en " + elegido;
                            break;
                        }
                        vistos.merge(elegido.getKind(), 1, Integer::sum);
                        nodos++;
                    }
                    if (porQue != null) {
                        break;
                    }
                    if (act < AscentRun.ACTS && !run.nextAct()) {
                        porQue = "run " + i + ": no se ha podido pasar al acto " + (act + 1);
                        break;
                    }
                }
                if (porQue == null && run.isCompleted()) {
                    completadas++;
                }
            } finally {
                run.discard();
            }
        }

        if (porQue != null) {
            fail("recorrido: " + porQue);
        } else if (completadas == RUNS) {
            ok("recorrido: " + RUNS + " runs de " + AscentRun.ACTS + " actos completadas ("
                    + nodos + " nodos, " + (nodos / RUNS) + " por run)");
        } else {
            fail("recorrido: solo " + completadas + "/" + RUNS + " runs llegaron al final");
        }

        // Que se vea de todo. Un mapa que solo diera combates seria un pasillo.
        final Set<AscentNode.Kind> faltan = new HashSet<>();
        for (final AscentNode.Kind k : AscentNode.Kind.values()) {
            if (!vistos.containsKey(k)) {
                faltan.add(k);
            }
        }
        if (faltan.isEmpty()) {
            ok("recorrido: han salido los siete tipos de nodo " + vistos);
        } else {
            fail("recorrido: no ha salido ni un nodo de " + faltan + " en " + RUNS + " runs");
        }
    }

    // ------------------------------------------------------------------
    //  3. Los premios
    // ------------------------------------------------------------------

    /**
     * Que lo que da un nodo se pueda jugar, no se repita y no se pueda repetir
     * la tirada saliendo del juego.
     */
    private static void premios() {
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            final AscentRun run = demoRun(mode);
            if (run == null) {
                fail("premios (" + mode + "): no se ha podido montar la run");
                continue;
            }
            try {
                final AscentNode node = firstOfKind(run, AscentNode.Kind.COMBAT);
                if (node == null) {
                    fail("premios (" + mode + "): el mapa no tiene combates");
                    continue;
                }
                final AscentRewards.Reward r = AscentRewards.of(run, node);
                final int esperadas = AscentRewards.choices(mode);
                if (r.cards.size() == esperadas && r.picks == AscentRun.cardBatch(mode)) {
                    ok("premios (" + mode + "): eliges " + r.picks + " de " + esperadas
                            + " cartas, y " + r.credits + " creditos");
                } else {
                    fail("premios (" + mode + "): " + r.cards.size() + " cartas ofrecidas y "
                            + r.picks + " elecciones; se esperaban " + esperadas + " y "
                            + AscentRun.cardBatch(mode));
                }

                // El mismo nodo, otra vez: tiene que dar EXACTAMENTE lo mismo.
                // Si no, salir del juego antes de elegir y volver seria tirar
                // los dados hasta que salga la carta que quieres.
                final AscentRewards.Reward otra = AscentRewards.of(run, node);
                if (sameCards(r.cards, otra.cards) && r.credits == otra.credits) {
                    ok("premios (" + mode + "): el premio de un nodo no cambia al volver a mirarlo");
                } else {
                    fail("premios (" + mode + "): el premio se sortea cada vez que se mira");
                }

                // Y que se puedan jugar: en Commander es una REGLA (identidad
                // del comandante) y una carta fuera de ella no cabe en el mazo.
                final forge.card.ColorSet colores = AscentRewards.colorsOf(run);
                final List<String> fuera = new ArrayList<>();
                for (final PaperCard c : r.cards) {
                    if (!c.getRules().getColorIdentity().hasNoColorsExcept(colores)) {
                        fuera.add(c.getName());
                    }
                }
                if (fuera.isEmpty()) {
                    ok("premios (" + mode + "): las " + r.cards.size()
                            + " cartas caben en el mazo (" + colores + ")");
                } else {
                    fail("premios (" + mode + "): cartas que no se pueden jugar: " + fuera);
                }

                // Y que la elegida entre en el mazo de verdad, en el disco.
                if (!r.cards.isEmpty()) {
                    final int antes = AscentDecks.load(run).getMain().countAll();
                    AscentRewards.take(run, r.cards.get(0));
                    final int despues = AscentDecks.load(run).getMain().countAll();
                    if (despues == antes + 1) {
                        ok("premios (" + mode + "): la carta elegida entra en el mazo guardado ("
                                + antes + " -> " + despues + ")");
                    } else {
                        fail("premios (" + mode + "): el mazo sigue con " + despues
                                + " cartas despues de coger una");
                    }
                }

                // La reliquia de una elite, y que no se repita una que ya llevas.
                //
                // ⚠️ Si el mapa de esta run no trae elite, esto NO se puede
                // saltar en silencio: el numero de comprobaciones bailaria de
                // una corrida a otra y un dia faltaria esta sin que nadie lo
                // notara. Se buscan actos hasta encontrar una.
                AscentNode elite = firstOfKind(run, AscentNode.Kind.ELITE);
                while (elite == null && run.nextAct()) {
                    elite = firstOfKind(run, AscentNode.Kind.ELITE);
                }
                if (elite == null) {
                    fail("premios (" + mode + "): ningun acto de esta run trae una elite,"
                            + " asi que su reliquia se queda sin comprobar");
                } else {
                    final AscentRewards.Reward pe = AscentRewards.of(run, elite);
                    if (pe.singleRelic() == null) {
                        fail("premios (" + mode + "): una elite no ha dado reliquia");
                    } else {
                        run.addRelic(pe.singleRelic());
                        boolean repetida = false;
                        for (int i = 0; i < 20; i++) {
                            final AscentRelic otraR = AscentRewards.relic(run,
                                    AscentRelic.Rarity.COMMON, new Random(i));
                            if (otraR != null && otraR.getId().equals(pe.singleRelic().getId())) {
                                repetida = true;
                                break;
                            }
                        }
                        if (repetida) {
                            fail("premios (" + mode + "): se ofrece otra vez una reliquia"
                                    + " que ya llevas (" + pe.singleRelic().getCardName() + ")");
                        } else {
                            ok("premios (" + mode + "): la elite da reliquia ("
                                    + pe.singleRelic().getCardName() + ") y no se repite despues");
                        }
                    }
                }
            } finally {
                run.discard();
            }
        }
    }

    /**
     * Que se puedan meter <b>tierras</b> en el mazo, y que ensanchen tus
     * colores en Estandar pero no en Commander.
     *
     * <h2>Por que hace falta comprobarlo</h2>
     *
     * <p>Reportado jugando (05-09-2026): el mazo crece nodo a nodo con hechizos
     * y la base de mana no crece nunca, asi que se empieza con 12 tierras de 30
     * y se acaba con 12 de 40. La forma de arreglarlo es cambiar la carta del
     * premio por tierras ({@code AscentRewards.takeLands}).
     *
     * <p>Y lo segundo es lo que <b>no puede fallar en silencio</b>: en Estandar
     * meter una Isla tiene que abrirte las cartas azules — es la mitad del
     * valor de la opcion, y decidido con el jugador — pero en <b>Commander no
     * puede pasar</b>, porque los colores los manda la identidad del comandante
     * y una carta fuera de ella deja el mazo <b>ilegal</b>: el motor lo
     * rechazaria al empezar el nodo siguiente, o sea una run perdida por una
     * eleccion que el juego te ofrecio.
     */
    private static void tierrasEnElPremio() {
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            final AscentRun run = demoRun(mode);
            if (run == null) {
                fail("tierras (" + mode + "): no se ha podido montar la run");
                continue;
            }
            try {
                final List<PaperCard> basicas = AscentRewards.basicLandsFor(run);
                final forge.card.ColorSet colores = AscentRewards.colorsOf(run);
                elMontonDeTierras(run, mode, colores);
                if (basicas.isEmpty()) {
                    fail("tierras (" + mode + "): no se ofrece ni una basica, asi que"
                            + " la base de mana no se puede arreglar en toda la run");
                    continue;
                }
                // Todas tienen que ser jugables: una basica de un color que el
                // mazo no juega es una carta muerta, y en Commander es ilegal.
                final List<String> fuera = new ArrayList<>();
                for (final PaperCard b : basicas) {
                    if (!b.getRules().getType().isBasicLand()
                            || !b.getRules().getColorIdentity().hasNoColorsExcept(colores)) {
                        fuera.add(b.getName());
                    }
                }
                if (fuera.isEmpty()) {
                    ok("tierras (" + mode + "): se ofrecen " + basicas.size()
                            + " basicas y todas son de tus colores (" + colores + ")");
                } else {
                    fail("tierras (" + mode + "): se ofrecen basicas que no puedes jugar: "
                            + fuera);
                }

                // Y que entren en el mazo de verdad, en el disco, de dos en dos.
                final int antes = AscentDecks.load(run).getMain().countAll();
                AscentRewards.takeLands(run, basicas.get(0));
                final int despues = AscentDecks.load(run).getMain().countAll();
                if (despues == antes + AscentRewards.LANDS_INSTEAD) {
                    ok("tierras (" + mode + "): coger la tierra del premio mete "
                            + AscentRewards.LANDS_INSTEAD + " en el mazo guardado ("
                            + antes + " -> " + despues + "); no cuesta ninguna de tus"
                            + " elecciones de carta");
                } else {
                    fail("tierras (" + mode + "): el mazo ha pasado de " + antes + " a "
                            + despues + " cartas, y deberian ser "
                            + AscentRewards.LANDS_INSTEAD + " mas");
                }

                // Lo que separa los dos modos: meter una basica de OTRO color.
                final PaperCard ajena = offColourBasic(colores);
                if (ajena == null) {
                    fail("tierras (" + mode + "): el mazo ya juega los cinco colores,"
                            + " asi que no se puede comprobar si ensancha");
                    continue;
                }
                AscentRewards.takeLands(run, ajena);
                final forge.card.ColorSet ahora = AscentRewards.colorsOf(run);
                final boolean ensancha = !ahora.hasNoColorsExcept(colores);
                if (mode == AscentRun.Mode.STANDARD && ensancha) {
                    ok("tierras (STANDARD): meter " + ajena.getName()
                            + " ensancha tus colores (" + colores + " -> " + ahora
                            + "), asi que ya te ofreceran cartas de ese color");
                } else if (mode == AscentRun.Mode.STANDARD) {
                    fail("tierras (STANDARD): meter " + ajena.getName() + " NO ensancha"
                            + " los colores (" + colores + "); el premio seguira dando"
                            + " solo los de salida y la tierra no sirve de nada");
                } else if (!ensancha) {
                    ok("tierras (COMMANDER): meter " + ajena.getName() + " NO cambia tus"
                            + " colores (" + ahora + "): los manda el comandante, y una"
                            + " carta fuera de su identidad dejaria el mazo ilegal");
                } else {
                    fail("tierras (COMMANDER): meter " + ajena.getName() + " ha ensanchado"
                            + " los colores a " + ahora + "; el premio ofreceria cartas"
                            + " fuera de la identidad del comandante y el mazo seria ilegal");
                }
            } finally {
                run.discard();
            }
        }
    }

    /**
     * Que el premio ensenye <b>tierras de verdad</b>, no solo basicas, y que
     * sean tantas como cosas te puedes llevar.
     *
     * <h2>Que se vigila, y por que no se ve jugando</h2>
     *
     * <p>El premio es un solo monton: {@code choices} conjuros <b>mas</b>
     * {@code cardBatch} tierras, y eliges {@code picks} de todo ello — <i>"seis
     * conjuros y dos tierras, y eliges dos"</i>, que pueden ser una dual y una
     * criatura. Tres cosas se pueden romper sin dar ningun error:
     *
     * <ul>
     *   <li><b>Que las tierras buenas no salgan nunca.</b> Un premio con solo
     *       basicas funciona perfectamente; solo es peor, y hacen falta varias
     *       runs para sospecharlo.
     *   <li><b>Que salgan mas segun subes.</b> Es lo que se pidio, y es una
     *       probabilidad: se mide sobre muchos nodos, no sobre uno.
     *   <li><b>El singleton de Commander.</b> Dos copias de una dual dejan el
     *       mazo <b>ilegal</b>: eso si mata la run, y lo decidiria una opcion
     *       que el propio juego te ofrecio.
     * </ul>
     */
    private static void elMontonDeTierras(final AscentRun run, final AscentRun.Mode mode,
                                          final forge.card.ColorSet colores) {
        final int slots = run.cardBatch();
        // Cuantas opciones distintas puede llegar a haber sin contar las
        // tierras buenas: una por color del mazo.
        final int posibles = AscentRewards.basicLandsFor(run).size();
        int nodos = 0;
        int conBuena = 0;
        int malCount = 0;
        int fuera = 0;
        int copiasMal = 0;
        final int[] buenasPorActo = new int[AscentRun.ACTS];
        final int[] huecosPorActo = new int[AscentRun.ACTS];
        for (int act = 1; act <= AscentRun.ACTS; act++) {
            // ⚠️ UN solo Random para los cuarenta nodos, no uno por nodo.
            //
            // Con `new Random(act * 1000 + i)` dentro del bucle, lo que se pide
            // es el PRIMER double de semillas consecutivas — y eso en Java esta
            // sesgadisimo (es la misma trampa que midio "cara 60 de 60" en la
            // apuesta del tahur, el plan de Ascenso). La primera version de esta
            // sonda dio 0 tierras buenas de 120 en Estandar, donde solo hay un
            // hueco por nodo y por tanto solo se miraba ese primer valor. En la
            // partida de verdad no pasa: para cuando `of()` llega a las
            // tierras, el sorteo de las cartas y los creditos ya ha consumido
            // media docena de tiradas.
            final Random rnd = new Random(act * 7919L);
            for (int i = 0; i < 40; i++) {
                final List<PaperCard> lands = AscentRewards.landsFor(run, act, rnd);
                nodos++;
                // ⚠️ NO se exige siempre `slots`. Un mazo MONOCOLOR en el acto 1
                // tiene una sola opcion de tierra que exista: su basica — una
                // dual necesita dos colores, y las raras no entran hasta el
                // acto 2. Ofrecer una ahi es lo correcto; repetir la misma
                // Montanya en los dos huecos seria ofrecer dos veces lo mismo.
                //
                // Lo que si se exige, y es lo que importa: nunca cero, nunca
                // mas de las que te puedes llevar, y los dos huecos llenos en
                // cuanto el mazo tenga colores para llenarlos.
                if (lands.size() < 1 || lands.size() > slots
                        || (posibles >= slots && lands.size() != slots)) {
                    malCount++;
                }
                huecosPorActo[act - 1] += lands.size();
                boolean buena = false;
                final java.util.Set<String> vistas = new HashSet<>();
                for (final PaperCard l : lands) {
                    if (!l.getRules().getType().isLand()
                            || !l.getRules().getColorIdentity().hasNoColorsExcept(colores)
                            || !vistas.add(l.getName())) {
                        fuera++;
                    }
                    if (!l.getRules().getType().isBasicLand()) {
                        buena = true;
                        buenasPorActo[act - 1]++;
                        // ⚠️ Lo que mata la run: en Commander una no basica es
                        // de UNA copia. Lo contesta el motor, no nosotros.
                        if (mode == AscentRun.Mode.COMMANDER
                                && AscentRewards.copiesOf(run, l) > 1) {
                            copiasMal++;
                        }
                    }
                }
                if (buena) {
                    conBuena++;
                }
            }
        }

        if (malCount == 0) {
            ok("monton (" + mode + "): cada nodo ofrece hasta " + slots + " tierra(s) — o sea "
                    + AscentRewards.choices(mode) + " conjuros + " + slots
                    + " tierras y eliges " + slots + " de todo el monton"
                    + (posibles >= slots ? ""
                    : " (este mazo es de " + posibles + " color(es), asi que en el acto 1"
                    + " puede quedarse en una: no hay mas tierras que existan)"));
        } else {
            fail("monton (" + mode + "): " + malCount + " de " + nodos + " nodos han ofrecido"
                    + " un numero de tierras que no toca (hasta " + slots + ", y "
                    + slots + " exactas con " + posibles + " colores en el mazo)");
        }
        if (fuera == 0) {
            ok("monton (" + mode + "): ninguna tierra ofrecida se sale de " + colores
                    + " ni se repite dentro del mismo nodo");
        } else {
            fail("monton (" + mode + "): " + fuera + " tierras ofrecidas fuera de " + colores
                    + " o repetidas");
        }
        if (conBuena > 0) {
            ok(String.format(Locale.ROOT, "monton (%s): salen duales/triples y monocolores buenas"
                    + " en el %.0f%% de los nodos, no solo basicas", mode,
                    100.0 * conBuena / nodos));
        } else {
            fail("monton (" + mode + "): en " + nodos + " nodos no ha salido ni una tierra que"
                    + " no fuera basica; una base de mana no se arregla solo con basicas");
        }
        // Y que suba: el acto 3 tiene que traer mas que el 1.
        if (buenasPorActo[2] > buenasPorActo[0]) {
            ok("monton (" + mode + "): y son mas comunes segun subes — " + buenasPorActo[0]
                    + " en el acto 1, " + buenasPorActo[1] + " en el 2, " + buenasPorActo[2]
                    + " en el 3 (de " + huecosPorActo[0] + " huecos por acto)");
        } else {
            fail("monton (" + mode + "): las tierras buenas NO se hacen mas comunes al subir ("
                    + buenasPorActo[0] + " / " + buenasPorActo[1] + " / " + buenasPorActo[2] + ")");
        }
        if (mode != AscentRun.Mode.COMMANDER) {
            return;
        }
        if (copiasMal == 0) {
            ok("monton (COMMANDER): ninguna tierra no basica entraria por duplicado — el"
                    + " formato es de una copia y dos dejarian el mazo ilegal");
        } else {
            fail("monton (COMMANDER): " + copiasMal + " tierras no basicas entrarian con 2"
                    + " copias; el mazo seria ilegal y la run se perderia por eso");
        }
    }

    /** Una basica de un color que el mazo NO juega, para la prueba de arriba. */
    private static PaperCard offColourBasic(final forge.card.ColorSet colores) {
        for (int i = 0; i < forge.card.MagicColor.WUBRG.length; i++) {
            if (colores.hasAnyColor(forge.card.MagicColor.WUBRG[i])) {
                continue;
            }
            final PaperCard b = FModel.getMagicDb().getCommonCards()
                    .getCard(forge.card.MagicColor.Constant.BASIC_LANDS.get(i));
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    /**
     * Que en Commander <b>todo lo que va de cartas vaya por dos</b>.
     *
     * <h2>Por que</h2>
     *
     * <p>El mazo de Commander es de 60 (§24.1) y eso, solo, hacia peor el
     * bucle: una carta de premio dentro de sesenta no se nota como dentro de
     * veinte. Decision del autor (05-09-2026): si el mazo es el doble, lo que se
     * mueve tiene que ser el doble — <b>2 de 6</b> en el premio y <b>dos</b> al
     * quitar, en el descanso y en la tienda.
     *
     * <h2>Y lo que se vigila de verdad</h2>
     *
     * <p>Que la <b>proporcion</b> siga siendo 1 de 3. Doblar solo lo que te
     * llevas sin doblar lo ofrecido daria 2 de 3, y eso no es elegir: es
     * descartar. Y que la tienda <b>cobre una sola vez</b> por las dos: cobrar
     * por carta dejaria el precio del mostrador mintiendo (principio 1).
     */
    private static void elDobleEnCommander() {
        final int estandar = AscentRun.cardBatch(AscentRun.Mode.STANDARD);
        final int commander = AscentRun.cardBatch(AscentRun.Mode.COMMANDER);
        if (estandar == 1 && commander == 2) {
            ok("doble: en Commander se mueven " + commander + " cartas de golpe, en Estandar "
                    + estandar);
        } else {
            fail("doble: la tanda de cartas es " + estandar + " en Estandar y " + commander
                    + " en Commander; se esperaba 1 y 2");
        }

        final int ofreceE = AscentRewards.choices(AscentRun.Mode.STANDARD);
        final int ofreceC = AscentRewards.choices(AscentRun.Mode.COMMANDER);
        if (ofreceE == AscentRewards.CHOICES * estandar
                && ofreceC == AscentRewards.CHOICES * commander
                && ofreceC / commander == ofreceE / estandar) {
            ok("doble: se ofrecen " + ofreceE + " y " + ofreceC + " cartas, o sea la MISMA"
                    + " proporcion (1 de " + AscentRewards.CHOICES + ") hecha dos veces");
        } else {
            fail("doble: " + ofreceE + " y " + ofreceC + " cartas ofrecidas — la proporcion"
                    + " no se mantiene, y con 2 de 3 elegir es casi descartar");
        }

        // Y la tienda: dos quitadas por UN solo cobro.
        final AscentRun run = demoRun(AscentRun.Mode.COMMANDER);
        if (run == null) {
            fail("doble: no se ha podido montar la run de Commander");
            return;
        }
        try {
            AscentNode node = firstOfKind(run, AscentNode.Kind.SHOP);
            if (node == null) {
                node = run.map().boss();
            }
            final AscentShop.Item servicio = AscentShop.stock(run, node).stream()
                    .filter(i -> i.getKind() == AscentShop.Kind.REMOVE).findFirst().orElse(null);
            if (servicio == null) {
                fail("doble: el mostrador de Commander no trae el servicio de quitar carta");
                return;
            }
            if (servicio.getRemovals() != commander) {
                fail("doble: la tienda de Commander quita " + servicio.getRemovals()
                        + " cartas y deberian ser " + commander);
                return;
            }
            run.addCredits(servicio.getPrice() * 3);
            final int bolsa = run.getCredits();
            final Deck deck = AscentDecks.load(run);
            final int antes = size(deck);
            final List<PaperCard> orden = AscentDecks.sortedByCost(deck);
            final boolean una = AscentShop.removeCard(run, servicio, orden.get(0));
            final int trasUna = run.getCredits();
            final boolean dos = AscentShop.removeCard(run, servicio,
                    AscentDecks.sortedByCost(AscentDecks.load(run)).get(0));
            final int despues = size(AscentDecks.load(run));
            if (una && dos && despues == antes - commander
                    && trasUna == bolsa - servicio.getPrice()
                    && run.getCredits() == trasUna) {
                ok("doble: la tienda de Commander quita " + commander + " cartas (" + antes
                        + " -> " + despues + ") y cobra UNA vez (" + servicio.getPrice() + ")");
            } else {
                fail("doble: quitadas " + una + "/" + dos + ", mazo " + antes + " -> " + despues
                        + ", creditos " + bolsa + " -> " + trasUna + " -> " + run.getCredits());
            }
            // Y ya no queda nada que quitar: el servicio esta servido.
            if (servicio.isSold() && !AscentShop.removeCard(run, servicio,
                    AscentDecks.sortedByCost(AscentDecks.load(run)).get(0))) {
                ok("doble: agotadas las dos, el servicio no quita una tercera gratis");
            } else {
                fail("doble: el servicio de quitar sigue quitando despues de sus " + commander);
            }
        } finally {
            run.discard();
        }
    }

    /**
     * Que a la IA <b>no</b> le toquen reliquias que le llenan la mano.
     *
     * <h2>Por que, y por que no se ve jugando</h2>
     *
     * <p>Una reliquia que hace robar es un premio estupendo para ti y un
     * problema en el asiento de la IA — no por potencia, por <b>tiempo</b>:
     * Forge evalua cada carta jugable en cada prioridad, asi que tres cartas de
     * mas en su mano son muchas mas ramas cada turno.
     *
     * <p><b>Medido</b> el 05-09-2026 con {@code -Dneo.ai.relics} sobre partidas
     * de 70 segundos: 17-18 turnos sin reliquias, <b>12-13</b> con
     * <i>Hourglass of Kings</i>. La mitad de ritmo, que jugando es la espera
     * larga en el turno del rival que se reporto como <i>"la pelea contra el
     * boss se lagueaba"</i>.
     *
     * <p>Y no se ve: la partida <b>funciona</b>, solo va lenta, y quien juega
     * lo achaca a la interfaz. (No lo era: la mesa llena va a 63 fps y en 95
     * segundos de partida solo hay 7 frames por encima de 50 ms.)
     */
    private static void reliquiasQueLaIaPuedeLlevar() {
        final List<AscentRelic> roban = new ArrayList<>();
        for (final AscentRelic r : AscentRelics.all()) {
            if (AscentRelics.growsHand(r)) {
                roban.add(r);
            }
        }
        if (roban.isEmpty()) {
            fail("ia: ninguna reliquia del catalogo hace robar, asi que growsHand() no"
                    + " esta reconociendo nada — o ha cambiado el texto de los scripts");
            return;
        }
        ok("ia: " + roban.size() + " reliquias del catalogo llenan la mano y no van al rival "
                + roban);

        // Y que de verdad no salgan en el asiento de la IA, en muchos nodos.
        final java.util.Set<String> prohibidas = new HashSet<>();
        for (final AscentRelic r : roban) {
            final PaperCard c = AscentRelics.cardOf(r);
            if (c != null) {
                prohibidas.add(c.getName());
            }
        }
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("ia: no se ha podido montar la run de prueba");
            return;
        }
        try {
            int miradas = 0;
            final List<String> coladas = new ArrayList<>();
            for (int act = 1; act <= AscentRun.ACTS; act++) {
                for (final AscentNode n : todosLosNodos(run)) {
                    if (!AscentBattle.isBattle(n.getKind())) {
                        continue;
                    }
                    for (final PaperCard c : AscentBattle.plan(run, n).opponentRelics) {
                        miradas++;
                        if (prohibidas.contains(c.getName())) {
                            coladas.add(c.getName());
                        }
                    }
                }
                if (act < AscentRun.ACTS) {
                    run.nextAct();
                }
            }
            if (miradas == 0) {
                fail("ia: ningun nodo de tres actos ha dado reliquia al rival — o las elites"
                        + " y los jefes se han quedado sin ellas");
            } else if (coladas.isEmpty()) {
                ok("ia: en " + miradas + " reliquias repartidas al rival por tres actos no se"
                        + " ha colado ninguna de las que llenan la mano");
            } else {
                fail("ia: se han colado " + coladas.size() + " reliquias de robar en el asiento"
                        + " del rival: " + coladas);
            }
            // Y que quede pozo de sobra: filtrar no puede dejar al jefe sin nada.
            if (AscentRelics.all().size() - roban.size() >= 20) {
                ok("ia: quedan " + (AscentRelics.all().size() - roban.size())
                        + " reliquias para el rival, de sobra para elites y jefes");
            } else {
                fail("ia: filtrar deja solo " + (AscentRelics.all().size() - roban.size())
                        + " reliquias para el rival; el jefe empezaria a repetir");
            }
        } finally {
            run.discard();
        }
    }

    /** Todos los nodos del mapa actual. */
    private static List<AscentNode> todosLosNodos(final AscentRun run) {
        final List<AscentNode> out = new ArrayList<>();
        for (int r = 0; r < AscentMap.ROWS; r++) {
            out.addAll(run.map().row(r));
        }
        return out;
    }

    /**
     * Que el premio <b>mejore de verdad</b> segun subes.
     *
     * <h2>Por que hace falta</h2>
     *
     * <p>Reportado jugando (05-09-2026): <i>"siento que practicamente en el
     * piso 1 hasta el boss no he mejorado apenas... en Isaac existe la chance de
     * que en la sala 1 te den un super item, aqui estamos dando cartas de
     * mierda"</i>. Y es lo mas dificil de ver desde dentro: un premio flojo
     * <b>funciona</b>. No revienta, no da error, solo hace que la run no se
     * sienta subir — y para notarlo hay que jugar varias.
     *
     * <p>Se mide sobre 300 cartas por altura, que es lo que hace falta para que
     * un 5% se distinga del ruido.
     */
    private static void calidadDelPremio() {
        final AscentRun run = demoRun(AscentRun.Mode.COMMANDER);
        if (run == null) {
            fail("calidad: no se ha podido montar la run");
            return;
        }
        try {
            final java.util.Set<String> changers = new HashSet<>();
            for (final String line : forge.util.FileUtil.readFile(
                    forge.localinstance.properties.ForgeConstants
                            .COMMANDER_BRACKET_GAMECHANGERS_FILE)) {
                final int hash = line.indexOf('#');
                final String name = (hash < 0 ? line : line.substring(0, hash)).trim();
                if (!name.isEmpty()) {
                    changers.add(name);
                }
            }

            final double[] alturas = {0.0, 0.5, 1.0};
            final int[] comunes = new int[alturas.length];
            final int[] miticas = new int[alturas.length];
            final int[] gordas = new int[alturas.length];
            final int[] total = new int[alturas.length];
            for (int a = 0; a < alturas.length; a++) {
                final Random rnd = new Random(31337L + a);
                for (int i = 0; i < 60; i++) {
                    for (final PaperCard c : AscentRewards.offer(run, alturas[a], rnd, 5)) {
                        total[a]++;
                        if (c.getRarity() == forge.card.CardRarity.Common) {
                            comunes[a]++;
                        }
                        if (c.getRarity() == forge.card.CardRarity.MythicRare) {
                            miticas[a]++;
                        }
                        if (changers.contains(c.getName())) {
                            gordas[a]++;
                        }
                    }
                }
            }

            // 1. El suelo: ni una comun, a ninguna altura.
            if (comunes[0] + comunes[1] + comunes[2] == 0) {
                ok("calidad: ni una comun en " + (total[0] + total[1] + total[2])
                        + " cartas ofrecidas — el suelo es infrecuente");
            } else {
                fail("calidad: han salido " + (comunes[0] + comunes[1] + comunes[2])
                        + " comunes; el suelo tenia que ser infrecuente");
            }

            // 2. La tirada de Isaac: en el PRIMER nodo ya puede salir una mitica.
            if (miticas[0] > 0) {
                ok(String.format(Locale.ROOT, "calidad: ya en el primer nodo sale mitica el"
                        + " %.1f%% de las veces — es la tirada de Isaac, sin ella los primeros"
                        + " premios no tienen nada en juego", 100.0 * miticas[0] / total[0]));
            } else {
                fail("calidad: en " + total[0] + " cartas del primer nodo no ha salido ni una"
                        + " mitica; abrir el premio al empezar no tiene nada en juego");
            }

            // 3. Y sube de verdad, no solo un poco.
            if (miticas[2] > miticas[0] && miticas[1] > miticas[0]) {
                ok(String.format(Locale.ROOT, "calidad: las miticas van del %.0f%% al %.0f%%"
                        + " al %.0f%% segun subes", 100.0 * miticas[0] / total[0],
                        100.0 * miticas[1] / total[1], 100.0 * miticas[2] / total[2]));
            } else {
                fail("calidad: las miticas no suben con la altura (" + miticas[0] + " / "
                        + miticas[1] + " / " + miticas[2] + " de " + total[0] + ")");
            }

            // 4. Los gamechangers: nada abajo, y de verdad arriba.
            if (gordas[0] == 0 && gordas[2] > 0) {
                ok(String.format(Locale.ROOT, "calidad: los gamechangers de Forge no salen abajo"
                        + " y son el %.0f%% arriba — el acto 3 no es el 1 con numeros mas"
                        + " grandes", 100.0 * gordas[2] / total[2]));
            } else {
                fail("calidad: gamechangers " + gordas[0] + " abajo y " + gordas[2]
                        + " arriba; se esperaba ninguno abajo y varios arriba");
            }

            // 5. Y la lista es la del MOTOR, no una nuestra.
            if (changers.size() > 20) {
                ok("calidad: la lista de gamechangers es la de Forge (" + changers.size()
                        + " cartas de res/lists/gamechangers.txt), asi que las que anyadan"
                        + " manyana entran solas");
            } else {
                fail("calidad: la lista de gamechangers de Forge ha venido con "
                        + changers.size() + " cartas; o ha cambiado de sitio o de formato");
            }
        } finally {
            run.discard();
        }
    }

    /**
     * Que en Commander <b>no se ofrezca lo que ya llevas</b>.
     *
     * <h2>Por que es grave y por que no se ve</h2>
     *
     * <p>Commander es de <b>una copia</b>. El premio no pregunta: mete la carta
     * y guarda. Asi que ofrecer dos veces la misma carta es ofrecer un boton que
     * deja el mazo <b>ilegal</b> — y el jugador no tiene forma de saberlo, porque
     * en la pantalla del premio las dos veces se ven igual.
     *
     * <p>El agujero estaba desde el principio; doblar el premio a 2 de 6 (§24.6)
     * solo multiplica por dos las ocasiones de tropezar con el. Se tapa en el
     * pozo ({@code poolFor} descarta lo que ya tienes) y aqui se mide.
     */
    private static void singletonEnCommander() {
        final AscentRun run = demoRun(AscentRun.Mode.COMMANDER);
        if (run == null) {
            fail("singleton: no se ha podido montar la run de Commander");
            return;
        }
        try {
            final Deck deck = AscentDecks.load(run);
            if (deck == null) {
                fail("singleton: la run no tiene mazo");
                return;
            }
            final Set<String> dentro = new HashSet<>();
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                dentro.add(e.getKey().getName());
            }
            // Una muestra grande de los tres actos: el pozo es de miles de
            // cartas y con tres tiradas no se demuestra nada.
            int repetidas = 0;
            int miradas = 0;
            for (int act = 1; act <= AscentRun.ACTS; act++) {
                final List<PaperCard> muestra =
                        AscentRewards.offer(run, act, new Random(act * 77L), 120);
                for (final PaperCard c : muestra) {
                    miradas++;
                    if (dentro.contains(c.getName())) {
                        repetidas++;
                    }
                }
            }
            if (repetidas == 0) {
                ok("singleton: en " + miradas + " cartas ofrecidas no sale ni una de las que"
                        + " ya llevas — dos copias dejarian el mazo ilegal");
            } else {
                fail("singleton: " + repetidas + " de " + miradas + " cartas ofrecidas ya estan"
                        + " en el mazo; cogerlas lo dejaria ilegal y la run se perderia");
            }

            // Y lo mismo con las tierras no basicas, que es donde mas duele:
            // una dual repetida es exactamente la carta que apetece coger.
            final List<PaperCard> lands = AscentRewards.landsFor(run, 3, new Random(9L));
            int mal = 0;
            for (final PaperCard l : lands) {
                if (!l.getRules().getType().isBasicLand()
                        && AscentRewards.copiesOf(run, l) != 1) {
                    mal++;
                }
            }
            if (mal == 0) {
                ok("singleton: y una tierra no basica entra de UNA en UNA (lo dice"
                        + " DeckFormat, no nosotros)");
            } else {
                fail("singleton: " + mal + " tierras no basicas entrarian con un numero de"
                        + " copias que Commander no permite");
            }
        } finally {
            run.discard();
        }
    }

    /**
     * Que con un comandante <b>multicolor</b> se ofrezcan cartas multicolor, no
     * solo monocolor dentro de su identidad.
     *
     * <h2>Por que se comprueba y no se supone</h2>
     *
     * <p>Preguntado por Ana (05-09-2026): <i>"si el comandante es multicolor,
     * ¿mete al pool las cartas que compartan color, o solo salen monocolor
     * dentro de la identidad?"</i>. Leyendo el codigo la respuesta es que si —
     * el filtro es {@code identidad.hasNoColorsExcept(permitidos)}, o sea
     * <b>subconjunto</b>, el mismo criterio con el que el motor decide si una
     * carta es legal en un mazo de Commander— pero eso es leerlo, no medirlo. Y
     * el fallo contrario no daria ningun error: un pozo lleno de monocolores es
     * un pozo <b>que funciona</b>, solo que aburrido, y nadie lo notaria hasta
     * llevar varias runs.
     *
     * <p>Se prueba con un comandante de <b>tres colores</b> a proposito: con dos
     * bastaria una carta hibrida para pasar por casualidad.
     */
    private static void comandanteMulticolor() {
        // Un comandante de tres colores del pozo de verdad.
        PaperCard tricolor = null;
        for (final PaperCard c : AscentSeedDeck.commanderPool()) {
            if (c.getRules() != null
                    && c.getRules().getColorIdentity().countColors() >= 3) {
                tricolor = c;
                break;
            }
        }
        if (tricolor == null) {
            fail("multicolor: no hay ni un comandante de tres colores en el pozo");
            return;
        }
        AscentRun run = null;
        try {
            run = AscentRun.begin(AscentRun.Mode.COMMANDER, 0, 40, tricolor);
        } catch (final RuntimeException e) {
            fail("multicolor: no se ha podido montar la run con " + tricolor.getName() + ": " + e);
            return;
        }
        try {
            final forge.card.ColorSet id = AscentRewards.colorsOf(run);
            if (id.countColors() < 3) {
                fail("multicolor: el comandante " + tricolor.getName() + " es de "
                        + tricolor.getRules().getColorIdentity() + " pero el pozo se calcula sobre "
                        + id + "; se estarian perdiendo colores de su identidad");
                return;
            }
            // Una muestra grande: las multicolor son minoria en cualquier acto,
            // asi que con tres cartas podria no salir ninguna por azar y la
            // comprobacion fallaria sola de vez en cuando.
            int multi = 0;
            int fuera = 0;
            final List<PaperCard> muestra =
                    AscentRewards.offer(run, 1, new Random(4242L), 60);
            for (final PaperCard c : muestra) {
                final forge.card.ColorSet suya = c.getRules().getColorIdentity();
                if (!suya.hasNoColorsExcept(id)) {
                    fuera++;
                }
                if (suya.countColors() >= 2) {
                    multi++;
                }
            }
            if (fuera > 0) {
                fail("multicolor: " + fuera + " de " + muestra.size() + " cartas ofrecidas se"
                        + " salen de la identidad " + id + "; el mazo seria ilegal");
            } else if (multi > 0) {
                ok("multicolor: con un comandante " + id + " (" + tricolor.getName() + "), "
                        + multi + " de " + muestra.size() + " cartas ofrecidas son multicolor"
                        + " — no solo monocolores de su identidad");
            } else {
                fail("multicolor: con un comandante " + id + " no ha salido ni una carta"
                        + " multicolor en " + muestra.size() + "; el pozo estaria dando solo"
                        + " monocolores y las runs de tres colores se parecerian todas");
            }
        } finally {
            run.discard();
        }
    }

    // ------------------------------------------------------------------
    //  4. Guardar, recargar y arrastrar la vida
    // ------------------------------------------------------------------

    /**
     * Que una run recargada sea <b>la misma</b>: mismo mapa, mismo sitio, misma
     * vida, mismas reliquias y mismo mazo.
     *
     * <p>El mapa no se guarda: se guardan la semilla y los nodos resueltos, y
     * se reconstruye. O sea que si el generador dejara de ser determinista, al
     * recargar saldria <b>otro mapa</b> con los nodos resueltos apuntando a
     * sitios que ya no son esos. No revienta: deja la run sin sentido.
     */
    private static void guardarYRecargar() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("guardado: no se ha podido montar la run");
            return;
        }
        // Avanzar unos cuantos nodos, perder vida y coger una reliquia.
        final Random rnd = new Random(1234L);
        for (int i = 0; i < 4 && !run.available().isEmpty(); i++) {
            final List<AscentNode> opciones = run.available();
            run.clear(opciones.get(rnd.nextInt(opciones.size())));
        }
        run.recordLife(run.getLife() - 7);
        run.addCredits(120);
        final List<AscentRelic> relics = AscentRelics.all();
        if (!relics.isEmpty()) {
            run.addRelic(relics.get(0));
        }

        final String mapaAntes = run.map().render();
        final int vidaAntes = run.getLife();
        final int nodosAntes = run.getCleared();
        final int cartasAntes = AscentDecks.load(run).getMain().countAll();
        final List<String> disponiblesAntes = keys(run.available());

        final AscentRun recargada = AscentRun.current();
        if (recargada == null) {
            fail("guardado: la run no se ha recuperado de neo.properties");
            run.discard();
            return;
        }
        final boolean mismoMapa = mapaAntes.equals(recargada.map().render());
        final boolean mismaVida = vidaAntes == recargada.getLife();
        final boolean mismosNodos = nodosAntes == recargada.getCleared();
        final boolean mismoSitio = disponiblesAntes.equals(keys(recargada.available()));
        final Deck mazo = AscentDecks.load(recargada);
        final boolean mismoMazo = mazo != null && mazo.getMain().countAll() == cartasAntes;
        final boolean mismasReliquias = run.relics().size() == recargada.relics().size();

        if (mismoMapa && mismaVida && mismosNodos && mismoSitio && mismoMazo && mismasReliquias) {
            ok("guardado: la run recargada es la misma (acto " + recargada.getAct()
                    + ", " + nodosAntes + " nodos, " + vidaAntes + " vidas, "
                    + cartasAntes + " cartas, " + recargada.relics().size() + " reliquias)");
        } else {
            fail("guardado: la run recargada NO coincide"
                    + (mismoMapa ? "" : " [otro mapa]")
                    + (mismaVida ? "" : " [otra vida]")
                    + (mismosNodos ? "" : " [otros nodos resueltos]")
                    + (mismoSitio ? "" : " [otro sitio en el mapa]")
                    + (mismoMazo ? "" : " [otro mazo]")
                    + (mismasReliquias ? "" : " [otras reliquias]"));
        }

        // La vida se arrastra hacia arriba y NO se cura sola al pasar de acto:
        // es lo que sostiene la tension del modo entero.
        final int antesDelActo = recargada.getLife();
        while (!recargada.actCleared() && !recargada.available().isEmpty()) {
            recargada.clear(recargada.available().get(0));
        }
        recargada.nextAct();
        if (recargada.getLife() == antesDelActo && recargada.getAct() == 2) {
            ok("guardado: al pasar de acto la vida se arrastra (" + antesDelActo
                    + ") y el mapa se renueva");
        } else {
            fail("guardado: al pasar de acto la vida ha cambiado sola: " + antesDelActo
                    + " -> " + recargada.getLife());
        }
        recargada.discard();
    }

    // ------------------------------------------------------------------
    //  5. Perder y abandonar
    // ------------------------------------------------------------------

    /**
     * Que perder se acabe de verdad y no deje rastro.
     *
     * <p>Una run perdida que se quedara guardada saldria en el menu como
     * "continuar", y continuar una partida que ya perdiste es justo lo que un
     * roguelike no puede permitir. Y su {@code .dck} tiene que irse con ella:
     * si no, en un mes la carpeta de Ascenso son cuarenta mazos muertos.
     */
    private static void perderYAbandonar() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("derrota: no se ha podido montar la run");
            return;
        }
        final String deckName = run.getDeckName();
        final boolean viva = run.recordLife(0);
        if (viva) {
            fail("derrota: con 0 vidas la run se da por viva");
        } else {
            ok("derrota: a 0 vidas la run se da por perdida");
        }
        run.discard();
        if (AscentRun.current() == null) {
            ok("derrota: no queda run guardada que continuar");
        } else {
            fail("derrota: la run perdida sigue guardada y saldria como 'continuar'");
        }
        if (AscentDecks.load(deckName) == null) {
            ok("derrota: el mazo de la run se borra con ella");
        } else {
            fail("derrota: el mazo '" + deckName + "' se queda en la carpeta de Ascenso");
        }

        // Y que una run nueva no herede nada de la anterior.
        final AscentRun otra = demoRun(AscentRun.Mode.STANDARD);
        if (otra == null) {
            fail("derrota: no se ha podido empezar una run despues de perder");
            return;
        }
        try {
            if (otra.getCleared() == 0 && otra.getAct() == 1 && otra.relics().isEmpty()
                    && otra.getCredits() == 0 && otra.getLife() == otra.getMaxLife()) {
                ok("derrota: la run siguiente empieza limpia (acto 1, vida llena, sin reliquias)");
            } else {
                fail("derrota: la run nueva ha heredado algo: " + otra);
            }
        } finally {
            otra.discard();
        }
    }

    // ------------------------------------------------------------------
    //  Fontaneria
    // ------------------------------------------------------------------

    /** Una run de prueba, con su mazo generado y guardado. */
    // ------------------------------------------------------------------
    //  La tienda
    // ------------------------------------------------------------------

    /**
     * Que se pueda comprar, que cobre lo que dice y que <b>no se pueda rerodar</b>.
     *
     * <p>Lo ultimo es lo que no se ve jugando y por eso se comprueba aqui: si
     * el escaparate se sorteara suelto en vez de con la clave del nodo, salir
     * del juego y volver daria otro — o sea tirar los dados hasta que salga la
     * carta que quieres, que es exactamente la trampa que el modo no puede
     * permitirse.
     */
    private static void tienda() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("tienda: no se ha podido montar la run de prueba");
            return;
        }
        try {
            AscentNode node = firstOfKind(run, AscentNode.Kind.SHOP);
            if (node == null) {
                node = run.map().boss();
            }
            final List<AscentShop.Item> stock = AscentShop.stock(run, node);

            // Hay mostrador, y trae de las tres cosas.
            int cartas = 0;
            int reliquias = 0;
            int borrados = 0;
            for (final AscentShop.Item i : stock) {
                if (i.getKind() == AscentShop.Kind.CARD) {
                    cartas++;
                } else if (i.getKind() == AscentShop.Kind.RELIC) {
                    reliquias++;
                } else {
                    borrados++;
                }
            }
            if (cartas == AscentShop.CARDS && reliquias == 1 && borrados == 1) {
                ok("tienda: el mostrador trae " + cartas + " cartas, " + reliquias
                        + " reliquia y el servicio de quitar carta");
            } else {
                fail("tienda: mostrador raro — " + cartas + " cartas, " + reliquias
                        + " reliquias, " + borrados + " borrados");
            }

            boolean precios = !stock.isEmpty();
            for (final AscentShop.Item i : stock) {
                if (i.getPrice() <= 0) {
                    precios = false;
                }
            }
            if (precios) {
                ok("tienda: todo tiene precio (" + stock.get(0).getPrice() + " el primero)");
            } else {
                fail("tienda: hay articulos a precio cero o negativo");
            }

            // ⚠️ El mismo nodo, otra vez: tiene que dar EXACTAMENTE lo mismo.
            final List<AscentShop.Item> otra = AscentShop.stock(run, node);
            boolean igual = otra.size() == stock.size();
            for (int i = 0; igual && i < stock.size(); i++) {
                igual = otra.get(i).getPrice() == stock.get(i).getPrice()
                        && String.valueOf(otra.get(i).getCard())
                                .equals(String.valueOf(stock.get(i).getCard()))
                        && String.valueOf(otra.get(i).getRelic())
                                .equals(String.valueOf(stock.get(i).getRelic()));
            }
            if (igual) {
                ok("tienda: el escaparate NO se puede rerodar — mismo nodo, mismo mostrador");
            } else {
                fail("tienda: el mismo nodo ha dado dos mostradores distintos");
            }

            // Y otro nodo tiene que dar otra cosa (si no, todas las tiendas de
            // la run serian la misma y elegir ruta daria igual).
            final AscentNode otroNodo = run.map().boss();
            if (!otroNodo.key().equals(node.key())) {
                final List<AscentShop.Item> tercera = AscentShop.stock(run, otroNodo);
                boolean distinto = false;
                for (int i = 0; i < Math.min(tercera.size(), stock.size()); i++) {
                    if (tercera.get(i).getPrice() != stock.get(i).getPrice()
                            || !String.valueOf(tercera.get(i).getCard())
                                    .equals(String.valueOf(stock.get(i).getCard()))) {
                        distinto = true;
                    }
                }
                if (distinto) {
                    ok("tienda: otro nodo da otro mostrador");
                } else {
                    fail("tienda: dos nodos distintos dan el MISMO mostrador");
                }
            }

            // Sin dinero no se compra, y no se toca nada.
            final AscentShop.Item carta = stock.stream()
                    .filter(i -> i.getKind() == AscentShop.Kind.CARD).findFirst().orElse(null);
            if (carta == null) {
                fail("tienda: no hay ninguna carta en el mostrador");
                return;
            }
            final int antes = size(AscentDecks.load(run));
            if (!AscentShop.buy(run, carta) && run.getCredits() == 0
                    && size(AscentDecks.load(run)) == antes) {
                ok("tienda: sin creditos no se compra, y el mazo se queda como estaba");
            } else {
                fail("tienda: ha dejado comprar sin creditos (o ha tocado el mazo)");
            }

            // Con dinero: cobra lo que dice y la carta entra.
            run.addCredits(carta.getPrice() + 25);
            final int bolsa = run.getCredits();
            final boolean comprada = AscentShop.buy(run, carta);
            final int ahora = size(AscentDecks.load(run));
            if (comprada && run.getCredits() == bolsa - carta.getPrice() && ahora == antes + 1) {
                ok("tienda: comprar cobra " + carta.getPrice() + " y mete la carta ("
                        + antes + " -> " + ahora + ")");
            } else {
                fail("tienda: comprada=" + comprada + ", creditos " + bolsa + " -> "
                        + run.getCredits() + ", mazo " + antes + " -> " + ahora);
            }

            // Y no se compra dos veces.
            final int bolsa2 = run.getCredits();
            if (!AscentShop.buy(run, carta) && run.getCredits() == bolsa2) {
                ok("tienda: lo vendido no se vuelve a vender");
            } else {
                fail("tienda: se ha podido comprar dos veces el mismo articulo");
            }

            // Quitar carta: cobra y el mazo baja en una.
            final AscentShop.Item borrado = stock.stream()
                    .filter(i -> i.getKind() == AscentShop.Kind.REMOVE).findFirst().orElse(null);
            final Deck deck = AscentDecks.load(run);
            if (borrado != null && deck != null && !deck.getMain().isEmpty()) {
                run.addCredits(borrado.getPrice());
                final int bolsa3 = run.getCredits();
                final int cartas3 = size(deck);
                final PaperCard victima = AscentDecks.sortedByCost(deck).get(0);
                final boolean quitada = AscentShop.removeCard(run, borrado, victima);
                final int despues = size(AscentDecks.load(run));
                if (quitada && despues == cartas3 - 1
                        && run.getCredits() == bolsa3 - borrado.getPrice()) {
                    ok("tienda: quitar carta cobra " + borrado.getPrice() + " y el mazo baja ("
                            + cartas3 + " -> " + despues + ")");
                } else {
                    fail("tienda: quitada=" + quitada + ", mazo " + cartas3 + " -> " + despues
                            + ", creditos " + bolsa3 + " -> " + run.getCredits());
                }
            }

            // ⚠️ El suelo del mazo. Un mazo vacio no arranca partida, o sea que
            // dejar quitar sin freno seria perder la run por una compra.
            final Deck flaco = AscentDecks.load(run);
            if (flaco != null) {
                while (flaco.getMain().countAll() > AscentShop.MIN_DECK) {
                    flaco.getMain().remove(AscentDecks.sortedByCost(flaco).get(0));
                }
                AscentDecks.save(flaco);
                final List<AscentShop.Item> otroStock = AscentShop.stock(run, node);
                final AscentShop.Item servicio = otroStock.stream()
                        .filter(i -> i.getKind() == AscentShop.Kind.REMOVE).findFirst()
                        .orElse(null);
                run.addCredits(1000);
                final PaperCard victima = AscentDecks.sortedByCost(flaco).get(0);
                if (!AscentShop.canRemove(run)
                        && !AscentShop.removeCard(run, servicio, victima)) {
                    ok("tienda: con el mazo en el suelo (" + AscentShop.MIN_DECK
                            + ") ya no deja quitar mas");
                } else {
                    fail("tienda: ha dejado quitar carta por debajo del suelo de "
                            + AscentShop.MIN_DECK);
                }
            }
        } finally {
            run.discard();
        }
    }

    // ------------------------------------------------------------------
    //  Los eventos
    // ------------------------------------------------------------------

    /**
     * Que ninguno pueda dejar la run atascada, muerta ni rota.
     *
     * <p>Un evento es una lista de botones sobre una run que no se puede
     * rebobinar, asi que lo que se comprueba no es que "funcione" sino las
     * cuatro cosas que lo convertirian en un fallo sin arreglo: que siempre se
     * pueda salir, que no pueda matar, que el texto exista de verdad y que no
     * se pueda volver a tirar saliendo del juego.
     */
    private static void eventos() {
        final List<AscentEvent> todos = AscentEvent.all();
        if (todos.isEmpty()) {
            fail("eventos: no hay ni uno");
            return;
        }
        ok("eventos: hay " + todos.size() + " en el catalogo");

        // 1. TODOS tienen salida sin coste, y esa salida nunca se bloquea. Sin
        //    esto, un evento con todas las opciones sin fondos seria un nodo
        //    del que no se puede salir: la run se queda atascada para siempre.
        final AscentRun pobre = demoRun(AscentRun.Mode.STANDARD);
        if (pobre == null) {
            fail("eventos: no se ha podido montar la run de prueba");
            return;
        }
        try {
            pobre.recordLife(1);
            boolean todosSalen = true;
            boolean textos = true;
            for (final AscentEvent ev : todos) {
                boolean alguna = false;
                for (final AscentEvent.Choice c : ev.getChoices()) {
                    if (c.isAvailable(pobre)) {
                        alguna = true;
                    }
                }
                if (!alguna) {
                    fail("eventos: " + ev.getId() + " no deja NINGUNA opcion con 1 de vida"
                            + " y 0 creditos");
                    todosSalen = false;
                }
                // 2. Y que los textos existan. Si falta una clave se ve la
                //    clave en pantalla, que es peor que un error.
                if (!hasText(ev.getTitleKey()) || !hasText(ev.getTextKey())) {
                    fail("eventos: a " + ev.getId() + " le falta titulo o texto");
                    textos = false;
                }
                for (final AscentEvent.Choice c : ev.getChoices()) {
                    if (!hasText(c.getLabelKey())) {
                        fail("eventos: falta el texto de una opcion de " + ev.getId()
                                + " (" + c.getLabelKey() + ")");
                        textos = false;
                    }
                    if (c.getBlockedKey() != null && !hasText(c.getBlockedKey())) {
                        fail("eventos: falta el motivo de bloqueo " + c.getBlockedKey());
                        textos = false;
                    }
                }
            }
            if (todosSalen) {
                ok("eventos: los " + todos.size() + " dejan salir con 1 de vida y sin creditos"
                        + " (ninguno atasca la run)");
            }
            if (textos) {
                ok("eventos: todos tienen titulo, texto y opciones con su clave");
            }

            // 3. Ninguno puede MATAR. Acabar una run de cuarenta minutos en un
            //    menu, sin jugar una carta, es el peor final posible.
            boolean matan = false;
            final Random rnd = new Random(11);
            for (final AscentEvent ev : todos) {
                for (final AscentEvent.Choice c : ev.getChoices()) {
                    final AscentRun r = demoRun(AscentRun.Mode.STANDARD);
                    if (r == null) {
                        continue;
                    }
                    try {
                        r.recordLife(1);
                        r.addCredits(500);
                        if (c.isAvailable(r)) {
                            c.apply(r, rnd);
                        }
                        if (r.getLife() <= 0) {
                            fail("eventos: " + ev.getId() + " puede MATAR (" + c.getLabelKey()
                                    + ")");
                            matan = true;
                        }
                    } finally {
                        r.discard();
                    }
                }
            }
            if (!matan) {
                ok("eventos: ninguna opcion de ninguno puede matar (hurt deja en 1)");
            }
        } finally {
            pobre.discard();
        }

        // 4. El evento de un nodo no se puede volver a tirar.
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            return;
        }
        try {
            AscentNode node = firstOfKind(run, AscentNode.Kind.EVENT);
            if (node == null) {
                node = run.map().boss();
            }
            final AscentEvent a = AscentEvent.of(run, node);
            final AscentEvent b = AscentEvent.of(run, node);
            if (a == b) {
                ok("eventos: el mismo nodo da SIEMPRE el mismo evento (" + a.getId() + ")");
            } else {
                fail("eventos: el mismo nodo ha dado " + a.getId() + " y luego " + b.getId());
            }
            // Y que no salga siempre el mismo en toda la run, que seria lo
            // contrario: el mapa lleno de copias del mismo encuentro.
            final Set<String> vistos = new HashSet<>();
            for (int r = 0; r < AscentMap.ROWS; r++) {
                for (final AscentNode n : run.map().row(r)) {
                    vistos.add(AscentEvent.of(run, n).getId());
                }
            }
            if (vistos.size() > 1) {
                ok("eventos: por el mapa salen " + vistos.size() + " distintos");
            } else {
                fail("eventos: el mapa entero da el mismo evento");
            }

            // 5. Los efectos hacen lo que dicen. Se comprueba el mas peligroso
            //    de los dos sentidos: que cobrar cobre y que pagar pague.
            run.recordLife(run.getMaxLife());
            run.addCredits(1000);
            final int vidaAntes = run.getLife();
            final int bolsaAntes = run.getCredits();
            final AscentEvent cofre = AscentEvent.byId("chest");
            if (cofre != null) {
                final AscentEvent.Choice forzar = cofre.getChoices().get(0);
                forzar.apply(run, new Random(3));
                if (run.getCredits() > bolsaAntes && run.getLife() < vidaAntes) {
                    ok("eventos: el cofre da creditos Y quita vida, como dice ("
                            + bolsaAntes + " -> " + run.getCredits() + " creditos, "
                            + vidaAntes + " -> " + run.getLife() + " vidas)");
                } else {
                    fail("eventos: el cofre no ha hecho lo que dice — creditos " + bolsaAntes
                            + " -> " + run.getCredits() + ", vida " + vidaAntes + " -> "
                            + run.getLife());
                }
            }

            // 6. La fuente sube el TECHO, que es el unico premio que no se
            //    gasta. Y curar de mas no puede pasarse del nuevo techo.
            final AscentEvent fuente = AscentEvent.byId("fountain");
            if (fuente != null) {
                run.addCredits(1000);
                final int techoAntes = run.getMaxLife();
                fuente.getChoices().get(0).apply(run, new Random(5));
                if (run.getMaxLife() > techoAntes && run.getLife() <= run.getMaxLife()) {
                    ok("eventos: la fuente sube el techo de vida (" + techoAntes + " -> "
                            + run.getMaxLife() + ") y la vida no se pasa de el");
                } else {
                    fail("eventos: la fuente ha dejado el techo en " + run.getMaxLife()
                            + " y la vida en " + run.getLife());
                }
            }

            // 7. La apuesta sale a las dos: si sale siempre igual, no es una
            //    apuesta. Se tira muchas veces con semillas distintas.
            final AscentEvent tahur = AscentEvent.byId("gambler");
            if (tahur != null) {
                int gana = 0;
                for (int i = 0; i < 60; i++) {
                    final AscentRun r = demoRun(AscentRun.Mode.STANDARD);
                    if (r == null) {
                        continue;
                    }
                    try {
                        r.addCredits(500);
                        final int antes = r.getCredits();
                        tahur.getChoices().get(0).apply(r, new Random(i));
                        if (r.getCredits() > antes) {
                            gana++;
                        }
                    } finally {
                        r.discard();
                    }
                }
                if (gana > 5 && gana < 55) {
                    ok("eventos: la apuesta del tahur sale a las dos (" + gana + " de 60)");
                } else {
                    fail("eventos: la apuesta ha salido " + gana + " de 60 — no es mitad y mitad");
                }
            }
        } finally {
            run.discard();
        }
    }

    // ------------------------------------------------------------------
    //  El plano de cada acto
    // ------------------------------------------------------------------

    /**
     * Que cada acto tenga su sitio, que sea SIEMPRE el mismo y que los tres no
     * se repitan.
     *
     * <p>Lo tercero es lo unico que aporta: si el acto 2 saliera en el mismo
     * plano que el 1, avanzar no se notaria — que es justo lo que el nombre
     * del plano viene a arreglar.
     */
    private static void planos() {
        final List<PaperCard> pool = AscentPlanes.all();
        if (pool.size() > 50) {
            ok("planos: " + pool.size() + " sitios en el catalogo (sin fenomenos)");
        } else {
            fail("planos: solo " + pool.size() + " en el catalogo");
        }
        boolean soloPlanos = true;
        for (final PaperCard c : pool) {
            if (c.getRules() == null || !c.getRules().getType().isPlane()) {
                soloPlanos = false;
            }
        }
        if (soloPlanos) {
            ok("planos: todos son planos de verdad (un fenomeno no es un lugar)");
        } else {
            fail("planos: se ha colado algo que no es un plano");
        }

        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("planos: no se ha podido montar la run de prueba");
            return;
        }
        try {
            final PaperCard a = AscentPlanes.of(run, 1);
            final PaperCard b = AscentPlanes.of(run, 1);
            if (a != null && a == b) {
                ok("planos: el acto de una run da SIEMPRE el mismo sitio (" + a.getName() + ")");
            } else {
                fail("planos: el mismo acto ha dado dos sitios distintos");
            }
            final List<PaperCard> tres = AscentPlanes.forRun(run);
            final Set<String> nombres = new HashSet<>();
            for (final PaperCard c : tres) {
                if (c != null) {
                    nombres.add(c.getName());
                }
            }
            if (tres.size() == AscentRun.ACTS && nombres.size() == AscentRun.ACTS) {
                ok("planos: los " + AscentRun.ACTS + " actos dan " + nombres.size()
                        + " sitios distintos " + nombres);
            } else {
                fail("planos: " + tres.size() + " actos y solo " + nombres.size()
                        + " sitios distintos");
            }
            // Y dos runs distintas no viajan al mismo sitio (si no, "tu run"
            // seria siempre el mismo viaje con otro mapa).
            final AscentRun otra = demoRun(AscentRun.Mode.STANDARD);
            if (otra != null) {
                try {
                    final PaperCard c = AscentPlanes.of(otra, 1);
                    if (c != null && a != null && !c.getName().equals(a.getName())) {
                        ok("planos: otra run empieza en otro sitio (" + c.getName() + ")");
                    } else {
                        // No es un fallo: con 100+ planos coincidir es raro pero
                        // posible, y fallar por eso seria una prueba que se cae sola.
                        ok("planos: las dos runs han coincidido de sitio (pasa, hay "
                                + pool.size() + " y se sortean)");
                    }
                } finally {
                    otra.discard();
                }
            }
        } finally {
            run.discard();
        }
    }

    /**
     * Si esa clave de texto existe de verdad.
     *
     * <p>{@code NeoText.get} devuelve <b>la clave</b> cuando falta — a
     * proposito, para que un hueco se VEA en pantalla en vez de salir vacio —
     * y eso es justo lo que permite comprobarlo desde fuera sin ampliar
     * NeoText por una prueba.
     */
    private static boolean hasText(final String key) {
        return key != null && !NeoText.get(key).equals(key);
    }

    // ------------------------------------------------------------------
    //  El resumen del final
    // ------------------------------------------------------------------

    /**
     * Que la foto se pueda hacer, y que <b>sobreviva a borrar la run</b>.
     *
     * <p>Es todo el motivo de que {@link AscentSummary} exista: la run se borra
     * en cuanto termina —tiene que borrarse ahi— y {@code discard()} se lleva
     * el {@code .dck} entero. Si el resumen leyera de la run en vez de de la
     * foto, la pantalla del final saldria vacia <b>y sin dar ningun error</b>:
     * el fallo mas silencioso posible, justo en la pantalla que decide si hay
     * otra partida.
     */
    private static void resumen() {
        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("resumen: no se ha podido montar la run de prueba");
            return;
        }
        run.recordLife(7);
        run.addCredits(120);
        final AscentNode primero = run.available().isEmpty() ? null : run.available().get(0);
        if (primero != null) {
            run.clear(primero);
        }
        final AscentRelic relic = AscentRelics.all().isEmpty() ? null : AscentRelics.all().get(0);
        if (relic != null) {
            run.addRelic(relic);
        }
        final int cartas = size(AscentDecks.load(run));

        final AscentSummary foto = AscentSummary.of(run, false, false);
        if (foto.getDeck().size() == cartas && cartas > 0) {
            ok("resumen: la foto trae el mazo entero (" + cartas + " cartas)");
        } else {
            fail("resumen: la foto trae " + foto.getDeck().size() + " cartas y el mazo tenia "
                    + cartas);
        }
        if (foto.getLife() == 7 && foto.getCredits() == run.getCredits()
                && foto.getCleared() == run.getCleared()
                && foto.getRelics().size() == run.relics().size()) {
            ok("resumen: y los numeros de la run (vida, creditos, nodos y reliquias)");
        } else {
            fail("resumen: los numeros no cuadran — " + foto);
        }

        // El mazo, ordenado de mas caro a mas barato: es el orden de TODAS las
        // vistas del mazo, y si aqui saliera otro, la carta que el jugador ya
        // sabe donde esta cambiaria de sitio.
        boolean ordenado = true;
        for (int i = 1; i < foto.getDeck().size(); i++) {
            final PaperCard a = foto.getDeck().get(i - 1);
            final PaperCard b = foto.getDeck().get(i);
            if (a.getRules() != null && b.getRules() != null
                    && a.getRules().getManaCost().getCMC() < b.getRules().getManaCost().getCMC()) {
                ordenado = false;
            }
        }
        if (ordenado) {
            ok("resumen: el mazo viene de mas caro a mas barato");
        } else {
            fail("resumen: el mazo no viene ordenado por coste");
        }

        // Y AHORA se borra la run: la foto tiene que seguir entera.
        run.discard();
        if (foto.getDeck().size() == cartas && foto.getLife() == 7
                && foto.getRelics().size() == (relic == null ? 0 : 1)) {
            ok("resumen: la foto SOBREVIVE a borrar la run (que es para lo que existe)");
        } else {
            fail("resumen: al borrar la run la foto se ha quedado en " + foto);
        }
        if (AscentRun.current() == null) {
            ok("resumen: y la run se ha borrado de verdad, no queda nada que continuar");
        } else {
            fail("resumen: la run sigue guardada despues de discard()");
        }
    }

    /** Cuantas cartas tiene el principal de ese mazo. */
    private static int size(final Deck deck) {
        return deck == null ? 0 : deck.getMain().countAll();
    }

    private static AscentRun demoRun(final AscentRun.Mode mode) {
        try {
            return AscentRun.begin(mode, 0, mode == AscentRun.Mode.COMMANDER ? 40 : 20, null);
        } catch (final RuntimeException e) {
            System.out.println("  [MAL]  no se ha podido empezar una run: " + e);
            return null;
        }
    }

    /** El primer nodo de ese tipo del mapa actual, o {@code null}. */
    private static AscentNode firstOfKind(final AscentRun run, final AscentNode.Kind kind) {
        final AscentMap map = run.map();
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode n : map.row(r)) {
                if (n.getKind() == kind) {
                    return n;
                }
            }
        }
        return null;
    }

    /** Un nodo del mapa que NO este entre los accesibles, para probar que no deja entrar. */
    private static AscentNode farFrom(final AscentRun run, final List<AscentNode> allowed) {
        final Set<String> ok = new HashSet<>(keys(allowed));
        final AscentMap map = run.map();
        for (int r = AscentMap.ROWS - 1; r >= 0; r--) {
            for (final AscentNode n : map.row(r)) {
                if (!ok.contains(n.key()) && !n.isCleared()) {
                    return n;
                }
            }
        }
        return null;
    }

    private static List<String> keys(final List<AscentNode> nodes) {
        final List<String> out = new ArrayList<>();
        for (final AscentNode n : nodes) {
            out.add(n.key());
        }
        return out;
    }

    private static boolean sameCards(final List<PaperCard> a, final List<PaperCard> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getName().equals(b.get(i).getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * La potencia media del escalon {@code tier} del pozo.
     *
     * <p>Los limites los da {@link AscentBattle#tierRange}, no se recalculan
     * aqui: medir con un reparto distinto del que usa el juego seria medir otra
     * cosa — y daria por buena una curva que en la partida no existe.
     */
    private static double averagePower(final List<Deck> pool, final int tier) {
        final int[] range = AscentBattle.tierRange(pool.size(), tier);
        double sum = 0;
        for (int i = range[0]; i < range[1]; i++) {
            sum += AscentBattle.power(pool.get(i));
        }
        return sum / (range[1] - range[0]);
    }

    // ------------------------------------------------------------------
    //  Los hitos (el plan de Ascenso)
    // ------------------------------------------------------------------

    /**
     * Que los desbloqueos por hitos hagan lo que dicen.
     *
     * <p>Aqui hay <b>tres</b> fallos posibles y ninguno da error ni se ve
     * jugando, que es exactamente por lo que esto existe:
     *
     * <ol>
     *   <li>Que algo bloqueado <b>salga igual</b>. El jugador no puede notarlo:
     *       no sabe que no deberia estar viendolo.</li>
     *   <li>Que un hito no salte nunca — o que salte siempre. Lo primero deja
     *       contenido enterrado para siempre; lo segundo lo regala.</li>
     *   <li>Que los hitos se vayan con la derrota. Es el mismo fallo del que
     *       nacio {@link AscentUnlocks}: sin error, sin aviso, y el modo deja
     *       de progresar.</li>
     * </ol>
     */
    private static void hitos() {
        // 1. Sin ningun hito, nada bloqueado puede aparecer. Y se mira por el
        //    camino de verdad (AscentEvent.of sobre el mapa entero), no
        //    llamando a pool() y dandolo por bueno: el fallo que esto caza es
        //    justo que un sorteo lea el catalogo entero por su cuenta.
        AscentUnlocks.grantForTest();
        final List<AscentEvent> sinHitos = AscentEvent.pool();
        final List<AscentEvent> todos = AscentEvent.all();
        final int bloqueados = todos.size() - sinHitos.size();
        if (bloqueados <= 0) {
            fail("hitos: no hay ni un evento detras de un hito — el sistema no abre nada");
        } else {
            ok("hitos: de " + todos.size() + " eventos, " + bloqueados
                    + " estan detras de un hito y no salen sin el");
        }

        final AscentRun run = demoRun(AscentRun.Mode.STANDARD);
        if (run == null) {
            fail("hitos: no se ha podido montar la run de prueba");
            return;
        }
        boolean colado = false;
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode n : run.map().row(r)) {
                if (AscentEvent.of(run, n).getGate() != null) {
                    colado = true;
                }
            }
        }
        if (colado) {
            fail("hitos: por el mapa sale un evento que todavia no esta desbloqueado");
        } else {
            ok("hitos: sin ningun hito, el mapa entero no saca ni un evento bloqueado");
        }

        // 2. Con todos los hitos, el pozo es el catalogo entero. Lo contrario
        //    seria contenido inalcanzable: un hito que promete algo que nunca
        //    llega.
        AscentUnlocks.grantForTest(AscentFeat.values());
        if (AscentEvent.pool().size() == todos.size()) {
            ok("hitos: con todos conseguidos salen los " + todos.size() + " eventos");
        } else {
            fail("hitos: con todos conseguidos siguen faltando "
                    + (todos.size() - AscentEvent.pool().size()) + " eventos");
        }

        // 3. Ningun hito puede quedarse sin abrir nada: seria una promesa
        //    vacia, y el resumen la anunciaria igual.
        final Set<AscentFeat> abren = new HashSet<>();
        for (final AscentEvent e : todos) {
            if (e.getGate() != null) {
                abren.add(e.getGate());
            }
        }
        final List<String> vacios = new ArrayList<>();
        for (final AscentFeat f : AscentFeat.values()) {
            if (!abren.contains(f)) {
                vacios.add(f.getId());
            }
        }
        if (vacios.isEmpty()) {
            ok("hitos: los " + AscentFeat.values().length + " abren algo de verdad");
        } else {
            fail("hitos: no abren nada: " + String.join(", ", vacios));
        }

        // 4. Sus textos existen en los dos sitios donde se ven.
        boolean textos = true;
        for (final AscentFeat f : AscentFeat.values()) {
            if (!hasText(f.getNameKey()) || !hasText(f.getOpensKey())) {
                fail("hitos: a " + f.getId() + " le falta el nombre o el 'que abre'");
                textos = false;
            }
        }
        if (textos) {
            ok("hitos: todos tienen nombre y dicen que abren");
        }

        // 5. Cada hito salta con su condicion, y NO con una run vacia. Las dos
        //    mitades hacen falta: un hito que no salta nunca entierra su
        //    contenido, y uno que salta siempre lo regala en la primera run.
        AscentUnlocks.grantForTest();
        final AscentSummary nada = AscentSummary.of(run, false, false);
        final List<String> saltanSolos = new ArrayList<>();
        for (final AscentFeat f : AscentFeat.values()) {
            if (f.earnedBy(nada)) {
                saltanSolos.add(f.getId());
            }
        }
        if (saltanSolos.isEmpty()) {
            ok("hitos: ninguno se consigue con una run recien empezada y perdida");
        } else {
            fail("hitos: se regalan sin hacer nada: " + String.join(", ", saltanSolos));
        }

        run.nextAct();
        if (AscentFeat.REACH_ACT_2.earnedBy(AscentSummary.of(run, false, false))) {
            ok("hitos: el primer escalon salta al llegar al acto 2, aunque se pierda");
        } else {
            fail("hitos: el primer escalon no salta en el acto 2");
        }
        if (AscentFeat.FIRST_WIN.earnedBy(AscentSummary.of(run, true, false))
                && !AscentFeat.FIRST_WIN.earnedBy(AscentSummary.of(run, false, false))) {
            ok("hitos: 'ascenso completado' pide ganar, y solo ganar");
        } else {
            fail("hitos: 'ascenso completado' no distingue ganar de perder");
        }

        int puestas = 0;
        for (final AscentRelic relic : AscentRelics.all()) {
            if (puestas < 6 && run.addRelic(relic)) {
                puestas++;
            }
        }
        if (AscentFeat.HOARDER.earnedBy(AscentSummary.of(run, false, false))) {
            ok("hitos: el coleccionista salta con " + puestas + " reliquias");
        } else {
            fail("hitos: el coleccionista no salta con " + puestas + " reliquias");
        }

        // 6. record() apunta solo los NUEVOS, y a la segunda vez no repite: un
        //    cartel de "¡hito conseguido!" por algo que ya tenias deja de
        //    significar nada.
        AscentUnlocks.grantForTest();
        final AscentSummary buena = AscentSummary.of(run, true, false);
        final int primera = AscentUnlocks.record(buena).size();
        final int segunda = AscentUnlocks.record(buena).size();
        if (primera > 0 && segunda == 0) {
            ok("hitos: la primera vez se anuncian " + primera + ", la segunda ninguno");
        } else {
            fail("hitos: se han anunciado " + primera + " y luego " + segunda);
        }

        // 7. Y lo que de verdad importa: la derrota NO se los lleva. Es el
        //    mismo fallo por el que AscentUnlocks vive fuera del prefijo
        //    "ascent." — si estuviera dentro, discard() lo borraria y nadie se
        //    enteraria nunca.
        final Set<AscentFeat> antes = AscentUnlocks.feats();
        run.discard();
        if (AscentUnlocks.feats().equals(antes) && !antes.isEmpty()) {
            ok("hitos: perder la run NO se lleva los hitos (" + antes.size() + " siguen ahi)");
        } else {
            fail("hitos: la derrota se ha llevado hitos: " + antes.size()
                    + " -> " + AscentUnlocks.feats().size());
        }
    }

    private static void ok(final String msg) {
        passed++;
        System.out.println("  [ok]   " + msg);
    }

    private static void fail(final String msg) {
        failed++;
        System.out.println("  [MAL]  " + msg);
    }
}
