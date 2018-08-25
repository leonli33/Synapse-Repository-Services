package org.sagebionetworks.repo.web.filter;

import static org.sagebionetworks.repo.web.filter.ThrottleUtils.isMigrationAdmin;

import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.AuthorizationUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public abstract class AbstractRequestThrottleFilter implements Filter {
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		String userId = request.getParameter(AuthorizationConstants.USER_ID_PARAM);
		long userIdLong = Long.parseLong(userId);
		if (AuthorizationUtils.isUserAnonymous(userIdLong) || isMigrationAdmin(userIdLong) ) {
			//do not throttle anonymous users nor the admin responsible for migration.
			chain.doFilter(request, response);
		} else {
			throttle(request, response, chain, userId);
		}
	}

	protected abstract void throttle(ServletRequest request, ServletResponse response, FilterChain chain, String userId) throws IOException, ServletException;

	@Override
	public void init(FilterConfig filterConfig) {
		//do nothing
	}

	@Override
	public void destroy() {
		//do nothing
	}
}
