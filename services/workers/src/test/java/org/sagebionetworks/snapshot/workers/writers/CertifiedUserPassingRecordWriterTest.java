package org.sagebionetworks.snapshot.workers.writers;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.sagebionetworks.snapshot.workers.writers.CertifiedUserPassingRecordWriter.LIMIT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sagebionetworks.asynchronous.workers.sqs.MessageUtils;
import org.sagebionetworks.audit.dao.ObjectRecordDAO;
import org.sagebionetworks.audit.utils.ObjectRecordBuilderUtils;
import org.sagebionetworks.common.util.progress.ProgressCallback;
import org.sagebionetworks.reflection.model.PaginatedResults;
import org.sagebionetworks.repo.manager.CertifiedUserManager;
import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.audit.ObjectRecord;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.sagebionetworks.repo.model.message.ChangeType;
import org.sagebionetworks.repo.model.quiz.PassingRecord;

import com.amazonaws.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
public class CertifiedUserPassingRecordWriterTest {

	@Mock
	private CertifiedUserManager mockCertifiedUserManager;
	@Mock
	private UserManager mockUserManager;
	@Mock
	private ObjectRecordDAO mockObjectRecordDAO;
	@Mock
	private ProgressCallback mockCallback;
	
	@InjectMocks
	private CertifiedUserPassingRecordWriter writer;
	
	private UserInfo admin = new UserInfo(true);
	private Long userId = 123L;

	@BeforeEach
	public void before() {
		Mockito.when(mockUserManager.getUserInfo(BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId())).thenReturn(admin );
	}

	@Test
	public void deleteChangeMessage() throws IOException {
		Message message = MessageUtils.buildMessage(ChangeType.DELETE, "123", ObjectType.CERTIFIED_USER_PASSING_RECORD, "etag", System.currentTimeMillis());
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		writer.buildAndWriteRecords(mockCallback, Arrays.asList(changeMessage));
		verify(mockObjectRecordDAO, never()).saveBatch(anyList(), anyString());
	}

	@Test
	public void invalidObjectType() throws IOException {
		Message message = MessageUtils.buildMessage(ChangeType.CREATE, "123", ObjectType.ENTITY, "etag", System.currentTimeMillis());
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		
		assertThrows(IllegalArgumentException.class, () -> {			
			writer.buildAndWriteRecords(mockCallback, Arrays.asList(changeMessage));
		});
	}

	@Test
	public void emptyRecords() throws IOException {
		PaginatedResults<PassingRecord> results = new PaginatedResults<PassingRecord>();
		results.setTotalNumberOfResults(0);
		results.setResults(new ArrayList<PassingRecord>());
		Mockito.when(mockCertifiedUserManager.getPassingRecords(admin, userId, LIMIT , 0L)).thenReturn(results );

		Message message = MessageUtils.buildMessage(ChangeType.CREATE, "123", ObjectType.CERTIFIED_USER_PASSING_RECORD, "etag", System.currentTimeMillis());
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		writer.buildAndWriteRecords(mockCallback, Arrays.asList(changeMessage));

		verifyZeroInteractions(mockObjectRecordDAO);
	}

	@Test
	public void onePageOfRecords() throws IOException {
		PassingRecord passingRecord = new PassingRecord();
		long timestamp = System.currentTimeMillis();
		ObjectRecord record = ObjectRecordBuilderUtils.buildObjectRecord(passingRecord, timestamp);
		List<ObjectRecord> orList = new ArrayList<ObjectRecord>();
		orList.add(record);
		PaginatedResults<PassingRecord> pageOne = new PaginatedResults<PassingRecord>();
		pageOne.setTotalNumberOfResults(1);
		pageOne.setResults(Arrays.asList(passingRecord));
		Mockito.when(mockCertifiedUserManager.getPassingRecords(admin, userId, LIMIT , 0L)).thenReturn(pageOne);
		Mockito.when(mockCertifiedUserManager.getPassingRecords(admin, userId, LIMIT , 0L)).thenReturn(pageOne);

		Message message = MessageUtils.buildMessage(ChangeType.CREATE, "123", ObjectType.CERTIFIED_USER_PASSING_RECORD, "etag", timestamp);
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		writer.buildAndWriteRecords(mockCallback, Arrays.asList(changeMessage));

		verify(mockObjectRecordDAO).saveBatch(orList, record.getJsonClassName());
	}

	@Test
	public void twoPagesOfRecords() throws IOException {
		PassingRecord passingRecord = new PassingRecord();
		long timestamp = System.currentTimeMillis();
		ObjectRecord record = ObjectRecordBuilderUtils.buildObjectRecord(passingRecord, timestamp);
		PaginatedResults<PassingRecord> pageOne = new PaginatedResults<PassingRecord>();
		pageOne.setTotalNumberOfResults(11);
		pageOne.setResults(Arrays.asList(passingRecord));
		Mockito.when(mockCertifiedUserManager.getPassingRecords(admin, userId, LIMIT , 0L)).thenReturn(pageOne);
		Mockito.when(mockCertifiedUserManager.getPassingRecords(admin, userId, LIMIT , LIMIT)).thenReturn(pageOne);

		Message message = MessageUtils.buildMessage(ChangeType.CREATE, "123", ObjectType.CERTIFIED_USER_PASSING_RECORD, "etag", timestamp);
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		writer.buildAndWriteRecords(mockCallback, Arrays.asList(changeMessage));

		verify(mockObjectRecordDAO).saveBatch(Arrays.asList(record, record), record.getJsonClassName());
	}
}
