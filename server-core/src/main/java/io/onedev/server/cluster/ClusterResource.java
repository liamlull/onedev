package io.onedev.server.cluster;

import com.google.common.collect.Sets;
import io.onedev.commons.utils.FileUtils;
import io.onedev.server.OneDev;
import io.onedev.server.attachment.AttachmentManager;
import io.onedev.server.manager.ProjectManager;
import io.onedev.server.git.CommandUtils;
import io.onedev.server.git.GitFilter;
import io.onedev.server.git.GitUtils;
import io.onedev.server.git.LfsObject;
import io.onedev.server.git.command.AdvertiseReceiveRefsCommand;
import io.onedev.server.git.command.AdvertiseUploadRefsCommand;
import io.onedev.server.git.hook.HookUtils;
import io.onedev.server.infomanager.CommitInfoManager;
import io.onedev.server.infomanager.VisitInfoManager;
import io.onedev.server.model.Build;
import io.onedev.server.model.Project;
import io.onedev.server.rest.annotation.Api;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.StorageManager;
import io.onedev.server.util.concurrent.PrioritizedRunnable;
import io.onedev.server.util.concurrent.WorkExecutor;
import io.onedev.server.util.patternset.PatternSet;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.onedev.commons.bootstrap.Bootstrap.BUFFER_SIZE;
import static io.onedev.commons.utils.FileUtils.tar;
import static io.onedev.commons.utils.LockUtils.read;
import static io.onedev.commons.utils.LockUtils.write;
import static io.onedev.server.model.Build.getArtifactsLockName;
import static io.onedev.server.model.Project.SHARE_TEST_DIR;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

@Api(internal=true)
@Path("/cluster")
@Consumes(MediaType.WILDCARD)
@Singleton
public class ClusterResource {

	private final ProjectManager projectManager;
	
	private final AttachmentManager attachmentManager;
	
	private final CommitInfoManager commitInfoManager;
	
	private final VisitInfoManager visitInfoManager;
	
	private final StorageManager storageManager;
	
	private final WorkExecutor workExecutor;
	
	@Inject
	public ClusterResource(ProjectManager projectManager, CommitInfoManager commitInfoManager, 
						   AttachmentManager attachmentManager, VisitInfoManager visitInfoManager, 
						   WorkExecutor workExecutor, StorageManager storageManager) {
		this.commitInfoManager = commitInfoManager;
		this.projectManager = projectManager;
		this.workExecutor = workExecutor;
		this.attachmentManager = attachmentManager;
		this.visitInfoManager = visitInfoManager;
		this.storageManager = storageManager;
	}

	@Path("/project-files")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadFiles(@QueryParam("projectId") Long projectId,
								  @QueryParam("path") String path,
								  @QueryParam("patterns") String patterns,
								  @QueryParam("readLock") String readLock) {
		if (!SecurityUtils.getUser().isSystem())
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		StreamingOutput os = output -> read(readLock, () -> {
			File directory = new File(projectManager.getStorageDir(projectId), path);
			PatternSet patternSet = PatternSet.parse(patterns);
			patternSet.getExcludes().add(SHARE_TEST_DIR + "/**");
			tar(directory, patternSet.getIncludes(), patternSet.getExcludes(), output, false);
			return null;
		});
		return ok(os).build();
	}

	@Path("/assets")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadAssets() {
		if (!SecurityUtils.getUser().isSystem())
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> tar(OneDev.getAssetsDir(), Sets.newHashSet("**"), null, output, false);
		return ok(os).build();
	}
	
	@Path("/project-file")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadFile(@QueryParam("projectId") Long projectId,
										@QueryParam("path") String path,
										@QueryParam("readLock") String readLock) {
		if (!SecurityUtils.getUser().isSystem())
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		File file = new File(projectManager.getStorageDir(projectId), path);
		if (read(readLock, () -> file.exists())) {
			StreamingOutput os = output -> read(readLock, () -> {
				try (output; InputStream is = new FileInputStream(file)) {
					IOUtils.copy(is, output, BUFFER_SIZE);
				}
				return null;
			});
			return ok(os).build();
		} else {
			return status(NO_CONTENT).build();
		}
	}
	
