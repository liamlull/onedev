package io.onedev.server.plugin.imports.jiracloud;

import io.onedev.server.OneDev;
import io.onedev.server.annotation.ChoiceProvider;
import io.onedev.server.annotation.ClassValidating;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.ShowCondition;
import io.onedev.server.manager.ProjectManager;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.security.permission.CreateChildren;
import io.onedev.server.util.ComponentContext;
import io.onedev.server.util.EditContext;
import io.onedev.server.validation.Validatable;
import io.onedev.server.web.editable.BeanEditor;

import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Editable
@ClassValidating
public class ImportProjects implements Serializable, Validatable {

	private static final long serialVersionUID = 1L;
	
	ImportServer server;

	private String parentOneDevProject;

	private boolean all;

	private List<String> jiraProjects;

	@Editable(order=200, name="Parent OneDev Project", description = "Optionally specify a OneDev project " +
			"to be used as parent of imported projects. Leave empty to import as root projects")
	@ChoiceProvider("getParentOneDevProjectChoices")
	public String getParentOneDevProject() {
		return parentOneDevProject;
	}

	public void setParentOneDevProject(String parentOneDevProject) {
		this.parentOneDevProject = parentOneDevProject;
	}

	private static List<String> getParentOneDevProjectChoices() {
		ProjectManager projectManager = OneDev.getInstance(ProjectManager.class);
		return projectManager.getPermittedProjects(new CreateChildren()).stream()
				.map(it->it.getPath()).sorted().collect(Collectors.toList());
	}

	@Editable(order=300, name="Import All Projects")
	public boolean isAll() {
		return all;
	}

	public void setAll(boolean all) {
		this.all = all;
	}

	private static boolean isAllDisabled() {
		return !(Boolean) EditContext.get().getInputValue("all");
	}

	@Editable(order=500, name="JIRA Projects to Import")
	@ChoiceProvider("getJiraProjectChoices")
	@ShowCondition("isAllDisabled")
	@Size(min=1, message="At least one project should be selected")
	public List<String> getJiraProjects() {
		return jiraProjects;
	}

	public void setJiraProjects(List<String> jiraProjects) {
		this.jiraProjects = jiraProjects;
	}

	private static List<String> getJiraProjectChoices() {
		BeanEditor editor = ComponentContext.get().getComponent().findParent(BeanEditor.class);
		ImportProjects projects = (ImportProjects) editor.getModelObject();
		return projects.server.listProjects();
	}

	public Collection<String> getImportProjects() {
		if (isAll())
			return server.listProjects();
		else
			return getJiraProjects();
	}

	@Override
	public boolean isValid(ConstraintValidatorContext context) {
		if (parentOneDevProject == null && !SecurityUtils.canCreateRootProjects()) {
			context.disableDefaultConstraintViolation();
			var errorMessage = "No permission to import as root projects, please specify parent project";
			context.buildConstraintViolationWithTemplate(errorMessage)
					.addPropertyNode("parentOneDevProject")
					.addConstraintViolation();
			return false;
		} else {
			return true;
		}
	}

}
