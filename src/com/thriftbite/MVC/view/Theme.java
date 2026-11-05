package com.thriftbite.MVC.view;

import java.awt.Color;
import java.awt.Font;

public class Theme {
    public static final Color PRIMARY = new Color(0, 131, 143);
    public static final Color ACCENT = new Color(230, 126, 34);
    public static final Color BACKGROUND = new Color(248, 249, 250);
    public static final Color TEXT_MAIN = new Color(52, 58, 64);
    public static final Color TEXT_LIGHT = Color.WHITE;

    public static final Font HEADER_FONT = new Font("Arial", Font.BOLD, 24);
    public static final Font STANDARD_FONT = new Font("Arial", Font.PLAIN, 14);
    public static final Font BUTTON_FONT = new Font("Arial", Font.BOLD, 14);

    public static void stylePrimaryButton(javax.swing.JButton btn) {
        btn.setBackground(PRIMARY);
        btn.setForeground(TEXT_LIGHT);
        btn.setFont(BUTTON_FONT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    }

    public static void styleAccentButton(javax.swing.JButton btn) {
        stylePrimaryButton(btn);
        btn.setBackground(ACCENT);
    }
}