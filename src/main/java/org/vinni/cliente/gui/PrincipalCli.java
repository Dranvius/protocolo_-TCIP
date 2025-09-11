package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.Base64;
import org.vinni.cliente.gui.configuracion.configuracionModelo;
import com.google.gson.Gson;
import java.io.FileReader;
import java.awt.*;

public class PrincipalCli extends JFrame {

    private final Gson gson = new Gson();
    private FileReader reader;

    {
        try {
            // Obtiene la ruta del proyecto actual
            String proyectoPath = System.getProperty("user.dir");
            // Construye la ruta relativa al archivo JSON
            String rutaJson = proyectoPath + File.separator +
                    "src" + File.separator +
                    "main" + File.separator +
                    "java" + File.separator +
                    "org" + File.separator +
                    "vinni" + File.separator +
                    "cliente" + File.separator +
                    "gui" + File.separator +
                    "configuracion" + File.separator +
                    "parametros.json";

            reader = new FileReader(rutaJson);

        } catch (FileNotFoundException e) {
            throw new RuntimeException("No se encontró el archivo de configuración JSON", e);
        }
    }

    private final configuracionModelo configuracion = gson.fromJson(reader, configuracionModelo.class);

    private final int[] PORTS = {12345, 12346, 12347, 12348, 12349};

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String username;
    private final principal_cliente ui;

    // 🔑 Control del hilo de escucha
    private volatile boolean escuchando = false;
    private Thread listenerThread;

    public PrincipalCli() {
        ui = new principal_cliente();

        setContentPane(ui.BG_CLIENT);
        setTitle("Cliente TCP - Multi Puerto");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 450);
        setLocationRelativeTo(null);

        for (int port : PORTS) {
            ui.comboPuertos.addItem(port);
        }

