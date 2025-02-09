package io.onedev.server.manager;

import java.util.Collection;

import io.onedev.server.model.Issue;
import io.onedev.server.model.IssueField;
import io.onedev.server.persistence.dao.EntityManager;

public interface IssueFieldManager extends EntityManager<IssueField> {
	
	void saveFields(Issue issue);
	
	void onRenameUser(String oldName, String newName);

    void create(IssueField entity);

    void onRenameGroup(String oldName, String newName);
			
	void populateFields(Collection<Issue> issues);
	
}
