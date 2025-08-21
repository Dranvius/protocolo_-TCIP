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

    private JTextArea mensajesTxt;
    private JCheckBox[] puertoChecks;

    private final int[] PUERTOS = {12345, 12346, 12347, 12348, 12349};

    private final ConcurrentHashMap<Integer, ServidorPuerto> servidores = new ConcurrentHashMap<>();

    public PrincipalSrv() {
        initComponents();
    }

    private void initComponents() {
        setTitle("Servidor Multi-Puerto");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 460);
        setLayout(null);

        JLabel titulo = new JLabel("SERVIDOR MULTIPUERTO", SwingConstants.CENTER);
        titulo.setBounds(200, 10, 300, 30);
        add(titulo);

        puertoChecks = new JCheckBox[PUERTOS.length];
        for (int i = 0; i < PUERTOS.length; i++) {
            final int idx = i;
            int puerto = PUERTOS[i];

            puertoChecks[i] = new JCheckBox("Puerto " + puerto);
            puertoChecks[i].setBounds(20, 50 + (i * 30), 150, 25);
            add(puertoChecks[i]);

            puertoChecks[i].addActionListener(e -> {
                if (puertoChecks[idx].isSelected()) {
                    iniciarServidor(puerto);
                } else {
                    detenerServidor(puerto);
                }
            });
        }

        mensajesTxt = new JTextArea();
        mensajesTxt.setEditable(false);
        JScrollPane scroll = new JScrollPane(mensajesTxt);
        scroll.setBounds(200, 50, 470, 340);
        add(scroll);

        setLocationRelativeTo(null);
    }

    private void iniciarServidor(int puerto) {
        if (!servidores.containsKey(puerto)) {
            ServidorPuerto servidor = new ServidorPuerto(puerto, this);
            servidores.put(puerto, servidor);
            servidor.start();
            log("Servidor iniciado en puerto " + puerto);
        }
    }

    private void detenerServidor(int puerto) {
        ServidorPuerto servidor = servidores.remove(puerto);
        if (servidor != null) {
            servidor.detener();
            log("Servidor detenido en puerto " + puerto);
        }
    }

    public void log(String mensaje) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(mensaje + "\n"));
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
            gui.log("No se pudieron crear directorios de datos para el puerto " + puerto + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(puerto);
            gui.log("Escuchando en puerto " + puerto);
            while (activo) {
                Socket cliente = serverSocket.accept();
                cliente.setTcpNoDelay(true);

                PrintWriter out = new PrintWriter(new OutputStreamWriter(cliente.getOutputStream()), true);
                ClienteInfo info = new ClienteInfo(cliente, out, generarAliasTemporal(cliente));
                clientes.put(cliente, info);

                new Thread(() -> manejarCliente(info)).start();
            }
        } catch (SocketException se) {
            if (activo) gui.log("Error de socket en puerto " + puerto + ": " + se.getMessage());
        } catch (IOException e) {
            if (activo) gui.log("Error en puerto " + puerto + ": " + e.getMessage());
        } finally {
            cerrarTodo();
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
            logYArchivo("** " + usuario + " se ha conectado (puerto " + puerto + ") a las " + horaCon + " **");
            broadcastSistema(usuario + " se ha conectado.");
            broadcastUsuarios(); // 🔑 enviar lista de usuarios al conectarse

            String linea = primera;
            while (linea != null) {
                if (linea.startsWith("MSG:")) {
                    String contenido = linea.substring("MSG:".length()).trim();
                    String hora = LocalTime.now().format(HORA_FMT);
                    String registro = usuario + " envió a las " + hora + " esto : " + contenido;
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
                    String registro = usuario + " envió a las " + hora + " esto : [imagen guardada en " + nombreArchivo + "]";
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
            logYArchivo("** " + usuario + " se ha desconectado (puerto " + puerto + ") a las " + hora + " **");
            broadcastSistema(usuario + " se ha desconectado.");
            broadcastUsuarios(); // 🔑 enviar lista de usuarios al desconectarse
        }
    }

    // ----------------- NUEVOS MÉTODOS -----------------
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

    // ----------------- MÉTODOS EXISTENTES -----------------
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
            return dirBase.relativize(destino).toString().replace('\\', '/');
        } catch (Exception e) {
            logYArchivo("(!) Error guardando imagen: " + e.getMessage());
            return "(error_al_guardar_imagen)";
        }
    }

    private String detectarExtension(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') return "bmp";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return "bin";
    }

    private synchronized void logYArchivo(String linea) {
        gui.log(linea);
        String nombreLog = "log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
        Path logFile = dirLogs.resolve(nombreLog);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logFile.toFile(), true), "UTF-8"), true)) {
            pw.println(linea);
        } catch (IOException e) {
            gui.log("No se pudo escribir en el log (" + logFile + "): " + e.getMessage());
        }
    }

    private String generarAliasTemporal(Socket s) {
        String suf = Integer.toHexString(System.identityHashCode(s)).toUpperCase();
        return "Anon_" + suf.substring(Math.max(0, suf.length() - 4));
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

        ClienteInfo(Socket socket, PrintWriter out, String usuario) {
            this.socket = socket;
            this.out = out;
            this.usuario = usuario;
        }
    }
}
