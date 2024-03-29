/*
 *
 * Copyright (c) Mairie de Paris, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.supportpivot.services;

import fr.openent.supportpivot.model.jira.JiraTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.Future;

/**
 * Created by mercierq on 09/02/2018.
 * Service to handle pivot information and send it to Jira
 */
public interface JiraService {
    /**
     * Send pivot ticket to Jira
     * A ticket is created with the Jira API with all the pivot information received
     *
     * @param pivotTicket pivot format
     * @return a success future with the pivot ticket modified
     */
    Future<PivotTicket> sendToJIRA(final PivotTicket pivotTicket);

    /**
     * Get jira JiraTicket from this id
     *
     * @param jiraTicketId jira ticket id
     * @return a future with jira ticket if succeeded
     */
    Future<JiraTicket> getFromJira(final String jiraTicketId);
}
