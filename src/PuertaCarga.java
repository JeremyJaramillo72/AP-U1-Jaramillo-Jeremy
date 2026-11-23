import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class PruebaCarga {

    public static void main(String[] args) throws Exception {
        Config config = new Config("config.properties");
        int hilosPorPuerta = 10;
        int totalHilos = hilosPorPuerta * 3;

        CountDownLatch listo = new CountDownLatch(totalHilos);
        CountDownLatch disparar = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(totalHilos);

        AtomicInteger permitidas = new AtomicInteger(0);
        AtomicInteger rechazadas = new AtomicInteger(0);
        AtomicInteger errores = new AtomicInteger(0);
        AtomicInteger relojSimulado = new AtomicInteger(1);

        for (int puerta = 1; puerta <= 3; puerta++) {
            final int idPuerta = puerta;
            for (int i = 0; i < hilosPorPuerta; i++) {
                new Thread(() -> {
                    listo.countDown();
                    try {
                        disparar.await(); // Todos los 30 hilos salen al mismo tiempo
                        int ts = relojSimulado.getAndIncrement();
                        String req = Protocolo.crearSolicitud("ENTRAR", config.token, idPuerta, ts);
                        String resp = Protocolo.enviarYRecibir(config.host, config.puerto, req);
                        String resultado = Protocolo.extraer(resp, "resultado");

                        if ("PERMITIDA".equals(resultado)) {
                            permitidas.incrementAndGet();
                        } else {
                            rechazadas.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errores.incrementAndGet();
                    } finally {
                        fin.countDown();
                    }
                }).start();
            }
        }

        listo.await();
        disparar.countDown();
        fin.await();

r
        String reqEstado = Protocolo.crearSolicitud("ESTADO", config.token, 1, relojSimulado.getAndIncrement());
        String respEstado = Protocolo.enviarYRecibir(config.host, config.puerto, reqEstado);
        int registradasCoord = Protocolo.extraerInt(respEstado, "dentro", -1);

        System.out.println("=== RESULTADOS PruebaCarga (30 solicitudes, aforo=" + config.aforo + ") ===");
        System.out.println("Solicitudes PERMITIDAS : " + permitidas.get());
        System.out.println("Solicitudes RECHAZADAS : " + rechazadas.get());
        System.out.println("Errores de conexion    : " + errores.get());
        System.out.println("Personas en coordinador: " + registradasCoord);
    }
}