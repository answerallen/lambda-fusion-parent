package com.lambda.fusion.authority.authentication.assembler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.authentication.model.MenuRoute;
import com.lambda.fusion.authority.authentication.model.NavigationRouteMeta;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 菜单路由组装器
 * <p>
 * 负责将数据库查询的原始菜单数据组装为前端路由结构，
 * 包括元信息填充、组件解析、重定向设置等
 */
@Component
public class MenuRouteAssembler {

    /**
     * 丰富菜单路由信息
     *
     * @param menuRouteTree 菜单路由树
     */
    public void enrich(List<MenuRoute> menuRouteTree) {
        if (CollUtil.isEmpty(menuRouteTree)) {
            return;
        }
        for (MenuRoute root : menuRouteTree) {
            enrichNode(root);
        }
    }

    private void enrichNode(MenuRoute node) {
        if (node == null) {
            return;
        }
        NavigationRouteMeta meta = node.getMeta();
        if (meta == null) {
            meta = new NavigationRouteMeta();
            node.setMeta(meta);
        }
        meta.putIfAbsent("title", node.getName());
        meta.putIfAbsent("icon", node.getIcon());
        meta.putIfAbsent("order", node.getOrderNo());
        meta.putIfAbsent("hideInMenu", node.isHidden());
        meta.putIfAbsent("keepAlive", node.isKeepAlive());
        if (StrUtil.isBlank(node.getComponent())) {
            String component = resolveComponent(node, meta);
            if (StrUtil.isNotBlank(component)) {
                node.setComponent(component);
            } else {
                String path = StrUtil.removePrefix(node.getPath(), "/");
                if (StrUtil.endWith(path, "/")) {
                    path = StrUtil.removeSuffix(path, "/");
                    path = path + "/index";
                }
                node.setComponent(path);
            }
        }
        if (StrUtil.isBlank(node.getRedirect()) && CollUtil.isNotEmpty(node.getChildren())) {
            MenuRoute firstChild = node.getChildren().getFirst();
            if (firstChild != null && StrUtil.isNotBlank(firstChild.path)) {
                node.setRedirect(firstChild.getPath());
            }
        }
        if (CollUtil.isNotEmpty(node.getChildren())) {
            for (MenuRoute child : node.getChildren()) {
                enrichNode(child);
            }
        }
    }

    private String resolveComponent(MenuRoute node, NavigationRouteMeta meta) {
        if (CollUtil.isNotEmpty(node.getChildren())) {
            return "BasicLayout";
        }
        Integer type = node.getType();
        if (type == null) {
            return null;
        }
        String url = node.getUrl();
        if (type.equals(AuthorityConstants.MenuType.EXTERNAL_LINK.getCode())) {
            if (StrUtil.isNotBlank(url)) {
                meta.putIfAbsent("link", url);
                return "IFrameView";
            }
            return null;
        }
        if (type.equals(AuthorityConstants.MenuType.EMBEDDED_PAGE.getCode())) {
            if (StrUtil.isNotBlank(url)) {
                meta.putIfAbsent("iframeSrc", url);
                return "IFrameView";
            }
            return null;
        }
        return null;
    }
}
