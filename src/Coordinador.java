import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Coordinador {
    private final Config config;
    private final boolean modoInseguro;
    private final RelojLamport reloj;
    private final Object lockAforo = new Object();
    private int dentro = 0;

    private final Map<Integer, Long> ultimoLatido = new ConcurrentHashMap<>();
    private final Map<Integer, String> estadoPuertas = new ConcurrentHashMap<>();

    public Coordinador(Config config, boolean modoInseguro) {
        this.config = config;
        this.modoInseguro = modoInseguro;
        this.reloj = new RelojLamport(0, "coordinador.log");
        estadoPuertas.put(1, "INACTIVA");
        estadoPuertas.put(2, "INACTIVA");
        estadoPuertas.put(3, "INACTIVA");
    }

    public void iniciar() throws IOException {
        iniciarMonitorLatidos();
        reloj.registrarEventoLocal("INICIO_COORDINADOR", "aforo=" + config.aforo + " modo=" + (modoInseguro ? "INSEGURO" : "NORMAL"));
        System.out.println("=== COORDINADOR AforoSeguro (ID 0) ===");
        System.out.println("Puerto: " + config.puerto + " | Aforo: " + config.aforo + " | Modo: " + (modoInseguro ? "INSEGURO" : "NORMAL"));

        try (ServerSocket server = new ServerSocket(config.puerto)) {
            while (true) {
                Socket cliente = server.accept();
                new Thread(() -> atenderCliente(cliente)).start();
            }
        }
    }

    private void iniciarMonitorLatidos() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            long ahora = System.currentTimeMillis();
            for (int id = 1; id <= 3; id++) {
                String estado = estadoPuertas.get(id);
                Long ultimo = ultimoLatido.get(id);
                if ("ACTIVA".equals(estado) && ultimo != null && (ahora - ultimo) > config.timeoutLatidoMs) {
                    estadoPuertas.put(id, "CAIDA");
                    reloj.registrarEventoLocal("PUERTA_CAIDA", "P" + id + " sin latido por >" + config.timeoutLatidoMs + "ms");
                    System.out.println("[ALERTA] Puerta P" + id + " marcada como CAIDA");
                }
            }
        }, 1000, 500, TimeUnit.MILLISECONDS);
    }

    private void atenderCliente(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String linea = in.readLine();
            if (linea == null || linea.isBlank()) return;

            String tipo = Protocolo.extraer(linea, "tipo");
            String token = Protocolo.extraer(linea, "token");
            int puerta = Protocolo.extraerInt(linea, "puerta", -1);
            int lamportRecibido = Protocolo.extraerInt(linea, "lamport", 0);

            int tsRec = reloj.actualizar(lamportRecibido);
            reloj.registrar(tsRec, "RECIBE_" + tipo, "puerta=P" + puerta);

            // A3: Validacion de token
            if (!config.token.equals(token)) {
                reloj.registrarEventoLocal("TOKEN_INVALIDO", "puerta=P" + puerta + " tipo=" + tipo);
                int tsEnv = reloj.tick();
                String resp = Protocolo.crearRespuesta("RECHAZADA", "token invalido", obtenerDentro(), config.aforo, resumenPuertas(), tsEnv);
                reloj.registrar(tsEnv, "ENVIO_RESPUESTA", "RECHAZADA token invalido a P" + puerta);
                out.println(resp);
                System.out.println("[SEGURIDAD] Token invalido recibido desde P" + puerta);
                return;
            }

            String resultado = "PERMITIDA";
            String motivo = "ok";
            int dentroActual;

            switch (tipo) {
                case "LATIDO" -> {
                    ultimoLatido.put(puerta, System.currentTimeMillis());
                    String anterior = estadoPuertas.put(puerta, "ACTIVA");
                    if (!"ACTIVA".equals(anterior)) {
                        reloj.registrarEventoLocal("PUERTA_ACTIVA", "P" + puerta + " ahora ACTIVA");
                        System.out.println("[INFO] Puerta P" + puerta + " marcada como ACTIVA");
                    }
                    dentroActual = obtenerDentro();
                }
                case "ENTRAR" -> {
                    if (modoInseguro) {
                        // B3: Consulta y actualizacion separadas con pausa de 50 ms
                        if (dentro < config.aforo) {
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                            dentro++;
                            dentroActual = dentro;
                        } else {
                            resultado = "RECHAZADA";
                            motivo = "aforo completo";
                            dentroActual = dentro;
                        }
                    } else {
                        // B1: Exclusion mutua
                        synchronized (lockAforo) {
                            if (dentro < config.aforo) {
                                dentro++;
                            } else {
                                resultado = "RECHAZADA";
                                motivo = "aforo completo";
                            }
                            dentroActual = dentro;
                        }
                    }
                    System.out.println("[ENTRAR] P" + puerta + " -> " + resultado + " (" + motivo + ") | Dentro: " + dentroActual + "/" + config.aforo);
                }
                case "SALIR" -> {
                    synchronized (lockAforo) {
                        if (dentro > 0) {
                            dentro--;
                        } else {
                            resultado = "RECHAZADA";
                            motivo = "nadie dentro";
                        }
                        dentroActual = dentro;
                    }
                    System.out.println("[SALIR]  P" + puerta + " -> " + resultado + " (" + motivo + ") | Dentro: " + dentroActual + "/" + config.aforo);
                }
                case "ESTADO" -> {
                    dentroActual = obtenerDentro();
                }
                default -> {
                    resultado = "RECHAZADA";
                    motivo = "solicitud desconocida";
                    dentroActual = obtenerDentro();
                }
            }

            int tsEnv = reloj.tick();
            String resp = Protocolo.crearRespuesta(resultado, motivo, dentroActual, config.aforo, resumenPuertas(), tsEnv);
            reloj.registrar(tsEnv, "ENVIO_RESPUESTA", resultado + " (" + motivo + ") dentro=" + dentroActual + " a P" + puerta);
            out.println(resp);

        } catch (IOException e) {
            System.err.println("Error atendiendo cliente: " + e.getMessage());
        }
    }

    private int obtenerDentro() {
        synchronized (lockAforo) {
            return dentro;
        }
    }

    private String resumenPuertas() {
        return "P1:" + estadoPuertas.get(1) + ", P2:" + estadoPuertas.get(2) + ", P3:" + estadoPuertas.get(3);
    }

    public static void main(String[] args) throws IOException {
        boolean inseguro = false;
        for (String arg : args) {
            if ("-inseguro".equalsIgnoreCase(arg)) {
                inseguro = true;
            }
        }
        Config config = new Config("config.properties");
        new Coordinador(config, inseguro).iniciar();
    }
}