import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private  int aforo;
    private String token;
    private  String host;
    private int puerto;
    private int intervaloLatidos;
    private int timeoutLatidosMs;
    public Config(String ruta) throws IOException{
        Properties props =new Properties();
        try (FileInputStream fis= new FileInputStream(ruta)){
            props.load(fis);

        }
        this.aforo= Integer.parseInt(props.getProperty("aforo","10").trim());
        this.token= props.getProperty("token","Coliseo2026");
        this.host= props.getProperty("host","127.0.0.1").trim();
        this.puerto=Integer.parseInt(props.getProperty("puerto","5000").trim());
        this.intervaloLatidos= Integer.parseInt(props.getProperty("Latidos.intervalo.ms","2000").trim());
        this.timeoutLatidosMs =Integer.parseInt(props.getProperty("latidos.timeout.ms","6000").trim());
    }









}
