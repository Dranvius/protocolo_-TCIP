package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PrincipalSrv extends JFrame {


    private final Servidor_interfaz ui;


    private final int[] PUERTOS = {12345, 12346, 12347, 12348, 12349};


    private final ConcurrentHashMap<Integer, ServidorPuerto> servidores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, JRadioButton> botonesPorPuerto = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, JTextArea> areasPorPuerto = new ConcurrentHashMap<>();

    public PrincipalSrv() {
        ui = new Servidor_interfaz();

        setContentPane(ui.BG_SERVER);
        setTitle("Servidor TCP - Multi Puerto");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);


        configurarPuerto(12345, ui.a12345RadioButton, ui.textArea1);
        configurarPuerto(12346, ui.a12346RadioButton, ui.textArea2);
        configurarPuerto(12347, ui.a12347RadioButton, ui.textArea3);
        configurarPuerto(12348, ui.a12348RadioButton, ui.textArea4);
        configurarPuerto(12349, ui.a12349RadioButton, ui.textArea5);
    }

    private void configurarPuerto(int puerto, JRadioButton boton, JTextArea area) {
        botonesPorPuerto.put(puerto, boton);
        areasPorPuerto.put(puerto, area);

        boton.addActionListener(e -> {
            if (boton.isSelected()) {
                iniciarServidor(puerto);
                boton.setText("Puerto " + puerto + " ✅ Encendido");
            } else {
                detenerServidor(puerto);
                boton.setText("Puerto " + puerto + " ⛔ Apagado");
            }
        });
    }

    private void iniciarServidor(int puerto) {
        if (!servidores.containsKey(puerto)) {
            ServidorPuerto servidor = new ServidorPuerto(puerto, this);
            servidores.put(puerto, servidor);
            servidor.start();
            log(puerto, "Servidor iniciado en puerto " + puerto);
        }
    }

    private void detenerServidor(int puerto) {
        ServidorPuerto servidor = servidores.remove(puerto);
        if (servidor != null) {
            servidor.detener();
            log(puerto, "Servidor detenido en puerto " + puerto);

            // Reinicio automático después de 3 segundos
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    iniciarServidor(puerto);
                    SwingUtilities.invokeLater(() -> {
                        JRadioButton boton = botonesPorPuerto.get(puerto);
                        if (boton != null) {
                            boton.setSelected(true);
                            boton.setText("Puerto " + puerto + " ✅ Encendido");
                        }
                    });
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }


    public void log(int puerto, String mensaje) {
        JTextArea area = areasPorPuerto.get(puerto);
        if (area != null) {
            SwingUtilities.invokeLater(() -> area.append(mensaje + "\n"));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}

// ---------------- CLASE AUXILIAR ----------------
class ServidorPuerto extends Thread {

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TS_FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final int puerto;
    private final PrincipalSrv gui;
    private ServerSocket serverSocket;
    private volatile boolean activo = true;

    private final ConcurrentHashMap<Socket, ClienteInfo> clientes = new ConcurrentHashMap<>();

    private final Path dirBase;
    private final Path dirLogs;
    private final Path dirImgs;

    public ServidorPuerto(int puerto, PrincipalSrv gui) {
        this.puerto = puerto;
        this.gui = gui;

        this.dirBase = Paths.get("server_data", "port_" + puerto);
        this.dirLogs = dirBase.resolve("logs");
        this.dirImgs = dirBase.resolve("images");

        try {
            Files.createDirectories(dirLogs);
            Files.createDirectories(dirImgs);
        } catch (IOException e) {
            gui.log(puerto, "No se pudieron crear directorios para el puerto " + puerto + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        while (activo) {
            try (ServerSocket ss = new ServerSocket(puerto)) {
                this.serverSocket = ss;
                gui.log(puerto, "Servidor activo en el puerto " + puerto);

                while (activo) {
                    Socket clientSocket = ss.accept();
                    ClienteInfo info = new ClienteInfo(clientSocket);
                    clientes.put(clientSocket, info);

                    new Thread(() -> manejarCliente(info)).start();
                }

            } catch (IOException e) {
                if (activo) {
                    gui.log(puerto, "⚠ Error en puerto " + puerto + ": " + e.getMessage());
                    gui.log(puerto, "⏳ Reiniciando servidor en 3 segundos...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
        }
    }

    private void manejarCliente(ClienteInfo info) {
        Socket cliente = info.socket;
        String usuario = info.usuario;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()))) {
            String primera = in.readLine();
            if (primera != null && primera.startsWith("USER:")) {
                usuario = limpiarUsuario(primera.substring("USER:".length()).trim());
                info.usuario = usuario;
            }
            String horaCon = LocalTime.now().format(HORA_FMT);
            logYArchivo("** " + usuario + " se ha conectado a las " + horaCon + " **");
            broadcastSistema(usuario + " se ha conectado.");
            broadcastUsuarios();

            String linea = primera;
            while (linea != null) {
                if (linea.startsWith("MSG:")) {
                    String contenido = linea.substring("MSG:".length()).trim();
                    String hora = LocalTime.now().format(HORA_FMT);
                    String registro = usuario + " [" + hora + "]: " + contenido;
                    logYArchivo(registro);
                    broadcast("MSG:" + usuario + "|" + hora + "|" + contenido);
                } else if (linea.startsWith("MSGTO:")) {
                    String[] parts = linea.split(":", 3);
                    if (parts.length == 3) {
                        String destinatario = parts[1].trim();
                        String contenido = parts[2].trim();
                        enviarPrivado(destinatario, "[Privado de " + usuario + "]: " + contenido);
                    }
                } else if (linea.startsWith("IMG:")) {
                    String base64 = linea.substring("IMG:".length()).trim();
                    String hora = LocalTime.now().format(HORA_FMT);
                    String nombreArchivo = guardarImagen(usuario, base64);
                    String registro = usuario + " [" + hora + "] envió imagen: " + nombreArchivo;
                    logYArchivo(registro);
                    broadcast("IMG:" + usuario + "|" + hora + "|" + base64);
                }
                linea = in.readLine();
            }
        } catch (IOException ignored) {
        } finally {
            clientes.remove(cliente);
            try { cliente.close(); } catch (IOException ignored) {}
            String hora = LocalTime.now().format(HORA_FMT);
            logYArchivo("** " + usuario + " se ha desconectado a las " + hora + " **");
            broadcastSistema(usuario + " se ha desconectado.");
            broadcastUsuarios();
        }
    }

    // ---------- Usuarios conectados ----------
    private void broadcastUsuarios() {
        String lista = String.join(",", usuariosConectados());
        for (ClienteInfo ci : clientes.values()) {
            try {
                ci.out.println("USERS:" + lista);
            } catch (Exception ignored) {}
        }
    }

    private void enviarPrivado(String destinatario, String mensaje) {
        for (ClienteInfo ci : clientes.values()) {
            if (ci.usuario.equals(destinatario)) {
                ci.out.println(mensaje);
                return;
            }
        }
    }

    private void broadcast(String mensaje) {
        for (Map.Entry<Socket, ClienteInfo> entry : clientes.entrySet()) {
            try {
                entry.getValue().out.println(mensaje);
            } catch (Exception ignored) {}
        }
    }

    private void broadcastSistema(String texto) {
        String hora = LocalTime.now().format(HORA_FMT);
        broadcast("SYS:" + hora + "|" + texto);
    }

    private String guardarImagen(String usuario, String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            String ext = detectarExtension(bytes);
            String seguro = usuario.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
            String ts = LocalDateTime.now().format(TS_FILE_FMT);
            String fileName = ts + "_" + seguro + "." + ext;
            Path destino = dirImgs.resolve(fileName);
            Files.write(destino, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            return destino.getFileName().toString();
        } catch (Exception e) {
            logYArchivo("(!) Error guardando imagen: " + e.getMessage());
            return "(error)";
        }
    }

    private String detectarExtension(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "jpg";
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "gif";
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') return "bmp";
        return "bin";
    }

    private synchronized void logYArchivo(String linea) {
        gui.log(puerto, linea);
        String nombreLog = "log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
        Path logFile = dirLogs.resolve(nombreLog);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logFile.toFile(), true), "UTF-8"), true)) {
            pw.println(linea);
        } catch (IOException e) {
            gui.log(puerto, "No se pudo escribir en el log: " + e.getMessage());
        }
    }

    private String limpiarUsuario(String u) {
        if (u == null || u.trim().isEmpty()) return "Usuario";
        String v = u.trim();
        if (v.length() > 32) v = v.substring(0, 32);
        return v.replaceAll("[\\r\\n\\t|]", "_");
    }

    private String[] usuariosConectados() {
        return clientes.values().stream().map(ci -> ci.usuario).distinct().sorted().toArray(String[]::new);
    }

    public void detener() {
        activo = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) {}
        cerrarTodo();
    }

    private void cerrarTodo() {
        for (Map.Entry<Socket, ClienteInfo> e : clientes.entrySet()) {
            try {
                e.getKey().close();
            } catch (IOException ignored) {}
        }
        clientes.clear();
    }

    private static class ClienteInfo {
        final Socket socket;
        final PrintWriter out;
        volatile String usuario;

        ClienteInfo(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.usuario = "Anon_" + Integer.toHexString(System.identityHashCode(socket));
        }
    }
}
