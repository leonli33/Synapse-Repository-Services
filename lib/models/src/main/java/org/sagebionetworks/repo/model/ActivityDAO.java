package org.sagebionetworks.repo.model;

import java.util.List;

import org.sagebionetworks.repo.model.message.ChangeType;
import org.sagebionetworks.repo.model.provenance.Activity;
import org.sagebionetworks.repo.web.NotFoundException;

public interface ActivityDAO extends MigratableDAO {

	/**
	 * @param dto
	 *            object to be created
	 * @param paramsSchema the schema of the parameters field
	 * @return the id of the newly created object
	 * @throws DatastoreException
	 * @throws InvalidModelException
	 */
	public <T extends Activity> T create(T dto) throws DatastoreException, InvalidModelException;

	/**
	 * Updates the object.
	 *
	 * @param dto
	 * @throws DatastoreException
	 */
	public <T extends Activity> T update(T activity) throws InvalidModelException,
			NotFoundException, ConflictingUpdateException, DatastoreException;

	/**
	 * Retrieves the object given its id
	 * 
	 * @param id
	 * @return
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	public Activity get(String id) throws DatastoreException, NotFoundException;
		
	/**
	 * delete the object given by the given ID
	 * 
	 * @param id
	 *            the id of the object to be deleted
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	public void delete(String id) throws DatastoreException, NotFoundException;

	/**
	 * 
	 * @return all IDs in the system
	 */
	List<String> getIds();

	/**
	 * Locks the activity and increments its eTag
	 * Note: You cannot call this method outside of a transaction.
	 * @param id
	 * @param eTag
	 * @return
	 * @throws NotFoundException
	 * @throws ConflictingUpdateException
	 * @throws DatastoreException
	 */
	public String lockActivityAndIncrementEtag(String id, String eTag, ChangeType changeType) throws NotFoundException, ConflictingUpdateException, DatastoreException; 

	/**
	 * @param id
	 * @return Returns true if the activity id exists in the database
	 */
	public boolean doesActivityExist(String id);
		
	/**
	 * @param id activity id
	 * @return Returns the list of EntityHeaders that were generated by 
	 */
	public List<Reference> getEntitiesGeneratedBy(String id);

}
