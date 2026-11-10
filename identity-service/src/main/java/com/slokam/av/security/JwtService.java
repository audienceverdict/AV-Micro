package com.slokam.av.security;

import com.slokam.av.entity.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;


@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expiration;

    private com.nimbusds.jose.jwk.RSAKey publicJwk;
    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(@Value("${jwt.private-key}") org.springframework.core.io.Resource privateKey,
            @Value("${jwt.public-key}") org.springframework.core.io.Resource publicKey,
            @Value("${jwt.expiration-ms:3600000}") long expiration,
            @Value("${jwt.key-id:av-identity-1}") String keyId) throws Exception {
        java.security.interfaces.RSAPrivateKey priv;
        java.security.interfaces.RSAPublicKey pub;
        try(var in=privateKey.getInputStream()) { priv=org.springframework.security.converter.RsaKeyConverters.pkcs8().convert(in); }
        try(var in=publicKey.getInputStream()) { pub=org.springframework.security.converter.RsaKeyConverters.x509().convert(in); }
        if (priv == null || pub == null || !priv.getModulus().equals(pub.getModulus()) || pub.getModulus().bitLength()<2048 || expiration<1)
            throw new IllegalArgumentException("Matching RSA keys (at least 2048 bits) and positive JWT expiration required");
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder(pub).privateKey(priv).keyID(keyId).build();
        publicJwk=key.toPublicJWK();
        encoder=new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
        var nimbus=NimbusJwtDecoder.withPublicKey(pub).build();
        nimbus.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(java.time.Duration.ZERO),new JwtIssuerValidator("audience-verdict")));
        decoder=nimbus; this.expiration=expiration;
    }
    public java.util.Map<String,Object> jwks() {
        if(publicJwk==null) throw new IllegalStateException("RSA signing is required");
        return new com.nimbusds.jose.jwk.JWKSet(publicJwk).toJSONObject();
    }
    private JwsHeader header() {
        return JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).keyId(publicJwk.getKeyID()).build();
    }
    public String generate(User user) {
        Instant now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .issuer("audience-verdict")
                        .subject(user.id)
                        .audience(java.util.List.of("av-api"))
                        .claim("userId", user.id)
                        .claim("role", user.role.name())
                        .claim("roles", user.effectiveRoles().stream().map(Enum::name).toList())
                        .issuedAt(now)
                        .expiresAt(now.plusMillis(expiration));
        if (user.mobile != null) claims.claim("mobile", user.mobile);
        return encoder.encode(
                        JwtEncoderParameters.from(
                                header(), claims.build()))
                .getTokenValue();
    }

    public String registrationProof(String destination, String channel, Long challenge) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("audience-verdict")
                .subject(destination).claim("purpose", "otp-registration")
                .claim("channel", channel).claim("challenge", challenge)
                .issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        return encoder.encode(JwtEncoderParameters.from(
                header(), claims)).getTokenValue();
    }

    public Jwt validateRegistration(String token) {
        Jwt jwt = decoder.decode(token);
        if (!"otp-registration".equals(jwt.getClaimAsString("purpose")))
            throw new BadJwtException("Invalid registration proof");
        return jwt;
    }

    public Jwt validate(String token) {
        Jwt jwt = decoder.decode(token);
        if (jwt.hasClaim("purpose") || !jwt.getAudience().contains("av-api")) throw new BadJwtException("Access token required");
        return jwt;
    }
}
