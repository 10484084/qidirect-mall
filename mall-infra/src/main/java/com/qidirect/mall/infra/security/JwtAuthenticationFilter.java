package com.qidirect.mall.infra.security;

import com.qidirect.mall.common.constant.AuthConstants;
import com.qidirect.mall.common.enums.UserType;
import com.qidirect.mall.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 Bearer Token 并写入 SecurityContext。
 * 双端隔离：mall-admin 只接受 userType=admin，mall-api 只接受 userType=store。
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final String expectedUserType;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   @Value("${security.user-type}") String expectedUserType) {
        this.jwtUtil = jwtUtil;
        this.expectedUserType = expectedUserType;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(AuthConstants.HEADER_AUTHORIZATION);
        if (header != null && header.startsWith(AuthConstants.TOKEN_PREFIX)) {
            authenticate(request, header.substring(AuthConstants.TOKEN_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Claims claims = jwtUtil.parse(token);
            String userType = claims.get(AuthConstants.CLAIM_USER_TYPE, String.class);
            if (!expectedUserType.equals(userType)) {
                return;
            }

            LoginUser loginUser = new LoginUser(
                    userType,
                    Long.valueOf(claims.getSubject()),
                    longClaim(claims, AuthConstants.CLAIM_STORE_ID),
                    longClaim(claims, AuthConstants.CLAIM_LEVEL_ID));

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + UserType.valueOf(userType.toUpperCase()).name()));
            List<?> perms = claims.get(AuthConstants.CLAIM_PERMS, List.class);
            if (perms != null) {
                perms.forEach(perm -> authorities.add(new SimpleGrantedAuthority(String.valueOf(perm))));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.debug("Token 解析失败：{}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private Long longClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
