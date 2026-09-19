package forge.neo.net;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import forge.gamemodes.net.server.FServerManager;
import forge.gui.GuiBase;

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.Service;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;
import org.jupnp.support.igd.PortMappingListener;
import org.jupnp.support.igd.callback.GetExternalIP;
import org.jupnp.util.SpecificationViolationReporter;

/**
 * ¿Puede entrar alguien de internet a MI ordenador?
 *
 * <p>Nace de una tarde perdida (18-09-2026): dos personas intentando jugar una
 * partida privada, ninguna de las dos consigue que entre la otra. La sala
 * enseñaba <i>"Por internet: 81.0.42.39:36743"</i>, esa direccion se pasaba por
 * WhatsApp, y no entraba nadie — <b>porque esa direccion no era suya</b>. La
 * conexion estaba detras de <b>CGNAT</b>: el operador reparte una sola IP
 * publica entre muchos clientes, asi que el router de casa no tiene direccion
 * propia y desde fuera no hay forma de llegar a el. Ni abriendo el puerto a
 * mano, ni con UPnP, ni con nada.
 *
 * <p><b>Y el aviso que daba el motor era JUSTO el contrario.</b> Con la casilla
 * de UPnP marcada, {@code FServerManager} consigue el mapeo — el router lo
 * acepta tan contento — y suelta por el chat {@code lblUPnPSuccess}: <i>"Port
 * forwarded successfully. Remote players should be able to connect using your
 * external IP"</i>. Bajo CGNAT eso es mentira: el puerto queda abierto en la
 * cara WAN del router, que es una direccion privada del operador y no se ve
 * desde internet. O sea que la unica pista que tenia el jugador apuntaba al
 * lado equivocado.
 *
 * <p><b>Como se sabe, sin depender de nadie de fuera.</b> Se le pregunta al
 * router — por UPnP, con {@code GetExternalIPAddress} — <i>que direccion tienes
 * tu por fuera</i>, y se compara con la que ve internet
 * ({@code FServerManager.getExternalAddress()}, que va a checkip.amazonaws.com):
 *
 * <ul>
 *   <li>el router dice tener una <b>privada o del rango 100.64/10</b> → hay otro
 *       NAT por encima que no es suyo: <b>CGNAT</b>, seguro;</li>
 *   <li>el router dice tener <b>la misma que ve internet</b> → esa IP es de
 *       verdad suya: <b>se puede abrir el puerto</b>;</li>
 *   <li>el router dice una publica <b>distinta</b> → no lo sabemos. Lo normal es
 *       que haya una VPN puesta en este ordenador y checkip este viendo la
 *       salida de la VPN, no la de casa. <b>No se avisa</b>: equivocarse aqui es
 *       decirle a alguien que no puede hospedar cuando si puede;</li>
 *   <li>el router <b>no contesta</b> (UPnP apagado, no es un IGD, o el chisme no
 *       habla) → tampoco lo sabemos, y tampoco se avisa.</li>
 * </ul>
 *
 * <p><b>Solo se afirma lo que se puede demostrar.</b> Tres de los cuatro casos
 * salen como {@code UNKNOWN} y dejan la sala exactamente como estaba. El aviso
 * sale unicamente cuando el propio router ha dicho, con su boca, que por fuera
 * tiene una direccion que no se ve desde internet.
 *
 * <p><b>Ojo con el 100.64/10, que significa dos cosas segun donde este.</b> En
 * una interfaz <b>local</b> de esta maquina es Tailscale — asi lo reconoce
 * {@code FServerManager.getFriendlyInterfaceName}, y por eso ahi es una buena
 * noticia. En la cara <b>WAN del router</b> es CGNAT, que es justo la contraria.
 * Es el mismo rango (RFC 6598) usado para dos cosas distintas: no se puede
 * reaprovechar la comprobacion de una para la otra.
 *
 * <p>La consulta <b>tarda segundos</b> (descubrimiento UPnP, con tope de
 * {@value #TIMEOUT_MS} ms) y se hace <b>una sola vez por sesion</b>: el
 * resultado se guarda. Nunca desde el hilo de JavaFX.
 */
