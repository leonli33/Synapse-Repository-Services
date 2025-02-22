package org.sagebionetworks.repo.manager.oauth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.repo.manager.oauth.GoogleOAuth2Provider.EMAIL;
import static org.sagebionetworks.repo.manager.oauth.GoogleOAuth2Provider.EMAIL_VERIFIED;
import static org.sagebionetworks.repo.manager.oauth.GoogleOAuth2Provider.FAMILY_NAME;
import static org.sagebionetworks.repo.manager.oauth.GoogleOAuth2Provider.GIVEN_NAME;
import static org.sagebionetworks.repo.manager.oauth.GoogleOAuth2Provider.SUB;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sagebionetworks.repo.model.oauth.ProvidedUserInfo;

public class GoogleOAuth2ProviderTest {
	
	String apiKey;
	String apiSecret;
	OIDCConfig mockConfig;
	GoogleOAuth2Provider provider;
	
	private String authEndpoint = "https://auth_url.org";
	private String tokenEndpoint = "https://token_url.org";
	private String userInfoEndpoint = "https://userInfo_url.org";
	
	@BeforeEach
	public void before(){
		apiKey = "fake key";
		apiSecret = "fake secret";
		mockConfig = Mockito.mock(OIDCConfig.class);
		
		when(mockConfig.getAuthorizationEndpoint()).thenReturn(authEndpoint);
		when(mockConfig.getTokenEndpoint()).thenReturn(tokenEndpoint);
		when(mockConfig.getUserInfoEndpoint()).thenReturn(userInfoEndpoint);
		
		provider = new GoogleOAuth2Provider(apiKey, apiSecret, mockConfig);
	}

	
	@Test
	public void testGetAuthorizationUrl(){
		String redirectUrl = "https://domain.com";
		String authUrl = provider.getAuthorizationUrl(redirectUrl);
		assertEquals(authEndpoint + "?response_type=code&client_id=fake key&redirect_uri=https%3A%2F%2Fdomain.com&prompt=select_account&scope=openid%20profile%20email", authUrl);
	}
	
	@Test
	public void testParserResponseBody() throws JSONException{
		JSONObject json = new JSONObject();
		json.put(FAMILY_NAME, "last");
		json.put(GIVEN_NAME, "first");
		json.put(EMAIL_VERIFIED, true);
		json.put(EMAIL, "first.last@domain.com");
		json.put(SUB, "abcd");
		ProvidedUserInfo info = GoogleOAuth2Provider.parseUserInfo(json.toString());
		assertNotNull(info);
		assertEquals("last", info.getLastName());
		assertEquals("first", info.getFirstName());
		assertEquals("first.last@domain.com", info.getUsersVerifiedEmail());
		assertEquals("abcd", info.getSubject());
	}
	
	@Test
	public void testParserResponseBodyVerifiedNull() throws JSONException{
		JSONObject json = new JSONObject();
		// This case does not have a verified email so no email should be returned.
		json.put(EMAIL, "first.last@domain.com");
		ProvidedUserInfo info = GoogleOAuth2Provider.parseUserInfo(json.toString());
		assertNotNull(info);
		assertEquals("Email was not verified and should not have been returned.", null, info.getUsersVerifiedEmail());
	}
	
	@Test
	public void testParserResponseBodyEmailNotVerifeid() throws JSONException{
		JSONObject json = new JSONObject();
		// email not verified.
		json.put(EMAIL_VERIFIED, false);
		json.put(EMAIL, "first.last@domain.com");
		ProvidedUserInfo info = GoogleOAuth2Provider.parseUserInfo(json.toString());
		assertNotNull(info);
		assertEquals("Email was not verified and should not have been returned.",null, info.getUsersVerifiedEmail());
	}
}
