package io.onedev.server.buildspecmodel.inputspec.groupchoiceinput.choiceprovider;

import java.util.Comparator;
import java.util.List;

import edu.emory.mathcs.backport.java.util.Collections;
import io.onedev.server.OneDev;
import io.onedev.server.manager.GroupManager;
import io.onedev.server.model.Group;
import io.onedev.server.annotation.Editable;

@Editable(order=100, name="All groups")
public class AllGroups implements ChoiceProvider {

	private static final long serialVersionUID = 1L;

	@Override
	public List<Group> getChoices(boolean allPossible) {
		List<Group> groups = OneDev.getInstance(GroupManager.class).query();
		Collections.sort(groups, new Comparator<Group>() {

			@Override
			public int compare(Group o1, Group o2) {
				return o1.getName().compareTo(o2.getName());
			}
			
		});
		return groups;
	}

}