public final class NetReach {

    private NetReach() {
    }

    /** Lo que se le da al router para contestar, antes de rendirse. */
    private static final int TIMEOUT_MS = 6000;

    /** Que sabemos de si alguien de fuera puede llegar hasta aqui. */
    public enum Verdict {
        /** El router tiene una IP publica suya: se puede abrir el puerto. */
        REACHABLE,
        /** Hay otro NAT por encima del router. Por internet no entra nadie. */
        CGNAT,
        /** No se ha podido averiguar. La sala se queda como estaba. */
        UNKNOWN
    }

    /** El veredicto y los dos numeros con los que se ha sacado. */
    public static final class Result {
        private final Verdict verdict;
        private final String routerWan;
        private final String publicIp;

        Result(final Verdict verdict, final String routerWan, final String publicIp) {
            this.verdict = verdict;
            this.routerWan = routerWan;
            this.publicIp = publicIp;
        }

        public Verdict verdict() {
            return verdict;
        }

        /** Lo que el router dice tener por fuera, o null si no contesto. */
        public String routerWan() {
            return routerWan;
        }

        /** Lo que ve internet, o null si no se pudo consultar. */
        public String publicIp() {
            return publicIp;
        }

        public boolean isCgnat() {
            return verdict == Verdict.CGNAT;
        }
    }

    // ------------------------------------------------------------------
    // La regla, sin red: es lo unico que se puede comprobar sin ventana
    // ------------------------------------------------------------------

    /**
     * Una IPv4 que no se ve desde internet: privada (RFC 1918), de enlace local,
     * de bucle o sin asignar.
     */
    public static boolean isPrivate(final String ip) {
        final int[] p = parse(ip);
        if (p == null) {
            return false;
        }
        if (p[0] == 10 || p[0] == 127 || p[0] == 0) {
            return true;
        }
        if (p[0] == 192 && p[1] == 168) {
            return true;
        }
        if (p[0] == 172 && p[1] >= 16 && p[1] <= 31) {
            return true;
        }
        return p[0] == 169 && p[1] == 254;
    }

    /**
     * El rango del NAT del operador: {@code 100.64.0.0/10} (RFC 6598).
     *
     * <p><b>Solo significa CGNAT en la cara WAN de un router.</b> En una
     * interfaz de esta maquina el mismo rango es Tailscale.
     */
    public static boolean isCarrierNat(final String ip) {
        final int[] p = parse(ip);
        return p != null && p[0] == 100 && p[1] >= 64 && p[1] <= 127;
    }

    /**
     * ¿Es una red virtual de las de jugar con amigos?
     *
     * <p><b>Es la respuesta a "y entonces que IP le paso".</b> Bajo CGNAT no hay
     * ninguna direccion de internet que sirva — no es que este mal elegida, es
     * que no existe — asi que la unica que funciona es la de una red virtual:
     * los dos ordenadores se ven como si estuvieran en el mismo salon y el
     * router deja de pintar nada.
     *
     * <p>Se reconoce <b>por el nombre que ya les pone el motor</b>
     * ({@code FServerManager.getFriendlyInterfaceName}, que mira el nombre de la
     * interfaz y su descripcion): Hamachi, ZeroTier, Tailscale, Radmin VPN,
     * WireGuard y los tuneles genericos. Repetir aqui esa deteccion seria tener
     * dos listas que se van separando; y si Card-Forge reconoce una mas, esto se
     * entera solo.
     *
     * <p><b>Un tunel de VPN comercial cae dentro y esta bien que caiga</b>: si
     * los dos estan en la misma, tambien se ven. Lo que decide no es la marca,
     * es que haya una red por encima de los dos routers.
     */
    public static boolean isVirtualLan(final String networkName) {
        if (networkName == null) {
            return false;
        }
        final String n = networkName.toLowerCase(Locale.ROOT);
        return n.contains("hamachi") || n.contains("zerotier") || n.contains("tailscale")
                || n.contains("radmin") || n.contains("wireguard")
                || n.startsWith("vpn") || n.contains("virtual network");
    }

