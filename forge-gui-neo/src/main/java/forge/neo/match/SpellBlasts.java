package forge.neo.match;

import forge.neo.match.LayeredSignature.Beams;
import forge.neo.match.LayeredSignature.Burst;
import forge.neo.match.LayeredSignature.Drift;
import forge.neo.match.LayeredSignature.Flow;
import forge.neo.match.LayeredSignature.Halo;
import forge.neo.match.LayeredSignature.Meteor;
import forge.neo.match.LayeredSignature.Orbit;
import forge.neo.match.LayeredSignature.Rise;
import forge.neo.match.LayeredSignature.Sweep;
import forge.neo.match.LayeredSignature.Twinkle;

/**
 * <b>Las coreografias de {@link SpellSignature}</b>: las mismas capas que las
 * entradas de comandante, sin carta. Aqui la "carta" de las capas es el centro
 * de lo que se golpea: la mesa entera en las limpiezas (un rectangulo de medio
 * alto en el centro) o la carta objetivo en las quitadas. {@code landAt} es el
 * golpe ({@link SpellSignature#IMPACT_AT}): lo de antes prepara, y al golpe
 * estalla. Ninguna pasa de 40 dibujos.
 */
final class SpellBlasts {

    private SpellBlasts() {
    }

    /** Blasphemous Act: un meteorito en diagonal y, al caer, rocas, ascuas y chispas por todas partes. */
    static LayeredSignature blasphemousAct(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Meteor(6, 0.55),
                new Burst(22, 360, 0.22, 0.8, true, 0xB1A5L,
                        SignatureSprite.ROCK, SignatureSprite.EMBER_R, SignatureSprite.SPARK_GOLD, SignatureSprite.FLAME));
    }

    /** Wrath of God: columnas de luz por toda la mesa, estrellas doradas y un estallido de luz. */
    static LayeredSignature wrathOfGod(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Beams(8, 0.06, 0.04, 0.55, 0x3A70L, false),
                new Twinkle(18, false, 0.05, 1, 0x3A71L, SignatureSprite.STAR_GOLD),
                new Burst(16, 360, 0.2, 0.1, false, 0x3A72L, SignatureSprite.STAR_GOLD, SignatureSprite.SPARK_GOLD));
    }

    /** Damnation: ceniza que cae y, al golpe, un anillo de humo morado. */
    static LayeredSignature damnation(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(18, Flow.FALL, 0.035, 1, true, false, 0xDA4AL, SignatureSprite.ASH),
                new Burst(14, 360, 0.42, 0, false, 0xDA4BL, SignatureSprite.SMOKE));
    }

    /** Toxic Deluge: burbujas venenosas que suben por toda la mesa. */
    static LayeredSignature toxicDeluge(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(26, Flow.RISE, 0.05, 1, false, false, 0x7071L, SignatureSprite.MOTE_GREEN),
                new Burst(16, 360, 0.2, 0, false, 0x7072L, SignatureSprite.MOTE_GREEN, SignatureSprite.SPARK_GREEN));
    }

    /** Cyclonic Rift: un remolino azul que se lo traga todo hacia el centro. */
    static LayeredSignature cyclonicRift(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.GLOW_BLUE, true, 0.0, 2.2, 0, 0, 0, 0),
                new Orbit(28, 0.0, 0.5, 1.0, 1.3, 0.11, true, SignatureSprite.SPARK_BLUE, SignatureSprite.MOTE_TEAL),
                new Burst(14, 360, 0.2, 0, false, 0xC7C1L, SignatureSprite.SPARK_BLUE));
    }

    /** Farewell: un barrido de luz blanca de lado a lado y estrellas plateadas. */
    static LayeredSignature farewell(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Sweep(SignatureSprite.SLASH_GLOW_WHITE, SignatureSprite.SLASH_WHITE, 3.2,
                        new double[] {0.3}, new double[] {90}),
                new Twinkle(18, false, 0.05, 1, 0xFA2EL, SignatureSprite.STAR_SILVER),
                new Burst(16, 360, 0.2, 0.05, false, 0xFA2FL, SignatureSprite.STAR_SILVER));
    }

    /** Supreme Verdict: tres rayos blancos y azules sobre la mesa. */
    static LayeredSignature supremeVerdict(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Sweep(SignatureSprite.LIGHTNING_GLOW, SignatureSprite.LIGHTNING, 2.2,
                        new double[] {0.24, 0.34, 0.44}, new double[] {-14, 18, -4}),
                new Burst(16, 360, 0.2, 0.1, false, 0x5E7DL, SignatureSprite.SPARK_BLUE, SignatureSprite.STAR_SILVER));
    }

    /** Swords to Plowshares: un tajo blanco sobre el objetivo y chispas doradas. */
    static LayeredSignature swords(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Sweep(SignatureSprite.SLASH_GLOW_WHITE, SignatureSprite.SLASH_WHITE, 1.7,
                        new double[] {0.3}, new double[] {-55}),
                new Burst(14, 360, 0.32, 0.3, false, 0x5D01L, SignatureSprite.SPARK_GOLD, SignatureSprite.STAR_GOLD));
    }

    /** Path to Exile: un portal de luz que gira sobre el objetivo, motas que suben y estrellas. */
    static LayeredSignature path(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.GLORY, true, 0.02, 2.4, 60, 0, 0, 0),
                new Rise(14, 0.12, 1.1, 1, 0x9A70L, SignatureSprite.STAR_SILVER),
                new Burst(14, 360, 0.3, 0, false, 0x9A71L, SignatureSprite.STAR_SILVER, SignatureSprite.STAR_GOLD));
    }

    /** Chaos Warp: un remolino rojo y morado que se traga el objetivo. */
    static LayeredSignature chaosWarp(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Orbit(18, 0.0, 0.45, 1.4, 1.3, 0.14, true, SignatureSprite.SPARK_RED, SignatureSprite.SPARK_PURPLE),
                new Burst(14, 360, 0.3, 0.2, false, 0xC4A0L, SignatureSprite.SPARK_RED, SignatureSprite.SPARK_PURPLE));
    }
}
