package com.waterquality.controller;

import com.waterquality.service.LatestService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Servlet gérant l'API pour obtenir les données les plus récentes d'une commune.
 * Route : GET /api/latest/{codeInsee}
 */
@WebServlet("/api/latest/*")
public class LatestServlet extends HttpServlet {

    private final LatestService latestService = new LatestService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        // On définit la réponse comme étant du JSON en UTF-8
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo(); // Récupère ce qu'il y a après "/api/latest"
        
        if (path == null || path.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing codeInsee\"}");
            return;
        }

        // Nettoyage de la chaîne pour récupérer le code (ex: "/76540" -> "76540")
        String codeInsee = path.startsWith("/") ? path.substring(1).trim() : path.trim();
        
        if (codeInsee.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing codeInsee\"}");
            return;
        }

        try {
            // Le service retourne une Map contenant deux clés : "commune" et "prelevement"
            Map<String, Object> data = latestService.getLatestByCodeInsee(codeInsee);
            
            Object commune = data.get("commune");
            
            // Si la commune n'existe pas en base, on renvoie une erreur 404
            if (commune == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Commune non trouvée\"}");
                return;
            }

            resp.getWriter().write(toJson(data));

        } catch (Exception e) {
            // Gestion des erreurs serveurs (SQL, etc.)
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Latest error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    /**
     * Convertit manuellement la Map de données en chaîne de caractères JSON.
     * Cette méthode construit deux objets imbriqués : "commune" et "prelevement".
     */
    @SuppressWarnings("unchecked")
    private static String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder().append('{');
        
        // Partie "commune"
        Object commune = data.get("commune");
        if (commune != null) {
            Map<String, Object> c = (Map<String, Object>) commune;
            sb.append("\"commune\":{")
              .append("\"codeInsee\":\"").append(escapeJson(String.valueOf(c.get("codeInsee"))))
              .append("\",\"nomCommune\":\"").append(escapeJson(String.valueOf(c.get("nomCommune"))))
              .append("\",\"departement\":\"").append(escapeJson(String.valueOf(c.get("departement"))))
              .append("\",\"status\":\"").append(escapeJson(String.valueOf(c.get("status"))))
              .append("\",\"color\":\"").append(escapeJson(String.valueOf(c.get("color"))))
              .append("\"},");
        }

        // Partie "prelevement" (peut être null si aucune analyse n'a été faite)
        Object prelevement = data.get("prelevement");
        if (prelevement != null) {
            Map<String, Object> p = (Map<String, Object>) prelevement;
            sb.append("\"prelevement\":{")
              .append("\"id\":").append(p.get("id"))
              .append(",\"referenceprel\":\"").append(escapeJson(String.valueOf(p.get("referenceprel"))))
              .append("\",\"dateprel\":\"").append(escapeJson(String.valueOf(p.get("dateprel"))))
              .append("\",\"color\":\"").append(escapeJson(String.valueOf(p.get("color"))))
              .append("\",\"status\":\"").append(escapeJson(String.valueOf(p.get("status"))))
              .append("\"}");
        } else {
            // Cas où la commune existe mais n'a pas de données d'analyse
            sb.append("\"prelevement\":null");
        }
        
        return sb.append('}').toString();
    }

    /**
     * Nettoie les chaînes de caractères pour éviter de casser la structure du JSON
     * (protection contre les guillemets ou les retours à la ligne).
     */
    private static String escapeJson(String s) {
        if (s == null || "null".equals(s)) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}