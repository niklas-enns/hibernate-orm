/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Overrides whether a persistent attribute inherited from a {@code @MappedSuperclass}
 * participates in {@linkplain Audited auditing}, as seen from the point of view of the
 * class on which this annotation is placed.
 * <p>
 * A plain {@link Audited.Excluded @Audited.Excluded} declared directly on a member of a
 * {@code @MappedSuperclass} excludes that attribute from every entity which inherits it.
 * {@code @AuditOverride} lets a specific subclass override that decision for its own
 * audit log, without changing the shared superclass:
 * <pre>
 * &#64;MappedSuperclass
 * class Base {
 *     &#64;Audited.Excluded
 *     String comment;
 * }
 *
 * &#64;Entity
 * &#64;Audited
 * &#64;AuditOverride(name = "comment", isAudited = true)
 * class IncludeComment extends Base {}
 * </pre>
 * <p>
 * As with {@link jakarta.persistence.AttributeOverride}, an override declared on a
 * subclass takes precedence over one declared on a superclass for the same attribute
 * path.
 *
 * @see Audited.Excluded
 * @see AuditOverrides
 */
@Target({TYPE, METHOD, FIELD})
@Retention(RUNTIME)
@Repeatable(AuditOverrides.class)
public @interface AuditOverride {
	/**
	 * The path of the persistent attribute whose auditing is being overridden,
	 * relative to the class or embedded attribute on which this annotation is
	 * placed.
	 */
	String name();

	/**
	 * Whether the named attribute should be audited.
	 */
	boolean isAudited() default true;
}
