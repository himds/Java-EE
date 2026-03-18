package com.waterquality.controller;

import com.waterquality.model.ResultatAnalyse;
import com.waterquality.service.DetailsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Servlet gérant l'API de consultation des détails d'un prélèvement d'eau.
 * L'URL attendue est de type : /api/details/{id}
 */
@WebServlet("/api/details/*")
public class DetailsServlet extends HttpServlet {

    private final DetailsService detailsService = new DetailsService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        // On définit le type de réponse en JSON avec l'encodage UTF-8 pour les accents
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo(); // Récupère la partie après "/api/details"
        
        if (path == null || path.equals("/")) {
            // Si l'ID est manquant (ex: /api/details/), on renvoie une erreur 400
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing prelevement id\"}");
            return;
        }

        // On nettoie la chaîne pour obtenir uniquement l'ID (ex: "/123" -> "123")
        String idStr = path.startsWith("/") ? path.substring(1) : path;
        int prelevementId;
        try {
            prelevementId = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            // Si l'ID n'est pas un nombre valide, erreur 400
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid prelevement id\"}");
            return;
        }

        try {
            // Le service retourne un tableau d'objets : [0] = Référence (String), [1] = Liste d'analyses (List)
            Object[] out = detailsService.getDetailsByPrelevementId(prelevementId);
            
            String referenceprel = (String) out[0];
            @SuppressWarnings("unchecked")
            List<ResultatAnalyse> analyses = (List<ResultatAnalyse>) out[1];

            // On transforme les données Java en format JSON texte
            resp.getWriter().write(toJson(referenceprel, analyses));

        } catch (Exception e) {
            // En cas d'erreur serveur (ex: SQL), on logue l'erreur et on renvoie une 500
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Details error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    /**
     * Méthode utilitaire pour construire manuellement la chaîne JSON.
     */
    private static String toJson(String referenceprel, List<ResultatAnalyse> analyses) {
        StringBuilder sb = new StringBuilder()
            .append("{\"referenceprel\":\"").append(escapeJson(referenceprel != null ? referenceprel : ""))
            .append("\",\"analyses\":[");

        for (int i = 0; i < analyses.size(); i++) {
            if (i > 0) sb.append(','); // Ajoute une virgule entre chaque objet de la liste
            
            ResultatAnalyse r = analyses.get(i);
            Double vm = r.getValeurMesuree();
            Double ll = r.getLimiteLegale();

            sb.append("{")
              .append("\"parametre\":\"").append(escapeJson(r.getParametre())).append("\",")
              .append("\"valeurMesuree\":").append(vm == null ? "null" : vm).append(",")
              .append("\"limiteLegale\":").append(ll == null ? "null" : ll)
              .append("}");
        }
        return sb.append("]}").toString();
    }

    /**
     * Sécurise les chaînes de caractères pour qu'elles ne cassent pas le format JSON.
     * Échappe les guillemets, les barres obliques et les retours à la ligne.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}