package com.waterquality.controller;

import com.waterquality.service.MapDataService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet générant les données globales pour l'affichage de la carte.
 * Route : GET /api/map-data
 * Retourne une liste de "features" (caractéristiques) pour chaque commune.
 */
@WebServlet("/api/map-data")
public class MapDataServlet extends HttpServlet {

    private final MapDataService mapDataService = new MapDataService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        // Configuration de la réponse en JSON UTF-8
        resp.setContentType("application/json; charset=UTF-8");

        try {
            // Chaque élément de la liste est une Map représentant une commune
            List<Map<String, Object>> features = mapDataService.getMapFeatures();
            
            // On transforme la liste d'objets Java en un objet JSON {"features": [...]}
            resp.getWriter().write(toJson(features));
            
        } catch (Exception e) {
            // Log de l'erreur et réponse 500 en cas de problème (souvent lié à la mémoire ou SQL)
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Map data error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    /**
     * Transforme la liste des communes en format JSON.
     * Cette méthode est optimisée pour traiter un grand volume de données (boucle for).
     */
    private static String toJson(List<Map<String, Object>> features) {
        StringBuilder sb = new StringBuilder().append("{\"features\":[");
        
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) sb.append(','); // Ajoute la virgule entre les communes
            
            Map<String, Object> f = features.get(i);
            
            // On construit l'objet JSON pour une commune
            sb.append("{")
              .append("\"id\":\"").append(escapeJson(String.valueOf(f.get("id")))).append("\",")
              .append("\"name\":\"").append(escapeJson(String.valueOf(f.get("name")))).append("\",")
              .append("\"departement\":\"").append(escapeJson(String.valueOf(f.get("departement")))).append("\",")
              .append("\"color\":\"").append(escapeJson(String.valueOf(f.get("color")))).append("\",")
              .append("\"status\":\"").append(escapeJson(String.valueOf(f.get("status")))).append("\"")
              .append("}");
        }
        
        return sb.append("]}").toString();
    }

    /**
     * Méthode de protection pour nettoyer les caractères spéciaux.
     * Essentiel ici car les noms de communes contiennent souvent des apostrophes ou des tirets.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}