package com.example.paintquality.excel;

import com.example.paintquality.common.ApiException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任区域与检查区域的固定映射。
 */
public final class AreaCatalog {

    private static final Map<String, List<String>> CHECK_AREAS = new LinkedHashMap<>();

    static {
        CHECK_AREAS.put("前处理电泳", List.of("前处理", "电泳", "电泳烘房"));
        CHECK_AREAS.put("密封", List.of("密封线", "UBC", "Antichip", "中涂烘房出口"));
        CHECK_AREAS.put("面漆", List.of("中涂喷房", "中涂烘房", "面漆1线", "面漆2线",
                "面漆烘房1线", "面漆烘房2线"));
    }

    private AreaCatalog() {
    }

    public static List<String> responsibilities() {
        return List.copyOf(CHECK_AREAS.keySet());
    }

    public static List<String> checkAreas(String area) {
        if (area == null) {
            return List.of();
        }
        List<String> areas = CHECK_AREAS.get(area.trim());
        return areas == null ? List.of() : areas;
    }

    public static boolean isResponsibility(String area) {
        return area != null && CHECK_AREAS.containsKey(area.trim());
    }

    public static boolean isAllowed(String area, String checkArea) {
        return checkArea != null && checkAreas(area).contains(checkArea.trim());
    }

    public static String requireResponsibility(String area) {
        String normalized = area == null ? "" : area.trim();
        if (!CHECK_AREAS.containsKey(normalized)) {
            throw ApiException.badRequest("责任区域必须是前处理电泳、密封或面漆");
        }
        return normalized;
    }
}
