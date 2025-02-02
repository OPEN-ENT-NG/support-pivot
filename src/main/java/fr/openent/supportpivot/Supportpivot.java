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

package fr.openent.supportpivot;

import io.vertx.core.Promise;
import org.entcore.common.http.BaseServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Supportpivot extends BaseServer {

    /**
     * JSON fields for Pivot format
     */
    public final static String IDIWS_FIELD = "id_iws";
    public final static String IDENT_FIELD = "id_ent";
    public final static String IDJIRA_FIELD = "id_jira";
    public final static String COLLECTIVITY_FIELD = "collectivite";
    public final static String ACADEMY_FIELD = "academie";
    public final static String CREATOR_FIELD = "demandeur";
    public final static String TICKETTYPE_FIELD = "type_demande";
    public final static String TITLE_FIELD = "titre";
    public final static String DESCRIPTION_FIELD = "description";
    public final static String PRIORITY_FIELD = "priorite";
    public final static String MODULES_FIELD = "modules";
    public final static String COMM_FIELD = "commentaires";
    public final static String ATTACHMENT_FIELD = "pj";
    public final static String ATTACHMENT_NAME_FIELD = "nom";
    public final static String ATTACHMENT_CONTENT_FIELD = "contenu";
    public final static String ATTACHMENT_CONTENT_TYPE_FIELD = "contentType";
    public final static String STATUSIWS_FIELD = "statut_iws";
    public final static String STATUSENT_FIELD = "statut_ent";
    public final static String STATUSJIRA_FIELD = "statut_jira";
    public final static String DATE_CREA_FIELD = "date_creation";
    public final static String DATE_RESOIWS_FIELD = "date_resolution_iws";
    public final static String DATE_RESOENT_FIELD = "date_resolution_ent";
    public final static String DATE_RESOJIRA_FIELD = "date_resolution_jira";
    public final static String TECHNICAL_RESP_FIELD = "reponse_technique";
    public final static String CLIENT_RESP_FIELD = "reponse_client";
    public final static String ATTRIBUTION_FIELD = "attribution";

    public final static String ATTRIBUTION_IWS = "RECTORAT";
    public final static String ATTRIBUTION_ENT = "ENT";
    public final static String ATTRIBUTION_CGI = "CGI";
    /**
     * Mandatory fields
     */
    public static final String[] IWS_MANDATORY_FIELDS = {
            IDIWS_FIELD,
            COLLECTIVITY_FIELD,
            CREATOR_FIELD,
            DESCRIPTION_FIELD,
            ATTRIBUTION_FIELD
            };

    public static final String[] ENT_MANDATORY_FIELDS = {
            IDENT_FIELD,
            CREATOR_FIELD,
            TITLE_FIELD,
            DESCRIPTION_FIELD
    };

    public static final List<String> PIVOT_PRIORITY_LEVEL = Arrays.asList(
            "Mineur",
            "Majeur",
            "Bloquant");

    public static final String STATUSENT_NEW = "1";
    public static final String STATUSENT_OPENED = "2";
    public static final String STATUSENT_RESOLVED = "3";
    public static final String STATUSENT_CLOSED = "4";

    public static final String STATUSPIVOT_NEW = stringEncode("Ouvert");
    public static final String STATUSPIVOT_OPENED = stringEncode("En cours");
    public static final String STATUSPIVOT_RESOLVED = stringEncode("R&eacute;solu");
    public static final String STATUSPIVOT_CLOSED = stringEncode("Ferm&eacute;");

    /**
     * Encode a string in UTF-8
     * @param in String to encode
     * @return encoded String
     */
    private static String stringEncode(String in) {
        return  in;
    }


    public final static Map<String, String> applicationsMap = new HashMap<String, String>()
    {
        {
            put("/eliot/absences",stringEncode("Absences (Axess)"));
            put("/actualites",stringEncode("Actualit&eacute;s"));
            put("/admin",stringEncode("Administration"));
            put("/calendar",stringEncode("Agenda"));
            put("/eliot/agenda",stringEncode("Agenda (Axess)"));
            put("/support",stringEncode("Aide et Support"));
            put("/userbook/annuaire#/search",stringEncode("Annuaire"));
            put("/blog",stringEncode("Blog"));
            put("/eliot/textes",stringEncode("Cahier de textes (Axess)"));
            put("/mindmap",stringEncode("Carte mentale"));
            put("/rack",stringEncode("Casier"));
            put("/community",stringEncode("Communaut&eacute;"));
            put("/cas",stringEncode("Connexion"));
            put("/workspace/workspace",stringEncode("Documents"));
            put("/exercizer",stringEncode("Exercizer"));
            put("/forum",stringEncode("Forum"));
            put("/timelinegenerator",stringEncode("Frise chronologique"));
            put("/conversation/conversation",stringEncode("Messagerie"));
            put("/collaborativewall",stringEncode("Mur collaboratif"));
            put("/eliot/notes",stringEncode("Notes (Axess)"));
            put("/pages",stringEncode("Pages"));
            put("/rbs",stringEncode("R&eacute;servation de ressources"));
            put("/eliot/scolarite",stringEncode("Scolarité (Axess)"));
            put("/poll",stringEncode("Sondage"));
            put("/statistics",stringEncode("Statistiques"));
            put("/rss",stringEncode("Widget Rss"));
            put("/bookmark",stringEncode("Widget Signets"));
            put("/wiki",stringEncode("Wiki"));
            put("/xiti",stringEncode("Xiti"));
        }
    };

  @Override
	public void start(final Promise<Void> startPromise) throws Exception {
		super.start(startPromise);
		addController(new SupportController());
    startPromise.tryComplete();
	}

}
