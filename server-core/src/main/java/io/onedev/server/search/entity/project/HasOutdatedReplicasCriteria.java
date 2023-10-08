package io.onedev.server.search.entity.project;

import io.onedev.server.OneDev;
import io.onedev.server.manager.ProjectManager;
import io.onedev.server.model.Project;
import io.onedev.server.util.criteria.Criteria;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;

public class HasOutdatedReplicasCriteria extends Criteria<Project> {

	private static final long serialVersionUID = 1L;

	@Override
	public Predicate getPredicate(CriteriaQuery<?> query, From<Project, Project> from, CriteriaBuilder builder) {
		ProjectManager projectManager = OneDev.getInstance(ProjectManager.class);
		return forManyValues(builder, from.get(Project.PROP_ID),
				projectManager.getIdsHasOutdatedReplicas(), projectManager.getIds());
	}

	@Override
	public boolean matches(Project project) {
		return OneDev.getInstance(ProjectManager.class).hasOutdatedReplicas(project.getId());
	}

	@Override
	public String toStringWithoutParens() {
		return ProjectQuery.getRuleName(ProjectQueryLexer.HasOutdatedReplicas);
	}

}
