/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Groups multiple {@link AuditOverride} declarations on the same class or attribute.
 *
 * @see AuditOverride
 */
@Target({TYPE, METHOD, FIELD})
@Retention(RUNTIME)
public @interface AuditOverrides {
	AuditOverride[] value();
}