        ui.bConectar.addActionListener(evt -> bConectarActionPerformed());
        ui.btEnviar.addActionListener(evt -> enviarMensaje());
        ui.btEnviarImg.addActionListener(evt -> enviarImagen());
    }

    private void bConectarActionPerformed() {
        int port = (int) ui.comboPuertos.getSelectedItem();

        if (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(this, "Ingrese su nombre de usuario:");
            if (username == null || username.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Debe ingresar un nombre de usuario");
                return;
            }
        }

        conectar(port);
    }

    /**
     * Conexión inicial al servidor
     */
    private void conectar(int port) {
        try {
            cerrarConexionActual();

            socket = new Socket("localhost", port);
            socket.setSoTimeout(0);

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("USER:" + username);
            escucharServidor(port);

            ui.mensajesTxt.append("✔ Conectado al servidor en el puerto " + port + "\n");

        } catch (IOException e) {
            ui.mensajesTxt.append("❌ Error conectando: " + e.getMessage() + "\n");
        }
    }

    /**
     * Cierra cualquier conexión existente
     */
    private void cerrarConexionActual() {
        escuchando = false;
        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
            try {
                listenerThread.join(500);
            } catch (InterruptedException ignored) {}
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    /**
     * Reconexión automática al servidor
     */
    private void reinicio(int puerto) {
        int maxReintentos = configuracion.timeout;
        int intentos = 0;

        while (intentos < maxReintentos) {
            try {
                ui.mensajesTxt.append("Intentando reconectar al servidor... (" + (intentos + 1) + "/" + maxReintentos + ")\n");
                Thread.sleep(3000);

                conectar(puerto); // reconecta usando método seguro
                ui.mensajesTxt.append("✔ Reconexión exitosa al servidor en el puerto " + puerto + "\n");
                return;
            } catch (Exception e) {
                intentos++;
                ui.mensajesTxt.append("Error al reconectar: " + e.getMessage() + "\n");
            }
        }
        ui.mensajesTxt.append("❌ No se pudo reconectar al servidor después de " + maxReintentos + " intentos\n");
    }

    private void enviarMensaje() {
        if (out != null) {
            String msg = ui.mensajeTxt.getText().trim();
            String destinatario = (String) ui.usuariosCombo.getSelectedItem();

            if (!msg.isEmpty()) {
                if (destinatario != null && !destinatario.equals(username)) {
                    out.println("MSGTO:" + destinatario + ":" + msg);
                    ui.mensajesTxt.append("[Privado a " + destinatario + "] " + username + ": " + msg + "\n");
                } else {
                    out.println("MSG:" + msg);
                    ui.mensajesTxt.append(username + ": " + msg + "\n");
                }
                ui.mensajeTxt.setText("");
            }
        } else {
            JOptionPane.showMessageDialog(this, "No estás conectado a ningún servidor");
        }
    }

    private void enviarImagen() {
        if (out != null) {
            JFileChooser chooser = new JFileChooser();
            int option = chooser.showOpenDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                int puertoActual = (int) ui.comboPuertos.getSelectedItem();
                try {
                    byte[] imgBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String encoded = Base64.getEncoder().encodeToString(imgBytes);
                    out.println("IMG:" + encoded);
                    ui.mensajesTxt.append(username + " envió una imagen: " + file.getName() + "\n");

                    // ✅ Log de envío de imagen
                    log(puertoActual, username + " envió una imagen: " + file.getName());

                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Error leyendo imagen: " + e.getMessage());
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "No estás conectado a ningún servidor");
        }
    }

    /**
     * Escucha mensajes del servidor y reintenta si la conexión se cae
     */
    private void escucharServidor(int port) {
        escuchando = false;
        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
            try { listenerThread.join(500); } catch (InterruptedException ignored) {}
        }

        escuchando = true;
        listenerThread = new Thread(() -> {
            try {
                String fromServer;
                while (escuchando && !Thread.currentThread().isInterrupted()) {
                    try {
                        fromServer = in.readLine();
                        if (fromServer == null) throw new IOException("Servidor desconectado");

                        if (fromServer.startsWith("USERS:")) {
                            String[] usuarios = fromServer.substring(6).split(",");
                            SwingUtilities.invokeLater(() -> {
                                ui.usuariosCombo.removeAllItems();
                                for (String u : usuarios) {
                                    if (!u.trim().isEmpty()) ui.usuariosCombo.addItem(u);
                                }
                            });
                        } else if (fromServer.startsWith("IMG:")) {
                            // Decodifica y muestra la imagen, pero no muestra Base64 en el área de mensajes
                            byte[] imgBytes = Base64.getDecoder().decode(fromServer.substring(4));
                            ImageIcon icon = new ImageIcon(imgBytes);
                            Image img = icon.getImage().getScaledInstance(250, 250, Image.SCALE_SMOOTH);
                            icon = new ImageIcon(img);
                            JLabel imgLabel = new JLabel(icon);
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(this, imgLabel, "Imagen recibida", JOptionPane.PLAIN_MESSAGE)
                            );
                            ui.mensajesTxt.append("📷 Imagen recibida desde servidor (" + port + ")\n");
                        } else if (fromServer.startsWith("MSG:")) {
                            String contenido = fromServer.substring(4).trim();
                            if (!contenido.startsWith(username + ":")) {
                                ui.mensajesTxt.append(fromServer + "\n");
                            }
                        } else {
                            ui.mensajesTxt.append(fromServer + "\n");
                        }

                    } catch (IOException timeoutEx) {
                        throw new IOException("Timeout de lectura, servidor caído");
                    }
                }
            } catch (IOException ex) {
                if (escuchando) {
                    ui.mensajesTxt.append("⚠ Conexión perdida. Intentando reconectar...\n");
                    escuchando = false;
                    reinicio(port);
                }
            }
        });
        listenerThread.start();
    }

    // ✅ Método de log
    private void log(int puerto, String mensaje) {
        System.out.println("[Puerto " + puerto + "] " + mensaje);
        // También puedes escribir a un archivo si quieres
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}