	@Path("/artifacts")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadArtifacts(@QueryParam("projectId") Long projectId,
			@QueryParam("buildNumber") Long buildNumber,
			@QueryParam("artifacts") String artifacts) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> read(getArtifactsLockName(projectId, buildNumber), () -> {
			File artifactsDir = Build.getArtifactsDir(projectId, buildNumber);
			PatternSet patternSet = PatternSet.parse(artifacts);
			patternSet.getExcludes().add(SHARE_TEST_DIR + "/**");
			tar(artifactsDir, patternSet.getIncludes(), patternSet.getExcludes(), output, false);
			return null;
		});
		return ok(os).build();
	}

	@Path("/artifact")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadArtifact(@QueryParam("projectId") Long projectId,
									  @QueryParam("buildNumber") Long buildNumber,
									  @QueryParam("artifactPath") String artifactPath) {
		if (!SecurityUtils.getUser().isSystem())
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		StreamingOutput os = output -> read(getArtifactsLockName(projectId, buildNumber), () -> {
			File artifactsDir = Build.getArtifactsDir(projectId, buildNumber);
			File artifactFile = new File(artifactsDir, artifactPath);
			try (output; InputStream is = new FileInputStream(artifactFile)) {
				IOUtils.copy(is, output, BUFFER_SIZE);
			}
			return null;
		});
		return ok(os).build();
	}
	
	@Path("/blob")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadBlob(@QueryParam("projectId") Long projectId, @QueryParam("revId") String revId, 
			@QueryParam("path") String path) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> {
			Repository repository = projectManager.getRepository(projectId);
			try (output; InputStream is = GitUtils.getInputStream(repository, ObjectId.fromString(revId), path)) {
				IOUtils.copy(is, output, BUFFER_SIZE);
			}
	   };
		return ok(os).build();
	}
	
	@Path("/site")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadSiteFile(@QueryParam("projectId") Long projectId, @QueryParam("filePath") String filePath) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> read(Project.getSiteLockName(projectId), () -> {
			File file = new File(projectManager.getSiteDir(projectId), filePath);
			try (output; InputStream is = new FileInputStream(file)) {
				IOUtils.copy(is, output, BUFFER_SIZE);
			}
			return null;
		});
		return ok(os).build();
	}
	
	@Path("/git-advertise-refs")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response gitAdvertiseRefs(@QueryParam("projectId") Long projectId, 
			@QueryParam("protocol") String protocol, @QueryParam("upload") boolean upload) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> {
			File gitDir = projectManager.getGitDir(projectId);
			if (upload)
				new AdvertiseUploadRefsCommand(gitDir, output).protocol(protocol).run();
			else
				new AdvertiseReceiveRefsCommand(gitDir, output).protocol(protocol).run();
	   };
		return ok(os).build();
	}
		
	@Path("/git-pack")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@POST
	public Response gitPack(InputStream is, @QueryParam("projectId") Long projectId, 
			@QueryParam("userId") Long userId, @QueryParam("protocol") String protocol, 
			@QueryParam("upload") boolean upload) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> {
			Map<String, String> hookEnvs = HookUtils.getHookEnvs(projectId, userId);
			
			try {
				File gitDir = projectManager.getGitDir(projectId);
				if (upload) {
					workExecutor.submit(new PrioritizedRunnable(GitFilter.PRIORITY) {
						
						@Override
						public void run() {
							CommandUtils.uploadPack(gitDir, hookEnvs, protocol, is, output);
						}
						
					}).get();
				} else {
					workExecutor.submit(new PrioritizedRunnable(GitFilter.PRIORITY) {
						
						@Override
						public void run() {
							CommandUtils.receivePack(gitDir, hookEnvs, protocol, is, output);
						}
						
					}).get();
				}
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
	   };
		return ok(os).build();
	}
	
	@Path("/commit-info")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadCommitInfo(@QueryParam("projectId") Long projectId) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> {
			File tempDir = FileUtils.createTempDir("commit-info"); 
			try {
				commitInfoManager.export(projectId, tempDir);
				tar(tempDir, output, false);
			} finally {
				FileUtils.deleteDir(tempDir);
			}
	   };
		return ok(os).build();
	}

	@Path("/visit-info")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadVisitInfo(@QueryParam("projectId") Long projectId) {
		if (!SecurityUtils.getUser().isSystem())
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		StreamingOutput os = output -> {
			File tempDir = FileUtils.createTempDir("visit-info");
			try {
				visitInfoManager.export(projectId, tempDir);
				tar(tempDir, output, false);
			} finally {
				FileUtils.deleteDir(tempDir);
			}
		};
		return ok(os).build();
	}
	
	@Path("/lfs")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadLfs(@QueryParam("projectId") Long projectId, @QueryParam("objectId") String objectId) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");
		
		StreamingOutput os = output -> {
			try (output; InputStream is = new LfsObject(projectId, objectId).getInputStream()) {
				IOUtils.copy(is, output, BUFFER_SIZE);
			}
	   };
		return ok(os).build();
	}
	
	@Path("/lfs")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@POST
	public Response uploadLfs(InputStream input, @QueryParam("projectId") Long projectId, 
			@QueryParam("objectId") String objectId) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		try (input; OutputStream os = new LfsObject(projectId, objectId).getOutputStream()) {
			IOUtils.copy(input, os, BUFFER_SIZE);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return ok().build();
	}

	@Path("/attachment")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@POST
	public Response uploadAttachment(InputStream input, @QueryParam("projectId") Long projectId, 
			@QueryParam("attachmentGroup") String attachmentGroup, 
			@QueryParam("suggestedAttachmentName") String suggestedAttachmentName) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		String attachmentName = attachmentManager.saveAttachmentLocal(
				projectId, attachmentGroup, suggestedAttachmentName, input);
		return ok(attachmentName).build();
	}
	
	@Path("/attachments")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@GET
	public Response downloadAttachments(@QueryParam("projectId") Long projectId, 
			@QueryParam("attachmentGroup") String attachmentGroup) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		StreamingOutput os = output -> read(attachmentManager.getAttachmentLockName(projectId, attachmentGroup), () -> {
			tar(attachmentManager.getAttachmentGroupDir(projectId, attachmentGroup),
					Sets.newHashSet("**"), Sets.newHashSet(), output, false);
			return null;					
		});
		return ok(os).build();
	}
	
	@Path("/artifact")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@POST
	public Response uploadArtifact(InputStream input, @QueryParam("projectId") Long projectId, 
			@QueryParam("buildNumber") Long buildNumber,  @QueryParam("artifactPath") String artifactPath) {
		if (!SecurityUtils.getUser().isSystem()) 
			throw new UnauthorizedException("This api can only be accessed via cluster credential");

		write(getArtifactsLockName(projectId, buildNumber), () -> {
			var artifactsDir = storageManager.initArtifactsDir(projectId, buildNumber);
			File artifactFile = new File(artifactsDir, artifactPath);
			FileUtils.createDir(artifactFile.getParentFile());
			try (input; OutputStream os = new FileOutputStream(artifactFile)) {
				IOUtils.copy(input, os, BUFFER_SIZE);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			projectManager.directoryModified(projectId, artifactsDir);
			return null;
		});
		
		return ok().build();
	}
	
}
