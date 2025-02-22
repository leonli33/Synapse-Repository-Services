package org.sagebionetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseForbiddenException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.evaluation.dao.SubmissionField;
import org.sagebionetworks.evaluation.dbo.DBOConstants;
import org.sagebionetworks.evaluation.model.BatchUploadResponse;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.EvaluationRound;
import org.sagebionetworks.evaluation.model.EvaluationRoundLimit;
import org.sagebionetworks.evaluation.model.EvaluationRoundLimitType;
import org.sagebionetworks.evaluation.model.EvaluationRoundListRequest;
import org.sagebionetworks.evaluation.model.EvaluationRoundListResponse;
import org.sagebionetworks.evaluation.model.MemberSubmissionEligibility;
import org.sagebionetworks.evaluation.model.Submission;
import org.sagebionetworks.evaluation.model.SubmissionBundle;
import org.sagebionetworks.evaluation.model.SubmissionContributor;
import org.sagebionetworks.evaluation.model.SubmissionEligibility;
import org.sagebionetworks.evaluation.model.SubmissionQuota;
import org.sagebionetworks.evaluation.model.SubmissionStatus;
import org.sagebionetworks.evaluation.model.SubmissionStatusBatch;
import org.sagebionetworks.evaluation.model.SubmissionStatusEnum;
import org.sagebionetworks.evaluation.model.TeamSubmissionEligibility;
import org.sagebionetworks.evaluation.model.UserEvaluationPermissions;
import org.sagebionetworks.reflection.model.PaginatedResults;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.Challenge;
import org.sagebionetworks.repo.model.ChallengeTeam;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.annotation.Annotations;
import org.sagebionetworks.repo.model.annotation.DoubleAnnotation;
import org.sagebionetworks.repo.model.annotation.StringAnnotation;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsV2Utils;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValue;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.auth.JSONWebTokenHelper;
import org.sagebionetworks.repo.model.entitybundle.v2.EntityBundle;
import org.sagebionetworks.repo.model.entitybundle.v2.EntityBundleRequest;
import org.sagebionetworks.repo.model.file.FileHandle;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.query.QueryTableResults;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.Query;
import org.sagebionetworks.repo.model.table.QueryOptions;
import org.sagebionetworks.repo.model.table.QueryResultBundle;
import org.sagebionetworks.repo.model.table.Row;
import org.sagebionetworks.repo.model.table.SelectColumn;
import org.sagebionetworks.repo.model.table.SubmissionView;
import org.sagebionetworks.repo.model.table.ViewEntityType;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.JSONObjectAdapterImpl;
import org.sagebionetworks.table.cluster.utils.TableModelUtils;
import org.sagebionetworks.utils.MD5ChecksumHelper;

import com.google.common.collect.ImmutableList;

/**
 * Exercise the Evaluation Services methods in the Synapse Java Client
 * 
 * @author bkng
 */
@ExtendWith(ITTestExtension.class)
public class IT520SynapseJavaClientEvaluationTest {

	private static SynapseClient synapseTwo;
	private static Long user2ToDelete;
	
	private Project project = null;
	private Folder dataset = null;
	private Project projectTwo = null;
	private FileHandle fileHandle = null;
	
	private static String userName;
	
	private Evaluation eval1;
	private Evaluation eval2;
	private Submission sub1;
	private Submission sub2;
	private File dataSourceFile;
	private FileEntity fileEntity;
	private Team participantTeam;
	
	private List<String> evaluationsToDelete;
	private List<String> submissionsToDelete;
	private List<String> entitiesToDelete;
	private List<String> teamsToDelete;
	private Challenge challenge;

	private static final int RDS_WORKER_TIMEOUT = 2*1000*60; // Two min
	private static final String FILE_NAME = "LittleImage.png";
	
	private static final String MOCK_TEAM_ENDPOINT = "https://www.synapse.org/#Team:";
	private static final String MOCK_NOTIFICATION_UNSUB_ENDPOINT = "https://www.synapse.org#unsub:";
	private static final String MOCK_CHALLENGE_ENDPOINT = "https://synapse.org/#ENTITY:";
	
	private SynapseAdminClient adminSynapse;
	private SynapseClient synapse;
	
	public IT520SynapseJavaClientEvaluationTest(SynapseAdminClient adminSynapse, SynapseClient synapse) {
		this.adminSynapse = adminSynapse;
		this.synapse = synapse;
	}
	
	@BeforeAll
	public static void beforeClass(SynapseAdminClient adminSynapse) throws Exception {
		synapseTwo = new SynapseClientImpl();
		user2ToDelete = SynapseClientHelper.createUser(adminSynapse, synapseTwo);
	}
	
	@BeforeEach
	public void before() throws DatastoreException, NotFoundException, SynapseException, IOException {
		adminSynapse.clearAllLocks();
		evaluationsToDelete = new ArrayList<String>();
		submissionsToDelete = new ArrayList<String>();
		entitiesToDelete = new ArrayList<String>();
		
		// create Entities
		project = synapse.createEntity(new Project());
		dataset = new Folder();
		dataset.setParentId(project.getId());
		dataset = synapse.createEntity(dataset);
		projectTwo = synapseTwo.createEntity(new Project());
		
		{
			dataSourceFile = File.createTempFile("integrationTest", ".txt");
			dataSourceFile.deleteOnExit();
			FileWriter writer = new FileWriter(dataSourceFile);
			writer.write("Hello world!");
			writer.close();
			FileHandle fileHandle = synapse.multipartUpload(dataSourceFile, null, false, false);
			fileEntity = new FileEntity();
			fileEntity.setParentId(project.getId());
			fileEntity.setDataFileHandleId(fileHandle.getId());
			fileEntity = synapse.createEntity(fileEntity);
		}
		
		entitiesToDelete.add(project.getId());
		entitiesToDelete.add(dataset.getId());
		entitiesToDelete.add(projectTwo.getId());
		
		// initialize Evaluations
		eval1 = new Evaluation();
		eval1.setName("some name");
		eval1.setDescription("description");
        eval1.setContentSource(project.getId());
        eval1.setSubmissionInstructionsMessage("foo");
        eval1.setSubmissionReceiptMessage("bar");
        eval2 = new Evaluation();
		eval2.setName("name2");
		eval2.setDescription("description");
        eval2.setContentSource(project.getId());
        eval2.setSubmissionInstructionsMessage("baz");
        eval2.setSubmissionReceiptMessage("mumble");
        
        // initialize Submissions
        sub1 = new Submission();
        sub1.setName("submission1");
        sub1.setVersionNumber(1L);
        sub1.setSubmitterAlias("Team Awesome!");
        sub2 = new Submission();
        sub2.setName("submission2");
        sub2.setVersionNumber(1L);
        sub2.setSubmitterAlias("Team Even Better!");
        
		teamsToDelete = new ArrayList<String>();
		// create a Team
		participantTeam = new Team();
		participantTeam.setCanPublicJoin(true);
		participantTeam.setName("challenge participant team");
		participantTeam = synapse.createTeam(participantTeam);
		teamsToDelete.add(participantTeam.getId());
		
		challenge = new Challenge();
		challenge.setProjectId(project.getId());
		challenge.setParticipantTeamId(participantTeam.getId());
		challenge = adminSynapse.createChallenge(challenge);
	}
	
