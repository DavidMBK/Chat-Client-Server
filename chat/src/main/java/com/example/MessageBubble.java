package com.example;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class MessageBubble extends JPanel {

    public MessageBubble(String message, boolean sentByUser) {
        this.setLayout(new BorderLayout());

        JLabel messageLabel = new JLabel("<html><p style='width: 200px;'>" + message + "</p></html>");
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        if (sentByUser) {
            messageLabel.setBackground(new Color(0, 132, 255));
            messageLabel.setForeground(Color.WHITE);
            this.setLayout(new FlowLayout(FlowLayout.RIGHT));
        } else {
            messageLabel.setBackground(new Color(220, 220, 220));
            messageLabel.setForeground(Color.BLACK);
            this.setLayout(new FlowLayout(FlowLayout.LEFT));
        }

        messageLabel.setOpaque(true);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.add(messageLabel, BorderLayout.CENTER);
        this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    }
}
