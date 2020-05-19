package org.sagebionetworks.repo.manager.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.AccessControlListDAO;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.InvalidModelException;
import org.sagebionetworks.repo.model.NextPageToken;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.auth.AuthorizationStatus;
import org.sagebionetworks.repo.model.dbo.schema.JsonSchemaDao;
import org.sagebionetworks.repo.model.dbo.schema.NewSchemaVersionRequest;
import org.sagebionetworks.repo.model.dbo.schema.OrganizationDao;
import org.sagebionetworks.repo.model.dbo.schema.SchemaDependency;
import org.sagebionetworks.repo.model.schema.CreateOrganizationRequest;
import org.sagebionetworks.repo.model.schema.CreateSchemaRequest;
import org.sagebionetworks.repo.model.schema.CreateSchemaResponse;
import org.sagebionetworks.repo.model.schema.JsonSchema;
import org.sagebionetworks.repo.model.schema.JsonSchemaInfo;
import org.sagebionetworks.repo.model.schema.JsonSchemaVersionInfo;
import org.sagebionetworks.repo.model.schema.ListJsonSchemaInfoRequest;
import org.sagebionetworks.repo.model.schema.ListJsonSchemaInfoResponse;
import org.sagebionetworks.repo.model.schema.ListJsonSchemaVersionInfoRequest;
import org.sagebionetworks.repo.model.schema.ListJsonSchemaVersionInfoResponse;
import org.sagebionetworks.repo.model.schema.ListOrganizationsRequest;
import org.sagebionetworks.repo.model.schema.ListOrganizationsResponse;
import org.sagebionetworks.repo.model.schema.Organization;
import org.sagebionetworks.repo.model.util.AccessControlListUtil;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.EntityFactory;
import org.sagebionetworks.schema.id.SchemaId;
import org.sagebionetworks.schema.parser.SchemaIdParser;

import com.google.common.collect.Lists;

@ExtendWith(MockitoExtension.class)
public class JsonSchemaManagerImplTest {

	@Mock
	OrganizationDao mockOrganizationDao;

	@Mock
	AccessControlListDAO mockAclDao;

	@Mock
	JsonSchemaDao mockSchemaDao;

	@Captor
	ArgumentCaptor<AccessControlList> aclCaptor;

	@InjectMocks
	JsonSchemaManagerImpl manager;

	Date now;
	UserInfo user;
	UserInfo anonymousUser;
	UserInfo adminUser;
	String organizationName;
	CreateOrganizationRequest createOrganizationRequest;
	String schemaName;
	String semanticVersionString;
	JsonSchema schema;
	CreateSchemaRequest createSchemaRequest;
	String schemaJson;
	String schemaJsonSHA256Hex;
	String jsonBlobId;
	SchemaId schemaId;
	String versionId;

	Organization organization;
	JsonSchemaVersionInfo versionInfo;

	ListOrganizationsRequest listOrganizationsRequest;
	ListJsonSchemaInfoRequest listJsonSchemaInfoRequest;
	ListJsonSchemaVersionInfoRequest listJsonSchemaVersionInfoRequest;

	AccessControlList acl;

	@BeforeEach
	public void before() throws JSONObjectAdapterException {
		boolean isAdmin = false;
		user = new UserInfo(isAdmin, 123L);
		isAdmin = true;
		adminUser = new UserInfo(isAdmin, 456L);

		anonymousUser = new UserInfo(isAdmin, BOOTSTRAP_PRINCIPAL.ANONYMOUS_USER.getPrincipalId());

		now = new Date(1L);

		organizationName = "a.z2.b.com";
		createOrganizationRequest = new CreateOrganizationRequest();
		createOrganizationRequest.setOrganizationName(organizationName);

		organization = new Organization();
		organization.setCreatedBy(user.getId().toString());
		organization.setName(organizationName);
		organization.setCreatedOn(now);
		organization.setId("4321");

		acl = AccessControlListUtil.createACL(organization.getId(), user, JsonSchemaManagerImpl.ADMIN_PERMISSIONS,
				new Date());

		schemaName = "path.SomeSchema.json";
		semanticVersionString = "1.2.3";
		schema = new JsonSchema();
		schema.set$id(organization.getName() + "/" + schemaName + "/" + semanticVersionString);
		schemaId = SchemaIdParser.parseSchemaId(schema.get$id());

		createSchemaRequest = new CreateSchemaRequest();
		createSchemaRequest.setSchema(schema);

		schemaJson = EntityFactory.createJSONStringForEntity(schema);
		schemaJsonSHA256Hex = DigestUtils.sha256Hex(schemaJson);
		jsonBlobId = "987";

		versionId = "888";

		versionInfo = new JsonSchemaVersionInfo();
		versionInfo.setOrganizationId(organization.getId());
		versionInfo.setOrganizationName(organizationName);
		versionInfo.setCreatedBy(user.getId().toString());
		versionInfo.setCreatedOn(now);
		versionInfo.setJsonSHA256Hex(schemaJsonSHA256Hex);
		versionInfo.setSemanticVersion(semanticVersionString);
		versionInfo.setVersionId(versionId);

		listOrganizationsRequest = new ListOrganizationsRequest();

		listJsonSchemaInfoRequest = new ListJsonSchemaInfoRequest();
		listJsonSchemaInfoRequest.setOrganizationName(organizationName);

		listJsonSchemaVersionInfoRequest = new ListJsonSchemaVersionInfoRequest();
		listJsonSchemaVersionInfoRequest.setOrganizationName(organizationName);
		listJsonSchemaVersionInfoRequest.setSchemaName(schemaName);
	}

