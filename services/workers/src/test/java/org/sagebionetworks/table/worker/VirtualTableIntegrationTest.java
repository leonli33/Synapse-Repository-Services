package org.sagebionetworks.table.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sagebionetworks.repo.model.util.AccessControlListUtil.createResourceAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagebionetworks.AsynchronousJobWorkerHelper;
import org.sagebionetworks.repo.manager.EntityManager;
import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.manager.table.ColumnModelManager;
import org.sagebionetworks.repo.manager.table.TableManagerSupport;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AsynchJobFailedException;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.annotation.v2.Annotations;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsV2TestUtils;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.auth.NewUser;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.model.helper.AccessControlListObjectHelper;
import org.sagebionetworks.repo.model.helper.FileHandleObjectHelper;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.table.AppendableRowSetRequest;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.ColumnType;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.table.Query;
import org.sagebionetworks.repo.model.table.QueryOptions;
import org.sagebionetworks.repo.model.table.ReplicationType;
import org.sagebionetworks.repo.model.table.Row;
import org.sagebionetworks.repo.model.table.RowReferenceSet;
import org.sagebionetworks.repo.model.table.RowReferenceSetResults;
import org.sagebionetworks.repo.model.table.RowSet;
import org.sagebionetworks.repo.model.table.TableEntity;
import org.sagebionetworks.repo.model.table.TableUpdateTransactionRequest;
import org.sagebionetworks.repo.model.table.TableUpdateTransactionResponse;
import org.sagebionetworks.repo.model.table.ViewEntityType;
import org.sagebionetworks.repo.model.table.ViewTypeMask;
import org.sagebionetworks.repo.model.table.VirtualTable;
import org.sagebionetworks.table.cluster.utils.TableModelUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class VirtualTableIntegrationTest {

	public static final Long MAX_WAIT_MS = 30_000L;

	@Autowired
	private AsynchronousJobWorkerHelper asyncHelper;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private UserManager userManager;

	@Autowired
	private AccessControlListObjectHelper aclDaoHelper;

	@Autowired
	private ColumnModelManager columnModelManager;

	@Autowired
	private FileHandleObjectHelper fileHandleObjectHelper;

	@Autowired
	private TableManagerSupport tableManagerSupport;

	private UserInfo adminUserInfo;
	private UserInfo userInfo;

	@BeforeEach
	public void before() {
		aclDaoHelper.truncateAll();
		entityManager.truncateAll();

		adminUserInfo = userManager.getUserInfo(BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId());

		boolean acceptsTermsOfUse = true;
		String userName = UUID.randomUUID().toString();
		userInfo = userManager.createOrGetTestUser(adminUserInfo,
				new NewUser().setUserName(userName).setEmail(userName + "@foo.org"), acceptsTermsOfUse);
	}

	@AfterEach
	public void after() {
		aclDaoHelper.truncateAll();
		entityManager.truncateAll();
	}

	@Test
	public void testVirtualTableAgainstTable() throws Exception {

		List<Entity> entitites = createProjectHierachy();
		Project project = (Project) entitites.get(0);

		List<ColumnModel> tableSchema = List.of(
				new ColumnModel().setName("foo").setColumnType(ColumnType.STRING).setMaximumSize(50L),
				new ColumnModel().setName("bar").setColumnType(ColumnType.INTEGER));
		tableSchema = columnModelManager.createColumnModels(adminUserInfo, tableSchema);
		ColumnModel foo = tableSchema.get(0);

		TableEntity table = asyncHelper.createTable(adminUserInfo, "sometable", project.getId(),
				tableSchema.stream().map(ColumnModel::getId).collect(Collectors.toList()), false);

		List<Row> row = List.of(new Row().setValues(List.of("a", "1")), new Row().setValues(List.of("a", "5")),
				new Row().setValues(List.of("b", "2")), new Row().setValues(List.of("b", "16")));
		appendRowsToTable(tableSchema, table.getId(), row);

		asyncHelper.assertQueryResult(adminUserInfo, "select count(*) from " + table.getId(), (results) -> {
			assertEquals(List.of(new Row().setValues(List.of("4"))),
					results.getQueryResult().getQueryResults().getRows());
		}, MAX_WAIT_MS);

		ColumnModel barSum = columnModelManager
				.createColumnModel(new ColumnModel().setName("barSum").setColumnType(ColumnType.INTEGER));

		String definingSql = String.format("select foo, cast(sum(bar) as %s) from %s group by foo order by foo",
				barSum.getId(), table.getId());

		VirtualTable virtualTable = asyncHelper.createVirtualTable(adminUserInfo, project.getId(), definingSql);
		assertEquals(List.of(tableSchema.get(0).getId(), barSum.getId()), virtualTable.getColumnIds());

		Query query = new Query();
		query.setSql("select * from " + virtualTable.getId());
		query.setIncludeEntityEtag(false);

		QueryOptions options = new QueryOptions().withRunQuery(true).withRunCount(true).withReturnFacets(false)
				.withReturnColumnModels(true);

		asyncHelper.assertQueryResult(adminUserInfo, query, options, (results) -> {
			assertEquals(List.of(new Row().setValues(List.of("a", "6")), new Row().setValues(List.of("b", "18"))),
					results.getQueryResult().getQueryResults().getRows());
			assertEquals(List.of(foo, barSum), results.getColumnModels());
			assertEquals(2L, results.getQueryCount());
		}, MAX_WAIT_MS);

		String message = assertThrows(UnauthorizedException.class, () -> {
			asyncHelper.assertQueryResult(userInfo, query, options, (results) -> {
				// should fail
			}, MAX_WAIT_MS);
		}).getMessage();
		assertEquals("You lack DOWNLOAD access to the requested entity.", message);
	}

	@Test
	public void testVirtualTableWithSourceView() throws Exception {
		List<Entity> containers = createProjectHierachy();
		String projectId = containers.get(0).getId();
		int filesPerContainer = 5;
		List<FileEntity> files = createFiles(containers, filesPerContainer);
		FileEntity lastFile = files.get(files.size() - 1);
		asyncHelper.waitForObjectReplication(ReplicationType.ENTITY, KeyFactory.stringToKey(lastFile.getId()),
				lastFile.getEtag(), MAX_WAIT_MS);
		EntityView view = createEntityView(projectId, files);
		
		asyncHelper.assertQueryResult(adminUserInfo, "select count(*) from " + view.getId(), (results) -> {
			assertEquals(List.of(new Row().setValues(List.of("15"))),
					results.getQueryResult().getQueryResults().getRows());
		}, MAX_WAIT_MS);
		
		// at this point the view exists and is ready for query.
		String definingSql = String.format("select * from %s", view.getId());
		
		VirtualTable virtualTable = asyncHelper.createVirtualTable(adminUserInfo, projectId, definingSql);
		
		String runtimeSql = "select count(*) from "+virtualTable.getId();
		// admin should see all
		asyncHelper.assertQueryResult(adminUserInfo, runtimeSql, (results) -> {
			assertEquals(List.of(new Row().setValues(List.of("15"))),
					results.getQueryResult().getQueryResults().getRows());
		}, MAX_WAIT_MS);
		// user should only see the subset
		asyncHelper.assertQueryResult(userInfo, runtimeSql, (results) -> {
			assertEquals(List.of(new Row().setValues(List.of("10"))),
					results.getQueryResult().getQueryResults().getRows());
		}, MAX_WAIT_MS);

	}
	

	void appendRowsToTable(List<ColumnModel> schema, String tableId, List<Row> rows)
			throws AssertionError, AsynchJobFailedException {
		RowSet set = new RowSet().setRows(rows).setTableId(tableId)
				.setHeaders(TableModelUtils.getSelectColumns(schema));
		AppendableRowSetRequest request = new AppendableRowSetRequest().setEntityId(tableId).setEntityId(tableId)
				.setToAppend(set);

		TableUpdateTransactionRequest txRequest = TableModelUtils.wrapInTransactionRequest(request);

		// Wait for the job to complete.
		asyncHelper.assertJobResponse(adminUserInfo, txRequest, (TableUpdateTransactionResponse response) -> {
			RowReferenceSetResults results = TableModelUtils.extractResponseFromTransaction(response,
					RowReferenceSetResults.class);
			assertNotNull(results.getRowReferenceSet());
			RowReferenceSet refSet = results.getRowReferenceSet();
			assertNotNull(refSet.getRows());
			assertEquals(rows.size(), refSet.getRows().size());
		}, MAX_WAIT_MS);
	}

	/**
	 * Create a project with two folders. The non-admin user has "read" on 'folder
	 * one' and no access on 'folder two'
	 * 
	 * @return index 0 = project, index 1 = 'folder one', index 2 = 'folder two'
	 */
	public List<Entity> createProjectHierachy() {

		List<Entity> results = new ArrayList<>(3);
		Project project = entityManager.getEntity(adminUserInfo, createProject(), Project.class);
		results.add(project);

		Folder folderOne = entityManager.getEntity(adminUserInfo, entityManager.createEntity(adminUserInfo,
				new Folder().setName("folder one").setParentId(project.getId()), null), Folder.class);
		results.add(folderOne);
		Folder folderTwo = entityManager.getEntity(adminUserInfo, entityManager.createEntity(adminUserInfo,
				new Folder().setName("folder two").setParentId(project.getId()), null), Folder.class);
		results.add(folderTwo);

		// grant the user read on the project
		aclDaoHelper.update(project.getId(), ObjectType.ENTITY, a -> {
			a.getResourceAccess().add(createResourceAccess(userInfo.getId(), ACCESS_TYPE.READ));
		});

		// add an ACL on folder two that does not grant the user read.
		aclDaoHelper.create(a -> {
			a.setId(folderTwo.getId());
			a.getResourceAccess().add(createResourceAccess(adminUserInfo.getId(), ACCESS_TYPE.CHANGE_PERMISSIONS));
			a.getResourceAccess().add(createResourceAccess(adminUserInfo.getId(), ACCESS_TYPE.CREATE));
			a.getResourceAccess().add(createResourceAccess(adminUserInfo.getId(), ACCESS_TYPE.UPDATE));
			a.getResourceAccess().add(createResourceAccess(adminUserInfo.getId(), ACCESS_TYPE.DELETE));
		});

		return results;
	}

	/**
	 * Create numberOfFiles in each of the provided containers.
	 * 
	 * @param containers
	 * @param numberOfFiles
	 * @return
	 */
	public List<FileEntity> createFiles(List<Entity> containers, int numberOfFiles) {
		List<FileEntity> files = new ArrayList<>(numberOfFiles);
		containers.forEach((c) -> {
			for (int i = 0; i < numberOfFiles; i++) {
				String parentId = c.getId();
				final int index = i;
				S3FileHandle fileHandle = fileHandleObjectHelper.createS3(f -> {
					f.setFileName("f" + index);
				});
				FileEntity file = entityManager.getEntity(adminUserInfo,
						entityManager.createEntity(adminUserInfo, new FileEntity().setName("file_" + index)
								.setParentId(parentId).setDataFileHandleId(fileHandle.getId()), null),
						FileEntity.class);
				files.add(file);
			}
		});
		return files;
	}

	public EntityView createEntityView(String parentId, List<FileEntity> files)
			throws DatastoreException, InterruptedException {

		Long viewTypeMask = ViewTypeMask.File.getMask();
		List<ColumnModel> schema = tableManagerSupport.getDefaultTableViewColumns(ViewEntityType.entityview,
				viewTypeMask);
		schema.add(new ColumnModel().setName("stringKey").setColumnType(ColumnType.STRING).setMaximumSize(50L));
//		schema.add(new ColumnModel().setName("doubleKey").setColumnType(ColumnType.DOUBLE));
		schema.add(new ColumnModel().setName("longKey").setColumnType(ColumnType.INTEGER));
		schema.add(new ColumnModel().setName("dateKey").setColumnType(ColumnType.DATE));
		schema.add(new ColumnModel().setName("booleanKey").setColumnType(ColumnType.BOOLEAN));
		schema = columnModelManager.createColumnModels(adminUserInfo, schema);

		// Add annotations to each files
		for (int i = 0; i < files.size(); i++) {
			Entity entity = files.get(i);
			FileEntity file = (FileEntity) entity;
			Annotations annos = entityManager.getAnnotations(adminUserInfo, file.getId());
			AnnotationsV2TestUtils.putAnnotations(annos, "stringKey", "a string: " + i, AnnotationsValueType.STRING);
			AnnotationsV2TestUtils.putAnnotations(annos, "doubleKey", Double.toString(3.14 + i),
					AnnotationsValueType.DOUBLE);
			AnnotationsV2TestUtils.putAnnotations(annos, "longKey", Long.toString(5 + i), AnnotationsValueType.LONG);
			AnnotationsV2TestUtils.putAnnotations(annos, "dateKey", Long.toString(1001 + i),
					AnnotationsValueType.TIMESTAMP_MS);
			AnnotationsV2TestUtils.putAnnotations(annos, "booleanKey", Boolean.toString(i % 2 == 0),
					AnnotationsValueType.BOOLEAN);
			entityManager.updateAnnotations(adminUserInfo, file.getId(), annos);
		}
		
		for(FileEntity file: files) {
			// each file needs to be replicated.
			asyncHelper.waitForEntityReplication(adminUserInfo, file.getId(), MAX_WAIT_MS);
		}

		List<String> scope = Arrays.asList(parentId);
		List<String> columnIds = schema.stream().map(c -> c.getId()).collect(Collectors.toList());
		return asyncHelper.createEntityView(adminUserInfo, UUID.randomUUID().toString(), parentId, columnIds, scope,
				viewTypeMask, false);
	}

	private String createProject() {
		return entityManager.createEntity(adminUserInfo, new Project().setName(null), null);
	}

}
