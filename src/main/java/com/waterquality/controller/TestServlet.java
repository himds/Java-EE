package com.waterquality.controller;

import com.waterquality.service.TestService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Servlet de test.
 * Utile pour vérifier que le projet est bien déployé et que l'injection du service fonctionne.
 */
@WebServlet("/api/test")
public class TestServlet extends HttpServlet {

    private final TestService testService = new TestService();

    /**
     * Gère les requêtes HTTP GET.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        // On indique au navigateur que la réponse est du texte simple en UTF-8
        resp.setContentType("text/plain; charset=UTF-8");
        
        // On récupère le message depuis la couche "Service" et on l'écrit dans la réponse
        resp.getWriter().write(testService.getMessage());
    }
}