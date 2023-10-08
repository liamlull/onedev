package io.onedev.server.manager;

import io.onedev.server.model.CodeCommentReply;
import io.onedev.server.persistence.dao.EntityManager;

public interface CodeCommentReplyManager extends EntityManager<CodeCommentReply> {

	void create(CodeCommentReply reply);

	void update(CodeCommentReply reply);
	
}	
