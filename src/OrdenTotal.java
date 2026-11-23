import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OrdenTotal {

    record Registro(int lamport, int idProceso, String linea) {}

    public static void main(String[] args) throws IOException {
        String[] archivos = {
                "logs/coordinador.log",
                "logs/puerta1.log",
                "logs/puerta2.log",
                "logs/puerta3.log"
        };

        List<Registro> registros = new ArrayList<>();

        for (String ruta : archivos) {
            Path p = Paths.get(ruta);
            if (!Files.exists(p)) {
                System.out.println("Aviso: no existe " + ruta + ", se omite.");
                continue;
            }
            for (String linea : Files.readAllLines(p)) {
                if (linea.isBlank()) continue;
                String[] partes = linea.split(";", 4);
                if (partes.length < 4) continue;
                int lamport = Integer.parseInt(partes[0].trim());
                int idProceso = Integer.parseInt(partes[1].trim());
                registros.add(new Registro(lamport, idProceso, linea.trim()));
            }
        }

        registros.sort(Comparator.comparingInt(Registro::lamport)
                .thenComparingInt(Registro::idProceso));

        Files.createDirectories(Paths.get("logs"));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("logs/orden_total.log"))) {
            for (Registro r : registros) {
                bw.write(r.linea());
                bw.newLine();
            }
        }

        System.out.println("logs/orden_total.log generado con " + registros.size() + " eventos ordenados.");
    }
}