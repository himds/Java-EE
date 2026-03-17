package com.waterquality.service;

/** 流程：浏览器 → Tomcat → @WebServlet → Service */
public class TestService {

    public String getMessage() {
        return "Backend is running!";
    }
}
