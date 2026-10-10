package com.tavern.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 鉴权拦截器的放行/拒绝规则。
 *
 * <p>重点验两件事：公开接口别误伤（游客能逛角色列表），私有接口别漏放（社区、
 * 可视化这些新加的必须登录）。角色越权是另一条分支，也要测。
 */
@DisplayName("JWT 鉴权拦截")
class JwtInterceptorTest {

    private JwtInterceptor interceptor;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        interceptor = new JwtInterceptor(jwtUtil, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private MockHttpServletRequest req(String method, String uri, String auth) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        if (auth != null) {
            r.addHeader("Authorization", auth);
        }
        return r;
    }

    @Test
    @DisplayName("公开 GET 接口无 token 也放行")
    void publicGetAllowed() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req("GET", "/api/characters", null), resp, new Object()));
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("公开接口带坏 token 也放行——游客不该被错误 token 拦住")
    void publicPathIgnoresBadToken() throws Exception {
        when(jwtUtil.getUserId(anyString())).thenThrow(new RuntimeException("token 已过期"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req("GET", "/api/tags", "Bearer garbage"), resp, new Object()));
    }

    @Test
    @DisplayName("私有接口无 token 返回 401")
    void privateWithoutTokenRejected() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req("GET", "/api/chat/sessions", null), resp, new Object()));
        assertEquals(401, resp.getStatus());
    }

    @Test
    @DisplayName("私有接口带坏 token 返回 401")
    void privateWithBadTokenRejected() throws Exception {
        when(jwtUtil.getUserId(anyString())).thenThrow(new RuntimeException("签名不对"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req("POST", "/api/community/works/1/like", "Bearer bad"), resp, new Object()));
        assertEquals(401, resp.getStatus());
    }

    @Test
    @DisplayName("社区与可视化接口必须登录")
    void newEndpointsRequireAuth() throws Exception {
        for (String uri : Arrays.asList(
                "/api/community/works",
                "/api/community/works/1/comments",
                "/api/metrics/context",
                "/api/metrics/cache",
                "/api/metrics/token")) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(req("GET", uri, null), resp, new Object()),
                    uri + " 不该匿名访问");
            assertEquals(401, resp.getStatus());
        }
    }

    @Test
    @DisplayName("合法 token 能放行并写入上下文")
    void validTokenPopulatesContext() throws Exception {
        when(jwtUtil.getUserId("good")).thenReturn(42L);
        when(jwtUtil.getRole("good")).thenReturn(0);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req("GET", "/api/community/works", "Bearer good"), resp, new Object()));
        assertEquals(42L, UserContext.getUserId());
        assertEquals(0, UserContext.getRole());
    }

    @Test
    @DisplayName("管理端接口非管理员返回 403")
    void adminRequiresRole() throws Exception {
        when(jwtUtil.getUserId("user")).thenReturn(7L);
        when(jwtUtil.getRole("user")).thenReturn(0);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req("GET", "/api/admin/stats", "Bearer user"), resp, new Object()));
        assertEquals(403, resp.getStatus());
    }

    @Test
    @DisplayName("管理员可访问管理端")
    void adminAllowed() throws Exception {
        when(jwtUtil.getUserId("boss")).thenReturn(1L);
        when(jwtUtil.getRole("boss")).thenReturn(1);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req("GET", "/api/admin/stats", "Bearer boss"), resp, new Object()));
    }

    @Test
    @DisplayName("请求结束必须清理 ThreadLocal，不能跨请求泄漏")
    void contextClearedAfterCompletion() {
        UserContext.set(99L, 1);
        interceptor.afterCompletion(req("GET", "/api/x", null), new MockHttpServletResponse(), new Object(), null);
        assertNull(UserContext.getUserId(), "ThreadLocal 未清理会导致线程复用时身份串号");
        assertNull(UserContext.getRole());
    }

    @Test
    @DisplayName("CORS 预检直接放行")
    void optionsPassThrough() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req("OPTIONS", "/api/chat/send", null), resp, new Object()));
    }
}
