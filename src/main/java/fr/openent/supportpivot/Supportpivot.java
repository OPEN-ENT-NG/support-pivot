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

import fr.openent.supportpivot.controllers.JiraController;
import fr.openent.supportpivot.controllers.LdeController;
import fr.openent.supportpivot.controllers.SupportController;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import org.entcore.common.http.BaseServer;

import java.util.HashMap;
import java.util.Map;

public class Supportpivot extends BaseServer {

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
			put("/actualites", stringEncode("Actualité"));
			put("/calendar", stringEncode("Agenda"));
			put("/support", stringEncode("Aide et Support"));
			put("/userbook/annuaire#/search", stringEncode("Annuaire"));
			put("/community", stringEncode("Communauté"));
			put("/autres", stringEncode("Autre"));
			put("/blog", stringEncode("Blog"));
			put("/mindmap", stringEncode("Carte mentale"));
			put("/rack", stringEncode("Casier"));
			put("/workspace/workspace", stringEncode("Espace documentaire"));
			put("/exercizer", stringEncode("Exercices et évaluations"));
			put("/formulaire", stringEncode("Formulaire"));
			put("/forum", stringEncode("Forum"));
			put("/timelinegenerator", stringEncode("Frise chronologique"));
			put("/lool", stringEncode("LibreOffice Online (LOOL)"));
			put("/lool", stringEncode("LibreOffice Online (LOOL)"));
			put("/conversation/conversation", stringEncode("Messagerie Zimbra"));
			put("/moodle", stringEncode("Moodle"));
			put("/collaborativewall", stringEncode("Mur collaboratif"));
			put("/mediacentre", stringEncode("Mediacentre"));
			put("/collaborativeeditor", stringEncode("Pad"));
			put("/pages", stringEncode("Pages"));
			put("/peertube", stringEncode("Peertube"));
			put("/rbs", stringEncode("Réservation de ressources"));
			put("/schooltoring", stringEncode("Schooltoring"));
			put("/site-web", stringEncode("Site Web"));
			put("/poll", stringEncode("Sondage"));
			put("/statistics", stringEncode("Statistiques"));
			put("/telechargement", stringEncode("Téléchargement de fichiers lourds"));
			put("web-conference", stringEncode("Web-conférence"));
			put("/wiki", stringEncode("Wiki"));
			put("/application-mobile", stringEncode("Application Mobile"));
			put("/cas", stringEncode("Connexion"));
			put("/workspace/workspace", stringEncode("Espace Documentaire"));
			put("/auth/saml/sso/urn:gar:ihmAffectation:seclin", stringEncode("GAR"));
			put("/portail", stringEncode("Portail"));
			put("/trousseau", stringEncode("Trousseau"));
			put("/vie-scolaire", stringEncode(" Vie Scolaire"));
			put("/homework-assistance", stringEncode("Widget aide aux devoirs (Maxicours)"));
			put("/calendar", stringEncode("Widget calendrier"));
			put("https://www.index-education.com/fr/pronote-info191-pronote-a-chacun-son-espace--demonstration.php", stringEncode("Widget pronote"));
			put("/rss", stringEncode("Widget Rss"));
			put("/bookmark", stringEncode("Widget Signets"));
			put("/xiti", stringEncode("Xiti"));
		}
	};

    @Override
	public void start() throws Exception {
		super.start();
		ConfigManager.init(config);

		ServiceManager.init(vertx, config);
		addController(new SupportController());
		addController(new JiraController());
		addController(new LdeController());

	}

}
