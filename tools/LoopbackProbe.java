import java.net.*;
import java.nio.channels.*;

/** Bounded local-only diagnosis; never contacts a remote host. */
class LoopbackProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("java=" + System.getProperty("java.version") + ", preferIPv4=" + System.getProperty("java.net.preferIPv4Stack"));
        for (String host : new String[]{"127.0.0.1", "::1"}) {
            try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName(host))) {
                server.setSoTimeout(2000);
                try (Socket client = new Socket()) {
                    client.connect(new InetSocketAddress(host, server.getLocalPort()), 2000);
                    try (Socket accepted = server.accept()) { System.out.println(host + " TCP OK"); }
                }
            } catch (Exception e) { System.out.println(host + ": " + e); }
        }
        try { Pipe p = Pipe.open(); p.source().close();p.sink().close();System.out.println("NIO pipe OK"); }
        catch (Exception e) { e.printStackTrace(); }
    }
}
