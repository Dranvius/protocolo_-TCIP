package org.vinni.servidor.gui;

import org.vinni.servidor.gui.monitor.MonitorServidor;
import org.vinni.servidor.core.ServidorPuerto;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana principal del servidor multipuerto.
 * - Controla 5 servidores TCP en paralelo (puertos definidos en PUERTOS).
 * - Cada puerto se maneja con un botón y un área de log.
 * - Incluye persistencia de estado y reinicio automático.
 * - Integra un monitor para ver el estado de todos los puertos.
 * - Recibe imágenes desde los clientes y las guarda en disco.
 */
public class PrincipalSrv extends JFrame {

    private final Servidor_interfaz ui;

    // Puertos configurados
    private final int[] PUERTOS = {12345, 12346, 12347, 12348, 12349};

    // Mapas para gestión de servidores, botones y logs
    private final ConcurrentHashMap<Integer, ServidorPuerto> servidores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, JRadioButton> botonesPorPuerto = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, JTextArea> areasPorPuerto = new ConcurrentHashMap<>();

    // Mapas para usuarios conectados por puerto
    private final ConcurrentHashMap<Integer, Set<String>> usuariosPorPuerto = new ConcurrentHashMap<>();

    // Mapas para clientes ya reportados por monitor (solo para evitar mensajes duplicados)
    private final ConcurrentHashMap<Integer, Set<String>> clientesMonitorReportados = new ConcurrentHashMap<>();

    // --- Singleton de instancia activa ---
    private static volatile PrincipalSrv instanciaActiva;

    // --- Monitor asociado ---
    private MonitorServidor monitor;

    // --- Archivo de persistencia ---
    private final Path estadoFile = Paths.get("server_data", "estado_puertos.txt");

    public PrincipalSrv() {
        ui = new Servidor_interfaz();

        setContentPane(ui.BG_SERVER);
        setTitle("Servidor TCP - Multi Puerto");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);

        // Configurar cada puerto con su botón y log
        configurarPuerto(12345, ui.a12345RadioButton, ui.textArea1);
        configurarPuerto(12346, ui.a12346RadioButton, ui.textArea2);
        configurarPuerto(12347, ui.a12347RadioButton, ui.textArea3);
        configurarPuerto(12348, ui.a12348RadioButton, ui.textArea4);
        configurarPuerto(12349, ui.a12349RadioButton, ui.textArea5);

        // Guardar instancia activa
        instanciaActiva = this;

        // Restaurar estado previo
        restaurarEstado();
    }

    // --- acceso desde el monitor ---
    public static PrincipalSrv getInstanciaActiva() {
        return instanciaActiva;
    }

    private void configurarPuerto(int puerto, JRadioButton boton, JTextArea area) {
        botonesPorPuerto.put(puerto, boton);
        areasPorPuerto.put(puerto, area);
        usuariosPorPuerto.put(puerto, ConcurrentHashMap.newKeySet());
        clientesMonitorReportados.put(puerto, ConcurrentHashMap.newKeySet());

        if (boton != null) {
            boton.addActionListener(e -> {
                if (boton.isSelected()) {
                    try {
                        iniciarServidor(puerto);
                        boton.setText("Puerto " + puerto + " ✅ Encendido");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this,
                                "Error al iniciar servidor en puerto " + puerto + ": " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        boton.setSelected(false);
                    }
                } else {
                    detenerServidor(puerto);
                    boton.setText("Puerto " + puerto + " ⛔ Apagado");
                }
            });
        }
    }

    public void iniciarServidor(int puerto) throws IOException {
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

            limpiarClientesReportados(puerto);

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
                } catch (InterruptedException | IOException ignored) {}
            }).start();
        }
    }

    public void log(int puerto, String mensaje) {
        JTextArea area = areasPorPuerto.get(puerto);
        if (area != null) {
            SwingUtilities.invokeLater(() -> area.append(mensaje + "\n"));
        }
    }

    // ---------- Gestión de usuarios ----------
    public void agregarUsuario(int puerto, String usuario) {
        Set<String> usuarios = usuariosPorPuerto.get(puerto);
        if (usuarios != null) usuarios.add(usuario);
    }

    public void removerUsuario(int puerto, String usuario) {
        Set<String> usuarios = usuariosPorPuerto.get(puerto);
        if (usuarios != null) {
            usuarios.remove(usuario);
            clientesMonitorReportados.getOrDefault(puerto, Collections.emptySet()).remove(usuario);
        }
    }

    public Set<String> obtenerUsuarios(int puerto) {
        return usuariosPorPuerto.getOrDefault(puerto, Collections.emptySet());
    }

    // ---------- Gestión de clientes para el monitor ----------
    public boolean clienteYaReportado(int puerto, String ipCliente) {
        Set<String> reportados = clientesMonitorReportados.get(puerto);
        return reportados != null && reportados.contains(ipCliente);
    }

    public void marcarClienteReportado(int puerto, String ipCliente) {
        Set<String> reportados = clientesMonitorReportados.get(puerto);
        if (reportados != null) reportados.add(ipCliente);
    }

    public void limpiarClientesReportados(int puerto) {
        Set<String> reportados = clientesMonitorReportados.get(puerto);
        if (reportados != null) reportados.clear();
    }

    // ---------- Persistencia del estado ----------
    private void guardarEstado() {
        try {
            Files.createDirectories(estadoFile.getParent());
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(estadoFile))) {
                for (int puerto : PUERTOS) {
                    if (servidores.containsKey(puerto)) pw.println("PUERTO:" + puerto);
                }
                if (monitor != null && monitor.isVisible()) pw.println("MONITOR:1");
            }
        } catch (IOException e) {
            System.err.println("No se pudo guardar estado: " + e.getMessage());
        }
    }

    private void restaurarEstado() {
        if (Files.exists(estadoFile)) {
            try {
                for (String linea : Files.readAllLines(estadoFile)) {
                    if (linea.startsWith("PUERTO:")) {
                        int puerto = Integer.parseInt(linea.substring("PUERTO:".length()).trim());
                        iniciarServidor(puerto);
                        JRadioButton boton = botonesPorPuerto.get(puerto);
                        if (boton != null) {
                            boton.setSelected(true);
                            boton.setText("Puerto " + puerto + " ✅ Encendido");
                        }
                    } else if (linea.startsWith("MONITOR:")) {
                        SwingUtilities.invokeLater(() -> {
                            monitor = new MonitorServidor(this);
                            monitor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                            monitor.setVisible(true);
                        });
                    }
                }
            } catch (IOException e) {
                System.err.println("No se pudo restaurar estado: " + e.getMessage());
            }
        }
    }

    public int[] getPuertos() { return PUERTOS.clone(); }

    public JRadioButton getBotonPorPuerto(int puerto) { return botonesPorPuerto.get(puerto); }

    public JTextArea getAreaPorPuerto(int puerto) { return areasPorPuerto.get(puerto); }

    @Override
    public void dispose() {
        guardarEstado();
        for (ServidorPuerto servidor : servidores.values()) {
            try { servidor.detener(); } catch (Exception ignored) {}
        }
        servidores.clear();
        instanciaActiva = null;
        super.dispose();
    }

    public void reiniciarPuerto(int puerto) {
        detenerServidor(puerto);
        log(puerto, "Puerto " + puerto + " reiniciado manualmente desde PrincipalSrv.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PrincipalSrv principal = new PrincipalSrv();
            principal.setVisible(true);

            principal.monitor = new MonitorServidor(principal);
            principal.monitor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            principal.monitor.setVisible(true);
        });
    }
}
