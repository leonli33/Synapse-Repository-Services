package org.sagebionetworks.repo.web.controller;

import org.sagebionetworks.auth.BaseController;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.auth.Username;
import org.sagebionetworks.repo.model.principal.AliasCheckRequest;
import org.sagebionetworks.repo.model.principal.AliasCheckResponse;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.repo.web.UrlHelpers;
import org.sagebionetworks.repo.web.rest.doc.ControllerInfo;
import org.sagebionetworks.repo.web.service.ServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A <a href="http://en.wikipedia.org/wiki/Principal_%28computer_security%29">
 * Principal</a> in Synapse can be a User, Group, or a Team. This is a set of
 * services that provides the means to look-up principals by their various
 * attributes and also to test unique names such as USER_NAME, USER_EMAIL, or
 * TEAM_NAME are available for use.
 */
@Controller
@ControllerInfo(displayName = "Principal Services", path = "repo/v1")
@RequestMapping(UrlHelpers.REPO_PATH)
public class PrincipalController extends BaseController {

	@Autowired
	ServiceProvider serviceProvider;

	/**
	 * <p>
	 * This call is used to determine if an alias is currently available.
	 * </p>
	 * <p>A session token is not required for this call.</p>
	 * Each value of each <a
	 * href="${org.sagebionetworks.repo.model.principal.AliasType}"
	 * >AliasType</a> must have a unique string representation. While some
	 * AliasTypes allow white-space and punctuation, only letters and numbers
	 * contribute to the uniqueness of the alias. Also while an alias can have
	 * both upper and lower case letters, the uniqueness test is
	 * case-insensitive. Here are some examples:
	 * <ul>
	 * <li>'foo-bar', 'foo bar', and 'foo.bar' are all the same as 'foobar'</li>
	 * <li>'FooBar' and 'FOOBAR' are the same as 'foobar'</li>
	 * <li>'foo', 'foo1', and 'foo2' are each distinct</li>
	 * </ul>
	 * Note: This method will NOT reserve the passed alias. So it is possible
	 * that an alias, could be available during a pre-check, but then consumed
	 * before the caller has a chance to reserve it.
	 * 
	 * @param check
	 *            The request should include both the type and alias.
	 * @return
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = { UrlHelpers.PRINCIPAL_AVAILABLE }, method = RequestMethod.POST)
	public @ResponseBody
	AliasCheckResponse checkAlias(
			@RequestBody AliasCheckRequest check) {
		return serviceProvider.getPrincipalService().checkAlias(check);
	}
	
	/**
	 * This service sets the email used for user notifications, i.e. when a Synapse message is
	 * sent and if the user has elected to receive messages by email, then this is the email
	 * address at which the user will receive the message.  Note:  The given email address
	 * must already be established as being owned by the user.
	 * 
	 * @param userId
	 * @param email the email address to use for notifications
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { UrlHelpers.NOTIFICATION_EMAIL }, method = RequestMethod.PUT)
	void setNotificationEmail(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId,
			@RequestBody Username email
		) throws NotFoundException {
		serviceProvider.getPrincipalService().setNotificationEmail(userId, email.getEmail());
	}
	
	/**
	 * This service returns the email used for user notifications, i.e. when a Synapse message is
	 * sent and if the user has elected to receive messages by email, then this is the email
	 * address at which the user will receive the message.
	 * 
	 * @param userId
	 * @return the email address to use for notifications
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = { UrlHelpers.NOTIFICATION_EMAIL }, method = RequestMethod.GET)
	public @ResponseBody
	Username getNotificationEmail(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM) Long userId) throws NotFoundException {
		return serviceProvider.getPrincipalService().getNotificationEmail(userId);
	}

}
