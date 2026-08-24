/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.temporal.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.Audited;
import org.hibernate.annotations.AuditOverride;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import org.hibernate.testing.orm.junit.BaseUnitTest;
import org.hibernate.testing.util.ServiceRegistryUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link AuditOverride @AuditOverride} lets a subclass override
 * whether an attribute inherited from a {@code @MappedSuperclass} is excluded
 * from that subclass's audit log, without changing the shared superclass or
 * any sibling subclass.
 */
@BaseUnitTest
class AuditOverrideTest {
	private StandardServiceRegistry registry;

	@BeforeEach
	void buildServiceRegistry() {
		registry = ServiceRegistryUtil.serviceRegistry();
	}

	@AfterEach
	void releaseServiceRegistry() {
		if ( registry != null ) {
			StandardServiceRegistryBuilder.destroy( registry );
		}
	}

	@MappedSuperclass
	static class Base {
		@Id
		long id;
		String name;
		@Audited.Excluded
		String secret;
	}

	@Audited
	@Entity(name = "Excluded")
	static class ExcludedEntity extends Base {
	}

	@Audited
	@AuditOverride(name = "secret", isAudited = true)
	@Entity(name = "Included")
	static class IncludedEntity extends Base {
	}

	// @AuditOverride, like JPA's @AttributeOverride, only applies across
	// @MappedSuperclass (or embeddable) boundaries: a property already bound
	// by a real @Entity superclass is shared with its subclasses rather than
	// rebound per subclass, so it cannot be overridden per subclass. Chaining
	// two @MappedSuperclass levels is how a "closer override wins" scenario
	// is exercised instead.
	@MappedSuperclass
	@AuditOverride(name = "secret", isAudited = true)
	static class Middle extends Base {
	}

	@Audited
	@Entity(name = "InheritsMiddleOverride")
	static class InheritsMiddleOverrideEntity extends Middle {
	}

	@Audited
	@AuditOverride(name = "secret", isAudited = false)
	@Entity(name = "ReExcluded")
	static class ReExcludedEntity extends Middle {
	}

	@Test
	void subclassOverridesInheritedExclusion() {
		final Metadata metadata = new MetadataSources( registry )
				.addAnnotatedClass( Base.class )
				.addAnnotatedClass( ExcludedEntity.class )
				.addAnnotatedClass( IncludedEntity.class )
				.buildMetadata();

		assertTrue(
				metadata.getEntityBinding( ExcludedEntity.class.getName() ).getProperty( "secret" ).isAuditedExcluded(),
				"sibling without @AuditOverride keeps the superclass's exclusion"
		);
		assertFalse(
				metadata.getEntityBinding( IncludedEntity.class.getName() ).getProperty( "secret" ).isAuditedExcluded(),
				"@AuditOverride(isAudited = true) should include the inherited attribute"
		);
	}

	@Test
	void deeperSubclassOverrideTakesPrecedence() {
		final Metadata metadata = new MetadataSources( registry )
				.addAnnotatedClass( Base.class )
				.addAnnotatedClass( Middle.class )
				.addAnnotatedClass( InheritsMiddleOverrideEntity.class )
				.addAnnotatedClass( ReExcludedEntity.class )
				.buildMetadata();

		assertFalse(
				metadata.getEntityBinding( InheritsMiddleOverrideEntity.class.getName() )
						.getProperty( "secret" ).isAuditedExcluded(),
				"entity with no @AuditOverride of its own inherits Middle's override"
		);
		assertTrue(
				metadata.getEntityBinding( ReExcludedEntity.class.getName() ).getProperty( "secret" ).isAuditedExcluded(),
				"ReExcludedEntity's own @AuditOverride should win over the one inherited from Middle"
		);
	}
}
