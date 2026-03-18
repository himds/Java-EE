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
 * GET /api/search?q=rou → retourne la liste des communes (pour l'autocomplétion côté front).
 */
@WebServlet("/api/search")
public class SearchServlet extends HttpServlet {

    private final SearchService searchService = new SearchService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String rawQuery = req.getParameter("q");
        if (rawQuery == null || rawQuery.trim().length() < 1) {
            resp.getWriter().write("[]");
            return;
        }

        // Éviter les problèmes d'encodage
        String q = URLDecoder.decode(rawQuery, StandardCharsets.UTF_8.name());

        try {
            List<Commune> communes = searchService.searchCommunesByName(q);
            resp.getWriter().write(toJson(communes));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Search error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    private static String toJson(List<Commune> list) {
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            Commune c = list.get(i);
            sb.append("{\"codeInsee\":\"").append(escapeJson(c.getCodeInsee()))
              .append("\",\"nomCommune\":\"").append(escapeJson(c.getNomCommune()))
              .append("\",\"departement\":\"").append(escapeJson(c.getDepartement()))
              // latitude / longitude calculées côté front à partir du GeoJSON (centroïde), donc null ici
              .append("\",\"latitude\":null,\"longitude\":null}");
        }
        return sb.append(']').toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

