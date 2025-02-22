package org.sagebionetworks.repo.web.service.metadata;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.sagebionetworks.repo.manager.table.TableViewManager;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.EntityRef;
import org.sagebionetworks.repo.model.InvalidModelException;
import org.sagebionetworks.repo.model.NodeDAO;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.table.Dataset;
import org.sagebionetworks.repo.model.table.DatasetCollection;
import org.sagebionetworks.repo.model.table.ViewEntityType;
import org.sagebionetworks.repo.model.table.ViewScope;
import org.sagebionetworks.repo.model.table.ViewTypeMask;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetCollectionMetadataProvider extends ViewMetadataProvider<DatasetCollection> implements EntityValidator<DatasetCollection> {

	private NodeDAO nodeDao;
	
	@Autowired
	public DatasetCollectionMetadataProvider(TableViewManager viewManager, NodeDAO nodeDao) {
		super(viewManager);
		this.nodeDao = nodeDao;
	}

	@Override
	public void validateEntity(DatasetCollection entity, EntityEvent event)
			throws InvalidModelException, NotFoundException, DatastoreException, UnauthorizedException {
		if (entity.getItems() != null) {
			
			Set<Long> uniqueIds = new HashSet<>(entity.getItems().size());
			for(EntityRef item: entity.getItems()) {
				if(item.getEntityId() == null) {
					throw new IllegalArgumentException("Each dataset collection item must have a non-null entity ID.");
				}
				if(item.getVersionNumber() == null) {
					throw new IllegalArgumentException("Each dataset collection item must have a non-null version number");
				}
				if(!uniqueIds.add(KeyFactory.stringToKey(item.getEntityId()))) {
					throw new IllegalArgumentException("Each dataset collection item must have a unique entity ID.  Duplicate: "+item.getEntityId());
				}
			}
			// Only allow datasets
			List<EntityHeader> headers = nodeDao.getEntityHeader(entity.getItems().stream()
					.map(i -> KeyFactory.stringToKey(i.getEntityId())).collect(Collectors.toSet()));
			
			headers.stream()
				.filter(Predicate.not(h -> Dataset.class.getName().equals(h.getType())))
				.findFirst()
				.ifPresent(firstNonDataset -> {
					throw new IllegalArgumentException(String.format("Only dataset entities can be included in a dataset collection. %s is '%s'", firstNonDataset.getId(), firstNonDataset.getType()));	
				});
			
		}
		
	}

	@Override
	public ViewScope createViewScope(UserInfo userInfo, DatasetCollection view) {
		ViewScope scope = new ViewScope();
		scope.setViewEntityType(ViewEntityType.datasetcollection);
		if (view.getItems() != null) {
			scope.setScope(view.getItems().stream().map(EntityRef::getEntityId).collect(Collectors.toList()));
		}
		scope.setViewTypeMask(ViewTypeMask.Dataset.getMask());
		return scope;
	}

}
