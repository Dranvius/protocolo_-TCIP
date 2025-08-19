package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;




public class PrincipalSrv extends JFrame {




    private JTextArea mensajesTxt;
    private JCheckBox[] puertoChecks;


    private final int[] PUERTOS = {12345, 12346, 12347, 12348, 12349};


    private ConcurrentHashMap<Integer, ServidorPuerto> servidores = new ConcurrentHashMap<>();

    public PrincipalSrv() {
        initComponents();
    }

    private void initComponents() {
        setTitle("Servidor Multi-Puerto");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLayout(null);

        JLabel titulo = new JLabel("SERVIDOR MULTIPUERTO", SwingConstants.CENTER);
        titulo.setBounds(150, 10, 300, 30);
        add(titulo);

        puertoChecks = new JCheckBox[PUERTOS.length];
        for (int i = 0; i < PUERTOS.length; i++) {
            final int idx = i;                // 👈 variable final para usar en lambda
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
        JScrollPane scroll = new JScrollPane(mensajesTxt);
        scroll.setBounds(200, 50, 370, 280);
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
        SwingUtilities.invokeLater(() -> {
            mensajesTxt.append(mensaje + "\n");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}

// ---------------- CLASE AUXILIAR ----------------
class ServidorPuerto extends Thread {
    private int puerto;
    private PrincipalSrv gui;
    private ServerSocket serverSocket;
    private boolean activo = true;

    public ServidorPuerto(int puerto, PrincipalSrv gui) {
        this.puerto = puerto;
        this.gui = gui;
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(puerto);
            gui.log("Escuchando en puerto " + puerto);
            while (activo) {
                Socket cliente = serverSocket.accept();
                new Thread(() -> manejarCliente(cliente)).start();
            }
        } catch (IOException e) {
            gui.log("Error en puerto " + puerto + ": " + e.getMessage());
        }
    }

    private void manejarCliente(Socket cliente) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()))) {
            String linea;
            while ((linea = in.readLine()) != null) {
                String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                gui.log("[" + hora + "] Puerto " + puerto + " → Cliente: " + linea);
            }
        } catch (IOException e) {
            gui.log("Cliente desconectado en puerto " + puerto);
        }
    }

    public void detener() {
        activo = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}
