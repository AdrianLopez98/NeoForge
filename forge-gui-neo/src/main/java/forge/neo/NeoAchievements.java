package forge.neo;

import java.util.ArrayList;
import java.util.List;

import forge.game.GameType;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.localinstance.achievements.Achievement;
import forge.localinstance.achievements.AchievementCollection;
import forge.localinstance.achievements.AltWinAchievements;
import forge.localinstance.achievements.CardActivationAchievements;
import forge.localinstance.achievements.ChallengeAchievements;
import forge.localinstance.achievements.PlaneswalkerAchievements;
import forge.model.FModel;
import forge.util.Localizer;

/**
 * Los logros que <b>ya tienes</b>.
 *
 * <p>Esto no anyade logros: los ensenya. Forge lleva 38 clases de logro y los
 * actualiza solo al acabar cada partida — {@code PlayerControllerHuman
 * .updateAchievements()} — desde el primer dia del proyecto. Estaba todo
 * escrito en {@code %APPDATA%\Forge\achievements\*.xml} y no habia ni una
 * pantalla que lo leyera.
 *
 * <p>Cero reglas propias: quien decide si un logro esta conseguido y en que
 * grado es el motor ({@code Achievement.isActive()}, {@code earnedMythic()}...).
 * Aqui solo se traduce eso a algo que se pueda pintar.
 *
 * <p><b>Lo que NO se pide.</b> Nunca {@code Achievement.getImage()}: el trofeo
 * lo compone {@code IGuiBase.createLayeredImage}, que es una pantalla de la GUI
 * vieja que no tenemos. Para reconocer un logro de carta ya esta la carta
 * misma, que el motor si publica ({@link Achievement#getPaperCard()}).
 */
public final class NeoAchievements {

    private NeoAchievements() {
    }

    /**
     * El grado conseguido.
     *
     * <p>Es el mismo escalado de rarezas que usa Magic, y el motor lo calcula
     * con umbrales distintos por logro. {@code ESPECIAL} es para los que no
     * tienen grados: o lo has hecho o no.
     */
    public enum Tier {
        NONE, COMMON, UNCOMMON, RARE, MYTHIC, SPECIAL;

        /** El nombre del grado, traducido por el motor. */
        public String label() {
            switch (this) {
                case COMMON: return Localizer.getInstance().getMessage("lblCommon");
                case UNCOMMON: return Localizer.getInstance().getMessage("lblUncommon");
                case RARE: return Localizer.getInstance().getMessage("lblRare");
                case MYTHIC: return Localizer.getInstance().getMessage("lblMythic");
                case SPECIAL: return NeoText.get("achv.special");
                default: return "";
            }
        }