	@Test
	public void processAndValidateOrganizationNameWithNullUser() {
		String inputName = " A.b9.C.DEFG \n";
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, inputName);
		});
	}

	@Test
	public void processAndValidateOrganizationNameWithNullInputName() {
		String inputName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, inputName);
		});
	}

	@Test
	public void testProcessAndValidateOrganizationName() {
		String inputName = " A.b9.C.DEFG \n";
		String processedName = JsonSchemaManagerImpl.processAndValidateOrganizationName(user, inputName);
		assertEquals("A.b9.C.DEFG", processedName);
	}

	@Test
	public void testProcessAndValidateOrganizationNameMaxLength() {
		String input = StringUtils.repeat("a", JsonSchemaManagerImpl.MAX_ORGANZIATION_NAME_CHARS);
		String processedName = JsonSchemaManagerImpl.processAndValidateOrganizationName(user, input);
		assertNotNull(processedName);
		assertEquals(JsonSchemaManagerImpl.MAX_ORGANZIATION_NAME_CHARS, processedName.length());
	}

	@Test
	public void testProcessAndValidateOrganizationNameOverMaxLength() {
		String input = StringUtils.repeat("a", JsonSchemaManagerImpl.MAX_ORGANZIATION_NAME_CHARS + 1);
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, input);
		}).getMessage();
		assertEquals("Organization name must be 250 characters or less", message);
	}

	@Test
	public void testProcessAndValidateOrganizationNameMinLength() {
		String input = StringUtils.repeat("a", JsonSchemaManagerImpl.MIN_ORGANZIATION_NAME_CHARS);
		String processedName = JsonSchemaManagerImpl.processAndValidateOrganizationName(user, input);
		assertNotNull(processedName);
		assertEquals(JsonSchemaManagerImpl.MIN_ORGANZIATION_NAME_CHARS, processedName.length());
	}

	@Test
	public void testProcessAndValidateOrganizationNameUnderMinLength() {
		String input = StringUtils.repeat("a", JsonSchemaManagerImpl.MIN_ORGANZIATION_NAME_CHARS - 1);
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, input);
		}).getMessage();
		assertEquals("Organization name must be at least 6 characters", message);
	}

	@Test
	public void testProcessAndValidateOrganizationNameSagebionetworks() {
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, "sagebionetwork");
		}).getMessage();
		assertEquals(JsonSchemaManagerImpl.SAGEBIONETWORKS_RESERVED_MESSAGE, message);
	}

	@Test
	public void testProcessAndValidateOrganizationWithNameSagebionetworksAdmin() {
		// call under test
		String name = JsonSchemaManagerImpl.processAndValidateOrganizationName(adminUser, "org.sagebionetworks");
		assertEquals("org.sagebionetworks", name);
	}

	@Test
	public void testProcessAndValidateOrganizationNameSagebionetworksUpper() {
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, "SageBionetwork");
		}).getMessage();
		assertEquals(JsonSchemaManagerImpl.SAGEBIONETWORKS_RESERVED_MESSAGE, message);
	}

	@Test
	public void testProcessAndValidateOrganizationNameStartWithDigit() {
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, "1abcdefg");
		}).getMessage();
		assertTrue(message.startsWith("Invalid 'organizationName'"));
	}

	@Test
	public void testProcessAndValidateOrganizationNameStartWithDot() {
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, ".abcdefg");
		}).getMessage();
		assertTrue(message.startsWith("Invalid 'organizationName'"));
	}

	@Test
	public void testProcessAndValidateOrganizationNameEndWithDot() {
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, "abcdefg.");
		}).getMessage();
		assertTrue(message.startsWith("Invalid 'organizationName'"));
	}

	@Test
	public void testProcessAndValidateOrganizationNameContainsInvalidChars() {
		String message = assertThrows(IllegalArgumentException.class, () -> {
			JsonSchemaManagerImpl.processAndValidateOrganizationName(user, "abc/defg");
		}).getMessage();
		assertTrue(message.startsWith("Invalid 'organizationName'"));
	}

	@Test
	public void testCreateOrganziation() {
		when(mockOrganizationDao.createOrganization(createOrganizationRequest.getOrganizationName(), user.getId()))
				.thenReturn(organization);
		// call under test
		Organization returned = manager.createOrganziation(user, createOrganizationRequest);
		assertNotNull(returned);
		assertEquals(organization, returned);

		verify(mockOrganizationDao).createOrganization(createOrganizationRequest.getOrganizationName(), user.getId());

		verify(mockAclDao).create(aclCaptor.capture(), eq(ObjectType.ORGANIZATION));
		AccessControlList acl = aclCaptor.getValue();
		assertNotNull(acl);
		assertEquals(returned.getId(), acl.getId());
		AccessControlList expectedAcl = AccessControlListUtil.createACL(returned.getId(), user,
				JsonSchemaManagerImpl.ADMIN_PERMISSIONS, new Date());
		assertEquals(expectedAcl.getResourceAccess(), acl.getResourceAccess());
	}

	@Test
	public void testCreateOrganziationNameWithUpperCase() {
		createOrganizationRequest.setOrganizationName("ALLCAPS");
		when(mockOrganizationDao.createOrganization(anyString(), anyLong())).thenReturn(organization);
		// call under test
		Organization returned = manager.createOrganziation(user, createOrganizationRequest);
		assertNotNull(returned);
		assertEquals(organization, returned);

		verify(mockOrganizationDao).createOrganization("ALLCAPS", user.getId());
	}

	@Test
	public void testCreateOrganziationNameWithWhiteSpace() {
		createOrganizationRequest.setOrganizationName(" needs.trimmed\n");
		when(mockOrganizationDao.createOrganization(anyString(), anyLong())).thenReturn(organization);
		// call under test
		Organization returned = manager.createOrganziation(user, createOrganizationRequest);
		assertNotNull(returned);
		assertEquals(organization, returned);

		verify(mockOrganizationDao).createOrganization("needs.trimmed", user.getId());
	}

	@Test
	public void testCreateOrganizationAnonymous() {
		assertThrows(UnauthorizedException.class, () -> {
			// call under test
			manager.createOrganziation(anonymousUser, createOrganizationRequest);
		});
	}

	@Test
	public void testCreateOrganizationNullUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.createOrganziation(user, createOrganizationRequest);
		});
	}

	@Test
	public void testCreateOrganizationNullRequest() {
		createOrganizationRequest = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.createOrganziation(user, createOrganizationRequest);
		});
	}

	@Test
	public void testCreateOrganizationNullName() {
		createOrganizationRequest.setOrganizationName(null);
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.createOrganziation(user, createOrganizationRequest);
		});
	}

	@Test
	public void testCreateOrganizationBadName() {
		createOrganizationRequest.setOrganizationName("endsWithDot.");
		String message = assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.createOrganziation(user, createOrganizationRequest);
		}).getMessage();
		assertTrue(message.startsWith("Invalid 'organizationName'"));
	}

	@Test
	public void testGetOrganizationAcl() {
		when(mockAclDao.canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.READ))
				.thenReturn(AuthorizationStatus.authorized());
		when(mockAclDao.get(organization.getId(), ObjectType.ORGANIZATION)).thenReturn(acl);
		// call under test
		AccessControlList result = manager.getOrganizationAcl(user, organization.getId());
		assertEquals(acl, result);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.READ);
	}

	@Test
	public void testGetOrganizationAclNoRead() {
		when(mockAclDao.canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.READ))
				.thenReturn(AuthorizationStatus.accessDenied("nope"));
		assertThrows(UnauthorizedException.class, () -> {
			// call under test
			manager.getOrganizationAcl(user, organization.getId());
		});
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.READ);
	}

	@Test
	public void testGetOrganziationAclNullUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.getOrganizationAcl(user, organization.getId());
		});
	}

	@Test
	public void testGetOrganziationAclNullId() {
		organization.setId(null);
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.getOrganizationAcl(user, organization.getId());
		});
	}

	@Test
	public void testUpdateOrganizationAcl() {
		when(mockAclDao.canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.CHANGE_PERMISSIONS))
				.thenReturn(AuthorizationStatus.authorized());
		when(mockAclDao.get(organization.getId(), ObjectType.ORGANIZATION)).thenReturn(acl);
		// call under test
		AccessControlList result = manager.updateOrganizationAcl(user, organization.getId(), acl);
		assertEquals(acl, result);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION,
				ACCESS_TYPE.CHANGE_PERMISSIONS);
		verify(mockAclDao).update(acl, ObjectType.ORGANIZATION);
	}

	@Test
	public void testUpdateOrganizationAclRevokeOwnAccess() {
		// try to revoke all access to the organization
		acl.setResourceAccess(new HashSet<ResourceAccess>());
		assertThrows(InvalidModelException.class, () -> {
			// call under test
			manager.updateOrganizationAcl(user, organization.getId(), acl);
		});
		verify(mockAclDao, never()).update(any(AccessControlList.class), any(ObjectType.class));
	}

	@Test
	public void testUpdateOrganizationAclPassedIdDoesNotMatchAclId() {
		String passedId = "456";
		// ID in the ACL does not match the passed ID
		acl.setId("123");

		when(mockAclDao.canAccess(user, passedId, ObjectType.ORGANIZATION, ACCESS_TYPE.CHANGE_PERMISSIONS))
				.thenReturn(AuthorizationStatus.authorized());
		when(mockAclDao.get(passedId, ObjectType.ORGANIZATION)).thenReturn(acl);

		// call under test
		AccessControlList result = manager.updateOrganizationAcl(user, passedId, acl);
		assertEquals(acl, result);
		// the
		verify(mockAclDao).canAccess(user, passedId, ObjectType.ORGANIZATION, ACCESS_TYPE.CHANGE_PERMISSIONS);
		verify(mockAclDao).update(aclCaptor.capture(), eq(ObjectType.ORGANIZATION));
		// passed ACL should have the ID from the paths
		AccessControlList capturedAcl = aclCaptor.getValue();
		assertEquals(passedId, capturedAcl.getId());
		verify(mockAclDao).get(passedId, ObjectType.ORGANIZATION);
	}

	@Test
	public void testUpdateOrganizationAclUnauthorized() {
		when(mockAclDao.canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.CHANGE_PERMISSIONS))
				.thenReturn(AuthorizationStatus.accessDenied("not allowed"));
		assertThrows(UnauthorizedException.class, () -> {
			// call under test
			manager.updateOrganizationAcl(user, organization.getId(), acl);
		});
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION,
				ACCESS_TYPE.CHANGE_PERMISSIONS);
	}

	@Test
	public void testUpdateOrganizationAclNulUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.updateOrganizationAcl(user, organization.getId(), acl);
		});
	}

	@Test
	public void testUpdateOrganizationAclNulId() {
		String id = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.updateOrganizationAcl(user, id, acl);
		});
	}

	@Test
	public void testUpdateOrganizationAclIdNotNumber() {
		String id = "not a number";
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.updateOrganizationAcl(user, id, acl);
		});
	}

	@Test
	public void testUpdateOrganizationAclNulAcl() {
		acl = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.updateOrganizationAcl(user, organization.getId(), acl);
		});
	}

	@Test
	public void testDeleteOrganization() {
		when(mockAclDao.canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE))
				.thenReturn(AuthorizationStatus.authorized());
		// call under test
		manager.deleteOrganization(user, organization.getId());
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockOrganizationDao).deleteOrganization(organization.getId());
		verify(mockAclDao).delete(organization.getId(), ObjectType.ORGANIZATION);
	}

	@Test
	public void testDeleteOrganizationAsAdmin() {
		boolean isAdmin = true;
		UserInfo admin = new UserInfo(isAdmin, 123L);
		// call under test
		manager.deleteOrganization(admin, organization.getId());
		verify(mockAclDao, never()).canAccess(any(UserInfo.class), anyString(), any(ObjectType.class),
				any(ACCESS_TYPE.class));
		verify(mockOrganizationDao).deleteOrganization(organization.getId());
		verify(mockAclDao).delete(organization.getId(), ObjectType.ORGANIZATION);
	}

	@Test
	public void testDeleteOrganizationUnauthorized() {
		when(mockAclDao.canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE))
				.thenReturn(AuthorizationStatus.accessDenied("no way"));
		assertThrows(UnauthorizedException.class, () -> {
			// call under test
			manager.deleteOrganization(user, organization.getId());
		});
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockOrganizationDao, never()).deleteOrganization(anyString());
		verify(mockAclDao, never()).delete(anyString(), any(ObjectType.class));
	}

	@Test
	public void testDeleteOrganizationNullUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.deleteOrganization(user, organization.getId());
		});
	}

	@Test
	public void testDeleteOrganizationNullId() {
		String id = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.deleteOrganization(user, id);
		});
	}

	@Test
	public void testGetOrganizationByName() {
		String name = "org.org";
		when(mockOrganizationDao.getOrganizationByName(name)).thenReturn(organization);
		// call under test
		Organization result = manager.getOrganizationByName(user, name);
		assertEquals(result, organization);
		verify(mockOrganizationDao).getOrganizationByName(name);
	}

	@Test
	public void testGetOrganizationByNameTrim() {
		String name = " org.org\n";
		String trimmedName = "org.org";
		when(mockOrganizationDao.getOrganizationByName(trimmedName)).thenReturn(organization);
		// call under test
		Organization result = manager.getOrganizationByName(user, name);
		assertEquals(result, organization);
		verify(mockOrganizationDao).getOrganizationByName(trimmedName);
	}

	@Test
	public void testGetSchemaVersionId() {
		String versionId = "123";
		when(mockSchemaDao.getLatestVersionId(any(), any())).thenReturn(versionId);
		// call under test
		String id = manager.getSchemaVersionId("org/one");
		assertEquals(versionId, id);
		String organizationName = "org";
		String schemaName = "one";
		verify(mockSchemaDao).getLatestVersionId(organizationName, schemaName);
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
	}

	@Test
	public void testGetSchemaVersionIdWithVersion() {
		String versionId = "123";
		when(mockSchemaDao.getVersionId(any(), any(), any())).thenReturn(versionId);
		// call under test
		String id = manager.getSchemaVersionId("org/one/1.0.1");
		assertEquals(versionId, id);
		String organizationName = "org";
		String schemaName = "one";
		String semanticVersion = "1.0.1";
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao).getVersionId(organizationName, schemaName, semanticVersion);
	}

	@Test
	public void testGetSchemaVersionIdWithNullId() {
		String $id = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.getSchemaVersionId($id);
		});
	}

	@Test
	public void testFindAllDependencies() {
		JsonSchema one = new JsonSchema();
		one.set$id("org/one");

		JsonSchema refToOne = new JsonSchema();
		refToOne.set$ref(one.get$id());

		JsonSchema two = new JsonSchema();
		two.set$id("org/two");
		two.setItems(refToOne);

		String organizationName = "org";
		String schemaName = "one";
		String versionId = "123";
		when(mockSchemaDao.getLatestVersionId(any(), any())).thenReturn(versionId);
		JsonSchemaVersionInfo versionInfo = new JsonSchemaVersionInfo();
		versionInfo.setVersionId(versionId);
		versionInfo.setSchemaId("111");
		when(mockSchemaDao.getVersionInfo(any())).thenReturn(versionInfo);

		// call under test
		List<SchemaDependency> actual = manager.findAllDependencies(two);
		assertNotNull(actual);
		List<SchemaDependency> expected = Lists.newArrayList(new SchemaDependency().withDependsOnSchemaId("111"));
		assertEquals(expected, actual);
		verify(mockSchemaDao).getLatestVersionId(organizationName, schemaName);
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
		verify(mockSchemaDao).getVersionInfo(versionId);
	}

	@Test
	public void testFindAllDependenciesWithVersion() {
		JsonSchema one = new JsonSchema();
		one.set$id("org/one/1.1.1");

		JsonSchema refToOne = new JsonSchema();
		refToOne.set$ref(one.get$id());

		JsonSchema two = new JsonSchema();
		two.set$id("org/two");
		two.setItems(refToOne);

		String organizationName = "org";
		String schemaName = "one";
		String semanticVersion = "1.1.1";
		String versionId = "123";
		when(mockSchemaDao.getVersionId(any(), any(), any())).thenReturn(versionId);
		JsonSchemaVersionInfo versionInfo = new JsonSchemaVersionInfo();
		versionInfo.setVersionId(versionId);
		versionInfo.setSchemaId("111");
		when(mockSchemaDao.getVersionInfo(any())).thenReturn(versionInfo);

		// call under test
		List<SchemaDependency> actual = manager.findAllDependencies(two);
		assertNotNull(actual);
		// depends on both the schemaId and versionId
		ArrayList<SchemaDependency> expected = Lists
				.newArrayList(new SchemaDependency().withDependsOnSchemaId("111").withDependsOnVersionId(versionId));
		assertEquals(expected, actual);
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao).getVersionId(organizationName, schemaName, semanticVersion);
		verify(mockSchemaDao).getVersionInfo(versionId);
	}
	
	@Test
	public void testFindAllDependenciesWith$RefNotFound() {
		JsonSchema one = new JsonSchema();
		one.set$id("org/one/1.1.1");

		JsonSchema refToOne = new JsonSchema();
		refToOne.set$ref(one.get$id());

		JsonSchema two = new JsonSchema();
		two.set$id("org/two");
		two.setItems(refToOne);

		String organizationName = "org";
		String schemaName = "one";
		String semanticVersion = "1.1.1";
		String versionId = "123";
		when(mockSchemaDao.getVersionId(any(), any(), any())).thenThrow(new NotFoundException());

		assertThrows(NotFoundException.class, ()->{
			// call under test
			 manager.findAllDependencies(two);
		});
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao).getVersionId(organizationName, schemaName, semanticVersion);
		verify(mockSchemaDao, never()).getVersionInfo(versionId);
	}
	
	@Test
	public void testFindAllDependenciesWithNo$Refs() {
		JsonSchema one = new JsonSchema();
		one.set$id("org/one/1.1.1");

		// two depends on one directly
		JsonSchema two = new JsonSchema();
		two.set$id("org/two");
		two.setItems(one);

		// call under test
		List<SchemaDependency> actual = manager.findAllDependencies(two);
		List<SchemaDependency> expected = new ArrayList<SchemaDependency>();
		assertEquals(expected, actual);
		
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
		verify(mockSchemaDao, never()).getVersionInfo(versionId);
	}
	
	@Test
	public void testFindAllDependenciesWithNullSchema() {
		JsonSchema nullSchema = null;
		assertThrows(IllegalArgumentException.class, ()->{
			// call under test
			 manager.findAllDependencies(nullSchema);
		});
	}
	
	@Test
	public void testFindAllDependenciesWithNoRefs() {
		JsonSchema one = new JsonSchema();
		one.set$id("org/one/1.1.1");
		// call under test
		List<SchemaDependency> actual = manager.findAllDependencies(one);
		assertNotNull(actual);
		// depends on both the schemaId and versionId
		ArrayList<SchemaDependency> expected = new ArrayList<SchemaDependency>();
		assertEquals(expected, actual);
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
		verify(mockSchemaDao, never()).getVersionInfo(any());
	}


	@Test
	public void testCreateJsonSchema() {
		when(mockOrganizationDao.getOrganizationByName(any())).thenReturn(organization);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.authorized());
		when(mockSchemaDao.createNewSchemaVersion(any())).thenReturn(versionInfo);
		// call under test
		CreateSchemaResponse response = manager.createJsonSchema(user, createSchemaRequest);
		assertNotNull(response);
		assertEquals(versionInfo, response.getNewVersionInfo());
		verify(mockOrganizationDao).getOrganizationByName(organizationName);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.CREATE);
		NewSchemaVersionRequest expectedNewSchemaRequest = new NewSchemaVersionRequest()
				.withOrganizationId(organization.getId()).withSchemaName(schemaName).withCreatedBy(user.getId())
				.withJsonSchema(schema).withSemanticVersion(semanticVersionString)
				.withDependencies(new ArrayList<SchemaDependency>());
		verify(mockSchemaDao).createNewSchemaVersion(expectedNewSchemaRequest);
	}

	@Test
	public void testCreateJsonSchemaNullVersion() {
		when(mockOrganizationDao.getOrganizationByName(any())).thenReturn(organization);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.authorized());
		when(mockSchemaDao.createNewSchemaVersion(any())).thenReturn(versionInfo);
		schema.set$id(organizationName + "/" + schemaName);
		// call under test
		CreateSchemaResponse response = manager.createJsonSchema(user, createSchemaRequest);
		assertNotNull(response);
		assertEquals(versionInfo, response.getNewVersionInfo());
		verify(mockOrganizationDao).getOrganizationByName(organizationName);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.CREATE);
		NewSchemaVersionRequest expectedNewSchemaRequest = new NewSchemaVersionRequest()
				.withOrganizationId(organization.getId()).withSchemaName(schemaName).withCreatedBy(user.getId())
				.withJsonSchema(schema).withSemanticVersion(null).withDependencies(new ArrayList<SchemaDependency>());
		verify(mockSchemaDao).createNewSchemaVersion(expectedNewSchemaRequest);
	}

	@Test
	public void testCreateJsonSchemaAnonymous() {
		String message = assertThrows(UnauthorizedException.class, () -> {
			manager.createJsonSchema(anonymousUser, createSchemaRequest);
		}).getMessage();
		assertEquals("Must login to perform this action", message);
	}

	@Test
	public void testCreateJsonSchemaUnauthorized() {
		when(mockOrganizationDao.getOrganizationByName(any())).thenReturn(organization);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.accessDenied("no"));
		String message = assertThrows(UnauthorizedException.class, () -> {
			manager.createJsonSchema(user, createSchemaRequest);
		}).getMessage();
		assertEquals("no", message);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.CREATE);
	}

	@Test
	public void testCreateJsonSchemaNullUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.createJsonSchema(user, createSchemaRequest);
		});
	}

	@Test
	public void testCreateJsonSchemaNullRequest() {
		createSchemaRequest = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.createJsonSchema(user, createSchemaRequest);
		});
	}

	@Test
	public void testCreateJsonSchemaNullSchema() {
		createSchemaRequest.setSchema(null);
		assertThrows(IllegalArgumentException.class, () -> {
			manager.createJsonSchema(user, createSchemaRequest);
		});
	}

	@Test
	public void testGetSchemaWithVersion() {
		when(mockSchemaDao.getVersionId(any(), any(), any())).thenReturn(versionId);
		when(mockSchemaDao.getSchema(any())).thenReturn(schema);
		// call under test
		JsonSchema result = manager.getSchema(organizationName, schemaName, semanticVersionString);
		assertEquals(schema, result);
		verify(mockSchemaDao).getVersionId(organizationName, schemaName, semanticVersionString);
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao).getSchema(versionId);
	}

	@Test
	public void testGetSchemaWithVersionTrim() {
		when(mockSchemaDao.getVersionId(any(), any(), any())).thenReturn(versionId);
		when(mockSchemaDao.getSchema(any())).thenReturn(schema);
		// call under test
		JsonSchema result = manager.getSchema(organizationName + "\n", schemaName + " ", semanticVersionString + "\t");
		assertEquals(schema, result);
		verify(mockSchemaDao).getVersionId(organizationName, schemaName, semanticVersionString);
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao).getSchema(versionId);
	}

	@Test
	public void testGetSchemaNullVersion() {
		when(mockSchemaDao.getLatestVersionId(any(), any())).thenReturn(versionId);
		when(mockSchemaDao.getSchema(any())).thenReturn(schema);
		semanticVersionString = null;
		// call under test
		JsonSchema result = manager.getSchema(organizationName, schemaName, semanticVersionString);
		assertEquals(schema, result);
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
		verify(mockSchemaDao).getLatestVersionId(organizationName, schemaName);
		verify(mockSchemaDao).getSchema(versionId);
	}

	@Test
	public void testGetSchemaNullVersionTrim() {
		when(mockSchemaDao.getLatestVersionId(any(), any())).thenReturn(versionId);
		when(mockSchemaDao.getSchema(any())).thenReturn(schema);
		semanticVersionString = null;
		// call under test
		JsonSchema result = manager.getSchema(organizationName + " ", schemaName + " \t", semanticVersionString);
		assertEquals(schema, result);
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
		verify(mockSchemaDao).getLatestVersionId(organizationName, schemaName);
		verify(mockSchemaDao).getSchema(versionId);
	}

	@Test
	public void testGetSchemaEmptyVersion() {
		when(mockSchemaDao.getLatestVersionId(any(), any())).thenReturn(versionId);
		when(mockSchemaDao.getSchema(any())).thenReturn(schema);
		semanticVersionString = " ";
		// call under test
		JsonSchema result = manager.getSchema(organizationName, schemaName, semanticVersionString);
		assertEquals(schema, result);
		verify(mockSchemaDao, never()).getVersionId(any(), any(), any());
		verify(mockSchemaDao).getLatestVersionId(organizationName, schemaName);
		verify(mockSchemaDao).getSchema(versionId);
	}

	@Test
	public void testGetSchemaNullOrganization() {
		organizationName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.getSchema(organizationName, schemaName, semanticVersionString);
		});
	}

	@Test
	public void testGetSchemaNullSchemaName() {
		schemaName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.getSchema(organizationName, schemaName, semanticVersionString);
		});
	}

	@Test
	public void testDeleteSchema() {
		when(mockSchemaDao.getVersionLatestInfo(any(), any())).thenReturn(versionInfo);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.authorized());

		// call under test
		manager.deleteSchemaAllVersion(user, organizationName, schemaName);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockSchemaDao).deleteSchema(versionInfo.getSchemaId());
	}

	@Test
	public void testDeleteSchemaAdmin() {
		when(mockSchemaDao.getVersionLatestInfo(any(), any())).thenReturn(versionInfo);
		// call under test
		manager.deleteSchemaAllVersion(adminUser, organizationName, schemaName);
		verify(mockAclDao, never()).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockSchemaDao).deleteSchema(versionInfo.getSchemaId());
	}

	@Test
	public void testDeleteSchemaUnauthorized() {
		when(mockSchemaDao.getVersionLatestInfo(any(), any())).thenReturn(versionInfo);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.accessDenied("nope"));
		assertThrows(UnauthorizedException.class, () -> {
			// call under test
			manager.deleteSchemaAllVersion(user, organizationName, schemaName);
		});
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockSchemaDao, never()).deleteSchema(any());
	}

	@Test
	public void testDeleteSchemaNullUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.deleteSchemaAllVersion(user, organizationName, schemaName);
		});
	}

	@Test
	public void testDeleteSchemaNullOrganization() {
		organizationName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.deleteSchemaAllVersion(user, organizationName, schemaName);
		});
	}

	@Test
	public void testDeleteSchemaNullSchema() {
		schemaName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.deleteSchemaAllVersion(user, organizationName, schemaName);
		});
	}

	@Test
	public void testDeleteSchemaVersion() {
		when(mockSchemaDao.getVersionInfo(any(), any(), any())).thenReturn(versionInfo);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.authorized());
		// call under test
		manager.deleteSchemaVersion(user, organizationName, schemaName, semanticVersionString);
		verify(mockSchemaDao).getVersionInfo(organizationName, schemaName, semanticVersionString);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockSchemaDao).deleteSchemaVersion(versionId);
	}

	@Test
	public void testDeleteSchemaVersionUnauthorized() {
		when(mockSchemaDao.getVersionInfo(any(), any(), any())).thenReturn(versionInfo);
		when(mockAclDao.canAccess(any(UserInfo.class), any(), any(), any()))
				.thenReturn(AuthorizationStatus.accessDenied("naw"));
		assertThrows(UnauthorizedException.class, () -> {
			// call under test
			manager.deleteSchemaVersion(user, organizationName, schemaName, semanticVersionString);
		});
		verify(mockSchemaDao).getVersionInfo(organizationName, schemaName, semanticVersionString);
		verify(mockAclDao).canAccess(user, organization.getId(), ObjectType.ORGANIZATION, ACCESS_TYPE.DELETE);
		verify(mockSchemaDao, never()).deleteSchemaVersion(any());
	}

	@Test
	public void testDeleteSchemaVersionAdmin() {
		when(mockSchemaDao.getVersionInfo(any(), any(), any())).thenReturn(versionInfo);
		// call under test
		manager.deleteSchemaVersion(adminUser, organizationName, schemaName, semanticVersionString);
		verify(mockSchemaDao).getVersionInfo(organizationName, schemaName, semanticVersionString);
		verify(mockAclDao, never()).canAccess(any(UserInfo.class), any(), any(), any());
		verify(mockSchemaDao).deleteSchemaVersion(versionId);
	}

	@Test
	public void testDeleteSchemaVersionNullUser() {
		user = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.deleteSchemaVersion(user, organizationName, schemaName, semanticVersionString);
		});
	}

	@Test
	public void testDeleteSchemaVersionNullOrganizationName() {
		organizationName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.deleteSchemaVersion(user, organizationName, schemaName, semanticVersionString);
		});
	}

	@Test
	public void testDeleteSchemaVersionNullSchemaName() {
		schemaName = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.deleteSchemaVersion(user, organizationName, schemaName, semanticVersionString);
		});
	}

	@Test
	public void testDeleteSchemaVersionNullSemanticVersion() {
		semanticVersionString = null;
		assertThrows(IllegalArgumentException.class, () -> {
			// call under test
			manager.deleteSchemaVersion(user, organizationName, schemaName, semanticVersionString);
		});
	}

	@Test
	public void testListOrganizations() {
		List<Organization> results = new ArrayList<Organization>();
		for (int i = 0; i < NextPageToken.MAX_LIMIT + 1; i++) {
			results.add(new Organization());
		}
		when(mockOrganizationDao.listOrganizations(anyLong(), anyLong())).thenReturn(results);
		// call under test
		ListOrganizationsResponse response = manager.listOrganizations(listOrganizationsRequest);
		assertNotNull(response);
		assertNotNull(response.getPage());
		assertEquals("50a50", response.getNextPageToken());
		long expectedLimit = 51;
		long expectedOffset = 0;
		verify(mockOrganizationDao).listOrganizations(expectedLimit, expectedOffset);
	}

	@Test
	public void testListOrganizationsWithNullRequest() {
		listOrganizationsRequest = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.listOrganizations(listOrganizationsRequest);
		});
	}

	@Test
	public void testListSchemas() {
		List<JsonSchemaInfo> results = new ArrayList<JsonSchemaInfo>();
		for (int i = 0; i < NextPageToken.MAX_LIMIT + 1; i++) {
			results.add(new JsonSchemaInfo());
		}
		when(mockSchemaDao.listSchemas(any(), anyLong(), anyLong())).thenReturn(results);
		// call under test
		ListJsonSchemaInfoResponse response = manager.listSchemas(listJsonSchemaInfoRequest);
		assertNotNull(response);
		assertNotNull(response.getPage());
		assertEquals("50a50", response.getNextPageToken());
		long expectedLimit = 51;
		long expectedOffset = 0;
		verify(mockSchemaDao).listSchemas(organizationName, expectedLimit, expectedOffset);
	}

	@Test
	public void testListSchemasWithNullRequest() {
		listJsonSchemaInfoRequest = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.listSchemas(listJsonSchemaInfoRequest);
		});
	}

	@Test
	public void testListSchemasWithNullOrganizationName() {
		listJsonSchemaInfoRequest.setOrganizationName(null);
		assertThrows(IllegalArgumentException.class, () -> {
			manager.listSchemas(listJsonSchemaInfoRequest);
		});
	}

	@Test
	public void testListSchemaVersions() {
		List<JsonSchemaVersionInfo> results = new ArrayList<JsonSchemaVersionInfo>();
		for (int i = 0; i < NextPageToken.MAX_LIMIT + 1; i++) {
			results.add(new JsonSchemaVersionInfo());
		}
		when(mockSchemaDao.listSchemaVersions(any(), any(), anyLong(), anyLong())).thenReturn(results);
		// call under test
		ListJsonSchemaVersionInfoResponse response = manager.listSchemaVersions(listJsonSchemaVersionInfoRequest);
		assertNotNull(response);
		assertNotNull(response.getPage());
		assertEquals("50a50", response.getNextPageToken());
		long expectedLimit = 51;
		long expectedOffset = 0;
		verify(mockSchemaDao).listSchemaVersions(organizationName, schemaName, expectedLimit, expectedOffset);
	}

	@Test
	public void testListSchemaVersionsWithNullRequest() {
		listJsonSchemaVersionInfoRequest = null;
		assertThrows(IllegalArgumentException.class, () -> {
			manager.listSchemaVersions(listJsonSchemaVersionInfoRequest);
		});
	}

	@Test
	public void testListSchemaVersionsWithNullOrganizationName() {
		listJsonSchemaVersionInfoRequest.setOrganizationName(null);
		assertThrows(IllegalArgumentException.class, () -> {
			manager.listSchemaVersions(listJsonSchemaVersionInfoRequest);
		});
	}

	@Test
	public void testListSchemaVersionsWithNullSchemaName() {
		listJsonSchemaVersionInfoRequest.setSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> {
			manager.listSchemaVersions(listJsonSchemaVersionInfoRequest);
		});
	}
	
	@Test
	public void testGetLatestVersion() {
		String organizationName = "org";
		String schemaName = "one";
		String versionId = "111";
		when(mockSchemaDao.getLatestVersionId(any(), any())).thenReturn(versionId);
		JsonSchemaVersionInfo expected = new JsonSchemaVersionInfo();
		expected.setSchemaId("222");
		expected.setVersionId(versionId);
		when(mockSchemaDao.getVersionInfo(any())).thenReturn(expected);
		// call under test
		JsonSchemaVersionInfo actual = manager.getLatestVersion(organizationName, schemaName);
		assertEquals(expected, actual);
		verify(mockSchemaDao).getLatestVersionId(organizationName, schemaName);
		verify(mockSchemaDao).getVersionInfo(versionId);
	}
	
	@Test
	public void testGetLatestVersionWithNullOrganization() {
		String organizationName = null;
		String schemaName = "one";
		assertThrows(IllegalArgumentException.class, ()->{
			// call under test
			manager.getLatestVersion(organizationName, schemaName);
		});
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao, never()).getVersionInfo(any());
	}
	
	@Test
	public void testGetLatestVersionWithNullSchemaName() {
		String organizationName = "org";
		String schemaName = null;
		assertThrows(IllegalArgumentException.class, ()->{
			// call under test
			manager.getLatestVersion(organizationName, schemaName);
		});
		verify(mockSchemaDao, never()).getLatestVersionId(any(), any());
		verify(mockSchemaDao, never()).getVersionInfo(any());
	}
}
