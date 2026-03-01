package com.lambda.fusion.authority.model.authentication;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "路由元信息")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NavigationRouteMeta {

    private String title;

    private String authority;

    private String icon;

    private Integer order;

    private Boolean hideInMenu;

    private Boolean keepAlive;

    private String link;

    private String iframeSrc;

    @JsonIgnore
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public boolean containsKey(String key) {
        if (key == null) {
            return false;
        }
        return switch (key) {
            case "title" -> this.title != null;
            case "icon" -> this.icon != null;
            case "order" -> this.order != null;
            case "hideInMenu" -> this.hideInMenu != null;
            case "keepAlive" -> this.keepAlive != null;
            case "link" -> this.link != null;
            case "iframeSrc" -> this.iframeSrc != null;
            default -> this.extra.containsKey(key);
        };
    }

    public void putIfAbsent(String key, Object value) {
        if (key == null || value == null || containsKey(key)) {
            return;
        }
        switch (key) {
            case "title" -> this.title = String.valueOf(value);
            case "icon" -> this.icon = String.valueOf(value);
            case "order" -> this.order = tryToInt(value);
            case "hideInMenu" -> this.hideInMenu = tryToBoolean(value);
            case "keepAlive" -> this.keepAlive = tryToBoolean(value);
            case "link" -> this.link = String.valueOf(value);
            case "iframeSrc" -> this.iframeSrc = String.valueOf(value);
            default -> this.extra.put(key, value);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return this.extra;
    }

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        if (key == null) {
            return;
        }
        this.extra.put(key, value);
    }

    private static Integer tryToInt(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean tryToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if ("true".equalsIgnoreCase(s)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(s)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }
}
