package io.onedev.server.web.page.admin.groupmanagement.create;

import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.OneDev;
import io.onedev.server.manager.GroupManager;
import io.onedev.server.model.Group;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.Path;
import io.onedev.server.util.PathNode;
import io.onedev.server.web.editable.BeanContext;
import io.onedev.server.web.editable.BeanEditor;
import io.onedev.server.web.page.admin.AdministrationPage;
import io.onedev.server.web.page.admin.groupmanagement.GroupCssResourceReference;
import io.onedev.server.web.page.admin.groupmanagement.GroupListPage;
import io.onedev.server.web.page.admin.groupmanagement.membership.GroupMembershipsPage;

@SuppressWarnings("serial")
public class NewGroupPage extends AdministrationPage {

	private Group group = new Group();
	
	public NewGroupPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		BeanEditor editor = BeanContext.edit("editor", group);
		
		Form<?> form = new Form<Void>("form") {

			@Override
			protected void onSubmit() {
				super.onSubmit();
				
				GroupManager groupManager = OneDev.getInstance(GroupManager.class);
				Group groupWithSameName = groupManager.find(group.getName());
				if (groupWithSameName != null) {
					editor.error(new Path(new PathNode.Named("name")),
							"This name has already been used by another group");
				} 
				if (editor.isValid()) {
					groupManager.create(group);
					Session.get().success("Group created");
					setResponsePage(GroupMembershipsPage.class, GroupMembershipsPage.paramsOf(group));
				}
			}
			
		};
		form.add(editor);
		add(form);
	}

	@Override
	protected boolean isPermitted() {
		return SecurityUtils.isAdministrator();
	}
	
	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(CssHeaderItem.forReference(new GroupCssResourceReference()));
	}

	@Override
	protected Component newTopbarTitle(String componentId) {
		Fragment fragment = new Fragment(componentId, "topbarTitleFrag", this);
		fragment.add(new BookmarkablePageLink<Void>("groups", GroupListPage.class));
		return fragment;
	}

}
