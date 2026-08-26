/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.temporal.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import org.hibernate.SharedSessionContract;
import org.hibernate.annotations.Audited;
import org.hibernate.audit.AuditLogFactory;
import org.hibernate.cfg.StateManagementSettings;
import org.hibernate.testing.orm.junit.AuditedTest;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.hibernate.temporal.spi.ChangesetIdentifierSupplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that {@link Audited.Override @Audited.Override} also works against a property physically owned by a
 * genuine {@code @Entity} ancestor (as opposed to only a {@code @MappedSuperclass}, the only case
 * {@link AuditOverrideTest} exercises). Two sibling subclasses each override a *different* ancestor-owned
 * property in opposite directions - {@code ExcludeSub} suppresses {@code auditedByDefault} (audited on the
 * ancestor by default), {@code ReviveSub} revives {@code excludedByDefault} ({@code @Audited.Excluded} on
 * the ancestor) - and neither affects the ancestor's own audit history or the other sibling's.
 */
@AuditedTest
@SessionFactory
@DomainModel(annotatedClasses = {
		AuditOverrideEntityAncestorTest.SingleTable.Base.class,
		AuditOverrideEntityAncestorTest.SingleTable.ExcludeSub.class,
		AuditOverrideEntityAncestorTest.SingleTable.ReviveSub.class,
		AuditOverrideEntityAncestorTest.Joined.Base.class,
		AuditOverrideEntityAncestorTest.Joined.ExcludeSub.class,
		AuditOverrideEntityAncestorTest.Joined.ReviveSub.class
})
@ServiceRegistry(settings = @Setting(name = StateManagementSettings.CHANGESET_ID_SUPPLIER,
		value = "org.hibernate.temporal.audit.AuditOverrideEntityAncestorTest$TxIdSupplier"))
class AuditOverrideEntityAncestorTest {
	private static int currentTxId;

	public static class TxIdSupplier implements ChangesetIdentifierSupplier<Integer> {
		@Override
		public Integer generateIdentifier(SharedSessionContract session) {
			return ++currentTxId;
		}
	}

	static class SingleTable {
		@Audited
		@Entity(name = "STAncestorBase")
		@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
		static class Base {
			@Id
			long id;
			String auditedByDefault;
			@Audited.Excluded
			String excludedByDefault;
		}

		@Audited
		@Audited.Override(name = "auditedByDefault", isAudited = false)
		@Entity(name = "STAncestorExcludeSub")
		static class ExcludeSub extends Base {
			String extra;
		}

		@Audited
		@Audited.Override(name = "excludedByDefault", isAudited = true)
		@Entity(name = "STAncestorReviveSub")
		static class ReviveSub extends Base {
			String extra;
		}
	}

	static class Joined {
		@Audited
		@Entity(name = "JAncestorBase")
		@Inheritance(strategy = InheritanceType.JOINED)
		static class Base {
			@Id
			long id;
			String auditedByDefault;
			@Audited.Excluded
			String excludedByDefault;
		}

		@Audited
		@Audited.Override(name = "auditedByDefault", isAudited = false)
		@Entity(name = "JAncestorExcludeSub")
		static class ExcludeSub extends Base {
			String extra;
		}

		@Audited
		@Audited.Override(name = "excludedByDefault", isAudited = true)
		@Entity(name = "JAncestorReviveSub")
		static class ReviveSub extends Base {
			String extra;
		}
	}

	@Test
	void singleTableOverridesApplyOnlyToTheOverridingSubclass(SessionFactoryScope scope) {
		currentTxId = 0;
		scope.getSessionFactory().inTransaction( session -> {
			final var e = new SingleTable.ExcludeSub();
			e.id = 1L;
			e.auditedByDefault = "audited-val";
			e.excludedByDefault = "excluded-val";
			e.extra = "extra1";
			session.persist( e );
		} );
		scope.getSessionFactory().inTransaction( session -> {
			final var r = new SingleTable.ReviveSub();
			r.id = 2L;
			r.auditedByDefault = "audited-val2";
			r.excludedByDefault = "excluded-val2";
			r.extra = "extra2";
			session.persist( r );
		} );
		scope.getSessionFactory().inTransaction( session -> {
			final var b = new SingleTable.Base();
			b.id = 3L;
			b.auditedByDefault = "audited-val3";
			b.excludedByDefault = "excluded-val3";
			session.persist( b );
		} );

		try (var auditLog = AuditLogFactory.create( scope.getSessionFactory() )) {
			final var excludeRev = auditLog.find( SingleTable.ExcludeSub.class, 1L, 1 );
			assertNull( excludeRev.auditedByDefault, "ExcludeSub's override suppresses this ancestor-audited property" );
			assertEquals( "extra1", excludeRev.extra );

			final var reviveRev = auditLog.find( SingleTable.ReviveSub.class, 2L, 2 );
			assertEquals( "excluded-val2", reviveRev.excludedByDefault, "ReviveSub's override revives this ancestor-excluded property" );
			assertEquals( "audited-val2", reviveRev.auditedByDefault );

			final var baseRev = auditLog.find( SingleTable.Base.class, 3L, 3 );
			assertEquals( "audited-val3", baseRev.auditedByDefault, "the ancestor's own instances are unaffected by either sibling's override" );
			assertNull( baseRev.excludedByDefault );
		}
	}

	@Test
	void joinedOverridesApplyOnlyToTheOverridingSubclass(SessionFactoryScope scope) {
		currentTxId = 0;
		scope.getSessionFactory().inTransaction( session -> {
			final var e = new Joined.ExcludeSub();
			e.id = 1L;
			e.auditedByDefault = "audited-val";
			e.excludedByDefault = "excluded-val";
			e.extra = "extra1";
			session.persist( e );
		} );
		scope.getSessionFactory().inTransaction( session -> {
			final var r = new Joined.ReviveSub();
			r.id = 2L;
			r.auditedByDefault = "audited-val2";
			r.excludedByDefault = "excluded-val2";
			r.extra = "extra2";
			session.persist( r );
		} );
		scope.getSessionFactory().inTransaction( session -> {
			final var b = new Joined.Base();
			b.id = 3L;
			b.auditedByDefault = "audited-val3";
			b.excludedByDefault = "excluded-val3";
			session.persist( b );
		} );

		try (var auditLog = AuditLogFactory.create( scope.getSessionFactory() )) {
			final var excludeRev = auditLog.find( Joined.ExcludeSub.class, 1L, 1 );
			assertNull( excludeRev.auditedByDefault, "ExcludeSub's override suppresses this ancestor-audited property" );
			assertEquals( "extra1", excludeRev.extra );

			final var reviveRev = auditLog.find( Joined.ReviveSub.class, 2L, 2 );
			assertEquals( "excluded-val2", reviveRev.excludedByDefault, "ReviveSub's override revives this ancestor-excluded property" );
			assertEquals( "audited-val2", reviveRev.auditedByDefault );

			final var baseRev = auditLog.find( Joined.Base.class, 3L, 3 );
			assertEquals( "audited-val3", baseRev.auditedByDefault, "the ancestor's own instances are unaffected by either sibling's override" );
			assertNull( baseRev.excludedByDefault );
		}
	}
}
