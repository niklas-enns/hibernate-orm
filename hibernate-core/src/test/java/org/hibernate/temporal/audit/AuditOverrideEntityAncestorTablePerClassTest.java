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
 * Same as {@link AuditOverrideEntityAncestorTest}, but for {@code TABLE_PER_CLASS} inheritance. Uses
 * {@code atChangeset(...).find(...)} rather than {@code AuditLogFactory.find(...)} - the latter has a
 * pre-existing, unrelated gap for {@code TABLE_PER_CLASS} hierarchies with multiple concrete subclasses
 * under the default audit strategy (reproducible with plain {@code @Audited} entities, no
 * {@code @Audited.Override} involved), which {@link org.hibernate.temporal.audit.inheritance
 * .AuditTablePerClassInheritanceTest} likewise avoids by querying through a session opened
 * {@code atChangeset(...)} instead.
 */
@AuditedTest
@SessionFactory
@DomainModel(annotatedClasses = {
		AuditOverrideEntityAncestorTablePerClassTest.Base.class,
		AuditOverrideEntityAncestorTablePerClassTest.ExcludeSub.class,
		AuditOverrideEntityAncestorTablePerClassTest.ReviveSub.class
})
@ServiceRegistry(settings = @Setting(name = StateManagementSettings.CHANGESET_ID_SUPPLIER,
		value = "org.hibernate.temporal.audit.AuditOverrideEntityAncestorTablePerClassTest$TxIdSupplier"))
class AuditOverrideEntityAncestorTablePerClassTest {
	private static int currentTxId;

	public static class TxIdSupplier implements ChangesetIdentifierSupplier<Integer> {
		@Override
		public Integer generateIdentifier(SharedSessionContract session) {
			return ++currentTxId;
		}
	}

	@Audited
	@Entity(name = "TpcAncestorBase")
	@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
	static class Base {
		@Id
		long id;
		String auditedByDefault;
		@Audited.Excluded
		String excludedByDefault;
	}

	@Audited
	@Audited.Override(name = "auditedByDefault", isAudited = false)
	@Entity(name = "TpcAncestorExcludeSub")
	static class ExcludeSub extends Base {
		String extra;
	}

	@Audited
	@Audited.Override(name = "excludedByDefault", isAudited = true)
	@Entity(name = "TpcAncestorReviveSub")
	static class ReviveSub extends Base {
		String extra;
	}

	@Test
	void overridesApplyOnlyToTheOverridingSubclass(SessionFactoryScope scope) {
		currentTxId = 0;
		final var sf = scope.getSessionFactory();

		sf.inTransaction( session -> {
			final var e = new ExcludeSub();
			e.id = 1L;
			e.auditedByDefault = "audited-val";
			e.excludedByDefault = "excluded-val";
			e.extra = "extra1";
			session.persist( e );
		} );
		final int excludeRevisionId = currentTxId;

		sf.inTransaction( session -> {
			final var r = new ReviveSub();
			r.id = 2L;
			r.auditedByDefault = "audited-val2";
			r.excludedByDefault = "excluded-val2";
			r.extra = "extra2";
			session.persist( r );
		} );
		final int reviveRevisionId = currentTxId;

		try (var s = sf.withOptions().atChangeset( excludeRevisionId ).openSession()) {
			final var excludeRev = s.find( ExcludeSub.class, 1L );
			assertNull( excludeRev.auditedByDefault, "ExcludeSub's override suppresses this ancestor-audited property" );
			assertEquals( "extra1", excludeRev.extra );
		}
		try (var s = sf.withOptions().atChangeset( reviveRevisionId ).openSession()) {
			final var reviveRev = s.find( ReviveSub.class, 2L );
			assertEquals( "excluded-val2", reviveRev.excludedByDefault, "ReviveSub's override revives this ancestor-excluded property" );
			assertEquals( "audited-val2", reviveRev.auditedByDefault );
		}
	}
}
