package forge.neo.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import forge.gamemodes.match.GameLobby;
import forge.gamemodes.net.ChatMessage;
import forge.gamemodes.net.IOnlineChatInterface;
import forge.gamemodes.net.IOnlineLobby;
import forge.gamemodes.net.IRemote;
import forge.gamemodes.net.NetConnectUtil;
import forge.gamemodes.net.client.FGameClient;
import forge.gamemodes.net.event.MessageEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gui.interfaces.ILobbyView;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.look.NeoLook;

/**
 * El pegamento entre nuestra pantalla y la red de Forge.
 *
 * <p>Existe para poder entrar por {@link NetConnectUtil}, que es el camino
 * oficial y hace bastantes mas cosas que levantar el socket: engancha el chat
 * con sus <b>comandos de anfitrion</b> ({@code /skipreconnect},
 * {@code /skiptimeout}), anuncia el plazo de AFK, conecta el manejador de draft
 * en red y, en el invitado, deja el lobby atado al {@code AbstractGuiGame} para
 * que la partida sepa de que mazo juega cada uno. Montarlo a mano era escribir
 * eso otra vez peor.
 *
 * <p>Para usarlo hay que darle a Forge dos interfaces, y son pequenyas:
 * {@link IOnlineLobby} (tres metodos) y {@link IOnlineChatInterface} (dos).
 * Las dos las implementa esta clase.
 *
 * <p><b>Dos cosas de hilos que no se pueden olvidar:</b>
 * <ol>
 *   <li>{@code host}/{@code join} se llaman desde un hilo <b>de fondo</b>, nunca
 *       desde el de JavaFX: abren sockets y pueden tardar.</li>
 *   <li>y por eso {@link #setLobby} llega en ese hilo de fondo. La pantalla
 *       tiene que existir <b>antes</b>, creada en el hilo de JavaFX; aqui solo
 *       se le ata el lobby. Crear nodos aqui seria crearlos fuera de su hilo.</li>
 * </ol>
 */
public final class NeoOnline implements IOnlineLobby, IOnlineChatInterface {

    /** Lo que la pantalla tiene que saber hacer para que esto funcione. */
    public interface View extends ILobbyView {
        /** Ata el lobby (del servidor o del cliente) a la pantalla. */
        void bindLobby(GameLobby lobby);

        /** Un mensaje de chat, del sistema o de alguien. */
        void addChat(ChatMessage message);

        /** Se ha caido la conexion. Llega en un hilo cualquiera. */
        void connectionLost(String message);
    }

    private final View view;
    private volatile FGameClient client;
    private volatile IRemote remote;

    public NeoOnline(final View view) {
        this.view = view;
    }

    // ------------------------------------------------------------------
    // Arrancar
    // ------------------------------------------------------------------

