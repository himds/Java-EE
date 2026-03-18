package com.waterquality.util;

/**
 * 根据 Prelevement 四个合规字段计算显示颜色与状态文案。
 * 规则：全部合格→绿；健康合格且指标异常→黄；细菌OK且化学不OK→橙；无数据→灰；否则→红。
 */
public final class ConformityColor {

    public static final String GREEN  = "#22c55e";
    public static final String YELLOW = "#eab308";
    public static final String ORANGE = "#f97316";
    public static final String RED    = "#ef4444";
    public static final String GREY   = "#9ca3af";

    private ConformityColor() {}

    private static boolean isConforme(String value) {
        if (value == null) return false;
        return "C".equalsIgnoreCase(value.trim());
    }

    /**
     * 根据四个合规字段返回 [颜色十六进制, 状态文案]。
     * 无数据（四个均为 null/空）时返回灰色。
     */
    public static String[] fromPrelevement(String bacterio, String chimique, String refBact, String refChim) {
        boolean b = isConforme(bacterio);
        boolean c = isConforme(chimique);
        boolean rb = isConforme(refBact);
        boolean rc = isConforme(refChim);

        boolean hasData = (bacterio != null && !bacterio.trim().isEmpty())
            || (chimique != null && !chimique.trim().isEmpty())
            || (refBact != null && !refBact.trim().isEmpty())
            || (refChim != null && !refChim.trim().isEmpty());

        if (!hasData) {
            return new String[] { GREY, "Aucune donnée" };
        }

        if (b && c && rb && rc) {
            return new String[] { GREEN, "Conforme" };
        }

        if (rb && rc && (!b || !c)) {
            return new String[] { YELLOW, "Conformité santé, indicateur(s) anormal(aux)" };
        }

        if (b && rb && (!c || !rc)) {
            return new String[] { ORANGE, "Bactériologie conforme, chimie non conforme" };
        }

        return new String[] { RED, "Non conforme" };
    }
}
