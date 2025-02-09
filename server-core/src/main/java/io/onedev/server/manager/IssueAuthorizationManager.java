package io.onedev.server.manager;

import io.onedev.server.model.Issue;
import io.onedev.server.model.IssueAuthorization;
import io.onedev.server.model.User;
import io.onedev.server.persistence.dao.EntityManager;

public interface IssueAuthorizationManager extends EntityManager<IssueAuthorization> {

	void authorize(Issue issue, User user);

    void create(IssueAuthorization authorization);

	void update(IssueAuthorization authorization);
	
}