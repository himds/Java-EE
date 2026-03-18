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
 * GET /api/details/{prelevementId} → 根据 referenceprel 对应 prélèvement 返回分析列表：
 * { "referenceprel": "...", "analyses": [ { "parametre", "valeurMesuree", "limiteLegale" }, ... ] }
 */
@WebServlet("/api/details/*")
public class DetailsServlet extends HttpServlet {

    private final DetailsService detailsService = new DetailsService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo();
        if (path == null || path.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing prelevement id\"}");
            return;
        }
        String idStr = path.startsWith("/") ? path.substring(1) : path;
        int prelevementId;
        try {
            prelevementId = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid prelevement id\"}");
            return;
        }

        try {
            Object[] out = detailsService.getDetailsByPrelevementId(prelevementId);
            String referenceprel = (String) out[0];
            @SuppressWarnings("unchecked")
            List<ResultatAnalyse> analyses = (List<ResultatAnalyse>) out[1];
            resp.getWriter().write(toJson(referenceprel, analyses));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String message = e.getMessage() == null ? "Details error" : e.getMessage();
            resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    private static String toJson(String referenceprel, List<ResultatAnalyse> analyses) {
        StringBuilder sb = new StringBuilder()
            .append("{\"referenceprel\":\"").append(escapeJson(referenceprel != null ? referenceprel : ""))
            .append("\",\"analyses\":[");
        for (int i = 0; i < analyses.size(); i++) {
            if (i > 0) sb.append(',');
            ResultatAnalyse r = analyses.get(i);
            Double vm = r.getValeurMesuree();
            Double ll = r.getLimiteLegale();
            sb.append("{\"parametre\":\"").append(escapeJson(r.getParametre()))
              .append("\",\"valeurMesuree\":").append(vm == null ? "null" : vm)
              .append(",\"limiteLegale\":").append(ll == null ? "null" : ll)
              .append("}");
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