        /** La clase de estilo con la que se pinta la pastilla. */
        public String styleClass() {
            return "achv-tier-" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Un logro, ya masticado para pintarlo. */
    public static final class Item {
        private final String name;
        private final String description;
        private final String progress;
        private final Tier tier;
        private final PaperCard card;
        private final String search;

        Item(final Achievement a) {
            this.name = a.getDisplayName() == null ? a.getKey() : a.getDisplayName();
            this.description = describe(a);
            // El progreso, SOLO si hay algo apuntado. Los logros progresivos
            // (los 251 ultimates, por ejemplo) devuelven "0 ganas" cuando no
            // has hecho nada, y eso repetido en toda la rejilla no es
            // informacion, es ruido. Quien sabe si hay marca es needSave(): es
            // lo mismo que decide si el logro llega siquiera a escribirse.
            this.progress = a.needSave() ? a.getSubTitle(true) : null;
            this.tier = tierOf(a);
            final IPaperCard paper = a.getPaperCard();
            this.card = paper instanceof PaperCard pc ? pc : null;
            this.search = (name + " " + description).toLowerCase(java.util.Locale.ROOT);
        }

        public String getName() {
            return name;
        }

        /** Que hay que hacer para conseguirlo, o que se hizo. */
        public String getDescription() {
            return description;
        }

        /** "Best: 38 games (26/8/26)". null si todavia no se ha empezado. */
        public String getProgress() {
            return progress;
        }

        public Tier getTier() {
            return tier;
        }

        public boolean isEarned() {
            return tier != Tier.NONE;
        }

        /** La carta del logro, si es un logro de carta. Puede ser null. */
        public PaperCard getCard() {
            return card;
        }

        /** Si el buscador de la pantalla lo tiene que ensenyar. */
        public boolean matches(final String query) {
            return query.isEmpty() || search.contains(query);
        }
    }

    /** Una coleccion de logros: un modo de juego, o un tema transversal. */
    public static final class Group {
        private final String label;
        private final List<Item> items;
        private final int earned;

        Group(final String label, final List<Item> items) {
            this.label = label;
            this.items = items;
            int n = 0;
            for (final Item i : items) {
                if (i.isEarned()) {
                    n++;
                }
            }
            this.earned = n;
        }

        public String getLabel() {
            return label;
        }

        public List<Item> getItems() {
            return items;
        }

        public int getEarned() {
            return earned;
        }

        public int getTotal() {
            return items.size();
        }
    }

    /**
     * Los modos que se pueden jugar <b>desde aqui</b>.
     *
     * <p>La GUI vieja ensenya siete ({@code AchievementCollection
     * .buildComboBox}), pero tres de ellos — Sellado, Conquista Planar y la
     * Aventura de Forge — no existen en esta aplicacion, asi que sus logros no
     * se pueden conseguir por ningun camino. Ensenyar una pestanya de la que es
     * imposible ganar nada no informa, estorba: son 48 lineas muertas y ademas
     * la Aventura de Forge se llama igual que NUESTRA aventura (el modo Quest),
     * asi que salian dos pestanyas con el mismo nombre.
     *
     * <p>Si algun dia se anyade uno de esos modos, su pestanya vuelve sola:
     * basta con meter su {@code GameType} en esta lista.
     */
    private static final GameType[] PLAYABLE = {
        GameType.Constructed, GameType.Draft, GameType.Quest, GameType.Puzzle,
    };

    /**
     * Todas las colecciones, en el orden en el que se juegan.
     *
     * <p>Primero los modos, despues los cuatro temas transversales — que es el
     * orden de la GUI vieja, y empieza por donde de verdad se juega.
     *
     * <p>Se lee del disco cada vez que se llama. Son ficheros XML pequenyos, y
     * asi la pantalla ensenya lo que acabas de ganar sin reiniciar.
     */
    public static List<Group> all() {
        final List<Group> out = new ArrayList<>();
        for (final GameType type : PLAYABLE) {
            add(out, FModel.getAchievements(type));
        }
        add(out, AltWinAchievements.instance);
        add(out, PlaneswalkerAchievements.instance);
        add(out, CardActivationAchievements.instance);
        add(out, ChallengeAchievements.instance);
        return out;
    }

    private static void add(final List<Group> out, final AchievementCollection collection) {
        if (collection == null) {
            return;
        }
        // Se relee del XML: la coleccion es un singleton que vive toda la
        // sesion, y sin esto lo que ganaste en la partida anterior no aparece
        // hasta reiniciar.
        collection.load();
        final List<Item> items = new ArrayList<>();
        for (final Achievement a : collection) {
            items.add(new Item(a));
        }
        out.add(new Group(collection.toString(), items));
    }

    /** Cuantos llevas de cuantos, sumando todas las colecciones. */
    public static int[] totals(final List<Group> groups) {
        int earned = 0;
        int total = 0;
        for (final Group g : groups) {
            earned += g.getEarned();
            total += g.getTotal();
        }
        return new int[] {earned, total};
    }

    // ---------------------------------------------------------------

    /** El grado que dice el motor. El orden importa: de mas alto a mas bajo. */
    private static Tier tierOf(final Achievement a) {
        if (!a.isActive()) {
            return Tier.NONE;
        }
        if (a.isSpecial()) {
            return Tier.SPECIAL;
        }
        if (a.earnedMythic()) {
            return Tier.MYTHIC;
        }
        if (a.earnedRare()) {
            return Tier.RARE;
        }
        if (a.earnedUncommon()) {
            return Tier.UNCOMMON;
        }
        return Tier.COMMON;
    }

    /**
     * Que pone debajo del nombre.
     *
     * <p>Los logros con grados guardan la frase en dos trozos: uno comun
     * ("Gana una partida sin cartas en tu...") y uno por grado ("...mano",
     * "...mano o biblioteca"). Se junta el comun con el del <b>siguiente</b>
     * grado que te falta, que es lo unico accionable; si ya estan todos, con el
     * ultimo, que es lo que hiciste.
     *
     * <p>Es exactamente como lo compone el motor cuando te lo anuncia
     * ({@code Achievement.update}), asi que la frase encaja sola.
     */
    private static String describe(final Achievement a) {
        if (a.isSpecial()) {
            // Sin grados: la descripcion es la comun, y el "grado mitico" lleva
            // el texto de ambientacion entre parentesis.
            return join(a.getSharedDesc(), a.getMythicDesc());
        }
        final String tier;
        if (!a.earnedCommon()) {
            tier = a.getCommonDesc();
        } else if (!a.earnedUncommon()) {
            tier = a.getUncommonDesc();
        } else if (!a.earnedRare()) {
            tier = a.getRareDesc();
        } else {
            tier = a.getMythicDesc();
        }
        return join(a.getSharedDesc(), tier);
    }

    private static String join(final String a, final String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + " " + b;
    }
}
