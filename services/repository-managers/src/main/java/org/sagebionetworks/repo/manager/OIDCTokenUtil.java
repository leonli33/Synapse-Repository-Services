package org.sagebionetworks.repo.manager;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.StackConfigurationSingleton;
import org.sagebionetworks.repo.model.oauth.OAuthScope;
import org.sagebionetworks.repo.model.oauth.OIDCAuthorizationRequest;
import org.sagebionetworks.repo.model.oauth.OIDCClaimName;
import org.sagebionetworks.repo.model.oauth.OIDCClaimsRequestDetails;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.JSONObjectAdapterImpl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public class OIDCTokenUtil {
	private static final String ISSUER = "https://repo-prod.prod.sagebase.org/auth/v1"; // TODO  Is this redundant with a string provided elsewhere? Should it be passed in?
	private static final String ACCESS = "access";
	private static final String SCOPE = "scope";
	private static final String USER_INFO_CLAIMS = "odic_claims";
	private static final String NONCE = "nonce";
	
	// the time window during which the client will consider the returned claims to be valid
	private static final long OIDC_CLAIMS_EXPIRATION_TIME_SECONDS = 60L; // a minute

	private static final String OIDC_SIGNATURE_KEY_ID;
	private static final PrivateKey OIDC_SIGNATURE_PRIVATE_KEY;
	
	private static List<JWK> JSON_WEB_KEY_SET;

	static {
		StackConfiguration stackConfig = StackConfigurationSingleton.singleton();

		JSON_WEB_KEY_SET = new ArrayList<JWK>();
		PrivateKey signingPrivateKey = null;
		String signingKeyId = null;
		for (String s: stackConfig.getOIDCSignatureRSAPrivateKeys()) {
			KeyPair keyPair = JWTUtil.getRSAKeyPairFromPEM(s);
			String kid = JWTUtil.computeKeyId(keyPair.getPublic());
			// grab the first one to use when signing
			if (signingPrivateKey==null) {
				signingPrivateKey=keyPair.getPrivate();
				signingKeyId = kid;
			}
			if (keyPair.getPublic() instanceof ECPublicKey) {
				Curve curve = Curve.forECParameterSpec(((ECPublicKey)keyPair.getPublic()).getParams());
				JWK jwk = new ECKey.Builder(curve, (ECPublicKey)keyPair.getPublic())
						.privateKey((ECPrivateKey)keyPair.getPrivate())
						.algorithm(JWSAlgorithm.ES256)
						.keyUse(KeyUse.SIGNATURE)
						.keyID(kid)
						.build();
				JSON_WEB_KEY_SET.add(jwk);
			
			} else if (keyPair.getPublic() instanceof RSAPublicKey) {
				JWK jwk = new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
						.privateKey((RSAPrivateKey)keyPair.getPrivate())
						.algorithm(JWSAlgorithm.RS256)
						.keyUse(KeyUse.SIGNATURE)
						.keyID(kid)
						.build();
				JSON_WEB_KEY_SET.add(jwk);
			} else {
				throw new RuntimeException(keyPair.getPublic()+" not supported.");
			}
		}
		OIDC_SIGNATURE_PRIVATE_KEY = signingPrivateKey;
		OIDC_SIGNATURE_KEY_ID = signingKeyId;
	}

	public static List<JWK> getJSONWebKeySet() {
		return JSON_WEB_KEY_SET;
	}

	public static String createSignedJWT(Claims claims) {
		return Jwts.builder().setClaims(claims).
		setHeaderParam(Header.TYPE, Header.JWT_TYPE).
		setHeaderParam("kid", OIDC_SIGNATURE_KEY_ID).
		signWith(SignatureAlgorithm.RS256, OIDC_SIGNATURE_PRIVATE_KEY).compact();
	}
	
	public static boolean validateSignedJWT(String token) {
		boolean verified = false;
		try {
			SignedJWT signedJWT = (SignedJWT)JWTParser.parse(token);
			JWK matchingJwk = null;
			for (JWK jwk : JSON_WEB_KEY_SET) {
				if (jwk.getKeyID().equals(signedJWT.getHeader().getKeyID())) {
					matchingJwk = jwk;
				}
			}
			if (matchingJwk!=null && matchingJwk instanceof RSAKey) {
				JWSVerifier verifier = new RSASSAVerifier((RSAKey)matchingJwk);
				verified = signedJWT.verify(verifier);
			}
			
			if (System.currentTimeMillis()>signedJWT.getJWTClaimsSet().getExpirationTime().getTime()) {
				verified=false;
			}
			// We check issuer (though the validation of the signature should be enough to know it came from us
			if (!signedJWT.getJWTClaimsSet().getIssuer().equals(ISSUER)) {
				verified=false;
			}
		} catch (ParseException | JOSEException e) {
			throw new RuntimeException(e);
		}
		return verified;
	}
	
	public static String createOIDCIdToken(
			String subject, 
			String oauthClientId,
			long now, 
			String nonce, 
			Long authTimeSeconds,
			String tokenId,
			Map<OIDCClaimName,String> userInfo) {
		
		Claims claims = Jwts.claims();
		
		for (OIDCClaimName claimName: userInfo.keySet()) {
			claims.put(claimName.name(), userInfo.get(claimName));
		}
		
		claims.setIssuer(ISSUER)
			.setAudience(oauthClientId)
			.setExpiration(new Date(now+OIDC_CLAIMS_EXPIRATION_TIME_SECONDS*1000L))
			.setNotBefore(new Date(now))
			.setIssuedAt(new Date(now))
			.setId(tokenId)
			.setSubject(subject);
		
		if (nonce!=null) claims.put(NONCE, nonce);
		
		claims.put(OIDCClaimName.auth_time.name(), authTimeSeconds);

		return createSignedJWT(claims);
	}
	
	public static List<OAuthScope> getScopeFromClaims(JWTClaimsSet claimSet) {
		JSONObject scopeAndClaims;
		try {
			scopeAndClaims = claimSet.getJSONObjectClaim(ACCESS);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		JSONArray scopeArray = (JSONArray)scopeAndClaims.get(SCOPE);
		List<OAuthScope> result = new ArrayList<OAuthScope>();
		for (String scope : scopeArray.toArray(new String[] {})) {
			result.add(OAuthScope.valueOf(scope));
		}
		return result;
	}
	
	public static Map<OIDCClaimName, OIDCClaimsRequestDetails> getOIDCClaimsFromClaimSet(JWTClaimsSet claimSet) {
		JSONObject scopeAndClaims;
		try {
			scopeAndClaims = claimSet.getJSONObjectClaim(ACCESS);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		JSONObject userInfoClaims = (JSONObject)scopeAndClaims.get(USER_INFO_CLAIMS);
		Map<OIDCClaimName, OIDCClaimsRequestDetails> result = new HashMap<OIDCClaimName, OIDCClaimsRequestDetails>();
		for (String claimName : userInfoClaims.keySet()) {
			OIDCClaimsRequestDetails details = new OIDCClaimsRequestDetails();
			try {
				JSONObjectAdapter adapter = new JSONObjectAdapterImpl(userInfoClaims.getAsString(claimName));
				details.initializeFromJSONObject(adapter);
			} catch (JSONObjectAdapterException e) {
				throw new RuntimeException(e);
			}
			result.put(OIDCClaimName.valueOf(claimName), details);
		}
		return result;
	}
	
	public static String createOIDCaccessToken(
			String subject, 
			String oauthClientId,
			long now, 
			Long authTimeSeconds,
			String tokenId,
			List<OAuthScope> scopes,
			Map<OIDCClaimName, OIDCClaimsRequestDetails> oidcClaims) {
		
		Claims claims = Jwts.claims();
		
		JSONObject scopeAndClaims = new JSONObject();
		JSONArray scopeArray = new JSONArray();
		for (OAuthScope scope : scopes) {
			scopeArray.add(scope.name());
		}
		scopeAndClaims.put(SCOPE, scopeArray);
		JSONObject userInfoClaims = new JSONObject();
		for (OIDCClaimName claimName : oidcClaims.keySet()) {
			OIDCClaimsRequestDetails claimDetails = oidcClaims.get(claimName);
			userInfoClaims.put(claimName.name(), claimDetails.toString());
		}
		scopeAndClaims.put(USER_INFO_CLAIMS, userInfoClaims);
		claims.put(ACCESS, scopeAndClaims);
		
		claims.setIssuer(ISSUER)
			.setAudience(oauthClientId)
			.setExpiration(new Date(now+OIDC_CLAIMS_EXPIRATION_TIME_SECONDS*1000L))
			.setNotBefore(new Date(now))
			.setIssuedAt(new Date(now))
			.setId(tokenId)
			.setSubject(subject);
		
		claims.put(OIDCClaimName.auth_time.name(), authTimeSeconds);

		return createSignedJWT(claims);
	}
	

}
