package org.vinni.servidor.gui;

import javax.swing.*;
import java.awt.*;

public class Servidor_interfaz {

    public JRadioButton a12345RadioButton;
    public JRadioButton a12346RadioButton;
    public JRadioButton a12347RadioButton;
    public JRadioButton a12348RadioButton;
    public JRadioButton a12349RadioButton;

    public JTextArea textArea1;
    public JTextArea textArea2;
    public JTextArea textArea3;
    public JTextArea textArea4;
    public JTextArea textArea5;

    public JPanel BG_SERVER;

    public Servidor_interfaz() {
        BG_SERVER = new JPanel();
        BG_SERVER.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.BOTH;

        // Inicializar botones
        a12345RadioButton = new JRadioButton("Puerto 12345");
        a12346RadioButton = new JRadioButton("Puerto 12346");
        a12347RadioButton = new JRadioButton("Puerto 12347");
        a12348RadioButton = new JRadioButton("Puerto 12348");
        a12349RadioButton = new JRadioButton("Puerto 12349");

        // Inicializar áreas de texto
        textArea1 = crearTextAreaConScroll();
        textArea2 = crearTextAreaConScroll();
        textArea3 = crearTextAreaConScroll();
        textArea4 = crearTextAreaConScroll();
        textArea5 = crearTextAreaConScroll();

        // Agregar componentes al panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        BG_SERVER.add(a12345RadioButton, gbc);
        gbc.gridy = 1;
        BG_SERVER.add(a12346RadioButton, gbc);
        gbc.gridy = 2;
        BG_SERVER.add(a12347RadioButton, gbc);
        gbc.gridy = 3;
        BG_SERVER.add(a12348RadioButton, gbc);
        gbc.gridy = 4;
        BG_SERVER.add(a12349RadioButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        BG_SERVER.add(new JScrollPane(textArea1), gbc);
        gbc.gridy = 1;
        BG_SERVER.add(new JScrollPane(textArea2), gbc);
        gbc.gridy = 2;
        BG_SERVER.add(new JScrollPane(textArea3), gbc);
        gbc.gridy = 3;
        BG_SERVER.add(new JScrollPane(textArea4), gbc);
        gbc.gridy = 4;
        BG_SERVER.add(new JScrollPane(textArea5), gbc);
    }

    private JTextArea crearTextAreaConScroll() {
        JTextArea area = new JTextArea(8, 30);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }
}
