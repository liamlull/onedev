package io.onedev.server.manager.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import com.google.common.base.Preconditions;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import io.onedev.server.manager.GroupManager;
import io.onedev.server.manager.MembershipManager;
import io.onedev.server.model.Group;
import io.onedev.server.model.Membership;
import io.onedev.server.model.User;
import io.onedev.server.persistence.annotation.Sessional;
import io.onedev.server.persistence.annotation.Transactional;
import io.onedev.server.persistence.dao.BaseEntityManager;
import io.onedev.server.persistence.dao.Dao;

@Singleton
public class DefaultMembershipManager extends BaseEntityManager<Membership> implements MembershipManager {

	private static final Logger logger = LoggerFactory.getLogger(DefaultMembershipManager.class);
	
	private final GroupManager groupManager;
	
	@Inject
	public DefaultMembershipManager(Dao dao, GroupManager groupManager) {
		super(dao);
		this.groupManager = groupManager;
	}

	@Transactional
	@Override
	public void delete(Collection<Membership> memberships) {
		for (Membership membership: memberships)
			dao.remove(membership);
	}

	@Override
	public List<Membership> query() {
		return query(true);
	}
	
	@Override
	public int count() {
		return count(true);
	}

	@Override
	public void syncMemberships(User user, Collection<String> groupNames) {
    	Map<String, Membership> syncMap = new HashMap<>();
    	for (String groupName: groupNames) {
    		Group group = groupManager.find(groupName);
    		if (group == null) {
    			logger.warn("Unable to find group: " + groupName);
    		} else {
    			Membership membership = new Membership();
    			membership.setGroup(group);
    			membership.setUser(user);
    			syncMap.put(groupName, membership);
    		}
    	}

    	Map<String, Membership> currentMap = new HashMap<>();
		user.getMemberships().forEach(membership -> 
				currentMap.put(membership.getGroup().getName(), membership));
		
		MapDifference<String, Membership> diff = Maps.difference(currentMap, syncMap);
		
		diff.entriesOnlyOnLeft().values().forEach(membership -> delete(membership));
		diff.entriesOnlyOnRight().values().forEach(membership -> dao.persist(membership));		
	}

	@Transactional
	@Override
	public void create(Membership membership) {
		Preconditions.checkState(membership.isNew());
		dao.persist(membership);
	}

	@Sessional
	@Override
	public List<User> queryMembers(User user) {
		CriteriaBuilder builder = getSession().getCriteriaBuilder();
		CriteriaQuery<User> criteriaQuery = builder.createQuery(User.class);
		Root<User> root = criteriaQuery.from(User.class);
		
		List<Predicate> predicates = new ArrayList<>();
		for (Group group: user.getGroups()) {
			
			Subquery<Membership> membershipQuery = criteriaQuery.subquery(Membership.class);
			Root<Membership> membershipRoot = membershipQuery.from(Membership.class);
			membershipQuery.select(membershipRoot);
	
			predicates.add(builder.exists(membershipQuery.where(
					builder.equal(membershipRoot.get(Membership.PROP_USER), root),
					builder.equal(membershipRoot.get(Membership.PROP_GROUP), group))));
		}
		
		criteriaQuery.where(builder.or(predicates.toArray(new Predicate[0])));
		Query<User> query = getSession().createQuery(criteriaQuery);
		return query.getResultList();
	}

}
