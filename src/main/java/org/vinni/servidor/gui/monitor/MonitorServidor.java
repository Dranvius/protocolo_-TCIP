package org.vinni.servidor.gui.monitor;

import org.vinni.servidor.gui.PrincipalSrv;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Monitor que comprueba periódicamente los puertos activos
 * en la interfaz principal. Si detecta que un puerto no responde,
 * intenta reiniciarlo automáticamente.
 *
 * Si la interfaz original ya no está disponible, crea una nueva
 * instancia de PrincipalSrv y levanta el puerto allí.
 */
public class MonitorServidor extends JFrame implements Runnable {

    private PrincipalSrv servidorGUI;
    private final JTextArea areaLogs;
    private volatile boolean activo = true;
    private final int intervaloMs = 5000; // 5 segundos de chequeo

    public MonitorServidor(PrincipalSrv servidorGUI) {
        this.servidorGUI = servidorGUI;

        setTitle("Monitor de Servidor");
        setSize(420, 320);
        setLayout(new BorderLayout());

        areaLogs = new JTextArea();
        areaLogs.setEditable(false);
        JScrollPane scroll = new JScrollPane(areaLogs);
        add(scroll, BorderLayout.CENTER);

        // detener el monitor al cerrar la ventana
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                detener();
            }
        });

        // Centrar respecto al servidor
        setLocationRelativeTo(servidorGUI);
        setVisible(true);

        // Arrancar el hilo en background
        new Thread(this, "MonitorServidor").start();
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Thread.sleep(intervaloMs);

                // si la GUI principal ya no existe, recrearla
                if (servidorGUI == null || !servidorGUI.isDisplayable()) {
                    SwingUtilities.invokeLater(() -> {
                        servidorGUI = new PrincipalSrv();
                        servidorGUI.setVisible(true);
                        log("⚠ El servidor principal estaba cerrado. Se creó una nueva instancia.");
                    });
                    continue;
                }

                int[] puertos = servidorGUI.getPuertos();
                for (int puerto : puertos) {
                    JRadioButton boton = servidorGUI.getBotonPorPuerto(puerto);

                    // si no existe botón o está apagado -> ignoramos
                    if (boton == null || !boton.isSelected()) continue;

                    boolean vivo = estaVivo(puerto);
                    if (!vivo) {
                        String msg = "⚠ Puerto " + puerto + " caído. Intentando reactivar...";
                        log(msg);
                        servidorGUI.log(puerto, msg);

                        try {
                            servidorGUI.iniciarServidor(puerto);
                            marcarPuertoActivo(puerto);
                            log("✔ Puerto " + puerto + " reiniciado en la instancia actual.");
                        } catch (Exception ex) {
                            String err = "(!) Error al intentar iniciar puerto " + puerto + ": " + ex.getMessage();
                            log(err);
                            servidorGUI.log(puerto, err);

                            // si falla, levantar nueva ventana de servidor
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(this,
                                        "El puerto " + puerto + " será reiniciado en una nueva ventana de servidor.",
                                        "Monitor de Servidor",
                                        JOptionPane.INFORMATION_MESSAGE);

                                PrincipalSrv nuevoServidor = new PrincipalSrv();
                                nuevoServidor.setVisible(true);
                                try {
                                    nuevoServidor.iniciarServidor(puerto);
                                } catch (IOException e) {
                                    log("(!) No se pudo reiniciar puerto " + puerto + " en nueva instancia: " + e.getMessage());
                                }

                                servidorGUI = nuevoServidor;
                                marcarPuertoActivo(puerto);
                                log("✔ Puerto " + puerto + " reiniciado en nueva instancia.");
                            });
                        }
                    } else {
                        log("✔ Puerto " + puerto + " está activo.");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                activo = false;
            }
        }
    }

    /**
     * Verifica si un puerto responde.
     */
    private boolean estaVivo(int puerto) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", puerto), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Marca el puerto como activo en la GUI principal.
     */
    private void marcarPuertoActivo(int puerto) {
        SwingUtilities.invokeLater(() -> {
            JRadioButton b = servidorGUI.getBotonPorPuerto(puerto);
            if (b != null) {
                b.setSelected(true);
                b.setText("Puerto " + puerto + " ✅ Encendido");
            }
        });
    }

    /**
     * Log de mensajes dentro del monitor.
     */
    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            areaLogs.append(msg + "\n");
            areaLogs.setCaretPosition(areaLogs.getDocument().getLength());
        });
    }

    /**
     * Detiene el monitor.
     */
    public void detener() {
        activo = false;
        SwingUtilities.invokeLater(this::dispose);
    }
}