	@AfterEach
	public void after() throws Exception {
		if (challenge!=null) {
			adminSynapse.deleteChallenge(challenge.getId());
		}
		// clean up submissions
		for (String id : submissionsToDelete) {
			try {
				adminSynapse.deleteSubmission(id);
			} catch (SynapseNotFoundException e) {}
		}
		// clean up evaluations
		for (String id : evaluationsToDelete) {
			try {
				adminSynapse.deleteEvaluation(id);
			} catch (SynapseNotFoundException e) {}
		}
		
		// clean up nodes
		for (String id : entitiesToDelete) {
			try {
				adminSynapse.deleteEntityById(id);
			} catch (SynapseNotFoundException e) {}
		}
		
		// clean up FileHandle
		if(fileHandle != null){
			try {
				adminSynapse.deleteFileHandle(fileHandle.getId());
			} catch (SynapseException e) { }
		}
		
		for (String id : teamsToDelete) {
			try {
				adminSynapse.deleteTeam(id);
			} catch (SynapseNotFoundException e) {}
		}
		dataSourceFile=null;
	}
	
	@AfterAll
	public static void afterClass(SynapseAdminClient adminSynapse) throws Exception {
		try {
			adminSynapse.deleteUser(user2ToDelete);
		} catch (SynapseException e) { }
	}

	@Test
	public void testEvaluationRoundTrip() throws SynapseException, UnsupportedEncodingException {
		int initialCount = synapse.getAvailableEvaluationsPaginated(0, 100).getResults().size();
		
		// Create
		eval1 = synapse.createEvaluation(eval1);
		assertNotNull(eval1.getEtag());
		assertNotNull(eval1.getId());
		evaluationsToDelete.add(eval1.getId());
		int newCount = initialCount + 1;
		assertEquals(newCount, synapse.getAvailableEvaluationsPaginated(0, 100).getResults().size());
		
		// Read
		Evaluation fetched = synapse.getEvaluation(eval1.getId());
		assertEquals(eval1, fetched);
		fetched = synapse.findEvaluation(eval1.getName());
		assertEquals(eval1, fetched);
		PaginatedResults<Evaluation> evals = synapse.getEvaluationByContentSource(project.getId(), 0, 10);
		assertEquals(1, evals.getTotalNumberOfResults());
		fetched = evals.getResults().get(0);
		assertEquals(eval1, fetched);
		
		// Update
		fetched.setDescription(eval1.getDescription() + " (modified)");
		fetched.setSubmissionInstructionsMessage("foobar2");
		Evaluation updated = synapse.updateEvaluation(fetched);
		assertFalse(updated.getEtag().equals(fetched.getEtag()), "eTag was not updated");
		fetched.setEtag(updated.getEtag());
		assertEquals(fetched, updated);
		
		// Delete
		synapse.deleteEvaluation(eval1.getId());
		
		assertThrows(SynapseException.class, () -> {
			synapse.getEvaluation(eval1.getId());
		});
		
		assertEquals(initialCount, synapse.getAvailableEvaluationsPaginated(100, 0).getResults().size());
	}

	@Test
	public void testEvaluationRound_RoundTrip() throws SynapseException {
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());

		Instant now = Instant.now();
		EvaluationRound round = new EvaluationRound();
		round.setEvaluationId(eval1.getId());
		round.setRoundStart(Date.from(now));
		round.setRoundEnd(Date.from(now.plus(1, ChronoUnit.DAYS)));

		//create
		EvaluationRound created = synapse.createEvaluationRound(round);

		//read
		EvaluationRound retrieved = synapse.getEvaluationRound(created.getEvaluationId(), created.getId());
		assertEquals(created, retrieved);

		// create second round
		EvaluationRound round2 = new EvaluationRound();
		round2.setEvaluationId(eval1.getId());
		round2.setRoundStart(Date.from(now.plus(1, ChronoUnit.DAYS)));
		round2.setRoundEnd(Date.from(now.plus(2, ChronoUnit.DAYS)));
		EvaluationRound created2 = synapse.createEvaluationRound(round2);

		//read all
		EvaluationRoundListResponse listResponse = synapse.getAllEvaluationRounds(created.getEvaluationId(), new EvaluationRoundListRequest());
		assertEquals(Arrays.asList(created, created2), listResponse.getPage());

		//update
		created.setLimits(Arrays.asList(newEvaluationRoundLimit(EvaluationRoundLimitType.DAILY, 45)));
		EvaluationRound updated = synapse.updateEvaluationRound(created);
		assertEquals(created.getLimits(), updated.getLimits());

