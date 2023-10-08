package io.onedev.server.manager;

import io.onedev.server.model.Issue;
import io.onedev.server.model.Milestone;
import io.onedev.server.model.Project;
import io.onedev.server.persistence.dao.EntityManager;
import io.onedev.server.search.entity.EntityQuery;
import io.onedev.server.search.entity.EntitySort;
import io.onedev.server.util.*;
import io.onedev.server.util.criteria.Criteria;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedFieldResolution;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedFieldValue;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedFieldValuesResolution;
import io.onedev.server.web.component.issue.workflowreconcile.UndefinedStateResolution;

import javax.annotation.Nullable;
import javax.persistence.criteria.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IssueManager extends EntityManager<Issue> {
	
    @Nullable
    Issue find(Project project, long number);
    
    @Nullable
    Issue find(ProjectScopedNumber fqn);
    
    @Nullable
    Issue findByFQN(String fqn);

    @Nullable
    Issue findByUUID(String uuid);
    
	void open(Issue issue);
	
	void togglePin(Issue issue);
	
	Long getNextNumber(Project numberScope);
	
	void resetNextNumber(Project numberScope);
	
	List<Issue> queryPinned(Project project);

	Predicate[] buildPredicates(@Nullable ProjectScope projectScope, @Nullable Criteria<Issue> issueCriteria,
								CriteriaQuery<?> query, CriteriaBuilder builder, From<Issue, Issue> issue);

	List<javax.persistence.criteria.Order> buildOrders(List<EntitySort> sorts, CriteriaBuilder builder, 
													   From<Issue, Issue> issue);
	
	List<Issue> query(@Nullable ProjectScope projectScope, EntityQuery<Issue> issueQuery, 
			boolean loadFieldsAndLinks, int firstResult, int maxResults);
	
	int count(@Nullable ProjectScope projectScope, @Nullable Criteria<Issue> issueCriteria);
	
	IssueTimes queryTimes(ProjectScope projectScope, @Nullable Criteria<Issue> issueCriteria);
	
	List<Issue> query(@Nullable EntityQuery<Issue> scope, Project project, String term, int count);

	Collection<String> getUndefinedStates();
	
	void fixUndefinedStates(Map<String, UndefinedStateResolution> resolutions);
	
	Collection<String> getUndefinedFields();
	
	void fixUndefinedFields(Map<String, UndefinedFieldResolution> resolutions);
	
	Collection<UndefinedFieldValue> getUndefinedFieldValues();
	
	void fixUndefinedFieldValues(Map<String, UndefinedFieldValuesResolution> resolutions);
	
	void fixStateAndFieldOrdinals();
	
	@Override
	void delete(Issue issue);
	
	void move(Collection<Issue> issues, Project sourceProject, Project targetProject);

	void copy(Collection<Issue> issues, Project sourceProject, Project targetProject);
	
	void delete(Collection<Issue> issues, Project project);
	
	Collection<MilestoneAndIssueState> queryMilestoneAndIssueStates(Project project, Collection<Milestone> milestones);
	
	List<ProjectIssueStats> queryStats(Collection<Project> projects);
	
	Collection<Milestone> queryUsedMilestones(Project project);

	void clearSchedules(Project project, Collection<Milestone> milestones);
	
	List<Issue> queryAfter(Long projectId, Long afterIssueId, int count);

	Collection<Long> parseFixedIssueIds(Project project, String commitMessage);
	
}
