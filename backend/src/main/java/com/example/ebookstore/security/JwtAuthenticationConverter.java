package com.example.ebookstore.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.example.ebookstore.entity.User;
import com.example.ebookstore.repository.UserRepository;

/**
 * Turns a verified JWT into the logged-in user for the rest of the request.
 *
 * <p>The signature, expiry and issuer were already checked by the JWT decoder. This class adds one more safety
 * step: it loads the user from the database <em>on every request</em>. As a result:
 * <ul>
 *   <li>a deleted or disabled account stops working immediately, even with an unexpired token;</li>
 *   <li>the role comes from the database, so a role change takes effect at once and cannot be forged.</li>
 * </ul>
 * Any problem becomes a {@code 401}.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public JwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId = parseUserId(jwt.getSubject());
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Unknown or disabled user"));

        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getRole());
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private static long parseUserId(String subject) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Invalid token subject");
        }
    }
}
