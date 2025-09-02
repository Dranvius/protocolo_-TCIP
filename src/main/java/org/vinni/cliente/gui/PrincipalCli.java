package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Base64;

//Control de rutas
import java.io.File;
import java.nio.file.Paths;

//Clase de JSon para ser leido
import org.vinni.cliente.gui.configuracion.configuracionModelo;


// Lector de json
import com.google.gson.Gson;
import java.io.FileReader;


public class PrincipalCli extends JFrame {

    //Archivo exogeno de control de procesos
    // JSON
    Gson gson = new Gson();
    FileReader reader;

    {
        try {
            reader = new FileReader("C:\\Users\\LENOVO\\Documents\\Universidad\\Sistemas_distribuidos\\taller_3\\protocolo_-TCIP\\src\\main\\java\\org\\vinni\\cliente\\gui\\configuracion\\parametros.json");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Convertir a objeto Java
    configuracionModelo configuracion = gson.fromJson(reader, configuracionModelo.class);

    //Pasar a JSon
    private final int[] PORTS = {12345, 12346, 12347, 12348, 12349};


    // Controladores de servidor
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

        try {
            if (socket != null && !socket.isClosed()) socket.close();

            socket = new Socket("localhost", port);
            out = new PrintWriter(socket.getOutputStream(), true);

            // In hace referencia a lo que entra del server
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



                    // Si llegamos aquí, el servidor cerró la conexión
                    ui.mensajesTxt.append("Conexión perdida. Intentando reconectar...\n");
                    reinicio(port);

                } catch (IOException ex) {
                    ui.mensajesTxt.append("Conexión cerrada por el servidor.\n");
                }
            }).start();


        } catch (IOException e) {

            JOptionPane.showMessageDialog(this, "Error conectando: " + e.getMessage());


        }
    }

    /**
    * Verificar si el servidor esta operando
    */

    /**
     * Intentar reconectar al servidor si se cerró la conexión.
     */
    private void reinicio(int puerto) {
        int maxReintentos = configuracion.timeout; // lo tomas del JSON
        int intentos = 0;

        while (intentos < maxReintentos) {
            try {
                ui.mensajesTxt.append("Intentando reconectar al servidor... (" + (intentos + 1) + "/" + maxReintentos + ")\n");

                // Espera 3 segundos antes de reintentar
                Thread.sleep(3000);

                // Intentar reconectar
                socket = new Socket("localhost", puerto);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Reenviar usuario al servidor
                out.println("USER:" + username);

                ui.mensajesTxt.append("Reconexión exitosa al servidor en el puerto " + puerto + " \n");

                // Reiniciar el hilo de escucha
                escucharServidor(puerto);

                return; // salir porque ya se reconectó

            } catch (Exception e) {
                intentos++;
                ui.mensajesTxt.append("Error al reconectar: " + e.getMessage() + "\n");
            }
        }

        ui.mensajesTxt.append("No se pudo reconectar al servidor después de " + maxReintentos + " intentos ❌\n");
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

    /*
    Intenta conectarse de nuevo
    * */
    private void escucharServidor(int port) {
        new Thread(() -> {
            try {
                String fromServer;
                while ((fromServer = in.readLine()) != null) {
                    if (fromServer.startsWith("USERS:")) {
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

                // Si llegamos aquí, el servidor cerró la conexión
                ui.mensajesTxt.append("Conexión perdida. Intentando reconectar...\n");
                reinicio(port);

            } catch (IOException ex) {
                ui.mensajesTxt.append("Conexión cerrada por el servidor. Intentando reconectar...\n");
                reinicio(port);
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}
