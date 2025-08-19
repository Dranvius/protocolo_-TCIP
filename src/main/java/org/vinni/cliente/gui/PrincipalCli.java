package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * author: Vinni 2024
 */
public class PrincipalCli extends javax.swing.JFrame {

    private final int[] PORTS = {12345, 12346, 12347, 12348, 12349}; // Puertos disponibles
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // GUI
    private JComboBox<Integer> comboPuertos;
    private javax.swing.JButton bConectar;
    private javax.swing.JButton btEnviar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea mensajesTxt;
    private JTextField mensajeTxt;

    public PrincipalCli() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {

        this.setTitle("Cliente TCP - Multi Puerto");
        bConectar = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        mensajesTxt = new javax.swing.JTextArea();
        mensajeTxt = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        btEnviar = new javax.swing.JButton();
        comboPuertos = new JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        // Botón Conectar
        bConectar.setFont(new java.awt.Font("Segoe UI", 0, 14));
        bConectar.setText("CONECTAR");
        bConectar.addActionListener(evt -> bConectarActionPerformed());
        getContentPane().add(bConectar);
        bConectar.setBounds(260, 40, 120, 40);

        // Label
        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("CLIENTE TCP : DFRACK");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(110, 10, 250, 17);

        // Mensajes
        mensajesTxt.setColumns(20);
        mensajesTxt.setRows(5);
        mensajesTxt.setEnabled(false);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(30, 210, 410, 110);

        // Mensaje entrada
        mensajeTxt.setFont(new java.awt.Font("Verdana", 0, 14));
        getContentPane().add(mensajeTxt);
        mensajeTxt.setBounds(40, 120, 350, 30);

        jLabel2.setFont(new java.awt.Font("Verdana", 0, 14));
        jLabel2.setText("Mensaje:");
        getContentPane().add(jLabel2);
        jLabel2.setBounds(20, 90, 120, 30);

        // Botón Enviar
        btEnviar.setFont(new java.awt.Font("Verdana", 0, 14));
        btEnviar.setText("Enviar");
        btEnviar.addActionListener(evt -> enviarMensaje());
        getContentPane().add(btEnviar);
        btEnviar.setBounds(327, 160, 120, 27);

        // ComboBox de puertos
        for (int port : PORTS) {
            comboPuertos.addItem(port);
        }
        getContentPane().add(comboPuertos);
        comboPuertos.setBounds(120, 45, 120, 30);

        setSize(new java.awt.Dimension(491, 375));
        setLocationRelativeTo(null);
    }

    private void bConectarActionPerformed() {
        int port = (int) comboPuertos.getSelectedItem();
        conectar(port);
    }

    private void conectar(int port) {
        JOptionPane.showMessageDialog(this, "Conectando al puerto " + port);
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket("localhost", port);
                out = new PrintWriter(socket.getOutputStream(), true);
            }
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            new Thread(() -> {
                try {
                    String fromServer;
                    while ((fromServer = in.readLine()) != null) {
                        mensajesTxt.append("Servidor(" + port + "): " + fromServer + "\n");
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error conectando: " + e.getMessage());
        }
    }

    private void enviarMensaje() {
        if (out != null) {
            out.println(mensajeTxt.getText());
            mensajeTxt.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "No estás conectado a ningún servidor");
        }
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}
