package io.onedev.server.manager.impl;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Preconditions;
import io.onedev.server.manager.IssueAuthorizationManager;
import io.onedev.server.model.Issue;
import io.onedev.server.model.IssueAuthorization;
import io.onedev.server.model.User;
import io.onedev.server.persistence.annotation.Transactional;
import io.onedev.server.persistence.dao.BaseEntityManager;
import io.onedev.server.persistence.dao.Dao;

@Singleton
public class DefaultIssueAuthorizationManager extends BaseEntityManager<IssueAuthorization> 
		implements IssueAuthorizationManager {

	@Inject
	public DefaultIssueAuthorizationManager(Dao dao) {
		super(dao);
	}
	
	@Override
	public List<IssueAuthorization> query() {
		return query(true);
	}

	@Override
	public int count() {
		return count(true);
	}

	@Transactional
	@Override
	public void authorize(Issue issue, User user) {
		boolean authorized = false;
		for (IssueAuthorization authorization: issue.getAuthorizations()) {
			if (authorization.getUser().equals(user)) {
				authorized = true;
				break;
			}
		}
		if (!authorized) {
			IssueAuthorization authorization = new IssueAuthorization();
			authorization.setIssue(issue);
			authorization.setUser(user);
			issue.getAuthorizations().add(authorization);
			create(authorization);
		}
	}

	@Transactional
	@Override
	public void create(IssueAuthorization authorization) {
		Preconditions.checkState(authorization.isNew());
		dao.persist(authorization);
	}

	@Transactional
	@Override
	public void update(IssueAuthorization authorization) {
		Preconditions.checkState(!authorization.isNew());
		dao.persist(authorization);
	}
	
}
