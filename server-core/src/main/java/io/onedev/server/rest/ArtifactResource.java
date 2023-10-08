package io.onedev.server.rest;

import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.StringUtils;
import io.onedev.server.cluster.ClusterManager;
import io.onedev.server.manager.BuildManager;
import io.onedev.server.manager.ProjectManager;
import io.onedev.server.model.Build;
import io.onedev.server.rest.annotation.Api;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.artifact.ArtifactInfo;
import org.apache.shiro.authz.UnauthorizedException;
import org.glassfish.jersey.client.ClientProperties;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.InputStream;

import static io.onedev.commons.bootstrap.Bootstrap.BUFFER_SIZE;
import static io.onedev.k8shelper.KubernetesHelper.BEARER;
import static io.onedev.k8shelper.KubernetesHelper.checkStatus;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.compress.utils.IOUtils.copy;

@Api(order=4100, description="In most cases, artifact resource is operated with build id, which is different from build number. "
		+ "To get build id of a particular build number, use the <a href='/~help/api/io.onedev.server.rest.BuildResource/queryBasicInfo'>Query Basic Info</a> operation with query for "
		+ "instance <code>&quot;Number&quot; is &quot;projectName#100&quot;</code>")
@Path("/artifacts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class ArtifactResource {
	
	private final ProjectManager projectManager;
	
	private final BuildManager buildManager;
	
	private final ClusterManager clusterManager;
	
	@Inject
	public ArtifactResource(ProjectManager projectManager, BuildManager buildManager, 
							ClusterManager clusterManager) {
		this.projectManager = projectManager;
		this.buildManager = buildManager;
		this.clusterManager = clusterManager;
	}
	
	@Nullable
	private String normalizeArtifactPath(@Nullable String artifactPath) {
		if (StringUtils.isNotBlank(artifactPath)) {
			artifactPath = StringUtils.stripStart(artifactPath, "/");
			if (StringUtils.isNotBlank(artifactPath)) {
				if (artifactPath.contains(".."))
					throw new ExplicitException("Invalid artifact path");
				return artifactPath;
			}
		} 
		return null;
	}
	
	@Api(order=100, description = "Get artifact info of specified path")
	@Path("/{buildId}/infos{artifactPath:(/.*)?}")
    @GET
    public ArtifactInfo getArtifactInfo(@PathParam("buildId") Long buildId, 
										@PathParam("artifactPath") @Api(example = "/path/to/directoryOrFile") String artifactPath) {
		Build build = buildManager.load(buildId);
		if (!SecurityUtils.canAccess(build))
			throw new UnauthorizedException();
		return buildManager.getArtifactInfo(build, normalizeArtifactPath(artifactPath));
    }

	@Api(order=200, description = "Download artifact of specified path")
	@Path("/{buildId}/contents/{artifactPath:(.*)}")
	@GET
	@Produces(APPLICATION_OCTET_STREAM)
	public StreamingOutput downloadArtifact(@PathParam("buildId") Long buildId,
									 @PathParam("artifactPath") @Api(example = "path/to/file") String artifactPath) {
		Build build = buildManager.load(buildId);
		if (!SecurityUtils.canAccess(build))
			throw new UnauthorizedException();

		return output -> {
			String activeServer = projectManager.getActiveServer(
					build.getProject().getId(), true);
			String serverUrl = clusterManager.getServerUrl(activeServer);
			Client client = ClientBuilder.newClient();
			try {
				WebTarget target = client.target(serverUrl).path("~api/cluster/artifact")
						.queryParam("projectId", build.getProject().getId())
						.queryParam("buildNumber", build.getNumber())
						.queryParam("artifactPath", normalizeArtifactPath(artifactPath));
				Invocation.Builder builder = target.request();
				builder.header(AUTHORIZATION, BEARER + " "
						+ clusterManager.getCredential());

				try (Response response = builder.get()) {
					checkStatus(response);
					try (InputStream is = response.readEntity(InputStream.class)) {
						copy(is, output, BUFFER_SIZE);
					} finally {
						output.close();
					}
				}
			} finally {
				client.close();
			}
		};
	}

	@Api(order=300, description = "Upload artifact to specified path")
	@Path("/{buildId}/{artifactPath:(.*)}")
	@POST
	@Consumes(APPLICATION_OCTET_STREAM)
	public Response uploadArtifact(
			@PathParam("buildId") Long buildId, 
			@PathParam("artifactPath") @Api(example = "path/to/file") String artifactPath, 
			InputStream input) {
		Build build = buildManager.load(buildId);
		if (!SecurityUtils.canManage(build))
			throw new UnauthorizedException();

		String activeServer = projectManager.getActiveServer(
				build.getProject().getId(), true);
		String serverUrl = clusterManager.getServerUrl(activeServer);

		Client client = ClientBuilder.newClient();
		client.property(ClientProperties.REQUEST_ENTITY_PROCESSING, "CHUNKED");
		try {
			WebTarget target = client.target(serverUrl)
					.path("~api/cluster/artifact")
					.queryParam("projectId", build.getProject().getId())
					.queryParam("buildNumber", build.getNumber())
					.queryParam("artifactPath", normalizeArtifactPath(artifactPath));
			Invocation.Builder builder = target.request();
			builder.header(AUTHORIZATION, BEARER + " " + clusterManager.getCredential());

			StreamingOutput os = output -> {
				try {
					copy(input, output, BUFFER_SIZE);
				} finally {
					input.close();
					output.close();
				}
			};

			try (Response response = builder.post(entity(os, APPLICATION_OCTET_STREAM))) {
				checkStatus(response);
			}
		} finally {
			client.close();
		}
		return ok().build();
	}
	
	@Api(order=400, description = "Delete artifact of specified path, or delete all artifacts " +
			"if artifact path is not specified")
	@Path("/{buildId}{artifactPath:(/.*)?}")
	@DELETE
	public Response deleteArtifact(
			@PathParam("buildId") Long buildId, 
			@PathParam("artifactPath") @Api(example = "/path/to/directoryOrFile") String artifactPath) {
		Build build = buildManager.load(buildId);
		if (!SecurityUtils.canManage(build))
			throw new UnauthorizedException();
		
		buildManager.deleteArtifact(build, normalizeArtifactPath(artifactPath));
		return ok().build();
	}
	
}