		//delete
		synapse.deleteEvaluationRound(updated.getEvaluationId(), updated.getId());
		listResponse = synapse.getAllEvaluationRounds(created.getEvaluationId(), new EvaluationRoundListRequest());
		assertEquals(Arrays.asList(created2), listResponse.getPage());
	}

	@Test
	public void testEvaluationRoundLimitEnforcement() throws SynapseException {
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());


		//create a new EvaluationRound
		Instant now = Instant.now();
		EvaluationRound round = new EvaluationRound();
		round.setEvaluationId(eval1.getId());
		round.setRoundStart(Date.from(now));
		round.setRoundEnd(Date.from(now.plus(1, ChronoUnit.DAYS)));
		round.setLimits(Collections.singletonList(newEvaluationRoundLimit(EvaluationRoundLimitType.WEEKLY, 1L)));
		EvaluationRound created = synapse.createEvaluationRound(round);

		// create a submission
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(fileEntity.getId());
		sub1 = synapse.createIndividualSubmission(sub1, fileEntity.getEtag(), MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		submissionsToDelete.add(sub1.getId());

		sub2.setEvaluationId(eval1.getId());
		sub2.setEntityId(fileEntity.getId());
		String message = assertThrows(SynapseForbiddenException.class, () -> {
			sub2 = synapse.createIndividualSubmission(sub2, fileEntity.getEtag(), MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		}).getMessage();

		assertEquals("Submitter has reached the weekly limit of 1. (for the current submission round).", message);
	}

	private EvaluationRoundLimit newEvaluationRoundLimit(EvaluationRoundLimitType type, long maxSubmissions){
		EvaluationRoundLimit limit = new EvaluationRoundLimit();
		limit.setLimitType(type);
		limit.setMaximumSubmissions(maxSubmissions);
		return limit;
	}

	@Test
	public void testSubmissionView() throws Exception {
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		String entityId = fileEntity.getId();
		String entityEtag = fileEntity.getEtag();
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag, MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		SubmissionStatus submissionStatus = synapse.getSubmissionStatus(sub1.getId());
		
		submissionsToDelete.add(sub1.getId());
		
		List<ColumnModel> model = synapse.getDefaultColumnsForView(ViewEntityType.submissionview, null);
		
		// now create the view
		SubmissionView view = new SubmissionView();
		
		view.setName("Submission View");
		view.setColumnIds(TableModelUtils.getIds(model));
		view.setScopeIds(ImmutableList.of(eval1.getId()));
		view.setParentId(project.getId());
		view = synapse.createEntity(view);

		entitiesToDelete.add(view.getId());
		
		String sql = "select * from " + view.getId() + " order by id";
		
		final Long submissionId = KeyFactory.stringToKey(submissionStatus.getId());
		final String submissionEtag = submissionStatus.getEtag();
		final String evaluationId = KeyFactory.stringToKey(sub1.getEvaluationId()).toString();
		final String status = submissionStatus.getStatus().name();
		final String submitterId = sub1.getUserId();
		final String submitterAlias = sub1.getSubmitterAlias();
		
		Query query = new Query();
		
		query.setSql(sql);
		query.setIncludeEntityEtag(true);
		query.setOffset(0L);
		query.setLimit(1L);
		
		QueryOptions queryOptions = new QueryOptions()
				.withRunQuery(true)
				.withReturnSelectColumns(true);

		Consumer<QueryResultBundle> resultsConsumer = (result) -> {
			List<Row> rows = result.getQueryResult().getQueryResults().getRows();
			
			assertFalse(rows.isEmpty());
			
			if (rows.size() > 1) {
				throw new IllegalStateException("Expected one row, got " + rows.size());
			}
			
			Row submissionRow = rows.iterator().next();
			
			assertEquals(submissionId, submissionRow.getRowId());
			assertEquals(submissionEtag, submissionRow.getEtag());
			
			List<SelectColumn> columns = result.getQueryResult().getQueryResults().getHeaders();
			
			Map<SubmissionField, Integer> fieldIndexMap = getFieldsIndex(columns);
			
			assertEquals(status, submissionRow.getValues().get(fieldIndexMap.get(SubmissionField.status)));
			assertEquals(evaluationId, submissionRow.getValues().get(fieldIndexMap.get(SubmissionField.evaluationid)));
			assertEquals(submitterId, submissionRow.getValues().get(fieldIndexMap.get(SubmissionField.submitterid)));
			assertEquals(submitterAlias, submissionRow.getValues().get(fieldIndexMap.get(SubmissionField.submitteralias)));
		};
		
		AsyncJobHelper.assertQueryBundleResults(synapse, view.getId(), query, queryOptions, resultsConsumer, RDS_WORKER_TIMEOUT);
		
	}
	
	private static Map<SubmissionField, Integer> getFieldsIndex(List<SelectColumn> columns) {
		Map<SubmissionField, Integer> indexMap = new HashMap<>(SubmissionField.values().length);
		Map<String, SubmissionField> fieldsMap = Stream.of(SubmissionField.values())
				.collect(Collectors.toMap(SubmissionField::getColumnName, Function.identity()));
		
		int index = 0;
		
		for (SelectColumn column : columns) {
			SubmissionField matchedField = fieldsMap.get(column.getName());
			
			if (matchedField != null) {
				indexMap.put(matchedField, index);
			}
			
			index++;
		}
		
		return indexMap;
	}

	@Test
	public void testGetEvaluationsByAccessType() throws Exception {
		eval1 = synapse.createEvaluation(eval1);
		
		evaluationsToDelete.add(eval1.getId());
		
		ACCESS_TYPE accessType = ACCESS_TYPE.READ_PRIVATE_SUBMISSION;
		
		PaginatedResults<Evaluation> evals = synapse.getEvaluationByContentSource(project.getId(), accessType, 0, 10);
		assertEquals(1, evals.getTotalNumberOfResults());
		assertEquals(eval1, evals.getResults().get(0));
		
		// Another user cannot list it with READ_PRIVATE_SUBMISSION
		evals = synapseTwo.getEvaluationByContentSource(project.getId(), accessType, 0, 10);
		assertEquals(0, evals.getTotalNumberOfResults());
		
		// ..nor READ
		evals = synapseTwo.getEvaluationByContentSource(project.getId(), 0, 10);
		assertEquals(0, evals.getTotalNumberOfResults());

		AccessControlList acl = synapse.getEvaluationAcl(eval1.getId());

		// Update ACL
		ResourceAccess ra = new ResourceAccess();
		ra.setAccessType(Collections.singleton(ACCESS_TYPE.READ));
		
		Long user2Id = Long.parseLong(JSONWebTokenHelper.getSubjectFromJWTAccessToken(
				synapseTwo.getAccessToken()));
		ra.setPrincipalId(user2Id);
		
		acl.getResourceAccess().add(ra);
		acl = synapse.updateEvaluationAcl(acl);
		
		// Now the user can read
		evals = synapseTwo.getEvaluationByContentSource(project.getId(), 0, 10);
		assertEquals(1, evals.getTotalNumberOfResults());
		assertEquals(eval1, evals.getResults().get(0));
		
		// Same result specifying the accessType
		evals = synapseTwo.getEvaluationByContentSource(project.getId(), ACCESS_TYPE.READ, 0, 10);
		assertEquals(1, evals.getTotalNumberOfResults());
		assertEquals(eval1, evals.getResults().get(0));
		
		// But still cannot list with READ_PRIVATE_SUBMISSION
		evals = synapseTwo.getEvaluationByContentSource(project.getId(), accessType, 0, 10);
		assertEquals(0, evals.getTotalNumberOfResults());
	}
	
	@Test
	public void testGetEvaluationsByIds() throws Exception {
		eval1 = synapse.createEvaluation(eval1);
		
		evaluationsToDelete.add(eval1.getId());
		
		ACCESS_TYPE accessType = ACCESS_TYPE.READ_PRIVATE_SUBMISSION;
		boolean activeOnly = false;
		List<Long> evalIds = null;
		
		// Without
		PaginatedResults<Evaluation> evals = synapse.getEvaluationByContentSource(project.getId(), accessType, activeOnly, evalIds, 0, 10);
		
		assertEquals(1, evals.getTotalNumberOfResults());
		assertEquals(eval1, evals.getResults().get(0));
		
		// Filter by the eval id
		evalIds = Collections.singletonList(KeyFactory.stringToKey(eval1.getId()));
		
		evals = synapse.getEvaluationByContentSource(project.getId(), accessType, activeOnly, evalIds, 0, 10);
		
		assertEquals(1, evals.getTotalNumberOfResults());
		assertEquals(eval1, evals.getResults().get(0));
		
		// Non existing eval id
		evalIds = Collections.singletonList(-1L);
		
		evals = synapse.getEvaluationByContentSource(project.getId(), accessType, activeOnly, evalIds, 0, 10);
		
		assertTrue(evals.getResults().isEmpty());
		
		// Multiple
		evalIds = Arrays.asList(KeyFactory.stringToKey(eval1.getId()), -1L);
		
		evals = synapse.getEvaluationByContentSource(project.getId(), accessType, activeOnly, evalIds, 0, 10);
		
		assertEquals(1, evals.getTotalNumberOfResults());
		assertEquals(eval1, evals.getResults().get(0));
	}
	
	@Test
	public void testSubmissionRoundTrip() throws SynapseException, NotFoundException, InterruptedException, IOException {
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		String entityId = fileEntity.getId();
		String entityEtag = fileEntity.getEtag();
		String entityFileHandleId = fileEntity.getDataFileHandleId();
		assertNotNull(entityId);
		
		int initialCount = synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size();
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub1.getId());
		submissionsToDelete.add(sub1.getId());
		int newCount = initialCount + 1;
		assertEquals(newCount, synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size());
		
		// read
		Submission clone = synapse.getSubmission(sub1.getId());
		assertNotNull(clone.getEntityBundleJSON());
		sub1.setEntityBundleJSON(clone.getEntityBundleJSON());
		assertEquals(sub1, clone);
		SubmissionStatus status = synapse.getSubmissionStatus(sub1.getId());
		assertNotNull(status);
		assertEquals(sub1.getId(), status.getId());
		assertEquals(sub1.getEntityId(), status.getEntityId());
		assertEquals(sub1.getVersionNumber(), status.getVersionNumber());
		assertEquals(SubmissionStatusEnum.RECEIVED, status.getStatus());
		
		File target = File.createTempFile("test", null);
		target.deleteOnExit();
		adminSynapse.downloadFromSubmission(sub1.getId(), entityFileHandleId, target);
		String expectedMD5 = MD5ChecksumHelper.getMD5Checksum(this.dataSourceFile);
		String actualMD5 = MD5ChecksumHelper.getMD5Checksum(target);
		assertEquals(expectedMD5, actualMD5);

		// update
		Thread.sleep(1L);
		
		StringAnnotation sa = new StringAnnotation();
		sa.setIsPrivate(true);
		sa.setKey("foo");
		sa.setValue("bar");
		List<StringAnnotation> stringAnnos = new ArrayList<StringAnnotation>();
		stringAnnos.add(sa);
		Annotations annos = new Annotations();
		annos.setStringAnnos(stringAnnos);
		
		status.setScore(0.5);
		status.setStatus(SubmissionStatusEnum.SCORED);
		status.setReport("Lorem ipsum");
		status.setAnnotations(annos);
		status.setCanCancel(true);
		
		SubmissionStatus statusClone = synapse.updateSubmissionStatus(status);
		assertFalse(status.getModifiedOn().equals(statusClone.getModifiedOn()), "Modified date was not updated");
		status.setModifiedOn(statusClone.getModifiedOn());
		assertFalse(status.getEtag().equals(statusClone.getEtag()), "Etag was not updated");
		
		status.setEtag(statusClone.getEtag());
		status.setStatusVersion(statusClone.getStatusVersion());
		status.getAnnotations().setObjectId(sub1.getId());
		status.getAnnotations().setScopeId(sub1.getEvaluationId());
		
		assertEquals(status, statusClone);
		assertEquals(newCount, synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size());
		
		status = statusClone; // 'status' is, once again, the current version
		SubmissionStatusBatch batch = new SubmissionStatusBatch();
		List<SubmissionStatus> statuses = new ArrayList<SubmissionStatus>();
		statuses.add(status);
		batch.setStatuses(statuses);
		batch.setIsFirstBatch(true);
		batch.setIsLastBatch(true);
		BatchUploadResponse batchUpdateResponse = synapse.updateSubmissionStatusBatch(eval1.getId(), batch);
		// after last batch there's no 'next batch' token
		assertNull(batchUpdateResponse.getNextUploadToken());

		synapse.requestToCancelSubmission(sub1.getId());
		status = synapse.getSubmissionStatus(sub1.getId());
		assertTrue(status.getCancelRequested());
		
		// delete
		synapse.deleteSubmission(sub1.getId());
		
		assertThrows(SynapseException.class, () -> {
			synapse.deleteSubmission(sub1.getId());
		});
		
		assertEquals(initialCount, synapse.getAllSubmissions(eval1.getId(), 100, 0).getResults().size());
	}
	
	@Test
	public void testSubmissionWithAnnotationsV2() throws SynapseException, NotFoundException, InterruptedException, IOException {
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		String entityId = fileEntity.getId();
		String entityEtag = fileEntity.getEtag();
		
		assertNotNull(entityId);
		
		int initialCount = synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size();
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag, MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub1.getId());
		submissionsToDelete.add(sub1.getId());
		int newCount = initialCount + 1;
		assertEquals(newCount, synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size());
		
		SubmissionStatus status = synapse.getSubmissionStatus(sub1.getId());
				
		org.sagebionetworks.repo.model.annotation.v2.Annotations annotations = AnnotationsV2Utils.emptyAnnotations();
		
		AnnotationsValue bar = new AnnotationsValue();
		
		bar.setType(AnnotationsValueType.STRING);
		bar.setValue(Collections.singletonList("bar"));
		
		annotations.getAnnotations().put("foo", bar);
		
		status.setScore(0.5);
		status.setStatus(SubmissionStatusEnum.SCORED);
		status.setReport("Lorem ipsum");
		status.setSubmissionAnnotations(annotations);
		
		SubmissionStatus statusClone = synapse.updateSubmissionStatus(status);
		
		assertFalse(status.getModifiedOn().equals(statusClone.getModifiedOn()), "Modified date was not updated");
		status.setModifiedOn(statusClone.getModifiedOn());
		assertFalse(status.getEtag().equals(statusClone.getEtag()), "Etag was not updated");
		
		status.setEtag(statusClone.getEtag());
		status.setStatusVersion(statusClone.getStatusVersion());
		
		assertNull(statusClone.getAnnotations());
		assertNotNull(statusClone.getSubmissionAnnotations());
		
		annotations.setId(eval1.getId());
		annotations.setEtag(statusClone.getEtag());
		
		// delete
		synapse.deleteSubmission(sub1.getId());
		
		assertThrows(SynapseException.class, () -> {
			synapse.deleteSubmission(sub1.getId());
		});
		
		assertEquals(initialCount, synapse.getAllSubmissions(eval1.getId(), 100, 0).getResults().size());
	}
	
	private static SubmissionQuota createSubmissionQuota() {
		SubmissionQuota quota = new SubmissionQuota();
		quota.setFirstRoundStart(new Date(System.currentTimeMillis()));
		quota.setNumberOfRounds(1L);
		quota.setRoundDurationMillis(60*1000L); // 60 seconds
		quota.setSubmissionLimit(1L);
		return quota;
	}
	
	private Team createParticipantTeam() throws SynapseException {
		Team myTeam = new Team();
		myTeam.setCanPublicJoin(true);
		myTeam.setName("registered Team");
		myTeam = synapse.createTeam(myTeam);
		this.teamsToDelete.add(myTeam.getId());
		ChallengeTeam challengeTeam = new ChallengeTeam();
		challengeTeam.setChallengeId(challenge.getId());
		challengeTeam.setTeamId(myTeam.getId());
		// this is the actual Team registration step
		challengeTeam = synapse.createChallengeTeam(challengeTeam);
		return myTeam;
	}
	
	@Test
	public void testTeamSubmissionRoundTrip() throws SynapseException, NotFoundException, InterruptedException, IOException {
		SubmissionQuota quota = createSubmissionQuota();
		eval1.setQuota(quota);
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		
		Evaluation evaluationClone = synapse.getEvaluation(eval1.getId());
		assertEquals(quota, evaluationClone.getQuota());
		
		String entityId = fileEntity.getId();
		String entityEtag = fileEntity.getEtag();
		assertNotNull(entityId);
		
		int initialCount = synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size();
		
		// let's register for the challenge!
		Team myTeam = createParticipantTeam();
			
		String expectedUserId = synapse.getMyProfile().getOwnerId();
		// am I eligible to submit?
		TeamSubmissionEligibility tse = synapse.getTeamSubmissionEligibility(eval1.getId(), myTeam.getId());
		assertEquals(eval1.getId(), tse.getEvaluationId());
		assertEquals(myTeam.getId(), tse.getTeamId());
		SubmissionEligibility teamEligibility = tse.getTeamEligibility();
		assertTrue(teamEligibility.getIsEligible());
		assertFalse(teamEligibility.getIsQuotaFilled());
		assertTrue(teamEligibility.getIsRegistered());
		List<MemberSubmissionEligibility> mseList = tse.getMembersEligibility();
		assertEquals(1, mseList.size());
		MemberSubmissionEligibility mse = mseList.get(0);
		assertFalse(mse.getHasConflictingSubmission());
		assertTrue(mse.getIsEligible());
		assertFalse(mse.getIsQuotaFilled());
		assertTrue(mse.getIsRegistered());
		assertEquals(expectedUserId, mse.getPrincipalId().toString());
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1.setTeamId(myTeam.getId());
		long submissionEligibilityHash = tse.getEligibilityStateHash();
		sub1 = synapse.createTeamSubmission(sub1, entityEtag, ""+submissionEligibilityHash,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub1.getId());
		submissionsToDelete.add(sub1.getId());
		int newCount = initialCount + 1;
		assertEquals(newCount, synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size());
		
		Submission clone = synapse.getSubmission(sub1.getId());
		assertEquals(myTeam.getId(), clone.getTeamId());
		assertEquals(1, clone.getContributors().size());
		SubmissionContributor sb = clone.getContributors().iterator().next();
		assertEquals(expectedUserId, sb.getPrincipalId());
		assertNotNull(sb.getCreatedOn());

		// an admin can add my colleague as a contributor
		SubmissionContributor added = new SubmissionContributor();
		added.setPrincipalId(""+user2ToDelete);
		SubmissionContributor created = adminSynapse.addSubmissionContributor(clone.getId(), added);
		assertEquals(""+user2ToDelete, created.getPrincipalId());
		assertNotNull(created.getCreatedOn());
	}
	
	@Test
	public void testTeamSubmissionRoundTripWithNotification() throws Exception {
		SubmissionQuota quota = createSubmissionQuota();
		eval1.setQuota(quota);
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		
		Evaluation evaluationClone = synapse.getEvaluation(eval1.getId());
		assertEquals(quota, evaluationClone.getQuota());
		
		String entityId = fileEntity.getId();
		String entityEtag = fileEntity.getEtag();
		
		// let's register for the challenge!
		Team myTeam = createParticipantTeam();
		
		// I want my friend to join my team
		// first, he must register for the challenge
		UserProfile contributorProfile = synapseTwo.getMyProfile();
		synapseTwo.addTeamMember(participantTeam.getId(), contributorProfile.getOwnerId(), null, null);
		// then he has to join my team
		synapseTwo.addTeamMember(myTeam.getId(), contributorProfile.getOwnerId(), null, null);
				
		List<String> contributorEmails = contributorProfile.getEmails();
		assertEquals(1, contributorEmails.size());
		String contributorEmail = contributorEmails.get(0);
		String contributorNotification = EmailValidationUtil.getBucketKeyForEmail(contributorEmail);
		// make sure there is no notification before the submission is created
		if (EmailValidationUtil.doesFileExist(contributorNotification, 2000L))
			EmailValidationUtil.deleteFile(contributorNotification);

		TeamSubmissionEligibility tse = synapse.getTeamSubmissionEligibility(eval1.getId(), myTeam.getId());
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1.setTeamId(myTeam.getId());
		SubmissionContributor contributor = new SubmissionContributor();
		contributor.setPrincipalId(contributorProfile.getOwnerId());
		sub1.setContributors(Collections.singleton(contributor));
		long submissionEligibilityHash = tse.getEligibilityStateHash();
		sub1 = synapse.createTeamSubmission(sub1, entityEtag, ""+submissionEligibilityHash,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		submissionsToDelete.add(sub1.getId());

		// contributor should get notification
		assertTrue(EmailValidationUtil.doesFileExist(contributorNotification, 60000L));
	}

	@Test
	public void testSubmissionEntityBundle() throws SynapseException, NotFoundException, InterruptedException, JSONObjectAdapterException {
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		String entityId = project.getId();
		String entityEtag = project.getEtag();
		assertNotNull(entityId);
		entitiesToDelete.add(entityId);
		
		int initialCount = synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size();
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub1.getId());
		submissionsToDelete.add(sub1.getId());
		int newCount = initialCount + 1;
		assertEquals(newCount, synapse.getAllSubmissions(eval1.getId(), 0, 100).getResults().size());
		
		// read
		sub1 = synapse.getSubmission(sub1.getId());
		
		// verify EntityBundle
		EntityBundleRequest bundleV2Request = new EntityBundleRequest();
		bundleV2Request.setIncludeEntity(true);
		bundleV2Request.setIncludeAnnotations(true);
		bundleV2Request.setIncludeFileHandles(true);
		EntityBundle bundle = synapse.getEntityBundleV2(entityId, bundleV2Request);
		EntityBundle sumbissionJSONStringBundle = new EntityBundle();
		JSONObjectAdapter joa = new JSONObjectAdapterImpl();
		sumbissionJSONStringBundle.initializeFromJSONObject(joa.createNew(sub1.getEntityBundleJSON()));
		// we don't care if etags have changed
		sumbissionJSONStringBundle.getEntity().setEtag(null);
		sumbissionJSONStringBundle.getAnnotations().setEtag(null);
		bundle.getEntity().setEtag(null);
		bundle.getAnnotations().setEtag(null);
		assertEquals(bundle.getEntity(), sumbissionJSONStringBundle.getEntity());
		assertEquals(bundle.getAnnotations(), sumbissionJSONStringBundle.getAnnotations());
		assertEquals(bundle.getFileHandles(), sumbissionJSONStringBundle.getFileHandles());
		
		// delete
		synapse.deleteSubmission(sub1.getId());
		
		assertThrows(SynapseException.class, () -> {
			synapse.deleteSubmission(sub1.getId());
		});
		
		assertEquals(initialCount, synapse.getAllSubmissions(eval1.getId(), 100, 0).getResults().size());
	}
	
	@Test
	public void testSubmissionsPaginated() throws SynapseException {
		// create objects
		eval1 = synapse.createEvaluation(eval1);
		assertNotNull(eval1.getId());
		evaluationsToDelete.add(eval1.getId());
		eval2 = synapse.createEvaluation(eval2);
		assertNotNull(eval2.getId());
		evaluationsToDelete.add(eval2.getId());
		
		String entityId1 = project.getId();
		String entityEtag1 = project.getEtag();
		assertNotNull(entityId1);
		entitiesToDelete.add(entityId1);
		String entityId2 = dataset.getId();
		String entityEtag2 = dataset.getEtag();
		assertNotNull(entityId2);
		entitiesToDelete.add(entityId2);
		
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId1);
		sub1.setVersionNumber(1L);
		sub1.setUserId(userName);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag1,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub1.getId());
		submissionsToDelete.add(sub1.getId());		
		sub2.setEvaluationId(eval1.getId());
		sub2.setEntityId(entityId2);
		sub2.setVersionNumber(1L);
		sub2.setUserId(userName);
		sub2 = synapse.createIndividualSubmission(sub2, entityEtag2,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub2.getId());
		submissionsToDelete.add(sub2.getId());

		// paginated submissions, statuses, and bundles
		PaginatedResults<Submission> subs;
		PaginatedResults<SubmissionStatus> subStatuses;
		PaginatedResults<SubmissionBundle> subBundles;
		
		subs = synapse.getAllSubmissions(eval1.getId(), 0, 10);
		subStatuses = synapse.getAllSubmissionStatuses(eval1.getId(), 0, 10);
		subBundles = synapse.getAllSubmissionBundles(eval1.getId(), 0, 10);		
		assertEquals(2, subs.getTotalNumberOfResults());
		assertEquals(2, subStatuses.getTotalNumberOfResults());
		assertEquals(2, subBundles.getTotalNumberOfResults());
		assertEquals(2, subs.getResults().size());
		for (Submission s : subs.getResults()) {
			assertTrue(s.equals(sub1) || s.equals(sub2), "Unknown Submission returned: " + s.toString());
		}
		assertEquals(2, subStatuses.getResults().size());
		for (SubmissionStatus status : subStatuses.getResults()) {
			assertTrue(status.getId().equals(sub1.getId()) || status.getId().equals(sub2.getId()), "Unknown SubmissionStatus returned: " + status.toString());
		}
		assertEquals(2, subBundles.getResults().size());
		for (SubmissionBundle bundle : subBundles.getResults()) {
			Submission sub = bundle.getSubmission();
			SubmissionStatus status = bundle.getSubmissionStatus();
			assertTrue(sub.equals(sub1) || sub.equals(sub2), "Unknown Submission returned: " + bundle.toString());
			assertTrue(sub.getId().equals(status.getId()), "SubmissionBundle contents do not match: " + bundle.toString());
		}
				
		subs = synapse.getAllSubmissionsByStatus(eval1.getId(), SubmissionStatusEnum.RECEIVED, 0, 10);
		subStatuses = synapse.getAllSubmissionStatusesByStatus(eval1.getId(), SubmissionStatusEnum.RECEIVED, 0, 10);
		subBundles = synapse.getAllSubmissionBundlesByStatus(eval1.getId(), SubmissionStatusEnum.RECEIVED, 0, 10);
		assertEquals(2, subs.getTotalNumberOfResults());
		assertEquals(2, subBundles.getTotalNumberOfResults());
		assertEquals(2, subs.getResults().size());
		for (Submission s : subs.getResults()) {
			assertTrue(s.equals(sub1) || s.equals(sub2), "Unknown Submission returned: " + s.toString());
		}
		assertEquals(2, subStatuses.getResults().size());
		for (SubmissionStatus status : subStatuses.getResults()) {
			assertTrue(status.getId().equals(sub1.getId()) || status.getId().equals(sub2.getId()), "Unknown SubmissionStatus returned: " + status.toString());
		}
		assertEquals(2, subBundles.getResults().size());
		for (SubmissionBundle bundle : subBundles.getResults()) {
			Submission sub = bundle.getSubmission();
			SubmissionStatus status = bundle.getSubmissionStatus();
			assertTrue(sub.equals(sub1) || sub.equals(sub2), "Unknown Submission returned: " + bundle.toString());
			assertTrue(sub.getId().equals(status.getId()), "SubmissionBundle contents do not match: " + bundle.toString());
		}
		
		// verify url in PaginatedResults object contains eval ID (PLFM-1774)
		subs = synapse.getAllSubmissionsByStatus(eval1.getId(), SubmissionStatusEnum.RECEIVED, 0, 1);
		assertEquals(2, subs.getTotalNumberOfResults());
		assertEquals(1, subs.getResults().size());
		
		subs = synapse.getAllSubmissionsByStatus(eval1.getId(), SubmissionStatusEnum.RECEIVED, 0, 10);
		subBundles = synapse.getAllSubmissionBundlesByStatus(eval1.getId(), SubmissionStatusEnum.RECEIVED, 0, 10);
		assertEquals(2, subs.getTotalNumberOfResults());
		assertEquals(2, subBundles.getTotalNumberOfResults());
		assertEquals(2, subs.getResults().size());
		for (Submission s : subs.getResults()) {
			assertTrue(s.equals(sub1) || s.equals(sub2), "Unknown Submission returned: " + s.toString());
		}
		
		subs = synapse.getAllSubmissionsByStatus(eval1.getId(), SubmissionStatusEnum.SCORED, 0, 10);
		subBundles = synapse.getAllSubmissionBundlesByStatus(eval1.getId(), SubmissionStatusEnum.SCORED, 0, 10);
		assertEquals(0, subs.getTotalNumberOfResults());
		assertEquals(0, subBundles.getTotalNumberOfResults());
		assertEquals(0, subs.getResults().size());
		assertEquals(0, subBundles.getResults().size());
		
		subs = synapse.getAllSubmissions(eval2.getId(), 0, 10);
		subBundles = synapse.getAllSubmissionBundles(eval2.getId(), 0, 10);
		assertEquals(0, subs.getTotalNumberOfResults());
		assertEquals(0, subBundles.getTotalNumberOfResults());
		assertEquals(0, subs.getResults().size());
		assertEquals(0, subBundles.getResults().size());
	}
	
	@Test
	public void testGetMySubmissions() throws SynapseException {
		// create objects
		eval1 = synapse.createEvaluation(eval1);
		assertNotNull(eval1.getId());
		evaluationsToDelete.add(eval1.getId());

		// open the evaluation for user 2 to join
		Set<ACCESS_TYPE> accessSet = new HashSet<ACCESS_TYPE>(12);
		accessSet.add(ACCESS_TYPE.SUBMIT);
		accessSet.add(ACCESS_TYPE.READ);
		ResourceAccess ra = new ResourceAccess();
		ra.setAccessType(accessSet);
		String user2Id = synapseTwo.getMyProfile().getOwnerId();
		ra.setPrincipalId(Long.parseLong(user2Id));
		AccessControlList acl = synapse.getEvaluationAcl(eval1.getId());
		acl.getResourceAccess().add(ra);
		acl = synapse.updateEvaluationAcl(acl);
		assertNotNull(acl);

		String entityId1 = project.getId();
		String entityEtag1 = project.getEtag();
		assertNotNull(entityId1);
		entitiesToDelete.add(entityId1);
		String entityId2 = projectTwo.getId();
		String entityEtag2 = projectTwo.getEtag();
		assertNotNull(entityId2);
		entitiesToDelete.add(entityId2);
		
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId1);
		sub1.setVersionNumber(1L);
		sub1.setUserId(userName);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag1,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub1.getId());
		submissionsToDelete.add(sub1.getId());

		// synapseTwo must join the challenge
		synapseTwo.addTeamMember(participantTeam.getId(), ""+user2ToDelete, MOCK_TEAM_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		sub2.setEvaluationId(eval1.getId());
		sub2.setEntityId(projectTwo.getId());
		sub2.setVersionNumber(1L);
		sub2.setUserId(userName);
		sub2 = synapseTwo.createIndividualSubmission(sub2, entityEtag2,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		assertNotNull(sub2.getId());
		submissionsToDelete.add(sub2.getId());
		
		// paginated submissions
		PaginatedResults<Submission> subs = synapse.getMySubmissions(eval1.getId(), 0, 10);
		assertEquals(1, subs.getTotalNumberOfResults());
		for (Submission s : subs.getResults())
			assertTrue(s.equals(sub1), "Unknown Submission returned: " + s.toString());

	}
	
	@Test
	public void testGetFileTemporaryUrlForSubmissionFileHandle() throws Exception {
		// create Objects
		eval1 = synapse.createEvaluation(eval1);
		assertNotNull(eval1.getId());
		evaluationsToDelete.add(eval1.getId());
		
		FileEntity file = createTestFileEntity(project);
		
		// create Submission
		String entityId = file.getId();
		String entityEtag = file.getEtag();
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		submissionsToDelete.add(sub1.getId());

		// get file URL
		String expected = synapse.getFileEntityTemporaryUrlForCurrentVersion(file.getId()).toString();
		String actual = synapse.getFileTemporaryUrlForSubmissionFileHandle(sub1.getId(), fileHandle.getId()).toString();
		
		// trim time-sensitive params from URL (PLFM-2019)
		String timeSensitiveParameterStart = "&X-Amz-Date";
		expected = expected.substring(0, expected.indexOf(timeSensitiveParameterStart));
		actual = actual.substring(0, actual.indexOf(timeSensitiveParameterStart));
		
		assertEquals(expected, actual);
	}

	private FileEntity createTestFileEntity(Entity parent) throws SynapseException, FileNotFoundException, IOException {
		// create a FileHandle
		URL url = IT054FileEntityTest.class.getClassLoader().getResource("images/"+FILE_NAME);
		File imageFile = new File(url.getFile().replaceAll("%20", " "));
		assertNotNull(imageFile);
		assertTrue(imageFile.exists());
		fileHandle = synapse.multipartUpload(imageFile, null, false, false);
		
		// create a FileEntity
		FileEntity file = new FileEntity();
		file.setName("IT520SynapseJavaClientEvaluationTest.testGetFileTemporaryUrlForSubmissionFileHandle");
		file.setParentId(project.getId());
		file.setDataFileHandleId(fileHandle.getId());
		file = synapse.createEntity(file);
		assertNotNull(file);
		entitiesToDelete.add(file.getId());
		return file;
	}

	@Test
	public void testAclRoundtrip() throws Exception {

		// Create ACL
		eval1 = synapse.createEvaluation(eval1);
		assertNotNull(eval1);
		final String evalId = eval1.getId();
		assertNotNull(evalId);
		evaluationsToDelete.add(evalId);

		// Get ACL
		AccessControlList acl = synapse.getEvaluationAcl(evalId);
		assertNotNull(acl);
		assertEquals(evalId, acl.getId());

		// Get Permissions
		UserEvaluationPermissions uep1 = synapse.getUserEvaluationPermissions(evalId);
		assertNotNull(uep1);
		assertTrue(uep1.getCanChangePermissions());
		assertTrue(uep1.getCanDelete());
		assertTrue(uep1.getCanEdit());
		assertFalse(uep1.getCanPublicRead());
		assertTrue(uep1.getCanView());
		UserEvaluationPermissions uep2 = synapseTwo.getUserEvaluationPermissions(evalId);
		assertNotNull(uep2);
		assertFalse(uep2.getCanChangePermissions());
		assertFalse(uep2.getCanDelete());
		assertFalse(uep2.getCanEdit());
		assertFalse(uep2.getCanPublicRead());
		assertFalse(uep2.getCanView());

		// Update ACL
		Set<ACCESS_TYPE> accessSet = new HashSet<ACCESS_TYPE>(12);
		accessSet.add(ACCESS_TYPE.CHANGE_PERMISSIONS);
		accessSet.add(ACCESS_TYPE.DELETE);
		ResourceAccess ra = new ResourceAccess();
		ra.setAccessType(accessSet);
		Long user2Id = Long.parseLong(JSONWebTokenHelper.getSubjectFromJWTAccessToken(
				synapseTwo.getAccessToken()));
		ra.setPrincipalId(user2Id);
		Set<ResourceAccess> raSet = new HashSet<ResourceAccess>();
		raSet.add(ra);
		acl.setResourceAccess(raSet);
		acl = synapse.updateEvaluationAcl(acl);
		assertNotNull(acl);
		assertEquals(evalId, acl.getId());

		// Check again for updated permissions
		uep1 = synapse.getUserEvaluationPermissions(evalId);
		assertNotNull(uep1);
		uep2 = synapseTwo.getUserEvaluationPermissions(evalId);
		assertNotNull(uep2);
		assertTrue(uep2.getCanChangePermissions());
		assertTrue(uep2.getCanDelete());
		assertFalse(uep2.getCanEdit());
		assertFalse(uep2.getCanPublicRead());
		assertFalse(uep2.getCanView());
	}
	
	@Test
	public void testAnnotationsQuery() throws SynapseException, InterruptedException, JSONObjectAdapterException {
		// set up objects
		eval1 = synapse.createEvaluation(eval1);
		evaluationsToDelete.add(eval1.getId());
		String entityId = project.getId();
		String entityEtag = project.getEtag();
		entitiesToDelete.add(entityId);
		
		// create
		sub1.setEvaluationId(eval1.getId());
		sub1.setEntityId(entityId);
		sub1 = synapse.createIndividualSubmission(sub1, entityEtag,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		submissionsToDelete.add(sub1.getId());
		sub2.setEvaluationId(eval1.getId());
		sub2.setEntityId(entityId);
		sub2 = synapse.createIndividualSubmission(sub2, entityEtag,
				MOCK_CHALLENGE_ENDPOINT, MOCK_NOTIFICATION_UNSUB_ENDPOINT);
		submissionsToDelete.add(sub2.getId());
		
		String doubleHeader = "DOUBLE";
		double doubleValue = Double.NaN;
		// add annotations
		BatchUploadResponse response = null;
		{
			SubmissionStatus status = synapse.getSubmissionStatus(sub1.getId());
			Thread.sleep(1L);		
			StringAnnotation sa = new StringAnnotation();
			sa.setIsPrivate(true);
			sa.setKey("foo");
			sa.setValue("bar");
			List<StringAnnotation> stringAnnos = new ArrayList<StringAnnotation>();
			stringAnnos.add(sa);
			Annotations annos = new Annotations();
			annos.setStringAnnos(stringAnnos);		
			
			DoubleAnnotation da = new DoubleAnnotation();
			da.setIsPrivate(true);
			da.setKey(doubleHeader);
			da.setValue(doubleValue);
			List<DoubleAnnotation> doubleAnnos = new ArrayList<DoubleAnnotation>();
			doubleAnnos.add(da);
			annos.setDoubleAnnos(doubleAnnos);					
			
			status.setScore(0.5);
			status.setStatus(SubmissionStatusEnum.SCORED);
			status.setReport("Lorem ipsum");
			status.setAnnotations(annos);
			SubmissionStatusBatch batch = new SubmissionStatusBatch();
			batch.setStatuses(Collections.singletonList(status));
			batch.setIsFirstBatch(true);
			batch.setIsLastBatch(false);
			response = synapse.updateSubmissionStatusBatch(eval1.getId(), batch);
		}
		{
			SubmissionStatus status = synapse.getSubmissionStatus(sub2.getId());
			Thread.sleep(1L);		
			StringAnnotation sa = new StringAnnotation();
			sa.setIsPrivate(true);
			sa.setKey("foo");
			sa.setValue("bar");
			List<StringAnnotation> stringAnnos = new ArrayList<StringAnnotation>();
			stringAnnos.add(sa);
			Annotations annos = new Annotations();
			annos.setStringAnnos(stringAnnos);		
			
			DoubleAnnotation da = new DoubleAnnotation();
			da.setIsPrivate(true);
			da.setKey(doubleHeader);
			da.setValue(doubleValue);
			List<DoubleAnnotation> doubleAnnos = new ArrayList<DoubleAnnotation>();
			doubleAnnos.add(da);
			annos.setDoubleAnnos(doubleAnnos);					
			
			status.setScore(0.5);
			status.setStatus(SubmissionStatusEnum.SCORED);
			status.setReport("Lorem ipsum");
			status.setAnnotations(annos);
			SubmissionStatusBatch batch = new SubmissionStatusBatch();
			batch.setStatuses(Collections.singletonList(status));
			batch.setIsFirstBatch(false);
			batch.setIsLastBatch(true);
			batch.setBatchToken(response.getNextUploadToken());
			response = synapse.updateSubmissionStatusBatch(eval1.getId(), batch);
			assertNull(response.getNextUploadToken());
		}

	
		// query for the object
		// we must wait for the annotations to be populated by a worker
		String queryString = "SELECT * FROM evaluation_" + eval1.getId() + " WHERE foo == \"bar\"";
		QueryTableResults results = synapse.queryEvaluation(queryString);
		assertNotNull(results);
		long start = System.currentTimeMillis();
		while (results.getTotalNumberOfResults() < 2) {
			long elapsed = System.currentTimeMillis() - start;
			assertTrue(elapsed < RDS_WORKER_TIMEOUT, "Timed out waiting for annotations to be published for query: " + queryString);
			System.out.println("Waiting for annotations to be published... " + elapsed + "ms");		
			Thread.sleep(1000);
			results = synapse.queryEvaluation(queryString);
		}
		
		// verify the results
		List<String> headers = results.getHeaders();
		List<org.sagebionetworks.repo.model.query.Row> rows = results.getRows();
		assertEquals(2, rows.size());
		assertTrue(headers.contains("foo"));
		int index = headers.indexOf(DBOConstants.PARAM_ANNOTATION_OBJECT_ID);
		assertTrue(rows.get(0).getValues().get(index).contains(sub1.getId()));
		assertTrue(rows.get(1).getValues().get(index).contains(sub2.getId()));
		
		int nanColumnIndex = headers.indexOf(doubleHeader);
		assertTrue(rows.get(0).getValues().get(nanColumnIndex).contains(""+doubleValue),
				"Expected NaN but found: "+rows.get(0).getValues().get(nanColumnIndex).toString());
		assertTrue(rows.get(1).getValues().get(nanColumnIndex).contains(""+doubleValue),
				"Expected NaN but found: "+rows.get(1).getValues().get(nanColumnIndex).toString());
		
		
		// now check that if you delete the submission it stops appearing in the query
		adminSynapse.deleteSubmission(sub1.getId());
		submissionsToDelete.remove(sub1.getId());
		// rerun the query.  We should get just one result (for sub2)
		// we must wait for the annotations to be populated by a worker
		results = synapse.queryEvaluation(queryString);
		assertNotNull(results);
		start = System.currentTimeMillis();
		while (results.getTotalNumberOfResults() > 1) {
			long elapsed = System.currentTimeMillis() - start;
			assertTrue(elapsed < RDS_WORKER_TIMEOUT, "Timed out waiting for annotations to be deleted for query: " + queryString);
			System.out.println("Waiting for annotations to be deleted... " + elapsed + "ms");
			Thread.sleep(1000);
			results = synapse.queryEvaluation(queryString);
		}
		assertEquals(1, results.getRows().size());
		
	}
}