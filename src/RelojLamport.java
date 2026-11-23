import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RelojLamport {

private int valor=0;
private int idProceso;
private String rutaLog;
public RelojLamport (int idProceso,String nombreArchivo){
    this.idProceso=idProceso;
    this.rutaLog= "logs/" + nombreArchivo;
    try {
        Files.createDirectories(Paths.get("logss"));


    } catch (IOException e) {
        System.err.println("No  se pudo crear el directorio");
    }

}
    public synchronized  int tick(){
        valor++;
        return valor;
    }
    public synchronized  int actual(int recibido){
    valor= Math.max(valor,recibido) +1;
    return valor;
    }

    public synchronized void registrar(int marca, String evento, String detalle) throws IOException {
    String linea= marca +";"+ idProceso +";" + evento +";" + detalle;
    try(BufferedWriter bw= new BufferedWriter(new FileWriter(rutaLog,true))) {
        bw.write(linea);
        bw.newLine();
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    }
    public synchronized  int registrarEvento(String evento,String detalle ) throws IOException {
    int marca = tick();
    registrar(marca,evento,detalle);
    return  marca;
    }

}