    /**
     * Lo que ha escrito el jugador, puesto como el motor lo entiende.
     *
     * <p><b>Existe por una sola cosa: una IPv6 pegada tal cual no vale.</b>
     * Lleva dos puntos dentro, asi que {@code 2001:db8::1} o
     * {@code 2001:db8::1:36743} son ambiguos y {@code URLValidator.parseURL}
     * devuelve null — medido: la sala contesta "esa direccion no vale" a una
     * direccion que es perfectamente correcta. Y es justo la forma en la que
     * cualquiera la va a copiar, porque es como se ve en todas partes.
     *
     * <p>Con corchetes ({@code [2001:db8::1]:36743}) atraviesa el motor entero
     * sin tocarle una linea — tambien medido. Asi que se le ponen aqui.
     *
     * <p>Lo que NO hace, a proposito: adivinar el puerto de una IPv6 sin
     * corchetes. En {@code 2001:db8::1:36743} no hay forma de saber si ese
     * ultimo grupo es el puerto o parte de la direccion, y elegir mal
     * conectaria a otro sitio en silencio. Se toma entera como direccion y se
     * usa el puerto de siempre, que es lo que pasa el 99% de las veces.
     */
    public static String normaliseAddress(final String typed) {
        if (typed == null) {
            return null;
        }
        final String s = typed.trim();
        if (s.isEmpty() || s.startsWith("[")) {
            return s;                       // ya viene bien, o no hay nada
        }
        final int colons = (int) s.chars().filter(c -> c == ':').count();
        if (colons < 2) {
            return s;                       // IPv4, nombre de maquina, o con puerto
        }
        return "[" + s + "]";
    }

    /** La decision, aislada de la red para poder comprobarla sin ventana. */
    public static Verdict verdictFor(final String routerWan, final String publicIp) {
        if (routerWan == null || routerWan.isBlank() || parse(routerWan) == null) {
            return Verdict.UNKNOWN;
        }
        if (isPrivate(routerWan) || isCarrierNat(routerWan)) {
            return Verdict.CGNAT;
        }
        if (publicIp == null || publicIp.isBlank()) {
            // El router tiene una publica suya; que internet no conteste no lo
            // cambia.
            return Verdict.REACHABLE;
        }
        return routerWan.trim().equals(publicIp.trim()) ? Verdict.REACHABLE : Verdict.UNKNOWN;
    }

