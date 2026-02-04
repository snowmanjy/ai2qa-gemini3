/**
 * Ai2QA Infrastructure JPA - PostgreSQL persistence.
 *
 * <p>
 * This module contains:
 * <ul>
 * <li>JPA Entities</li>
 * <li>Spring Data Repositories</li>
 * <li>Flyway Migrations</li>
 * <li>Encryption Service (AES-256)</li>
 * <li>Tenant Isolation via Hibernate Filter</li>
 * </ul>
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class), defaultCondition = "tenant_id = :tenantId")
package com.ai2qa.infra.jpa;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