    /**
     * Hospedar.
     *
     * <p>La pregunta del UPnP se contesta <b>antes</b> y a proposito. Forge la
     * hace dentro de {@code startServer} con un {@code SOptionPane}, y ese
     * dialogo tendria que salir y bloquear en mitad de una llamada que ya viene
     * de un hilo de fondo: es justo la clase de dialogo modal cruzado que cuelga
     * esta interfaz. Preguntandolo nosotros en la pantalla, con una casilla, el
     * motor no llega a preguntar nada.
     *
     * <p>Se escribe <b>solo en memoria</b> (sin {@code save()}): es una decision
     * de esta partida, no un ajuste que haya que heredar.
     *
     * @return el mensaje que Forge quiere que se enseñe (puerto, o el fallo)
     */
    public ChatMessage host(final boolean useUpnp) {
        FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.UPnP,
                useUpnp ? "ALWAYS" : "NEVER");
        ensureName();
        NeoLobby.markHosting();
        return NetConnectUtil.host(this, this);
    }

    /**
     * Unirse a la partida de otro.
     *
     * @param url {@code ip} o {@code ip:puerto}; Forge lo valida y pone el
     *            puerto por defecto si falta
     */
    public ChatMessage join(final String url) {
        ensureName();
        return NetConnectUtil.join(url, this, this);
    }

    /**
     * El nombre con el que te sientas.
     *
     * <p>Forge lo pediria con un dialogo si estuviera vacio
     * ({@code NetConnectUtil.ensurePlayerName}), y ese dialogo sale en mitad de
     * la conexion. Aqui ya tenemos uno elegido en Personalizacion, asi que se
     * usa ese y la pregunta no llega a hacerse.
     */
    private static void ensureName() {
        final String current = FModel.getPreferences().getPref(FPref.PLAYER_NAME);
        if (current == null || current.isBlank()) {
            FModel.getPreferences().setPref(FPref.PLAYER_NAME, NeoLook.playerName());
        }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    /**
     * Manda una linea de chat.
     *
     * <p>El anfitrion y el invitado escriben por sitios distintos y el
     * {@code IRemote} que nos da Forge ya lo resuelve: en el anfitrion es un
     * envoltorio que primero mira si la linea es un <b>comando</b>
     * ({@code /skipreconnect}) y solo si no lo es la reparte.
     */
    public void say(final String text) {
        final IRemote r = remote;
        if (r == null || text == null || text.isBlank()) {
            return;
        }
        // CON nombre. MessageEvent(texto) a secas no lleva autor: lo que
        // escribe un invitado lo firma el servidor con su nombre, pero lo del
        // anfitrion se repartia tal cual y a todo el mundo le salia como un
        // aviso del sistema, sin saber quien lo decia.
        r.send(new MessageEvent(myName(), text.trim()));
    }

    private static String myName() {
        final String name = FModel.getPreferences().getPref(FPref.PLAYER_NAME);
        return name == null || name.isBlank() ? NeoLook.playerName() : name;
    }

    // ------------------------------------------------------------------
    // La version de cada uno (ver NetBuild)
    // ------------------------------------------------------------------

    private final AtomicBoolean announced = new AtomicBoolean();
    private final Set<String> warnedAbout = ConcurrentHashMap.newKeySet();
    private final Set<String> answered = ConcurrentHashMap.newKeySet();

    /**
     * El invitado dice su version al sentarse, una vez.
     *
     * <p>El anfitrion contesta con la suya ({@link #onBuild}), y cada lado
     * compara por su cuenta: asi el aviso sale a los DOS, que es a quien le
     * toca actualizar. En un hilo aparte porque la huella lee los jars.
     */
    public void announceBuild() {
        if (!announced.compareAndSet(false, true)) {
            return;
        }
        final Thread t = new Thread(() -> say(NetBuild.message()), "neo-lobby-build");
        t.setDaemon(true);
        t.start();
    }

    private void onBuild(final String source, final String theirs) {
        if (source == null || source.isBlank()) {
            return;
        }
        final String who = source.endsWith(" (Host)")
                ? source.substring(0, source.length() - " (Host)".length()) : source;
        final String mine = NetBuild.id();
        if (who.equals(myName()) && theirs.equals(mine)) {
            // Es mi propia huella que vuelve: el servidor reparte a todos.
            return;
        }
        if (NetBuild.differs(mine, theirs) && warnedAbout.add(who)) {
            System.out.printf(Locale.ROOT, "[lobby] %s tiene otra version (%s, yo %s)%n",
                    who, theirs, mine);
            view.addChat(new ChatMessage(null,
                    forge.neo.NeoText.get("lobby.versionMismatch", who),
                    ChatMessage.MessageType.SYSTEM));
        }
        if (isHost() && answered.add(who)) {
            say(NetBuild.message());
        }
    }

    @Override
    public void setGameClient(final IRemote remote) {
        this.remote = remote;
    }

    @Override
    public void addMessage(final ChatMessage message) {
        final String build = message == null ? null : NetBuild.parse(message.getMessage());
        if (build != null) {
            // No es una frase para nadie: es la version de alguien.
            onBuild(message.getSource(), build);
            return;
        }
        view.addChat(message);
    }

    // ------------------------------------------------------------------
    // IOnlineLobby
    // ------------------------------------------------------------------

    @Override
    public ILobbyView setLobby(final GameLobby lobby) {
        // Llega en el hilo de fondo. La pantalla ya existe: aqui solo se ata.
        view.bindLobby(lobby);
        return view;
    }

    @Override
    public void setClient(final FGameClient client) {
        this.client = client;
    }

    @Override
    public void closeConn(final String msg) {
        view.connectionLost(msg);
    }

    // ------------------------------------------------------------------
    // Cerrar
    // ------------------------------------------------------------------

    /**
     * Deja de hospedar o de estar conectado, segun el papel.
     *
     * <p>Se puede llamar siempre: si no habia nada abierto no hace nada.
     */
    public void leave() {
        announced.set(false);
        final FGameClient c = client;
        if (c != null) {
            c.close();
            client = null;
        }
        NeoLobby.stopHosting();
        remote = null;
    }

    public boolean isHost() {
        return client == null && FServerManager.getInstance().isHosting();
    }

    public boolean isGuest() {
        return client != null;
    }

    /** La direccion que hay que pasarle a los amigos, ya formateada. */
    public static String shareAddress() {
        final List<String[]> all = localAddresses();
        return all.isEmpty()
                ? String.format(Locale.ROOT, "%s:%d", FServerManager.getLocalAddress(), NeoLobby.port())
                : all.get(0)[1];
    }

    /**
     * TODAS las direcciones de esta maquina, como {nombre de la red, ip:puerto}.
     *
     * <p>Antes solo salia una: la que el sistema usaria para llegar a
     * internet. Y hay dos casos muy normales en los que esa no es la buena:
     * con una VPN de las de jugar con amigos (Radmin, Hamachi, ZeroTier,
     * Tailscale) la que hay que pasar es la de la VPN, y sin salida a internet
     * — dos portatiles en el mismo router del pueblo — salia "localhost", que
     * no le sirve a nadie mas. Forge ya sabe listarlas y ponerles nombre.
     *
     * <p>Fuera las de autoconfiguracion (169.254.x.x): son las de una tarjeta
     * que no ha conseguido red, y por ahi no va a entrar nadie.
     */
    public static List<String[]> localAddresses() {
        final List<String[]> out = new ArrayList<>();
        final int port = NeoLobby.port();
        // Una red virtual de mentira, para poder MIRAR la sala que sale cuando
        // hay una: -Dneo.net.fakeLan=Radmin-VPN,26.31.4.12. Sin esto, la
        // pantalla que se acaba de escribir solo se puede ver instalando Radmin
        // de verdad, o sea que no se revisa nunca. Va la primera porque el
        // motor tambien pone delante la ruta buena. Separador coma y nombre sin
        // espacios: esto se escribe en una linea de cmd, donde la barra
        // vertical es una tuberia y el espacio parte el argumento.
        final String fake = System.getProperty("neo.net.fakeLan");
        if (fake != null && fake.contains(",")) {
            final int comma = fake.indexOf(',');
            out.add(new String[] {fake.substring(0, comma),
                    String.format(Locale.ROOT, "%s:%d", fake.substring(comma + 1), port)});
        }
        // Se enumera aqui y NO con FServerManager.getAllLocalAddresses(), que
        // PIERDE direcciones. Guarda las suyas en un mapa con el nombre bonito
        // de clave, y ese nombre sale de mirar si la interfaz empieza por
        // "eth" -> "Ethernet"; en Windows eso es la tarjeta de verdad Y el
        // conmutador virtual de Hyper-V o WSL, que tambien se llama ethN. Las
        // dos con la misma clave, y al final hace result.putAll(sorted), que
        // SOBRESCRIBE la buena con la virtual.
        //
        // Caso real (18-09-2026): a un jugador la sala le ensenyaba una sola
        // direccion, 172.24.192.1 — el conmutador de WSL — y su 192.168.x.x no
        // salia por ningun lado. Con esa direccion no le puede entrar nadie, ni
        // de su casa, y encima es la que hay que saber para abrir el puerto a
        // mano. Perdio la tarde.
        //
        // Aqui no se pierde ninguna: el nombre repetido se desempata, y los
        // conmutadores virtuales van al FINAL (no se quitan — alguno puede ser
        // la unica red que compartan dos maquinas).
        final List<String[]> real = new ArrayList<>();
        final List<String[]> switches = new ArrayList<>();
        final Set<String> used = new java.util.LinkedHashSet<>();
        String routable = null;
        try {
            routable = FServerManager.getLocalAddress();
        } catch (final RuntimeException e) {
            System.out.println("[lobby] no se ha podido saber la ruta buena: " + e);
        }
        try {
            for (final NetworkInterface iface : java.util.Collections
                    .list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (final InetAddress addr : java.util.Collections.list(iface.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    final String ip = addr.getHostAddress();
                    if (ip.startsWith("169.254.") || "localhost".equals(ip)) {
                        continue;
                    }
                    String label = labelFor(iface);
                    // Desempate: dos tarjetas "Ethernet" son dos renglones, no
                    // uno. Sin esto volvemos al fallo que se viene a arreglar.
                    if (!used.add(label)) {
                        int n = 2;
                        while (!used.add(label + " " + n)) {
                            n++;
                        }
                        label = label + " " + n;
                    }
                    final String[] row = {label, String.format(Locale.ROOT, "%s:%d", ip, port)};
                    if (isVirtualSwitch(iface)) {
                        switches.add(row);
                    } else if (ip.equals(routable)) {
                        real.add(0, row);       // la ruta a internet, la primera
                    } else {
                        real.add(row);
                    }
                }
            }
        } catch (final SocketException e) {
            System.out.println("[lobby] no se han podido listar las redes: " + e);
        }
        out.addAll(real);
        out.addAll(switches);
        return out;
    }

    /**
     * El nombre con el que se le ensenya una red al jugador.
     *
     * <p>Mismo criterio que el del motor ({@code getFriendlyInterfaceName}), que
     * es privado y no se puede llamar. Lo que NO se copia es su fallo: aqui el
     * nombre puede repetirse y quien llama lo desempata, en vez de perder una
     * de las dos.
     */
    private static String labelFor(final NetworkInterface iface) {
        final String brand = shortBrand(iface.getDisplayName() == null
                ? iface.getName() : iface.getDisplayName());
        if (brand != null) {
            return brand;
        }
        final String n = iface.getName().toLowerCase(Locale.ROOT);
        final String d = iface.getDisplayName() == null
                ? "" : iface.getDisplayName().toLowerCase(Locale.ROOT);
        if (d.contains("wi-fi") || d.contains("wifi") || d.contains("wireless")
                || n.startsWith("wl")) {
            return "Wi-Fi";
        }
        if (n.startsWith("eth") || n.startsWith("ens") || n.startsWith("enp")
                || d.contains("ethernet")) {
            return "Ethernet";
        }
        return iface.getDisplayName() == null ? iface.getName() : iface.getDisplayName();
    }

    /**
     * ¿Es un conmutador virtual de la propia maquina (Hyper-V, WSL, VirtualBox,
     * VMware)?
     *
     * <p>Por ahi no entra nadie de fuera: es una red que Windows se monta
     * consigo mismo. No se esconden — si dos personas compartieran una, valdria
     * — pero van detras de las de verdad, que es donde estorban menos.
     *
     * <p>Se reconocen por la <b>descripcion</b>, no por el nombre: en Windows se
     * llaman {@code ethN} igual que la tarjeta buena, y ese es exactamente el
     * parecido que hace que el motor las confunda.
     */
    private static boolean isVirtualSwitch(final NetworkInterface iface) {
        final String d = iface.getDisplayName() == null
                ? "" : iface.getDisplayName().toLowerCase(Locale.ROOT);
        return d.contains("hyper-v") || d.contains("vethernet") || d.contains("wsl")
                || d.contains("virtualbox") || d.contains("vmware")
                || d.contains("vmnet") || d.contains("loopback adapter");
    }

    /**
     * Tu IPv6 de internet, si la hay: {nombre, [direccion]:puerto}, o null.
     *
     * <p><b>Es la salida gratis del CGNAT.</b> Con IPv6 no hay NAT de ningun
     * tipo: cada maquina tiene su propia direccion publica, asi que el problema
     * que hace imposible hospedar en IPv4 <b>ni se plantea</b>. No hay que
     * instalar nada ni pagar nada — si el operador te la da.
     *
     * <p>Hay que listarla nosotros porque
     * {@code FServerManager.getAllLocalAddresses()} filtra por
     * {@code Inet4Address}: las IPv6 <b>no salen</b>, asi que aunque las
     * tuvieras no habria forma de saber cual pasarle a nadie. El servidor en
     * cambio ya las acepta sin tocar nada — {@code b.bind(port)} es el comodin,
     * y con la pila dual (lo normal en Java) atiende IPv4 e IPv6 a la vez.
     *
     * <p><b>Se devuelve entre corchetes</b> y no es un adorno: una IPv6 lleva
     * dos puntos dentro, asi que {@code 2001:db8::1:36743} es ambiguo y el
     * parser del motor <b>la rechaza</b> (medido). La forma con corchetes
     * atraviesa {@code URLValidator} y {@code InetSocketAddress} sin tocar una
     * linea de Forge.
     *
     * <p>Fuera las que no sirven para que entre alguien de fuera: enlace local
     * ({@code fe80::}), bucle, sitio local y las multicast. De las que quedan se
     * coge <b>la primera</b>: Windows fabrica ademas direcciones temporales que
     * rotan cada pocas horas, y una direccion que caduca no se le pasa a nadie.
     */
    public static String[] globalIpv6() {
        final int port = NeoLobby.port();
        final String fake = System.getProperty("neo.net.fakeIpv6");
        if (fake != null && !fake.isBlank()) {
            return new String[] {"IPv6", String.format(Locale.ROOT, "[%s]:%d", fake.trim(), port)};
        }
        try {
            for (final NetworkInterface iface : java.util.Collections
                    .list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (final InetAddress addr : java.util.Collections.list(iface.getInetAddresses())) {
                    if (!(addr instanceof Inet6Address) || addr.isLoopbackAddress()
                            || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                            || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                        continue;
                    }
                    // Sin el %scope: solo lo llevan las de enlace local, que ya
                    // estan fuera, pero una cadena con % no se puede pasar por
                    // WhatsApp sin que alguien la rompa.
                    final String ip = addr.getHostAddress().split("%")[0];
                    return new String[] {"IPv6",
                            String.format(Locale.ROOT, "[%s]:%d", ip, port)};
                }
            }
        } catch (final SocketException e) {
            System.out.println("[lobby] no se han podido listar las IPv6: " + e);
        }
        return null;
    }

    /**
     * Una red virtual <b>instalada pero sin conectar</b>, o null.
     *
     * <p>Es el caso que mas rabia da y el que no se ve: tienes Radmin puesto,
     * pero cerrado o sin entrar en la red, asi que su adaptador esta <b>caido</b>
     * — y {@code getAllLocalAddresses()} se salta los que no estan {@code isUp()}.
     * Resultado: la sala se comporta <b>exactamente igual</b> que si no lo
     * tuvieras instalado, te manda a descargar lo que ya tienes y no hay ni un
     * aviso que te diga que lo unico que falta es abrirlo.
     *
     * <p>Se mira sobre las interfaces caidas, y se devuelve el nombre bonito
     * (el que pone el motor) para poder decirlo por su marca.
     */
    public static String dormantVirtualLan() {
        try {
            for (final NetworkInterface iface : java.util.Collections
                    .list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isLoopback()) {
                    continue;
                }
                final String name = iface.getDisplayName() == null
                        ? iface.getName() : iface.getDisplayName();
                // Solo marcas que se reconozcan por su nombre, y NO el criterio
                // ancho de isVirtualLan: ahi entran "VPN (...)" y "Virtual
                // Network", que sobre los nombres crudos de Windows pueden ser
                // la VPN del trabajo o un TAP olvidado. Decirle a alguien "abre
                // tu VPN para jugar a Magic" es peor que no decir nada — y aqui
                // la frase nombra la marca, asi que si no la sabemos, callamos.
                final String brand = shortBrand(name);
                if (brand == null) {
                    continue;
                }
                // Caida, o levantada pero sin ninguna IPv4 que pasar: las dos
                // cosas se ven igual desde la sala (no sale nada) y se arreglan
                // igual (abrir el programa y entrar en la red).
                boolean usable = false;
                if (iface.isUp()) {
                    for (final InetAddress a : java.util.Collections.list(iface.getInetAddresses())) {
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()
                                && !a.getHostAddress().startsWith("169.254.")) {
                            usable = true;
                            break;
                        }
                    }
                }
                if (!usable) {
                    return brand;
                }
            }
        } catch (final SocketException e) {
            System.out.println("[lobby] no se han podido listar las redes dormidas: " + e);
        }
        return null;
    }

    /**
     * La marca a secas, sacada del nombre largo de Windows, o null.
     *
     * <p>El nombre de un adaptador es "Radmin VPN Ethernet Adapter" o
     * "ZeroTier One [abcd1234]": decirle a alguien que abra eso no ayuda, lo que
     * tiene que abrir se llama Radmin VPN.
     *
     * <p><b>Devuelve null si no la reconoce</b>, y eso es lo que evita el
     * consejo absurdo. Una maquina cualquiera trae docenas de adaptadores
     * fantasma — medido aqui: 27, con "Microsoft Wi-Fi Direct Virtual Adapter",
     * cuatro "WAN Miniport" de protocolos de VPN y un tunel Teredo, todos
     * caidos. Ninguno es una red de jugar, y sin este null la sala mandaria a
     * abrir cualquiera de ellos.
     */
    private static String shortBrand(final String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("radmin")) {
            return "Radmin VPN";
        }
        if (n.contains("zerotier")) {
            return "ZeroTier";
        }
        if (n.contains("tailscale")) {
            return "Tailscale";
        }
        if (n.contains("hamachi")) {
            return "Hamachi";
        }
        if (n.contains("wireguard")) {
            return "WireGuard";
        }
        return null;
    }

    /**
     * La red virtual, si hay una puesta: {nombre, ip:puerto}, o null.
     *
     * <p><b>Es la direccion que de verdad sirve para jugar con alguien de
     * fuera</b>, y por eso se pregunta aparte en vez de dejarla perdida entre
     * las demas de {@link #localAddresses()}: con Radmin o ZeroTier instalados
     * salen cuatro o cinco renglones identicos y el bueno no se distingue de la
     * tarjeta de red de casa, que no le vale a nadie que no este en tu salon.
     *
     * <p>Si hay varias se coge la primera, que es la que el motor pone antes —
     * y con dos redes virtuales puestas a la vez cualquiera de las dos vale
     * mientras el amigo este en la misma.
     */
    public static String[] virtualLan() {
        for (final String[] a : localAddresses()) {
            if (NetReach.isVirtualLan(a[0])) {
                return a;
            }
        }
        return null;
    }

    /**
     * La direccion desde fuera de casa, ya con el puerto, o null.
     *
     * <p>Quien la pregunta es {@link NetReach}, que ademas averigua si <b>sirve
     * de algo</b>: bajo CGNAT esta direccion existe, se puede copiar y no lleva
     * a ninguna parte. Por eso la sala no llama aqui sino alli — esto se queda
     * para quien solo quiera el numero.
     *
     * <p><b>Consulta a internet</b> ({@code checkip.amazonaws.com}), asi que
     * NUNCA desde el hilo de JavaFX: puede tardar segundos o no contestar.
     */
    public static void externalAddress(final Consumer<String> whenKnown) {
        NetReach.check(r -> whenKnown.accept(r.publicIp() == null ? null
                : String.format(Locale.ROOT, "%s:%d", r.publicIp(), NeoLobby.port())));
    }
}