    private static int[] parse(final String ip) {
        if (ip == null) {
            return null;
        }
        final String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return null;
        }
        final int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (final NumberFormatException e) {
                return null;
            }
            if (out[i] < 0 || out[i] > 255) {
                return null;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // La consulta al router
    // ------------------------------------------------------------------

    /** Lo averiguado en esta sesion. Preguntarlo cuesta segundos. */
    private static final AtomicReference<Result> CACHED = new AtomicReference<>();

    /**
     * Donde esta esta maquina vista desde internet, y si alguien puede llegar.
     *
     * <p><b>Es el unico sitio que hace las dos preguntas</b> — la de internet
     * (checkip) y la del router (UPnP) —, porque la respuesta util sale de
     * <b>comparar</b> las dos y tenerlas en dos sitios seria poder enseñar una
     * sin la otra: exactamente el fallo que esto viene a arreglar.
     *
     * <p><b>Consulta a internet y a la red local, y tarda segundos.</b> Nunca
     * desde el hilo de JavaFX. La respuesta llega en un hilo cualquiera, asi
     * que quien pinte algo con ella tiene que volver por
     * {@code Platform.runLater}.
     */
    public static void check(final Consumer<Result> whenKnown) {
        final Result forced = forced();
        if (forced != null) {
            whenKnown.accept(forced);
            return;
        }
        final Result already = CACHED.get();
        if (already != null) {
            whenKnown.accept(already);
            return;
        }
        final Thread t = new Thread(() -> {
            // En este orden a proposito: checkip suele contestar en menos de un
            // segundo y el descubrimiento UPnP se lleva hasta seis. Lanzarlos a
            // la vez ahorraria ese segundo y costaria un hilo mas y un latch.
            final String publicIp = askInternet();
            final String routerWan = askRouter();
            final Result out = new Result(verdictFor(routerWan, publicIp), routerWan, publicIp);
            // SIEMPRE, pase lo que pase. Costo un viaje entero de ida y vuelta
            // (18-09-2026): alguien manda su neo.log para que le miremos por que
            // no se le abre el puerto y el registro esta MUDO — porque el unico
            // caso que no apuntaba nada era justo el mas comun, que el router no
            // conteste. El silencio no se puede distinguir de "no llego a
            // ejecutarse", y eso convierte el registro en inutil para lo unico
            // para lo que existe: mirar que paso en el ordenador de otro.
            System.out.printf(Locale.ROOT,
                    "[lobby] alcance: %s (el router dice %s, internet ve %s)%n",
                    out.verdict(), routerWan == null ? "nada" : routerWan,
                    publicIp == null ? "nada" : publicIp);
            CACHED.set(out);
            whenKnown.accept(out);
        }, "neo-net-reach");
        t.setDaemon(true);
        t.start();
    }

    /**
     * El veredicto a la fuerza, para poder VER las tres salas.
     *
     * <p>{@code -Dneo.net.reach=cgnat|reachable|unknown}. Sin esto, cual de las
     * tres sale <b>depende de la casa en la que se ejecute</b>: aqui siempre
     * saldria la de CGNAT y la rama normal —la que lleva años funcionando— no se
     * podria capturar nunca para comprobar que no se ha roto. Y al reves, quien
     * tenga IP publica no puede ver el aviso ni para revisar como se lee.
     *
     * <p>No pasa por la cache a proposito: es para mirar, y tiene que poder
     * cambiarse sin reiniciar nada.
     */
    private static Result forced() {
        final String want = System.getProperty("neo.net.reach");
        if (want == null || want.isBlank()) {
            return null;
        }
        switch (want.trim().toLowerCase(Locale.ROOT)) {
        case "cgnat":
            return new Result(Verdict.CGNAT, "100.73.68.227", "81.0.42.39");
        case "reachable":
            return new Result(Verdict.REACHABLE, "81.0.42.39", "81.0.42.39");
        case "unknown":
            return new Result(Verdict.UNKNOWN, null, "81.0.42.39");
        default:
            System.out.printf(Locale.ROOT,
                    "[lobby] -Dneo.net.reach=%s no vale: cgnat, reachable o unknown%n", want);
            return null;
        }
    }

    /** Que IP ve internet. Bloquea. Devuelve null si no hay linea. */
    private static String askInternet() {
        try {
            final String ip = FServerManager.getExternalAddress();
            return ip == null || ip.isBlank() ? null : ip.trim();
        } catch (final Exception e) {
            System.out.println("[lobby] no se ha podido averiguar la IP externa: " + e);
            return null;
        }
    }

    /**
     * Le pregunta al router su direccion de fuera. Bloquea hasta
     * {@value #TIMEOUT_MS} ms.
     *
     * <p>Se traga <b>todo</b> lo que pueda salir mal y devuelve null, que es
     * "no lo se". Un router raro, un UPnP apagado o un jupnp que no carga
     * (pasa en iOS, y un {@code NoClassDefFoundError} es un {@code Error}, no
     * una excepcion) no pueden dejar sin sala a nadie: esto es un aviso, no una
     * parte de hospedar.
     */
    private static String askRouter() {
        UpnpService upnp = null;
        try {
            final AtomicReference<String> found = new AtomicReference<>();
            final AtomicBoolean done = new AtomicBoolean(false);

            // Los routers se saltan el estandar a diario y jupnp lo tolera; no
            // hace falta un renglon de queja por cada uno.
            SpecificationViolationReporter.disableReporting();

            upnp = new UpnpServiceImpl(upnpConfig());
            upnp.startup();
            upnp.getRegistry().addListener(new IgdAsker(found, done));
            upnp.getControlPoint().search();

            final long until = System.currentTimeMillis() + TIMEOUT_MS;
            while (!done.get() && System.currentTimeMillis() < until) {
                Thread.sleep(100);
            }
            return found.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (final LinkageError | Exception e) {
            System.out.println("[lobby] no se ha podido preguntar al router: " + e);
            return null;
        } finally {
            if (upnp != null) {
                try {
                    upnp.shutdown();
                } catch (final LinkageError | Exception ignored) {
                    // Apagarlo es limpieza: si falla, no cambia la respuesta.
                }
            }
        }
    }

    /**
     * Como habla UPnP esta plataforma.
     *
     * <p>Lo normal es preguntarselo a la interfaz, que es lo que hace el motor
     * — cada plataforma tiene la suya y en Android no vale la de escritorio.
     * Pero {@code GuiBase.getInterface()} es un <b>estatico global que puede
     * estar sin poner</b>: en un comprobador sin ventana, o antes de que arranque
     * la interfaz. Sin este respaldo eso sale por el {@code catch} como un
     * {@code NullPointerException} y todo el mundo seria {@code UNKNOWN} sin que
     * nada avisara.
     */
    private static org.jupnp.UpnpServiceConfiguration upnpConfig() {
        final forge.gui.interfaces.IGuiBase gui = GuiBase.getInterface();
        return gui != null ? gui.getUpnpPlatformService()
                : new org.jupnp.DefaultUpnpServiceConfiguration();
    }

    /**
     * Encuentra el router en la red y le pide su direccion de fuera.
     *
     * <p>Los cuatro tipos que hay que casar (puerta de enlace, dispositivo de
     * conexion y los dos sabores del servicio, IP y PPP) <b>los publica el
     * propio jupnp</b> como constantes de {@code PortMappingListener}: no hay
     * que escribirlos a mano ni adivinarlos.
     */
    private static final class IgdAsker extends DefaultRegistryListener {

        private final AtomicReference<String> found;
        private final AtomicBoolean done;

        IgdAsker(final AtomicReference<String> found, final AtomicBoolean done) {
            this.found = found;
            this.done = done;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void deviceAdded(final Registry registry, final Device device) {
            if (done.get() || device == null
                    || !PortMappingListener.IGD_DEVICE_TYPE.equals(device.getType())) {
                return;
            }
            final Device[] connections =
                    device.findDevices(PortMappingListener.CONNECTION_DEVICE_TYPE);
            if (connections == null) {
                return;
            }
            for (final Device connection : connections) {
                Service service = connection.findService(PortMappingListener.IP_SERVICE_TYPE);
                if (service == null) {
                    service = connection.findService(PortMappingListener.PPP_SERVICE_TYPE);
                }
                if (service == null) {
                    continue;
                }
                registry.getUpnpService().getControlPoint().execute(new GetExternalIP(service) {
                    @Override
                    protected void success(final String ip) {
                        if (ip != null && !ip.isBlank()) {
                            found.set(ip.trim());
                        }
                        done.set(true);
                    }

                    @Override
                    public void failure(final ActionInvocation invocation,
                            final UpnpResponse response, final String message) {
                        System.out.printf(Locale.ROOT,
                                "[lobby] el router no ha dicho su IP de fuera: %s%n", message);
                        done.set(true);
                    }
                });
                return;
            }
        }
    }
}
