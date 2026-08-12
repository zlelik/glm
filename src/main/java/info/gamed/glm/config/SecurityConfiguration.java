package info.gamed.glm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import info.gamed.glm.entity.Player;
import info.gamed.glm.service.PlayerDetailsService;

/**
 * This class is implementing Spring Security configuration. It defines protected and open pages, user authentication service, etc. 
 * @author Z@
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	private final PlayerDetailsService playerDetailsService;

	public SecurityConfiguration(PlayerDetailsService playerDetailsService) {
		this.playerDetailsService = playerDetailsService;
	}

	@Bean
    public AuthenticationConfigurer authenticationConfigurer() throws Exception {
        return new AuthenticationConfigurer(playerDetailsService, Player.PASSWORD_ENCODER);
    }
	
	public static class AuthenticationConfigurer {
        private final PlayerDetailsService playerDetailsService;
        private final PasswordEncoder passwordEncoder;

        public AuthenticationConfigurer(PlayerDetailsService playerDetailsService, PasswordEncoder passwordEncoder) {
            this.playerDetailsService = playerDetailsService;
            this.passwordEncoder = passwordEncoder;
        }

        public void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.userDetailsService(playerDetailsService).passwordEncoder(passwordEncoder);
        }
    }

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/", "/built/**", "/img/**", "/main.css", "/howtoplay", "/contact", "/favicon.ico").permitAll()
				.anyRequest().authenticated()
			)
		.formLogin((form) -> form.defaultSuccessUrl("/", true).permitAll())
		.httpBasic(Customizer.withDefaults())
		// CSRF protection enabled. The token is stored in a non-HttpOnly XSRF-TOKEN cookie so the
		// React SPA can read it and echo it back in the X-XSRF-TOKEN header on state-changing requests
		// (e.g. POST /logout). SpaCsrfTokenRequestHandler resolves the raw header token for the SPA while
		// keeping BREACH (XOR) protection for server-rendered forms such as the default login page.
		.csrf(csrf -> csrf
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
		// Ensures the deferred CSRF token is materialised so the XSRF-TOKEN cookie is actually sent to the browser.
		.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
		// Security response headers. Spring Security already adds sensible defaults (X-Content-Type-Options:
		// nosniff, X-Frame-Options: DENY, Cache-Control). Add a Content-Security-Policy as defence-in-depth
		// against XSS / data injection. The directives below match what the app actually loads:
		//   script-src 'self'          - the React bundle is served same-origin from /built/bundle.js
		//   style-src 'self' + inline  - main.css plus React inline element styles and the default login page
		//   connect-src 'self' ws/wss  - same-origin REST plus the STOMP WebSocket (ws:// or wss://) endpoint
		//   img-src 'self' data:       - same-origin images and any data: URIs
		.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives(
						"default-src 'self'; " +
						"script-src 'self'; " +
						"style-src 'self' 'unsafe-inline'; " +
						"img-src 'self' data:; " +
						"connect-src 'self' ws: wss:; " +
						"font-src 'self'; " +
						"object-src 'none'; " +
						"base-uri 'self'; " +
						"frame-ancestors 'self'")))
		.logout((logout) -> logout.logoutSuccessUrl("/"));

		return http.build();
	}

	/**
	 * Forces the (deferred) {@link CsrfToken} to be loaded on each request so that
	 * {@link CookieCsrfTokenRepository} writes the XSRF-TOKEN cookie to the response.
	 */
	static final class CsrfCookieFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			// Render the token value to a cookie by causing the deferred token to be loaded.
			csrfToken.getToken();
			filterChain.doFilter(request, response);
		}
	}

	/**
	 * Spring Security's recommended CSRF request handler for SPAs: it keeps XOR/BREACH protection when
	 * rendering the token for server-side forms, but resolves the plain token value when the SPA sends it
	 * back via the request header (the value read from the XSRF-TOKEN cookie).
	 */
	static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
		private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
			this.delegate.handle(request, response, csrfToken);
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			// If the token comes in via the header (SPA), use the plain value; otherwise (form param) use XOR decoding.
			if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
				return super.resolveCsrfTokenValue(request, csrfToken);
			}
			return this.delegate.resolveCsrfTokenValue(request, csrfToken);
		}
	}
}
