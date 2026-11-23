import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Puerta {
    private final int id;
    private final String nombre;
    private final Config config;
    private final RelojLamport reloj;

    public Puerta(int id, Config config) {
        this.id = id;
        this.nombre = switch (id) {
            case 1 -> "P1 Norte";
            case 2 -> "P2 Sur";
            case 3 -> "P3 Este";
            default -> "P" + id;
        };
        this.config = config;
        this.reloj = new RelojLamport(id, "puerta" + id + ".log");
    }

    public void iniciar() {
        reloj.registrarEventoLocal("INICIO_PUERTA", nombre);


        ScheduledExecutorService latidos = Executors.newSingleThreadScheduledExecutor();
        latidos.scheduleAtFixedRate(this::enviarLatido, 0, config.intervaloLatidoMs, TimeUnit.MILLISECONDS);

        System.out.println("=== " + nombre + " (ID " + id + ") ===");
        System.out.println("Ordenes: entrar | salir | estado | token_mal | cerrar");

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print(nombre + "> ");
                if (!sc.hasNextLine()) break;
                String cmd = sc.nextLine().trim().toLowerCase();

                switch (cmd) {
                    case "entrar" -> enviarOperacion("ENTRAR", config.token);
                    case "salir" -> enviarOperacion("SALIR", config.token);
                    case "estado" -> enviarOperacion("ESTADO", config.token);
                    case "token_mal" -> enviarOperacion("ENTRAR", "TOKEN_INVALIDO_TEST");
                    case "cerrar" -> {
                        reloj.registrarEventoLocal("CIERRE_PUERTA", nombre);
                        latidos.shutdownNow();
                        System.out.println("Puerta detenida.");
                        return;
                    }
                    default -> System.out.println("Orden no reconocida. Use: entrar, salir, estado, token_mal, cerrar");
                }
            }
        }
    }

    private void enviarLatido() {
        int tsEnv = reloj.tick();
        reloj.registrar(tsEnv, "ENVIO_LATIDO", "hacia coordinador");
        String msg = Protocolo.crearSolicitud("LATIDO", config.token, id, tsEnv);
        try {
            String resp = Protocolo.enviarYRecibir(config.host, config.puerto, msg);
            if (resp != null) {
                int tsRec = reloj.actualizar(Protocolo.extraerInt(resp, "lamport", 0));
                reloj.registrar(tsRec, "RECIBE_ACK_LATIDO", "ok");
            }
        } catch (IOException e) {
            reloj.registrarEventoLocal("LATIDO_FALLIDO", "coordinador no responde");
        }
    }

    private void enviarOperacion(String tipo, String tokenAEnviar) {
        int tsEnv = reloj.tick();
        reloj.registrar(tsEnv, "ENVIO_" + tipo, "solicitud al coordinador");
        String msg = Protocolo.crearSolicitud(tipo, tokenAEnviar, id, tsEnv);

        try {
            String resp = Protocolo.enviarYRecibir(config.host, config.puerto, msg);
            if (resp == null) {
                throw new IOException("Respuesta vacia del coordinador");
            }
            int tsRec = reloj.actualizar(Protocolo.extraerInt(resp, "lamport", 0));
            String resultado = Protocolo.extraer(resp, "resultado");
            String motivo = Protocolo.extraer(resp, "motivo");
            int dentro = Protocolo.extraerInt(resp, "dentro", 0);
            int aforo = Protocolo.extraerInt(resp, "aforo", config.aforo);
            String puertas = Protocolo.extraer(resp, "puertas");

            reloj.registrar(tsRec, "RECIBE_RESPUESTA", resultado + " (" + motivo + ") dentro=" + dentro);

            if ("ESTADO".equals(tipo)) {
                System.out.println("Aforo actual: " + dentro + "/" + aforo + " | Puertas: " + puertas);
            } else {
                System.out.println("Resultado: " + resultado + " | Motivo: " + motivo + " | Dentro: " + dentro + "/" + aforo);
            }
        } catch (IOException e) {
            // E5: Coordinador caido -> priorizamos Consistencia (CP) rechazando la entrada
            reloj.registrarEventoLocal("RECHAZO_LOCAL", "coordinador inaccesible al intentar " + tipo);
            System.out.println("[ERROR] Coordinador no disponible. Operacion " + tipo + " DENEGADA para proteger el aforo.");
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Uso: java -cp bin Puerta <1|2|3>");
            return;
        }
        int id = Integer.parseInt(args[0]);
        Config config = new Config("config.properties");
        new Puerta(id, config).iniciar();
    }
}


}
