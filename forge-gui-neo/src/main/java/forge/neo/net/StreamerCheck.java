package forge.neo.net;

/**
 * {@code run.cmd streamercheck}: que el modo streamer ({@link StreamerMode})
 * tape todas las formas en que sale una IP en la interfaz, y nada mas. Sin
 * motor y sin red.
 */
public final class StreamerCheck {

    private StreamerCheck() {
    }

    private static int good;
    private static int bad;

    public static void run() {
        good = 0;
        bad = 0;
        final String m = StreamerMode.MASK;
        hidden("Your address: 192.168.1.20", "Your address: " + m);
        hidden("Over the internet: 83.45.120.7", "Over the internet: " + m);
        hidden("Share this one — Radmin VPN: 26.11.0.5", "Share this one — Radmin VPN: " + m);
        hidden("On your network (Wi-Fi): 10.0.0.12", "On your network (Wi-Fi): " + m);
        hidden("Could not connect: java.net.ConnectException: Connection refused: /192.168.1.20:36743",
                "Could not connect: java.net.ConnectException: Connection refused: " + m);
        hidden("192.168.1.20:36743", m);
        hidden("Over the internet (IPv6): 2a02:9130:84c3:1f00:5d2e:aa1:7c0e:12", "Over the internet (IPv6): " + m);
        hidden("[2a02:9130:84c3::12]:36743", m);
        hidden("fe80::1%eth0", m);
        hidden("::1", m);
        // El aviso que Forge manda al chat de TODOS cuando una conexion se cae
        // antes de presentarse (FServerManager.DeregisterClientHandler).
        hidden("/83.45.120.7:51234 timed out after 30 seconds without a network response. Closing connection.",
                m + " timed out after 30 seconds without a network response. Closing connection.");
        // Y por donde pasa TODO el chat (sala, mesa y Android): NetPhrases.
        System.setProperty("neo.streamer", "true");
        try {
            final String said = NetPhrases.read(null,
                    "/83.45.120.7:51234 timed out after 30 seconds without a network response. Closing connection.").text();
            check(!said.contains("83.45") && said.contains(m), "el chat (NetPhrases) la tapa: \"" + said + "\"");
            final String typed = NetPhrases.read("Paige", "my ip is 192.168.1.20").text();
            check(!typed.contains("192.168") && typed.startsWith("Paige: "), "una IP escrita en el chat, tambien: \"" + typed + "\"");
        } finally {
            System.clearProperty("neo.streamer");
        }
        final String off = NetPhrases.read("Paige", "my ip is 192.168.1.20").text();
        check(off.contains("192.168.1.20") || StreamerMode.on(), "con el modo apagado, el chat se queda como esta");
        // Lo que NO es una IP se queda como esta.
        same("Turn 12 · 12:30:45");
        same("Is another program using port 36743?");
        same("Ready: 3/4 players — Commander 1.0.50");
        same("Waiting for Paige to be ready");
        same("");
        System.out.printf("%n  %d bien, %d mal%n", good, bad);
        if (bad > 0) {
            throw new IllegalStateException("el modo streamer: no cuadra");
        }
    }

    private static void hidden(final String in, final String want) {
        final String got = StreamerMode.mask(in);
        check(want.equals(got), "\"" + in + "\" -> \"" + got + "\"");
    }

    private static void same(final String in) {
        final String got = StreamerMode.mask(in);
        check(in.equals(got), "se queda igual: \"" + in + "\"" + (in.equals(got) ? "" : " (sale \"" + got + "\")"));
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            good++;
        } else {
            bad++;
        }
        System.out.println((ok ? "  [ok]   " : "  [MAL]  ") + what);
    }
}
