package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.Base64;

public class PrincipalCli extends JFrame {

    private final int[] PORTS = {12345, 12346, 12347, 12348, 12349};
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username; // Nombre de usuario del cliente

    private final principal_cliente ui; // referencia al form generado

    public PrincipalCli() {
        // Instancia del formulario
        ui = new principal_cliente();

        // Montar el panel raíz en el JFrame
        setContentPane(ui.BG_CLIENT);
        setTitle("Cliente TCP - Multi Puerto");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 450);
        setLocationRelativeTo(null);

        // Llenar el combo con los puertos disponibles
        for (int port : PORTS) {
            ui.comboPuertos.addItem(port);
        }

        // Eventos de botones
        ui.bConectar.addActionListener(evt -> bConectarActionPerformed());
        ui.btEnviar.addActionListener(evt -> enviarMensaje());
        ui.btEnviarImg.addActionListener(evt -> enviarImagen());
    }

    /**
     * Acción del botón Conectar
     */
    private void bConectarActionPerformed() {
        int port = (int) ui.comboPuertos.getSelectedItem();

        // Pedir nombre de usuario si aún no existe
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
     * Conexión al servidor
     */
    private void conectar(int port) {
        JOptionPane.showMessageDialog(this, "Conectando al puerto " + port);
        try {
            if (socket != null && !socket.isClosed()) socket.close();

            socket = new Socket("localhost", port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Enviar nombre de usuario al servidor
            out.println("USER:" + username);

            // Hilo para escuchar respuestas del servidor
            new Thread(() -> {
                try {
                    String fromServer;
                    while ((fromServer = in.readLine()) != null) {
                        if (fromServer.startsWith("USERS:")) {
                            // actualizar lista de usuarios
                            String[] usuarios = fromServer.substring(6).split(",");
                            SwingUtilities.invokeLater(() -> {
                                ui.usuariosCombo.removeAllItems();
                                for (String u : usuarios) {
                                    if (!u.trim().isEmpty()) {
                                        ui.usuariosCombo.addItem(u);
                                    }
                                }
                            });
                        } else {
                            ui.mensajesTxt.append("Servidor(" + port + "): " + fromServer + "\n");
                        }
                    }
                } catch (IOException ex) {
                    ui.mensajesTxt.append("Conexión cerrada por el servidor.\n");
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error conectando: " + e.getMessage());
        }
    }

    /**
     * Enviar mensaje (público o privado según destinatario)
     */
    private void enviarMensaje() {
        if (out != null) {
            String msg = ui.mensajeTxt.getText().trim();
            String destinatario = (String) ui.usuariosCombo.getSelectedItem();

            if (!msg.isEmpty()) {
                if (destinatario != null && !destinatario.equals(username)) {
                    // Mensaje privado
                    out.println("MSGTO:" + destinatario + ":" + msg);
                    ui.mensajesTxt.append("[Privado a " + destinatario + "] " + username + ": " + msg + "\n");
                } else {
                    // Mensaje público
                    out.println("MSG:" + msg);
                    ui.mensajesTxt.append(username + ": " + msg + "\n");
                }
                ui.mensajeTxt.setText("");
            }
        } else {
            JOptionPane.showMessageDialog(this, "No estás conectado a ningún servidor");
        }
    }

    /**
     * Enviar imagen (codificada en Base64)
     */
    private void enviarImagen() {
        if (out != null) {
            JFileChooser chooser = new JFileChooser();
            int option = chooser.showOpenDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    byte[] imgBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String encoded = Base64.getEncoder().encodeToString(imgBytes);
                    out.println("IMG:" + encoded);
                    ui.mensajesTxt.append(username + " envió una imagen: " + file.getName() + "\n");
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Error leyendo imagen: " + e.getMessage());
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "No estás conectado a ningún servidor");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}
