package io.onedev.server.web.resource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.apache.tika.mime.MimeTypes;
import org.apache.wicket.request.resource.AbstractResource;

import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.FileUtils;
import io.onedev.server.OneDev;
import io.onedev.server.manager.AgentManager;
import io.onedev.server.manager.AgentTokenManager;
import io.onedev.server.security.SecurityUtils;

public class AgentLibResource extends AbstractResource {

	private static final long serialVersionUID = 1L;

	@Override
	protected ResourceResponse newResourceResponse(Attributes attributes) {
		String bearerToken = SecurityUtils.getBearerToken(
				(HttpServletRequest) attributes.getRequest().getContainerRequest());
		if (bearerToken != null 
				&& OneDev.getInstance(AgentTokenManager.class).find(bearerToken) != null) {
			ResourceResponse response = new ResourceResponse();
			response.setContentType(MimeTypes.OCTET_STREAM);
			response.disableCaching();
			
			response.setWriteCallback(new WriteCallback() {

				@Override
				public void writeData(Attributes attributes) throws IOException {
					File tempDir = FileUtils.createTempDir("agent-lib");
					try {
						Collection<String> agentLibs = OneDev.getInstance(AgentManager.class).getAgentLibs();
						
						for (File file: Bootstrap.getBootDir().listFiles()) {
							if (agentLibs.contains(file.getName())) 
								FileUtils.copyFileToDirectory(file, tempDir);
						}
						
						for (File file: Bootstrap.getLibDir().listFiles()) {
							if (agentLibs.contains(file.getName())) 
								FileUtils.copyFileToDirectory(file, tempDir);
						}
						
						OutputStream os = attributes.getResponse().getOutputStream();
						FileUtils.tar(tempDir, os, false);
					} finally {
						FileUtils.deleteDir(tempDir);
					}
				}				
				
			});

			return response;				
		} else {
			throw new ExplicitException("A valid agent token is expected");
		}
	}

}
