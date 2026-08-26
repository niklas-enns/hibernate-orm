/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.temporal.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.Audited;
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
 * Tests that {@link Audited.Override @Audited.Override} lets a subclass override
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
	@Audited.Override(name = "secret", isAudited = true)
	@Entity(name = "Included")
	static class IncludedEntity extends Base {
	}

	// Chaining two @MappedSuperclass levels exercises a "closer override wins" scenario. See
	// AuditOverrideEntityAncestorTest for @Audited.Override targeting a property owned by a genuine
	// @Entity ancestor rather than a @MappedSuperclass.
	@MappedSuperclass
	@Audited.Override(name = "secret", isAudited = true)
	static class Middle extends Base {
	}

	@Audited
	@Entity(name = "InheritsMiddleOverride")
	static class InheritsMiddleOverrideEntity extends Middle {
	}

	@Audited
	@Audited.Override(name = "secret", isAudited = false)
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
				"sibling without @Audited.Override keeps the superclass's exclusion"
		);
		assertFalse(
				metadata.getEntityBinding( IncludedEntity.class.getName() ).getProperty( "secret" ).isAuditedExcluded(),
				"@Audited.Override(isAudited = true) should include the inherited attribute"
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
				"entity with no @Audited.Override of its own inherits Middle's override"
		);
		assertTrue(
				metadata.getEntityBinding( ReExcludedEntity.class.getName() ).getProperty( "secret" ).isAuditedExcluded(),
				"ReExcludedEntity's own @Audited.Override should win over the one inherited from Middle"
		);
	}
}
