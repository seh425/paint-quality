package com.paintshop.quality.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任区域与检查区域的固定映射，需与服务端保持一致。
 */
public final class AreaCatalog {

    private static final Map<String, List<String>> CHECK_AREAS = new LinkedHashMap<>();

    static {
        CHECK_AREAS.put("前处理电泳", Collections.unmodifiableList(
                Arrays.asList("前处理", "电泳", "电泳烘房")));
        CHECK_AREAS.put("密封", Collections.unmodifiableList(
                Arrays.asList("密封线", "UBC", "Antichip", "中涂烘房出口")));
        CHECK_AREAS.put("面漆", Collections.unmodifiableList(
                Arrays.asList("中涂喷房", "中涂烘房", "面漆1线", "面漆2线",
                        "面漆烘房1线", "面漆烘房2线")));
    }

    private AreaCatalog() {
    }

    public static List<String> responsibilities() {
        return Collections.unmodifiableList(Arrays.asList(
                CHECK_AREAS.keySet().toArray(new String[0])));
    }

    public static List<String> checkAreas(String area) {
        if (area == null) {
            return Collections.emptyList();
        }
        List<String> areas = CHECK_AREAS.get(area.trim());
        return areas == null ? Collections.emptyList() : areas;
    }

    public static List<String> allCheckAreas() {
        List<String> all = new java.util.ArrayList<>();
        for (List<String> areas : CHECK_AREAS.values()) {
            all.addAll(areas);
        }
        return all;
    }

    public static String firstCheckArea(String area) {
        List<String> areas = checkAreas(area);
        return areas.isEmpty() ? "" : areas.get(0);
    }
}
