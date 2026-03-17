package com.waterquality.controller;

import com.waterquality.service.TestService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** 流程：浏览器 → Tomcat → @WebServlet → Service */
@WebServlet("/api/test")
public class TestServlet extends HttpServlet {

    private final TestService testService = new TestService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain; charset=UTF-8");
        resp.getWriter().write(testService.getMessage());
    }
}