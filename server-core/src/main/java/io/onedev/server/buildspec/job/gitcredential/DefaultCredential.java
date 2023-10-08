package io.onedev.server.buildspec.job.gitcredential;

import io.onedev.k8shelper.CloneInfo;
import io.onedev.k8shelper.DefaultCloneInfo;
import io.onedev.server.OneDev;
import io.onedev.server.manager.UrlManager;
import io.onedev.server.model.Build;
import io.onedev.server.annotation.Editable;

@Editable(name="Default", order=100)
public class DefaultCredential implements GitCredential {

	private static final long serialVersionUID = 1L;

	@Override
	public CloneInfo newCloneInfo(Build build, String jobToken) {
		return new DefaultCloneInfo(OneDev.getInstance(UrlManager.class).cloneUrlFor(build.getProject(), false), jobToken);
	}

}
