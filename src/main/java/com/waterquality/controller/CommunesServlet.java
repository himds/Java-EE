package com.waterquality.controller;

import com.waterquality.model.Commune;
import com.waterquality.service.CommuneService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** 从 MySQL 拿 communes，返回 JSON。流程：浏览器 → Tomcat → @WebServlet → Service → MySQL */
@WebServlet("/api/communes")
public class CommunesServlet extends HttpServlet {

    private final CommuneService communeService = new CommuneService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        try {
            List<Commune> communes = communeService.getCommunesFromDb();
            resp.getWriter().write(toJson(communes));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String toJson(List<Commune> list) {
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            Commune c = list.get(i);
            sb.append("{\"codeInsee\":\"").append(escapeJson(c.getCodeInsee()))
              .append("\",\"nomCommune\":\"").append(escapeJson(c.getNomCommune()))
              .append("\",\"departement\":\"").append(escapeJson(c.getDepartement())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
