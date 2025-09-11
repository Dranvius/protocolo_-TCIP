package org.vinni.servidor.core;

import org.vinni.servidor.gui.PrincipalSrv;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Clase que representa un servidor en un puerto específico.
 * Se ejecuta en su propio hilo y atiende conexiones de clientes.
 */
public class ServidorPuerto implements Runnable {

    private final int puerto;
    private ServerSocket serverSocket;
    private volatile boolean activo = true;
    private final Thread hilo;
    private final PrincipalSrv gui;

    public ServidorPuerto(int puerto, PrincipalSrv gui) throws IOException {
        this.puerto = puerto;
        this.gui = gui;
        this.serverSocket = new ServerSocket(puerto);
        this.hilo = new Thread(this, "ServidorPuerto-" + puerto);
    }

    /**
     * Inicia el hilo del servidor.
     */
    public void start() {
        hilo.start();
    }

    @Override
    public void run() {
        try {
            gui.log(puerto, "Servidor escuchando en puerto " + puerto);
            while (activo) {
                Socket cliente = serverSocket.accept();
                gui.log(puerto, "Cliente conectado en puerto " + puerto + ": " + cliente.getInetAddress());

                // Atender cliente en un hilo separado
                new Thread(() -> atenderCliente(cliente)).start();
            }
        } catch (IOException e) {
            if (activo) {
                gui.log(puerto, "Error en puerto " + puerto + ": " + e.getMessage());
            } else {
                gui.log(puerto, "Servidor en puerto " + puerto + " detenido.");
            }
        }
    }

    /**
     * Lógica de atención a un cliente.
     */
    private void atenderCliente(Socket cliente) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
             PrintWriter out = new PrintWriter(cliente.getOutputStream(), true)) {

            String linea;
            while ((linea = in.readLine()) != null) {
                gui.log(puerto, "Mensaje recibido de " + cliente.getInetAddress() + ": " + linea);

                // Respuesta de eco
                out.println("Eco desde puerto " + puerto + ": " + linea);
            }

        } catch (IOException e) {
            gui.log(puerto, "Cliente desconectado en puerto " + puerto + ": " + e.getMessage());
        } finally {
            try {
                cliente.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Detiene el servidor y cierra el socket.
     */
    public void detener() {
        activo = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            gui.log(puerto, "Error cerrando puerto " + puerto + ": " + e.getMessage());
        }
    }

    public int getPuerto() {
        return puerto;
    }
}
