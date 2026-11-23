import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Protocolo {

    public static String crearSolicitud(String tipo, String token, int puerta, int lamport) {
        return "{\"tipo\":\"" + tipo + "\",\"token\":\"" + token + "\",\"puerta\":" + puerta + ",\"lamport\":" + lamport + "}";
    }

    public static String crearRespuesta(String resultado, String motivo, int dentro, int aforo, String puertas, int lamport) {
        return "{\"tipo\":\"RESPUESTA\",\"resultado\":\"" + resultado + "\",\"motivo\":\"" + motivo +
                "\",\"dentro\":" + dentro + ",\"aforo\":" + aforo + ",\"puertas\":\"" + puertas +
                "\",\"lamport\":" + lamport + "}";
    }

    public static String extraer(String json, String clave) {
        if (json == null) return "";
        Pattern p = Pattern.compile("\"" + clave + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return "";
    }

    public static int extraerInt(String json, String clave, int defecto) {
        String val = extraer(json, clave);
        if (val.isEmpty()) return defecto;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defecto;
        }
    }

    public static String enviarYRecibir(String host, int puerto, String mensaje) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, puerto), 2000);
            socket.setSoTimeout(3000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(mensaje); // Delimitado por salto de linea (\n)
            return in.readLine();
        }
    }
}