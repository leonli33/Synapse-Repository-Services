package org.sagebionetworks.repo.manager.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.dao.FileHandleDao;
import org.sagebionetworks.repo.model.file.FileHandleAssociateType;
import org.sagebionetworks.repo.model.file.FileHandleAssociationProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@ExtendWith(MockitoExtension.class)
public class FileHandleAssociationManagerImplTest {

	@Mock
	FileHandleAssociationProvider mockProvider;

	@Mock
	FileHandleDao mockFileHandleDao;

	FileHandleAssociationManagerImpl fileHandleAssociationManager;

	@BeforeEach
	public void before() {

		HashMap<FileHandleAssociateType, FileHandleAssociationProvider> mockMap = new HashMap<FileHandleAssociateType, FileHandleAssociationProvider>();
		mockMap.put(FileHandleAssociateType.TableEntity, mockProvider);

		fileHandleAssociationManager = new FileHandleAssociationManagerImpl(mockFileHandleDao);
		fileHandleAssociationManager.setProviderMap(mockMap);
	}

	@Test
	public void testGetObjectTypeForAssociatedType() {
		when(mockProvider.getAuthorizationObjectTypeForAssociatedObjectType()).thenReturn(ObjectType.ENTITY);
		assertEquals(ObjectType.ENTITY,
				fileHandleAssociationManager.getAuthorizationObjectTypeForAssociatedObjectType(FileHandleAssociateType.TableEntity));
	}

	@Test
	public void testGetFileHandleIdsAssociatedWithObject() {
		Set<String> sample = Sets.newHashSet("1");
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(anyList(), anyString())).thenReturn(sample);
		assertEquals(sample, fileHandleAssociationManager.getFileHandleIdsAssociatedWithObject(Arrays.asList("1"), "456",
				FileHandleAssociateType.TableEntity));
	}
	
	@Test
	public void testGetFileHandleIdsAssociatedWithObjectIncludingPreviews() {
		// The first is a file handle, the second is its preview
		List<String> fileHandleIds = Arrays.asList("1", "2");
		
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(anyList(), anyString()))
			.thenReturn(ImmutableSet.of("1"));
		
		when(mockFileHandleDao.getFileHandleIdsWithPreviewIds(Arrays.asList("2"))).thenReturn(ImmutableMap.of("1", "2"));
		
		Set<String> result = fileHandleAssociationManager.getFileHandleIdsAssociatedWithObject(fileHandleIds, "123", FileHandleAssociateType.TableEntity);
		
		verify(mockProvider, times(2)).getFileHandleIdsDirectlyAssociatedWithObject(anyList(), anyString());
		verify(mockFileHandleDao, times(1)).getFileHandleIdsWithPreviewIds(Arrays.asList("2"));
		
		assertEquals(ImmutableSet.copyOf(fileHandleIds), result);
	}
	
	@Test
	public void testGetFileHandleIdsAssociatedWithObjectWithAnotherPreview() {
		// These are all previews, the second is not associated with the object
		List<String> fileHandleIds = Arrays.asList("1", "3");
		
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(eq(fileHandleIds), anyString())).thenReturn(Collections.emptySet());
		when(mockFileHandleDao.getFileHandleIdsWithPreviewIds(fileHandleIds)).thenReturn(ImmutableMap.of("2", "1", "4", "3"));
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(eq(Arrays.asList("2", "4")), anyString())).thenReturn(ImmutableSet.of("2"));
		
		Set<String> result = fileHandleAssociationManager.getFileHandleIdsAssociatedWithObject(fileHandleIds, "123", FileHandleAssociateType.TableEntity);
		
		verify(mockProvider, times(2)).getFileHandleIdsDirectlyAssociatedWithObject(anyList(), anyString());
		verify(mockFileHandleDao, times(1)).getFileHandleIdsWithPreviewIds(fileHandleIds);
		
		assertEquals(ImmutableSet.of("1"), result);
	}
	
	@Test
	public void testGetFileHandleIdsAssociatedWithObjectWithOnlyPreviews() {
		// These are all previews
		List<String> fileHandleIds = Arrays.asList("1", "3");
		
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(eq(fileHandleIds), anyString())).thenReturn(Collections.emptySet());
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(eq(Arrays.asList("2", "4")), anyString())).thenReturn(ImmutableSet.of("2", "4"));
		when(mockFileHandleDao.getFileHandleIdsWithPreviewIds(fileHandleIds)).thenReturn(ImmutableMap.of("2", "1", "4", "3"));
		
		Set<String> result = fileHandleAssociationManager.getFileHandleIdsAssociatedWithObject(fileHandleIds, "123", FileHandleAssociateType.TableEntity);
		
		verify(mockProvider, times(2)).getFileHandleIdsDirectlyAssociatedWithObject(anyList(), anyString());
		verify(mockFileHandleDao, times(1)).getFileHandleIdsWithPreviewIds(fileHandleIds);
		
		assertEquals(ImmutableSet.copyOf(fileHandleIds), result);
	}
	
	@Test
	public void testGetFileHandleIdsAssociatedWithObjectWithNoPreview() {
		// These are hanldes, no preview
		List<String> fileHandleIds = Arrays.asList("1", "2");
		
		when(mockProvider.getFileHandleIdsDirectlyAssociatedWithObject(eq(fileHandleIds), anyString())).thenReturn(ImmutableSet.of("1", "2"));
		
		Set<String> result = fileHandleAssociationManager.getFileHandleIdsAssociatedWithObject(fileHandleIds, "123", FileHandleAssociateType.TableEntity);
		
		verify(mockProvider, times(1)).getFileHandleIdsDirectlyAssociatedWithObject(anyList(), anyString());
		verify(mockFileHandleDao, times(0)).getFileHandleIdsWithPreviewIds(anyList());
		
		assertEquals(ImmutableSet.copyOf(fileHandleIds), result);
	}

}
