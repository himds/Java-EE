package com.waterquality.controller;

import com.waterquality.model.Commune;
import com.waterquality.service.SearchService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servlet gérant la recherche de communes.
 * Route : GET /api/search?q=nom_de_la_ville
 * Utilisé principalement pour l'autocomplétion dans l'interface utilisateur.
 */
@WebServlet("/api/search")
public class SearchServlet extends HttpServlet {

    private final SearchService searchService = new SearchService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        // La réponse sera une liste d'objets au format JSON
        resp.setContentType("application/json; charset=UTF-8");

        String rawQuery = req.getParameter("q");

        // Si la requête est vide ou trop courte, on renvoie un tableau vide []
        if (rawQuery == null || rawQuery.trim().length() < 1) {
            resp.getWriter().write("[]");
            return;
        }

        // On décode la chaîne (ex: "saint%20étienne" devient "saint étienne") 
        // pour gérer correctement les espaces et les accents.
        String q = URLDecoder.decode(rawQuery, StandardCharsets.UTF_8.name());

        try {
            // Le service effectue généralement une requête SQL du type : 
            // SELECT * FROM communes WHERE nom_commune LIKE 'q%'
            List<Commune> communes = searchService.searchCommunesByName(q);
            
            resp.getWriter().write(toJson(communes));

        } catch (Exception e) {
            // Log de l'erreur côté serveur et retour d'un message d'erreur JSON
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Search error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    /**
     * Transforme une liste d'objets 'Commune' en une chaîne JSON.
     * On ne renvoie que le nécessaire : codeInsee, nom et département.
     */
    private static String toJson(List<Commune> list) {
        StringBuilder sb = new StringBuilder().append('[');
        
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(','); // Virgule entre chaque objet
            
            Commune c = list.get(i);
            
            sb.append("{")
              .append("\"codeInsee\":\"").append(escapeJson(c.getCodeInsee())).append("\",")
              .append("\"nomCommune\":\"").append(escapeJson(c.getNomCommune())).append("\",")
              .append("\"departement\":\"").append(escapeJson(c.getDepartement())).append("\",")
              // Les coordonnées GPS ne sont pas envoyées ici pour alléger la réponse,
              // le front-end les déduira de ses propres données géographiques.
              .append("\"latitude\":null,\"longitude\":null")
              .append("}");
        }
        
        return sb.append(']').toString();
    }

    /**
     * Nettoie les chaînes de caractères pour éviter les erreurs de syntaxe JSON
     * liées aux caractères spéciaux (guillemets, barres obliques).
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}