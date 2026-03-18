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
 * GET /api/map-data → retourne pour chaque commune id, name, departement, color, status
 * (couleur calculée à partir des quatre champs du dernier prélèvement).
 */
@WebServlet("/api/map-data")
public class MapDataServlet extends HttpServlet {

    private final MapDataService mapDataService = new MapDataService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        try {
            List<Map<String, Object>> features = mapDataService.getMapFeatures();
            resp.getWriter().write(toJson(features));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Map data error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    private static String toJson(List<Map<String, Object>> features) {
        StringBuilder sb = new StringBuilder().append("{\"features\":[");
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) sb.append(',');
            Map<String, Object> f = features.get(i);
            sb.append("{\"id\":\"").append(escapeJson(String.valueOf(f.get("id"))))
              .append("\",\"name\":\"").append(escapeJson(String.valueOf(f.get("name"))))
              .append("\",\"departement\":\"").append(escapeJson(String.valueOf(f.get("departement"))))
              .append("\",\"color\":\"").append(escapeJson(String.valueOf(f.get("color"))))
              .append("\",\"status\":\"").append(escapeJson(String.valueOf(f.get("status"))))
              .append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
